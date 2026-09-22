package com.jeerovan.comfer.journals

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

@Entity(tableName = "journal_entries", indices = [Index(value = ["day", "createdAt", "id"]), Index("deletedAt"), Index(value = ["deletedAt", "day", "createdAt", "id"])])
@kotlinx.serialization.Serializable
data class JournalEntry(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val day: Long = LocalDate.now().toEpochDay(),
    val zone: String = ZoneId.systemDefault().id,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = createdAt,
    val text: String = "",
    val image: String? = null,
    val revision: Long = 0,
    val deletedAt: Long? = null,
    val needsReview: String? = null,
)

/** Composer and each entry's edit buffer have different keys. IDs survive retries and restarts. */
@Entity(tableName = "journal_drafts")
@kotlinx.serialization.Serializable
data class JournalDraft(
    @PrimaryKey val key: String = "composer",
    val entryId: String = UUID.randomUUID().toString(),
    val day: Long = LocalDate.now().toEpochDay(),
    val zone: String = ZoneId.systemDefault().id,
    val text: String = "",
    val image: String? = null,
    val prompt: Int = 0,
    val language: String = java.util.Locale.getDefault().toLanguageTag(),
    val createdAt: Long? = null,
    val baseRevision: Long = -1,
    val revision: Long = 0,
) {
    val hasContent: Boolean get() = text.isNotBlank() || image != null
}

@Entity(tableName = "journal_segments", primaryKeys = ["sessionId", "segmentId"])
@kotlinx.serialization.Serializable
data class JournalSegment(
    val sessionId: String, val segmentId: Int, val entryId: String,
    val finalized: String, val provisional: String = "", val settled: Boolean = false,
)

@Entity(tableName = "journal_state")
data class JournalState(@PrimaryKey val id: Int = 1, val generation: Long = 0,
    @ColumnInfo(defaultValue = "0") val storageVersion: Int = 0,
    @ColumnInfo(defaultValue = "0") val moduleLocked: Boolean = false,
    @ColumnInfo(defaultValue = "0") val cleanupPending: Boolean = false)

@Dao
interface JournalRawDao {
    @Query("SELECT * FROM journal_state WHERE id=1") suspend fun storageState(): JournalState?
    @Query("SELECT * FROM journal_segments ORDER BY sessionId,segmentId") suspend fun allSegments(): List<JournalSegment>
    @Query("SELECT COALESCE(MAX(generation),0) FROM journal_state") suspend fun generation(): Long
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun state(state: JournalState)
    @Query("SELECT * FROM journal_segments WHERE entryId IN (SELECT id FROM journal_entries) ORDER BY sessionId,segmentId") suspend fun exportSegments(): List<JournalSegment>
    @Query("SELECT * FROM journal_entries ORDER BY day,createdAt,id") suspend fun exportEntries(): List<JournalEntry>
    @Query("SELECT * FROM journal_drafts ORDER BY `key`") suspend fun exportDrafts(): List<JournalDraft>
    @Query("DELETE FROM journal_entries") suspend fun clearEntries()
    @Query("DELETE FROM journal_drafts") suspend fun clearDrafts()
    @Query("SELECT image FROM journal_entries WHERE image IS NOT NULL UNION SELECT image FROM journal_drafts WHERE image IS NOT NULL") suspend fun referencedImages(): List<String>
    @Query("DELETE FROM journal_segments WHERE entryId NOT IN (SELECT id FROM journal_entries)") suspend fun removeOrphanSegments()
    @Query("DELETE FROM journal_segments") suspend fun clearSegments()

    @Query("SELECT * FROM journal_entries WHERE day=:day AND deletedAt IS NULL ORDER BY createdAt,id LIMIT :limit OFFSET :offset")
    suspend fun page(day: Long, limit: Int = 50, offset: Int = 0): List<JournalEntry>
    @Query("SELECT * FROM (SELECT * FROM journal_entries WHERE day<=:day AND deletedAt IS NULL ORDER BY day DESC,createdAt DESC,id DESC LIMIT :limit OFFSET :offset) ORDER BY day,createdAt,id")
    fun observeDay(day: Long, limit: Int, offset: Int = 0): Flow<List<JournalEntry>>
    @Query("SELECT DISTINCT day FROM journal_entries WHERE deletedAt IS NULL AND day<:day ORDER BY day DESC LIMIT 1")
    suspend fun previousDay(day: Long): Long?
    @Query("SELECT DISTINCT day FROM journal_entries WHERE deletedAt IS NULL AND day>:day ORDER BY day LIMIT 1")
    suspend fun nextDay(day: Long): Long?
    @Query("SELECT * FROM journal_entries WHERE id=:id") suspend fun entry(id: String): JournalEntry?
    @Query("SELECT * FROM journal_drafts WHERE `key`=:key") suspend fun draft(key: String = "composer"): JournalDraft?
    @Query("SELECT * FROM journal_drafts WHERE `key`='composer'") fun observeDraft(): Flow<JournalDraft?>
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insert(entry: JournalEntry)
    @Update suspend fun update(entry: JournalEntry)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putDraft(draft: JournalDraft)
    @Query("DELETE FROM journal_drafts WHERE `key`=:key") suspend fun removeDraft(key: String)
    @Query("SELECT * FROM journal_entries WHERE deletedAt IS NOT NULL AND (deletedAt>:cutoff OR id IN (SELECT entryId FROM journal_drafts WHERE `key` LIKE 'edit:%')) ORDER BY deletedAt DESC LIMIT :limit OFFSET :offset")
    fun trash(cutoff: Long = System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000, limit: Int = 100, offset: Int = 0): Flow<List<JournalEntry>>
    @Query("SELECT * FROM journal_entries WHERE deletedAt IS NOT NULL AND deletedAt<=:cutoff AND id NOT IN (SELECT entryId FROM journal_drafts WHERE `key` LIKE 'edit:%') LIMIT 100")
    suspend fun expired(cutoff: Long): List<JournalEntry>
    @Query("SELECT * FROM journal_entries WHERE deletedAt IS NOT NULL LIMIT 100") suspend fun trashForPurge(): List<JournalEntry>
    @Query("DELETE FROM journal_entries WHERE id=:id AND revision=:expected AND deletedAt IS NOT NULL")
    suspend fun deletePermanently(id: String, expected: Long): Int
    @Query("DELETE FROM journal_segments WHERE entryId=:id") suspend fun deleteSegments(id: String)
    @Transaction suspend fun purge(id: String, expected: Long): Int {
        val deleted = deletePermanently(id, expected)
        if (deleted == 1) { removeDraft("edit:$id"); deleteSegments(id) }
        return deleted
    }
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun segment(segment: JournalSegment)
    @Query("SELECT * FROM journal_segments WHERE entryId=:id ORDER BY sessionId,segmentId") suspend fun segments(id: String): List<JournalSegment>
}

@Database(entities = [JournalEntry::class, JournalDraft::class, JournalSegment::class, JournalState::class], version = 5, exportSchema = true)
abstract class JournalDatabase : RoomDatabase() {
    abstract fun rawDao(): JournalRawDao
    internal var storageContext: Context? = null
    internal var cipher: JournalCipher = KeystoreJournalCipher()
    private val contentDao by lazy { JournalDao(this) }
    fun dao(): JournalDao = contentDao
    companion object {
        @Volatile private var instance: JournalDatabase? = null
        fun get(context: Context): JournalDatabase = instance ?: synchronized(this) {
            instance ?: open(context, File(context.noBackupFilesDir, "journals.db").absolutePath)
                .also { instance = it }
        }
        /** Shared production/test factory so upgrade tests cover the registered paths. */
        internal fun open(context: Context, path: String): JournalDatabase =
            Room.databaseBuilder(context.applicationContext, JournalDatabase::class.java, path)
                .addMigrations(*JOURNAL_MIGRATIONS)
                .addCallback(object : Callback() {
                    override fun onOpen(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                        db.query("PRAGMA secure_delete=ON").use { it.moveToFirst() }
                    }
                }).build().also { it.storageContext = context.applicationContext }
    }
}

class JournalConflict : IllegalStateException("Journal changed; reopen it before saving")

/** A transaction either consumes this exact durable draft or leaves it available for retry. */
class JournalStore(val db: JournalDatabase) {
    val dao = db.dao()
    private var observedGeneration: Long? = null
    private suspend fun verifyGeneration() {
        val current = dao.generation()
        if(observedGeneration != null && observedGeneration != current) throw JournalConflict()
        observedGeneration = current
    }
    suspend fun beginEdit(entry: JournalEntry): JournalDraft = db.withTransaction {
        verifyGeneration()
        val current = dao.entry(entry.id) ?: throw JournalConflict()
        if(current.revision != entry.revision || current.deletedAt != null) throw JournalConflict()
        dao.draft("edit:${entry.id}") ?: JournalDraft(key = "edit:${entry.id}", entryId = entry.id,
            day = entry.day, zone = entry.zone, createdAt = entry.createdAt, text = entry.text, image = entry.image, baseRevision = entry.revision).also { dao.putDraft(it) }
    }
    suspend fun discardDraft(key: String) = db.withTransaction { verifyGeneration(); dao.removeDraft(key) }
    suspend fun ensureDraft(): JournalDraft = db.withTransaction {
        verifyGeneration()
        val existing = dao.draft()
        if (existing != null && dao.entry(existing.entryId) == null) existing
        else JournalDraft().also { dao.putDraft(it) }
    }
    suspend fun saveDraft(draft: JournalDraft): JournalDraft = db.withTransaction {
        verifyGeneration()
        val current = dao.draft(draft.key)
        if (current != null && (current.entryId != draft.entryId || current.revision != draft.revision)) throw JournalConflict()
        if (current == draft) return@withTransaction current
        draft.copy(revision = draft.revision + 1).also { dao.putDraft(it) }
    }
    suspend fun submit(draft: JournalDraft, now: Long = System.currentTimeMillis()): JournalEntry = db.withTransaction {
        verifyGeneration()
        // A retry after commit returns the same row without consuming a newer composer.
        dao.entry(draft.entryId)?.let { return@withTransaction it }
        require(draft.hasContent) { "Write something or add an image" }
        val current = dao.draft(draft.key) ?: throw JournalConflict()
        if (current != draft) throw JournalConflict()
        val entry = JournalEntry(id = draft.entryId, day = draft.day, zone = draft.zone,
            createdAt = draft.creationTime(now), updatedAt = now, text = draft.text, image = draft.image)
        dao.insert(entry)
        dao.removeDraft(draft.key)
        if (draft.key == "composer") dao.putDraft(JournalDraft(language = draft.language, prompt = (draft.prompt + 1 + kotlin.random.Random.nextInt(3)) % 4))
        entry
    }
    suspend fun speech(draft: JournalDraft, session: String, segment: Int, committed: String, provisional: String, finished: Boolean, review: Boolean, reason: String? = null, segmentFinal: String = committed, segmentRecovery: String = provisional, segmentSettled: Boolean = finished): JournalEntry? = db.withTransaction {
        verifyGeneration()
        val text = listOf(committed, provisional).filter { it.isNotBlank() }.joinToString(" ")
        if (text.isBlank() && draft.image == null) return@withTransaction null
        val current = dao.entry(draft.entryId)
        val entry = (current ?: JournalEntry(id = draft.entryId, day = draft.day, zone = draft.zone, createdAt = draft.creationTime(), image = draft.image)).copy(
            text = text, updatedAt = System.currentTimeMillis(), revision = (current?.revision ?: -1) + 1,
            needsReview = if (!finished || review) reason ?: "Interrupted or unconfirmed dictation" else null)
        if (current == null) dao.insert(entry) else {
            if (current.deletedAt != null) throw JournalConflict()
            dao.update(entry)
        }
        dao.segment(JournalSegment(session, segment, entry.id, segmentFinal, segmentRecovery, segmentSettled))
        if (finished && dao.draft()?.entryId == draft.entryId) {
            dao.putDraft(JournalDraft(language = draft.language, prompt = (draft.prompt + 1) % 4))
        }
        entry
    }
    suspend fun saveEdit(entry: JournalEntry, text: String, image: String?, createdAt: Long = entry.createdAt, day: Long = entry.day): JournalEntry = db.withTransaction {
        verifyGeneration()
        require(text.isNotBlank() || image != null)
        val current = dao.entry(entry.id) ?: throw JournalConflict()
        if (current.revision != entry.revision || current.deletedAt != null) throw JournalConflict()
        if (current.text != text || current.image != image || current.createdAt != createdAt || current.day != day) {
            dao.update(current.copy(text = text, image = image, createdAt = createdAt, day = day, updatedAt = System.currentTimeMillis(), revision = current.revision + 1))
        }
        dao.removeDraft("edit:${entry.id}")
        dao.entry(entry.id)!!
    }
    suspend fun delete(entry: JournalEntry, now: Long = System.currentTimeMillis()): JournalEntry = db.withTransaction {
        verifyGeneration()
        val current = dao.entry(entry.id) ?: throw JournalConflict()
        // A repeated swipe callback for the exact successful deletion is harmless.
        // Any edit, restore or replacement still changes the revision/content and is rejected.
        if (entry.deletedAt == null && current.deletedAt != null &&
            current == entry.copy(deletedAt = current.deletedAt, revision = entry.revision + 1)) return@withTransaction current
        if (entry.deletedAt != null || dao.setDeleted(entry.id, entry.revision, now) != 1) throw JournalConflict()
        dao.entry(entry.id)!!
    }
    suspend fun restore(entry: JournalEntry): JournalEntry = db.withTransaction {
        verifyGeneration()
        if (entry.deletedAt == null || dao.setDeleted(entry.id, entry.revision, null) != 1) throw JournalConflict()
        dao.entry(entry.id)!!
    }
}

/** An unset time means the current local time on the composer’s selected calendar day. */
internal fun JournalDraft.creationTime(now: Long = System.currentTimeMillis()): Long = createdAt
    ?: java.time.Instant.ofEpochMilli(now).atZone(ZoneId.of(zone)).let {
        LocalDate.ofEpochDay(day).atTime(it.toLocalTime()).atZone(ZoneId.of(zone)).toInstant().toEpochMilli()
    }
