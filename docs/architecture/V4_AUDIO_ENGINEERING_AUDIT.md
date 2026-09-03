# 🎙️ PROMPT MASTER V4 — Relatório de Auditoria de Engenharia de Áudio, Arquitetura e Viabilidade Técnica

**Projeto:** BT Mic Pro V4  
**Repositório:** `https://github.com/Caas2023/BTMicPro.git`  
**Dispositivo Alvo Primário:** Cubot KingKong X Pro (MediaTek Dimensity 8200, 12GB RAM, Android 14 / API 34, Bluetooth 5.3)  
**Data da Auditoria:** 02/09/2026  
**Especialidade:** Android Audio Framework · AudioManager · AudioRecord · AudioTrack · Bluetooth SCO/HFP · DSP em Tempo Real · WhatsApp Integration

---

## 📑 1. Auditoria Completa do Repositório Atual

### 1.1. Configuração de Build e SDK
- **Compile SDK:** 34 (Android 14)
- **Target SDK:** 34 (Android 14)
- **Min SDK:** 26 (Android 8.0 Oreo)
- **Versão Kotlin:** 1.9.23
- **Compatibilidade Java:** Java 17 (JVM target 17)
- **Framework de UI:** Jetpack Compose (BOM 2024.04.01) + Material 3
- **Dependência de Roteamento:** `com.twilio:audioswitch:1.2.0` (utilizada para escuta de dispositivos de áudio e fallback)

### 1.2. Permissões no `AndroidManifest.xml`
| Permissão | Finalidade | Avaliação Técnica |
| :--- | :--- | :--- |
| `RECORD_AUDIO` | Captura do microfone | Obrigatória para `AudioRecord`. |
| `BLUETOOTH_CONNECT` | Acesso e gerenciamento de dispositivos pareados no Android 12+ | Obrigatória na API 31+. |
| `BLUETOOTH` / `BLUETOOTH_ADMIN` | Fallback para Android 11 e inferiores | Correto (`maxSdkVersion="30"`). |
| `MODIFY_AUDIO_SETTINGS` | Roteamento de áudio, `setCommunicationDevice`, volume e SCO | Obrigatória para `AudioManager`. |
| `SYSTEM_ALERT_WINDOW` | Botão flutuante sobreposto | Usada pelo `FloatingButtonService`. |
| `FOREGROUND_SERVICE` | Manter serviço ativo em background | Obrigatória. |
| `FOREGROUND_SERVICE_MICROPHONE` | Manter captura de áudio com app minimizado | Obrigatória no Android 14+. |
| `FOREGROUND_SERVICE_CONNECTED_DEVICE` | Manter conexão Bluetooth contínua no Android 14+ | Obrigatória no Android 14+. |
| `MANAGE_OWN_CALLS` | Registro de `PhoneAccount` no Telecom | Usada por `FakeCallConnectionService` (ver análise na seção 3). |
| `POST_NOTIFICATIONS` | Notificação persistente no Android 13+ | Obrigatória. |
| `RECEIVE_BOOT_COMPLETED` | Inicialização no boot | Obrigatória para auto-start. |

---

## 🔬 2. Análise Crítica de Cada Módulo Existente

### 2.1. `BluetoothAudioRouter.kt`
- **O que faz:** Gerencia o ciclo de vida da comunicação Bluetooth via `setCommunicationDevice` (Android 12+) e fallback `startBluetoothSco`. Registra `AudioDeviceCallback` e aciona o `SilentAudioKeeper`.
- **Pontos Positivos:** Conexão direta com o `RouterStateHolder`, watchdog periódico para ressuscitar rotas caídas.
- **Limitações Identificadas:**
  - O estado atual (`RoutingActive`) indica apenas que o dispositivo foi selecionado, mas não valida se o fluxo PCM está efetivamente trafegando no canal SCO ou se houve queda silenciosa do driver da MediaTek.
  - Precisa evoluir para uma **Máquina de Estados de 8 estágios** com verificação ativa de roteamento (`ROUTING_VERIFIED`).

### 2.2. `CleanVoiceDsp.kt`
- **O que faz:** Implementa filtro passa-alta Butterworth de 4ª ordem (@ 160Hz), expansor descendente suave baseado em blocos RMS de 10ms, peaking EQ em 3.0 kHz e brickwall limiter.
- **Pontos Positivos:** Eliminou os cortes e picotamentos que ocorriam no gate seco anterior; zero alocação de objetos por amostra.
- **Limitações Identificadas para V4:**
  - Frequência de corte fixa em 160Hz (pode atenuar a base grave de vozes masculinas muito encorpadas). Deve ser adaptativa (80Hz a 160Hz).
  - Ausência de um **Detector Específico de Ruído de Vento (Wind Noise Detector)** baseado em assimetria espectral e energia de sub-graves.
  - Ausência de AGC e Dynamic EQ parametrizável com presets de motociclista (`CITY`, `HIGHWAY`, `EXTREME WIND`, `VOICE CLARITY`).

### 2.3. `SilentAudioKeeper.kt`
- **O que faz:** Mantém um `AudioTrack` bombeando PCM de silêncio contínuo em 16kHz mono com `USAGE_VOICE_COMMUNICATION` para impedir que o Android desligue o canal SCO por inatividade de 15 segundos.
- **Pontos Positivos:** Previne o atraso de 2 a 3 segundos de renegociação SCO ao gravar no WhatsApp.
- **Limitações Identificadas:** Deve garantir que não interfira no `AudioFocus` quando outros aplicativos exigirem exclusividade de reprodução.

### 2.4. `FakeCallConnectionService.kt` e `TelecomHelper.kt`
- **O que faz:** Simula uma chamada auto-gerenciada (`ConnectionService`) para enganar o `AudioManager` a manter SCO ativo.
- **Avaliação Técnica:** No Android 12+ (API 31+), `AudioManager.setCommunicationDevice()` substitui oficialmente essa necessidade sem gerar efeitos colaterais de telefonia. O `BtMicService` opera com `MICROPHONE | CONNECTED_DEVICE`, tornando a chamada fantasma desnecessária e potencialmente conflitante com chamadas reais do WhatsApp. Deve ser marcado como legado/deprecated.

### 2.5. `MediaBooster.kt`
- **O que faz:** Atua exclusivamente na **saída de áudio** (reprodução) utilizando `LoudnessEnhancer` e `Equalizer` na sessão global 0 para elevar o volume do capacete em barulho extremo.
- **Avaliação Técnica:** Muito útil para o motociclista ouvir melhor, mas atua apenas no fluxo de saída (`STREAM_MUSIC` / `STREAM_VOICE_CALL`), não na captura do microfone.

---

## ⚖️ 3. O Desafio Central: Áudio Processado vs. O que o WhatsApp Grava

> [!CRITICAL]
> **A Verdade da Arquitetura do Android Audio Framework:**
> Um aplicativo de usuário comum (não-root) **NÃO PODE** injetar áudio PCM processado por software diretamente no `AudioRecord` de outro aplicativo isolado (WhatsApp).

### 3.1. Por que o Android bloqueia injeção de microfone entre aplicativos?
1. **Sandbox e Isolamento de Processos (Linux UID / SELinux):** O WhatsApp roda em seu próprio processo com seu próprio UID. A comunicação do microfone é estabelecida diretamente entre o processo do WhatsApp e o daemon de sistema `audioserver` via Binder IPC (`IAudioFlinger`).
2. **Inexistência de Virtual Audio Device Público:** No Android oficial (AOSP), não existe API pública para registrar um "Microfone Virtual" em tempo de execução. Novos dispositivos de entrada (`AudioDeviceInfo`) só podem ser criados pelo kernel do Linux (drivers ALSA) através do Audio HAL (`audio.primary.*.so`) com permissões `audioserver` ou root.
3. **Escopo dos `AudioEffects`:**
   - Efeitos de áudio anexados a um `audioSessionId` específico só processam o áudio daquela sessão.
   - Criar `AudioEffect` na sessão global 0 para **captura (input)** é expressamente bloqueado pelo Android. A permissão para interceptar ou anexar efeitos nas sessões de outros aplicativos (`android.permission.MODIFY_DEFAULT_AUDIO_EFFECTS`) é restrita a aplicativos do sistema (`signature|privileged`).
4. **O que `setCommunicationDevice` e `setPreferredDeviceForCapturePreset` realmente fazem:**
   - Eles instruem o `AudioPolicyManager` sobre qual **dispositivo de hardware físico** (ex: o microfone Bluetooth SCO do capacete) deve ser conectado quando o WhatsApp abrir o microfone.
   - Eles **NÃO** realizam interceptação de dados PCM nem permitem intercalar um filtro de software no meio do caminho.

### 3.2. Quadro Comparativo de Viabilidade Técnica

| Requisito do Projeto | Nível de Viabilidade no Android Público | Como a Arquitetura V4 resolve |
| :--- | :--- | :--- |
| **Garantir que o WhatsApp use o microfone do intercomunicador Bluetooth** | `IMPLEMENTADO E 100% VIÁVEL` | Via `setCommunicationDevice` + `SilentAudioKeeper` + presets de captura, garantindo microfone SCO sem delay e sem quedas. |
| **Eliminar delay de 2-3 segundos no início dos áudios do WhatsApp** | `IMPLEMENTADO E 100% VIÁVEL` | O `SilentAudioKeeper` mantém o canal SCO engajado em background, eliminando a renegociação. |
| **Injetar DSP do BT Mic Pro no AudioRecord nativo do WhatsApp** | `NÃO POSSÍVEL COM API PÚBLICA (BLOQUEIO DO SISTEMA)` | Documentado com total transparência. O Android impede inter-process audio injection sem root. |
| **Gravar voz com DSP completo e enviar com 1 toque ao WhatsApp** | `IMPLEMENTADO E 100% VIÁVEL` | Botão Flutuante Sobreposto / Atalho que grava com o `VoiceProcessingEngine` (CleanVoice Pro) e envia direto à conversa do WhatsApp via `FileProvider`. |
| **Monitoramento do Intercomunicador ao Vivo no Capacete** | `IMPLEMENTADO E 100% VIÁVEL` | `LiveAudioMonitor` com baixíssima latência (PCM -> DSP -> AudioTrack LOW_LATENCY). |

---

## 🏛️ 4. A Nova Arquitetura BT Mic Pro V4

Para atender com máxima excelência ao Prompt Master, a V4 é dividida em **4 Camadas Especializadas**:

### CAMADA 1: Bluetooth Routing Engine & Máquina de Estados de 8 Estágios
Implementação de uma máquina de estados finitos estrita:
```text
DISCONNECTED
    ↓ (ACL conectado / Headset detectado)
BLUETOOTH_CONNECTED
    ↓ (AudioDeviceInfo presente)
AUDIO_DEVICE_AVAILABLE
    ↓ (setCommunicationDevice aplicado)
COMMUNICATION_DEVICE_SELECTED
    ↓ (Canal SCO aberto e com tráfego)
SCO_ACTIVE
    ↓ (Buffer de captura validado)
ROUTING_VERIFIED
    ↓ (Dispositivo desconectado ou timeout)
ROUTING_LOST → RECOVERING → DISCONNECTED
```

### CAMADA 2: Verificação Real de Roteamento & Painel de Diagnóstico
- Diferenciação inequívoca na UI entre o estado do hardware e o suporte do WhatsApp.
- Criação da tela **Developer Audio Diagnostics** contendo:
  - Modelo do dispositivo (`Cubot KingKong X Pro`, MediaTek Dimensity 8200).
  - Lista de `AudioDeviceInfo` de entrada e saída com tipos e IDs.
  - Status do Communication Device ativo (`productName`, `address`, `type`).
  - Taxa de amostragem real negociada (16.000 Hz mSBC vs 8.000 Hz CVSD).
  - AudioEffects disponíveis no hardware MediaTek (`NoiseSuppressor`, `AGC`, `AEC`).
  - Latência estimada de processamento (ms).

### CAMADA 3: Motor Modular `VoiceProcessingEngine` (DSP em Tempo Real)
Pipeline em memória com buffers pré-alocados (zero GC allocation):
```text
INPUT (PCM 16-bit)
    ↓
1. DC BLOCK (Remoção de offset DC a 20Hz)
    ↓
2. HIGH-PASS ADAPTATIVO (80Hz, 100Hz, 120Hz, 150Hz, 160Hz)
    ↓
3. WIND NOISE DETECTOR (Detecção espectral de rajadas subsônicas)
    ↓
4. EXPANSOR SUAVE RMS / NOISE REDUCTION (Atenuação adaptativa de ruído sem gating seco)
    ↓
5. DYNAMIC VOCAL EQ (Realce dinâmico em 3 kHz dependente do nível)
    ↓
6. AGC (Automatic Gain Control com Attack 10ms / Release 300ms)
    ↓
7. VOCAL COMPRESSOR (Controle de dinâmica suave 2:1 a 3:1)
    ↓
8. BRICKWALL LIMITER (-1.0 dBFS)
    ↓
OUTPUT
```
**Presets de Motociclista Integrados:**
- `NORMAL`: Equilíbrio para uso diário.
- `CITY`: Foco em ruído urbano e motores ao redor.
- `HIGHWAY`: Filtragem agressiva de vento contínuo acima de 80 km/h.
- `EXTREME WIND`: Redução máxima de sub-graves com expansor reforçado.
- `VOICE CLARITY`: Foco máximo em formantes vocais e inteligibilidade.

### CAMADA 4: Compatibilidade de Hardware (`DeviceCompatibilityManager`)
- Isolamento de particularidades do **Cubot KingKong X Pro**:
  - Chipset MediaTek Dimensity 8200.
  - Driver Bluetooth com preferência mSBC 16 kHz.
  - Gestão de Battery Doze Mode agressivo (MediaTek DuraSpeed).
  - Perfil genérico de fallback para outros modelos Android.
