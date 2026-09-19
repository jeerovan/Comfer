package com.jeerovan.comfer.journals

import android.graphics.Bitmap
import android.net.Uri
import androidx.room.withTransaction
import androidx.test.platform.app.InstrumentationRegistry
import com.jeerovan.comfer.BackupRestoreManager
import com.jeerovan.comfer.StartupCoordinator
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.*
import org.junit.Assert.*
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID

class JournalProtectedBackupTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var db: JournalDatabase
    private lateinit var original: JournalLocalSnapshot
    private lateinit var originalCipher: JournalCipher
    @Before fun setup() = runBlocking {
        check(context.packageName.endsWith(".notificationtest")); StartupCoordinator.awaitReady()
        db = JournalDatabase.get(context)
        original = JournalArchiveMedia.localSnapshot(context)
        originalCipher = db.cipher
        db.cipher = TestJournalCipher()
        db.withTransaction {
            db.rawDao().clearEntries(); db.rawDao().clearDrafts(); db.rawDao().clearSegments()
            db.rawDao().state(JournalState(storageVersion = 1))
        }
        JournalProtection.restoreState(context, false); JournalProtection.lock()
    }
    @After fun cleanup() = runBlocking {
        if (!::originalCipher.isInitialized) return@runBlocking
        db.cipher = originalCipher
        JournalArchiveMedia.replaceLocal(context, original)
        JournalProtection.lock()
    }
    private fun jpeg(): ByteArray {
        val bitmap = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.BLUE)
        return ByteArrayOutputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it); bitmap.recycle(); it.toByteArray() }
    }
    private suspend fun protect() {
        JournalProtection.authorize(); db.dao().changeProtection(true); JournalProtection.restoreState(context, true)
    }
    @Test fun protectionRekeysPhotosAndRemovesObsoleteFiles() = runBlocking {
        val id = "${UUID.randomUUID()}.jpg"; val bytes = jpeg()
        JournalMedia(context).write(id, bytes, false)
        val entry = JournalEntry(text = "protected image marker", image = id)
        db.dao().insert(entry)
        protect()
        JournalProtection.prepare(context)
        val restored = db.dao().entry(entry.id)!!
        assertEquals(entry.text, restored.text)
        assertNotEquals(id, restored.image)
        assertFalse(JournalMedia(context).file(id).exists())
        assertArrayEquals(bytes, JournalMedia(context).read(restored.image!!))
        assertNull(android.graphics.BitmapFactory.decodeFile(JournalMedia(context).file(restored.image!!).path))
        val diskFiles = listOf("journals.db", "journals.db-wal", "journals.db-shm").map { File(context.noBackupFilesDir, it) }
        diskFiles.filter { it.isFile }.forEach { assertFalse(it.readBytes().toString(Charsets.ISO_8859_1).contains(entry.text)) }
        JournalProtection.lock()
        try { JournalMedia(context).read(restored.image!!); fail("Private photo opened while locked") } catch (_: IllegalStateException) { }
    }
    @Test fun protectedBackupsRequirePasswordAndWrongPasswordLeavesDataIntact() = runBlocking {
        val media = JournalMedia(context); val id = "${UUID.randomUUID()}.jpg"; val bytes = jpeg()
        media.write(id, bytes, false)
        val entry = JournalEntry(text = "protected backup marker", image = id)
        db.dao().insert(entry); db.dao().putDraft(JournalDraft(text = "protected draft marker"))
        protect()
        val before = JournalBackup.snapshot(context)
        try { JournalBackup.pack(before, null); fail("Unencrypted protected archive accepted") } catch (_: IllegalArgumentException) { }
        try { JournalArchiveMedia.prepare(context, null).close(); fail("Missing password accepted") } catch (_: IllegalArgumentException) { }
        val archive = File(context.cacheDir, "protected-journal-regression.zip")
        try {
            BackupRestoreManager.createBackup(context, Uri.fromFile(archive), "en", "regression-password")
            java.util.zip.ZipFile(archive).use { zip ->
                val payloadText = zip.getInputStream(zip.getEntry("payload.json")).reader().readText()
                assertFalse(payloadText.contains(entry.text))
                val payload = org.json.JSONObject(payloadText)
                assertTrue(payload.getJSONObject("notes").getBoolean("encrypted"))
                assertTrue(payload.getJSONObject("journals").getBoolean("encrypted"))
                zip.entries().asSequence().filter { it.name.startsWith("journals/") }.forEach {
                    val data = zip.getInputStream(it).readBytes()
                    assertNull(android.graphics.BitmapFactory.decodeByteArray(data, 0, data.size))
                }
            }
            try { BackupRestoreManager.restoreBackup(context, Uri.fromFile(archive), "wrong-password"); fail("Wrong password accepted") } catch (_: Exception) { }
            assertEquals(before, JournalBackup.snapshot(context))
            BackupRestoreManager.restoreBackup(context, Uri.fromFile(archive), "regression-password")
            assertTrue(JournalProtection.requiresAuthentication(context))
            assertEquals(entry.text, db.dao().entry(entry.id)!!.text)
            assertArrayEquals(bytes, media.read(db.dao().entry(entry.id)!!.image!!))
            JournalProtection.lock()
            try { db.dao().entry(entry.id); fail("Restored Journal is unlocked") } catch (_: IllegalStateException) { }
        } finally { archive.delete() }
    }
    @Test fun ciphertextCheckpointCanRecoverWhileLocked() = runBlocking {
        val entry = JournalEntry(text = "checkpoint private marker")
        db.dao().insert(entry); protect()
        val checkpoint = JournalArchiveMedia.localSnapshot(context)
        assertTrue(checkpoint.deviceEncrypted)
        assertFalse(Json.encodeToString(checkpoint).contains(entry.text))
        db.dao().update(entry.copy(text = "Replacement", revision = 1))
        JournalProtection.lock()
        JournalArchiveMedia.restoreCheckpoint(context, checkpoint)
        assertTrue(JournalProtection.requiresAuthentication(context))
        try { db.dao().entry(entry.id); fail("Recovered content opened without authentication") } catch (_: IllegalStateException) { }
        JournalProtection.authorize()
        assertEquals(entry, db.dao().entry(entry.id))
    }
    @Test fun plaintextCheckpointIsRejectedWithoutReplacingContent() = runBlocking {
        val entry = JournalEntry(text = "Keep current content")
        db.dao().insert(entry)
        val checkpoint = JournalLocalSnapshot(listOf(JournalEntry(text = "Unsupported checkpoint")), emptyList())
        try { JournalArchiveMedia.restoreCheckpoint(context, checkpoint); fail("Plaintext checkpoint accepted") } catch (_: IllegalArgumentException) { }
        assertEquals(listOf(entry), db.dao().exportEntries())
    }
    @Test fun restoringUnprotectedArchiveIntoProtectedJournalRekeysPhotos() = runBlocking {
        val id = "${UUID.randomUUID()}.jpg"; val bytes = jpeg(); val media = JournalMedia(context)
        media.write(id, bytes, false)
        val incoming = JournalLocalSnapshot(listOf(JournalEntry(text = "incoming", image = id)), emptyList(), protectionEnabled = true)
        protect(); JournalArchiveMedia.replaceLocal(context, incoming)
        val restored = db.dao().exportEntries().single()
        assertArrayEquals(bytes, media.read(restored.image!!))
        JournalProtection.lock()
        try { media.read(restored.image!!); fail("Restored photo used an unauthenticated key") } catch (_: IllegalStateException) { }
    }
}
