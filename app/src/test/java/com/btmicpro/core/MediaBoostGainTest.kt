package com.btmicpro.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaBoostGainTest {
    @Test
    fun sliderMatchesDisplayedDecibels() {
        assertEquals(0, targetGainMillibels(0))
        assertEquals(400, targetGainMillibels(50))
        assertEquals("100% deve solicitar 8 dB, não 80 dB", 800, targetGainMillibels(100))
    }

    @Test
    fun persistedValuesOutsideSliderRangeAreClamped() {
        assertEquals(0, targetGainMillibels(Int.MIN_VALUE))
        assertEquals(800, targetGainMillibels(Int.MAX_VALUE))
    }

    @Test
    fun allSliderStepsStayWithinEightDecibelsAndIncreaseMonotonically() {
        var previous = -1
        for (level in 0..100) {
            val gain = targetGainMillibels(level)
            assertTrue(gain in 0..800)
            assertTrue(gain > previous)
            previous = gain
        }
    }
}
