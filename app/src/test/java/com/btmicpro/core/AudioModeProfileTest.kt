package com.btmicpro.core

import android.media.AudioManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioModeProfileTest {
    @Test
    fun exposesThirteenNumberedCompatibilityProfiles() {
        assertEquals(13, AudioModeProfile.entries.size)
        AudioModeProfile.entries.forEachIndexed { index, profile ->
            assertTrue(profile.title.startsWith("Modo ${index + 1} "))
        }
    }

    @Test
    fun legacyKingKongCodesMigrateWithoutChangingValidatedParameters() {
        val stable = AudioModeProfile.fromCode("mode_8")
        assertEquals(AudioModeProfile.MODE_1_KINGKONG_STABLE, stable)
        assertEquals(AudioManager.MODE_NORMAL, stable.targetAudioMode)
        assertEquals(KeepAliveStrategy.STREAM_VOICE, stable.keepAliveStrategy)
        assertEquals(16000, stable.keepAliveSampleRate)
        assertEquals(500, stable.keepAliveBufferMs)
        assertEquals(2500L, stable.mediaResumeDelayMs)
        assertEquals(1500L, stable.routeControlStableMs)
        assertEquals(500L, stable.routeControlUnstableMs)

        val eco = AudioModeProfile.fromCode("mode_9")
        assertEquals(AudioModeProfile.MODE_2_KINGKONG_ECO, eco)
        assertEquals(500, eco.keepAliveBufferMs)
        assertEquals(2500L, eco.mediaResumeDelayMs)
        assertEquals(3000L, eco.routeControlStableMs)
        assertEquals(1000L, eco.routeControlUnstableMs)
        assertEquals(true, eco.ecoPolling)

        val fast = AudioModeProfile.fromCode("mode_10")
        assertEquals(AudioModeProfile.MODE_3_KINGKONG_FAST, fast)
        assertEquals(500, fast.keepAliveBufferMs)
        assertEquals(1200L, fast.mediaResumeDelayMs)
        assertEquals(250L, fast.routeControlStableMs)
        assertEquals(250L, fast.routeControlUnstableMs)
    }

    @Test
    fun newCodesRoundTripAndUnknownDefaultsToStableKingKong() {
        AudioModeProfile.entries.forEach { profile ->
            assertEquals(profile, AudioModeProfile.fromCode(profile.code))
        }
        assertEquals(AudioModeProfile.MODE_1_KINGKONG_STABLE, AudioModeProfile.fromCode(null))
        assertEquals(AudioModeProfile.MODE_1_KINGKONG_STABLE, AudioModeProfile.fromCode("unknown"))
    }

    @Test
    fun manufacturerCandidatesUseDistinctStrategies() {
        val samsung = AudioModeProfile.MODE_4_SAMSUNG_SAFE
        val xiaomi = AudioModeProfile.MODE_5_XIAOMI_PERSISTENT
        val motorola = AudioModeProfile.MODE_6_MOTOROLA_BALANCED
        assertEquals(AudioManager.MODE_NORMAL, samsung.targetAudioMode)
        assertEquals(false, samsung.reassertOnTransient)
        assertEquals(true, xiaomi.reassertOnTransient)
        assertEquals(8000L, xiaomi.watchdogIntervalMs)
        assertEquals(350, motorola.keepAliveBufferMs)
    }

    @Test
    fun fallbackProfilesCoverLegacyNoKeeperStaticAndVoip() {
        assertEquals(AudioManager.MODE_IN_COMMUNICATION, AudioModeProfile.MODE_7_ANDROID_LEGACY.targetAudioMode)
        assertEquals(KeepAliveStrategy.NONE, AudioModeProfile.MODE_11_NO_KEEPALIVE.keepAliveStrategy)
        assertEquals(KeepAliveStrategy.STATIC_VOICE, AudioModeProfile.MODE_12_STATIC_LOOP.keepAliveStrategy)
        assertEquals(AudioManager.MODE_IN_COMMUNICATION, AudioModeProfile.MODE_13_VOIP_FALLBACK.targetAudioMode)
    }

    @Test
    fun allProfilesHaveValidMetadataAndTiming() {
        AudioModeProfile.entries.forEach { profile ->
            assertNotNull(profile.code)
            assertNotNull(profile.title)
            assertNotNull(profile.subtitle)
            assertNotNull(profile.details)
            assertTrue(profile.routeControlStableMs > 0)
            assertTrue(profile.routeControlUnstableMs > 0)
            assertTrue(profile.watchdogIntervalMs > 0)
            assertTrue(profile.transientRecheckMs > 0)
            assertTrue(profile.selectionTimeoutMs > 0)
        }
    }
}
