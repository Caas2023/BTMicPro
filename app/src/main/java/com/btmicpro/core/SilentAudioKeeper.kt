package com.btmicpro.core

import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Process
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max

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
 * Playback silencioso que mantém o UID solicitante da rota ativo no AudioDeviceBroker.
 * Não captura microfone nem solicita foco/modo de chamada. A engine encerra a sessão
 * antes de liberar a rota para mídia/chamadas. A eficácia depende da política do aparelho.
 */
class ExperimentalScoKeepAlive {
    private class Session(val track: AudioTrack, val deviceId: Int?, val profile: AudioModeProfile) {
        val running = AtomicBoolean(true)
        @Volatile var state = ScoKeepAliveState.TESTING
        var worker: Thread? = null
    }

    @Volatile private var session: Session? = null
    @Volatile private var startFailed = false
    val state: ScoKeepAliveState
        get() = session?.state ?: if (startFailed) ScoKeepAliveState.FAILED else ScoKeepAliveState.DISABLED

    @Synchronized
    fun start(device: AudioDeviceInfo?, profile: AudioModeProfile): Boolean {
        if (isActive() && session?.deviceId == device?.id && session?.profile == profile) return true
        stop()
        if (!profile.useSilenceKeepAlive) return true
        val sampleRate = profile.keepAliveSampleRate
        val staticLoop = profile.keepAliveStrategy == KeepAliveStrategy.STATIC_VOICE
        var track: AudioTrack? = null
        try {
            val minimum = AudioTrack.getMinBufferSize(sampleRate,
                AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
            check(minimum > 0) { "Buffer PCM indisponível: $minimum" }
            val bufferBytes = if (staticLoop) sampleRate * 2 else
                max(minimum * 2, sampleRate * profile.keepAliveBufferMs / 1000 * 2)
            val sonification = profile.keepAliveStrategy == KeepAliveStrategy.STREAM_SONIFICATION
            track = AudioTrack.Builder()
                .setAudioAttributes(AudioAttributes.Builder()
                    .setUsage(if (sonification) AudioAttributes.USAGE_ASSISTANCE_SONIFICATION else AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(if (sonification) AudioAttributes.CONTENT_TYPE_SONIFICATION else AudioAttributes.CONTENT_TYPE_SPEECH).build())
                .setAudioFormat(AudioFormat.Builder().setSampleRate(sampleRate)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                .setBufferSizeInBytes(bufferBytes)
                .setTransferMode(if (staticLoop) AudioTrack.MODE_STATIC else AudioTrack.MODE_STREAM)
                .build()
            check(track.state != AudioTrack.STATE_UNINITIALIZED) { "AudioTrack não inicializado" }
            if (device != null) check(track.setPreferredDevice(device)) { "Saída Bluetooth recusada" }
            val priming = ShortArray(bufferBytes / 2)
            val primed = track.write(priming, 0, priming.size, AudioTrack.WRITE_NON_BLOCKING)
            check(primed > 0 && (!staticLoop || primed == priming.size)) {
                "Falha ao preencher buffer de silêncio"
            }
            if (staticLoop) check(track.setLoopPoints(0, priming.size, -1) == AudioTrack.SUCCESS) {
                "Loop estático recusado"
            }
            track.play()
            val current = Session(track, device?.id, profile)
            session = current
            current.state = ScoKeepAliveState.ACTIVE
            if (!staticLoop) {
                current.worker = Thread({ streamSilence(current, sampleRate) }, "ScoRouteKeepAlive").apply {
                    isDaemon = true
                    start()
                }
            }
            AppLogger.i(TAG, "ACTIVE: estratégia=${profile.keepAliveStrategy}, saída=${device?.id}, ${sampleRate}Hz, bufferBytes=$bufferBytes")
            return true
        } catch (e: Exception) {
            stop()
            try { track?.release() } catch (_: Exception) { }
            startFailed = true
            AppLogger.e(TAG, "Falha ao iniciar sustentação da rota", e)
            return false
        }
    }

    private fun streamSilence(current: Session, sampleRate: Int) {
        try {
            // Sem requisito de latência (é silêncio): prioridade baixa + blocos de 100 ms
            // reduzem os acordos de CPU de ~50/s para ~10/s. O buffer (mínimo 200 ms,
            // 500 ms no Modo 8/9) absorve atrasos de escalonamento sem underrun audível.
            Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
            val silence = ShortArray(sampleRate / 10)
            var offset = 0
            while (current.running.get()) {
                // WRITE_BLOCKING já dosa o fluxo pelo relógio de áudio. Sleep extra causa underrun.
                val written = current.track.write(silence, offset, silence.size - offset, AudioTrack.WRITE_BLOCKING)
                if (!current.running.get()) break
                check(written > 0) { "AudioTrack.write falhou: $written" }
                offset = (offset + written) % silence.size
            }
        } catch (e: Exception) {
            if (current.running.get()) {
                current.state = ScoKeepAliveState.FAILED
                AppLogger.e(TAG, "Sustentação da rota interrompida", e)
            }
        } finally {
            current.running.set(false)
            try { current.track.stop() } catch (_: Exception) { }
            try { current.track.release() } catch (_: Exception) { }
            if (current.state != ScoKeepAliveState.FAILED) current.state = ScoKeepAliveState.DISABLED
        }
    }

    @Synchronized
    fun stop() {
        startFailed = false
        val previous = session ?: return
        session = null // Uma thread antiga nunca altera o estado de uma nova sessão.
        previous.running.set(false)
        try { previous.track.pause(); previous.track.flush() } catch (_: Exception) { }
        previous.worker?.interrupt()
        try { previous.worker?.join(500) } catch (_: InterruptedException) { Thread.currentThread().interrupt() }
        if (previous.worker == null) {
            try { previous.track.stop(); previous.track.release() } catch (_: Exception) { }
        }
        AppLogger.i(TAG, "DISABLED: sustentação encerrada")
    }

    fun isActive(): Boolean = session?.let { it.running.get() && it.state == ScoKeepAliveState.ACTIVE } == true

    fun describe(): String = session?.let {
        try { "${it.state};strategy=${it.profile.keepAliveStrategy};output=${it.track.routedDevice?.id};underruns=${it.track.underrunCount}" }
        catch (_: Exception) { it.state.name }
    } ?: state.name

    companion object {
        private const val TAG = "BTMIC_SCO_KEEPALIVE"
    }
}

/**
 * Alias de compatibilidade com o nome legado.
 */
typealias SilentAudioKeeper = ExperimentalScoKeepAlive
