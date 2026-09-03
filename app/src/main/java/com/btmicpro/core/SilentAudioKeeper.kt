package com.btmicpro.core

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import android.util.Log

/**
 * Mantém um AudioTrack tocando silêncio em loop para impedir que o
 * sistema desligue o canal SCO Bluetooth por timeout.
 *
 * Sem fluxo de áudio real, o Android desliga o SCO em ~15-30s.
 * Com silêncio contínuo, o SCO fica estável por horas.
 *
 * Usa 16kHz mono que é o nativo do SCO mSBC - evita reamostragem.
 */
class SilentAudioKeeper {

    private var audioTrack: AudioTrack? = null
    private var isKeepingAlive = false

    /**
     * Inicia o keep-alive de áudio silencioso.
     * Deve ser chamado APÓS setar MODE_IN_COMMUNICATION.
     */
    fun start() {
        if (isKeepingAlive) {
            Log.d(TAG, "SilentAudioKeeper já ativo")
            return
        }

        try {
            val sampleRate = 16000 // Nativo mSBC SCO
            val channelConfig = AudioFormat.CHANNEL_OUT_MONO
            val audioFormat = AudioFormat.ENCODING_PCM_16BIT
            val bufferSize = AudioTrack.getMinBufferSize(sampleRate, channelConfig, audioFormat)

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
                .setBufferSizeInBytes(bufferSize * 2)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .apply {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
                    }
                }
                .build()

            // Buffer de silêncio (zeros) - PCM 16-bit mono
            val silenceBuffer = ShortArray(bufferSize / 2) // todos zeros = silêncio

            audioTrack?.let { track ->
                track.play()

                // Thread dedicada para bombear silêncio contínuo
                Thread {
                    android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_AUDIO)
                    try {
                        while (isKeepingAlive && track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                            track.write(silenceBuffer, 0, silenceBuffer.size)
                            // Pequeno sleep para não queimar CPU - 20ms = 320 amostras a 16kHz
                            Thread.sleep(20)
                        }
                    } catch (e: InterruptedException) {
                        Log.d(TAG, "Silent keeper thread interrompida")
                    } catch (e: Exception) {
                        Log.e(TAG, "Erro no silent keeper loop", e)
                    }
                }.apply {
                    name = "SilentAudioKeeper"
                    isDaemon = true
                    start()
                }

                isKeepingAlive = true
                Log.d(TAG, "SilentAudioKeeper iniciado a ${sampleRate}Hz - SCO manterá vivo")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Falha ao iniciar SilentAudioKeeper", e)
            stop()
        }
    }

    /**
     * Para o keep-alive e libera recursos.
     */
    fun stop() {
        isKeepingAlive = false
        try {
            audioTrack?.let { track ->
                try {
                    if (track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                        track.stop()
                    }
                } catch (ignored: Exception) {}
                track.release()
            }
            audioTrack = null
            Log.d(TAG, "SilentAudioKeeper parado")
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao parar SilentAudioKeeper", e)
        }
    }

    fun isActive(): Boolean = isKeepingAlive && audioTrack != null

    companion object {
        private const val TAG = "SilentAudioKeeper"
    }
}
