package com.btmicpro.core

import org.junit.Assert.*
import org.junit.Test

class MediaRoutePolicyTest {
    @Test fun keepsVoiceAvailableBeforePlayback() {
        assertFalse(MediaRoutePolicy().shouldYield(true, false, false, 0))
    }

    @Test fun releasesDuringPlaybackAndWaitsBeforeRestoring() {
        val policy = MediaRoutePolicy()
        assertTrue(policy.shouldYield(true, true, false, 100))
        assertTrue(policy.shouldYield(true, true, false, 5000))
        assertTrue(policy.shouldYield(true, false, false, 6499))
        assertFalse(policy.shouldYield(true, false, false, 6500))
    }

    @Test fun shortPlaybackGapDoesNotReacquireSco() {
        val policy = MediaRoutePolicy()
        assertTrue(policy.shouldYield(true, true, false, 0))
        assertTrue(policy.shouldYield(true, false, false, 700))
        assertTrue(policy.shouldYield(true, true, false, 1000))
        assertTrue(policy.shouldYield(true, false, false, 2000))
        assertFalse(policy.shouldYield(true, false, false, 2500))
    }

    @Test fun changingProfileOrStoppingClearsTheHold() {
        val policy = MediaRoutePolicy()
        assertTrue(policy.shouldYield(true, true, false, 0))
        assertFalse(policy.shouldYield(false, true, false, 100))
        assertFalse(policy.shouldYield(true, false, false, 200))
        assertTrue(policy.shouldYield(true, true, false, 300))
        policy.reset()
        assertFalse(policy.shouldYield(true, false, false, 400))
    }

    @Test fun recordingPreemptsPlaybackAndClearsItsResumeHold() {
        val policy = MediaRoutePolicy()
        assertTrue(policy.shouldYield(true, true, false, 0))
        assertFalse(policy.shouldYield(true, true, true, 100))
        assertFalse(policy.shouldYield(true, false, false, 150))
        assertTrue(policy.shouldYield(true, true, false, 200))
    }
}
