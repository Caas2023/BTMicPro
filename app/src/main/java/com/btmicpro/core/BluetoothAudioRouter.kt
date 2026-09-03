package com.btmicpro.core

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
 * BluetoothAudioRouter V3.0 (Zero-Dropout & Instant Capture).
 * 
 * Garante roteamento imediato do microfone Bluetooth para o WhatsApp e outros apps.
 * Utiliza setCommunicationDevice nativo (Android 12+) e SilentAudioKeeper para manter
 * o canal SCO permanentemente ativo, eliminando o atraso de 2 a 3 segundos e impedindo
 * que o áudio seja gravado pelo microfone do aparelho.
 */
class BluetoothAudioRouter(
    private val context: Context,
    private val coroutineScope: CoroutineScope
) {
    private val audioManager: AudioManager =
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val _routerState = MutableStateFlow<RouterState>(RouterState.Inactive)
    val routerState: StateFlow<RouterState> = _routerState.asStateFlow()

    private var audioSwitch: AudioSwitch? = null
    private var isRouterRunning = false

    private val silentAudioKeeper = SilentAudioKeeper()

    private val watchdogHandler = Handler(Looper.getMainLooper())
    private var watchdogRunnable: Runnable? = null

    private var modeListener: AudioManager.OnModeChangedListener? = null
    private val modeExecutor: Executor = Executor { it.run() }

    private val audioDeviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
            if (isRouterRunning) {
                applyPreferredDevicesForCapture()
                updateStateForCurrentMode()
            }
        }
        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
            if (isRouterRunning) {
                updateStateForCurrentMode()
            }
        }
    }

    fun startRouting() {
        if (isRouterRunning) return
        isRouterRunning = true
        val initialState = RouterState.WaitingDevice
        _routerState.value = initialState
        RouterStateHolder.updateState(initialState)
        Log.d(TAG, "Iniciando V3.0: Roteamento Ativo + Keep-Alive Zero-Dropout")

        try { 
            audioManager.registerAudioDeviceCallback(audioDeviceCallback, null) 
        } catch (e: Exception) { 
            Log.e(TAG, "Erro ao registrar callback de áudio", e) 
        }

        // Listener de modo para reforçar roteamento em chamadas
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            modeListener = AudioManager.OnModeChangedListener { newMode ->
                Log.d(TAG, "Modo de áudio do sistema alterado: $newMode")
                if (!isRouterRunning) return@OnModeChangedListener
                applyPreferredDevicesForCapture()
                updateStateForCurrentMode()
            }
            try { 
                audioManager.addOnModeChangedListener(modeExecutor, modeListener!!) 
            } catch (e: Exception) { 
                Log.e(TAG, "Erro ao registrar mode listener", e) 
            }
        }

        initializeAudioSwitch()

        // 1. Aplica imediatamente as rotas para o microfone Bluetooth
        applyPreferredDevicesForCapture()

        // 2. Inicia o keep-alive silencioso para manter o canal SCO acordado
        try {
            silentAudioKeeper.start()
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao iniciar SilentAudioKeeper", e)
        }

        updateStateForCurrentMode()
        maximizeBluetoothVolume()
        startWatchdog()
    }

    private fun startWatchdog() {
        stopWatchdog()
        watchdogRunnable = object : Runnable {
            override fun run() {
                if (!isRouterRunning) return
                applyPreferredDevicesForCapture()
                updateStateForCurrentMode()
                watchdogHandler.postDelayed(this, 20000)
            }
        }
        watchdogHandler.postDelayed(watchdogRunnable!!, 20000)
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
                            Log.d(TAG, "AudioSwitch headset detectado: ${bt.name}")
                            selectDevice(bt)
                            activate()
                            applyPreferredDevicesForCapture()
                            val newState = RouterState.RoutingActive(BluetoothDeviceInfo(name = bt.name, isScoConnected = true))
                            _routerState.value = newState
                            RouterStateHolder.updateState(newState)
                        } else if (isRouterRunning && _routerState.value !is RouterState.RoutingActive) {
                            val waitState = RouterState.WaitingDevice
                            _routerState.value = waitState
                            RouterStateHolder.updateState(waitState)
                        }
                    }
                }
                activate()
            }
        } catch (e: Exception) { 
            Log.e(TAG, "Erro ao inicializar AudioSwitch", e) 
        }
    }

    private fun updateStateForCurrentMode() {
        val hasBt = hasBtDevice()
        val newState = if (hasBt) {
            val name = getConnectedBluetoothName() ?: "Fone Bluetooth"
            RouterState.RoutingActive(BluetoothDeviceInfo(name = name, isScoConnected = true))
        } else {
            RouterState.WaitingDevice
        }
        _routerState.value = newState
        RouterStateHolder.updateState(newState)
    }

    private fun hasBtDevice(): Boolean {
        return try {
            val inputs = audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)
            inputs.any { 
                it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO || 
                (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && it.type == AudioDeviceInfo.TYPE_BLE_HEADSET) 
            } || audioManager.availableCommunicationDevices.any { 
                it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO || it.type == AudioDeviceInfo.TYPE_BLE_HEADSET 
            }
        } catch (e: Exception) { false }
    }

    fun applyPreferredDevicesForCapture() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                // 1. API Oficial Android 12+ (setCommunicationDevice)
                val commDevices = audioManager.availableCommunicationDevices
                val btCommDevice = commDevices.find {
                    it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO || it.type == AudioDeviceInfo.TYPE_BLE_HEADSET
                }
                if (btCommDevice != null) {
                    val success = audioManager.setCommunicationDevice(btCommDevice)
                    Log.d(TAG, "setCommunicationDevice aplicado com sucesso=$success (${btCommDevice.productName})")
                }

                // 2. Presets de captura do sistema
                val inputs = audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)
                val btSco = inputs.find { 
                    it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO || it.type == AudioDeviceInfo.TYPE_BLE_HEADSET 
                }
                if (btSco != null) {
                    setPreferredDeviceForPreset(MediaRecorder.AudioSource.MIC, btSco)
                    setPreferredDeviceForPreset(MediaRecorder.AudioSource.VOICE_COMMUNICATION, btSco)
                    setPreferredDeviceForPreset(MediaRecorder.AudioSource.DEFAULT, btSco)
                    setPreferredDeviceForPreset(MediaRecorder.AudioSource.VOICE_RECOGNITION, btSco)
                    Log.d(TAG, "Presets de captura vinculados ao microfone Bluetooth SCO")
                }
            } catch (e: Exception) { 
                Log.e(TAG, "Erro em applyPreferredDevicesForCapture", e) 
            }
        } else {
            // Fallback legado para Android 11 e inferiores
            try {
                @Suppress("DEPRECATION")
                audioManager.startBluetoothSco()
                @Suppress("DEPRECATION")
                audioManager.isBluetoothScoOn = true
            } catch (e: Exception) {
                Log.e(TAG, "Erro no fallback startBluetoothSco legado", e)
            }
        }
    }

    private fun setPreferredDeviceForPreset(preset: Int, device: AudioDeviceInfo): Boolean {
        return try {
            val method = AudioManager::class.java.getMethod(
                "setPreferredDeviceForCapturePreset", 
                Int::class.javaPrimitiveType, 
                AudioDeviceInfo::class.java
            )
            method.invoke(audioManager, preset, device) as Boolean
        } catch (e: Exception) {
            false
        }
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
                Log.e(TAG, "Erro ao limpar rotas de comunicação", e) 
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

    private fun getConnectedBluetoothName(): String? {
        return try {
            val devs = audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)
            devs.find {
                it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && it.type == AudioDeviceInfo.TYPE_BLE_HEADSET)
            }?.productName?.toString() 
                ?: audioManager.availableCommunicationDevices.find { 
                    it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO || it.type == AudioDeviceInfo.TYPE_BLE_HEADSET 
                }?.productName?.toString()
        } catch (e: Exception) { null }
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
            val inactiveState = RouterState.Inactive
            _routerState.value = inactiveState
            RouterStateHolder.updateState(inactiveState)
            Log.d(TAG, "Roteador parado")
        } catch (e: Exception) { 
            Log.e(TAG, "Erro ao parar roteamento", e) 
        }
    }

    fun evaluateAndRouteBluetoothDevice() { 
        applyPreferredDevicesForCapture()
        updateStateForCurrentMode() 
    }

    private fun maximizeBluetoothVolume() {
        try {
            val maxVoice = audioManager.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL)
            audioManager.setStreamVolume(AudioManager.STREAM_VOICE_CALL, maxVoice, 0)
            val maxMusic = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, maxMusic, 0)
            Log.d(TAG, "Volumes de chamada e mídia elevados ao máximo")
        } catch (e: Exception) { 
            Log.e(TAG, "Erro ao maximizar volume", e) 
        }
    }

    companion object { 
        private const val TAG = "BluetoothAudioRouter" 
    }
}
