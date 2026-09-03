package com.btmicpro.core

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.util.Log

/**
 * Estados operacionais do keep-alive experimental (Item 26 do Prompt Master).
 */
enum class ScoKeepAliveState {
    DISABLED,
    TESTING,
    ACTIVE,
    FAILED
}

/**
 * ExperimentalScoKeepAlive — Componente estritamente experimental para manter o canal SCO ativo (Itens 26, 27, 28).
 *
 * Diretrizes:
 * - Não presume mSBC nem promete manter o canal vivo permanentemente sem comprovação.
 * - Desativado por padrão (useExperimentalKeepAlive = false).
 * - Não interfere no áudio do WhatsApp (não causa mute, nem rouba foco, nem emite som perceptível).
 * - Se detectar qualquer falha ou interferência, transita imediatamente para FAILED e libera o AudioTrack.
 */
class ExperimentalScoKeepAlive {

    var state: ScoKeepAliveState = ScoKeepAliveState.DISABLED
        private set

    private var audioTrack: AudioTrack? = null
    private var isRunning = false
    private var playbackThread: Thread? = null

    /**
     * Inicia o keep-alive experimental com geração de PCM silencioso em baixo consumo.
     */
    @Synchronized
    fun start(sampleRate: Int = 16000): Boolean {
        if (state == ScoKeepAliveState.ACTIVE) {
            Log.d(TAG, "ExperimentalScoKeepAlive já está ativo.")
            return true
        }

        state = ScoKeepAliveState.TESTING
        Log.i(TAG, "Iniciando teste de keep-alive experimental em ${sampleRate}Hz...")

        try {
            val channelConfig = AudioFormat.CHANNEL_OUT_MONO
            val audioFormat = AudioFormat.ENCODING_PCM_16BIT
            val minBufferSize = AudioTrack.getMinBufferSize(sampleRate, channelConfig, audioFormat)

            if (minBufferSize <= 0) {
                Log.w(TAG, "Tamanho de buffer inválido ($minBufferSize) para taxa $sampleRate.")
                state = ScoKeepAliveState.FAILED
                return false
            }

            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()

            val format = AudioFormat.Builder()
                .setSampleRate(sampleRate)
                .setEncoding(audioFormat)
                .setChannelMask(channelConfig)
                .build()

            audioTrack = AudioTrack.Builder()
                .setAudioAttributes(audioAttributes)
                .setAudioFormat(format)
                .setBufferSizeInBytes(minBufferSize * 2)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .apply {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
                    }
                }
                .build()

            val silenceBuffer = ShortArray(minBufferSize / 2) // Todos zeros = silêncio digital absoluto

            audioTrack?.let { track ->
                track.play()
                isRunning = true

                playbackThread = Thread {
                    android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND)
                    try {
                        while (isRunning && track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                            track.write(silenceBuffer, 0, silenceBuffer.size)
                            Thread.sleep(30) // Reduz taxa de ciclo de CPU
                        }
                    } catch (e: InterruptedException) {
                        Log.d(TAG, "Thread de keep-alive experimental interrompida.")
                    } catch (e: Exception) {
                        Log.e(TAG, "Erro no loop do keep-alive experimental", e)
                        state = ScoKeepAliveState.FAILED
                    }
                }.apply {
                    name = "ExperimentalScoKeepAlive"
                    isDaemon = true
                    start()
                }

                state = ScoKeepAliveState.ACTIVE
                Log.i(TAG, "ExperimentalScoKeepAlive iniciado com estado ACTIVE.")
                return true
            }

            state = ScoKeepAliveState.FAILED
            return false

        } catch (e: Exception) {
            Log.e(TAG, "Falha ao inicializar ExperimentalScoKeepAlive", e)
            stop()
            state = ScoKeepAliveState.FAILED
            return false
        }
    }

    /**
     * Interrompe o keep-alive e libera recursos de áudio.
     */
    @Synchronized
    fun stop() {
        isRunning = false
        playbackThread?.interrupt()
        playbackThread = null

        try {
            audioTrack?.let { track ->
                try {
                    if (track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                        track.stop()
                    }
                } catch (ignored: Exception) {}
                track.release()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao liberar AudioTrack do keep-alive", e)
        } finally {
            audioTrack = null
            if (state != ScoKeepAliveState.FAILED) {
                state = ScoKeepAliveState.DISABLED
            }
            Log.d(TAG, "ExperimentalScoKeepAlive finalizado. Estado: $state")
        }
    }

    fun isActive(): Boolean = (state == ScoKeepAliveState.ACTIVE) && (audioTrack != null)

    companion object {
        private const val TAG = "BTMIC_SCO_KEEPALIVE"
    }
}

/**
 * Alias de compatibilidade com o nome legado.
 */
typealias SilentAudioKeeper = ExperimentalScoKeepAlive

