# AUDITORIA COMPLETA DO SISTEMA - BT Mic Pro v1.1.5 (code 15)
**Data:** 02/09/2026  
**Build:** Debug APK 18.8 MB  
**Dispositivo Alvo:** Cubot KingKong X Pro (MediaTek Dimensity 8200, BT 5.3, Android 14 API 34)  
**Intercom Alvo:** WAYXIN R6S (HFP Classic mSBC 16kHz)

---

## 1. ARQUITETURA GERAL

### 1.1 Stack Tecnológica
| Componente | Versão/Detalhes |
|------------|-----------------|
| Language | Kotlin 2.0 + Compose |
| Build | Gradle KTS, compileSdk 34, minSdk 26, targetSdk 34 |
| JDK | 17 |
| Dependências Core | Twilio AudioSwitch, Coroutines, Material3 |
| DSP Áudio | High-Pass 120Hz + Gate + Limiter (software) + MediaTek NS/AGC/AEC (hardware) |
| Volume Boost | LoudnessEnhancer (sessão 0, até +8dB) + Equalizer (1-3kHz +4dB) |

### 1.2 Estrutura de Módulos
```
app/
├── core/                    # Lógica de negócio pura
│   ├── BluetoothAudioRouter.kt   # Roteamento A2DP/SCO dinâmico (V2.8)
│   ├── AudioCaptureEngine.kt     # Captura + DSP (sem IA)
│   ├── MediaBooster.kt           # MODO BAR - LoudnessEnhancer + EQ
│   ├── AudioEffectController.kt  # Efeitos Android (NS/AGC/AEC)
│   ├── AudioFileManager.kt       # Gravação WAV + compartilhamento
│   ├── RecordingState.kt         # Estados sealed
│   └── RouterState.kt            # Estados sealed
├── service/
│   ├── BtMicService.kt           # Foreground Service (mic + BT)
│   ├── RecordingService.kt       # Foreground Service (gravação)
│   └── FloatingButtonService.kt  # Overlay opcional (78dp)
├── receiver/
│   ├── BootReceiver.kt           # BOOT_COMPLETED + LOCKED_BOOT_COMPLETED
│   └── BluetoothAutoStartReceiver.kt  # Auto-início ao conectar headset
├── ui/
│   ├── MainActivity.kt           # Entry point Compose
│   ├── MainScreen.kt             # UI principal (v1.1.5)
│   ├── MainViewModel.kt          # Estado + lógica de apresentação
│   └── theme/                    # Cores neon (PrimaryNeon, AccentRed, WarningAmber)
├── telecom/
│   └── FakeCallConnectionService.kt  # Legacy (não usado ativo)
└── BtMicProApp.kt                # Application class
```

---

## 2. FUNCIONALIDADES CORE AUDITADAS

### 2.1 Roteamento de Áudio Bluetooth (BluetoothAudioRouter.kt) ✅
**Status:** PRODUÇÃO - V2.8 estável

| Aspecto | Implementação | Detalhes |
|---------|---------------|----------|
| **Estratégia** | SCO on-demand via `OnModeChangedListener` | API 31+ nativo |
| **Gravação (WhatsApp)** | `setCommunicationDevice` + `setPreferredDeviceForCapturePreset` reflection | Só ativa BT mic quando `MODE_IN_COMMUNICATION` |
| **Playback (A2DP)** | `clearCommunicationDevice()` + `clearPreferredDeviceForCapturePreset` | Limpa ao voltar `MODE_NORMAL` |
| **Watchdog** | 30s interval | Verifica estado só em Waiting/Error |
| **AudioSwitch** | Twilio para seleção automática | Reconexão automática |
| **Volume** | STREAM_VOICE_CALL + STREAM_MUSIC no max | Aplicado no start |

**Fixes Críticos Aplicados:**
- ✅ WhatsApp "cannot record during call" - removido FakeCallConnectionService ativo
- ✅ KingKong X Pro muting A2DP - fixed com SCO on-demand (só durante gravação)
- ✅ Mic cortando 10-15s - watchdog 5s → 30s

### 2.2 Captura de Áudio + DSP (AudioCaptureEngine.kt) ✅
**Status:** PRODUÇÃO - Sem IA (removida)

| Parâmetro | Valor | Justificativa |
|-----------|-------|---------------|
| Sample Rate (SCO) | 16kHz | mSBC nativo do HFP |
| Sample Rate (A2DP/Outros) | 48kHz | Qualidade máxima |
| High-Pass Filter | 120Hz (alpha adaptativo) | Corta ruído vento/motor |
| Noise Gate | Threshold 180-580 (denoise 0.85) | Suprime ruído abaixo limiar |
| Limiter | Teto 28000, knee 0.25 | Evita clipping sem distorcer |
| Raw Audio Mode | AudioSource.MIC (9) bypass DSP | Para vento extremo |
| Formato Saída | WAV 16-bit PCM mono | Compatível WhatsApp |

**Nota:** IA denoisers (RNNoise/DeepFilterNet) **removidos** - não funcionam no WhatsApp direto (o app ignora buffer processado).

### 2.3 MODO BAR - Aumentador de Mídia (MediaBooster.kt) ✅
**Status:** PRODUÇÃO - Ativo global (sessão 0)

| Componente | Configuração | Efeito |
|------------|--------------|--------|
| LoudnessEnhancer | 0-8000 mB (0 a +8dB) | Compressor inteligente aumenta voz |
| Equalizer | Bandas 1-3kHz +4dB | Inteligibilidade voz em bar |
| Equalizer | <300Hz -3dB | Corta grave ruído ambiente |
| Volume | STREAM_MUSIC + STREAM_VOICE_CALL max | Hardware gain |
| Sessão | 0 (global) | Afeta TODA mídia (WhatsApp, YouTube, Telegram) |

**Slider UI:** 0% (normal) → 100% (+8dB max)

### 2.4 Botão Flutuante (FloatingButtonService.kt) ✅
**Status:** PRODUÇÃO - Opcional

| Especificação | Valor |
|---------------|-------|
| Tamanho | 78dp (30% menor que 112dp anterior) |
| Formato | Circular com borda branca 3dp |
| Estados | TODO VERDE (HeadsetMic) / TODO VERMELHO (Power) |
| Arrastável | Sim (drag com threshold 10px) |
| Ciclo de vida | SavedStateRegistryOwner correto (não crasha) |
| Permissão | SYSTEM_ALERT_WINDOW (verificado runtime) |
| Integração | Toggle sincroniza com MainViewModel + prefs |

### 2.5 Auto-Start & Boot (BootReceiver + BluetoothAutoStartReceiver) ✅
| Receiver | Triggers | Ação |
|----------|----------|------|
| BootReceiver | `BOOT_COMPLETED`, `LOCKED_BOOT_COMPLETED`, `MY_PACKAGE_REPLACED` | Inicia BtMicService se `auto_start=true` |
| BluetoothAutoStartReceiver | `ACL_CONNECTED`, `HEADSET_PROFILE_CONNECTION_STATE_CHANGED`, `SCO_AUDIO_STATE_CHANGED` | Auto-inicia quando headset conecta |

### 2.6 Promo Carousel ✅
| Parâmetro | Valor |
|-----------|-------|
| Itens | 5 banners (Capa Chuva, Kit Relação, Capacetes, Intercomunicador, Pneus) |
| Cooldown | 5 horas após fechar |
| Limite/dia | 5 exibições |
| Animação | Blink neon + crossfade 4s |
| Ação | Abre link Shopee ao clicar |

### 2.7 UI Principal (MainScreen.kt v1.1.5) ✅
**Layout Atualizado:**
- Botão principal: **144dp** (reduzido 20% de 180dp)
- Toggles RAW AUDIO / AUTO INICIAR: **lado a lado** (Row weighted)
- Botão Flutuante: **compacto** (só label + switch)
- MODO BAR: Card expansível com slider
- Promo: Carrossel auto-rotativo

---

## 3. PERMISSÕES E MANIFEST

### 3.1 Permissões Declaradas
```xml
<!-- Áudio -->
<uses-permission android:name="android.permission.RECORD_AUDIO" />
<uses-permission android:name="android.permission.MODIFY_AUDIO_SETTINGS" />

<!-- Bluetooth -->
<uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
<uses-permission android:name="android.permission.BLUETOOTH" android:maxSdkVersion="30" />
<uses-permission android:name="android.permission.BLUETOOTH_ADMIN" android:maxSdkVersion="30" />

<!-- Overlay -->
<uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />

<!-- Foreground Services -->
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MICROPHONE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE" />

<!-- Telecom (legacy) -->
<uses-permission android:name="android.permission.MANAGE_OWN_CALLS" />

<!-- Notificações -->
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />

<!-- Boot -->
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />

<!-- Storage legacy -->
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" android:maxSdkVersion="28" />
```

### 3.2 Serviços Registrados
| Serviço | Tipo | Foreground Type |
|---------|------|-----------------|
| BtMicService | Foreground | microphone + connectedDevice |
| RecordingService | Foreground | microphone |
| FloatingButtonService | Overlay | N/A |
| FakeCallConnectionService | Telecom | N/A (legacy) |

### 3.3 Receivers
| Receiver | Exported | Actions |
|----------|----------|---------|
| BootReceiver | true | BOOT_COMPLETED, LOCKED_BOOT_COMPLETED, MY_PACKAGE_REPLACED |
| BluetoothAutoStartReceiver | true | ACL_CONNECTED, HEADSET_PROFILE, SCO_AUDIO_STATE_CHANGED |

### 3.4 FileProvider
- Authority: `${applicationId}.fileprovider`
- Paths: `xml/file_paths` (compartilhamento WhatsApp)

---

## 4. ESTADO DO BUILD

### 4.1 Versões
| Arquivo | VersionCode | VersionName |
|---------|-------------|-------------|
| `app/build.gradle.kts` | 15 | 1.1.5 |
| `MainScreen.kt` | - | "v1.1.5" |
| APK Gerado | - | BTMicPro_v1.1.5_code15.apk |

### 4.2 APK
- **Local:** `D:\aplicativo intercominicador\BTMicPro_v1.1.5_code15.apk`
- **Tamanho:** 18.8 MB (18.817.811 bytes)
- **Build:** `gradlew.bat clean assembleDebug` - SUCCESSFUL (26s)
- **Warnings:** 1 deprecation (`isBluetoothScoOn` - intencional, fallback compat)

---

## 5. PONTOS DE ATENÇÃO / RISCOS CONHECIDOS

| Item | Risco | Mitigação |
|------|-------|-----------|
| `FakeCallConnectionService` no Manifest | Telecom pode conflitar se habilitado | Não instanciado ativo; `MANAGE_OWN_CALLS` necessário só p/ Telecom |
| `isBluetoothScoOn` deprecated | API 31+ usa `getDevices()` | Fallback implementado em `isScoActive()` |
| LoudnessEnhancer sessão 0 | Alguns MediaTek bloqueiam | Fallback `tryAlternativeLoudness()` só volume+EQ |
| `SYSTEM_ALERT_WINDOW` | User pode negar | Verificado runtime + requestOverlayPermission |
| `FOREGROUND_SERVICE_MICROPHONE` | Android 14+ exige | Declarado corretamente |
| Shevery (Shizuku fork) | Precisa instalar separado | Documentado para user final |

---

## 6. TESTES RECOMENDADOS NO DISPOSITIVO (KingKong X Pro)

### 6.1 Cenários Críticos
1. **WhatsApp Áudio:** Gravar 30s → Verificar sem "cannot record during call"
2. **Playback A2DP:** YouTube/Spotify → Áudio estéreo no capacete (não mono)
3. **Transição:** Gravar → Parar → Ouvir música → SCO desliga, A2DP volta
4. **MODO BAR:** YouTube volume baixo → Ativar BAR 80% → Voz estoura no capacete
5. **Raw Audio:** Vento forte → Ativar RAW → Voz não corta
6. **Botão Flutuante:** Arrastar → Clicar → Liga/desliga router
6. **Boot:** Reiniciar celular → Router inicia sozinho (se enabled)
7. **Auto BT:** Conectar WAYXIN R6S → Inicia automaticamente

### 6.2 Logs Chave para Monitorar
```
BluetoothAudioRouter: "Modo mudou: 3" (IN_COMMUNICATION) -> "BT mic"
BluetoothAudioRouter: "Modo mudou: 0" (NORMAL) -> "limpa BT mic, deixa A2DP"
AudioCaptureEngine: "SCO ativo=true -> sr=16000Hz"
MediaBooster: "MODO BAR ativado: boost=80% loudness+EQ"
```

---

## 7. HISTÓRICO DE VERSÕES

| Versão | Code | Data | Principais Mudanças |
|--------|------|------|---------------------|
| 1.1.5 | 15 | 02/09/2026 | UI compacta: botão -20%, toggles lado a lado, floating button só label+switch; build 1.1.5 |
| 1.1.4 | 14 | - | MODO BAR +8dB; Floating button 78dp todo verde/vermelho; Promo carousel 5h/5dia; Auto-start receivers; Shevery instalado; IA removida |
| 1.1.3 | - | - | SCO on-demand fix (OnModeChangedListener); WhatsApp recording fix; Watchdog 30s |
| 1.1.2 | - | - | FakeCallConnectionService removido do fluxo ativo |
| 1.1.1 | - | - | AudioSwitch + DSP pipeline |

---

## 8. CHECKLIST DE ENTREGA

- [x] APK gerado e versionado (v1.1.5 code 15)
- [x] UI compacta implementada (botão 144dp, toggles lado a lado, floating compacto)
- [x] Build limpo (`clean assembleDebug`) - SUCCESS
- [x] Permissões corretas no Manifest
- [x] Serviços Foreground com tipos corretos
- [x] Receivers BOOT + Bluetooth auto-start
- [x] MODO BAR funcional (LoudnessEnhancer + EQ)
- [x] DSP pipeline sem IA (HighPass + Gate + Limiter)
- [x] Raw Audio Mode (bypass DSP)
- [x] Botão flutuante 78dp com SavedStateRegistryOwner
- [x] Promo carousel com cooldown 5h / limite 5/dia
- [x] Shevery documentado para acesso privilegiado
- [x] Auditoria completa documentada neste arquivo

---

## 9. PRÓXIMOS PASSOS SUGERIDOS

1. **Instalar no KingKong X Pro** via ADB: `adb install -r BTMicPro_v1.1.5_code15.apk`
2. **Conceder permissões:** Overlay, Microfone, Bluetooth, Notificações, Battery Optimization (unrestricted)
3. **Instalar Shevery** (Shizuku fork) para acesso ADB privilegiado se necessário
4. **Testar cenários 6.1** e validar logs
5. **Se tudo OK:** Gerar Release Build (`assembleRelease`) para distribuição

---

**Auditoria concluída.** Sistema pronto para teste em dispositivo real.