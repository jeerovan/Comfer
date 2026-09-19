package com.jeerovan.comfer.journals

import android.graphics.Bitmap
import android.net.Uri
import androidx.room.withTransaction
import androidx.test.platform.app.InstrumentationRegistry
import com.jeerovan.comfer.BackupRestoreManager
import com.jeerovan.comfer.StartupCoordinator
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import java.io.File

class JournalBackupTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var previous: JournalSnapshot
    @Before fun setup() = runBlocking {
        check(context.packageName.endsWith(".notificationtest"))
        StartupCoordinator.awaitReady()
        androidx.work.WorkManager.getInstance(context).cancelUniqueWork("ImageWorker").result.get()
        previous = JournalBackup.snapshot(context)
        JournalBackup.replace(context, JournalSnapshot(entries = emptyList(), drafts = emptyList(), media = emptyList()))
    }
    @After fun cleanup() = runBlocking { JournalBackup.replace(context, previous) }
    @Test fun selectedDraftTimeSurvivesBackupSerialization() {
        val draft = JournalDraft(text = "Timed draft", createdAt = 1750000000000L)
        val snapshot = JournalSnapshot(entries = emptyList(), drafts = listOf(draft), media = emptyList())
        val json = kotlinx.serialization.json.Json
        val encoded = json.encodeToString(JournalSnapshot.serializer(), snapshot)
        assertEquals(snapshot, json.decodeFromString(JournalSnapshot.serializer(), encoded))
    }

    @Test fun shortPasswordsRejectedByBothExportFormats() = runBlocking {
        val snapshot = JournalBackup.snapshot(context)
        for (password in listOf("", "1", "12", "123")) {
            try { JournalBackup.pack(snapshot, password); fail("Accepted short password") }
            catch (_: IllegalArgumentException) { }
            try { JournalArchiveMedia.prepare(context, password).use { }; fail("Accepted short password") }
            catch (_: IllegalArgumentException) { }
        }
    }

    @Test fun encryptedPortableRoundTripAndWrongPassword() = runBlocking {
        val store = JournalStore(JournalDatabase.get(context))
        val image = File(context.cacheDir, "journal-test.png")
        val bitmap = Bitmap.createBitmap(128, 64, Bitmap.Config.ARGB_8888)
        image.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
        val id = JournalMedia(context).import(Uri.fromFile(image))
        val draft = store.saveDraft(store.ensureDraft().copy(text = "Private synthetic journal", image = id))
        store.submit(draft)
        val before = JournalBackup.snapshot(context)
        val protected = JournalBackup.pack(before, "synthetic-password-only")
        assertFalse(protected.content.contains("Private synthetic"))
        try { JournalBackup.unpack(protected, "wrong-password"); fail() } catch (_: Exception) { }
        assertEquals(before, JournalBackup.snapshot(context))
        val decoded = JournalBackup.unpack(protected, "synthetic-password-only")
        JournalBackup.replace(context, decoded)
        val restored = JournalBackup.snapshot(context)
        assertEquals(before.entries.single().text, restored.entries.single().text)
        assertNotEquals(before.entries.single().image, restored.entries.single().image)
        assertEquals(before.media.single().sha256, restored.media.single().sha256)
        image.delete(); Unit
    }
    @Test fun actualComferArchiveRestoresJournalAndDraft() = runBlocking {
        val store = JournalStore(JournalDatabase.get(context))
        val first = store.submit(store.saveDraft(store.ensureDraft().copy(text = "Archive entry")))
        store.saveDraft(store.ensureDraft().copy(text = "Unfinished", day = -123))
        val file = File(context.cacheDir, "journal-comfer.zip")
        BackupRestoreManager.createBackup(context, Uri.fromFile(file), "en")
        val preview = BackupRestoreManager.inspectBackup(context, Uri.fromFile(file))
        assertTrue(preview.journalsIncluded)
        store.delete(first)
        BackupRestoreManager.restoreBackup(context, Uri.fromFile(file))
        assertNull(store.dao.entry(first.id)!!.deletedAt)
        assertEquals("Unfinished", store.dao.draft()!!.text)
        assertEquals(-123L, store.dao.draft()!!.day)
        file.delete(); Unit
    }
    @Test fun invalidMediaAndDuplicateIdsLeaveDatabaseUntouched() = runBlocking {
        val store = JournalStore(JournalDatabase.get(context))
        val entry = store.submit(store.saveDraft(store.ensureDraft().copy(text = "Keep")))
        val before = JournalBackup.snapshot(context)
        try { JournalBackup.replace(context, before.copy(entries = listOf(entry, entry))); fail() } catch (_: IllegalArgumentException) { }
        assertEquals(before, JournalBackup.snapshot(context))
        try { JournalBackup.replace(context, before.copy(entries = listOf(entry.copy(image = "../../outside")))); fail() } catch (_: IllegalArgumentException) { }
        assertEquals(before, JournalBackup.snapshot(context))
    }
    @Test fun oversizeAndCorruptImagesAreRejected() = runBlocking {
        val file = File(context.cacheDir, "journal-bad-image")
        file.outputStream().use { out -> repeat(1221) { out.write(ByteArray(8192)) } }
        try { JournalMedia(context).import(Uri.fromFile(file)); fail() } catch (_: IllegalArgumentException) { }
        file.writeBytes(byteArrayOf(1, 2, 3))
        try { JournalMedia(context).import(Uri.fromFile(file)); fail() } catch (_: IllegalArgumentException) { }
        file.delete(); Unit
    }

    @Test fun exactSourceLimitIsAcceptedAndAnimatedPngIsRejected() = runBlocking {
        val file = File(context.cacheDir, "journal-boundary.png")
        val bitmap = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
        val original = file.readBytes()
        java.io.RandomAccessFile(file, "rw").use { it.setLength(10_000_000) }
        val id = JournalMedia(context).import(Uri.fromFile(file))
        assertTrue(JournalMedia(context).file(id).length() <= 2_000_000)
        val animation = java.io.ByteArrayOutputStream()
        java.io.DataOutputStream(animation).use { output ->
            output.writeInt(8)
            val payload = byteArrayOf(97, 99, 84, 76, 0, 0, 0, 2, 0, 0, 0, 0)
            output.write(payload)
            val crc = java.util.zip.CRC32(); crc.update(payload); output.writeInt(crc.value.toInt())
        }
        file.writeBytes(original.copyOfRange(0, 33) + animation.toByteArray() + original.copyOfRange(33, original.size))
        try { JournalMedia(context).import(Uri.fromFile(file)); fail() } catch (_: IllegalArgumentException) { }
        file.delete(); Unit
    }

    @Test fun missingJournalSectionPreservesDataAndExplicitEmptyReplacesIt() = runBlocking {
        val store = JournalStore(JournalDatabase.get(context))
        val entry = store.submit(store.saveDraft(store.ensureDraft().copy(text = "Keep with legacy archive")))
        val file = File(context.cacheDir, "journal-legacy.zip")
        BackupRestoreManager.createBackup(context, Uri.fromFile(file), "en")
        fun rewrite(empty: Boolean) {
            val contents = java.util.zip.ZipFile(file).use { zip -> zip.entries().asSequence().associate { it.name to zip.getInputStream(it).readBytes() }.toMutableMap() }
            val payload = org.json.JSONObject(String(contents.getValue("payload.json")))
            if (empty) payload.put("journals", org.json.JSONObject(kotlinx.serialization.json.Json.encodeToString(JournalArchive.serializer(), JournalBackup.pack(JournalSnapshot(entries = emptyList(), drafts = emptyList(), media = emptyList()), null))))
            else payload.remove("journals")
            val bytes = payload.toString().toByteArray()
            contents["payload.json"] = bytes
            val manifest = org.json.JSONObject(String(contents.getValue("manifest.json")))
            manifest.put("formatVersion", if(empty) 4 else 3)
            manifest.put("payloadSha256", java.security.MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) })
            contents["manifest.json"] = manifest.toString().toByteArray()
            java.util.zip.ZipOutputStream(file.outputStream()).use { zip -> contents.forEach { (name, data) -> zip.putNextEntry(java.util.zip.ZipEntry(name)); zip.write(data); zip.closeEntry() } }
        }
        rewrite(false)
        assertFalse(BackupRestoreManager.inspectBackup(context, Uri.fromFile(file)).journalsIncluded)
        BackupRestoreManager.restoreBackup(context, Uri.fromFile(file))
        assertEquals(entry, store.dao.entry(entry.id))
        rewrite(true)
        assertTrue(BackupRestoreManager.inspectBackup(context, Uri.fromFile(file)).journalsIncluded)
        BackupRestoreManager.restoreBackup(context, Uri.fromFile(file))
        assertTrue(store.dao.exportEntries().isEmpty())
        file.delete(); Unit
    }

    @Test fun journalWriteFailureRollsBackEveryModule() = runBlocking {
        val store = JournalStore(JournalDatabase.get(context))
        val incoming = store.submit(store.saveDraft(store.ensureDraft().copy(text = "Reject Journal restore")))
        val file = File(context.cacheDir, "journal-rollback.zip")
        BackupRestoreManager.createBackup(context, Uri.fromFile(file), "en")
        store.saveEdit(incoming, "Keep Journal", null)
        val before = JournalBackup.snapshot(context)
        val tasks = com.jeerovan.comfer.tasks.TaskStore.snapshot(context)
        val settings = com.jeerovan.comfer.PreferenceManager.snapshotForBackup()
        val room = com.jeerovan.comfer.data.ComferRepository.snapshot(context)
        val notifications = com.jeerovan.comfer.notifications.NotificationPreferences.withBackupAccess(context) { snapshot() }
        try {
            store.db.openHelper.writableDatabase.execSQL("CREATE TEMP TRIGGER reject_journal_restore BEFORE INSERT ON journal_entries WHEN NEW.id='${incoming.id}' AND NEW.revision=${incoming.revision} BEGIN SELECT RAISE(ABORT,'Injected Journal write failure'); END")
            try { BackupRestoreManager.restoreBackup(context, Uri.fromFile(file)); fail("Failed write accepted") } catch (_: android.database.sqlite.SQLiteException) { }
            assertEquals(before, JournalBackup.snapshot(context))
            assertEquals(tasks, com.jeerovan.comfer.tasks.TaskStore.snapshot(context))
            assertEquals(settings, com.jeerovan.comfer.PreferenceManager.snapshotForBackup())
            assertEquals(room, com.jeerovan.comfer.data.ComferRepository.snapshot(context))
            assertEquals(notifications, com.jeerovan.comfer.notifications.NotificationPreferences.withBackupAccess(context) { snapshot() })
            assertFalse(File(context.filesDir, "backup_restore_journal.json").exists())
        } finally { store.db.openHelper.writableDatabase.execSQL("DROP TRIGGER IF EXISTS reject_journal_restore"); file.delete() }
    }

    @Test fun archiveStreamsMoreThanTwelveMegabytesOfImagesOutsideJson() = runBlocking {
        val bitmap = Bitmap.createBitmap(1024, 1024, Bitmap.Config.ARGB_8888)
        val random = java.util.Random(123)
        bitmap.setPixels(IntArray(1024 * 1024) { random.nextInt() or (0xff shl 24) }, 0, 1024, 0, 0, 1024, 1024)
        val input = File(context.cacheDir, "journal-noise.png")
        input.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
        val media = JournalMedia(context)
        val first = media.import(Uri.fromFile(input))
        val source = media.file(first)
        val copies = (12_000_000 / source.length()).toInt() + 2
        val db = JournalDatabase.get(context)
        db.withTransaction {
            repeat(copies) {
                val id = "${java.util.UUID.randomUUID()}.jpg"
                media.write(id, media.read(first), false)
                db.dao().insert(JournalEntry(text = "Synthetic image $it", image = id))
            }
        }
        val archive = File(context.cacheDir, "journal-large.zip")
        BackupRestoreManager.createBackup(context, Uri.fromFile(archive), "en")
        java.util.zip.ZipFile(archive).use { zip ->
            assertTrue(zip.getEntry("payload.json").size < 100_000)
            assertTrue(zip.entries().asSequence().filter { it.name.startsWith("journals/") }.sumOf { it.size } > 12_000_000)
        }
        BackupRestoreManager.restoreBackup(context, Uri.fromFile(archive))
        assertEquals(copies, db.dao().exportEntries().size)
        db.dao().exportEntries().forEach { assertTrue(media.file(it.image!!).isFile) }
        archive.delete(); input.delete(); Unit
    }
    @Test fun protectedComferArchiveEncryptsAndRestoresExternalImage() = runBlocking {
        val bitmap = Bitmap.createBitmap(24, 24, Bitmap.Config.ARGB_8888)
        val input = File(context.cacheDir, "journal-private.png")
        input.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
        val image = JournalMedia(context).import(Uri.fromFile(input))
        val store = JournalStore(JournalDatabase.get(context))
        val entry = store.submit(store.saveDraft(store.ensureDraft().copy(text = "Protected synthetic text", image = image)))
        val archive = File(context.cacheDir, "journal-protected.zip")
        BackupRestoreManager.createBackup(context, Uri.fromFile(archive), "en", "1234")
        java.util.zip.ZipFile(archive).use { zip ->
            assertFalse(zip.getInputStream(zip.getEntry("payload.json")).reader().readText().contains("Protected synthetic text"))
            val encrypted = zip.getInputStream(zip.getEntry("journals/$image")).readBytes()
            assertFalse(encrypted.contentEquals(JournalMedia(context).file(image).readBytes()))
        }
        store.delete(entry)
        BackupRestoreManager.restoreBackup(context, Uri.fromFile(archive), "1234")
        val restored = store.dao.entry(entry.id)!!
        assertNull(restored.deletedAt)
        assertEquals(entry.text, restored.text)
        assertArrayEquals(JournalMedia(context).read(image), JournalMedia(context).read(restored.image!!))
        archive.delete(); input.delete(); Unit
    }

    @Test fun decodedPixelLimitIsInclusiveAndDecodeRemainsBounded() = runBlocking {
        fun png(width: Int, height: Int): ByteArray {
            val output = java.io.ByteArrayOutputStream()
            output.write(byteArrayOf(137.toByte(), 80, 78, 71, 13, 10, 26, 10))
            fun chunk(name: String, bytes: ByteArray) {
                val data = java.io.DataOutputStream(output)
                data.writeInt(bytes.size); data.writeBytes(name); data.write(bytes)
                val crc = java.util.zip.CRC32(); crc.update(name.toByteArray()); crc.update(bytes); data.writeInt(crc.value.toInt())
            }
            val header = java.nio.ByteBuffer.allocate(13).putInt(width).putInt(height).put(8.toByte()).put(2.toByte()).put(0.toByte()).put(0.toByte()).put(0.toByte()).array()
            chunk("IHDR", header)
            val compressed = java.io.ByteArrayOutputStream()
            java.util.zip.DeflaterOutputStream(compressed).use { zip -> val row = ByteArray(width * 3 + 1); repeat(height) { zip.write(row) } }
            chunk("IDAT", compressed.toByteArray()); chunk("IEND", byteArrayOf())
            return output.toByteArray()
        }
        val file = File(context.cacheDir, "journal-pixels.png")
        file.writeBytes(png(5000, 4000))
        val media = JournalMedia(context)
        val id = media.import(Uri.fromFile(file))
        val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        media.read(id).let { android.graphics.BitmapFactory.decodeByteArray(it, 0, it.size, bounds) }
        assertTrue(bounds.outWidth <= 2048 && bounds.outHeight <= 2048)
        file.writeBytes(png(5000, 4001))
        try { media.import(Uri.fromFile(file)); fail() } catch(e: IllegalArgumentException) { assertTrue(e.message!!.contains("20 megapixels")) }
        file.delete(); Unit
    }
}
