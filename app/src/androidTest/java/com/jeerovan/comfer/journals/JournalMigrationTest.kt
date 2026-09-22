package com.jeerovan.comfer.journals

import android.database.sqlite.SQLiteDatabase
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

class JournalMigrationTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun version4UpgradePreservesContentAndCanReopen() = verifyUpgrade(4)
    @Test fun version1UpgradePreservesContent() = verifyUpgrade(1)
    @Test fun version2UpgradePreservesContent() = verifyUpgrade(2)
    @Test fun version3UpgradePreservesContent() = verifyUpgrade(3)

    @Test fun encryptionFailureRollsBackAndReopenRetriesWithoutChangingOriginalContent() = runBlocking {
        val file = File(context.noBackupFilesDir, "journal-upgrade-${UUID.randomUUID()}.db")
        fixture(4, file)
        val cipher = TestJournalCipher().apply { failAfter = 1 }
        try {
            val first = JournalDatabase.open(context, file.absolutePath).also { it.cipher = cipher }
            try {
                try { first.dao().initialize(); fail("Injected encryption failure was ignored") }
                catch (expected: IllegalStateException) { assertTrue(expected.message!!.contains("Injected")) }
                assertEquals("Private old entry", first.rawDao().entry("entry")!!.text)
                assertEquals("Unfinished draft", first.rawDao().draft()!!.text)
                assertEquals(9L, first.rawDao().storageState()!!.generation)
                assertEquals(LEGACY_JOURNAL_STORAGE, first.rawDao().storageState()!!.storageVersion)
            } finally { first.close() }
            cipher.failAfter = Int.MAX_VALUE
            val retry = JournalDatabase.open(context, file.absolutePath).also { it.cipher = cipher }
            try {
                assertEquals("Private old entry", retry.dao().entry("entry")!!.text)
                assertEquals("Unfinished draft", retry.dao().draft()!!.text)
                assertEquals(1, retry.rawDao().storageState()!!.storageVersion)
            } finally { retry.close() }
        } finally { context.deleteDatabase(file.absolutePath) }
    }

    @Test fun legacyImageIsCopiedEncryptedAndSharedReferencesStayConsistent() = runBlocking {
        val file = File(context.noBackupFilesDir, "journal-upgrade-${UUID.randomUUID()}.db")
        fixture(4, file)
        val id = "${UUID.randomUUID()}.jpg"
        val original = JournalMedia(context).file(id)
        val bytes = byteArrayOf(0xff.toByte(), 0xd8.toByte(), 1, 2, 3, 0xff.toByte(), 0xd9.toByte())
        original.writeBytes(bytes)
        SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READWRITE).use {
            it.execSQL("UPDATE journal_entries SET image=?", arrayOf(id))
            it.execSQL("UPDATE journal_drafts SET image=?", arrayOf(id))
        }
        val cipher = TestJournalCipher()
        var converted: File? = null
        val room = JournalDatabase.open(context, file.absolutePath).also { it.cipher = cipher }
        try {
            val entry = room.dao().entry("entry")!!
            val draft = room.dao().draft()!!
            assertNotEquals(id, entry.image)
            assertEquals(entry.image, draft.image)
            converted = JournalMedia(context).file(entry.image!!)
            assertFalse(bytes.contentEquals(converted.readBytes()))
            assertArrayEquals(bytes, cipher.open(converted.readBytes(), "Comfer Journal image 1:${entry.image}"))
            assertArrayEquals(bytes, original.readBytes())
            assertTrue(room.rawDao().storageState()!!.cleanupPending)
            val ciphertext = room.rawDao().entry("entry")!!.text
            room.dao().initialize()
            assertEquals(ciphertext, room.rawDao().entry("entry")!!.text)
        } finally { room.close(); original.delete(); converted?.delete(); context.deleteDatabase(file.absolutePath) }
    }

    @Test fun missingLegacyImageRetainsRowsAndCanRetryAfterImageRecovery() = runBlocking {
        val file = File(context.noBackupFilesDir, "journal-upgrade-${UUID.randomUUID()}.db")
        fixture(4, file)
        val id = "${UUID.randomUUID()}.jpg"
        val original = JournalMedia(context).file(id)
        SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READWRITE).use {
            it.execSQL("UPDATE journal_entries SET image=?", arrayOf(id))
        }
        val room = JournalDatabase.open(context, file.absolutePath).also { it.cipher = TestJournalCipher() }
        var converted: File? = null
        try {
            try { room.dao().initialize(); fail("Missing image was silently discarded") }
            catch (expected: IllegalArgumentException) { assertTrue(expected.message!!.contains("retained")) }
            assertEquals(id, room.rawDao().entry("entry")!!.image)
            assertEquals("Private old entry", room.rawDao().entry("entry")!!.text)
            original.writeBytes(byteArrayOf(1, 2, 3))
            val entry = room.dao().entry("entry")!!
            assertEquals("Private old entry", entry.text)
            converted = JournalMedia(context).file(entry.image!!)
        } finally { room.close(); original.delete(); converted?.delete(); context.deleteDatabase(file.absolutePath) }
    }

    private fun fixture(version: Int, path: File) {
        val schema = InstrumentationRegistry.getInstrumentation().context.assets
            .open("com.jeerovan.comfer.journals.JournalDatabase/$version.json")
            .bufferedReader().use { JSONObject(it.readText()).getJSONObject("database") }
        SQLiteDatabase.openOrCreateDatabase(path, null).use { db ->
            val entities = schema.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i)
                fun sql(value: String) = value.replace("\${TABLE_NAME}", entity.getString("tableName"))
                db.execSQL(sql(entity.getString("createSql")))
                val indices = entity.optJSONArray("indices") ?: org.json.JSONArray()
                for (j in 0 until indices.length()) db.execSQL(sql(indices.getJSONObject(j).getString("createSql")))
            }
            val setup = schema.getJSONArray("setupQueries")
            for (i in 0 until setup.length()) db.execSQL(setup.getString(i))
            db.execSQL("INSERT INTO journal_entries VALUES ('entry',20000,'Asia/Kolkata',100,200,'Private old entry',NULL,7,300,'Review dictation')")
            db.execSQL("INSERT INTO journal_drafts (`key`,entryId,day,zone,text,image,prompt,language,baseRevision,revision) VALUES ('composer','draft-entry',20001,'Asia/Kolkata','Unfinished draft',NULL,2,'hi-IN',-1,4)")
            if (version >= 4) db.execSQL("UPDATE journal_drafts SET createdAt=150")
            db.execSQL("INSERT INTO journal_segments VALUES ('session',2,'entry','Final speech','Provisional speech',0)")
            if (version >= 2) db.execSQL("INSERT INTO journal_state (id,generation) VALUES (1,9)")
            db.version = version
        }
    }

    private fun verifyUpgrade(version: Int) = runBlocking {
        val file = File(context.noBackupFilesDir, "journal-upgrade-${UUID.randomUUID()}.db")
        fixture(version, file)
        val cipher = TestJournalCipher()
        try {
            repeat(2) {
                val room = JournalDatabase.open(context, file.absolutePath).also { it.cipher = cipher }
                try {
                    assertEquals(5, room.openHelper.writableDatabase.version)
                    val dao = room.dao()
                    val entry = dao.entry("entry")!!
                    assertEquals("Private old entry", entry.text)
                    assertEquals("Asia/Kolkata", entry.zone)
                    assertEquals(7L, entry.revision)
                    assertEquals(300L, entry.deletedAt)
                    assertEquals("Review dictation", entry.needsReview)
                    val draft = dao.draft()!!
                    assertEquals("Unfinished draft", draft.text)
                    assertEquals("hi-IN", draft.language)
                    assertEquals(4L, draft.revision)
                    assertEquals(if (version >= 4) 150L else null, draft.createdAt)
                    assertEquals(listOf(JournalSegment("session",2,"entry","Final speech","Provisional speech",false)), dao.segments("entry"))
                    assertEquals(if (version >= 2) 9L else 0L, dao.generation())
                    assertEquals(1, room.rawDao().storageState()!!.storageVersion)
                    assertFalse(room.rawDao().entry("entry")!!.text.contains("Private old entry"))
                    assertFalse(room.rawDao().draft()!!.text.contains("Unfinished draft"))
                } finally { room.close() }
            }
        } finally { context.deleteDatabase(file.absolutePath) }
    }
}
