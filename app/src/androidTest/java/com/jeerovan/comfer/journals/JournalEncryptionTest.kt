package com.jeerovan.comfer.journals

import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.*
import org.junit.Assert.*
import java.io.File
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.GCMParameterSpec

/** Deterministic session/failure tests use software keys; separate tests cover Android Keystore. */
class JournalEncryptionTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var db: JournalDatabase
    private lateinit var cipher: TestJournalCipher
    @Before fun setup() {
        check(context.packageName.endsWith(".notificationtest"))
        db = Room.inMemoryDatabaseBuilder(context, JournalDatabase::class.java).build()
        cipher = TestJournalCipher(); db.cipher = cipher
        JournalProtection.lock()
    }
    @After fun cleanup() { db.close(); JournalProtection.lock() }

    @Test fun unprotectedJournalMediaDoesNotParticipateInSessionKeyUpgrade() = runBlocking {
        db.storageContext = context
        val entry = JournalEntry(text = "Keep unprotected content", image = "${java.util.UUID.randomUUID()}.jpg")
        db.dao().insert(entry)
        // An unavailable unprotected attachment must not block authentication for Notes.
        db.dao().upgradeProtectedContent()
        assertEquals(entry, db.dao().entry(entry.id))
    }
    @Test fun sessionKeyUpgradePreservesContentAndRollsBackPartialFailure() = runBlocking {
        JournalProtection.authorize(); db.dao().changeProtection(true)
        val entry = JournalEntry(text = "Protected entry")
        val draft = JournalDraft(text = "Protected draft")
        val segment = JournalSegment("session", 0, entry.id, "Protected dictation")
        db.dao().insert(entry); db.dao().putDraft(draft); db.dao().segment(segment)
        val before = db.rawDao().entry(entry.id)!!
        cipher.upgradeRequired = true; cipher.failAfter = 1
        try { db.dao().upgradeProtectedContent(); fail("Partial upgrade committed") } catch (_: IllegalStateException) { }
        assertEquals(before, db.rawDao().entry(entry.id))
        cipher.failAfter = Int.MAX_VALUE
        db.dao().upgradeProtectedContent()
        assertEquals(entry, db.dao().entry(entry.id))
        assertEquals(draft, db.dao().draft())
        assertEquals(listOf(segment), db.dao().segments(entry.id))
        assertNotEquals(before.text, db.rawDao().entry(entry.id)!!.text)
    }
    @Test fun allContentIsEncryptedAndSearchEditArchiveStillWork() = runBlocking {
        val store = JournalStore(db)
        val entry = store.submit(store.saveDraft(store.ensureDraft().copy(text = "Secret journal sentence")))
        val draft = store.saveDraft(store.ensureDraft().copy(text = "Private unfinished draft"))
        val segment = JournalSegment("session", 0, entry.id, "Private finalized speech", "Private provisional speech")
        db.dao().segment(segment)
        assertFalse(db.rawDao().entry(entry.id)!!.text.contains("Secret journal"))
        assertFalse(db.rawDao().draft()!!.text.contains("unfinished"))
        assertFalse(db.rawDao().allSegments().single().finalized.contains("speech"))
        assertEquals("", db.rawDao().allSegments().single().provisional)
        assertEquals(listOf(entry), db.dao().search("SECRET", 10).first())
        assertEquals(draft, db.dao().draft())
        assertEquals(listOf(segment), db.dao().segments(entry.id))
        val edited = store.saveEdit(entry, "Changed secret", null)
        assertTrue(db.dao().search("sentence", 10).first().isEmpty())
        val deleted = store.delete(edited)
        assertTrue(db.dao().search("Changed", 10).first().isEmpty())
        assertEquals(listOf(deleted), db.dao().trash().first())
        assertEquals("Changed secret", store.restore(deleted).text)
    }

    @Test fun unsupportedStorageIsRejectedWithoutRewritingContent() = runBlocking {
        val entry = JournalEntry(text = "Unsupported content")
        db.rawDao().state(JournalState(storageVersion = 0))
        db.rawDao().insert(entry)
        try { db.dao().initialize(); fail("Unsupported storage accepted") } catch (_: IllegalStateException) { }
        assertEquals(entry, db.rawDao().entry(entry.id))
        assertEquals(0, db.rawDao().storageState()!!.storageVersion)
    }

    @Test fun protectionRequiresAuthenticationAndRelocksWithoutLosingContent() = runBlocking {
        val entry = JournalEntry(text = "Protected content")
        db.dao().insert(entry)
        try { db.dao().changeProtection(true); fail("Unauthenticated enable accepted") } catch (_: IllegalStateException) { }
        JournalProtection.authorize(); db.dao().changeProtection(true)
        assertEquals(entry, db.dao().entry(entry.id))
        JournalProtection.lock()
        try { db.dao().entry(entry.id); fail("Locked content readable") } catch (_: IllegalStateException) { }
        try { db.dao().insert(JournalEntry(text = "Locked write")); fail("Locked write accepted") } catch (_: IllegalStateException) { }
        try { db.dao().changeProtection(false); fail("Unauthenticated disable accepted") } catch (_: IllegalStateException) { }
        JournalProtection.authorize(); db.dao().changeProtection(false); JournalProtection.lock()
        assertEquals(entry, db.dao().entry(entry.id))
    }

    @Test fun failedRekeyRollsBackEveryRowAndProtectionState() = runBlocking {
        val entries = listOf(JournalEntry(text = "First"), JournalEntry(text = "Second"))
        entries.forEach { db.dao().insert(it) }
        val rawBefore = db.rawDao().exportEntries()
        JournalProtection.authorize(); cipher.failAfter = 1
        try { db.dao().changeProtection(true); fail("Injected rekey failure ignored") } catch (_: IllegalStateException) { }
        assertEquals(rawBefore, db.rawDao().exportEntries())
        assertFalse(db.rawDao().storageState()!!.moduleLocked)
        cipher.failAfter = Int.MAX_VALUE
        assertEquals(entries.toSet(), db.dao().exportEntries().toSet())
    }

    @Test fun ciphertextCannotBeSubstitutedBetweenRowsOrTampered() = runBlocking {
        val a = JournalEntry(text = "One"); val b = JournalEntry(text = "Two")
        db.dao().insert(a); db.dao().insert(b)
        val rawA = db.rawDao().entry(a.id)!!; val rawB = db.rawDao().entry(b.id)!!
        db.rawDao().update(rawB.copy(text = rawA.text))
        try { db.dao().entry(b.id); fail("Row substitution accepted") } catch (_: java.security.GeneralSecurityException) { }
        val bytes = android.util.Base64.decode(rawA.text, android.util.Base64.NO_WRAP)
        bytes[bytes.lastIndex] = (bytes.last().toInt() xor 1).toByte()
        db.rawDao().update(rawA.copy(text = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)))
        try { db.dao().entry(a.id); fail("Tampering accepted") } catch (_: java.security.GeneralSecurityException) { }
    }

    @Test fun realKeystoreRoundTripsMaximumImagePayload() {
        val real = KeystoreJournalCipher()
        for (size in listOf(65_536, 900_000, 2_000_000)) {
            val bytes = ByteArray(size).also { java.util.Random(123).nextBytes(it) }
            assertArrayEquals("Payload size $size", bytes, real.open(real.seal(bytes, "image:$size", false), "image:$size"))
        }
    }

    @Test fun realKeystoreRoundTripUsesAuthenticatedCiphertext() {
        val real = KeystoreJournalCipher()
        val source = "Journal real Keystore test".toByteArray()
        val first = real.seal(source, "test-identity", false)
        val second = real.seal(source, "test-identity", false)
        assertFalse(first.contentEquals(second))
        assertFalse(first.toString(Charsets.UTF_8).contains("Journal real"))
        assertArrayEquals(source, KeystoreJournalCipher().open(first, "test-identity"))
        try { real.open(first, "different-identity"); fail("Identity mismatch accepted") } catch (_: java.security.GeneralSecurityException) { }
        try { real.seal(source, "test", true); fail("Locked private key accepted") } catch (_: IllegalStateException) { }
    }
}

internal class TestJournalCipher : JournalCipher {
    private val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    var failAfter = Int.MAX_VALUE
    var upgradeRequired = false
    override fun needsSessionUpgrade(bytes: ByteArray) = upgradeRequired && bytes.size >= 2 && bytes[1] == 1.toByte()
    override fun seal(bytes: ByteArray, identity: String, protected: Boolean): ByteArray {
        if (protected) JournalProtection.requireAuthorization()
        check(failAfter-- > 0) { "Injected encryption failure" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.ENCRYPT_MODE, key)
        val header = byteArrayOf(1, if (protected) 1 else 0)
        cipher.updateAAD(header + identity.toByteArray())
        return header + cipher.iv + cipher.doFinal(bytes)
    }
    override fun open(bytes: ByteArray, identity: String): ByteArray {
        if (bytes[1] == 1.toByte()) JournalProtection.requireAuthorization()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, bytes.copyOfRange(2, 14)))
        cipher.updateAAD(bytes.copyOfRange(0, 2) + identity.toByteArray())
        return cipher.doFinal(bytes.copyOfRange(14, bytes.size))
    }
}
