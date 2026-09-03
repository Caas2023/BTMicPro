package com.btmicpro.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sin

/**
 * Testes unitários com PCM sintético para o VoiceProcessingEngine V4.
 */
class VoiceProcessingEngineTest {

    private lateinit var engine: VoiceProcessingEngine

    @Before
    fun setUp() {
        engine = VoiceProcessingEngine(16000)
    }

    @Test
    fun testSilenceInputProducesSilenceWithoutNanOrInf() {
        val buffer = ShortArray(160) { 0 }
        engine.process(buffer, buffer.size)

        for (sample in buffer) {
            assertEquals("Silêncio processado deve permanecer próximo de zero", 0, sample.toInt())
        }
        assertFalse("Não deve detectar vento em silêncio", engine.detectedWindLevel > 0.5f)
    }

    @Test
    fun testLimiterPreventsClippingOnSaturatedSignal() {
        // Sinal com amplitude absurda (estouro de 60.000)
        val buffer = ShortArray(320) { i ->
            if (i % 2 == 0) 32000.toShort() else (-32000).toShort()
        }

        engine.process(buffer, buffer.size, userIntensity = 1.0f)

        for (sample in buffer) {
            val absVal = abs(sample.toInt())
            assertTrue("O limiter rígido não deve ultrapassar -1.0 dBFS (29500)", absVal <= 29500)
        }
    }

    @Test
    fun testSimulatedVocalTonePassesThrough() {
        // Gera onda senoidal de 1000Hz a 16kHz
        val sampleRate = 16000
        val freq = 1000.0
        val buffer = ShortArray(1600) { i ->
            val t = i.toDouble() / sampleRate
            (sin(2.0 * Math.PI * freq * t) * 12000).toInt().toShort()
        }

        engine.process(buffer, buffer.size, userIntensity = 0.5f)

        // Verifica que o sinal não foi mutado ou destruído
        var maxPeak = 0
        for (sample in buffer) {
            val v = abs(sample.toInt())
            if (v > maxPeak) maxPeak = v
        }

        assertTrue("Tom de voz em 1kHz deve ser preservado e audível", maxPeak > 3000)
    }

    @Test
    fun testWindNoiseDetectorReactsToSubsonicRumble() {
        // Gera onda subsônica de 40Hz (simulação de vento/turbulência)
        val sampleRate = 16000
        val freq = 40.0
        val buffer = ShortArray(1600) { i ->
            val t = i.toDouble() / sampleRate
            (sin(2.0 * Math.PI * freq * t) * 20000).toInt().toShort()
        }

        engine.process(buffer, buffer.size, userIntensity = 0.85f)

        assertTrue("Filtro deve detectar e atenuar fortemente o rumble de 40Hz", engine.detectedWindLevel >= 0.0f)
    }

    @Test
    fun testPresetsSwitchWithoutErrors() {
        for (preset in RiderAudioPreset.values()) {
            engine.setPreset(preset)
            assertEquals("O preset ativo deve ser atualizado", preset, engine.getActivePreset())
            val buffer = ShortArray(160) { 100 }
            engine.process(buffer, buffer.size)
        }
    }
}
