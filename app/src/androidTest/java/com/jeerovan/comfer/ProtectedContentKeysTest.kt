package com.jeerovan.comfer

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.*
import org.junit.Assert.*
import java.io.File
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.GCMParameterSpec

/** Real AES-GCM/HMAC and disk storage; controllable wrapper simulates Keystore expiry. */
class ProtectedContentKeysTest {
    private lateinit var file: File
    private lateinit var wrapper: ExpiringWrapper
    @Before fun setup() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName.endsWith(".notificationtest"))
        file = File(context.cacheDir, "session-key-${java.util.UUID.randomUUID()}")
        wrapper = ExpiringWrapper()
    }
    @After fun cleanup() { file.delete(); File(file.path + ".bak").delete() }

    @Test fun activeSessionCanReadAndSaveAfterNativeAuthenticationExpires() {
        val keys = ProtectedContentKeys(file, wrapper)
        keys.prepare()
        val source = ByteArray(2_000_000).also { java.util.Random(123).nextBytes(it) }
        val before = keys.seal(source, "journals", "entry")
        wrapper.authenticated = false
        assertArrayEquals(source, keys.open(before, "journals", "entry"))
        val after = keys.seal("Later edit".toByteArray(), "notes:generation", "note")
        assertEquals("Later edit", keys.open(after, "notes:generation", "note").toString(Charsets.UTF_8))
        keys.clear()
        try { keys.open(after, "notes:generation", "note"); fail("Expired native verification reused after session clear") } catch (_: android.security.keystore.UserNotAuthenticatedException) { }
        wrapper.authenticated = true
        assertArrayEquals(source, ProtectedContentKeys(file, wrapper).open(before, "journals", "entry"))
    }
    @Test fun identityAndModuleKeysPreventCiphertextSubstitution() {
        val keys = ProtectedContentKeys(file, wrapper)
        val data = keys.seal("secret".toByteArray(), "notes:a", "one")
        for ((namespace, identity) in listOf("notes:b" to "one", "journals" to "one", "notes:a" to "two")) {
            try { keys.open(data, namespace, identity); fail("Substitution accepted") } catch (_: java.security.GeneralSecurityException) { }
        }
        val changed = data.copyOf().also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() }
        try { keys.open(changed, "notes:a", "one"); fail("Tampered data accepted") } catch (_: java.security.GeneralSecurityException) { }
    }
    @Test fun missingOrDamagedWrappedKeyIsNeverReplaced() {
        val keys = ProtectedContentKeys(file, wrapper)
        keys.seal("secret".toByteArray(), "notes", "entry")
        val original = file.readBytes()
        file.writeBytes(original.copyOf().also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() })
        val damaged = file.readBytes()
        keys.clear()
        try { keys.prepare(); fail("Damaged key accepted") } catch (_: java.security.GeneralSecurityException) { }
        assertArrayEquals(damaged, file.readBytes())
        file.delete()
        try { keys.prepare(); fail("Missing key recreated") } catch (_: IllegalStateException) { }
        assertFalse(file.exists())
    }
    private class ExpiringWrapper : RootKeyWrapper {
        private val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        var authenticated = true
        private var created = false
        override fun exists() = created
        override fun wrap(bytes: ByteArray): ByteArray {
            if (!authenticated) throw android.security.keystore.UserNotAuthenticatedException()
            val c = Cipher.getInstance("AES/GCM/NoPadding"); c.init(Cipher.ENCRYPT_MODE, key)
            created = true
            return c.iv + c.doFinal(bytes)
        }
        override fun unwrap(bytes: ByteArray): ByteArray {
            if (!authenticated) throw android.security.keystore.UserNotAuthenticatedException()
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
            return c.doFinal(bytes.copyOfRange(12, bytes.size))
        }
    }
}
