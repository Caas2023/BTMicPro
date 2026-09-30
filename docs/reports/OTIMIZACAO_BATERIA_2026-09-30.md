# Otimização de bateria sobre o Modo 8 — v1.5.12

**Data:** 30/09/2026 · **Versão:** 1.5.12, código 30 · **Base:** Modo 8 validado pelo usuário no KingKong X Pro + WAYXIN KT-1.

## Resultado do usuário

O **Modo 8 funcionou** (silêncio de voz 16 kHz, buffer 500 ms, retomada após 2,5 s, MODE_NORMAL, liberação para mídia). Os parâmetros de áudio dele foram preservados integralmente. Esta versão reduz o consumo de bateria sem alterar o comportamento de áudio.

## Onde a bateria ia embora (evidência no código)

1. **Thread do silêncio:** blocos de 20 ms (~50 escritas/s) com prioridade `THREAD_PRIORITY_AUDIO` (a mais alta). Para silêncio não há requisito de latência.
2. **Loop de controle a cada 300 ms:** 3–4 chamadas binder por ciclo (`activeRecordingConfigurations`, `isMusicActive`, `audioManager.mode`), impedindo sono profundo — mesmo com rota estável há horas.
3. **Diagnóstico a cada 30 s** mesmo estável, com leitura de volumes, bateria e escrita em arquivo.
4. **Consulta de música** mesmo quando o perfil nem libera para mídia (Standard) ou há chamada ativa — resultado descartado, custo pago.

## Otimizações aplicadas

| # | Arquivo | Mudança |
|---|---|---|
| B1 | `core/SilentAudioKeeper.kt` | Blocos de 100 ms (~10 escritas/s, 5× menos) + prioridade `BACKGROUND`. Buffer de 500 ms absorve atrasos; underruns (só silêncio) seguem visíveis no diagnóstico. |
| B2 | `core/BluetoothRoutingEngine.kt` | Loop adaptativo: estável 1.500 ms / instável 500 ms. Callbacks (captura, reprodução, SCO, dispositivos) mantêm a imediaticidade; o loop é rede de segurança. |
| B3 | `core/BluetoothRoutingEngine.kt` | `isMusicActive` pulado quando `releaseForMedia=false` ou há chamada — comportamento idêntico, menos acordos. |
| B4 | `core/BluetoothRoutingEngine.kt` | Heartbeat: estável 90 s / instável 30 s. Perfil eco registra flag `eco=` no `PROFILE_APPLIED`. |
| B5 | `core/AudioModeProfile.kt` | **Modo 9 (Eco)**: áudio idêntico ao Modo 8 + `ecoPolling` (estável 3 s / instável 1 s, heartbeat 180 s/60 s). |
| B6 | `core/LiveAudioMonitor.kt` | Modo 9 no ramo de voz do retorno local. |

O Modo Bar (LoudnessEnhancer + Equalizer globais) continua opcional: para economia máxima, desligue-o — DSP contínuo no mix de saída consome CPU sempre.

## Protocolo de validação

1. Usar o Modo 9 por alguns dias como uso normal; Modo 8 como referência.
2. Notas de voz de 30–60 s, reprodução, tela apagada, reconexão Bluetooth; marcar falhas no botão **Marcar corte/falha agora**.
3. No ZIP, comparar por perfil: `CAPTURE_STATE` sobrepostas a quedas, `ROUTE_RETRY`, `underruns` no heartbeat, marcas manuais e duração da bateria percebida.
4. Critério: Modo 9 deve gravar/ouvir como o Modo 8, com menos tempo de CPU (incomeensurável por log; percepção de bateria em dias de uso).

## Limites honestos

- Testes/Lint verificam compilação e regressões determinísticas, não consumo em mA nem áudio no WhatsApp.
- O rádio SCO ativo consome bateria por natureza; a otimização ataca CPU e acordos do app.
- Sem celular conectado nesta sessão: build, testes e Lint executados; instalação e medição em aparelho pendentes de reconexão USB/ADB.
