package com.btmicpro.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class AudioModeProfileTest {
    @Test
    fun xProTestUsesNormalModeWithRouteSustainingPlayback() {
        val profile = AudioModeProfile.fromCode("x_pro_test")
        assertEquals(AudioModeProfile.X_PRO_TEST, profile)
        assertEquals(android.media.AudioManager.MODE_NORMAL, profile.targetAudioMode)
        assertEquals(true, profile.useSilenceKeepAlive)
    }

    @Test
    fun testFromCodeReturnsCorrectProfile() {
        assertEquals(AudioModeProfile.STANDARD, AudioModeProfile.fromCode("standard"))
        assertEquals(AudioModeProfile.MODE_2, AudioModeProfile.fromCode("mode_2"))
        assertEquals(AudioModeProfile.MODE_3, AudioModeProfile.fromCode("mode_3"))
        assertEquals(AudioModeProfile.MODE_4, AudioModeProfile.fromCode("mode_4"))
        assertEquals(AudioModeProfile.MODE_5, AudioModeProfile.fromCode("mode_5"))
        assertEquals(AudioModeProfile.MODE_6, AudioModeProfile.fromCode("mode_6"))
        assertEquals(AudioModeProfile.MODE_7, AudioModeProfile.fromCode("mode_7"))
        assertEquals(AudioModeProfile.MODE_8, AudioModeProfile.fromCode("mode_8"))
        assertEquals(AudioModeProfile.MODE_9, AudioModeProfile.fromCode("mode_9"))
        assertEquals(AudioModeProfile.MODE_10, AudioModeProfile.fromCode("mode_10"))
    }

    @Test
    fun testFromCodeNullOrUnknownDefaultsToStandard() {
        assertEquals(AudioModeProfile.STANDARD, AudioModeProfile.fromCode(null))
        assertEquals(AudioModeProfile.STANDARD, AudioModeProfile.fromCode("unknown"))
        assertEquals(AudioModeProfile.STANDARD, AudioModeProfile.fromCode(""))
    }

    @Test
    fun mode9EcoMatchesMode8AudioWithSlowerPolling() {
        val eco = AudioModeProfile.fromCode("mode_9")
        val reference = AudioModeProfile.MODE_8
        assertEquals(reference.targetAudioMode, eco.targetAudioMode)
        assertEquals(reference.keepAliveStrategy, eco.keepAliveStrategy)
        assertEquals(reference.keepAliveSampleRate, eco.keepAliveSampleRate)
        assertEquals(reference.keepAliveBufferMs, eco.keepAliveBufferMs)
        assertEquals(reference.mediaResumeDelayMs, eco.mediaResumeDelayMs)
        assertEquals(reference.releaseForMedia, eco.releaseForMedia)
        assertEquals(true, eco.ecoPolling)
        assertEquals(false, reference.ecoPolling)
        assertEquals(3000L, eco.routeControlStableMs)
    }

    @Test
    fun mode10KeepsMode8AudioButReducesMediaDelay() {
        val fast = AudioModeProfile.fromCode("mode_10")
        val reference = AudioModeProfile.MODE_8
        assertEquals(reference.targetAudioMode, fast.targetAudioMode)
        assertEquals(reference.keepAliveStrategy, fast.keepAliveStrategy)
        assertEquals(reference.keepAliveSampleRate, fast.keepAliveSampleRate)
        assertEquals(reference.keepAliveBufferMs, fast.keepAliveBufferMs)
        assertEquals(reference.releaseForMedia, fast.releaseForMedia)
        assertEquals(1200L, fast.mediaResumeDelayMs)
        assertEquals(250L, fast.routeControlStableMs)
        assertEquals(250L, fast.routeControlUnstableMs)
    }

    @Test
    fun testAllProfilesHaveTitlesAndSubtitles() {
        AudioModeProfile.values().forEach { profile ->
            val expected = if (profile == AudioModeProfile.MODE_2 || profile == AudioModeProfile.MODE_5)
                android.media.AudioManager.MODE_IN_COMMUNICATION else android.media.AudioManager.MODE_NORMAL
            assertEquals(expected, profile.targetAudioMode)
            assertNotNull(profile.code)
            assertNotNull(profile.title)
            assertNotNull(profile.subtitle)
            assertNotNull(profile.details)
        }
    }
}
