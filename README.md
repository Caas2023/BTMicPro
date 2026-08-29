# BT Mic Pro 🏍️🎤

**BT Mic Pro** é um aplicativo Android desenvolvido especialmente para motociclistas e criadores de conteúdo que gravam áudios e vídeos em ambientes com ruído extremo (como rodovias e trânsito intenso).

O aplicativo atua forçando o roteamento de áudio do sistema, obrigando aplicativos de terceiros (como **WhatsApp**, **Telegram**, e a própria **Câmera**) a utilizarem o microfone do seu intercomunicador ou fone Bluetooth (via protocolo SCO), ao invés do microfone interno do celular.

## 🚀 Funcionalidades Principais

### 1. MOTO WHATSAPP MODE (Modo Roteador Ao Vivo)
Uma tela inicial minimalista com um botão **GIGANTE**, ideal para ser pressionado rapidamente mesmo utilizando luvas de motociclista. 
* **O que faz:** Ao ser ativado, o aplicativo cria um serviço em segundo plano (Foreground Service) que "engana" o Android, forçando o canal Bluetooth SCO a ficar permanentemente aberto.
* **Volume Maximizado:** Ao ligar o modo, o volume de mídia e chamadas do aparelho é automaticamente elevado para 100%, garantindo que você ouça as mensagens no meio do vento.
* **Filtros Nativos:** Tenta ativar no hardware (DSP do processador, como MediaTek ou Snapdragon) o Cancelamento de Eco Acústico (AEC), Supressão de Ruído (NS) e Controle Automático de Ganho (AGC).
* **Uso Prático:** Você ativa o botão, minimiza o app, abre o WhatsApp e grava um áudio ou vídeo. O WhatsApp vai puxar o som direto do capacete!

### 2. ADVANCED RECORDER (Gravador com IA / Tratamento Avançado)
Para situações onde o vento está absurdo e o WhatsApp sozinho não dá conta, o aplicativo possui um gravador embutido (Segunda Tela) com processamento digital de sinal (DSP) pesado.
* **Filtro Passa-Alta (High-Pass Filter):** Corta as frequências graves (abaixo de 120Hz~150Hz), eliminando quase que totalmente aquele som de "estrondo" do vento batendo no capacete e do escapamento da moto.
* **Noise Gate Dinâmico:** Um portão de ruído ajustável. Quando você para de falar, o microfone é mutado quase instantaneamente, cortando o som do motor no fundo.
* **Compressor Compensatório:** Aumenta o volume da sua voz automaticamente, compensando a perda causada pelo filtro de ruídos e garantindo um áudio alto e claro.
* **Compartilhamento:** Após a gravação, basta clicar no ícone de enviar para mandar o arquivo `.wav` tratado direto para a conversa do WhatsApp.

## 🛠️ Tecnologias Utilizadas
* **Kotlin & Jetpack Compose:** Interface 100% moderna, fluida e reativa.
* **AudioManager & AudioRecord:** APIs nativas de baixo nível do Android para manipulação de bytes de áudio.
* **Twilio AudioSwitch (Fallback):** Utilizado internamente para gerenciar transições complexas de áudio Bluetooth e garantir alta resiliência de conexão.
* **Coroutines & StateFlow:** Gerenciamento assíncrono para garantir que a gravação e o processamento (DSP) não travem a interface.

## 📱 Como Instalar e Testar
1. O APK compilado encontra-se na raiz do projeto: `BTMicPro.apk`.
2. Transfira para o seu dispositivo Android (Testado no Cubot KingKong X Pro).
3. Conecte o seu intercomunicador ou fone Bluetooth.
4. Abra o app, dê as permissões necessárias e ative o **MOTO WHATSAPP MODE**.

## 🚧 Estrutura do Projeto
* `com.btmicpro.core`: Contém a lógica pesada de roteamento (`BluetoothAudioRouter`) e processamento matemático de áudio (`AudioCaptureEngine`).
* `com.btmicpro.service`: Serviço de primeiro plano para manter o app vivo com notificação na barra de status.
* `com.btmicpro.ui`: Telas feitas em Jetpack Compose, desenhadas para alto contraste (Dark Theme + Neon Green) e alvos de toque grandes.
