# 🏍️ BT MIC PRO V4 — ARQUITETURA DEFINITIVA DE ROTEAMENTO DE ÁUDIO

## Roteamento Bidirecional WhatsApp ↔ Intercom Bluetooth
**Dispositivo Primário de Referência:** Cubot KingKong X Pro (MediaTek Dimensity 8200, Android 14 API 34)

---

## 1. Visão Geral e Papel do Sistema

O **BT Mic Pro** não é um gravador de áudio, não cria arquivos intermediários e não intercepta mensagens. Ele atua estritamente no **plano de controle** como:

> **CONTROLADOR E ESTABILIZADOR DA ROTA DE ÁUDIO DE COMUNICAÇÃO BLUETOOTH**

```text
                  WHATSAPP
                ↙       ↘
          CAPTURA       PLAYBACK
             ↓             ↑
          ANDROID AUDIO ROUTING
             ↓             ↑
          BLUETOOTH HFP/SCO
             ↓             ↑
             INTERCOM
             ↓             ↑
       MICROFONE / FONE DO CAPACETE
```

O BT Mic Pro fica no plano de controle:

```text
                  BT MIC PRO (PLANO DE CONTROLE)
                                │
          ┌─────────────────────┼─────────────────────┐
          ↓                     ↓                     ↓
       SELECT                MONITOR               RECOVER
  (Communication         (AudioDeviceCallback,   (Exponential Backoff
   Device Manager)        Mode, DeviceChange)      Auto-Healing)
          │                     │                     │
          └─────────────────────┼─────────────────────┘
                                ↓
                      AUDIO COMMUNICATION
                                ↓
                             WHATSAPP
```

---

## 2. Fluxos de Áudio Bidirecionais

### 2.1. Entrada (Usuário Fala)
```text
MICROFONE DO INTERCOMUNICADOR
        ↓
Bluetooth HFP / SCO (mSBC 16.000 Hz)
        ↓
Android Communication Input
        ↓
WHATSAPP (AudioRecord nativo do WhatsApp)
        ↓
Mensagem de Voz Gravada pelo WhatsApp
```

### 2.2. Saída (Usuário Escuta)
```text
WHATSAPP (AudioTrack nativo do WhatsApp)
        ↓
Android Communication Output
        ↓
Bluetooth HFP / SCO / A2DP
        ↓
INTERCOMUNICADOR
        ↓
FONE / ALTO-FALANTE DO CAPACETE
```

### 2.3. Simultaneidade Bidirecional
O sistema opera em modo de comunicação bidirecional contínua (`CommunicationRoute`), onde a rota só é classificada como `ROUTE_READY` quando tanto a **Entrada Bluetooth** quanto a **Saída Bluetooth** estão ativas e vinculadas ao intercomunicador.

---

## 3. Arquitetura Modular V4

```text
com.btmicpro.core
├── BluetoothRoutingEngine.kt       # Autoridade única e orquestrador do ciclo de vida
├── CommunicationDeviceManager.kt   # API moderna setCommunicationDevice / clearCommunicationDevice
├── AudioRouteMonitor.kt            # Monitor de eventos (AudioDeviceCallback, Mode, CommDevice)
├── RoutingRecoveryManager.kt       # Autorrecuperação com retries e backoff exponencial
├── DeviceCompatibilityManager.kt   # Perfis de hardware (Cubot KingKong X Pro e Genérico)
├── AudioDiagnostics.kt             # Modelo de telemetria completa e exportadores TXT/JSON
├── RouterState.kt                  # Máquina de estados finita de 10 estados
└── RouterStateHolder.kt            # Singleton reativo para sincronização entre Service e UI
```

---

## 4. Máquina de Estados de 10 Estágios

A máquina de estados finita segue rigorosamente o ciclo de vida:

1. **`DISCONNECTED`**: Nenhum intercomunicador conectado ou serviço inativo.
2. **`BLUETOOTH_CONNECTED`**: Conexão ACL/HFP estabelecida com o fone/intercom.
3. **`COMMUNICATION_DEVICE_AVAILABLE`**: O sistema Android indexou o dispositivo como canal de comunicação.
4. **`COMMUNICATION_DEVICE_SELECTED`**: Invocado `setCommunicationDevice` e confirmado pelo AudioManager.
5. **`INPUT_AVAILABLE`**: Microfone Bluetooth SCO/BLE detectado nos dispositivos de entrada do sistema.
6. **`OUTPUT_AVAILABLE`**: Alto-falante do fone Bluetooth detectado nos dispositivos de saída do sistema.
7. **`ROUTE_READY`**: Rota bidirecional pronta e estável (Entrada + Saída + CommDevice confirmados).
8. **`ROUTE_LOST`**: Perda inesperada de rota ou desconexão física.
9. **`RECOVERING`**: Sequência de autorrecuperação com retries (backoff de 600ms, 1200ms, 2400ms, 3500ms).
10. **`ERROR`**: Falha irrecuperável reportada ao usuário.

---

## 5. Auditoria de Código Legado e Eliminação de Conflitos

Conforme classificado na auditoria V4:
- **`FakeCallConnectionService` & `TelecomHelper`**: `REMOVIDOS`. Simular chamada de voz no Telecom bloqueava gravações no WhatsApp ("cannot record during call").
- **`SilentAudioKeeper`**: `EXPERIMENTAL / OPCIONAL`. Desligado por padrão, com switch disponível na interface para testes comparativos no hardware.
- **`AudioEffects`**: `LOCAL_ONLY`. Subclasses de `AudioEffect` só processam sessões locais do app; documentado explicitamente que não interceptam o WhatsApp.
- **`VoiceProcessingEngine`**: `SEPARATE_RESOURCE`. Focado no teste de voz ao vivo do motociclista (`LiveAudioMonitor`), sem disputar áudio com o WhatsApp.

---

## 6. Otimizações para o Cubot KingKong X Pro

- **Chipset:** MediaTek Dimensity 8200 (Octa-core 3.1 GHz).
- **Taxa de Amostragem:** 16.000 Hz nativo (mSBC Wideband Speech).
- **Multiplicador de Buffers:** 2x para proteção contra buffer underruns no driver de áudio da MediaTek.
- **Proteção do Foreground Service:** Configurado com `FOREGROUND_SERVICE_TYPE_MICROPHONE` e `FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE` para imunidade ao DuraSpeed e economia de bateria profunda.

---

## 7. Diagnóstico e Telemetria

O modal **Developer Audio Diagnostics** permite visualizar em tempo real e exportar em **TXT** ou **JSON**:
- Fabricante, Modelo, Versão do Android, SDK e Build.
- Nome e perfil do dispositivo Bluetooth.
- Dispositivo de comunicação selecionado.
- Lista completa de entradas e saídas de áudio indexadas.
- Estado do SCO e Codec (mSBC / CVSD).
- Estado atual da rota na máquina de estados.
- Status do WhatsApp (`ROUTE_PREPARED`, `USER_VALIDATED`, etc.).
