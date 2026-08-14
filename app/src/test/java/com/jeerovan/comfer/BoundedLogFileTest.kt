package com.jeerovan.comfer

import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BoundedLogFileTest {
    @Test
    fun appendNeverExceedsByteLimit() {
        val file = Files.createTempFile("bounded-log", ".txt").toFile()
        try {
            repeat(20) { BoundedLogFile.appendBounded(file, "entry-$it\n", 64) }
            assertTrue(file.length() <= 64)
            assertTrue(file.readText().endsWith("entry-19\n"))
        } finally {
            file.delete()
        }
    }

    @Test
    fun readerReturnsOnlyTailLines() {
        val file = Files.createTempFile("bounded-log", ".txt").toFile()
        try {
            file.writeText((1..10).joinToString("\n") { "line-$it" })
            assertEquals(listOf("line-8", "line-9", "line-10"), BoundedLogFile.readTailLines(file, 1_024, 3))
        } finally {
            file.delete()
        }
    }
}
