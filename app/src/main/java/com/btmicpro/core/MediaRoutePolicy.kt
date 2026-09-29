package com.btmicpro.core

/** Keeps SCO released across short gaps between media playback updates. */
internal class MediaRoutePolicy(private val resumeDelayMs: Long = 1500L) {
    private var lastPlaybackAt: Long? = null

    fun shouldYield(enabled: Boolean, musicActive: Boolean, recordingActive: Boolean, now: Long,
                    delayMs: Long = resumeDelayMs): Boolean {
        // A playback hint must not interrupt a note already being recorded.
        if (!enabled || recordingActive) {
            lastPlaybackAt = null
            return false
        }
        if (musicActive) lastPlaybackAt = now
        return musicActive || lastPlaybackAt?.let { now - it < delayMs } == true
    }

    fun reset() { lastPlaybackAt = null }
}
