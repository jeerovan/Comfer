package com.jeerovan.comfer

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import java.io.File
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

internal interface RootKeyWrapper {
    fun exists(): Boolean
    fun wrap(bytes: ByteArray): ByteArray
    fun unwrap(bytes: ByteArray): ByteArray
}

/** Only the 32-byte root is handled by Keystore. Its disk copy always needs device
 * authentication; plaintext key material lives only inside the unlocked app session. */
internal class KeystoreRootKeyWrapper : RootKeyWrapper {
    private val alias = "comfer.protection.root.v1"
    private fun store() = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    override fun exists() = store().containsAlias(alias)
    private fun key(create: Boolean): SecretKey {
        (store().getKey(alias, null) as? SecretKey)?.let { return it }
        check(create) { "Protected content key unavailable. Restore a portable backup." }
        val spec = KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256).setUserAuthenticationRequired(true)
            .setUserAuthenticationValidityDurationSeconds(300).setInvalidatedByBiometricEnrollment(false)
        return KeyGenerator.getInstance("AES", "AndroidKeyStore").apply { init(spec.build()) }.generateKey()
    }
    override fun wrap(bytes: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key(true))
        cipher.updateAAD("Comfer protected root 1".toByteArray())
        return cipher.iv + cipher.doFinal(bytes)
    }
    override fun unwrap(bytes: ByteArray): ByteArray {
        require(bytes.size == 60) { "Damaged protected content key" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(false), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
        cipher.updateAAD("Comfer protected root 1".toByteArray())
        return cipher.doFinal(bytes.copyOfRange(12, bytes.size))
    }
}

/** Session owner serializes access and clears this cache at lock/timeout. */
internal class ProtectedContentKeys(private val file: File, private val wrapper: RootKeyWrapper) {
    private var root: ByteArray? = null
    fun clear() { root?.fill(0); root = null }
    fun prepare() { load(create = true) }
    private fun load(create: Boolean): ByteArray {
        root?.let { return it }
        val atomic = AtomicFile(file)
        val bytes = if (file.exists() || File(file.path + ".bak").exists()) {
            val stored = File(file.path + ".bak").takeIf { it.exists() } ?: file
            require(stored.length() == 60L) { "Damaged protected content key" }
            wrapper.unwrap(atomic.readFully())
        } else {
            check(create && !wrapper.exists()) { "Protected content key unavailable. Existing content has not been reset." }
            val generated = ByteArray(32).also { SecureRandom().nextBytes(it) }
            try {
                val wrapped = wrapper.wrap(generated)
                val output = atomic.startWrite()
                try { output.write(wrapped); atomic.finishWrite(output) }
                catch (error: Exception) { atomic.failWrite(output); throw error }
                generated
            } catch (error: Exception) { generated.fill(0); throw error }
        }
        require(bytes.size == 32) { "Invalid protected content key" }
        root = bytes
        return bytes
    }
    private fun derived(namespace: String, create: Boolean): ByteArray = Mac.getInstance("HmacSHA256").run {
        init(SecretKeySpec(load(create), "HmacSHA256"))
        doFinal("Comfer protected content 3:$namespace".toByteArray())
    }
    fun seal(bytes: ByteArray, namespace: String, identity: String): ByteArray {
        val key = derived(namespace, true)
        try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"))
            cipher.updateAAD(identity.toByteArray())
            return cipher.iv + cipher.doFinal(bytes)
        } finally { key.fill(0) }
    }
    fun open(bytes: ByteArray, namespace: String, identity: String): ByteArray {
        require(bytes.size >= 28) { "Invalid protected content" }
        val key = derived(namespace, false)
        try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
            cipher.updateAAD(identity.toByteArray())
            return cipher.doFinal(bytes.copyOfRange(12, bytes.size))
        } finally { key.fill(0) }
    }
}
