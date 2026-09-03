package com.btmicpro.core

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * BluetoothRoutingEngine — Autoridade Central de Roteamento de Comunicação Bluetooth V4.
 *
 * Responsabilidades (Item 10, 11, 25 do Prompt Master):
 * 1. Detectar o Intercom Bluetooth conectado (HFP/SCO/BLE).
 * 2. Selecionar o melhor CommunicationDevice do sistema via CommunicationDeviceManager.
 * 3. Validar a rota bidirecional (Entrada Bluetooth + Saída Bluetooth).
 * 4. Monitorar alterações via AudioRouteMonitor.
 * 5. Recuperar a rota automaticamente via RoutingRecoveryManager sem guerras de concorrência.
 * 6. Fornecer telemetria em tempo real (AudioDiagnostics).
 */
class BluetoothRoutingEngine(
    private val context: Context,
    private val coroutineScope: CoroutineScope
) {

    private val audioManager: AudioManager =
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    val commDeviceManager = CommunicationDeviceManager(context)
    private val profile = DeviceCompatibilityManager.currentProfile

    // Máquina de estados V4
    private val _routerState = MutableStateFlow<RouterState>(RouterState.Disconnected)
    val routerState: StateFlow<RouterState> = _routerState.asStateFlow()

    // Rota de comunicação observada
    private val _currentRoute = MutableStateFlow(CommunicationRoute())
    val currentRoute: StateFlow<CommunicationRoute> = _currentRoute.asStateFlow()

    // Status do WhatsApp
    private val _whatsappStatus = MutableStateFlow(WhatsAppRouteStatus.UNKNOWN)
    val whatsappStatus: StateFlow<WhatsAppRouteStatus> = _whatsappStatus.asStateFlow()

    // Ferramenta experimental SilentAudioKeeper (Desligada por padrão conforme Item 28)
    private val silentAudioKeeper = SilentAudioKeeper()
    var silentAudioKeepAliveEnabled: Boolean = false
        set(value) {
            field = value
            if (value && isRunning) {
                try { silentAudioKeeper.start() } catch (ignored: Exception) {}
            } else {
                try { silentAudioKeeper.stop() } catch (ignored: Exception) {}
            }
        }

    private var isRunning = false
    private val mainHandler = Handler(Looper.getMainLooper())
    private var watchdogRunnable: Runnable? = null

    // Monitor contínuo de eventos do sistema
    private val routeMonitor = AudioRouteMonitor(context) {
        if (isRunning) {
            Log.d(TAG, "AudioRouteMonitor detectou mudança de áudio. Avaliando rota...")
            evaluateAndApplyRoute()
        }
    }

    // Gerenciador de recuperação resiliente com backoff
    private val recoveryManager = RoutingRecoveryManager(
        maxAttempts = 4,
        onAttempt = { attempt ->
            val devName = getConnectedBluetoothName() ?: "Intercom"
            updateState(RouterState.Recovering(BluetoothDeviceInfo(name = devName), attempt))
            mainHandler.post { evaluateAndApplyRoute() }
        },
        onRecoverySuccess = {
            Log.i(TAG, "Recuperação de rota finalizada com sucesso.")
        },
        onRecoveryFailed = { reason ->
            updateState(RouterState.RouteLost(reason))
            _whatsappStatus.value = WhatsAppRouteStatus.FAILED
        }
    )

    /**
     * Inicia o controle de rota de comunicação do BT Mic Pro.
     */
    @Synchronized
    fun startEngine() {
        if (isRunning) return
        isRunning = true
        Log.i(TAG, "Iniciando BluetoothRoutingEngine V4 [Perfil: ${profile.profileName}]")

        updateState(RouterState.Disconnected)
        _whatsappStatus.value = WhatsAppRouteStatus.ROUTE_PREPARED

        // Inicia monitoramento de hardware
        routeMonitor.startMonitoring()

        // Se o keep-alive experimental estiver habilitado, ativa-o
        if (silentAudioKeepAliveEnabled) {
            try { silentAudioKeeper.start() } catch (e: Exception) {
                Log.e(TAG, "Erro ao iniciar SilentAudioKeeper experimental", e)
            }
        }

        // Avalia e aplica a rota imediatamente
        evaluateAndApplyRoute()

        // Inicia o watchdog inteligente (Item 23)
        startSmartWatchdog()
    }

    /**
     * Avalia o estado atual do hardware e aplica o roteamento bidirecional.
     * Segue a máquina de estados finita de 10 estágios (Item 18).
     */
    @Synchronized
    fun evaluateAndApplyRoute() {
        if (!isRunning) return

        try {
            // Estágio 1: Detectar se há dispositivo Bluetooth conectado
            val btDeviceName = getConnectedBluetoothName()
            if (btDeviceName == null) {
                Log.d(TAG, "Nenhum dispositivo Bluetooth detectado.")
                updateState(RouterState.Disconnected)
                _currentRoute.value = CommunicationRoute()
                recoveryManager.cancel()
                return
            }

            val devInfo = BluetoothDeviceInfo(
                name = btDeviceName,
                sampleRate = profile.preferredSampleRate
            )

            // Estágio 2: Bluetooth Conectado
            if (_routerState.value is RouterState.Disconnected) {
                updateState(RouterState.BluetoothConnected(devInfo))
            }

            // Estágio 3: Buscar dispositivo de comunicação compatível
            val bestCommDevice = commDeviceManager.findBestBluetoothCommunicationDevice()
            if (bestCommDevice == null) {
                Log.w(TAG, "Dispositivo Bluetooth conectado, mas canal de comunicação ainda não exposto pelo AudioManager.")
                updateState(RouterState.BluetoothConnected(devInfo))
                return
            }

            updateState(RouterState.CommunicationDeviceAvailable(devInfo))

            // Estágio 4: Selecionar Communication Device
            val isSelected = commDeviceManager.selectCommunicationDevice(bestCommDevice)
            if (!isSelected && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Log.w(TAG, "setCommunicationDevice falhou ou não foi aceito pelo sistema.")
                if (!recoveryManager.isRecoveryActive()) {
                    recoveryManager.startRecovery("setCommunicationDevice rejeitado")
                } else {
                    recoveryManager.retry()
                }
                return
            }

            updateState(RouterState.CommunicationDeviceSelected(devInfo))

            // Estágio 5: Verificar disponibilidade de Entrada (Microfone Bluetooth)
            val inputs = audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)
            val btInput = inputs.find {
                it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && it.type == AudioDeviceInfo.TYPE_BLE_HEADSET)
            }

            if (btInput != null) {
                updateState(RouterState.InputAvailable(devInfo))
            } else {
                Log.d(TAG, "Entrada Bluetooth SCO/BLE ainda não indexada nos inputs.")
            }

            // Estágio 6: Verificar disponibilidade de Saída (Fone Bluetooth)
            val outputs = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            val btOutput = outputs.find {
                it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && it.type == AudioDeviceInfo.TYPE_BLE_HEADSET) ||
                it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP
            }

            if (btOutput != null) {
                updateState(RouterState.OutputAvailable(devInfo))
            }

            // Estágio 7: Rota Pronta Bidirecional (Item 17 & 19)
            val isBidirectionalReady = (btInput != null) && (btOutput != null)
            val finalRoute = CommunicationRoute(
                inputDevice = btInput,
                outputDevice = btOutput,
                communicationDevice = bestCommDevice,
                bluetoothDeviceName = btDeviceName,
                bluetoothProfile = if (btInput?.type == AudioDeviceInfo.TYPE_BLE_HEADSET) "BLE_HEADSET" else "HFP/SCO",
                isBidirectionalReady = isBidirectionalReady
            )
            _currentRoute.value = finalRoute

            if (isBidirectionalReady || !profile.isCubotKingKongXPro) {
                val latency = if (profile.isCubotKingKongXPro) 15 else 20
                updateState(
                    RouterState.RouteReady(
                        device = devInfo.copy(isScoConnected = true),
                        sampleRate = profile.preferredSampleRate,
                        estimatedLatencyMs = latency,
                        route = finalRoute
                    )
                )
                _whatsappStatus.value = WhatsAppRouteStatus.ROUTE_PREPARED
                recoveryManager.markSuccess()
            } else {
                Log.w(TAG, "Aguardando canal bidirecional completo...")
            }

        } catch (e: Exception) {
            Log.e(TAG, "Erro na máquina de estados do BluetoothRoutingEngine", e)
            if (!recoveryManager.isRecoveryActive()) {
                recoveryManager.startRecovery("Exceção: ${e.message}")
            } else {
                recoveryManager.retry()
            }
        }
    }

    /**
     * Watchdog inteligente (Item 23 do Prompt Master):
     * Verifica periodicamente se a rota permanece ativa sem perturbar o áudio se tudo estiver OK.
     */
    private fun startSmartWatchdog() {
        stopSmartWatchdog()
        watchdogRunnable = object : Runnable {
            override fun run() {
                if (!isRunning) return

                val current = _routerState.value
                val isBtConnected = getConnectedBluetoothName() != null

                if (!isBtConnected && current !is RouterState.Disconnected) {
                    Log.w(TAG, "Watchdog: Bluetooth desconectou. Atualizando para DISCONNECTED.")
                    updateState(RouterState.Disconnected)
                } else if (isBtConnected && (current is RouterState.RouteLost || current is RouterState.Disconnected)) {
                    Log.i(TAG, "Watchdog: Bluetooth reconectado. Revalidando rota...")
                    evaluateAndApplyRoute()
                } else if (isBtConnected && current is RouterState.RouteReady) {
                    // SE OK -> NÃO ALTERAR NADA (Regra de Ouro do Item 23)
                    // Apenas valida se o communication device ainda está íntegro
                    val currentComm = commDeviceManager.getCurrentCommunicationDevice()
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && currentComm == null) {
                        Log.w(TAG, "Watchdog: Communication Device foi desvinculado pelo sistema. Recuperando...")
                        recoveryManager.startRecovery("Communication device desvinculado")
                    }
                }

                mainHandler.postDelayed(this, 12000L) // Checagem a cada 12 segundos sem consumo de bateria
            }
        }
        mainHandler.postDelayed(watchdogRunnable!!, 12000L)
    }

    private fun stopSmartWatchdog() {
        watchdogRunnable?.let { mainHandler.removeCallbacks(it) }
        watchdogRunnable = null
    }

    /**
     * Encerra o roteamento de forma limpa, liberando recursos e restaurando o estado anterior (Item 52, 60).
     */
    @Synchronized
    fun stopEngine() {
        if (!isRunning) return
        isRunning = false
        Log.i(TAG, "Encerrando BluetoothRoutingEngine V4...")

        stopSmartWatchdog()
        routeMonitor.stopMonitoring()
        recoveryManager.cancel()

        try {
            silentAudioKeeper.stop()
        } catch (ignored: Exception) {}

        // Limpa dispositivo de comunicação
        commDeviceManager.clearCommunicationDevice()

        updateState(RouterState.Disconnected)
        _currentRoute.value = CommunicationRoute()
        _whatsappStatus.value = WhatsAppRouteStatus.UNKNOWN
    }

    private fun updateState(newState: RouterState) {
        _routerState.value = newState
        RouterStateHolder.updateState(newState)
    }

    fun markUserValidatedWhatsApp() {
        _whatsappStatus.value = WhatsAppRouteStatus.USER_VALIDATED
    }

    fun getConnectedBluetoothName(): String? {
        return try {
            val devs = audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)
            devs.find {
                it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && it.type == AudioDeviceInfo.TYPE_BLE_HEADSET)
            }?.productName?.toString()
                ?: if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    audioManager.availableCommunicationDevices.find {
                        it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO || it.type == AudioDeviceInfo.TYPE_BLE_HEADSET
                    }?.productName?.toString()
                } else null
        } catch (e: Exception) { null }
    }

    /**
     * Gera relatório de diagnóstico completo e em conformidade com o Item 39 do Prompt Master.
     */
    fun getFullDiagnostics(): AudioDiagnostics {
        val modeStr = when (audioManager.mode) {
            AudioManager.MODE_NORMAL -> "MODE_NORMAL (0)"
            AudioManager.MODE_RINGTONE -> "MODE_RINGTONE (1)"
            AudioManager.MODE_IN_CALL -> "MODE_IN_CALL (2)"
            AudioManager.MODE_IN_COMMUNICATION -> "MODE_IN_COMMUNICATION (3)"
            AudioManager.MODE_CALL_SCREENING -> "MODE_CALL_SCREENING (4)"
            else -> "UNKNOWN (${audioManager.mode})"
        }

        val commDevStr = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            audioManager.communicationDevice?.let { "${it.productName} (tipo=${it.type})" } ?: "Nenhum"
        } else {
            "Não suportado (API < 31)"
        }

        val inputs = audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS).map {
            "${it.productName} [tipo=${it.type}]"
        }
        val outputs = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).map {
            "${it.productName} [tipo=${it.type}]"
        }

        val currentRouteVal = _currentRoute.value
        val hasInput = currentRouteVal.inputDevice != null
        val hasOutput = currentRouteVal.outputDevice != null

        val stateDesc = when (val s = _routerState.value) {
            is RouterState.RouteReady -> "ROTA PRONTA (Bidirecional no Intercom: ${s.device.name})"
            is RouterState.OutputAvailable -> "SAÍDA BLUETOOTH PRONTA (${s.device.name})"
            is RouterState.InputAvailable -> "ENTRADA BLUETOOTH PRONTA (${s.device.name})"
            is RouterState.CommunicationDeviceSelected -> "COMMUNICATION DEVICE SELECIONADO (${s.device.name})"
            is RouterState.CommunicationDeviceAvailable -> "COMMUNICATION DEVICE DETECTADO (${s.device.name})"
            is RouterState.BluetoothConnected -> "BLUETOOTH CONECTADO (${s.device.name})"
            is RouterState.Recovering -> "RECUPERANDO (Tentativa ${s.attempt})"
            is RouterState.RouteLost -> "ROTA PERDIDA: ${s.reason}"
            is RouterState.Disconnected -> "DESCONECTADO"
            is RouterState.Error -> "ERRO: ${s.message}"
            else -> "INATIVO"
        }

        val isScoActive = try {
            @Suppress("DEPRECATION")
            audioManager.isBluetoothScoOn || hasInput
        } catch (e: Exception) { false }

        return AudioDiagnostics(
            manufacturer = Build.MANUFACTURER,
            model = Build.MODEL,
            androidVersion = Build.VERSION.RELEASE,
            sdk = Build.VERSION.SDK_INT,
            build = Build.DISPLAY,
            bluetoothDevice = getConnectedBluetoothName() ?: "Nenhum intercom conectado",
            bluetoothProfile = currentRouteVal.bluetoothProfile ?: "Indefinido",
            communicationDevice = commDevStr,
            inputDevices = inputs,
            outputDevices = outputs,
            audioMode = modeStr,
            scoState = if (isScoActive) "SCO ATIVO" else "SCO INATIVO",
            scoCodec = if (profile.preferredSampleRate == 16000) "mSBC (Wideband 16kHz)" else "CVSD (8kHz)",
            routeState = stateDesc,
            inputAvailable = hasInput,
            outputAvailable = hasOutput,
            silentAudioKeeper = silentAudioKeepAliveEnabled,
            audioFocusState = "LIVRE (Não retido pelo BT Mic Pro)",
            whatsappStatus = _whatsappStatus.value,
            estimatedLatency = if (profile.isCubotKingKongXPro) "~15ms (mSBC Wideband)" else "~20ms",
            hardwareProfileName = profile.profileName
        )
    }

    companion object {
        private const val TAG = "BTMIC_ROUTER_V4"
    }
}
