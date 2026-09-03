package com.btmicpro.core

import android.annotation.SuppressLint
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.LinkedList

/**
 * BluetoothRoutingEngine — Autoridade Central e Única de Roteamento de Comunicação Bluetooth V5.
 *
 * Princípio Arquitetural Absoluto (Prompt Master V5):
 * O BT Mic Pro NÃO é um gravador nem injetor de PCM.
 * Atua exclusivamente como CONTROLADOR E ESTABILIZADOR DA ROTA DE ÁUDIO DE COMUNICAÇÃO BLUETOOTH.
 *
 * ENTRADA: Microfone Intercom -> Bluetooth HFP/SCO -> Android Audio Comm Input -> WhatsApp
 * SAÍDA: WhatsApp -> Android Audio Comm Output -> Bluetooth HFP/SCO -> Intercom
 */
class BluetoothRoutingEngine(
    private val context: Context,
    private val coroutineScope: CoroutineScope
) {

    private val audioManager: AudioManager =
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    val commDeviceManager = CommunicationDeviceManager(context)
    private val profile = DeviceCompatibilityManager.currentProfile

    // Mutex para prevenção de guerra de rota e garantia de serialização (Item 20)
    private val routeMutex = Mutex()

    // Máquina de estados central V5 (13 estágios — Item 7)
    private val _routerState = MutableStateFlow<RouterState>(RouterState.Disconnected)
    val routerState: StateFlow<RouterState> = _routerState.asStateFlow()

    // Rota de comunicação ativa observada
    private val _currentRoute = MutableStateFlow(CommunicationRoute())
    val currentRoute: StateFlow<CommunicationRoute> = _currentRoute.asStateFlow()

    // Status do WhatsApp (Item 9)
    private val _whatsappStatus = MutableStateFlow(WhatsAppRouteStatus.UNKNOWN)
    val whatsappStatus: StateFlow<WhatsAppRouteStatus> = _whatsappStatus.asStateFlow()

    // Contadores de queda e estabilidade (Item 58)
    var routeLossCount = 0
        private set
    var recoveryCount = 0
        private set
    var scoDisconnectCount = 0
        private set
    var communicationDeviceChangeCount = 0
        private set

    // Cronometragem de tempos (Item 12 e 59)
    private var preparationStartTime = 0L
    var routePreparationTimeMs = 0L
        private set

    // Histórico de até 100 eventos em memória (Item 61)
    private val eventHistory = LinkedList<RouteEvent>()

    // Componente experimental de Keep-Alive (Item 26, 27, 28)
    private val scoKeepAlive = ExperimentalScoKeepAlive()
    var useExperimentalKeepAlive: Boolean = false
        set(value) {
            field = value
            if (value && isRunning) {
                try { scoKeepAlive.start(profile.preferredSampleRate) } catch (ignored: Exception) {}
            } else {
                try { scoKeepAlive.stop() } catch (ignored: Exception) {}
            }
        }

    // Camada B: Gerenciador de HFP / Headset (Item 14, 15)
    @SuppressLint("MissingPermission")
    val bluetoothHfpManager = BluetoothHfpManager(
        context = context,
        onAudioStateChanged = { hfpState ->
            Log.d(TAG, "BluetoothHfpManager relatou estado de áudio: $hfpState")
            if (hfpState == HfpAudioState.AUDIO_DISCONNECTED) {
                scoDisconnectCount++
                recordEvent("SCO_DISCONNECTED", "Desconexão de áudio HFP reportada pelo SO")
            }
            if (isRunning) {
                triggerAsyncRouteEvaluation()
            }
        },
        onConnectionStateChanged = { device, state ->
            Log.d(TAG, "BluetoothHfpManager relatou conexão física: dev=${device?.name} state=$state")
            if (state == BluetoothProfile.STATE_DISCONNECTED) {
                recordEvent("ACL_DISCONNECTED", "Intercom desconectado: ${device?.name}")
            }
            if (isRunning) {
                triggerAsyncRouteEvaluation()
            }
        }
    )

    private var isRunning = false
    private val mainHandler = Handler(Looper.getMainLooper())
    private var watchdogRunnable: Runnable? = null

    // Monitor contínuo de hardware de áudio com debounce e snapshots (Item 16, 70, 71)
    private val routeMonitor = AudioRouteMonitor(context) { diff ->
        if (isRunning) {
            Log.d(TAG, "AudioRouteMonitor detectou alteração no hardware ($diff).")
            if (diff == RouteDiffType.COMMUNICATION_CHANGED) {
                communicationDeviceChangeCount++
            }
            triggerAsyncRouteEvaluation()
        }
    }

    // Gerenciador de recuperação com backoff exponencial (Item 23, 24)
    private val recoveryManager = RoutingRecoveryManager(
        maxAttempts = 4,
        backoffDelaysMs = longArrayOf(profile.routingRetryDelay, 1000L, 2000L, 4000L),
        onAttempt = { attempt ->
            val devName = getConnectedBluetoothName() ?: "Intercom"
            updateState(RouterState.Recovering(BluetoothDeviceInfo(name = devName), attempt))
            triggerAsyncRouteEvaluation()
        },
        onRecoverySuccess = { durationMs ->
            recoveryCount++
            recordEvent("RECOVERY_SUCCESS", "Rota restabelecida com sucesso em ${durationMs}ms")
            Log.i(TAG, "Recuperação concluída com sucesso em ${durationMs}ms.")
        },
        onRecoveryFailed = { reason ->
            routeLossCount++
            recordEvent("RECOVERY_FAILED", reason)
            updateState(RouterState.RouteLost(reason))
            _whatsappStatus.value = WhatsAppRouteStatus.FAILED
        }
    )

    /**
     * Inicia o serviço e o controle de roteamento da V5.
     */
    @Synchronized
    fun startEngine() {
        if (isRunning) return
        isRunning = true
        preparationStartTime = System.currentTimeMillis()
        Log.i(TAG, "Iniciando BluetoothRoutingEngine V5 [Perfil: ${profile.profileName}]")

        recordEvent("ENGINE_START", "Iniciando controle de rota V5")
        updateState(RouterState.Disconnected)
        _whatsappStatus.value = WhatsAppRouteStatus.ROUTE_PREPARED

        // Inicia componentes de monitoramento
        bluetoothHfpManager.start()
        routeMonitor.startMonitoring()

        if (useExperimentalKeepAlive) {
            try { scoKeepAlive.start(profile.preferredSampleRate) } catch (e: Exception) {
                Log.e(TAG, "Erro ao iniciar keepalive experimental", e)
            }
        }

        // Dispara avaliação inicial da rota
        triggerAsyncRouteEvaluation()

        // Inicia watchdog não-destrutivo (Item 72, 73, 74, 75)
        startSmartWatchdog()
    }

    private fun triggerAsyncRouteEvaluation() {
        coroutineScope.launch {
            evaluateAndApplyRoute()
        }
    }

    /**
     * Avalia o hardware e orquestra a máquina de estados canônica de 13 estágios.
     * Protegido por Mutex contra concorrência e condições de corrida (Item 20).
     */
    suspend fun evaluateAndApplyRoute() {
        if (!isRunning) return

        routeMutex.withLock {
            try {
                // Estágio 1: Identificar Bluetooth físico conectado (Item 13)
                val btDeviceName = getConnectedBluetoothName()
                if (btDeviceName == null) {
                    Log.d(TAG, "Nenhum dispositivo Bluetooth conectado detectado.")
                    if (_routerState.value !is RouterState.Disconnected) {
                        recordEvent("DISCONNECTED", "Nenhum fone/intercom detectado")
                        updateState(RouterState.Disconnected)
                    }
                    _currentRoute.value = CommunicationRoute()
                    recoveryManager.cancel()
                    return
                }

                val devInfo = BluetoothDeviceInfo(
                    name = btDeviceName,
                    sampleRate = profile.preferredSampleRate
                )

                // Estágio 2: BLUETOOTH_CONNECTED
                if (_routerState.value is RouterState.Disconnected) {
                    recordEvent("BLUETOOTH_CONNECTED", "Intercom identificado: $btDeviceName")
                    updateState(RouterState.BluetoothConnected(devInfo))
                }

                // Estágio 3: Buscar dispositivo de comunicação elegível (apenas sinks - Item 42 e 43)
                val bestCommDevice = commDeviceManager.findBestBluetoothCommunicationDevice()
                if (bestCommDevice == null) {
                    Log.w(TAG, "Dispositivo conectado ($btDeviceName), mas canal de comunicação ainda não exposto.")
                    if (_routerState.value !is RouterState.BluetoothConnected) {
                        updateState(RouterState.BluetoothConnected(devInfo))
                    }
                    return
                }

                updateState(RouterState.CommunicationDeviceAvailable(devInfo))

                // Estágio 4: Selecionar Communication Device com confirmação e timeout (Item 21, 22)
                updateState(RouterState.CommunicationDeviceSelected(devInfo))
                val isConfirmed = commDeviceManager.selectCommunicationDeviceWithConfirmation(
                    device = bestCommDevice,
                    timeoutMs = profile.scoConnectionTimeout
                )

                if (!isConfirmed) {
                    Log.w(TAG, "Confirmação de setCommunicationDevice falhou ou deu timeout.")
                    if (!recoveryManager.isRecoveryActive()) {
                        recoveryManager.startRecovery("setCommunicationDevice não confirmado dentro do timeout")
                    } else {
                        recoveryManager.retry()
                    }
                    return
                }

                // Estágio 5 e 6: Acompanhamento de Áudio HFP real (Item 15)
                val actualAudioState = bluetoothHfpManager.detectActualBluetoothAudioState()
                if (actualAudioState.hfpAudioState == HfpAudioState.AUDIO_CONNECTING) {
                    updateState(RouterState.AudioConnecting(devInfo))
                } else if (actualAudioState.isAudioConnected) {
                    updateState(RouterState.AudioConnected(devInfo))
                }

                // Estágio 7: Verificar disponibilidade de microfone (Input Device)
                val inputs = audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)
                val btInput = inputs.find {
                    it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                    (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && it.type == AudioDeviceInfo.TYPE_BLE_HEADSET)
                }
                if (btInput != null) {
                    updateState(RouterState.InputAvailable(devInfo))
                }

                // Estágio 8: Verificar disponibilidade de alto-falante (Output Device)
                val outputs = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                val btOutput = outputs.find {
                    it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                    (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && it.type == AudioDeviceInfo.TYPE_BLE_HEADSET) ||
                    it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP
                }
                if (btOutput != null) {
                    updateState(RouterState.OutputAvailable(devInfo))
                }

                // Estágio 9 ou 10: Rota Bidirecional Pronta ou Degradada
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

                if (isBidirectionalReady) {
                    if (preparationStartTime > 0L) {
                        routePreparationTimeMs = (System.currentTimeMillis() - preparationStartTime).coerceAtLeast(0L)
                        preparationStartTime = 0L
                    }

                    recordEvent("ROUTE_READY", "Rota bidirecional estável confirmada ($btDeviceName)")
                    updateState(
                        RouterState.RouteReady(
                            device = devInfo.copy(isScoConnected = true),
                            sampleRate = profile.preferredSampleRate,
                            routePreparationTimeMs = routePreparationTimeMs,
                            audioBufferEstimateMs = (profile.preferredBufferSize * 10L),
                            processingTimeMs = 0L,
                            endToEndLatency = "NOT_MEASURED",
                            route = finalRoute
                        )
                    )
                    _whatsappStatus.value = WhatsAppRouteStatus.ROUTE_PREPARED
                    recoveryManager.markSuccess()
                } else {
                    Log.w(TAG, "Rota incompleta (Input=${btInput != null}, Output=${btOutput != null}). Degradada.")
                    updateState(RouterState.RouteDegraded(devInfo, "Aguardando canal bidirecional completo"))
                }

            } catch (e: Exception) {
                Log.e(TAG, "Exceção no ciclo de roteamento V5", e)
                recordEvent("ERROR", e.message ?: "Erro desconhecido")
                if (!recoveryManager.isRecoveryActive()) {
                    recoveryManager.startRecovery("Exceção: ${e.message}")
                } else {
                    recoveryManager.retry()
                }
            }
        }
    }

    /**
     * Watchdog Não-Destrutivo (Item 72, 73, 74, 75 do Prompt Master):
     * Intervalo de 12 segundos. Se a rota estiver estável e funcional: NÃO FAZER NADA.
     */
    private fun startSmartWatchdog() {
        stopSmartWatchdog()
        watchdogRunnable = object : Runnable {
            override fun run() {
                if (!isRunning) return

                val current = _routerState.value
                val isBtConnected = getConnectedBluetoothName() != null

                if (!isBtConnected && current !is RouterState.Disconnected) {
                    Log.w(TAG, "Watchdog: Bluetooth desconectou fisicamente.")
                    recordEvent("WATCHDOG_DROP", "Perda física de conexão")
                    routeLossCount++
                    updateState(RouterState.Disconnected)
                } else if (isBtConnected && (current is RouterState.RouteLost || current is RouterState.Disconnected)) {
                    Log.i(TAG, "Watchdog: Intercom detectado após perda. Reavaliando rota...")
                    triggerAsyncRouteEvaluation()
                } else if (isBtConnected && current is RouterState.RouteReady) {
                    // SE ESTÁ TUDO OK -> NÃO TOCAR NA ROTA (Item 74, 75)
                    // Apenas valida se o communication device ainda está atribuído
                    val currentComm = commDeviceManager.getCurrentCommunicationDevice()
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && currentComm == null) {
                        Log.w(TAG, "Watchdog: Dispositivo de comunicação foi desvinculado pelo SO.")
                        recordEvent("WATCHDOG_COMM_LOST", "CommunicationDevice desvinculado")
                        recoveryManager.startRecovery("Communication device desvinculado")
                    }
                }

                mainHandler.postDelayed(this, 12000L)
            }
        }
        mainHandler.postDelayed(watchdogRunnable!!, 12000L)
    }

    private fun stopSmartWatchdog() {
        watchdogRunnable?.let { mainHandler.removeCallbacks(it) }
        watchdogRunnable = null
    }

    /**
     * Obtém o nome do dispositivo Bluetooth conectado seguindo a ordem estrita do Item 13:
     * 1. BluetoothProfile conectado (BluetoothHfpManager)
     * 2. availableCommunicationDevices
     * 3. AudioManager input devices
     * 4. Fallback output devices
     */
    @SuppressLint("MissingPermission")
    fun getConnectedBluetoothName(): String? {
        // 1. BluetoothProfile conectado (HFP proxy)
        val hfpDev = bluetoothHfpManager.connectedDevice.value ?: bluetoothHfpManager.refreshConnectedDevice()
        if (hfpDev != null && !hfpDev.name.isNullOrBlank()) {
            return hfpDev.name
        }

        // 2. availableCommunicationDevices
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val commDev = commDeviceManager.getAvailableCommunicationDevices().find {
                it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO || it.type == AudioDeviceInfo.TYPE_BLE_HEADSET
            }
            if (commDev != null) {
                return commDev.productName.toString()
            }
        }

        // 3. AudioManager input devices
        try {
            val inputs = audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)
            val btInput = inputs.find {
                it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && it.type == AudioDeviceInfo.TYPE_BLE_HEADSET)
            }
            if (btInput != null) {
                return btInput.productName.toString()
            }
        } catch (ignored: Exception) {}

        // 4. Fallback output devices
        try {
            val outputs = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            val btOutput = outputs.find {
                it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && it.type == AudioDeviceInfo.TYPE_BLE_HEADSET) ||
                it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP
            }
            if (btOutput != null) {
                return btOutput.productName.toString()
            }
        } catch (ignored: Exception) {}

        return null
    }

    private fun updateState(newState: RouterState) {
        _routerState.value = newState
        RouterStateHolder.updateState(newState)
        AppLogger.d(TAG, "Estado da Rota atualizado: ${newState.javaClass.simpleName}")
    }

    private fun recordEvent(event: String, reason: String? = null) {
        val prev = when (val s = _routerState.value) {
            is RouterState.RouteReady -> "ROUTE_READY"
            is RouterState.BluetoothConnected -> "BT_CONNECTED"
            is RouterState.Disconnected -> "DISCONNECTED"
            is RouterState.Recovering -> "RECOVERING"
            is RouterState.RouteLost -> "ROUTE_LOST"
            else -> s.javaClass.simpleName
        }
        val entry = RouteEvent(
            event = event,
            previousState = prev,
            newState = _routerState.value.javaClass.simpleName,
            device = getConnectedBluetoothName(),
            reason = reason
        )
        synchronized(eventHistory) {
            if (eventHistory.size >= 100) {
                eventHistory.removeFirst()
            }
            eventHistory.addLast(entry)
        }
        AppLogger.i(TAG, "[$event] $prev -> ${_routerState.value.javaClass.simpleName}${reason?.let { " ($it)" } ?: ""}")
    }

    fun markUserValidatedWhatsApp() {
        _whatsappStatus.value = WhatsAppRouteStatus.USER_VALIDATED
        recordEvent("USER_VALIDATION", "Usuário validou manualmente o funcionamento no WhatsApp")
    }

    /**
     * Encerra o roteamento de áudio liberando os recursos e restaurando o roteamento padrão do SO.
     */
    @Synchronized
    fun stopEngine() {
        if (!isRunning) return
        isRunning = false
        Log.i(TAG, "Encerrando BluetoothRoutingEngine V5...")

        recordEvent("ENGINE_STOP", "Desativação solicitada pelo usuário")
        stopSmartWatchdog()
        routeMonitor.stopMonitoring()
        recoveryManager.cancel()

        try { scoKeepAlive.stop() } catch (ignored: Exception) {}
        commDeviceManager.clearCommunicationDevice()
        bluetoothHfpManager.stop()

        updateState(RouterState.Disconnected)
        _currentRoute.value = CommunicationRoute()
        _whatsappStatus.value = WhatsAppRouteStatus.UNKNOWN
    }

    /**
     * Retorna diagnóstico canônico e completo com métricas reais (Item 47, 48, 49, 58).
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
            audioManager.communicationDevice?.let { "${it.productName} (ID=${it.id}, Tipo=${it.type})" } ?: "Nenhum"
        } else {
            "Não suportado (API < 31)"
        }

        val rawInputs = audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)
        val rawOutputs = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)

        val inputs = rawInputs.map {
            "${it.productName} [Tipo=${it.type}]"
        }
        val outputs = rawOutputs.map {
            "${it.productName} [Tipo=${it.type}]"
        }

        val currentRouteVal = _currentRoute.value
        val hasInput = currentRouteVal.inputDevice != null || rawInputs.any {
            it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && it.type == AudioDeviceInfo.TYPE_BLE_HEADSET)
        }
        val hasOutput = currentRouteVal.outputDevice != null || rawOutputs.any {
            it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && it.type == AudioDeviceInfo.TYPE_BLE_HEADSET) || it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP
        }
        val isBidiReady = currentRouteVal.isBidirectionalReady || (isRunning && hasInput && hasOutput)

        val hfpState = bluetoothHfpManager.detectActualBluetoothAudioState()

        val stateDesc = when (val s = _routerState.value) {
            is RouterState.RouteReady -> "ROTA PRONTA BIDIRECIONAL (${s.device.name})"
            is RouterState.RouteDegraded -> "ROTA DEGRADADA (${s.reason})"
            is RouterState.OutputAvailable -> "SAÍDA PRONTA (${s.device.name})"
            is RouterState.InputAvailable -> "ENTRADA PRONTA (${s.device.name})"
            is RouterState.AudioConnected -> "ÁUDIO HFP CONECTADO (${s.device.name})"
            is RouterState.AudioConnecting -> "NEGOCIANDO ÁUDIO HFP (${s.device.name})"
            is RouterState.CommunicationDeviceSelected -> "COMMUNICATION DEVICE SELECIONADO (${s.device.name})"
            is RouterState.CommunicationDeviceAvailable -> "COMMUNICATION DEVICE DISPONÍVEL (${s.device.name})"
            is RouterState.BluetoothConnected -> "BLUETOOTH CONECTADO (${s.device.name})"
            is RouterState.Recovering -> "RECUPERANDO (Tentativa ${s.attempt})"
            is RouterState.RouteLost -> "ROTA PERDIDA: ${s.reason}"
            is RouterState.Disconnected -> if (!isRunning) "INATIVO (Aguardando ativação no botão principal)" else "DESCONECTADO"
            is RouterState.Error -> "ERRO: ${s.message}"
            else -> "INATIVO"
        }

        val snapshotEvents: List<RouteEvent>
        synchronized(eventHistory) {
            snapshotEvents = ArrayList(eventHistory)
        }

        return AudioDiagnostics(
            manufacturer = Build.MANUFACTURER,
            model = Build.MODEL,
            androidVersion = Build.VERSION.RELEASE,
            sdk = Build.VERSION.SDK_INT,
            build = Build.DISPLAY,
            bluetoothDevice = getConnectedBluetoothName() ?: "Nenhum intercom conectado",
            bluetoothProfile = currentRouteVal.bluetoothProfile ?: "Indefinido",
            hfpAudioState = hfpState.hfpAudioState.label,
            scoCodec = hfpState.reportedCodec,
            communicationDevice = commDevStr,
            audioMode = modeStr,
            routeState = stateDesc,
            inputAvailable = hasInput,
            outputAvailable = hasOutput,
            isBidirectionalReady = isBidiReady,
            routePreparationTimeMs = routePreparationTimeMs,
            audioBufferEstimateMs = (profile.preferredBufferSize * 10L),
            processingTimeMs = 0L,
            endToEndLatency = "NOT_MEASURED",
            routeLossCount = routeLossCount,
            recoveryCount = recoveryCount,
            scoDisconnectCount = scoDisconnectCount,
            communicationDeviceChangeCount = communicationDeviceChangeCount,
            lastRecoveryDurationMs = recoveryManager.lastRecoveryDurationMs,
            scoKeepAliveState = scoKeepAlive.state.name,
            audioFocusState = "LIVRE (Não retido pelo BT Mic Pro)",
            whatsappStatus = _whatsappStatus.value,
            inputDevices = inputs,
            outputDevices = outputs,
            recentEvents = snapshotEvents,
            hardwareProfileName = profile.profileName
        )
    }

    companion object {
        private const val TAG = "BTMIC_ROUTING_ENGINE_V5"
    }
}
