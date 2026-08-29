# 📜 Histórico e Status do Projeto — BT Mic Pro

## Informações do Projeto
- **Nome**: BT Mic Pro (Roteador & Gravador Inteligente de Microfone Bluetooth)
- **Stack**: Android Nativo (Kotlin 2.0+) · Jetpack Compose (Material 3) · Coroutines & StateFlow · Gradle (KTS) · Foreground Services
- **SDK Alvo**: API 35 (Android 15) · Mínimo API 26 (Android 8.0)
- **Aparelho Alvo Validado**: Cubot KingKong X Pro (MediaTek Dimensity 8200, 12GB RAM, Android 14 / API 34, Bluetooth 5.3)
- **Repositório**: d:\aplicativo intercominicador

---

## Mapa de Módulos & Componentes Implementados
| Módulo / Pacote | Arquivo | Responsabilidade |
|-----------------|---------|------------------|
| `com.btmicpro` | `MainActivity.kt` | Ponto de entrada, gestão de permissões em tempo de execução (`RECORD_AUDIO`, `BLUETOOTH_CONNECT`, `POST_NOTIFICATIONS`) e renderização da UI |
| `com.btmicpro` | `BtMicProApp.kt` | Application class para inicialização global do app |
| `com.btmicpro.core` | `BluetoothAudioRouter.kt` | Gerenciador de roteamento Bluetooth SCO e `setCommunicationDevice` (API 31+) com monitoramento contínuo |
| `com.btmicpro.core` | `AudioEffectController.kt` | Ativação em nível de hardware dos efeitos `NoiseSuppressor`, `AutomaticGainControl` e `AcousticEchoCanceler` |
| `com.btmicpro.core` | `AudioCaptureEngine.kt` | Motor de gravação 48kHz com DSP em tempo real, Filtro Passa-Alta Anti-Vento (120Hz), Noise Gate e medidor VU |
| `com.btmicpro.core` | `AudioFileManager.kt` | Gravação em formato WAV canônico, player de áudio integrado e Intent de envio direto no WhatsApp via `FileProvider` |
| `com.btmicpro.core` | `RouterState.kt` | Sealed classes e data classes com tipagem canônica e estados reativos |
| `com.btmicpro.service` | `BtMicService.kt` | Foreground Service (tipo `microphone`) com notificação persistente e controle interativo |
| `com.btmicpro.service` | `RecordingService.kt` | Foreground Service para gravação ininterrupta em segundo plano |
| `com.btmicpro.receiver` | `BootReceiver.kt` | BroadcastReceiver para auto-inicialização no boot do celular |
| `com.btmicpro.ui` | `MainViewModel.kt` | ViewModel com `StateFlow` e persistência de preferências |
| `com.btmicpro.ui` | `MainScreen.kt` | Interface de usuário moderna (Jetpack Compose + Material 3) com Dark/Light mode e alto contraste |
| `com.btmicpro.ui.theme` | `Color.kt`, `Theme.kt`, `Type.kt` | Sistema de design, paleta de cores e tipografia Material 3 |
| `res` | `AndroidManifest.xml`, `strings.xml`, `file_paths.xml` | Configurações de sistema, textos em PT-BR e segurança do FileProvider |

---

## Registro de Alterações

### 2026-08-28 21:07 (BRT) — Conclusão da Estrutura e Código do Aplicativo
- **Descrição**: Implementação completa do aplicativo nativo Android BT Mic Pro com os dois modos operacionais:
  1. **Modo WhatsApp (Router)**: Foreground service para forçar o microfone do fone Bluetooth no WhatsApp com `setCommunicationDevice` e processamento DSP nativo do hardware MediaTek.
  2. **Modo Gravador com Tratamento de Vento**: Motor de áudio de 48kHz com filtro passa-alta IIR de 120Hz anti-vento, noise gate dinâmico e compartilhamento direto com WhatsApp.
- **Arquivos Criados/Modificados**:
  - `build.gradle.kts`, `settings.gradle.kts`, `gradle.properties`, `gradle/libs.versions.toml`, `app/build.gradle.kts`
  - `app/src/main/AndroidManifest.xml`
  - `app/src/main/res/values/strings.xml`, `app/src/main/res/xml/file_paths.xml`
  - `app/src/main/java/com/btmicpro/BtMicProApp.kt`
  - `app/src/main/java/com/btmicpro/MainActivity.kt`
  - `app/src/main/java/com/btmicpro/core/RouterState.kt`
  - `app/src/main/java/com/btmicpro/core/AudioEffectController.kt`
  - `app/src/main/java/com/btmicpro/core/BluetoothAudioRouter.kt`
  - `app/src/main/java/com/btmicpro/core/AudioCaptureEngine.kt`
  - `app/src/main/java/com/btmicpro/core/AudioFileManager.kt`
  - `app/src/main/java/com/btmicpro/service/BtMicService.kt`
  - `app/src/main/java/com/btmicpro/service/RecordingService.kt`
  - `app/src/main/java/com/btmicpro/receiver/BootReceiver.kt`
  - `app/src/main/java/com/btmicpro/ui/MainViewModel.kt`
  - `app/src/main/java/com/btmicpro/ui/MainScreen.kt`
  - `app/src/main/java/com/btmicpro/ui/theme/Color.kt`
  - `app/src/main/java/com/btmicpro/ui/theme/Type.kt`
  - `app/src/main/java/com/btmicpro/ui/theme/Theme.kt`
  - `README.md`, `docs/architecture/ARCHITECTURE.md`
- **Status**: ✅ Código-fonte 100% implementado e pronto para compilação.

### 2026-08-28 21:16 (BRT) — Orientações de Instalação (APK)
- **Descrição**: O usuário questionou se o app estava pronto. O código-fonte está concluído na pasta do projeto, porém, devido à ausência do Android SDK e Java no terminal, foi orientado a baixar o Android Studio para gerar o arquivo `.apk` de instalação.
- **Arquivos Afetados**: `docs/HISTORICO_E_STATUS.md`
- **Status**: ⏳ Aguardando confirmação do usuário sobre o ambiente de compilação (se já possui Android Studio ou se precisa de guia de instalação).

### 2026-08-29 01:15 (BRT) - Implementação do Raw Audio Mode, UI Renovada, Promoção Shopee e Versionamento
- **Descrição**: 
  1. Adicionado o 'Raw Audio Mode' baseado no 'Noise Uncanceller' (forçando AudioSource.UNPROCESSED e desabilitando cancelamento de hardware) para evitar cortes de voz pelo vento na moto.
  2. Implementado reforço automático de volume (100% no In-Call e Music) ao iniciar o modo de roteamento.
  3. Redesign completo da tela principal (estilo neon verde e dark mode com botão circular central).
  4. Implementação de Banner Promocional de Rodapé (Shopee) clicável, exibido de forma inteligente (respeitando limite de exibições/sessão).
  5. Versionamento do app introduzido (versão 1.0.2 no gradle e visível na UI).
  6. Sincronização limpa com o repositório GitHub sem arquivos temporários pesados.
- **Arquivos Afetados**: 
  - pp/build.gradle.kts (Versão 1.0.2)
  - pp/src/main/java/com/btmicpro/core/AudioCaptureEngine.kt (Raw Audio Mode)
  - pp/src/main/java/com/btmicpro/core/BluetoothAudioRouter.kt (Boost de volume)
  - pp/src/main/java/com/btmicpro/ui/MainViewModel.kt (Controle do popup)
  - pp/src/main/java/com/btmicpro/ui/MainScreen.kt (Novo layout, versão na tela e botão redimensionado)
- **Status**: ✅ Compilado, gerado APK (app-debug.apk / BTMicPro.apk) e código sincronizado no GitHub.

### 2026-08-29 01:55 (BRT) - Implementação do Carrossel Dinâmico de Promoções (5 Produtos com Links de Afiliado)
- **Descrição**:
  1. Implementação de sistema rotatório (Carrossel com Crossfade a cada 4 segundos) no rodapé do app.
  2. Inclusão dos 5 produtos com links de afiliados individuais:
     - **Capacetes**: https://s.shopee.com.br/3g3FumMouO (Tema Vermelho Neon)
     - **Capa de Chuva**: https://s.shopee.com.br/2gAij6Mj1r (Tema Ciano Neon)
     - **Kit Relação**: https://s.shopee.com.br/7fZOgLkL36 (Tema Laranja Neon)
     - **Intercomunicador**: https://s.shopee.com.br/4qFDJF1V58 (Tema Roxo Neon)
     - **Pneus de Moto**: https://s.shopee.com.br/6fgrTWMGS9 (Tema Verde Neon)
  3. Efeito de borda pulsante/piscante mantido de acordo com a cor do produto ativo.
  4. Redirecionamento dinâmico: o clique abre exatamente o link do produto que está sendo exibido na tela no momento.
- **Arquivos Afetados**:
  - pp/src/main/java/com/btmicpro/ui/MainScreen.kt
  - docs/HISTORICO_E_STATUS.md
- **Status**: ✅ Compilado com sucesso e sincronizado no GitHub.
