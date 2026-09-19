package com.jeerovan.comfer.notes

import androidx.room.withTransaction
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

/** All mutations verify revisions inside the same Room transaction as their write. */
class NotesStore(val db: NotesDatabase, private val cipher: NotesCipher = KeystoreNotesCipher()) {
    val dao = db.dao()
    private val json = Json { encodeDefaults = true }
    private var generation: Long? = null
    private fun identity(kind: String, id: String, revision: Long = 0) = "Comfer Notes 1:$kind:$id:$revision"
    private inline fun <reified T> seal(value: T, kind: String, id: String, revision: Long = 0, protected: Boolean = false):ByteArray {
        val bytes=json.encodeToString(value).toByteArray(Charsets.UTF_8)
        require(bytes.size<=1_000_000) { "This build supports up to 1 MB per note or draft, including formatting and images. Your input is retained here; copy your text before shortening it." }
        return cipher.seal(bytes, identity(kind,id,revision),protected)
    }
    private inline fun <reified T> open(payload: ByteArray, kind: String, id: String, revision: Long = 0, protected: Boolean = false): T =
        json.decodeFromString(cipher.open(payload, identity(kind, id, revision), protected).toString(Charsets.UTF_8))
    fun decode(row: NoteRow): Note = open<Note>(row.payload, "note", row.id, row.revision, row.protected).also {
        check(it.id == row.id && it.revision == row.revision && it.notebook == row.notebook && it.deletedAt == row.deletedAt)
    }
    private fun decode(row: NoteDraftRow): NoteDraft = open<NoteDraft>(row.payload, "draft", row.id, row.revision, row.protected).also {
        check(it.id == row.id && it.revision == row.revision && it.note.id == row.noteId)
    }
    private suspend fun verify() {
        val state = dao.state()
        cipher.selectGeneration(state?.keyGeneration.orEmpty())
        if (state?.moduleLocked == true) NotesSession.requireUnlocked()
        val current = state?.generation ?: 0
        if (generation != null && generation != current) throw NotesConflict()
        generation = current
    }
    suspend fun initialize() = db.withTransaction {
        if (dao.state() == null) {
            dao.putState(NotesStateRow(payload = seal(NotesPreferences(), "settings", "1")))
            putLabel(NoteLabel(Note.INBOX, "Inbox"))
        }
        verify()
    }
    /** Re-encrypt old authenticated-key payloads without changing IDs, revisions or content. */
    suspend fun upgradeProtectedContent() = db.withTransaction {
        verify()
        var offset = 0
        while (true) {
            val rows = dao.page(offset = offset)
            if (rows.isEmpty()) break
            rows.filter { cipher.needsSessionUpgrade(it.payload, it.protected) }.forEach { row ->
                dao.put(row.copy(payload = seal(decode(row), "note", row.id, row.revision, true)))
            }
            offset += rows.size
        }
        dao.drafts().filter { cipher.needsSessionUpgrade(it.payload, it.protected) }.forEach { row ->
            dao.putDraft(row.copy(payload = seal(decode(row), "draft", row.id, row.revision, true)))
        }
    }
    suspend fun preferences(): NotesPreferences {
        val state = dao.state() ?: return NotesPreferences()
        if (state.moduleLocked) NotesSession.requireUnlocked()
        return open<NotesPreferences>(state.payload, "settings", "1").also { check(it.moduleLocked == state.moduleLocked) }
    }
    suspend fun preferences(value: NotesPreferences) = db.withTransaction {
        verify()
        if (value.moduleLocked) NotesSession.requireUnlocked()
        val previous = preferences()
        val snapshot = if (previous.moduleLocked != value.moduleLocked) snapshot() else null
        dao.putState(NotesStateRow(payload = seal(value, "settings", "1"), generation = dao.state()?.generation ?: 0, moduleLocked = value.moduleLocked, keyGeneration=dao.state()?.keyGeneration.orEmpty()))
        snapshot?.notes?.forEach { writeNote(it) }
        snapshot?.drafts?.forEach { writeDraft(it) }
    }
    suspend fun labels(): List<NoteLabel> { verify(); return dao.labels().map { open(it.payload, "label", it.id) } }
    private suspend fun putLabel(value: NoteLabel) = dao.putLabel(NoteLabelRow(value.id, seal(value, "label", value.id)))
    suspend fun saveLabel(value: NoteLabel) = db.withTransaction {
        verify(); require(value.name.isNotBlank()) { "Enter a name" }
        val existing = labels().firstOrNull { it.id == value.id }
        require(existing == null || existing.tag == value.tag)
        require(value.id != Note.INBOX || !value.tag)
        putLabel(value.copy(name = value.name.trim()))
    }
    suspend fun note(id: String): Note? { verify(); return dao.note(id)?.let(::decode) }
    suspend fun drafts(): List<NoteDraft> { verify(); return dao.drafts().filter { !it.protected || NotesSession.unlocked() }.map(::decode) }
    suspend fun newDraft(content: NoteContent = NoteContent(), protected:Boolean=false): NoteDraft = db.withTransaction {
        verify()
        val draft = NoteDraft(note = Note(content = content, protected=protected))
        writeDraft(draft); draft
    }
    suspend fun beginEdit(note: Note): NoteDraft = db.withTransaction {
        verify(); val current = dao.note(note.id)?.let(::decode) ?: throw NotesConflict()
        if (current.revision != note.revision || current.deletedAt != null) throw NotesConflict()
        dao.drafts().firstOrNull { it.noteId == note.id }?.let(::decode)
            ?: NoteDraft(note = current, baseRevision = current.revision).also { writeDraft(it) }
    }
    private suspend fun writeDraft(draft: NoteDraft) {
        val protected = draft.note.protected || dao.state()?.moduleLocked == true
        dao.putDraft(NoteDraftRow(draft.id, draft.note.id, seal(draft, "draft", draft.id, draft.revision, protected), protected, draft.revision))
    }
    private suspend fun writeNote(note: Note) {
        note.content.validate()
        val labels = labels()
        require(labels.any { it.id == note.notebook && !it.tag }) { "Notebook no longer exists" }
        require(note.tags.all { id -> labels.any { it.id == id && it.tag } }) { "Label no longer exists" }
        val protected = note.protected || dao.state()?.moduleLocked == true
        dao.put(NoteRow(note.id, seal(note, "note", note.id, note.revision, protected), protected, note.revision, note.notebook, note.deletedAt))
    }
    /** First persist the working draft. A stale note never prevents recovering the user's text. */
    suspend fun saveDraft(draft: NoteDraft): NoteDraft = db.withTransaction {
        verify()
        val current = dao.draft(draft.id) ?: throw NotesConflict()
        if (current.revision != draft.revision) throw NotesConflict()
        draft.note.content.validate()
        draft.copy(revision = draft.revision + 1).also { writeDraft(it) }
    }
    /** Keep the draft as the durable editor session, advanced to the committed note revision. */
    suspend fun commit(draft: NoteDraft, now: Long = System.currentTimeMillis()): NoteDraft = db.withTransaction {
        verify()
        val durable = dao.draft(draft.id)?.let(::decode) ?: throw NotesConflict()
        if (durable != draft) throw NotesConflict()
        val existing = dao.note(draft.note.id)?.let(::decode)
        if ((existing?.revision ?: -1L) != draft.baseRevision || existing?.deletedAt != null) throw NotesConflict()
        if (!draft.note.content.meaningful && existing == null) return@withTransaction draft
        val committed = draft.note.copy(revision = draft.baseRevision + 1, updatedAt = now)
        writeNote(committed)
        draft.copy(note = committed, baseRevision = committed.revision, revision = draft.revision + 1).also { writeDraft(it) }
    }
    suspend fun finishDraft(draft: NoteDraft) = db.withTransaction {
        verify(); val stored = dao.draft(draft.id)?.let(::decode) ?: return@withTransaction
        if (stored != draft) throw NotesConflict()
        val note = dao.note(draft.note.id)?.let(::decode)
        require((note == draft.note && draft.baseRevision == note.revision) || (note == null && !draft.note.content.meaningful)) { "Keep or save this draft before leaving" }
        dao.deleteDraft(draft.id)
    }
    suspend fun mutate(id: String, expected: Long, change: (Note) -> Note): Note = db.withTransaction {
        verify(); val current = dao.note(id)?.let(::decode) ?: throw NotesConflict()
        if (current.revision != expected) throw NotesConflict()
        change(current).also { require(it.id == id) }.copy(revision = expected + 1, updatedAt = System.currentTimeMillis()).also { writeNote(it) }
    }
    suspend fun batch(notes: List<Note>, change: (Note) -> Note): List<Note> = db.withTransaction {
        verify(); notes.map { mutate(it.id, it.revision, change) }
    }
    suspend fun undo(before: List<Note>, after: List<Note>) = db.withTransaction {
        require(before.size == after.size)
        before.zip(after).forEach { (old, updated) -> mutate(updated.id, updated.revision) { old } }
    }
    suspend fun removeLabel(id: String, destination: String = Note.INBOX) = db.withTransaction {
        verify(); require(id != Note.INBOX && id != destination)
        val label = labels().first { it.id == id }
        var offset = 0
        while (true) {
            val rows = dao.page(offset = offset); if (rows.isEmpty()) break
            rows.forEach { row ->
                val note = decode(row)
                if (note.notebook == id || id in note.tags) writeNote(note.copy(notebook = if(note.notebook == id) destination else note.notebook, tags = note.tags - id, revision = note.revision + 1))
            }
            offset += rows.size
        }
        dao.drafts().forEach { row ->
            val draft = decode(row)
            if (draft.note.notebook == id || id in draft.note.tags) writeDraft(draft.copy(note = draft.note.copy(notebook = if(draft.note.notebook == id) destination else draft.note.notebook, tags = draft.note.tags - id), revision = draft.revision + 1))
        }
        if (!label.tag) require(labels().any { it.id == destination && !it.tag })
        dao.deleteLabel(id)
        if(preferences().notebook==id)preferences(preferences().copy(notebook=null))
    }
    /** Older builds allowed item locks. Preserve protection while adopting one module switch. */
    suspend fun normalizeLegacyProtection() = db.withTransaction {
        verify()
        val snapshot=snapshot()
        if(snapshot.notes.none{it.protected} && snapshot.drafts.none{it.note.protected}) return@withTransaction
        NotesSession.requireUnlocked()
        val prefs=preferences().copy(moduleLocked=true)
        val state=dao.state()!!
        dao.putState(state.copy(payload=seal(prefs,"settings","1"),moduleLocked=true))
        snapshot.notes.forEach{writeNote(it.copy(protected=false))}
        snapshot.drafts.forEach{writeDraft(it.copy(note=it.note.copy(protected=false)))}
    }
    suspend fun cleanup(now: Long = System.currentTimeMillis()) = db.withTransaction {
        verify()
        val active = dao.drafts().map { it.noteId }.toSet()
        var offset = 0
        val expired = mutableListOf<NoteRow>()
        while(true) {
            val rows = dao.page(offset=offset); if(rows.isEmpty()) break
            expired += rows.filter { it.deletedAt != null && it.deletedAt <= now - 7L*24*60*60*1000 && it.id !in active && (!it.protected || NotesSession.unlocked()) }
            offset += rows.size
        }
        expired.forEach { decode(it); dao.delete(it.id) }
    }
    suspend fun reorder(notes: List<Note>) = db.withTransaction {
        require(notes.map { it.id }.distinct().size == notes.size)
        notes.forEachIndexed { index, note -> mutate(note.id, note.revision) { it.copy(order=index.toLong()) } }
    }
    suspend fun purge(note: Note) = db.withTransaction {
        verify(); val current = dao.note(note.id)?.let(::decode) ?: return@withTransaction
        if (current.revision != note.revision) throw NotesConflict()
        require(current.deletedAt != null)
        require(dao.drafts().none { it.noteId == note.id }) { "Resolve the recovered draft before permanent deletion" }
        dao.delete(note.id)
    }
    suspend fun snapshot(): NotesSnapshot = db.withTransaction {
        verify(); val notes = mutableListOf<Note>(); var offset = 0
        while (true) { val rows = dao.page(offset = offset); if(rows.isEmpty()) break; notes += rows.map(::decode); offset += rows.size }
        NotesSnapshot(notes = notes, drafts = dao.drafts().map(::decode), labels = labels(), preferences = preferences())
    }
    suspend fun replace(snapshot: NotesSnapshot, rotateKey:Boolean=false) = db.withTransaction {
        verify(); snapshot.validate()
        if(snapshot.preferences.moduleLocked || snapshot.notes.any { it.protected } || snapshot.drafts.any { it.note.protected }) NotesSession.requireUnlocked()
        val keyGeneration=if(rotateKey)java.util.UUID.randomUUID().toString() else dao.state()?.keyGeneration.orEmpty()
        cipher.selectGeneration(keyGeneration)
        dao.clearNotes(); dao.clearDrafts(); dao.clearLabels()
        dao.putState(NotesStateRow(payload = seal(snapshot.preferences, "settings", "1"), generation = dao.state()?.generation ?: 0, moduleLocked = snapshot.preferences.moduleLocked, keyGeneration=keyGeneration))
        snapshot.labels.forEach { putLabel(it) }
        snapshot.notes.forEach { writeNote(it) }; snapshot.drafts.forEach { writeDraft(it) }
        val next = (dao.state()?.generation ?: 0) + 1
        dao.putState(NotesStateRow(payload = seal(snapshot.preferences, "settings", "1"), generation = next, moduleLocked = snapshot.preferences.moduleLocked, keyGeneration=keyGeneration))
        // Existing editor stores deliberately remain stale after a replacement.
    }
}
