package com.sleeptalker.app

import kotlin.math.sqrt

/**
 * Computes RMS amplitude from 16-bit little-endian PCM bytes.
 * Returns a value in [0.0, 1.0] relative to full scale.
 */
object AmplitudeAnalyzer {

    fun rms(pcm: ByteArray): Float {
        val sampleCount = pcm.size / 2
        if (sampleCount == 0) return 0f

        var sumSq = 0.0
        for (i in 0 until sampleCount) {
            val lo = pcm[i * 2].toInt() and 0xFF
            val hi = pcm[i * 2 + 1].toInt()
            val sample = (hi shl 8) or lo
            sumSq += sample.toDouble() * sample
        }
        return (sqrt(sumSq / sampleCount) / 32768.0).toFloat()
    }
}
