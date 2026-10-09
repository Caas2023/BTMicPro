# BT Mic Pro

Aplicativo Android em Kotlin e Jetpack Compose para selecionar e acompanhar a rota de comunicação de um fone ou intercomunicador Bluetooth.

O projeto contém um serviço de roteamento, controles de volume de mídia e chamada, botão flutuante opcional, diagnóstico exportável e um motor DSP para monitoramento local do microfone. O processamento local não fornece áudio tratado diretamente ao WhatsApp. O funcionamento em aplicativos de terceiros precisa ser verificado em cada combinação de celular, intercomunicador e versão do aplicativo.

## Estado atual

- Versão de teste: **1.5.18**, código **36**.
- Android mínimo declarado: API 26; `compileSdk` e `targetSdk`: **36**.
- Prioridade de validação: **Cubot KingKong X Pro + WAYXIN KT-1**. Treze estratégias selecionáveis pela tela inicial. Os antigos Modos 8, 9 e 10 foram preservados sem alterar parâmetros e renumerados como **Modo 1 (KingKong estável)**, **Modo 2 (KingKong Eco)** e **Modo 3 (KingKong rápido)**. Os Modos 4–13 são candidatos experimentais para outros aparelhos e não representam garantia por marca.
- Compare no aparelho: retorno local; com teste de microfone parado, envio e reprodução de nota de voz no WhatsApp; chamada e reconexão Bluetooth. O perfil é experimental e não comprova funcionamento em todos os celulares.
- A captura local tenta 16 kHz primeiro e depois 48/44,1/8 kHz conforme suporte. O roteamento legado usa SCO nas APIs 26–30; Android 12+ usa o dispositivo de comunicação.
- A inicialização automática tem restrições de permissões e execução em segundo plano, especialmente no Android 14+.
- A interface contém ajustes de retorno/DSP cujo fluxo de ativação ainda precisa ser concluído.
- Não há implementação atual de chamada simulada via Telecom nem de gravador WAV com compartilhamento.

Consulte o [relatório de otimização](docs/reports/OTIMIZACAO_BATERIA_2026-09-30.md) e o [histórico](docs/HISTORICO_E_STATUS.md).

## Modos comparativos

| Perfil | Experimento de roteamento |
|---|---|
| 1 — KingKong estável | **Antigo Modo 8 preservado**: MODE_NORMAL, voz 16 kHz, buffer 500 ms, retomada 2,5 s |
| 2 — KingKong Eco | **Antigo Modo 9 preservado**: áudio do Modo 1, polling estável 3 s e heartbeat 180 s |
| 3 — KingKong rápido | **Antigo Modo 10 preservado**: polling 250 ms e retomada 1,2 s |
| 4 — Samsung Safe | MODE_NORMAL, buffer 400 ms, retomada 2 s e seleção conservadora |
| 5 — Xiaomi persistente | Buffer 600 ms, watchdog 8 s e reafirmação somente após oscilação observada |
| 6 — Motorola equilibrado | Buffer 350 ms, retomada 1,8 s e polling moderado |
| 7 — Android 8–11 | SCO legado, timeout 15 s e MODE_IN_COMMUNICATION solicitado uma vez |
| 8 — Android 12+ universal | `setCommunicationDevice`, MODE_NORMAL, buffer 300 ms e retomada 1,8 s |
| 9 — Rádio fraco | Buffer 800 ms, tolerância de oscilação 1 s e retomada 3,5 s |
| 10 — Baixa latência | Buffer 200 ms, polling 200 ms e retomada 750 ms |
| 11 — Sem sustentação | Diagnóstico sem AudioTrack silencioso; a rota pode expirar |
| 12 — Loop estático | AudioTrack MODE_STATIC, sem produtor PCM contínuo |
| 13 — VoIP fallback | Último recurso com MODE_IN_COMMUNICATION; pode conflitar com notas do WhatsApp |

A liberação para mídia exige ausência de captura ativa visível ao Android. Trocas de perfil aguardam o fim da captura. Os Modos 7 e 13 usam modo VoIP uma única vez e podem afetar a aceitação de notas pelo WhatsApp. Nenhum perfil captura áudio de fundo para sustentar a rota. As diferenças do retorno/DSP pertencem apenas ao teste local.

## Economia de bateria e resposta rápida (v1.5.18)

- Silêncio de sustentação em blocos de 100 ms com prioridade baixa de CPU (~10 acordos/s em vez de ~50/s); o buffer de 500 ms dos Modos 1/2/3 absorve atrasos sem underrun audível (é silêncio).
- Verificação periódica adaptativa: Modo 1 usa 1,5 s estável/0,5 s instável; Modo 2 usa 3 s/1 s; Modo 3 usa 250 ms. Eventos reais continuam chegando por callbacks do Android.
- Diagnóstico periódico espaçado com rota estável: 90 s (180 s no Modo 2); 30 s quando instável.
- Consulta de música ativa pulada quando o perfil não libera para mídia ou há chamada (mesmo comportamento, menos acordos do sistema).
- Para economia máxima: use o Modo 2, desligue o **Modo Bar** e o botão flutuante se não usar. O rádio SCO ativo consome bateria por natureza.
- Para escutar áudios recebidos com menor atraso no KingKong, teste o Modo 3. Se ele oscilar ou cortar notas, volte ao Modo 1, que permanece como referência estável.
- A tela inicial possui carrossel de banners no rodapé, alternando a cada 1 segundo e abrindo o link do anúncio ao toque.

## Logs para vários dias

- Registro automático em `cache/flight_recorder/`: até **7 dias / 32 MiB**, com rotação em segmentos de 2 MiB.
- Cada evento contém data, fuso, sessão, sequência, uptime e perfil aplicado. Registra SCO/HFP, dispositivos, modo, metadados de captura/reprodução, recuperação, erros e snapshots a cada 30 s.
- **Marcar corte/falha agora** cria uma referência temporal para correlacionar o que foi ouvido com os eventos.
- **Exportar ZIP** compartilha todos os arquivos retidos e o diagnóstico atual; **Copiar** contém apenas eventos recentes. O histórico é relido ao abrir novamente o app.
- O cache pode ser removido pelo Android ou pelo usuário. Exporte antes disso. O app registra os eventos públicos que consegue observar; o logcat completo do sistema e a identificação da captura privada do WhatsApp podem exigir ADB.

Para comparar, use um modo por período, grave e reproduza notas e marque as falhas. Após alguns dias, exporte o ZIP para análise; a cor de rota pronta sozinha não confirma a origem nem a qualidade do microfone.

## Compilar e verificar

Requisitos: JDK 17, Android SDK com plataforma 36 e ferramentas compatíveis. Configure `JAVA_HOME` e o caminho `sdk.dir` no arquivo local `local.properties`. AGP 8.10.1 requer Gradle 8.11.1 ou superior.

```powershell
.\gradlew.bat --console=plain testDebugUnitTest lintDebug
.\gradlew.bat --console=plain assembleDebug
```

No Linux: `./gradlew testDebugUnitTest lintDebug assembleDebug --no-daemon`.

Quando as dependências já estiverem em cache, é possível adicionar `--offline`. Se o cache padrão não for acessível, configure `GRADLE_USER_HOME` para um diretório de cache gravável.

O build gera `app/build/outputs/apk/debug/app-debug.apk`. As cópias para distribuição ficam em `APK/BTMicPro_v<versionName>.apk`, conforme a [regra de armazenamento de APKs](.agents/rules/apk_management.md). Os testes e o Lint não substituem a validação de áudio Bluetooth em aparelho físico.

## Estrutura

| Caminho | Responsabilidade |
|---|---|
| `app/src/main/java/com/btmicpro/core/` | Roteamento, HFP, diagnóstico, volumes e processamento de áudio |
| `app/src/main/java/com/btmicpro/service/` | Serviço de roteamento e botão flutuante |
| `app/src/main/java/com/btmicpro/receiver/` | Eventos de inicialização e Bluetooth |
| `app/src/main/java/com/btmicpro/ui/` | Interface Compose, estado e preferências |
| `app/src/test/` | Testes JVM de DSP, modelos, exportação e ganho |
| `docs/reports/` | Auditorias e evidências |

Os documentos históricos descrevem versões anteriores; use o código atual e o relatório mais recente para avaliar o que está implementado e verificado.
