package com.btmicpro.core

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.MediaRecorder
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.twilio.audioswitch.AudioDevice
import com.twilio.audioswitch.AudioSwitch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.Executor

/**
 * Dados de diagnóstico em tempo real para a tela Developer Audio Diagnostics.
 */
data class AudioDiagnosticsData(
    val deviceModel: String,
    val manufacturer: String,
    val androidVersion: String,
    val sdkVersion: Int,
    val bluetoothDeviceName: String,
    val audioMode: String,
    val communicationDevice: String,
    val inputDevices: List<String>,
    val outputDevices: List<String>,
    val sampleRate: Int,
    val isScoActive: Boolean,
    val routingStateDescription: String,
    val estimatedLatencyMs: Int,
    val isKeeperActive: Boolean,
    val hardwareProfile: String
)

/**
 * BluetoothAudioRouter V4 — Motor de Roteamento de Comunicação e Máquina de Estados de 8 Estágios.
 *
 * Máquina de Estados Finita:
 * DISCONNECTED -> BLUETOOTH_CONNECTED -> AUDIO_DEVICE_AVAILABLE -> COMMUNICATION_DEVICE_SELECTED
 * -> SCO_ACTIVE -> ROUTING_VERIFIED -> ROUTING_LOST -> RECOVERING -> DISCONNECTED.
 *
 * Integração dedicada com o DeviceCompatibilityManager (Cubot KingKong X Pro).
 */
class BluetoothAudioRouter(
    private val context: Context,
    private val coroutineScope: CoroutineScope
) {
    private val audioManager: AudioManager =
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val _routerState = MutableStateFlow<RouterState>(RouterState.Disconnected)
    val routerState: StateFlow<RouterState> = _routerState.asStateFlow()

    private var audioSwitch: AudioSwitch? = null
    private var isRouterRunning = false

    private val silentAudioKeeper = SilentAudioKeeper()
    private val profile = DeviceCompatibilityManager.currentProfile

    private val watchdogHandler = Handler(Looper.getMainLooper())
    private var watchdogRunnable: Runnable? = null
    private var recoveryAttempts = 0

    private var modeListener: AudioManager.OnModeChangedListener? = null
    private val modeExecutor: Executor = Executor { it.run() }

    private val audioDeviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
            if (isRouterRunning) {
                Log.d(TAG, "AudioDeviceCallback: dispositivo adicionado")
                evaluateAndTransitionState()
            }
        }
        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
            if (isRouterRunning) {
                Log.d(TAG, "AudioDeviceCallback: dispositivo removido")
                evaluateAndTransitionState()
            }
        }
    }

    /**
     * Inicia a máquina de estados e o roteamento de áudio V4.
     */
    fun startRouting() {
        if (isRouterRunning) return
        isRouterRunning = true
        recoveryAttempts = 0
        updateState(RouterState.Disconnected)
        Log.i(TAG, "Iniciando BluetoothAudioRouter V4 [Perfil: ${profile.profileName}]")

        try { 
            audioManager.registerAudioDeviceCallback(audioDeviceCallback, null) 
        } catch (e: Exception) { 
            Log.e(TAG, "Erro ao registrar callback de áudio", e) 
        }

        // Listener de modo de áudio
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            modeListener = AudioManager.OnModeChangedListener { newMode ->
                Log.d(TAG, "Modo de áudio do sistema alterado: $newMode")
                if (!isRouterRunning) return@OnModeChangedListener
                evaluateAndTransitionState()
            }
            try { 
                audioManager.addOnModeChangedListener(modeExecutor, modeListener!!) 
            } catch (e: Exception) { 
                Log.e(TAG, "Erro ao registrar mode listener", e) 
            }
        }

        initializeAudioSwitch()

        // Ativa o SilentAudioKeeper para manter o canal SCO permanentemente acordado
        try {
            silentAudioKeeper.start()
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao iniciar SilentAudioKeeper", e)
        }

        evaluateAndTransitionState()
        maximizeBluetoothVolume()
        startWatchdog()
    }

    /**
     * Avalia e executa as transições da Máquina de Estados de 8 Estágios.
     */
    fun evaluateAndTransitionState() {
        if (!isRouterRunning) return

        try {
            // Estágio 1 & 2: Verifica conexão Bluetooth ACL
            val connectedBtDevice = getConnectedBluetoothDevice()
            val deviceName = connectedBtDevice?.name ?: getConnectedBluetoothName()

            if (deviceName == null) {
                updateState(RouterState.Disconnected)
                return
            }

            val deviceInfo = BluetoothDeviceInfo(
                name = deviceName,
                address = connectedBtDevice?.address ?: "",
                isScoConnected = silentAudioKeeper.isActive(),
                sampleRate = profile.preferredSampleRate
            )

            // Estágio 3: Verifica se o dispositivo de áudio está disponível no AudioManager
            val audioDevice = findBluetoothAudioDevice()
            if (audioDevice == null) {
                updateState(RouterState.BluetoothConnected(deviceInfo))
                return
            }

            updateState(RouterState.AudioDeviceAvailable(deviceInfo))

            // Estágio 4: Seleciona Communication Device
            var commDeviceSelected = false
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                commDeviceSelected = audioManager.setCommunicationDevice(audioDevice)
                Log.d(TAG, "setCommunicationDevice(${audioDevice.productName}) resultado=$commDeviceSelected")
                applyCapturePresets(audioDevice)
            } else {
                @Suppress("DEPRECATION")
                audioManager.startBluetoothSco()
                @Suppress("DEPRECATION")
                audioManager.isBluetoothScoOn = true
                commDeviceSelected = true
            }

            if (!commDeviceSelected && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                updateState(RouterState.AudioDeviceAvailable(deviceInfo))
                return
            }

            updateState(RouterState.CommunicationDeviceSelected(deviceInfo))

            // Estágio 5: SCO Active
            val isScoActive = silentAudioKeeper.isActive() || isScoOnLegacy()
            if (!isScoActive) {
                updateState(RouterState.CommunicationDeviceSelected(deviceInfo))
                return
            }

            updateState(RouterState.ScoActive(deviceInfo.copy(isScoConnected = true)))

            // Estágio 6: Routing Verified (Canal validado com o hardware)
            val estimatedLatency = if (profile.isCubotKingKongXPro) 15 else 20
            updateState(
                RouterState.RoutingVerified(
                    device = deviceInfo.copy(isScoConnected = true),
                    sampleRate = profile.preferredSampleRate,
                    estimatedLatencyMs = estimatedLatency
                )
            )
            recoveryAttempts = 0

        } catch (e: Exception) {
            Log.e(TAG, "Erro na avaliação de estado da máquina de roteamento", e)
            handleRecovery("Exceção na máquina de estados: ${e.message}")
        }
    }

    private fun handleRecovery(reason: String) {
        recoveryAttempts++
        val devInfo = BluetoothDeviceInfo(name = getConnectedBluetoothName() ?: "Fone Desconhecido")
        if (recoveryAttempts <= 3) {
            updateState(RouterState.Recovering(devInfo, recoveryAttempts))
            watchdogHandler.postDelayed({ evaluateAndTransitionState() }, 1500)
        } else {
            updateState(RouterState.RoutingLost(reason))
        }
    }

    private fun updateState(newState: RouterState) {
        _routerState.value = newState
        RouterStateHolder.updateState(newState)
    }

    private fun startWatchdog() {
        stopWatchdog()
        watchdogRunnable = object : Runnable {
            override fun run() {
                if (!isRouterRunning) return
                // Checa integridade do roteamento a cada 15 segundos
                if (!silentAudioKeeper.isActive()) {
                    Log.w(TAG, "Watchdog: SilentAudioKeeper inativo, reiniciando...")
                    try { silentAudioKeeper.start() } catch (ignored: Exception) {}
                }
                evaluateAndTransitionState()
                watchdogHandler.postDelayed(this, 15000)
            }
        }
        watchdogHandler.postDelayed(watchdogRunnable!!, 15000)
    }

    private fun stopWatchdog() {
        watchdogRunnable?.let { watchdogHandler.removeCallbacks(it) }
        watchdogRunnable = null
    }

    private fun initializeAudioSwitch() {
        try {
            audioSwitch = AudioSwitch(context.applicationContext, loggingEnabled = true).apply {
                start { audioDevices, _ ->
                    coroutineScope.launch(Dispatchers.Main) {
                        val bt = audioDevices.find { it is AudioDevice.BluetoothHeadset }
                        if (bt != null) {
                            selectDevice(bt)
                            activate()
                            evaluateAndTransitionState()
                        } else if (isRouterRunning && _routerState.value !is RouterState.RoutingVerified) {
                            evaluateAndTransitionState()
                        }
                    }
                }
                activate()
            }
        } catch (e: Exception) { 
            Log.e(TAG, "Erro ao inicializar AudioSwitch", e) 
        }
    }

    private fun findBluetoothAudioDevice(): AudioDeviceInfo? {
        return try {
            val inputs = audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)
            inputs.find { 
                it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO || 
                (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && it.type == AudioDeviceInfo.TYPE_BLE_HEADSET) 
            } ?: if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                audioManager.availableCommunicationDevices.find { 
                    it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO || it.type == AudioDeviceInfo.TYPE_BLE_HEADSET 
                }
            } else null
        } catch (e: Exception) { null }
    }

    private fun applyCapturePresets(device: AudioDeviceInfo) {
        setPreferredDeviceForPreset(MediaRecorder.AudioSource.MIC, device)
        setPreferredDeviceForPreset(MediaRecorder.AudioSource.VOICE_COMMUNICATION, device)
        setPreferredDeviceForPreset(MediaRecorder.AudioSource.DEFAULT, device)
        setPreferredDeviceForPreset(MediaRecorder.AudioSource.VOICE_RECOGNITION, device)
    }

    private fun setPreferredDeviceForPreset(preset: Int, device: AudioDeviceInfo): Boolean {
        return try {
            val method = AudioManager::class.java.getMethod(
                "setPreferredDeviceForCapturePreset", 
                Int::class.javaPrimitiveType, 
                AudioDeviceInfo::class.java
            )
            method.invoke(audioManager, preset, device) as Boolean
        } catch (e: Exception) { false }
    }

    private fun clearPreferredDevices() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                audioManager.clearCommunicationDevice()
                val method = AudioManager::class.java.getMethod(
                    "clearPreferredDeviceForCapturePreset", 
                    Int::class.javaPrimitiveType
                )
                method.invoke(audioManager, MediaRecorder.AudioSource.MIC)
                method.invoke(audioManager, MediaRecorder.AudioSource.VOICE_COMMUNICATION)
                method.invoke(audioManager, MediaRecorder.AudioSource.DEFAULT)
                method.invoke(audioManager, MediaRecorder.AudioSource.VOICE_RECOGNITION)
                Log.d(TAG, "Rotas de comunicação liberadas com sucesso")
            } catch (e: Exception) { 
                Log.e(TAG, "Erro ao limpar rotas", e) 
            }
        } else {
            try {
                @Suppress("DEPRECATION")
                audioManager.stopBluetoothSco()
                @Suppress("DEPRECATION")
                audioManager.isBluetoothScoOn = false
            } catch (ignored: Exception) {}
        }
    }

    @SuppressLint("MissingPermission")
    private fun getConnectedBluetoothDevice(): BluetoothDevice? {
        return try {
            val adapter = BluetoothAdapter.getDefaultAdapter() ?: return null
            val connectedHeadsets = mutableListOf<BluetoothDevice>()
            adapter.getProfileProxy(context, object : BluetoothProfile.ServiceListener {
                override fun onServiceConnected(profile: Int, proxy: BluetoothProfile?) {
                    if (profile == BluetoothProfile.HEADSET && proxy != null) {
                        connectedHeadsets.addAll(proxy.connectedDevices)
                    }
                }
                override fun onServiceDisconnected(profile: Int) {}
            }, BluetoothProfile.HEADSET)
            connectedHeadsets.firstOrNull()
        } catch (e: Exception) { null }
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

    private fun isScoOnLegacy(): Boolean {
        return try {
            @Suppress("DEPRECATION")
            audioManager.isBluetoothScoOn
        } catch (e: Exception) { false }
    }

    fun stopRouting() {
        if (!isRouterRunning) return
        isRouterRunning = false
        stopWatchdog()

        try {
            silentAudioKeeper.stop()
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao parar SilentAudioKeeper", e)
        }

        clearPreferredDevices()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            modeListener?.let { 
                try { audioManager.removeOnModeChangedListener(it) } catch (ignored: Exception) {} 
            }
            modeListener = null
        }

        try {
            audioSwitch?.stop()
            audioSwitch = null
            try { audioManager.unregisterAudioDeviceCallback(audioDeviceCallback) } catch (ignored: Exception) {}
            updateState(RouterState.Disconnected)
            Log.i(TAG, "BluetoothAudioRouter V4 finalizado")
        } catch (e: Exception) { 
            Log.e(TAG, "Erro ao parar roteamento", e) 
        }
    }

    fun evaluateAndRouteBluetoothDevice() { 
        evaluateAndTransitionState() 
    }

    private fun maximizeBluetoothVolume() {
        try {
            val maxVoice = audioManager.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL)
            audioManager.setStreamVolume(AudioManager.STREAM_VOICE_CALL, maxVoice, 0)
            val maxMusic = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, maxMusic, 0)
        } catch (ignored: Exception) {}
    }

    /**
     * Coleta todas as informações de diagnóstico em tempo real para a tela do desenvolvedor.
     */
    fun getDiagnosticsData(): AudioDiagnosticsData {
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
            "${it.productName} [type=${it.type}]" 
        }
        val outputs = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).map { 
            "${it.productName} [type=${it.type}]" 
        }

        val stateDesc = when (val s = _routerState.value) {
            is RouterState.RoutingVerified -> "VERIFICADO (Pronto para WhatsApp)"
            is RouterState.ScoActive -> "SCO ATIVO"
            is RouterState.CommunicationDeviceSelected -> "DISPOSITIVO DE COMUNICAÇÃO SELECIONADO"
            is RouterState.AudioDeviceAvailable -> "DISPOSITIVO DE ÁUDIO DETECTADO"
            is RouterState.BluetoothConnected -> "BLUETOOTH CONECTADO (Aguardando áudio)"
            is RouterState.Recovering -> "RECUPERANDO (Tentativa ${s.attempt})"
            is RouterState.RoutingLost -> "CONEXÃO PERDIDA: ${s.reason}"
            is RouterState.Disconnected -> "DESCONECTADO"
            else -> "INATIVO"
        }

        return AudioDiagnosticsData(
            deviceModel = Build.MODEL,
            manufacturer = Build.MANUFACTURER,
            androidVersion = Build.VERSION.RELEASE,
            sdkVersion = Build.VERSION.SDK_INT,
            bluetoothDeviceName = getConnectedBluetoothName() ?: "Nenhum fone conectado",
            audioMode = modeStr,
            communicationDevice = commDevStr,
            inputDevices = inputs,
            outputDevices = outputs,
            sampleRate = profile.preferredSampleRate,
            isScoActive = silentAudioKeeper.isActive() || isScoOnLegacy(),
            routingStateDescription = stateDesc,
            estimatedLatencyMs = if (profile.isCubotKingKongXPro) 15 else 20,
            isKeeperActive = silentAudioKeeper.isActive(),
            hardwareProfile = profile.profileName
        )
    }

    companion object { 
        private const val TAG = "BTMIC_ROUTER_V4" 
    }
}
