package com.btmicpro.core

/**
 * CleanVoiceDsp — Fachada / Wrapper de compatibilidade que utiliza o VoiceProcessingEngine V4.
 *
 * Oferece retrocompatibilidade total com as classes existentes e permite acesso aos novos
 * presets de motociclista (NORMAL, CITY, HIGHWAY, EXTREME_WIND, VOICE_CLARITY).
 */
class CleanVoiceDsp(private var sampleRate: Int = 16000) {

    private val engine = VoiceProcessingEngine(sampleRate)

    fun configureFilters(newSampleRate: Int) {
        sampleRate = newSampleRate
        engine.configure(newSampleRate)
    }

    fun setPreset(preset: RiderAudioPreset) {
        engine.setPreset(preset)
    }

    fun getActivePreset(): RiderAudioPreset = engine.getActivePreset()

    fun getDetectedWindLevel(): Float = engine.detectedWindLevel

    fun process(buffer: ShortArray, length: Int, intensity: Float = 0.85f, bypassDsp: Boolean = false) {
        engine.process(buffer, length, intensity, bypassDsp)
    }

    fun resetState() {
        engine.resetState()
    }
}
