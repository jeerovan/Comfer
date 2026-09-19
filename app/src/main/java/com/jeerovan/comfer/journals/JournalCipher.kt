package com.jeerovan.comfer.journals

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import java.security.SecureRandom

internal interface JournalCipher {
    fun needsSessionUpgrade(bytes: ByteArray): Boolean = false
    fun seal(bytes: ByteArray, identity: String, protected: Boolean): ByteArray
    fun open(bytes: ByteArray, identity: String): ByteArray
}

/** Independent Journal keys; authentication is enforced by both the session and Android Keystore. */
internal class KeystoreJournalCipher : JournalCipher {
    private val keys = mutableMapOf<Boolean, SecretKey>()
    @Synchronized private fun key(protected: Boolean, create: Boolean): SecretKey {
        if (protected) JournalProtection.requireAuthorization()
        keys[protected]?.let { return it }
        val alias = "comfer.journals.${if (protected) "private" else "local"}.v1"
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { keys[protected] = it; return it }
        check(create) { "Journal encryption key is unavailable. Existing data has been retained. Restore a password-protected backup on a fresh installation to recover it." }
        val spec = KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setKeySize(256)
        if (protected) {
            spec.setUserAuthenticationRequired(true)
            @Suppress("DEPRECATION")
            spec.setUserAuthenticationValidityDurationSeconds(300)
            spec.setInvalidatedByBiometricEnrollment(false)
        }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            .apply { init(spec.build()) }.generateKey().also { keys[protected] = it }
    }
    override fun needsSessionUpgrade(bytes: ByteArray) = bytes.size >= 2 && bytes[1] == 1.toByte() && bytes[0] != 3.toByte()
    override fun seal(bytes: ByteArray, identity: String, protected: Boolean): ByteArray {
        if (protected) return byteArrayOf(3, 1) + com.jeerovan.comfer.ProtectionSession.seal(bytes, "journals", "Journal 3:$identity")
        // Keep Keystore operations small on older Android versions. Each payload has a fresh
        // software AES key, wrapped by the authenticated, non-exportable device key.
        val contentKey = ByteArray(32).also { SecureRandom().nextBytes(it) }
        try {
            val header = byteArrayOf(2, if (protected) 1 else 0)
            val wrapper = Cipher.getInstance("AES/GCM/NoPadding")
            wrapper.init(Cipher.ENCRYPT_MODE, key(protected, true))
            wrapper.updateAAD(header + identity.toByteArray(Charsets.UTF_8))
            val wrapped = wrapper.doFinal(contentKey)
            val prefix = header + wrapper.iv + wrapped
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(contentKey, "AES"))
            cipher.updateAAD(prefix + identity.toByteArray(Charsets.UTF_8))
            return prefix + cipher.iv + cipher.doFinal(bytes)
        } finally { contentKey.fill(0) }
    }
    override fun open(bytes: ByteArray, identity: String): ByteArray {
        if (bytes.firstOrNull() == 3.toByte()) {
            require(bytes.size >= 30 && bytes[1] == 1.toByte())
            return com.jeerovan.comfer.ProtectionSession.open(bytes.copyOfRange(2, bytes.size), "journals", "Journal 3:$identity")
        }
        require(bytes.size >= 90 && bytes[0] == 2.toByte() && bytes[1] in byteArrayOf(0, 1)) { "Invalid encrypted Journal data" }
        val wrapper = Cipher.getInstance("AES/GCM/NoPadding")
        wrapper.init(Cipher.DECRYPT_MODE, key(bytes[1] == 1.toByte(), false), GCMParameterSpec(128, bytes.copyOfRange(2, 14)))
        wrapper.updateAAD(bytes.copyOfRange(0, 2) + identity.toByteArray(Charsets.UTF_8))
        val contentKey = wrapper.doFinal(bytes.copyOfRange(14, 62))
        try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(contentKey, "AES"), GCMParameterSpec(128, bytes.copyOfRange(62, 74)))
            cipher.updateAAD(bytes.copyOfRange(0, 62) + identity.toByteArray(Charsets.UTF_8))
            return cipher.doFinal(bytes.copyOfRange(74, bytes.size))
        } finally { contentKey.fill(0) }
    }
}
