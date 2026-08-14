package com.jeerovan.comfer

import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile

internal object BoundedLogFile {
    const val MAX_VIEW_BYTES = 2L * 1024L * 1024L
    const val MAX_VIEW_LINES = 2_000
    const val MAX_CRASH_BYTES = 512L * 1024L

    fun readTailLines(
        file: File,
        maxBytes: Long = MAX_VIEW_BYTES,
        maxLines: Int = MAX_VIEW_LINES,
    ): List<String> {
        if (!file.exists() || maxBytes <= 0 || maxLines <= 0) return emptyList()
        val bytes = RandomAccessFile(file, "r").use { input ->
            val count = minOf(input.length(), maxBytes).toInt()
            input.seek(input.length() - count)
            ByteArray(count).also(input::readFully)
        }
        var text = bytes.toString(Charsets.UTF_8)
        if (file.length() > bytes.size) {
            text = text.substringAfter('\n', "")
        }
        return text.lineSequence().toList().takeLast(maxLines)
    }

    fun appendBounded(file: File, text: String, maxBytes: Long = MAX_CRASH_BYTES) {
        if (maxBytes <= 0) return
        val newBytes = text.toByteArray(Charsets.UTF_8)
        val keptNewBytes = if (newBytes.size > maxBytes) {
            newBytes.copyOfRange(newBytes.size - maxBytes.toInt(), newBytes.size)
        } else {
            newBytes
        }
        val remaining = (maxBytes - keptNewBytes.size).coerceAtLeast(0)
        val oldBytes = if (file.exists() && remaining > 0) {
            RandomAccessFile(file, "r").use { input ->
                val count = minOf(input.length(), remaining).toInt()
                input.seek(input.length() - count)
                ByteArray(count).also(input::readFully)
            }
        } else {
            byteArrayOf()
        }
        FileOutputStream(file, false).use { output ->
            output.write(oldBytes)
            output.write(keptNewBytes)
        }
    }
}
