package com.btmicpro.core

import android.content.Context
import android.media.AudioManager
import android.media.AudioPlaybackConfiguration
import android.media.AudioRecordingConfiguration
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock

/** Observa metadados públicos; não abre AudioRecord nem identifica clientes anonimizados. */
internal class AudioActivityObserver(context: Context, private val onChanged: () -> Unit) {
    private val manager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val handler = Handler(Looper.getMainLooper())
    private var running = false
    private var lastRecording = ""
    private var lastPlayback = ""
    private var captureStartedAt: Long? = null
    var recordingActive = false; private set
    var captureSummary = "unknown"; private set

    private val recordingCallback = object : AudioManager.AudioRecordingCallback() {
        override fun onRecordingConfigChanged(configs: List<AudioRecordingConfiguration>?) {
            if (!running) return
            updateRecording(configs ?: emptyList())
            onChanged()
        }
    }
    private val playbackCallback = object : AudioManager.AudioPlaybackCallback() {
        override fun onPlaybackConfigChanged(configs: List<AudioPlaybackConfiguration>?) {
            if (!running) return
            val summary = configs.orEmpty().map { "usage=${it.audioAttributes.usage},content=${it.audioAttributes.contentType}" }.sorted().joinToString(";")
            if (lastPlayback != summary) {
                AppLogger.i("PLAYBACK_STATE", "count=${configs.orEmpty().size}; $summary; musicActive=${manager.isMusicActive}")
                lastPlayback = summary
            }
            onChanged()
        }
    }

    fun start() {
        running = true
        try { manager.registerAudioRecordingCallback(recordingCallback, handler) }
        catch (e: Exception) { AppLogger.e("AUDIO_OBSERVER", "Callback de captura indisponível", e) }
        try { manager.registerAudioPlaybackCallback(playbackCallback, handler) }
        catch (e: Exception) { AppLogger.e("AUDIO_OBSERVER", "Callback de reprodução indisponível", e) }
        poll()
    }

    fun poll() {
        try { updateRecording(manager.activeRecordingConfigurations) }
        catch (e: Exception) {
            recordingActive = true // Consulta inconclusiva não deve provocar teardown durante captura.
            captureSummary = "UNKNOWN:${e.javaClass.simpleName}"
            if (lastRecording != captureSummary) AppLogger.w("CAPTURE_STATE", captureSummary)
            lastRecording = captureSummary
        }
    }

    private fun updateRecording(configs: List<AudioRecordingConfiguration>) {
        val now = SystemClock.elapsedRealtime()
        val wasActive = recordingActive
        recordingActive = configs.isNotEmpty()
        if (recordingActive && !wasActive) captureStartedAt = now
        captureSummary = configs.map { config ->
            try {
                val device = config.audioDevice
                val silenced = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) config.isClientSilenced.toString() else "unknown"
                "inputType=${device?.type},inputId=${device?.id},source=${config.clientAudioSource}," +
                    "rate=${config.format.sampleRate},silenced=$silenced"
            } catch (e: Exception) { "input=UNKNOWN:${e.javaClass.simpleName}" }
        }.sorted().joinToString(";")
        val summary = "count=${configs.size};$captureSummary"
        if (summary != lastRecording) {
            val duration = if (!recordingActive && wasActive) captureStartedAt?.let { now - it } else null
            AppLogger.i("CAPTURE_STATE", "$summary; endedDurationMs=$duration; clientes podem estar anonimizados")
            lastRecording = summary
        }
        if (!recordingActive) captureStartedAt = null
    }

    fun stop() {
        running = false
        try { manager.unregisterAudioRecordingCallback(recordingCallback) } catch (_: Exception) { }
        try { manager.unregisterAudioPlaybackCallback(playbackCallback) } catch (_: Exception) { }
    }
}
