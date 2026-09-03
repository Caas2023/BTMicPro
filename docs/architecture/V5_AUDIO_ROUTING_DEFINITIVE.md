# 🎧 Arquitetura V5 Definitiva — Roteador & Estabilizador de Áudio Bluetooth

## BT Mic Pro — Sistema Especialista para Motociclistas & Intercomunicadores
- **Aparelho Alvo**: Cubot KingKong X Pro (MediaTek Dimensity 8200, Android 14/15, API 34/35)
- **Versão**: 1.5.0 (Build 18) — V5 Definitiva

---

## 1. Princípio Fundamental de Operação
O **BT Mic Pro** opera exclusivamente no **plano de controle** da rota de comunicação do sistema Android.
- **NÃO é um gravador intermediário** no Modo WhatsApp.
- **NÃO produz arquivos temporários** para enviar ao WhatsApp.
- **NÃO simula injeção de PCM** entre processos sem API oficial ou root.
- **NÃO bloqueia o hardware de áudio**: O WhatsApp continua sendo 100% responsável por abrir sua própria sessão de `AudioRecord`, codificar, enviar, receber e reproduzir o áudio.
- O BT Mic Pro atua como a **autoridade central** que seleciona e estabiliza a rota de comunicação Bluetooth (HFP/SCO) no nível de sistema, garantindo bidirecionalidade contínua (Microfone do capacete como entrada e Fones do capacete como saída).

---

## 2. Camadas Arquiteturais e Responsabilidades

```
┌─────────────────────────────────────────────────────────────┐
│                      Camada UI / UX                         │
│   MainScreen.kt • MainViewModel.kt • AudioDiagnosticsDialog │
└──────────────────────────────┬──────────────────────────────┘
                               │ (StateFlow / Coroutines)
┌──────────────────────────────▼──────────────────────────────┐
│       Camada de Serviço (Foreground Service / Background)   │
│           BtMicService.kt (Autoridade de FGS)               │
└──────────────────────────────┬──────────────────────────────┘
                               │
┌──────────────────────────────▼──────────────────────────────┐
│  Camada A — Autoridade Central de Roteamento (V5 Engine)    │
│            BluetoothRoutingEngine.kt (Mutex Serializado)    │
└──────┬──────────────────────┬──────────────────────┬────────┘
       │                      │                      │
┌──────▼──────┐       ┌───────▼──────┐       ┌───────▼────────┐
│  Camada B   │       │   Camada C   │       │   Camada D     │
│  Headset &  │       │ Communication│       │ Observabilidade│
│  HFP State  │       │ Device & SCO │       │ Snapshots/Diff │
│ BluetoothHfp│       │ Communication│       │ AudioRoute     │
│  Manager.kt │       │ DeviceManager│       │   Monitor.kt   │
└─────────────┘       └──────────────┘       └────────────────┘
       │                      │                      │
┌──────▼──────────────────────▼──────────────────────▼────────┐
│               Camada E — Resiliência & Diagnóstico          │
│   RoutingRecoveryManager.kt • AudioDiagnostics.kt           │
│   DeviceCompatibilityManager.kt (CubotKingKongXProProfile)  │
└─────────────────────────────────────────────────────────────┘
```

---

## 3. Máquina de Estados de 13 Estágios Estritos

A autoridade central transita rigorosamente pelos 13 estágios canônicos:

1. **`DISCONNECTED`**: Nenhum intercom conectado ou Bluetooth desativado.
2. **`BLUETOOTH_CONNECTED`**: Conexão ACL estabelecida com o intercomunicador Bluetooth.
3. **`COMMUNICATION_DEVICE_AVAILABLE`**: `AudioDeviceInfo` correspondente identificado pelo subsistema de áudio.
4. **`COMMUNICATION_DEVICE_SELECTED`**: `setCommunicationDevice()` invocado com sucesso (apenas dispositivos onde `isSink == true`).
5. **`AUDIO_CONNECTING`**: Broadcast `ACTION_AUDIO_STATE_CHANGED` emitindo `STATE_AUDIO_CONNECTING`.
6. **`AUDIO_CONNECTED`**: Broadcast `ACTION_AUDIO_STATE_CHANGED` confirmando `STATE_AUDIO_CONNECTED`.
7. **`INPUT_AVAILABLE`**: Dispositivo de entrada Bluetooth (SCO Headset Mic) detectado na lista de inputs.
8. **`OUTPUT_AVAILABLE`**: Dispositivo de saída Bluetooth (SCO Headset Earphone) ativo como sink de comunicação.
9. **`ROUTE_READY`**: Ambos canais (Mic e Fone) ativos e confirmados para uso imediato pelo WhatsApp.
10. **`ROUTE_DEGRADED`**: Rota parcialmente comprometida (apenas saída ou entrada ativa).
11. **`ROUTE_LOST`**: Rota perdida por desconexão ou intervenção de outro aplicativo.
12. **`RECOVERING`**: Recuperação automática serializada com backoff exponencial.
13. **`ERROR`**: Falha terminal que requer intervenção manual do usuário.

---

## 4. Status de Verificação do WhatsApp

Como o WhatsApp roda em sua própria sandbox de segurança com `Process.myUid()` isolado, o app reporta seu status de forma honesta e transparente:

- `UNKNOWN`: Estado inicial ainda não avaliado.
- `ROUTE_PREPARED`: Rota de comunicação do Android foi selecionada e confirmada pelo BT Mic Pro.
- `USER_VALIDATED`: Usuário gravou e ouviu um áudio no WhatsApp pelo intercom e confirmou na UI.
- `NOT_DIRECTLY_VERIFIABLE`: O Android restringe a inspeção de sessões de áudio de terceiros sem permissões de sistema ou root.
- `FAILED`: Rota não pôde ser ativada ou foi rejeitada pelo subsistema de áudio.

---

## 5. Correção de Falsidades Técnicas

1. **Codec SCO**:
   - **Anterior**: `if (sampleRate == 16000) codec = "mSBC"`.
   - **V5**: Reporta `"NOT_EXPOSED"` se a API pública do SO não disponibilizar o codec real negociado pelo hardware Bluetooth.
2. **Latência**:
   - **Anterior**: Textos prometendo "15ms" ou "ZERO LATÊNCIA".
   - **V5**: Utiliza métricas reais:
     - `routePreparationTimeMs`: Tempo cronometrado desde o início da conexão até `ROUTE_READY`.
     - `audioBufferEstimateMs`: Estimativa de buffer baseada em sample rate e frames.
     - `processingTimeMs`: Tempo de execução interno.
     - `endToEndLatency`: `"NOT_MEASURED"` (já que o percurso acústico total envolve o stack do WhatsApp e redes celulares).
3. **Dispositivo de Saída em `setCommunicationDevice`**:
   - A API do Android 12+ exige estritamente que o dispositivo passado seja um **SINK** (`isSink == true`). O app filtra `availableCommunicationDevices` garantindo conformidade total.

---

## 6. Otimizações para o Cubot KingKong X Pro
- **MediaTek Dimensity 8200 & DuraSpeed**: O perfil `CubotKingKongXProProfile` possui flags para alertar o usuário sobre as restrições do DuraSpeed do MediaTek e otimiza o buffer SCO para evitar pops de áudio sob ruído intenso de motocicleta.
