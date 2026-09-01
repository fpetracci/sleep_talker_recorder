package com.sleeptalker.app

import org.junit.Assert.assertEquals
import org.junit.Test

class AmplitudeAnalyzerTest {

    private fun shortToLittleEndian(value: Short): ByteArray {
        val v = value.toInt()
        return byteArrayOf((v and 0xFF).toByte(), ((v shr 8) and 0xFF).toByte())
    }

    private fun pcmOf(vararg samples: Short): ByteArray =
        samples.flatMap { shortToLittleEndian(it).toList() }.toByteArray()

    @Test
    fun `empty input returns zero`() {
        assertEquals(0f, AmplitudeAnalyzer.rms(ByteArray(0)), 0f)
    }

    @Test
    fun `silence returns zero`() {
        val silence = pcmOf(0, 0, 0, 0)
        assertEquals(0f, AmplitudeAnalyzer.rms(silence), 0f)
    }

    @Test
    fun `full positive scale returns 1`() {
        val fullScale = pcmOf(Short.MAX_VALUE, Short.MAX_VALUE)
        assertEquals(1f, AmplitudeAnalyzer.rms(fullScale), 0.001f)
    }

    @Test
    fun `full negative scale returns 1`() {
        val fullScale = pcmOf(Short.MIN_VALUE, Short.MIN_VALUE)
        assertEquals(1f, AmplitudeAnalyzer.rms(fullScale), 0.001f)
    }

    @Test
    fun `mixed signal is between 0 and 1`() {
        val mixed = pcmOf(10000, -10000, 5000, -5000)
        val result = AmplitudeAnalyzer.rms(mixed)
        assert(result > 0f) { "Expected RMS > 0, got $result" }
        assert(result < 1f) { "Expected RMS < 1, got $result" }
    }

    @Test
    fun `odd byte count - trailing byte ignored`() {
        // 3 bytes: only first 2 form a valid sample, last is ignored
        val pcm = byteArrayOf(0x00, 0x40, 0xFF) // one sample of 16384, one orphan byte
        val result = AmplitudeAnalyzer.rms(pcm)
        assert(result > 0f) { "Expected RMS > 0, got $result" }
    }
}
