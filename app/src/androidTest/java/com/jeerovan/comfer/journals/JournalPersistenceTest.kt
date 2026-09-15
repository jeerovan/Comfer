package com.jeerovan.comfer.journals

import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*

class JournalPersistenceTest {
    private lateinit var db: JournalDatabase
    private lateinit var store: JournalStore
    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, JournalDatabase::class.java).build()
        store = JournalStore(db)
    }
    @After fun close() = db.close()
    @Test fun archiveExpiresAtSevenDaysAndPreservesRecoveredEdits() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName.endsWith(".notificationtest"))
        val now = System.currentTimeMillis()
        val cutoff = now - 7L * 24 * 60 * 60 * 1000
        val expired = JournalEntry(text = "Expired", deletedAt = cutoff)
        val recent = JournalEntry(text = "Recent", deletedAt = cutoff + 1)
        val recovered = JournalEntry(text = "Recovered", deletedAt = cutoff - 1)
        listOf(expired, recent, recovered).forEach { db.dao().insert(it) }
        db.dao().putDraft(JournalDraft(key = "edit:${recovered.id}", entryId = recovered.id, text = "Unsaved edit"))
        assertEquals(setOf(recent.id, recovered.id), db.dao().trash(cutoff).first().map { it.id }.toSet())
        JournalMedia(context).collect(db, now)
        assertNull(db.dao().entry(expired.id))
        assertNotNull(db.dao().entry(recent.id))
        assertNotNull(db.dao().entry(recovered.id))
    }

    @Test fun chosenCreationTimeSurvivesDraftAndEdit() = runBlocking {
        val chosen = java.time.ZonedDateTime.of(2026, 9, 14, 18, 25, 0, 0, java.time.ZoneId.of("Asia/Kolkata"))
        val draft = store.saveDraft(store.ensureDraft().copy(text = "Backdated", day = chosen.toLocalDate().toEpochDay(), zone = chosen.zone.id, createdAt = chosen.toInstant().toEpochMilli()))
        assertEquals(draft.createdAt, JournalStore(db).ensureDraft().createdAt)
        val entry = store.submit(draft)
        assertEquals(chosen.toInstant().toEpochMilli(), entry.createdAt)
        assertEquals(draft.day, entry.day)
        val edit = store.beginEdit(entry)
        assertEquals(entry.createdAt, edit.createdAt)
        store.discardDraft(edit.key)
        assertEquals(entry, db.dao().entry(entry.id))
        val revised = chosen.minusDays(1)
        val changed = store.saveEdit(entry, entry.text, entry.image, revised.toInstant().toEpochMilli(), revised.toLocalDate().toEpochDay())
        assertEquals("Backdated", changed.text)
        assertEquals(revised.toInstant().toEpochMilli(), changed.createdAt)
        assertEquals(revised.toLocalDate().toEpochDay(), changed.day)
        try { store.saveEdit(entry, "Stale", null); fail("Stale edit must fail") } catch (_: JournalConflict) { }
    }

    @Test fun defaultCreationTimeUsesCurrentTimeOnSelectedDay() = runBlocking {
        val now = java.time.Instant.parse("2026-09-15T12:34:56Z").toEpochMilli()
        val today = java.time.LocalDate.of(2026, 9, 15).toEpochDay()
        val current = JournalDraft(day = today, zone = "UTC")
        assertEquals(now, current.creationTime(now))
        assertEquals(now - 24L * 60 * 60 * 1000, current.copy(day = today - 1).creationTime(now))
    }

    @Test fun duplicateDeletionIsIdempotentButOldDeletesAfterUndoAreRejected() = runBlocking {
        val entry = store.submit(store.saveDraft(store.ensureDraft().copy(text = "Delete once")))
        val deleted = store.delete(entry, 1234)
        assertEquals(deleted, store.delete(entry, 5678))
        val restored = store.restore(deleted)
        try { store.delete(entry); fail("Old swipe must not delete a restored entry") } catch (_: JournalConflict) { }
        assertEquals(restored, db.dao().entry(entry.id))
        val edited = store.saveEdit(restored, "New text", null)
        try { store.delete(restored); fail("Old swipe must not delete newer text") } catch (_: JournalConflict) { }
        assertEquals(edited, db.dao().entry(entry.id))
    }

    @Test fun duplicateSubmitConsumesOnlyItsOwnDraft() = runBlocking {
        val draft = store.saveDraft(store.ensureDraft().copy(text = "  First  ", day = -100, zone = "Asia/Kolkata"))
        val entries = coroutineScope { List(8) { async { store.submit(draft, 42) } }.awaitAll() }
        assertEquals(1, entries.map { it.id }.distinct().size)
        assertEquals("  First  ", entries.first().text)
        assertEquals(-100L, entries.first().day)
        assertEquals(1, db.dao().page(-100).size)
        val next = store.saveDraft(store.ensureDraft().copy(text = "Second"))
        store.submit(draft)
        assertEquals(next, db.dao().draft())
    }
    @Test fun staleDraftAndEditCannotOverwriteNewerContent() = runBlocking {
        val first = store.ensureDraft()
        val second = store.saveDraft(first.copy(text = "Saved"))
        try { store.saveDraft(first.copy(text = "Stale")); fail() } catch (_: JournalConflict) { }
        val entry = store.submit(second)
        val edited = store.saveEdit(entry, "Current", null)
        try { store.saveEdit(entry, "Old", null); fail() } catch (_: JournalConflict) { }
        assertEquals(edited, db.dao().entry(entry.id))
        val deleted = store.delete(edited, 1234)
        assertEquals(emptyList<JournalEntry>(), db.dao().page(entry.day))
        val restored = store.restore(deleted)
        assertEquals("Current", restored.text)
        assertEquals(entry.createdAt, restored.createdAt)
        try { store.restore(deleted); fail() } catch (_: JournalConflict) { }
    }
    @Test fun failedTransactionLeavesDraftAndNoEntry() = runBlocking {
        val draft = store.saveDraft(store.ensureDraft().copy(text = "Keep"))
        try { db.withTransaction { store.submit(draft); error("Injected commit failure") } } catch (_: IllegalStateException) { }
        assertEquals(draft, db.dao().draft())
        assertNull(db.dao().entry(draft.entryId))
    }
    @Test fun tenThousandEntriesAreBoundedAndOrdered() = runBlocking {
        db.withTransaction { repeat(10000) { db.dao().insert(JournalEntry(id = "id%05d".format(it), day = it.toLong() / 100, createdAt = it.toLong())) } }
        val first = db.dao().page(50, 25)
        val second = db.dao().page(50, 25, 25)
        assertEquals(25, first.size)
        assertEquals("id05000", first.first().id)
        assertEquals("id05025", second.first().id)
        assertEquals(49L, db.dao().previousDay(50))
        assertEquals(51L, db.dao().nextDay(50))
        assertTrue(db.dao().page(1000).isEmpty())
        val window = db.dao().observeDay(99, 151).first()
        val older = db.dao().observeDay(99, 151, 50).first()
        assertEquals(151, window.size); assertEquals(151, older.size)
        assertEquals("id09849", window.first().id)
        assertEquals("id09799", older.first().id)
        val plan = db.openHelper.readableDatabase.query("EXPLAIN QUERY PLAN SELECT * FROM journal_entries WHERE day<=99 AND deletedAt IS NULL ORDER BY day DESC,createdAt DESC,id DESC LIMIT 151").use { cursor ->
            buildList { while(cursor.moveToNext()) add(cursor.getString(3)) }
        }
        assertFalse("Feed must not sort the complete archive: $plan", plan.any { it.contains("TEMP B-TREE", ignoreCase = true) })
    }

    @Test fun speechRecoveryKeepsOneRowAndNeverRestartsCapture() = runBlocking {
        val draft = store.saveDraft(store.ensureDraft().copy(text = "Prefix", day = -5))
        store.speech(draft, "session", 0, "Prefix", "Hypothesis", false, false)
        store.speech(draft, "session", 0, "Prefix", "Revised hypothesis", false, false)
        assertEquals("Prefix Revised hypothesis", db.dao().page(-5).single().text)
        assertEquals("Revised hypothesis", db.dao().segments(draft.entryId).single().provisional)
        assertNotNull(db.dao().entry(draft.entryId)!!.needsReview)
        // Reopening consumes the live draft identity; recovered content stays in its one row.
        val reopened = JournalStore(db).ensureDraft()
        assertNotEquals(draft.entryId, reopened.entryId)
        assertFalse(reopened.hasContent)
        assertEquals(1, db.dao().page(-5).size)
    }
    @Test fun fileDatabaseReopenPreservesDraftAndCalendarDate() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = java.io.File(context.cacheDir, "journal-reopen-${java.util.UUID.randomUUID()}.db")
        val first = Room.databaseBuilder(context, JournalDatabase::class.java, file.absolutePath).build()
        val original = JournalStore(first).saveDraft(JournalStore(first).ensureDraft().copy(text = "Recovered", day = -1000, zone = "Pacific/Auckland"))
        first.close()
        val reopened = Room.databaseBuilder(context, JournalDatabase::class.java, file.absolutePath).build()
        try { assertEquals(original, JournalStore(reopened).ensureDraft()) } finally { reopened.close(); context.deleteDatabase(file.absolutePath) }
    }

    @Test fun restoreGenerationRejectsQueuedDraftAndSpeechWrites() = runBlocking {
        val draft = store.saveDraft(store.ensureDraft().copy(text = "Old draft"))
        db.dao().state(JournalState(generation = 1))
        try { store.submit(draft); fail() } catch (_: JournalConflict) { }
        try { store.speech(draft, "old", 0, "Old capture", "", true, false); fail() } catch (_: JournalConflict) { }
        assertTrue(db.dao().page(draft.day).isEmpty())
        assertEquals(draft, db.dao().draft())
    }
    @Test fun versionOneMigrationPreservesEntryAndInitializesGeneration() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val file = java.io.File(context.cacheDir, "journal-migration-${java.util.UUID.randomUUID()}.db")
        val schema = org.json.JSONObject(instrumentation.context.assets.open("com.jeerovan.comfer.journals.JournalDatabase/1.json").reader().readText()).getJSONObject("database")
        android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(file, null).use { database ->
            val entities = schema.getJSONArray("entities")
            for(index in 0 until entities.length()) {
                val entity = entities.getJSONObject(index)
                database.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", entity.getString("tableName")))
                val indices = entity.optJSONArray("indices") ?: org.json.JSONArray()
                for (item in 0 until indices.length()) database.execSQL(indices.getJSONObject(item).getString("createSql").replace("\${TABLE_NAME}", entity.getString("tableName")))
            }
            database.execSQL("INSERT INTO journal_entries(id,day,zone,createdAt,updatedAt,text,image,revision,deletedAt,needsReview) VALUES ('migration',-100,'Asia/Kolkata',42,42,'Keep entry',NULL,0,NULL,NULL)")
            database.version = 1
        }
        val upgraded = Room.databaseBuilder(context, JournalDatabase::class.java, file.absolutePath).addMigrations(JournalDatabase.MIGRATION_1_2, JournalDatabase.MIGRATION_2_3, JournalDatabase.MIGRATION_3_4).build()
        try {
            assertEquals("Keep entry", upgraded.dao().entry("migration")!!.text)
            assertEquals(-100L, upgraded.dao().entry("migration")!!.day)
            assertEquals(0L, upgraded.dao().generation())
        } finally { upgraded.close(); context.deleteDatabase(file.absolutePath) }
    }

    @Test fun failedFinalSpeechSaveRetainsDraftAndCanRetry() = runBlocking {
        val draft = store.saveDraft(store.ensureDraft().copy(text = "Prefix"))
        store.speech(draft, "retry", 0, "Partial", "", false, false)
        db.openHelper.writableDatabase.execSQL("CREATE TEMP TRIGGER fail_final BEFORE UPDATE ON journal_entries WHEN NEW.text='Final' BEGIN SELECT RAISE(ABORT,'Injected final failure'); END")
        try { store.speech(draft, "retry", 0, "Final", "", true, false); fail() } catch (_: android.database.sqlite.SQLiteException) { }
        assertEquals("Partial", db.dao().entry(draft.entryId)!!.text)
        assertEquals(draft, db.dao().draft())
        db.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_final")
        store.speech(draft, "retry", 0, "Final", "", true, false)
        assertEquals("Final", db.dao().entry(draft.entryId)!!.text)
        assertNull(db.dao().entry(draft.entryId)!!.needsReview)
        assertNotEquals(draft.entryId, db.dao().draft()!!.entryId)
        assertEquals(1, db.dao().page(draft.day).size)
    }
}
