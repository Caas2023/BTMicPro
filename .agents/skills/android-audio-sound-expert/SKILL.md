---
name: android-audio-sound-expert
description: "Expert guide on official Android Audio architecture, sound processing, Bluetooth SCO/HFP/LE Audio routing, DSP, low-latency audio, and third-party app compatibility (WhatsApp, GPS, calls)."
version: 1.0.0
priority: HIGH
---

# 🎧 Android Audio & Sound Engineering Expert (Melhores Práticas Oficiais)

Guia definitivo de engenharia e melhores práticas oficiais do Google Android (AOSP / Android Developers) para desenvolvimento de aplicativos de áudio, processamento de som, controle de volume, roteamento Bluetooth (SCO, HFP e LE Audio) e tratamento DSP.

---

## 🏛️ 1. Arquitetura do Subsistema de Áudio do Android

### 1.1. Modos de Áudio (`AudioManager.setMode`)
O modo de áudio informa ao subsistema de áudio (`AudioService` e `audioserver`) a natureza do áudio atual:

| Modo | Finalidade Oficial | Comportamento com Apps Terceiros (WhatsApp, etc.) |
|:---|:---|:---|
| `MODE_NORMAL` (0) | Áudio de mídia, reprodução comum e roteamento padrão. | **100% Compatível**. Microfone livre para gravação no WhatsApp. |
| `MODE_RINGTONE` (1) | Toque de chamada recebida. | Interrompe reproduções de mídia normais. |
| `MODE_IN_CALL` (2) | Chamada telefônica celular ativa (gerenciada pelo Modem/RIL). | Bloqueia gravação de áudio em mensageiros. |
| `MODE_IN_COMMUNICATION` (3) | Chamada VoIP ativa (WhatsApp Call, Meet, Discord). | ⚠️ **Cuidado crítico:** Bloqueia o envio de áudio no WhatsApp com o erro *"Não é possível gravar áudio durante chamada telefônica"*. Use com cautela ou mantenha `MODE_NORMAL` em apps de roteamento contínuo. |

> **Regra de Ouro para Roteadores de Microfone:** Para manter o microfone e fones do capacete prontos sem conflito com mensageiros, mantenha `audioManager.mode = AudioManager.MODE_NORMAL` e controle os fluxos via `setCommunicationDevice`.

---

## 📡 2. Roteamento de Dispositivos e Bluetooth (API 26 à 35)

### 2.1. Android 12+ (API 31 à 35) — API Moderna `CommunicationDevice`
Substitui as antigas chamadas de SCO e gerencia fones Bluetooth, capacetes e viva-voz:

```kotlin
val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

// 1. Descobrir dispositivos de comunicação disponíveis
val availableDevices = audioManager.availableCommunicationDevices
val btDevice = availableDevices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO }

// 2. Definir rota de comunicação ativa
if (btDevice != null) {
    val success = audioManager.setCommunicationDevice(btDevice)
    Log.d("AudioExpert", "setCommunicationDevice sucesso=$success")
}

// 3. Ouvir alterações na rota física (queda de conexão, desconexão de fone)
audioManager.addOnCommunicationDeviceChangedListener(
    context.mainExecutor,
    AudioManager.OnCommunicationDeviceChangedListener { device ->
        Log.i("AudioExpert", "Nova rota ativa: ${device?.productName} (Tipo=${device?.type})")
    }
)

// 4. Liberar rota quando o serviço for encerrado
audioManager.clearCommunicationDevice()
```

### 2.2. Suporte a Bluetooth LE Audio (API 33+ / Android 13+)
Fones e intercomunicadores modernos utilizam Bluetooth Low Energy Audio com codec LC3:
- Verificar tipos:
  - `AudioDeviceInfo.TYPE_BLE_HEADSET` (Fones e capacetes LE Audio)
  - `AudioDeviceInfo.TYPE_BLE_SPEAKER` (Caixas de som LE)
  - `AudioDeviceInfo.TYPE_BLE_BROADCAST` (Auracast)

### 2.3. Fallback Legado (Android 8 a 11 / API 26 a 30)
Para versões legadas, utilize o canal clássico de SCO com gerenciamento cuidadoso:
```kotlin
if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
    audioManager.startBluetoothSco()
    audioManager.isBluetoothScoOn = true
}
```

---

## 🎛️ 3. Efeitos de Áudio de Hardware (`android.media.audiofx`)

O Android oferece acesso aos blocos de processamento embutidos no DSP do processador (Qualcomm Snapdragon, MediaTek Dimensity, Exynos):

### 3.1. Efeitos Nativos Disponíveis
- **`NoiseSuppressor`**: Atenuação estática de ruído de fundo (vento contínuo, motor).
- **`AutomaticGainControl` (AGC)**: Nivela a voz do usuário para volume constante mesmo falando longe do microfone.
- **`AcousticEchoCanceler` (AEC)**: Elimina o eco gerado quando o som dos fones entra de volta no microfone.

### 3.2. Ciclo de Vida Obrigatório e Prevenção de Memory Leaks
```kotlin
class SafeAudioEffects(private val audioSessionId: Int) {
    private var noiseSuppressor: NoiseSuppressor? = null
    private var agc: AutomaticGainControl? = null
    private var aec: AcousticEchoCanceler? = null

    fun attach() {
        if (NoiseSuppressor.isAvailable()) {
            noiseSuppressor = NoiseSuppressor.create(audioSessionId)?.apply { enabled = true }
        }
        if (AutomaticGainControl.isAvailable()) {
            agc = AutomaticGainControl.create(audioSessionId)?.apply { enabled = true }
        }
        if (AcousticEchoCanceler.isAvailable()) {
            aec = AcousticEchoCanceler.create(audioSessionId)?.apply { enabled = true }
        }
    }

    fun release() {
        // OBRIGATÓRIO: Sem o release, o audioserver do Android retém o hardware,
        // gerando vazamento de memória e travamento no HAL.
        noiseSuppressor?.release(); noiseSuppressor = null
        agc?.release(); agc = null
        aec?.release(); aec = null
    }
}
```

---

## ⚡ 4. Processamento PCM em Tempo Real (Diretriz Zero-GC)

Quando processar áudio ao vivo com filtros digitais (DSP, BiQuad, passa-altas para corte de vento):

1. **Proibido alocar objetos no loop de streaming:**
   - ❌ **Incorreto:** Criar `val buffer = ShortArray(1024)` a cada iteração do loop `while(isRecording)`.
   - ✅ **Correto:** Pré-alocar um único buffer fixo antes do loop e reutilizá-lo (`audioRecord.read(fixedBuffer, 0, fixedBuffer.size)`).
2. **Corte de Graves para Vento (Filtro Passa-Altas / High-Pass):**
   - O ruído de vento de moto concentra-se entre 20 Hz e 300 Hz.
   - Um filtro passa-altas de 2ª ordem cortando abaixo de 250 Hz limpa 80% da turbulência sem afetar a inteligibilidade da voz (300 Hz a 3.4 kHz).
3. **Limite e Soft-Clipping:**
   - Sempre aplicar saturação suave (`tanh` ou corte de teto em `32767 / -32768`) para evitar estalos digitais quando o piloto gritar.

---

## 🔊 5. Gerenciamento de Volume Duplo e Prevenção de Feedback Loop

### 5.1. O Fenômeno de Oscilação Mecânica (Quantization Feedback)
- O fluxo de Mídia (`STREAM_MUSIC`) possui tipicamente de 15 a 25 passos inteiros.
- O fluxo de Chamada (`STREAM_VOICE_CALL`) possui de 5 a 7 passos inteiros.
- Sincronização reativa bidirecional via broadcast `ACTION_VOLUME_CHANGED` gera um loop de eco onde uma alteração em um fluxo recalcula o outro infinitamente.

### 5.2. Padrão de Solução Anti-Eco:
1. **Janela de Supressão Temporal (Debounce de 800ms):**
   Registrar `lastProgrammaticChangeTime = System.currentTimeMillis()`. Ignorar qualquer broadcast recebido dentro desse intervalo.
2. **Separação de Canais:**
   Permitir que o usuário ajuste Mídia (WhatsApp/GPS) e Chamada (Voz) de forma independente.
3. **Estado de Interface com Arredondamento Estável:**
   Utilizar `roundToInt()` na camada do Compose antes de disparar `setStreamVolume`.

---

## 🛡️ 6. Serviços em Primeiro Plano (Foreground Service) & Permissões

### 6.1. Android 14+ (API 34 e 35) Requisitos Estritos
No `AndroidManifest.xml`:
```xml
<uses-permission android:name="android.permission.RECORD_AUDIO" />
<uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
<uses-permission android:name="android.permission.MODIFY_AUDIO_SETTINGS" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MICROPHONE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE" />

<service
    android:name=".service.AudioService"
    android:exported="false"
    android:foregroundServiceType="microphone|connectedDevice" />
```

### 6.2. Notificação Contínua
Ao chamar `startForeground(NOTIFICATION_ID, notification)`:
- A notificação deve ser do canal com importância `IMPORTANCE_LOW` (sem som repetitivo).
- Deve conter flag `ongoing = true` para o Android não matar o processo em segundo plano.

---

## 📋 7. Checklist Rápido de Qualidade de Áudio

- [ ] `AudioManager.mode` está em `MODE_NORMAL` para evitar bloqueios no WhatsApp?
- [ ] O `setCommunicationDevice` é desfeito via `clearCommunicationDevice()` no `onDestroy`?
- [ ] Os efeitos `NoiseSuppressor`, `AGC` e `AEC` executam `.release()` ao fechar a gravação?
- [ ] Não há alocação de memória (`new` / coleções) dentro do loop de captura PCM?
- [ ] Os sliders de volume possuem supressão de eco para não oscilar sozinhos?
- [ ] O app funciona sem falhas tanto em fones Bluetooth SCO quanto LE Audio?
