package com.jeerovan.comfer.journals

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Serial mailbox orders keystrokes, picker results and submit; configuration changes keep ownership. */
class JournalViewModel(application: Application, private val savedState: androidx.lifecycle.SavedStateHandle = androidx.lifecycle.SavedStateHandle()) : AndroidViewModel(application) {
    var pendingImageTarget: String?
        get() = savedState["imageTarget"]
        private set(value) { savedState["imageTarget"] = value }
    var pendingImageRevision: Long?
        get() = savedState["imageRevision"]
        private set(value) { savedState["imageRevision"] = value }
    fun prepareImagePicker(entry: JournalEntry?) { pendingImageTarget = entry?.id; pendingImageRevision = entry?.revision }
    val store = JournalStore(JournalDatabase.get(application))
    val media = JournalMedia(application)
    private val commands = Channel<suspend () -> Unit>(Channel.UNLIMITED)
    private val mutableDraft = MutableStateFlow<JournalDraft?>(null)
    val draft = mutableDraft.asStateFlow()
    val error = MutableStateFlow<String?>(null)
    val busy = MutableStateFlow(false)
    val draftSaving = MutableStateFlow(false)
    private var draftWrites = 0
    val captureState = MutableStateFlow(JournalSpeech.State.IDLE)
    val audioLevel = MutableStateFlow<Float?>(null)
    val liveText = MutableStateFlow("")
    val provisionalText = MutableStateFlow("")
    val committedText = MutableStateFlow("")
    val liveEntryId = MutableStateFlow<String?>(null)
    private var captureDraft: JournalDraft? = null
    private var lastCaptureSnapshot = ""
    private var pendingSpeech: (suspend () -> Unit)? = null
    val recoveryPending = MutableStateFlow(false)
    fun retryRecovery() = enqueue {
        val operation = pendingSpeech ?: return@enqueue
        operation()
        if (pendingSpeech === operation) { pendingSpeech = null; recoveryPending.value = false }
    }
    internal val speech = JournalSpeechController(application, update = { protocol, level ->
        captureState.value = if (protocol.state == JournalSpeech.State.FINISHED && captureDraft != null) JournalSpeech.State.FINALIZING else protocol.state
        audioLevel.value = level
        liveText.value = protocol.text
        provisionalText.value = protocol.provisional
        committedText.value = protocol.committed
        val captured = captureDraft
        val signature = "${protocol.session}:${protocol.segment}:${protocol.committed}:${protocol.provisional}:${protocol.state}"
        if (captured != null && signature != lastCaptureSnapshot) {
            lastCaptureSnapshot = signature
            val session = protocol.session; val segment = protocol.segment
            val committed = protocol.committed; val provisional = protocol.provisional
            val finished = protocol.state == JournalSpeech.State.FINISHED
            val review = protocol.needsReview
            val reason = protocol.reviewReason
            val hasSpeechResults = protocol.hasSpeechResults
            val segmentFinal = protocol.segmentFinal; val segmentRecovery = protocol.segmentRecovery; val segmentSettled = protocol.segmentConfirmed
            val operation: suspend () -> Unit = {
                if (hasSpeechResults) store.speech(captured, session, segment, committed, provisional, finished, review, reason, segmentFinal, segmentRecovery, segmentSettled)
                if (finished) {
                    mutableDraft.value = store.ensureDraft(); captureDraft = null; liveEntryId.value = null
                    captureState.value = JournalSpeech.State.FINISHED
                    if (reason != null) error.value = reason
                }
            }
            pendingSpeech = operation
            enqueue {
                operation()
                if (pendingSpeech === operation) { pendingSpeech = null; recoveryPending.value = false }
            }
        }
    })
    fun startSpeech(language: String, networkConsent: Boolean) = enqueue {
        if (editing.value != null || speech.active || busy.value) return@enqueue
        val current = mutableDraft.value ?: store.ensureDraft()
        captureDraft = store.saveDraft(current.copy(language = language))
        mutableDraft.value = captureDraft
        liveEntryId.value = captureDraft?.entryId
        speech.start(java.util.UUID.randomUUID().toString(), captureDraft!!.text, language, networkConsent)
    }
    override fun onCleared() { speech.interrupt(); super.onCleared() }
    val editing = MutableStateFlow<JournalEntry?>(null)
    val editDraft = MutableStateFlow<JournalDraft?>(null)
    init {
        viewModelScope.launch {
            try { com.jeerovan.comfer.StartupCoordinator.awaitReady(); media.collect(store.db); mutableDraft.value = store.ensureDraft().let { store.saveDraft(it.copy(createdAt = null, prompt = (it.prompt + 1 + kotlin.random.Random.nextInt(3)) % 4)) } } catch (_: Exception) { error.value = "Journal could not be opened. Please reopen it." }
            for (command in commands) try { command() } catch (e: Exception) {
                error.value = if (e is JournalConflict) e.message else "Could not save. Your draft is still here; try again."
                busy.value = false
                recoveryPending.value = pendingSpeech != null
            }
        }
    }
    private fun enqueue(block: suspend () -> Unit) { commands.trySend(block) }
    private fun enqueueDraft(block: suspend () -> Unit) {
        draftWrites++; draftSaving.value = true
        enqueue { try { block() } finally { draftWrites--; draftSaving.value = draftWrites > 0 } }
    }
    fun closeWhenSaved(close: () -> Unit) = enqueue {
        mutableDraft.value?.let { mutableDraft.value = store.saveDraft(it) }
        editDraft.value?.let { editDraft.value = store.saveDraft(it) }
        close()
    }
    fun change(text: String? = null, image: String? = null, changeImage: Boolean = false, day: Long? = null) = enqueueDraft {
        val current = mutableDraft.value ?: store.ensureDraft()
        val next = current.copy(text = text ?: current.text, image = if (changeImage) image else current.image,
            day = day ?: current.day, createdAt = if (day != null && day != current.day) null else current.createdAt)
        mutableDraft.value = next
        mutableDraft.value = store.saveDraft(next)
    }
    fun submit(onSaved: (JournalEntry) -> Unit) {
        if (busy.value) return
        busy.value = true
        enqueue {
            val current = mutableDraft.value ?: store.ensureDraft()
            val savedDraft = store.saveDraft(current)
            mutableDraft.value = savedDraft
            val entry = store.submit(savedDraft)
            mutableDraft.value = store.ensureDraft()
            busy.value = false
            onSaved(entry)
        }
    }
    fun beginEdit(entry: JournalEntry) = enqueue {
        val buffer = store.beginEdit(entry)
        editing.value = entry
        editDraft.value = buffer
    }
    fun changeEdit(text: String? = null, image: String? = null, changeImage: Boolean = false, createdAt: Long? = null) = enqueueDraft {
        val current = editDraft.value ?: return@enqueueDraft
        val next = current.copy(text = text ?: current.text, image = if (changeImage) image else current.image, createdAt = createdAt ?: current.createdAt, day = if (createdAt == null) current.day else java.time.Instant.ofEpochMilli(createdAt).atZone(java.time.ZoneId.of(current.zone)).toLocalDate().toEpochDay())
        editDraft.value = next
        editDraft.value = store.saveDraft(next)
    }
    fun finishEdit(save: Boolean, next: JournalEntry? = null) = enqueue {
        val entry = editing.value ?: return@enqueue
        val draft = editDraft.value ?: return@enqueue
        if (save) store.saveEdit(entry.copy(revision = draft.baseRevision), draft.text, draft.image, draft.createdAt ?: entry.createdAt, draft.day)
        else store.discardDraft(draft.key)
        editing.value = null
        editDraft.value = null
        if (next != null) {
            val buffer = store.beginEdit(next)
            editing.value = next; editDraft.value = buffer
        }
    }
    fun delete(entry: JournalEntry, done: (JournalEntry) -> Unit) = enqueue { done(store.delete(entry)) }
    fun restore(entry: JournalEntry) = enqueue { store.restore(entry) }
}
