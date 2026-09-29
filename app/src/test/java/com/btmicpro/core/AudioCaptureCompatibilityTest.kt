package com.btmicpro.core

import org.junit.Assert.*
import org.junit.Test

class AudioCaptureCompatibilityTest {
    @Test fun keeps16kWhenSupported() {
        val attempts = mutableListOf<Int>()
        val result = AudioCaptureCompatibility.select { rate -> attempts.add(rate); "recorder" }
        assertEquals(16000, result?.first)
        assertEquals(listOf(16000), attempts)
    }

    @Test fun fallsBackWhenDriverRejectsFormats() {
        val attempts = mutableListOf<Int>()
        val result = AudioCaptureCompatibility.select { rate ->
            attempts.add(rate)
            if (rate == 44100) "recorder" else null
        }
        assertEquals(44100, result?.first)
        assertEquals(listOf(16000, 48000, 44100), attempts)
    }

    @Test fun unsupportedHardwareTerminatesWithoutResource() {
        val attempts = mutableListOf<Int>()
        assertNull(AudioCaptureCompatibility.select<Any> { attempts.add(it); null })
        assertEquals(listOf(16000, 48000, 44100, 8000), attempts)
    }
}
