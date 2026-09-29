# BT Mic Pro

Aplicativo Android em Kotlin e Jetpack Compose para selecionar e acompanhar a rota de comunicação de um fone ou intercomunicador Bluetooth.

O projeto contém um serviço de roteamento, controles de volume de mídia e chamada, botão flutuante opcional, diagnóstico exportável e um motor DSP para monitoramento local do microfone. O processamento local não fornece áudio tratado diretamente ao WhatsApp. O funcionamento em aplicativos de terceiros precisa ser verificado em cada combinação de celular, intercomunicador e versão do aplicativo.

## Estado atual

- Versão de teste: **1.5.11**, código **29**.
- Android mínimo declarado: API 26; `compileSdk` e `targetSdk`: **36**.
- Prioridade de validação: **Cubot KingKong X Pro + WAYXIN KT-1**. Nove estratégias selecionáveis em Configurações. A v1.5.11 corrige a sustentação da solicitação de rota: no Android 15, uma solicitação de UID sem áudio ativo pode expirar após seis segundos. A gravação contínua no WhatsApp ainda precisa ser validada por perfil.
- Compare no aparelho: retorno local; com teste de microfone parado, envio e reprodução de nota de voz no WhatsApp; chamada e reconexão Bluetooth. O perfil é experimental e não comprova funcionamento em todos os celulares.
- A captura local tenta 16 kHz primeiro e depois 48/44,1/8 kHz conforme suporte. O roteamento legado usa SCO nas APIs 26–30; Android 12+ usa o dispositivo de comunicação.
- A inicialização automática tem restrições de permissões e execução em segundo plano, especialmente no Android 14+.
- A interface contém ajustes de retorno/DSP cujo fluxo de ativação ainda precisa ser concluído.
- Não há implementação atual de chamada simulada via Telecom nem de gravador WAV com compartilhamento.

Consulte a [revisão e protocolo de testes](docs/reports/REVISAO_MODOS_E_LOGS_2026-09-29.md) e o [histórico](docs/HISTORICO_E_STATUS.md).

## Modos comparativos

| Perfil | Experimento de roteamento |
|---|---|
| X Pro experimental | MODE_NORMAL, silêncio de voz 16 kHz/buffer mínimo de 200 ms, liberação automática para mídia |
| Standard / referência | Mesmo silêncio, sem liberação automática para mídia |
| 2 | MODE_IN_COMMUNICATION solicitado uma vez, com silêncio de voz |
| 3 | MODE_NORMAL, apenas seleção de rota, sem silêncio |
| 4 | MODE_NORMAL, silêncio com atributos de sonificação e saída Bluetooth preferida |
| 5 | MODE_IN_COMMUNICATION solicitado uma vez, sem silêncio |
| 6 | MODE_NORMAL, silêncio de voz em loop estático, sem produtor PCM contínuo |
| 7 | Como X Pro, mas AudioTrack PCM em 8 kHz; não seleciona codec HFP |
| 8 | Como X Pro, buffer mínimo de 500 ms e retomada após 2,5 s sem mídia |

Exceto Standard, a liberação para mídia exige ausência de captura ativa visível ao Android. Trocas de perfil aguardam o fim da captura. Os modos 2/5 permitem comparar o modo VoIP, que pode afetar a aceitação de notas pelo WhatsApp; não há reafirmação periódica de modo. Nenhum perfil captura áudio de fundo para sustentar a rota. As diferenças do retorno/DSP pertencem ao teste local.

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
