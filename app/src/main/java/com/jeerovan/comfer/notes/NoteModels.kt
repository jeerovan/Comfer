package com.jeerovan.comfer.notes

import kotlinx.serialization.Serializable
import java.util.UUID

// Legacy item-list fields remain decodable for old backups; the editor uses mixed plain text.
@Serializable data class NoteItem(val id: String = UUID.randomUUID().toString(), val text: String = "", val checked: Boolean = false)
@Serializable data class NoteContent(val title: String = "", val text: String = "", val checklist: Boolean = false, val items: List<NoteItem> = emptyList()) {
    val meaningful: Boolean get() = title.isNotBlank() || text.isNotBlank() || items.any { it.text.isNotBlank() }
    val preview: String get() = title.ifBlank { searchableText.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty() }.take(160)
    val searchableText: String get() = if (checklist) items.joinToString("\n") { it.text } else text
    fun asText() = if (!checklist) this else copy(checklist = false, text = listOf(text, items.joinToString("\n") { "[${if (it.checked) "x" else " "}] ${it.text}" }).filter { it.isNotEmpty() }.joinToString("\n"), items = emptyList())
    fun validate() {
        require(items.map { it.id }.toSet().size == items.size) { "Duplicate checklist item" }
        require(items.all { it.id.isNotBlank() })
    }
}
@Serializable data class Note(
    val id: String = UUID.randomUUID().toString(), val content: NoteContent = NoteContent(),
    // Legacy storage membership only; there is no notebook/Inbox UI.
    val notebook: String = INBOX, val tags: Set<String> = emptySet(),
    val createdAt: Long = System.currentTimeMillis(), val updatedAt: Long = createdAt,
    val order: Long = createdAt, val pinned: Boolean = false, val archived: Boolean = false,
    val deletedAt: Long? = null, val protected: Boolean = false, val revision: Long = 0,
    val color: String = "default",
) { companion object { const val INBOX = "inbox" } }
@Serializable data class NoteDraft(
    val id: String = UUID.randomUUID().toString(), val note: Note = Note(),
    val baseRevision: Long = -1, val revision: Long = 0, val selectionStart: Int = 0, val selectionEnd: Int = 0,
)
@Serializable data class NoteLabel(val id: String = UUID.randomUUID().toString(), val name: String, val tag: Boolean = false)
@Serializable data class NotesPreferences(
    val grid: Boolean = false, val sort: String = "updated", val notebook: String? = null,
    val showCompleted: Boolean = true, val scrollIndex: Int = 0, val scrollOffset: Int = 0,
    val moduleLocked: Boolean = false,
)
@Serializable data class NotesSnapshot(val version: Int = 1, val notes: List<Note> = emptyList(), val drafts: List<NoteDraft> = emptyList(),
    val labels: List<NoteLabel> = listOf(NoteLabel(Note.INBOX, "Inbox")), val preferences: NotesPreferences = NotesPreferences()) {
    fun validate() {
        require(version == 1) { "Unsupported Notes version" }
        require(notes.map { it.id }.toSet().size == notes.size && drafts.map { it.id }.toSet().size == drafts.size)
        require(labels.map { it.id }.toSet().size == labels.size && labels.any { it.id == Note.INBOX && !it.tag })
        val notebooks = labels.filterNot { it.tag }.map { it.id }.toSet()
        val tags = labels.filter { it.tag }.map { it.id }.toSet()
        (notes + drafts.map { it.note }).forEach {
            require(it.id.isNotBlank() && it.revision >= 0 && it.notebook in notebooks && tags.containsAll(it.tags))
            it.content.validate()
        }
        require(drafts.all { it.id.isNotBlank() && it.baseRevision >= -1 && it.revision >= 0 })
        require(labels.all { it.name.isNotBlank() })
    }
}
class NotesConflict : IllegalStateException("This note changed elsewhere. Your draft is retained; reopen or save a copy.")
class NotesLocked : IllegalStateException("Unlock Notes to continue")
