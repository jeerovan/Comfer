package com.jeerovan.comfer.notifications

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.annotation.WorkerThread
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Phase-0 storage feasibility boundary. This does not enable or capture history. */
@WorkerThread
internal class NotificationHistoryCipher(private val alias: String = "comfer.notification.history.v1") {
    private fun store() = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    private fun key(): SecretKey = store().getKey(alias, null) as? SecretKey
        ?: throw IllegalStateException("Notification history key unavailable")

    /** Caller must first establish that the retained store is empty. Never replace a lost key. */
    fun initializeEmptyStore(hasRetainedRecords: Boolean) {
        require(!hasRetainedRecords) { "Existing history requires its original key" }
        if (store().containsAlias(alias)) return
        KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setKeySize(256).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true).build())
            generateKey()
        }
    }

    fun seal(recordId: String, payload: ByteArray): ByteArray {
        require(payload.size <= 16 * 1024)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        cipher.updateAAD(identity(recordId))
        val iv = cipher.iv
        check(iv.size == 12)
        return byteArrayOf(1, 12) + iv + cipher.doFinal(payload)
    }

    fun open(recordId: String, envelope: ByteArray): ByteArray {
        require(envelope.size in 30..(16 * 1024 + 30) && envelope[0] == 1.toByte() && envelope[1] == 12.toByte())
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, envelope.copyOfRange(2, 14)))
        cipher.updateAAD(identity(recordId))
        return cipher.doFinal(envelope.copyOfRange(14, envelope.size))
    }

    private fun identity(recordId: String): ByteArray {
        val bytes = recordId.toByteArray(Charsets.UTF_8)
        require(bytes.size in 1..128)
        return byteArrayOf(1) + bytes
    }

    /** Only for explicitly confirmed saved-data deletion (and isolated test-key cleanup). */
    fun deleteKey() { store().deleteEntry(alias) }
}
