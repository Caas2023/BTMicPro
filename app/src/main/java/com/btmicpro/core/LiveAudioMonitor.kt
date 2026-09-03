package com.btmicpro.core

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.os.Build
import android.os.Process
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.max

/**
 * LiveAudioMonitor — Motor de escuta e monitoramento em tempo real (Pass-Through / Hear-Through).
 * Tecnologia inspirada no Noise Uncanceller / Safe Headphones:
 * Captura o microfone do capacete/fone, passa pelo CleanVoiceDsp em baixíssima latência
 * e reproduz imediatamente nos fones de ouvido para que o motociclista verifique a clareza
 * da sua voz e a eficácia da redução do ruído de vento em tempo real.
 */
class LiveAudioMonitor(
    private val context: Context,
    private val coroutineScope: CoroutineScope
) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val _isMonitoring = MutableStateFlow(false)
    val isMonitoring: StateFlow<Boolean> = _isMonitoring.asStateFlow()

    private var monitorJob: Job? = null
    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null
    private val cleanVoiceDsp = CleanVoiceDsp(16000)

    @SuppressLint("MissingPermission")
    fun startMonitoring(
        denoiseIntensity: Float = 0.85f,
        bypassDsp: Boolean = false,
        volumeMultiplier: Float = 1.0f
    ) {
        if (_isMonitoring.value) return

        val sampleRate = 16000
        val channelConfigIn = AudioFormat.CHANNEL_IN_MONO
        val channelConfigOut = AudioFormat.CHANNEL_OUT_MONO
        val audioEncoding = AudioFormat.ENCODING_PCM_16BIT

        val minBufIn = AudioRecord.getMinBufferSize(sampleRate, channelConfigIn, audioEncoding)
        val minBufOut = AudioTrack.getMinBufferSize(sampleRate, channelConfigOut, audioEncoding)
        // Frame pequeno (320 amostras = 20ms a 16kHz) para latência mínima perceptível
        val frameSize = 320
        val bufferSize = max(frameSize * 2, max(minBufIn, minBufOut))

        try {
            val audioSource = if (bypassDsp) {
                MediaRecorder.AudioSource.UNPROCESSED
            } else {
                MediaRecorder.AudioSource.VOICE_RECOGNITION
            }

            audioRecord = AudioRecord(audioSource, sampleRate, channelConfigIn, audioEncoding, bufferSize)

            // Conecta ao fone Bluetooth se disponível
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val inputs = audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS)
                val btInput = inputs.find {
                    it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                    (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && it.type == AudioDeviceInfo.TYPE_BLE_HEADSET)
                }
                if (btInput != null) {
                    audioRecord?.preferredDevice = btInput
                    Log.d(TAG, "LiveMonitor: Gravando via preferredDevice: ${btInput.productName}")
                }
            }

            val attributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()

            val format = AudioFormat.Builder()
                .setSampleRate(sampleRate)
                .setChannelMask(channelConfigOut)
                .setEncoding(audioEncoding)
                .build()

            audioTrack = AudioTrack.Builder()
                .setAudioAttributes(attributes)
                .setAudioFormat(format)
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .apply {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
                    }
                }
                .build()

            cleanVoiceDsp.configureFilters(sampleRate)

            audioRecord?.startRecording()
            audioTrack?.play()
            _isMonitoring.value = true
            Log.d(TAG, "LiveAudioMonitor iniciado com sucesso a ${sampleRate}Hz")

            monitorJob = coroutineScope.launch(Dispatchers.IO) {
                Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
                val pcmBuffer = ShortArray(frameSize)

                try {
                    while (isActive && _isMonitoring.value) {
                        val readSamples = audioRecord?.read(pcmBuffer, 0, frameSize) ?: 0
                        if (readSamples > 0) {
                            // Aplica o CleanVoice DSP
                            cleanVoiceDsp.process(pcmBuffer, readSamples, denoiseIntensity, bypassDsp)

                            // Aplica o multiplicador de ganho de escuta
                            if (volumeMultiplier != 1.0f) {
                                for (i in 0 until readSamples) {
                                    val amplified = (pcmBuffer[i] * volumeMultiplier)
                                    pcmBuffer[i] = amplified.coerceIn(-32768f, 32767f).toInt().toShort()
                                }
                            }

                            // Escreve direto no AudioTrack de baixa latência
                            audioTrack?.write(pcmBuffer, 0, readSamples)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Erro no loop do LiveAudioMonitor", e)
                } finally {
                    stopMonitoring()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Falha ao iniciar LiveAudioMonitor", e)
            stopMonitoring()
        }
    }

    fun stopMonitoring() {
        if (!_isMonitoring.value) return
        _isMonitoring.value = false
        monitorJob?.cancel()
        monitorJob = null

        try {
            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null
        } catch (ignored: Exception) {}

        try {
            audioTrack?.stop()
            audioTrack?.release()
            audioTrack = null
        } catch (ignored: Exception) {}

        Log.d(TAG, "LiveAudioMonitor interrompido")
    }

    companion object {
        private const val TAG = "LiveAudioMonitor"
    }
}
