package com.sleeptalker.app

/**
 * Fixed-capacity circular buffer for raw PCM bytes.
 * Older data is silently overwritten once capacity is reached.
 * Used as a pre-buffer so the onset of a word is never clipped.
 */
class RingBuffer(val capacityBytes: Int) {

    private val buf = ByteArray(capacityBytes)
    private var writePos = 0
    var totalBytesWritten = 0
        private set

    val isFull: Boolean get() = totalBytesWritten >= capacityBytes
    val size: Int get() = minOf(totalBytesWritten, capacityBytes)

    fun write(data: ByteArray, offset: Int = 0, length: Int = data.size - offset) {
        for (i in offset until offset + length) {
            buf[writePos % capacityBytes] = data[i]
            writePos++
        }
        totalBytesWritten += length
    }

    /** Returns the buffered bytes in chronological order. */
    fun toByteArray(): ByteArray {
        if (!isFull) return buf.copyOf(totalBytesWritten)
        val out = ByteArray(capacityBytes)
        val start = writePos % capacityBytes
        System.arraycopy(buf, start, out, 0, capacityBytes - start)
        System.arraycopy(buf, 0, out, capacityBytes - start, start)
        return out
    }

    fun clear() {
        writePos = 0
        totalBytesWritten = 0
    }
}
