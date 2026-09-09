package com.sleeptalker.app.audio

import java.io.File
import java.io.RandomAccessFile

/**
 * Writes raw 16-bit little-endian mono PCM bytes to a standard 44-byte-header WAV
 * file — 16kHz mono 16-bit, matching what tests/conftest.py's convert_to_wav and
 * Vosk both expect.
 */
object WavWriter {

    fun write(file: File, pcm: ByteArray, sampleRate: Int = 16_000, channels: Int = 1) {
        val bitsPerSample = 16
        val byteRate = sampleRate * channels * bitsPerSample / 8
        val blockAlign = channels * bitsPerSample / 8
        val dataSize = pcm.size

        RandomAccessFile(file, "rw").use { raf ->
            raf.setLength(0)
            // RIFF header
            raf.writeAsciiBytes("RIFF")
            raf.writeIntLE(36 + dataSize)
            raf.writeAsciiBytes("WAVE")
            // fmt chunk
            raf.writeAsciiBytes("fmt ")
            raf.writeIntLE(16) // PCM fmt chunk size
            raf.writeShortLE(1) // audio format = PCM
            raf.writeShortLE(channels)
            raf.writeIntLE(sampleRate)
            raf.writeIntLE(byteRate)
            raf.writeShortLE(blockAlign)
            raf.writeShortLE(bitsPerSample)
            // data chunk
            raf.writeAsciiBytes("data")
            raf.writeIntLE(dataSize)
            raf.write(pcm)
        }
    }

    private fun RandomAccessFile.writeAsciiBytes(s: String) = write(s.toByteArray(Charsets.US_ASCII))

    private fun RandomAccessFile.writeIntLE(value: Int) {
        write(value and 0xFF)
        write((value shr 8) and 0xFF)
        write((value shr 16) and 0xFF)
        write((value shr 24) and 0xFF)
    }

    private fun RandomAccessFile.writeShortLE(value: Int) {
        write(value and 0xFF)
        write((value shr 8) and 0xFF)
    }
}
