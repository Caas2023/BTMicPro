package com.btmicpro.core

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
 * AudioRouteMonitor — Monitor Contínuo de Hardware de Áudio e Bluetooth (Itens 16, 17, 18, 70, 71).
 *
 * Responsabilidades:
 * 1. Escutar adições e remoções de dispositivos de áudio via AudioDeviceCallback.
 * 2. Escutar OnCommunicationDeviceChangedListener (Android 13+) e OnModeChangedListener (Android 12+).
 * 3. Aplicar debounce de 250ms para prevenir tempestades de callbacks do SO.
 * 4. Capturar AudioRouteSnapshot e calcular RouteDiffType para evitar chamadas repetitivas de roteamento.
 */
class AudioRouteMonitor(
    private val context: Context,
    private val onRouteChange: (RouteDiffType) -> Unit
) {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val handler = Handler(Looper.getMainLooper())
    private val mainExecutor = Executor { handler.post(it) }

    private var isMonitoring = false
    private var modeListener: AudioManager.OnModeChangedListener? = null
    private var commListener: Any? = null

    private var lastSnapshot: AudioRouteSnapshot? = null

    // Callback nativo de conexão/desconexão de dispositivos de áudio
    private val audioDeviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
            if (!isMonitoring) return
            Log.d(TAG, "AudioDeviceCallback: dispositivo(s) adicionado(s): ${addedDevices?.joinToString { it.productName.toString() }}")
            triggerDebouncedRouteEvaluation()
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
            if (!isMonitoring) return
            Log.d(TAG, "AudioDeviceCallback: dispositivo(s) removido(s): ${removedDevices?.joinToString { it.productName.toString() }}")
            triggerDebouncedRouteEvaluation()
        }
    }

    private val debounceRunnable = Runnable {
        val currentSnapshot = captureSnapshot()
        val diff = computeDiff(lastSnapshot, currentSnapshot)
        lastSnapshot = currentSnapshot

        Log.d(TAG, "Reavaliação de rota disparada. Diff detectado: $diff")
        onRouteChange(diff)
    }

    /**
     * Inicia o monitoramento de áudio com os callbacks disponíveis no sistema operacional.
     */
    fun startMonitoring() {
        if (isMonitoring) return
        isMonitoring = true
        Log.i(TAG, "Iniciando AudioRouteMonitor V5")

        lastSnapshot = captureSnapshot()

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
     * Dispara a revalidação de rota com debounce de 250ms (Item 18 do Prompt Master).
     */
    fun triggerDebouncedRouteEvaluation() {
        handler.removeCallbacks(debounceRunnable)
        handler.postDelayed(debounceRunnable, 250L)
    }

    /**
     * Captura um snapshot instantâneo do estado do subsistema de áudio.
     */
    fun captureSnapshot(): AudioRouteSnapshot {
        val commDev = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try { audioManager.communicationDevice } catch (e: Exception) { null }
        } else null

        val inputs = try {
            audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS).map { it.id }.toSet()
        } catch (e: Exception) { emptySet() }

        val outputs = try {
            audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).map { it.id }.toSet()
        } catch (e: Exception) { emptySet() }

        return AudioRouteSnapshot(
            timestamp = System.currentTimeMillis(),
            communicationDeviceId = commDev?.id,
            communicationDeviceName = commDev?.productName?.toString(),
            inputDeviceIds = inputs,
            outputDeviceIds = outputs,
            audioMode = audioManager.mode
        )
    }

    /**
     * Compara dois snapshots e retorna o tipo de alteração detectada (Item 71 do Prompt Master).
     */
    fun computeDiff(old: AudioRouteSnapshot?, current: AudioRouteSnapshot): RouteDiffType {
        return computeDiffInternal(old, current)
    }

    /**
     * Encerra o monitoramento e remove os ouvintes.
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

        lastSnapshot = null
        Log.i(TAG, "AudioRouteMonitor encerrado.")
    }

    companion object {
        private const val TAG = "BTMIC_ROUTE_MONITOR"

        fun computeDiffInternal(old: AudioRouteSnapshot?, current: AudioRouteSnapshot): RouteDiffType {
            if (old == null) return RouteDiffType.DEVICE_CHANGED

            return when {
                old.communicationDeviceId != current.communicationDeviceId -> RouteDiffType.COMMUNICATION_CHANGED
                old.inputDeviceIds != current.inputDeviceIds -> RouteDiffType.INPUT_CHANGED
                old.outputDeviceIds != current.outputDeviceIds -> RouteDiffType.OUTPUT_CHANGED
                old.audioMode != current.audioMode -> RouteDiffType.AUDIO_MODE_CHANGED
                else -> RouteDiffType.NO_CHANGE
            }
        }
    }
}
