package com.sleeptalker.app

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RingBufferTest {

    private lateinit var buffer: RingBuffer

    @Before
    fun setUp() {
        buffer = RingBuffer(8)
    }

    @Test
    fun `fresh buffer is empty`() {
        assertEquals(0, buffer.size)
        assertFalse(buffer.isFull)
    }

    @Test
    fun `write less than capacity - toByteArray returns written bytes in order`() {
        buffer.write(byteArrayOf(1, 2, 3))
        assertArrayEquals(byteArrayOf(1, 2, 3), buffer.toByteArray())
    }

    @Test
    fun `write exactly capacity - isFull becomes true`() {
        buffer.write(ByteArray(8) { it.toByte() })
        assertTrue(buffer.isFull)
        assertEquals(8, buffer.size)
    }

    @Test
    fun `write past capacity - oldest bytes are overwritten`() {
        // Fill with 0..7, then overwrite first 2 bytes with 8, 9
        buffer.write(ByteArray(8) { it.toByte() })
        buffer.write(byteArrayOf(8, 9))
        // Chronological order should be: 2 3 4 5 6 7 8 9
        assertArrayEquals(byteArrayOf(2, 3, 4, 5, 6, 7, 8, 9), buffer.toByteArray())
    }

    @Test
    fun `clear resets buffer`() {
        buffer.write(byteArrayOf(1, 2, 3))
        buffer.clear()
        assertEquals(0, buffer.size)
        assertFalse(buffer.isFull)
        assertEquals(0, buffer.toByteArray().size)
    }

    @Test
    fun `write with offset and length`() {
        val data = byteArrayOf(0, 1, 2, 3, 4)
        buffer.write(data, offset = 1, length = 3)
        assertArrayEquals(byteArrayOf(1, 2, 3), buffer.toByteArray())
    }
}
