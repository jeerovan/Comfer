package com.jeerovan.comfer

import com.jeerovan.comfer.notifications.NotificationHistoryCipher
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class NotificationHistoryCipherTest {
    @Test fun authenticatedEncryptionRejectsTamperingAndLostKeys() {
        val cipher = NotificationHistoryCipher("comfer.notification.test.${UUID.randomUUID()}")
        try {
            cipher.initializeEmptyStore(false)
            val payload = "Synthetic encrypted history fixture".toByteArray()
            val first = cipher.seal("record-1", payload)
            val second = cipher.seal("record-1", payload)
            assertFalse(first.contentEquals(second))
            assertArrayEquals(payload, cipher.open("record-1", first))
            assertTrue(runCatching { cipher.open("record-2", first) }.isFailure)
            val changed = first.copyOf().apply { this[lastIndex] = (this[lastIndex].toInt() xor 1).toByte() }
            assertTrue(runCatching { cipher.open("record-1", changed) }.isFailure)
            assertTrue(runCatching { cipher.seal("record-1", ByteArray(16 * 1024 + 1)) }.isFailure)
            cipher.deleteKey()
            assertTrue(runCatching { cipher.open("record-1", first) }.isFailure)
            assertTrue(runCatching { cipher.initializeEmptyStore(true) }.isFailure)
            assertTrue(runCatching { cipher.seal("record-1", payload) }.isFailure)
        } finally { cipher.deleteKey() }
    }
}
