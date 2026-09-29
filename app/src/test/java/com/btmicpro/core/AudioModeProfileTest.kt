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
    }

    @Test
    fun testFromCodeNullOrUnknownDefaultsToStandard() {
        assertEquals(AudioModeProfile.STANDARD, AudioModeProfile.fromCode(null))
        assertEquals(AudioModeProfile.STANDARD, AudioModeProfile.fromCode("unknown"))
        assertEquals(AudioModeProfile.STANDARD, AudioModeProfile.fromCode(""))
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
