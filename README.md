# BT Mic Pro 🏍️🎤

**BT Mic Pro** é um aplicativo Android desenvolvido especialmente para motociclistas que precisam enviar e ouvir áudios em qualquer aplicativo (WhatsApp, Telegram, etc) com o capacete, em ambientes com ruído extremo.

O aplicativo atua forçando o roteamento de áudio do sistema, obrigando aplicativos de terceiros a utilizarem o microfone do seu intercomunicador Bluetooth (via protocolo SCO), ao invés do microfone interno do celular, e mantendo o canal sempre ativo para ouvir e falar ao mesmo tempo.

## 🚀 Funcionalidades Principais

### MOTO WHATSAPP MODE (Sempre em Chamada)
Uma tela inicial minimalista com um botão **GIGANTE**, ideal para ser pressionado rapidamente mesmo utilizando luvas de motociclista. 
* **O que faz:** Ao ser ativado, o aplicativo cria um serviço em segundo plano (Foreground Service) com chamada fantasma via Telecom, AudioFocus e keep-alive de silêncio 16kHz, forçando o canal Bluetooth SCO a ficar permanentemente aberto.
* **Volume Maximizado:** Ao ligar o modo, o volume de mídia e chamadas do aparelho é automaticamente elevado para 100%, garantindo que você ouça as mensagens no meio do vento.
* **Filtros Nativos:** Ativa no hardware (DSP MediaTek/Snapdragon) o Cancelamento de Eco Acústico (AEC), Supressão de Ruído (NS) e Controle Automático de Ganho (AGC).
* **Auto-ligar:** Quando o intercomunicador conecta, o modo ativa sozinho. Após reiniciar o celular, volta sozinho.
* **Uso Prático:** Você ativa o botão, minimiza o app, abre o WhatsApp e grava um áudio ou ouve um áudio. O WhatsApp vai puxar o som direto do capacete e você ouve no capacete ao mesmo tempo!

## 🛠️ Tecnologias Utilizadas
* **Kotlin & Jetpack Compose:** Interface 100% moderna, fluida e reativa.
* **AudioManager & Telecom ConnectionService:** APIs nativas para simular chamada sempre ativa e forçar SCO.
* **SilentAudioKeeper & Watchdog:** Mantém o túnel SCO vivo com silêncio 16kHz e re-aplica roteamento a cada 3s.
* **Coroutines & StateFlow:** Gerenciamento assíncrono sem travar a interface.

## 📱 Como Instalar e Testar
1. O APK compilado encontra-se na raiz do projeto: `BTMicPro.apk`.
2. Transfira para o seu dispositivo Android (Testado no Cubot KingKong X Pro).
3. Conecte o seu intercomunicador ou fone Bluetooth.
4. Abra o app, dê as permissões necessárias (microfone, Bluetooth, notificações e ignorar otimização de bateria) e ative o **MOTO WHATSAPP MODE**.

## 🚧 Estrutura do Projeto
* `com.btmicpro.core`: Lógica de roteamento (`BluetoothAudioRouter`), keep-alive (`SilentAudioKeeper`) e otimização de bateria.
* `com.btmicpro.telecom`: Simulação de chamada fantasma (`FakeCallConnectionService`).
* `com.btmicpro.service`: Serviço de primeiro plano para manter o app vivo com notificação.
* `com.btmicpro.ui`: Tela única em Jetpack Compose, alto contraste (Dark Theme + Neon Green) e alvo de toque grande para luvas.
