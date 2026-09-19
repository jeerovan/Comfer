package com.jeerovan.comfer.journals

import android.util.Base64
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

/** Only encrypted content reaches Room. Clear columns are limited to lookup/order metadata.
 * The Room interface is kept separate so recovery can replay ciphertext without unlocking it. */
class JournalDao internal constructor(private val db: JournalDatabase) {
    private val raw get() = db.rawDao()
    private val json = Json { encodeDefaults = true }
    private fun identity(kind: String, id: String) = "Comfer Journal $kind 1:$id"
    private inline fun <reified T> seal(value: T, kind: String, id: String, locked: Boolean): String =
        Base64.encodeToString(db.cipher.seal(json.encodeToString(value).toByteArray(), identity(kind, id), locked), Base64.NO_WRAP)
    private inline fun <reified T> open(payload: String, kind: String, id: String): T =
        json.decodeFromString(db.cipher.open(Base64.decode(payload, Base64.NO_WRAP), identity(kind, id)).toString(Charsets.UTF_8))
    private fun encode(e: JournalEntry, locked: Boolean) = e.copy(text = seal(e, "entry", e.id, locked), zone = "", needsReview = null)
    private fun encode(d: JournalDraft, locked: Boolean) = d.copy(text = seal(d, "draft", d.key, locked), zone = "", language = "")
    private fun encode(s: JournalSegment, locked: Boolean) = s.copy(finalized = seal(s, "segment", "${s.sessionId}:${s.segmentId}", locked), provisional = "")
    private fun decode(e: JournalEntry): JournalEntry = open<JournalEntry>(e.text, "entry", e.id).also {
        check(it.id == e.id && it.revision == e.revision && it.day == e.day && it.createdAt == e.createdAt && it.deletedAt == e.deletedAt && it.image == e.image) { "Journal entry metadata is damaged" }
    }
    private fun decode(d: JournalDraft): JournalDraft = open<JournalDraft>(d.text, "draft", d.key).also {
        check(it.key == d.key && it.entryId == d.entryId && it.revision == d.revision && it.image == d.image) { "Journal draft metadata is damaged" }
    }
    private fun decode(s: JournalSegment): JournalSegment = open<JournalSegment>(s.finalized, "segment", "${s.sessionId}:${s.segmentId}").also {
        check(it.entryId == s.entryId) { "Journal dictation metadata is damaged" }
    }
    internal suspend fun initialize(): JournalState = db.withTransaction {
        val state = raw.storageState() ?: JournalState(storageVersion = 1).also { raw.state(it) }
        check(state.storageVersion == 1) { "Unsupported Journal storage version" }
        if (state.moduleLocked) JournalProtection.requireAuthorization()
        state
    }
    /** Compatible cipher upgrade; atomic image replacement preserves IDs and old checkpoints. */
    internal suspend fun upgradeProtectedContent() = db.withTransaction {
        if (!initialize().moduleLocked) return@withTransaction
        fun old(payload: String) = db.cipher.needsSessionUpgrade(Base64.decode(payload, Base64.NO_WRAP))
        raw.exportEntries().filter { old(it.text) }.forEach { raw.update(encode(decode(it), true)) }
        raw.exportDrafts().filter { old(it.text) }.forEach { raw.putDraft(encode(decode(it), true)) }
        raw.allSegments().filter { old(it.finalized) }.forEach { raw.segment(encode(decode(it), true)) }
        db.storageContext?.let { context ->
            val media = JournalMedia(context)
            raw.referencedImages().forEach { id ->
                val file = media.file(id)
                require(file.length() in 1..2_000_090) { "A Journal image is unavailable" }
                val header = file.inputStream().use { input -> ByteArray(2).also { input.read(it) } }
                if (db.cipher.needsSessionUpgrade(header)) media.write(id, media.read(id), true)
            }
        }
    }
    private suspend fun rewrite(state: JournalState, locked: Boolean, created: MutableList<String> = mutableListOf()): JournalState {
        val entries = raw.exportEntries().map(::decode)
        val drafts = raw.exportDrafts().map(::decode)
        val segments = raw.allSegments().map(::decode)
        val media = db.storageContext?.let(::JournalMedia)
        val ids = (entries.mapNotNull { it.image } + drafts.mapNotNull { it.image }).distinct()
        // New immutable IDs preserve original files if conversion or the transaction fails.
        try {
            val remap = ids.associateWith { id -> media?.copyEncrypted(id, locked)?.also { created += it } ?: id }
            entries.forEach { raw.update(encode(it.copy(image = it.image?.let(remap::getValue)), locked)) }
            drafts.forEach { raw.putDraft(encode(it.copy(image = it.image?.let(remap::getValue)), locked)) }
            segments.forEach { raw.segment(encode(it, locked)) }
            return state.copy(storageVersion = 1, moduleLocked = locked, cleanupPending = true).also { raw.state(it) }
        } catch (error: Exception) {
            created.forEach { media?.file(it)?.delete() }
            throw error
        }
    }
    internal suspend fun changeProtection(locked: Boolean) {
        val created = mutableListOf<String>()
        try {
            db.withTransaction {
                val state = initialize()
                if (locked || state.moduleLocked) JournalProtection.requireAuthorization()
                if (state.moduleLocked != locked) rewrite(state, locked, created)
            }
        } catch (error: Exception) {
            db.storageContext?.let(::JournalMedia)?.let { media -> created.forEach { media.file(it).delete() } }
            throw error
        }
    }
    private suspend fun <T> access(block: suspend (Boolean) -> T): T = db.withTransaction { block(initialize().moduleLocked) }
    private fun <T> observe(source: () -> Flow<T>): Flow<T> = flow { initialize(); emitAll(source()) }.flowOn(Dispatchers.IO)
    suspend fun generation(): Long = access { raw.generation() }
    suspend fun state(state: JournalState) = access { raw.state(state.copy(storageVersion = 1, moduleLocked = it, cleanupPending = raw.storageState()?.cleanupPending == true)) }
    suspend fun exportEntries(): List<JournalEntry> = access { raw.exportEntries().map(::decode) }
    suspend fun exportDrafts(): List<JournalDraft> = access { raw.exportDrafts().map(::decode) }
    suspend fun exportSegments(): List<JournalSegment> = access { raw.exportSegments().map(::decode) }
    suspend fun clearEntries() = access { raw.clearEntries() }
    suspend fun clearDrafts() = access { raw.clearDrafts() }
    suspend fun clearSegments() = access { raw.clearSegments() }
    suspend fun referencedImages(): List<String> = access { raw.referencedImages() }
    suspend fun removeOrphanSegments() = access { raw.removeOrphanSegments() }
    suspend fun page(day: Long, limit: Int = 50, offset: Int = 0): List<JournalEntry> = access { raw.page(day, limit, offset).map(::decode) }
    fun observeDay(day: Long, limit: Int, offset: Int = 0): Flow<List<JournalEntry>> = observe { raw.observeDay(day, limit, offset).map { rows -> access { rows.map(::decode) } } }
    fun search(query: String, limit: Int, offset: Int = 0): Flow<List<JournalEntry>> = observe {
        // Search plaintext only in memory; never add a plaintext index to encrypted storage.
        raw.observeDay(Long.MAX_VALUE, Int.MAX_VALUE).map { rows -> access {
            rows.asReversed().asSequence().map(::decode).filter { it.text.contains(query, ignoreCase = true) }
                .drop(offset).take(limit).toList().asReversed()
        } }
    }
    suspend fun previousDay(day: Long): Long? = access { raw.previousDay(day) }
    suspend fun nextDay(day: Long): Long? = access { raw.nextDay(day) }
    suspend fun entry(id: String): JournalEntry? = access { raw.entry(id)?.let(::decode) }
    suspend fun draft(key: String = "composer"): JournalDraft? = access { raw.draft(key)?.let(::decode) }
    fun observeDraft(): Flow<JournalDraft?> = observe { raw.observeDraft().map { row -> access { row?.let(::decode) } } }
    suspend fun insert(entry: JournalEntry) = access { raw.insert(encode(entry, it)) }
    suspend fun update(entry: JournalEntry) = access { raw.update(encode(entry, it)) }
    suspend fun putDraft(draft: JournalDraft) = access { raw.putDraft(encode(draft, it)) }
    suspend fun removeDraft(key: String) = access { raw.removeDraft(key) }
    suspend fun edit(id: String, expected: Long, text: String, image: String?, now: Long): Int = access { locked ->
        val entry = raw.entry(id)?.let(::decode)
        if (entry == null || entry.revision != expected || entry.deletedAt != null) 0
        else { raw.update(encode(entry.copy(text = text, image = image, updatedAt = now, revision = expected + 1), locked)); 1 }
    }
    suspend fun setDeleted(id: String, expected: Long, deletedAt: Long?): Int = access { locked ->
        val entry = raw.entry(id)?.let(::decode)
        if (entry == null || entry.revision != expected) 0
        else { raw.update(encode(entry.copy(deletedAt = deletedAt, revision = expected + 1), locked)); 1 }
    }
    fun trash(cutoff: Long = System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000, limit: Int = 100, offset: Int = 0): Flow<List<JournalEntry>> = observe { raw.trash(cutoff, limit, offset).map { rows -> access { rows.map(::decode) } } }
    suspend fun expired(cutoff: Long): List<JournalEntry> = access { raw.expired(cutoff).map(::decode) }
    suspend fun trashForPurge(): List<JournalEntry> = access { raw.trashForPurge().map(::decode) }
    suspend fun deletePermanently(id: String, expected: Long): Int = access { raw.deletePermanently(id, expected) }
    suspend fun deleteSegments(id: String) = access { raw.deleteSegments(id) }
    suspend fun purge(id: String, expected: Long): Int = access { raw.purge(id, expected) }
    suspend fun segment(segment: JournalSegment) = access { raw.segment(encode(segment, it)) }
    suspend fun segments(id: String): List<JournalSegment> = access { raw.segments(id).map(::decode) }
}
