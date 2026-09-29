# Relatório de melhorias — corte na gravação + mic sempre ligado

**Data:** 07/09/2026 · **Versão:** 1.5.3, código 21 · **APK de teste:** `APK/BTMicPro_v1.5.3.apk`

## 1. Diagnóstico do corte

O app não processa o áudio do WhatsApp — `VoiceProcessingEngine` só roda dentro do teste local (`LiveAudioMonitor`). A nota de voz vai direto intercom → Android → WhatsApp. Por isso os ajustes de DSP nunca tiraram o corte do WhatsApp. Causas encontradas:

1. **Re-seleção de SCO no meio da gravação (causa principal).** `matchInputEndpoint` (`core/RouteHealth.kt`) retornava `null` quando o endereço input/output não batia exato ou havia mais de uma saída — comum em intercoms. Sem `inputMatches`, a engine nunca atingia `KEEP` e reagendava `selectCommunicationDevice` a cada 0,5–15 s, renegociando o SCO no meio da frase.
2. **Gate agressivo no teste local.** Preset padrão `EXTREME_WIND` + denoise 1.0: limiar ~380 RMS, piso 0,15 (−16,5 dB), hold 120 ms. Sílaba fraca era mutada e parecia corte.
3. **Falso "ligado".** `hasRoutingPermissions()` (`MainActivity.kt`) só checava `BLUETOOTH_CONNECT`; sem `RECORD_AUDIO` a rota nunca ficava pronta e o WhatsApp caía para o mic do celular.
4. **Não existe captura contínua no foreground service.** `BtMicService` mantém a rota como serviço `connectedDevice`, mas não instancia `LiveAudioMonitor` nem `AudioRecord`. A única captura contínua está no `MainViewModel` e é encerrada por `MainActivity.onStop()` e `MainViewModel.onCleared()`. O tipo `microphone` e sua permissão também estão ausentes; ambos serão necessários se a captura migrar para o serviço, mas apenas declará-los não mantém o SCO ativo.

## 2. Correções aplicadas (neste relatório)

| # | Arquivo | Mudança |
|---|---------|---------|
| F1 | `core/RouteHealth.kt:4` | `matchInputEndpoint` tolerante: prefere endereço exato, senão usa o primeiro input do mesmo tipo; nunca bloqueia `KEEP` por ambiguidade |
| F2 | `core/VoiceProcessingEngine.kt:112,175` | Hold 120 ms → 250 ms; pisos do expansor 0,15–0,32 → 0,30–0,40 (atenua vento sem zerar voz) |
| F3 | `MainActivity.kt:68` | Exige `RECORD_AUDIO` + `BLUETOOTH_CONNECT` antes de mostrar "ligado" |
| F4 | `core/LiveAudioMonitor.kt:168` | Buffer `max(320×4, min×2)` contra overrun |

## 3. Limpeza do projeto (~765 MB)

Removidos do disco: 17 APKs duplicados (~306 MB), zips de setup + apk estranho (~459 MB), `temp_rish/`, `graphify-out/`, `.claude-flow/`, `.opencode/node_modules`, `.kotlin/`, `scripts/string_refactor.py` (one-shot). No repo (`git rm`): 6 auditorias antigas + 2 docs V4 + tabela de skills. Em `docs/reports/` ficam só os 2 relatórios de 06/09 + este.

## 4. Pesquisa: 10 projetos semelhantes (técnicas para mic 100% ligado)

1. **Endda/BTMicFix** (Kotlin+Compose) — força `setCommunicationDevice()` p/ troca A2DP→SCO que o WhatsApp não faz; auto-ativa via CompanionDeviceManager; fallback Shizuku.
2. **thrillfall/mic2bluetooth** (GPL-3.0) — streaming contínuo mic→BT (A2DP + modo SCO ~40–80 ms); prova de que captura contínua = canal nunca suspende.
3. **shivarya/clear-mic-router** — hold state machine (assert→held→fighting) + watchdog que detecta `setCommunicationDevice()` aceito-mas-ignorado pelo telecom.
4. **termux-api PR #857** — padrão canônico: SCO antes de gravar (API 31+ `setCommunicationDevice`; abaixo `startBluetoothSco` + esperar `SCO_AUDIO_STATE_CONNECTED`).
5. **Home Assistant Android** — `VOICE_COMMUNICATION` + start/stop SCO em volta do foco de áudio.
6. **aahlenst/android-audiorecord-sample** — referência: buffer ×2, só grava após `SCO_CONNECTED`, para se o SCO cair.
7. **llfbandit/record** — mesmo padrão SCO-antes-de-gravar em plugin.
8. **EarLLM One** — Android 14+ exige `FOREGROUND_SERVICE_MICROPHONE` + tipo `microphone`; nunca TTS/A2DP junto com gravação SCO; preferir BLE Audio; fallback por detecção de silêncio (bug HFP Samsung).
9. **WO Mic** (Play, `com.wo.voice2`) — mic permanente como produto: foreground service + captura contínua + auto-reconnect; whitelist de bateria; BT e Wi-Fi interferem (2,4 GHz).
10. **myMic / BlueMic / Microphone Amplifier** — mic sobrevive em background e **reconstrói a sessão após ligação**; status visível.

Conclusão técnica: o caminho público para testar "sempre ligado" é **AudioRecord contínuo em foreground service tipo `microphone`** (o modo SCO virtual do AOSP exige permissão de sistema). Isso mantém uma captura legítima ativa, mas a coexistência com a captura do WhatsApp depende do fabricante e precisa ser validada em aparelho físico. Shizuku fica como plano B para aparelhos que ignoram a API pública.

## 5. Auditoria de ciclo de vida e serviço

| Severidade | Evidência | Impacto |
|---|---|---|
| Alta | `MainActivity.kt:62-65` chama `stopLiveMonitorForBackground()` em `onStop()` | O monitor local para ao minimizar, trocar de app ou abrir o WhatsApp |
| Alta | `MainViewModel.kt:427` para `LiveAudioMonitor` em `onCleared()` | A captura não sobrevive ao encerramento da UI |
| Alta | `BtMicService.kt` usa `DualVolumeManager`, mas não `LiveAudioMonitor`/`AudioRecord` | O serviço mantém seleção e recuperação da rota, não o microfone ocupado continuamente |
| Média | Manifesto declara somente `FOREGROUND_SERVICE_CONNECTED_DEVICE` e `connectedDevice` | Uma futura captura no serviço não atende aos requisitos de foreground service de microfone do Android atual |
| Média | Componentes críticos de serviço, rota física e ciclo de vida não têm teste instrumental | Os testes unitários não comprovam comportamento SCO/HFP real nem coexistência com WhatsApp |

Recomendação: separar claramente os modos. O roteador do WhatsApp deve continuar sem DSP e sem assumir que controla o PCM do outro app. Um holder contínuo de microfone deve ser opcional, visível na notificação, iniciado por ação do usuário e interrompido com segurança para chamadas; sua eficácia deve ser medida em aparelho físico antes de virar padrão.

## 6. Plano pendente — mic 100% ligado ao ativar o app

1. Restaurar `FOREGROUND_SERVICE_MICROPHONE` + `microphone|connectedDevice` no manifest e no `startForeground`.
2. Holder de `AudioRecord(MIC, 16 kHz, mono)` + `setPreferredDevice(BT)` rodando do `startEngine` ao `onDestroy` (MIC tem prioridade baixa → WhatsApp prevalece; o holder só impede o SCO de suspender).
3. Ligar só em foreground (botão/notificação); manter `YIELD_TO_CALL` e rebuild após chamada/GPS.
4. Dual path API 31+ / legado com espera de `SCO_CONNECTED`; avisar que o ponto verde constante é intencional.

## 7. Como testar este APK

1. Teste local OFF, retorno 0%, Modo Bar OFF → ligue o roteador, aguarde `RouteReady`.
2. Grave 3 notas de 30 s no WhatsApp parado; confira no diagnóstico se `routeLossCount`/`scoDisconnectCount` subiram durante a gravação (se sim, ainda há flap — envie o Flight Recorder).
3. Teste local separado, tela ligada: preset `NORMAL`, depois `HIGHWAY`.
