package com.btmicpro.core

import android.annotation.SuppressLint
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.SystemClock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.coroutines.coroutineContext

/** Experimentos de rota sem captura própria; atividade e modo são controlados por perfil. */
class BluetoothRoutingEngine(context: Context, private val coroutineScope: CoroutineScope) {
    private val appContext = context.applicationContext
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    val commDeviceManager = CommunicationDeviceManager(context.applicationContext)
    private val profile = DeviceCompatibilityManager.currentProfile
    private val _routerState = MutableStateFlow<RouterState>(RouterState.Disconnected)
    val routerState: StateFlow<RouterState> = _routerState.asStateFlow()
    private val _currentRoute = MutableStateFlow(CommunicationRoute())
    val currentRoute: StateFlow<CommunicationRoute> = _currentRoute.asStateFlow()
    val whatsappStatus = RouterStateHolder.whatsappStatus
    private val events = ArrayDeque<RouteEvent>()
    private val recoveryBudget = RecoveryBudget()
    private var engineScope: CoroutineScope? = null
    private var evaluationJob: Job? = null
    private var recoveryJob: Job? = null
    private var transientRecheckJob: Job? = null
    // BUG-14: @Volatile garante visibilidade entre a coroutine de avaliação
    // e os callbacks de AudioRouteMonitor/BluetoothHfpManager que rodam em threads de Binder/Handler.
    @Volatile
    private var evaluateAgain = false
    // BUG-15: @Volatile garante que stopEngine() seja visível imediatamente
    // para callbacks multi-thread, evitando trigger de avaliação em engine parada.
    @Volatile
    private var isRunning = false
    private var startedAt = 0L
    private var recoveryStartedAt = 0L
    private var lastRecoveryDuration = 0L
    var routePreparationTimeMs = 0L; private set
    var routeLossCount = 0; private set
    var recoveryCount = 0; private set
    var scoDisconnectCount = 0; private set
    var communicationDeviceChangeCount = 0; private set
    private var transientFailureCount = 0
    private val mediaRoutePolicy = MediaRoutePolicy()
    private var yieldingToMedia = false
    private var yieldingToCall = false
    private val prefs = appContext.getSharedPreferences(com.btmicpro.receiver.BootReceiver.PREFS_NAME, Context.MODE_PRIVATE)
    private var selectedProfile = AudioModeProfile.fromCode(prefs.getString("audio_mode_profile", null))
    private val scoKeepAlive = ExperimentalScoKeepAlive()
    private var modeAttempted = false
    private var modeRequested = false
    private var lastTargetId: Int? = null
    private var pendingProfileCode: String? = null
    private val audioActivity = AudioActivityObserver(appContext) {
        if (isRunning) {
            updateRouteControl()
            triggerAsyncRouteEvaluation()
        }
    }

    val bluetoothHfpManager = BluetoothHfpManager(
        context.applicationContext,
        onAudioStateChanged = { state ->
            if (isRunning) {
                if (state == HfpAudioState.AUDIO_DISCONNECTED) {
                    scoDisconnectCount++
                    commDeviceManager.onLegacyScoDisconnected()
                }
                triggerAsyncRouteEvaluation()
            }
        },
        onConnectionStateChanged = { _, state ->
            if (isRunning) {
                if (state == BluetoothProfile.STATE_CONNECTED || state == BluetoothProfile.STATE_DISCONNECTED) {
                    recoveryBudget.reset()
                    recoveryJob?.cancel()
                    recoveryJob = null
                }
                triggerAsyncRouteEvaluation()
            }
        }
    )
    private val routeMonitor = AudioRouteMonitor(context.applicationContext) { diff ->
        if (isRunning) {
            if (diff == RouteDiffType.COMMUNICATION_CHANGED) communicationDeviceChangeCount++
            // Endpoint SCO removal is not a new physical connection: don't replenish retries.
            triggerAsyncRouteEvaluation()
        }
    }

    fun startEngine() {
        if (isRunning) return
        isRunning = true
        engineScope = CoroutineScope(coroutineScope.coroutineContext + SupervisorJob(coroutineScope.coroutineContext[Job]))
        startedAt = SystemClock.elapsedRealtime()
        recoveryBudget.reset()
        transientFailureCount = 0
        setWhatsAppStatus(WhatsAppRouteStatus.UNKNOWN)
        updateState(RouterState.WaitingDevice)
        AppLogger.setProfile(selectedProfile.code)
        logProfile()
        bluetoothHfpManager.start()
        routeMonitor.startMonitoring()
        audioActivity.start()
        updateRouteControl()
        triggerAsyncRouteEvaluation()
        engineScope?.launch {
            var lastWatchdog = SystemClock.elapsedRealtime()
            var lastHeartbeat = 0L
            while (isRunning) {
                // Economia de bateria: eventos (captura, reprodução, SCO, dispositivos)
                // chegam na hora via callbacks; este loop é só rede de segurança.
                val stable = _routerState.value is RouterState.RouteReady
                delay(if (stable) selectedProfile.routeControlStableMs else selectedProfile.routeControlUnstableMs)
                updateRouteControl()
                // A watchdog observes health; it never resets a failed recovery budget.
                val now = SystemClock.elapsedRealtime()
                if (now - lastWatchdog >= selectedProfile.watchdogIntervalMs) {
                    lastWatchdog = now
                    if (!recoveryBudget.exhausted) triggerAsyncRouteEvaluation()
                }
                val heartbeatInterval = if (stable) { if (selectedProfile.ecoPolling) 180000L else 90000L } else 30000L
                if (now - lastHeartbeat >= heartbeatInterval) {
                    lastHeartbeat = now
                    logHeartbeat()
                }
            }
        }
    }

    private fun updateRouteControl() {
        audioActivity.poll()
        val requested = AudioModeProfile.fromCode(prefs.getString("audio_mode_profile", null))
        if (requested != selectedProfile) {
            if (audioActivity.recordingActive) {
                if (pendingProfileCode != requested.code) AppLogger.i("PROFILE_PENDING", "${requested.code}; aguardando fim da captura")
                pendingProfileCode = requested.code
            } else {
                cancelRouteWork()
                scoKeepAlive.stop()
                commDeviceManager.clearCommunicationDevice()
                releaseAudioMode()
                selectedProfile = requested
                pendingProfileCode = null
                AppLogger.setProfile(requested.code)
                logProfile()
                recoveryBudget.reset()
                mediaRoutePolicy.reset()
                yieldingToMedia = false
                yieldingToCall = false
                _currentRoute.value = CommunicationRoute()
                setWhatsAppStatus(WhatsAppRouteStatus.UNKNOWN)
                startedAt = SystemClock.elapsedRealtime()
                updateState(RouterState.WaitingDevice)
                triggerAsyncRouteEvaluation()
            }
        }
        val callActive = anotherAppCommunicating()
        // Sem liberação para mídia (ou com chamada ativa), a consulta de música é
        // inútil: shouldYield retornaria false de qualquer forma. Pula a chamada
        // binder para não acordar o sistema à toa.
        val musicActive = if (selectedProfile.releaseForMedia && !callActive) audioManager.isMusicActive else false
        val recordingActive = audioActivity.recordingActive
        val yield = mediaRoutePolicy.shouldYield(
            selectedProfile.releaseForMedia && !callActive, musicActive, recordingActive,
            SystemClock.elapsedRealtime(), selectedProfile.mediaResumeDelayMs)
        if (yield == yieldingToMedia && callActive == yieldingToCall) return
        yieldingToMedia = yield
        yieldingToCall = callActive
        cancelRouteWork()
        recoveryBudget.reset()
        transientFailureCount = 0
        if (yield || callActive) {
            val device = BluetoothDeviceInfo(_currentRoute.value.bluetoothDeviceName ?: "Bluetooth")
            _currentRoute.value = CommunicationRoute()
            setWhatsAppStatus(WhatsAppRouteStatus.UNKNOWN)
            scoKeepAlive.stop()
            commDeviceManager.clearCommunicationDevice()
            releaseAudioMode()
            updateState(RouterState.RouteDegraded(device, if (yield) RouterState.MEDIA_PLAYBACK_REASON else "Outro aplicativo está usando comunicação. Aguardando liberar."))
            AppLogger.i(TAG, "${if (yield) "MEDIA_YIELD" else "CALL_YIELD"}: musicActive=$musicActive, recordingActive=$recordingActive; solicitação liberada")
        } else {
            AppLogger.i(TAG, "ROUTE_RESUME: preparando microfone após mídia/comunicação")
            triggerAsyncRouteEvaluation()
        }
    }

    private fun cancelRouteWork() {
        evaluationJob?.cancel(); evaluationJob = null
        recoveryJob?.cancel(); recoveryJob = null
        transientRecheckJob?.cancel(); transientRecheckJob = null
        transientFailureCount = 0
    }

    private fun anotherAppCommunicating(): Boolean = audioManager.mode != AudioManager.MODE_NORMAL &&
        !(modeRequested && audioManager.mode == selectedProfile.targetAudioMode)

    private fun requestAudioModeOnce() {
        if (modeAttempted) return
        modeAttempted = true
        if (selectedProfile.targetAudioMode == AudioManager.MODE_NORMAL || audioManager.mode != AudioManager.MODE_NORMAL) return
        audioManager.mode = selectedProfile.targetAudioMode
        modeRequested = true
        AppLogger.i("MODE_REQUEST", "solicitação única: ${selectedProfile.targetAudioMode}; sem reafirmação automática")
    }

    private fun releaseAudioMode() {
        if (modeRequested) {
            try { audioManager.mode = AudioManager.MODE_NORMAL }
            catch (e: Exception) { AppLogger.e(TAG, "Falha ao liberar solicitação própria de modo", e) }
            AppLogger.i("MODE_RELEASE", "solicitação própria removida")
        }
        modeRequested = false
        modeAttempted = false
    }

    private fun logProfile() = AppLogger.i("PROFILE_APPLIED", "code=${selectedProfile.code}; mode=${selectedProfile.targetAudioMode}; " +
        "keeper=${selectedProfile.keepAliveStrategy}; rate=${selectedProfile.keepAliveSampleRate}; bufferMs=${selectedProfile.keepAliveBufferMs}; " +
        "yieldMedia=${selectedProfile.releaseForMedia}; resumeMs=${selectedProfile.mediaResumeDelayMs}; eco=${selectedProfile.ecoPolling}; " +
        "pollStableMs=${selectedProfile.routeControlStableMs}; pollUnstableMs=${selectedProfile.routeControlUnstableMs}; " +
        "watchdogMs=${selectedProfile.watchdogIntervalMs}; transientMs=${selectedProfile.transientRecheckMs}; " +
        "selectTimeoutMs=${selectedProfile.selectionTimeoutMs}; reassert=${selectedProfile.reassertOnTransient}")

    private fun logHeartbeat() {
        try {
            val power = appContext.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
            val battery = appContext.getSystemService(Context.BATTERY_SERVICE) as android.os.BatteryManager
            val current = commDeviceManager.getCurrentCommunicationDevice()
            AppLogger.i("ROUTE_HEARTBEAT", "state=${_routerState.value.javaClass.simpleName}; mode=${audioManager.mode}; " +
                "comm=${current?.id}/${current?.type}; hfp=${bluetoothHfpManager.hfpAudioState.value}; " +
                "keeper=${scoKeepAlive.describe()}; music=${audioManager.isMusicActive}; capture=${audioActivity.captureSummary}; " +
                "routeLoss=$routeLossCount; scoDisconnect=$scoDisconnectCount; recovery=$recoveryCount; " +
                "interactive=${power.isInteractive}; idle=${power.isDeviceIdleMode}; powerSave=${power.isPowerSaveMode}; " +
                "battery=${battery.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY)}; " +
                "mediaVolume=${audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)}; callVolume=${audioManager.getStreamVolume(AudioManager.STREAM_VOICE_CALL)}")
        } catch (e: Exception) { AppLogger.e(TAG, "Falha no snapshot periódico", e) }
    }

    private fun triggerAsyncRouteEvaluation() {
        if (!isRunning) return
        evaluateAgain = true
        if (evaluationJob?.isActive == true) return
        evaluationJob = engineScope?.launch {
            do {
                evaluateAgain = false
                evaluateAndApplyRoute()
            } while (evaluateAgain && isRunning)
        }
    }

    suspend fun evaluateAndApplyRoute() {
        if (!isRunning || yieldingToMedia || yieldingToCall) return
        try {
            coroutineContext.ensureActive()
            val target = commDeviceManager.findBestBluetoothCommunicationDevice()
            if (target != null && target.id != lastTargetId) {
                lastTargetId = target.id
                recoveryBudget.reset()
            }
            val name = target?.productName?.toString() ?: getConnectedBluetoothName()
            val device = BluetoothDeviceInfo(name ?: "Intercom")
            val current = commDeviceManager.getCurrentCommunicationDevice()
            val legacy = Build.VERSION.SDK_INT < Build.VERSION_CODES.S
            val input = target?.let(commDeviceManager::findMatchingInput)
            val targetAddress = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) target?.address.orEmpty() else ""
            val isBle = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && target?.type == AudioDeviceInfo.TYPE_BLE_HEADSET
            val hfpAudioConnected = bluetoothHfpManager.isAudioConnectedFor(targetAddress)
            val selectionMatches = commDeviceManager.hasOwnedRequest &&
                if (legacy) hfpAudioConnected else target != null && current?.id == target.id
            // BLE usa o perfil LE Audio, não HEADSET/HFP. Neste caminho a confirmação
            // pública é o endpoint de comunicação selecionado com entrada correspondente.
            val audioConnected = if (isBle) selectionMatches && input != null else hfpAudioConnected
            val health = RouteHealth(
                hasDevice = target != null,
                selectionMatches = selectionMatches,
                inputMatches = input != null,
                audioConnected = audioConnected,
                anotherAppCommunicating = anotherAppCommunicating()
            )
            // Observe short route changes without fighting Android's media transitions.
            // Persistent failures still use the bounded recovery path below.
            val routeWasReady = _currentRoute.value.isBidirectionalReady || transientFailureCount > 0
            if (shouldTolerateTransientFailure(health.action(), routeWasReady, transientFailureCount)) {
                transientFailureCount++
                AppLogger.w(TAG, "Oscilação observada sem forçar seleção (${health.action()}, #$transientFailureCount)")
                if (selectedProfile.reassertOnTransient && target != null) {
                    val accepted = commDeviceManager.reassertCommunicationDevice(target)
                    AppLogger.i("ROUTE_REASSERT", "profile=${selectedProfile.code}; accepted=$accepted; target=${target.id}")
                }
                if (_currentRoute.value.isBidirectionalReady) routeLossCount++
                _currentRoute.value = _currentRoute.value.copy(isBidirectionalReady = false)
                setWhatsAppStatus(WhatsAppRouteStatus.UNKNOWN)
                updateState(RouterState.RouteDegraded(device, "Rechecando oscilação do canal de voz"))
                if (transientRecheckJob?.isActive != true) transientRecheckJob = engineScope?.launch {
                    delay(selectedProfile.transientRecheckMs)
                    transientRecheckJob = null
                    triggerAsyncRouteEvaluation()
                }
                return
            }
            transientFailureCount = 0
            if (health.action() != RouteAction.YIELD_TO_CALL && (target != null || legacy && name != null) &&
                !recoveryBudget.exhausted && recoveryJob?.isActive != true) {
                requestAudioModeOnce()
                if (!scoKeepAlive.start(target, selectedProfile)) {
                    _currentRoute.value = _currentRoute.value.copy(isBidirectionalReady = false)
                    setWhatsAppStatus(WhatsAppRouteStatus.UNKNOWN)
                    updateState(RouterState.RouteDegraded(device, "Falha na sustentação de áudio; tentando recuperar"))
                    scheduleRecovery("Não foi possível sustentar a solicitação de rota")
                    return
                }
            }
            if (health.action() == RouteAction.KEEP && target != null &&
                (!selectedProfile.useSilenceKeepAlive || scoKeepAlive.isActive())) {
                val route = CommunicationRoute(input, target, current, name,
                    if (target.type == AudioDeviceInfo.TYPE_BLE_HEADSET) "LE Audio" else "HFP/SCO", true)
                _currentRoute.value = route
                if (_routerState.value !is RouterState.RouteReady || (_routerState.value as RouterState.RouteReady).route?.outputDevice?.id != target.id) {
                    routePreparationTimeMs = SystemClock.elapsedRealtime() - startedAt
                    updateState(RouterState.RouteReady(device.copy(isScoConnected = true),
                        routePreparationTimeMs = routePreparationTimeMs, route = route))
                    setWhatsAppStatus(WhatsAppRouteStatus.ROUTE_PREPARED)
                }
                // Never reassert MODE_IN_COMMUNICATION from route callbacks. WhatsApp
                // owns its own recording mode; repeated mode changes cut capture.
                recoveryJob?.cancel(); recoveryJob = null
                transientRecheckJob?.cancel(); transientRecheckJob = null
                if (recoveryStartedAt > 0) {
                    lastRecoveryDuration = SystemClock.elapsedRealtime() - recoveryStartedAt
                    recoveryCount++
                    recoveryStartedAt = 0
                }
                recoveryBudget.reset()
                return
            }
            if (_currentRoute.value.isBidirectionalReady) {
                routeLossCount++
                recoveryBudget.reset()
            }
            _currentRoute.value = CommunicationRoute(inputDevice = input, outputDevice = target,
                communicationDevice = current, bluetoothDeviceName = name, isBidirectionalReady = false)
            setWhatsAppStatus(WhatsAppRouteStatus.UNKNOWN)
            if (health.action() == RouteAction.YIELD_TO_CALL) {
                recoveryJob?.cancel(); recoveryJob = null
                scoKeepAlive.stop()
                commDeviceManager.clearCommunicationDevice()
                releaseAudioMode()
                updateState(RouterState.RouteDegraded(device, "Outro aplicativo está usando comunicação. Aguardando liberar."))
                return
            }
            if (name == null) {
                recoveryJob?.cancel(); recoveryJob = null
                recoveryBudget.reset()
                recoveryStartedAt = 0
                scoKeepAlive.stop()
                releaseAudioMode()
                lastTargetId = null
                commDeviceManager.clearCommunicationDevice()
                updateState(RouterState.Disconnected)
                return
            }
            // Pending backoff and terminal failure may be interrupted only by a healthy route or hardware change.
            if (recoveryJob?.isActive == true) return
            if (recoveryBudget.exhausted && _routerState.value is RouterState.RouteLost) return
            if (target == null && !legacy) {
                scoKeepAlive.stop()
                updateState(RouterState.BluetoothConnected(device))
                scheduleRecovery("Canal de comunicação ainda indisponível")
                return
            }
            // BUG-03: No Android 8-11 (API 26-30), target pode ser null porque
            // availableCommunicationDevices não existe e getDevices(OUTPUTS) pode não listar
            // SCO ainda. Se name != null (HFP conectado via proxy), precisamos iniciar SCO
            // diretamente via requestLegacySco() em vez de ficar em scheduleRecovery infinito.
            if (target == null && legacy) {
                updateState(RouterState.CommunicationDeviceSelected(device))
                val accepted = commDeviceManager.requestLegacySco()
                coroutineContext.ensureActive()
                if (!isRunning) return
                if (!accepted) {
                    scheduleRecovery("Android não confirmou SCO legado")
                    return
                }
                evaluateAgain = true
                updateState(RouterState.AudioConnecting(device))
                scheduleRecovery("Aguardando confirmação de áudio SCO legado")
                return
            }
            if (health.action() == RouteAction.SELECT || (legacy && !audioConnected) ||
                (!audioConnected && recoveryBudget.attempt > 0)) {
                updateState(RouterState.CommunicationDeviceSelected(device))
                val accepted = if (legacy) commDeviceManager.requestLegacySco() else
                    commDeviceManager.selectCommunicationDeviceWithConfirmation(target!!, selectedProfile.selectionTimeoutMs,
                        forceRequest = !audioConnected)
                coroutineContext.ensureActive()
                if (!isRunning) return
                if (!accepted) {
                    scheduleRecovery("Android não confirmou o dispositivo solicitado")
                    return
                }
                // Re-read after selection. Never publish RouteReady from the pre-selection snapshot.
                evaluateAgain = true
                updateState(RouterState.AudioConnecting(device))
                scheduleRecovery("Aguardando confirmação de áudio Bluetooth")
                return
            }
            updateState(RouterState.RouteDegraded(device, "Dispositivo selecionado; aguardando confirmação do canal de voz"))
            scheduleRecovery("Canal bidirecional ainda não confirmado")
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: SecurityException) {
            setWhatsAppStatus(WhatsAppRouteStatus.FAILED)
            updateState(RouterState.Error("Permissão Bluetooth indisponível. Abra o app e conceda a permissão."))
        } catch (e: Exception) {
            AppLogger.e(TAG, "Falha ao avaliar rota", e)
            scheduleRecovery(e.message ?: "Falha na rota")
        }
    }

    private fun scheduleRecovery(reason: String) {
        if (!isRunning || recoveryJob?.isActive == true) return
        val wait = recoveryBudget.nextDelay()
        if (wait == null) {
            scoKeepAlive.stop()
            commDeviceManager.clearCommunicationDevice()
            releaseAudioMode()
            updateState(RouterState.RouteLost("$reason. Desligue e ligue o roteador para tentar novamente."))
            setWhatsAppStatus(WhatsAppRouteStatus.FAILED)
            return
        }
        if (recoveryStartedAt == 0L) recoveryStartedAt = SystemClock.elapsedRealtime()
        AppLogger.i("ROUTE_RETRY", "attempt=${recoveryBudget.attempt}; delayMs=$wait; reason=$reason; capture=${audioActivity.recordingActive}")
        recoveryJob = engineScope?.launch {
            delay(wait)
            recoveryJob = null
            updateState(RouterState.Recovering(null, recoveryBudget.attempt))
            triggerAsyncRouteEvaluation()
        }
    }

    @SuppressLint("MissingPermission")
    fun getConnectedBluetoothName(): String? = try {
        bluetoothHfpManager.refreshConnectedDevice()?.name
            ?: commDeviceManager.findBestBluetoothCommunicationDevice()?.productName?.toString()
    } catch (e: SecurityException) { null }

    private fun updateState(state: RouterState) {
        val previous = _routerState.value
        if (previous == state) return
        _routerState.value = state
        RouterStateHolder.updateState(state)
        val entry = RouteEvent(event = "STATE_CHANGED", previousState = previous.javaClass.simpleName,
            newState = state.javaClass.simpleName, reason = when (state) {
                is RouterState.Error -> state.message
                is RouterState.RouteLost -> state.reason
                is RouterState.RouteDegraded -> state.reason
                else -> null
            })
        synchronized(events) {
            if (events.size == 100) events.removeFirst()
            events.addLast(entry)
        }
        AppLogger.i(TAG, "${entry.previousState} -> ${entry.newState} ${entry.reason.orEmpty()}")
    }

    private fun setWhatsAppStatus(status: WhatsAppRouteStatus) = RouterStateHolder.updateWhatsAppStatus(status)
    fun markUserValidatedWhatsApp() {
        if (_routerState.value is RouterState.RouteReady) {
            setWhatsAppStatus(WhatsAppRouteStatus.USER_VALIDATED)
            AppLogger.i("USER_VALIDATION", "Usuário marcou teste WhatsApp como aprovado")
        }
    }

    fun stopEngine() {
        if (!isRunning) return
        isRunning = false
        AppLogger.i("ROUTER_STOP", "routeLoss=$routeLossCount; scoDisconnect=$scoDisconnectCount; recovery=$recoveryCount")
        yieldingToMedia = false
        yieldingToCall = false
        mediaRoutePolicy.reset()
        engineScope?.cancel(); engineScope = null
        evaluationJob = null; recoveryJob = null; evaluateAgain = false
        transientFailureCount = 0
        routeMonitor.stopMonitoring()
        audioActivity.stop()
        bluetoothHfpManager.stop()
        scoKeepAlive.stop()
        commDeviceManager.clearCommunicationDevice()
        releaseAudioMode()
        _currentRoute.value = CommunicationRoute()
        setWhatsAppStatus(WhatsAppRouteStatus.UNKNOWN)
        updateState(RouterState.Disconnected)
    }

    fun getFullDiagnostics(): AudioDiagnostics {
        val inputs = audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)
        val outputs = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        val current = commDeviceManager.getCurrentCommunicationDevice()
        val route = _currentRoute.value
        val hfp = bluetoothHfpManager.detectActualBluetoothAudioState()
        return AudioDiagnostics(
            manufacturer = Build.MANUFACTURER, model = Build.MODEL, androidVersion = Build.VERSION.RELEASE,
            sdk = Build.VERSION.SDK_INT, build = Build.DISPLAY,
            bluetoothDevice = getConnectedBluetoothName() ?: "Nenhum intercom conectado",
            bluetoothProfile = route.bluetoothProfile ?: "Não confirmado", hfpAudioState = hfp.hfpAudioState.label,
            scoCodec = "NOT_EXPOSED", communicationDevice = current?.let { "${it.productName} (ID=${it.id})" } ?: "Nenhum",
            audioMode = when (audioManager.mode) {
                AudioManager.MODE_NORMAL -> "MODE_NORMAL (0)"
                AudioManager.MODE_IN_CALL -> "MODE_IN_CALL (2)"
                AudioManager.MODE_IN_COMMUNICATION -> "MODE_IN_COMMUNICATION (3)"
                else -> "${audioManager.mode}"
            },
            routeState = _routerState.value.javaClass.simpleName,
            inputAvailable = route.inputDevice != null,
            outputAvailable = route.outputDevice != null,
            isBidirectionalReady = isRunning && route.isBidirectionalReady,
            routePreparationTimeMs = routePreparationTimeMs,
            routeLossCount = routeLossCount, recoveryCount = recoveryCount, scoDisconnectCount = scoDisconnectCount,
            communicationDeviceChangeCount = communicationDeviceChangeCount,
            lastRecoveryDurationMs = lastRecoveryDuration,
            scoKeepAliveState = scoKeepAlive.describe(), audioFocusState = "LIVRE (não solicitado)",
            whatsappStatus = whatsappStatus.value,
            inputDevices = inputs.map { "${it.productName} [Tipo=${it.type}, ID=${it.id}]" },
            outputDevices = outputs.map { "${it.productName} [Tipo=${it.type}, ID=${it.id}]" },
            recentEvents = synchronized(events) { events.toList() }, hardwareProfileName = "${profile.profileName} / ${selectedProfile.code}"
        )
    }

    companion object { private const val TAG = "BTMIC_ROUTING" }
}
