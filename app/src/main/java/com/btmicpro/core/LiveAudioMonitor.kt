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
    private val audioEffectController = AudioEffectController()

    @Volatile
    private var returnVolume: Float = 0.0f

    @Synchronized
    fun setReturnVolume(volume: Float) {
        returnVolume = volume.coerceIn(0.0f, 1.0f)
        if (returnVolume > 0.0f) {
            if (_isMonitoring.value && audioTrack == null) {
                initAudioTrack()
            }
            try {
                audioTrack?.setVolume(returnVolume)
            } catch (ignored: Exception) {}
        } else {
            // Volume 0% = Mudo total: Libera o AudioTrack para NUNCA ocupar a saída de áudio dos fones
            releaseAudioTrack()
        }
    }

    fun getReturnVolume(): Float = returnVolume

    @Synchronized
    private fun initAudioTrack() {
        if (audioTrack != null) return
        try {
            val sampleRate = 16000
            val channelConfigOut = AudioFormat.CHANNEL_OUT_MONO
            val audioEncoding = AudioFormat.ENCODING_PCM_16BIT
            val minBufOut = AudioTrack.getMinBufferSize(sampleRate, channelConfigOut, audioEncoding)
            val bufferSize = max(320 * 2, minBufOut)

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

            audioTrack?.setVolume(returnVolume)
            audioTrack?.play()
            AppLogger.d(TAG, "AudioTrack de retorno iniciado sob demanda (Volume: ${(returnVolume * 100).toInt()}%)")
        } catch (e: Exception) {
            AppLogger.e(TAG, "Falha ao inicializar AudioTrack de retorno", e)
        }
    }

    @Synchronized
    private fun releaseAudioTrack() {
        try {
            audioTrack?.let { track ->
                if (track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                    track.stop()
                }
                track.release()
            }
        } catch (ignored: Exception) {}
        audioTrack = null
        AppLogger.d(TAG, "AudioTrack de retorno liberado (Fones 100% livres para WhatsApp/chamadas)")
    }

    @SuppressLint("MissingPermission")
    fun startMonitoring(
        denoiseIntensity: Float = 0.85f,
        bypassDsp: Boolean = false,
        initialVolume: Float = 0.0f,
        preset: RiderAudioPreset = RiderAudioPreset.NORMAL
    ) {
        if (_isMonitoring.value) return
        returnVolume = initialVolume.coerceIn(0.0f, 1.0f)
        cleanVoiceDsp.setPreset(preset)

        val sampleRate = 16000
        val channelConfigIn = AudioFormat.CHANNEL_IN_MONO
        val audioEncoding = AudioFormat.ENCODING_PCM_16BIT

        val minBufIn = AudioRecord.getMinBufferSize(sampleRate, channelConfigIn, audioEncoding)
        val frameSize = 320
        val bufferSize = max(frameSize * 2, minBufIn)

        try {
            val audioSource = if (bypassDsp) {
                MediaRecorder.AudioSource.UNPROCESSED
            } else {
                MediaRecorder.AudioSource.VOICE_COMMUNICATION
            }

            audioRecord = AudioRecord(audioSource, sampleRate, channelConfigIn, audioEncoding, bufferSize)

            // Ativa os efeitos de tratamento nativos de hardware do celular (NoiseSuppressor, AGC, AEC)
            val sessionId = audioRecord?.audioSessionId ?: 0
            if (sessionId != 0) {
                audioEffectController.attachToSession(sessionId)
            }

            // Conecta ao microfone Bluetooth se disponível
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

            cleanVoiceDsp.configureFilters(sampleRate)

            // Se o volume de retorno estiver ativo (>0%), inicia o AudioTrack.
            // Se estiver em 0% (Mudo), NÃO cria nem inicia o AudioTrack, deixando a saída 100% livre para o WhatsApp!
            if (returnVolume > 0.0f) {
                initAudioTrack()
            } else {
                releaseAudioTrack()
            }

            audioRecord?.startRecording()
            _isMonitoring.value = true
            AppLogger.i(TAG, "LiveAudioMonitor iniciado com sucesso a ${sampleRate}Hz (Retorno: ${(returnVolume * 100).toInt()}%, Preset: ${preset.name}, Denoise: ${denoiseIntensity})")

            monitorJob = coroutineScope.launch(Dispatchers.IO) {
                Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
                val pcmBuffer = ShortArray(frameSize)
                var frameCount = 0
                var windowPeak = 0
                var windowSumSquares = 0.0
                var windowSamples = 0
                var windowClipping = 0
                var consecutiveSilenceFrames = 0
                var lastLogTime = System.currentTimeMillis()

                try {
                    while (isActive && _isMonitoring.value) {
                        val readSamples = audioRecord?.read(pcmBuffer, 0, frameSize) ?: 0
                        if (readSamples > 0) {
                            // Medição acústica bruta pré-DSP (Item 10)
                            for (i in 0 until readSamples) {
                                val s = pcmBuffer[i].toInt()
                                val absVal = if (s < 0) -s else s
                                if (absVal > windowPeak) windowPeak = absVal
                                if (absVal >= 32700) windowClipping++
                                windowSumSquares += (s.toDouble() * s.toDouble())
                            }
                            windowSamples += readSamples
                            frameCount++

                            // Aplica o CleanVoice DSP
                            cleanVoiceDsp.process(pcmBuffer, readSamples, denoiseIntensity, bypassDsp)

                            // Se o volume de retorno no capacete estiver ativo (> 0%), reproduz no AudioTrack
                            val currentVol = returnVolume
                            if (currentVol > 0.0f) {
                                if (currentVol != 1.0f) {
                                    for (i in 0 until readSamples) {
                                        val amplified = (pcmBuffer[i] * currentVol)
                                        pcmBuffer[i] = amplified.coerceIn(-32768f, 32767f).toInt().toShort()
                                    }
                                }
                                audioTrack?.write(pcmBuffer, 0, readSamples)
                            }

                            // Telemetria periódica a cada 2 segundos (~100 frames)
                            val now = System.currentTimeMillis()
                            if (now - lastLogTime >= 2000L && windowSamples > 0) {
                                val meanSquare = windowSumSquares / windowSamples
                                val rms = kotlin.math.sqrt(meanSquare)
                                val rmsDb = if (rms > 0.0) 20.0 * kotlin.math.log10(rms / 32768.0) else -96.0
                                val peakDb = if (windowPeak > 0) 20.0 * kotlin.math.log10(windowPeak / 32768.0) else -96.0

                                val status = when {
                                    windowClipping > 0 -> "⚠️ CLIPPING (Saturando microfone)"
                                    rmsDb < -55.0 -> "ℹ️ SILÊNCIO (Sem voz detectada)"
                                    rmsDb in -35.0..-12.0 -> "✅ VOZ CLARA (Nível ideal)"
                                    else -> "OK (Captando sinal)"
                                }

                                AppLogger.audio(
                                    TAG,
                                    "🎤 Áudio Captado: RMS=${"%.1f".format(rmsDb)} dBFS | Pico=${"%.1f".format(peakDb)} dBFS | Clipes=$windowClipping | $status"
                                )

                                if (windowClipping > 5) {
                                    AppLogger.w(TAG, "⚠️ ALERTA DE CORTE/DISTORÇÃO: $windowClipping amostras saturadas detectadas no microfone do capacete!")
                                }

                                if (rmsDb < -60.0) {
                                    consecutiveSilenceFrames++
                                    if (consecutiveSilenceFrames >= 2) {
                                        AppLogger.w(TAG, "⚠️ ALERTA DE MICROFONE MUDO: Sinal extremamente baixo ou microfone não respondendo.")
                                    }
                                } else {
                                    consecutiveSilenceFrames = 0
                                }

                                // Reinicia acumuladores de janela
                                windowPeak = 0
                                windowSumSquares = 0.0
                                windowSamples = 0
                                windowClipping = 0
                                lastLogTime = now
                            }
                        } else if (readSamples < 0) {
                            AppLogger.e(TAG, "ERRO DE CAPTURA: AudioRecord.read retornou código de erro $readSamples")
                        }
                    }
                } catch (e: Exception) {
                    AppLogger.e(TAG, "Erro no loop de áudio do LiveAudioMonitor", e)
                } finally {
                    stopMonitoring()
                }
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Falha ao inicializar AudioRecord/AudioTrack no LiveAudioMonitor", e)
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

        try {
            audioEffectController.release()
        } catch (ignored: Exception) {}

        AppLogger.i(TAG, "LiveAudioMonitor interrompido.")
    }

    companion object {
        private const val TAG = "LiveAudioMonitor"
    }
}
