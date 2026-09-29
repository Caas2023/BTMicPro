package com.btmicpro.core

/** Tries PCM formats in order; callers release any rejected native resource. */
internal object AudioCaptureCompatibility {
    val sampleRates = listOf(16000, 48000, 44100, 8000)

    fun <T : Any> select(create: (Int) -> T?): Pair<Int, T>? {
        for (rate in sampleRates) {
            val resource = create(rate) ?: continue
            return rate to resource
        }
        return null
    }
}
