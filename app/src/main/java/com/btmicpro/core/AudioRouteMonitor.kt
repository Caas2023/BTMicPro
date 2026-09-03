package com.btmicpro.core

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.util.concurrent.Executor

/**
 * AudioRouteMonitor — Monitor contínuo e responsivo de eventos do subsistema de áudio e Bluetooth.
 * Detecta alterações de conexões físicas, modo de áudio do sistema e mudanças de Communication Device.
 */
class AudioRouteMonitor(
    private val context: Context,
    private val onRouteChange: () -> Unit
) {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val handler = Handler(Looper.getMainLooper())
    private val mainExecutor = Executor { handler.post(it) }

    private var isMonitoring = false
    private var modeListener: AudioManager.OnModeChangedListener? = null
    private var commListener: Any? = null

    // Callback nativo de conexão/desconexão de dispositivos de áudio
    private val audioDeviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
            if (!isMonitoring) return
            Log.d(TAG, "AudioDeviceCallback: dispositivo(s) adicionado(s)")
            triggerDebouncedRouteEvaluation()
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
            if (!isMonitoring) return
            Log.d(TAG, "AudioDeviceCallback: dispositivo(s) removido(s)")
            triggerDebouncedRouteEvaluation()
        }
    }

    private var debounceRunnable = Runnable { onRouteChange() }

    /**
     * Inicia o monitoramento de áudio com os callbacks disponíveis no sistema.
     */
    fun startMonitoring() {
        if (isMonitoring) return
        isMonitoring = true
        Log.i(TAG, "Iniciando AudioRouteMonitor")

        try {
            audioManager.registerAudioDeviceCallback(audioDeviceCallback, handler)
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao registrar AudioDeviceCallback", e)
        }

        // Listener de modo de áudio (Android 12+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            modeListener = AudioManager.OnModeChangedListener { newMode ->
                if (!isMonitoring) return@OnModeChangedListener
                Log.d(TAG, "OnModeChangedListener: modo de áudio alterado para $newMode")
                triggerDebouncedRouteEvaluation()
            }
            try {
                audioManager.addOnModeChangedListener(mainExecutor, modeListener!!)
            } catch (e: Exception) {
                Log.e(TAG, "Erro ao registrar OnModeChangedListener", e)
            }
        }

        // Listener de dispositivo de comunicação (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val listener = AudioManager.OnCommunicationDeviceChangedListener { device ->
                if (!isMonitoring) return@OnCommunicationDeviceChangedListener
                Log.d(TAG, "OnCommunicationDeviceChangedListener: dispositivo alterado para ${device?.productName}")
                triggerDebouncedRouteEvaluation()
            }
            commListener = listener
            try {
                audioManager.addOnCommunicationDeviceChangedListener(mainExecutor, listener)
            } catch (e: Exception) {
                Log.e(TAG, "Erro ao registrar OnCommunicationDeviceChangedListener", e)
            }
        }
    }

    /**
     * Dispara a revalidação de rota com debounce de 250ms para evitar tempestade de eventos.
     */
    fun triggerDebouncedRouteEvaluation() {
        handler.removeCallbacks(debounceRunnable)
        handler.postDelayed(debounceRunnable, 250L)
    }

    /**
     * Encerra o monitoramento e desregistra todos os listeners do sistema operacional.
     */
    fun stopMonitoring() {
        if (!isMonitoring) return
        isMonitoring = false
        handler.removeCallbacks(debounceRunnable)

        try {
            audioManager.unregisterAudioDeviceCallback(audioDeviceCallback)
        } catch (ignored: Exception) {}

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && modeListener != null) {
            try {
                audioManager.removeOnModeChangedListener(modeListener!!)
            } catch (ignored: Exception) {}
            modeListener = null
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && commListener != null) {
            try {
                val listener = commListener as AudioManager.OnCommunicationDeviceChangedListener
                audioManager.removeOnCommunicationDeviceChangedListener(listener)
            } catch (ignored: Exception) {}
            commListener = null
        }

        Log.i(TAG, "AudioRouteMonitor encerrado")
    }

    companion object {
        private const val TAG = "BTMIC_ROUTE_MONITOR"
    }
}
