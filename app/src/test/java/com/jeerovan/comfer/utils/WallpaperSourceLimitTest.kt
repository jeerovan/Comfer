package com.jeerovan.comfer.utils

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class WallpaperSourceLimitTest {
    @Test
    fun exactLimitCopiesSuccessfully() {
        val source = ByteArray(16) { it.toByte() }
        val output = ByteArrayOutputStream()

        assertTrue(copyStreamWithLimit(ByteArrayInputStream(source), output, source.size.toLong()))
        assertArrayEquals(source, output.toByteArray())
    }

    @Test
    fun oversizedSourceStopsBeforeWritingOversizedChunk() {
        val source = ByteArray(DEFAULT_BUFFER_SIZE + 1)
        val output = ByteArrayOutputStream()

        assertFalse(
            copyStreamWithLimit(
                ByteArrayInputStream(source),
                output,
                DEFAULT_BUFFER_SIZE.toLong(),
            )
        )
        assertTrue(output.size().toLong() <= DEFAULT_BUFFER_SIZE.toLong())
    }

    @Test
    fun negativeLimitIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            copyStreamWithLimit(ByteArrayInputStream(byteArrayOf()), ByteArrayOutputStream(), -1)
        }
    }
}
