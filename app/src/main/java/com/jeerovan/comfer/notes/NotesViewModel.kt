package com.jeerovan.comfer.notes

import android.app.Application
import android.net.Uri
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import androidx.room.withTransaction
import androidx.compose.runtime.*
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class NotesViewModel(application: Application) : AndroidViewModel(application) {
    private var store = NotesStore(NotesDatabase.get(application))
    private val saving = Mutex()
    var ready by mutableStateOf(false); private set
    var collection by mutableStateOf(true)
    var draft by mutableStateOf<NoteDraft?>(null); private set
    var body by mutableStateOf(TextFieldValue()); private set
    var title by mutableStateOf(""); private set
    var findTarget by mutableStateOf<String?>(null); private set
    var findSelection by mutableStateOf(TextRange.Zero); private set
    val editorText get() = title + "\n" + body.text
    fun clearFind(){findTarget=null}
    fun selectMatch(match:NotesSearch.Match) {
        if(match.start < title.length) { findTarget="title";findSelection=TextRange(match.start,match.end.coerceAtMost(title.length));return }
        val offset=title.length+1
        findTarget="body";findSelection=TextRange((match.start-offset).coerceIn(0,body.text.length),(match.end-offset).coerceIn(0,body.text.length));body=body.copy(selection=findSelection)
    }
    var notes by mutableStateOf(emptyList<Note>()); private set
    var lockedCount by mutableIntStateOf(0); private set
    var labels by mutableStateOf(emptyList<NoteLabel>()); private set
    var prefs by mutableStateOf(NotesPreferences()); private set
    var status by mutableStateOf(""); private set
    var error by mutableStateOf<String?>(null)
    var recovery by mutableStateOf(false); private set
    var recoveredDrafts by mutableStateOf(emptyList<NoteDraft>()); private set
    var query by mutableStateOf("")
    var view by mutableStateOf("Notes")
    var selected by mutableStateOf(emptySet<String>())
    var undo by mutableStateOf<Pair<List<Note>,List<Note>>?>(null); private set
    var busy by mutableStateOf(false); private set
    private var saveJob: Job? = null
    private var observeJob: Job? = null
    private var editVersion = 0L
    private var undoJob: Job? = null
    private val history = ArrayDeque<NoteContent>()
    private val future = ArrayDeque<NoteContent>()
    private var initialized = false
    private var pendingShared: String? = null
    val sensitive get() = prefs.moduleLocked || draft?.note?.protected == true || (collection && notes.any { it.protected })
    var marks by mutableStateOf(emptyList<NoteMark>()); private set
    var images by mutableStateOf(emptyList<NoteImage>()); private set
    var canvasSelection by mutableStateOf(TextRange.Zero); private set
    var typingStyles by mutableStateOf(emptyMap<String,String>()); private set
    internal var canvasCommand by mutableStateOf<NoteCanvasCommand?>(null); private set
    private var commandSequence=0
    private var pendingImage:Uri?=null
    var importingImage by mutableStateOf(false); private set
    private fun content() = NoteContent(title, body.text,marks=marks,images=images)
    fun selectedText():String {val text=NoteFormatting.canvas(content());return text.substring(canvasSelection.min.coerceAtMost(text.length),canvasSelection.max.coerceAtMost(text.length))}
    fun command(kind:String,value:String="",text:String=""){canvasCommand=NoteCanvasCommand(++commandSequence,kind,value,text)}
    fun format(kind:String,value:String="") {
        val text=NoteFormatting.canvas(content())
        var start=canvasSelection.min.coerceAtMost(text.length);var end=canvasSelection.max.coerceAtMost(text.length)
        if(kind=="paragraph") {val range=NoteFormatting.paragraphRange(text,start,end);start=range.first;end=range.last+1}
        if(start==end){
            typingStyles=if(kind in typingStyles&&typingStyles[kind]==value)typingStyles-kind else typingStyles+(kind to value)
            return
        }
        rememberUndo();marks=NoteFormatting.apply(marks,start,end,kind,value,toggle=kind !in setOf("paragraph","color","url"));changed()
    }
    fun link(start:Int,end:Int,url:String,recordUndo:Boolean=true){if(!NoteFormatting.validUrl(url)){error="Enter a valid http or https URL";return};if(recordUndo)rememberUndo();marks=NoteFormatting.apply(marks,start,end,"url",url,false);changed()}
    fun queueImage(uri:Uri){pendingImage=uri;if(ready)consumeImage()}
    private fun consumeImage() {
        val uri=pendingImage?:return
        if(importingImage||draft==null)return
        pendingImage=null
        val id=draft?.id
        importingImage=true
        action {
            try {
                val image=withContext(Dispatchers.IO){NotesImages.read(getApplication(),uri)}
                check(draft?.id==id){"Reopen the note before adding this image"}
                if(sensitive)NotesSession.requireUnlocked()
                val before=NoteFormatting.canvas(content())
                val after=before+"\n\n"
                val placed=image.copy(offset=after.length)
                val candidate=content().copy(text=after.substringAfter('\n',""),images=images+placed)
                require(Json.encodeToString(candidate).toByteArray().size<=900_000){"This note is full. Use a smaller image or remove an existing image."}
                rememberUndo();body=TextFieldValue(candidate.text);images=candidate.images;changed();flush()
            } finally {importingImage=false}
        }
    }
    fun removeImage(id:String){rememberUndo();images=images.filterNot{it.id==id};changed()}
    private suspend fun show(value: NoteDraft) {
        draft = value
        var content=value.note.content.asText()
        if(content.images.any{it.offset==null}) {
            val canvas=NoteFormatting.canvas(content)+"\n\n"
            content=content.copy(text=canvas.substringAfter('\n',""),images=content.images.map{if(it.offset==null)it.copy(offset=canvas.length)else it})
        }
        title=content.title;marks=content.marks;images=content.images;typingStyles=emptyMap();canvasSelection=TextRange.Zero
        body=TextFieldValue(content.text,TextRange(value.selectionStart.coerceIn(0,content.text.length),value.selectionEnd.coerceIn(0,content.text.length)))
        history.clear();future.clear();findTarget=null
        val committed=withContext(Dispatchers.IO){store.note(value.note.id)}
        status = if(value.note.content.checklist) "Saving" else if(committed == value.note) "Saved" else if(value.note.content.meaningful) "Saving" else ""
        editVersion++
        if(value.note.content.checklist)flush()
    }
    private fun action(block: suspend () -> Unit) = viewModelScope.launch {
        try { block() } catch(e: CancellationException) { throw e } catch(e: Exception) { error = e.message ?: "Couldn't save. Your draft is retained." }
    }
    private val starting = Mutex()
    fun start(shared: String? = null) = action { starting.withLock {
        busy=true
        try {
            if(shared != null) pendingShared=shared
            withContext(Dispatchers.IO) { store.initialize(); store.normalizeLegacyProtection(); store.cleanup() }
            prefs=withContext(Dispatchers.IO){store.preferences()};labels=withContext(Dispatchers.IO){store.labels()}
            if(!initialized) {
                collection=true
                initialized=true
            }
            pendingShared?.let { text ->
                // Each received payload gets its own draft; existing failures remain in storage.
                val incoming=withContext(Dispatchers.IO){store.newDraft(NoteContent(text=text))}
                pendingShared=null
                if(flush()) { show(incoming);collection=false;recovery=true;flush() }
                else error="Your previous draft couldn't save and is still here. Shared text was kept as a separate recovered draft."
            }
            if(status == "Saving") flush()
            recoveredDrafts=withContext(Dispatchers.IO){store.drafts()}
            ready=true
            consumeImage()
            observeJob?.cancel()
            observeJob=viewModelScope.launch {
                val cache=mutableMapOf<String,Pair<Pair<Long,Int>,Note>>()
                store.dao.observeRows().collect { rows ->
                    try {
                        val visible=withContext(Dispatchers.IO) {
                            if(prefs.moduleLocked) NotesSession.requireUnlocked()
                            rows.filter { !it.protected || NotesSession.unlocked() }.map { row ->
                                cache[row.id]?.takeIf { it.first == (row.revision to row.payload.contentHashCode()) }?.second ?: store.decode(row).also { cache[row.id]=(row.revision to row.payload.contentHashCode()) to it }
                            }
                        }
                        cache.keys.retainAll(visible.map{it.id}.toSet());notes=visible;lockedCount=rows.size-visible.size
                    } catch(e: CancellationException) { throw e } catch(e: Exception) { notes=emptyList();cache.clear();error=e.message }
                }
            }
        } finally { busy=false }
    } }
    /** UI state is never placed in savedInstanceState. Activity removes sensitive composition on lock. */
    fun background() {
        saveJob?.cancel(); observeJob?.cancel(); notes=emptyList();ready=false;query="";selected=emptySet();undo=null;error=null;recoveredDrafts=emptyList()
        // Retain the in-memory editor buffer for authenticated retry; last durable draft survives death.
    }
    fun updateBody(value: TextFieldValue) {
        if(value.text != body.text) { rememberUndo(); body=value;changed() } else body=value
    }
    fun updateCanvas(value: TextFieldValue,listEdit:Boolean=false,segment:Int=0) {
        val heading=value.text.substringBefore('\n')
        val text=value.text.substringAfter('\n', "")
        val offset=if('\n' in value.text) heading.length+1 else heading.length
        val selection=TextRange((value.selection.start-offset).coerceIn(0,text.length),(value.selection.end-offset).coerceIn(0,text.length))
        if(heading!=title || text!=body.text) {
            rememberUndo()
            val before=NoteFormatting.canvas(content())
            val delta=NoteFormatting.change(before,value.text)
            images=NoteFormatting.rebaseImages(images,before,value.text,segment)
            marks=if(listEdit)NoteFormatting.rebaseList(marks,before,value.text)else NoteFormatting.rebase(marks,before,value.text)
            if(delta.newEnd>delta.start)typingStyles.forEach{(kind,style)->marks=NoteFormatting.apply(marks,delta.start,delta.newEnd,kind,style,false)}
            title=heading;body=TextFieldValue(text,selection);changed()
        } else {
            body=body.copy(selection=selection)
            if(canvasSelection!=value.selection)typingStyles=marks.filter{if(value.selection.collapsed)value.selection.start>it.start&&value.selection.start<=it.end else value.selection.min>=it.start&&value.selection.min<it.end}.associate{it.kind to it.value}
        }
        canvasSelection=value.selection
    }
    private fun rememberUndo() { history.addLast(content());if(history.size>50)history.removeFirst();future.clear() }
    private fun applyContent(value:NoteContent) { title=value.title;body=TextFieldValue(value.text);marks=value.marks;images=value.images;typingStyles=emptyMap();changed() }
    fun undoEdit() { if(history.isNotEmpty()){future.addLast(content());applyContent(history.removeLast())} }
    fun redoEdit() { if(future.isNotEmpty()){history.addLast(content());applyContent(future.removeLast())} }
    private fun changed() {
        editVersion++;status="Saving";saveJob?.cancel()
        saveJob=action { delay(350);flush() }
    }
    suspend fun flush(): Boolean = saving.withLock {
        val current=draft ?: return@withLock true
        val version=editVersion
        val updated=current.copy(note=current.note.copy(content=content()),selectionStart=body.selection.start,selectionEnd=body.selection.end)
        if(updated.note == current.note && current.baseRevision >= 0 && status != "Saving" && status != "Couldn't save") return@withLock true
        try {
            // Non-cancellable commit finishes atomically. UI edits arriving meanwhile stay in the buffer.
            withContext(NonCancellable + Dispatchers.IO) {
                val durable=store.saveDraft(updated)
                withContext(Dispatchers.Main.immediate) { draft=durable }
                val saved=store.commit(durable)
                withContext(Dispatchers.Main.immediate) { draft=saved }
            }
            if(version==editVersion){status=if(updated.note.content.meaningful || current.baseRevision>=0)"Saved" else "";recovery=false}
            true
        } catch(e: CancellationException) {
            // A newer edit/backgrounding may cancel the caller while the atomic write completes.
            // withContext then rethrows cancellation on return; it is not a storage failure.
            throw e
        } catch(e: Exception) {status="Couldn't save";error=e.message ?: "Couldn't save. Retry or copy your draft.";false}
    }
    fun retry() = action { flush() }
    fun browse() = action {
        saveJob?.cancel()
        if(flush()){draft?.let { withContext(Dispatchers.IO){store.finishDraft(it)} };draft=null;recoveredDrafts=withContext(Dispatchers.IO){store.drafts()};collection=true}
    }
    fun resumeDraft(id:String) = action {
        saveJob?.cancel();if(!flush())return@action
        draft?.let{withContext(Dispatchers.IO){store.finishDraft(it)}}
        val saved=withContext(Dispatchers.IO){store.drafts().firstOrNull{it.id==id}} ?: return@action
        show(saved);collection=false;recovery=true;flush()
    }
    fun capture() = action {
        saveJob?.cancel();if(!flush())return@action
        draft?.let { withContext(Dispatchers.IO){store.finishDraft(it)} }
        show(withContext(Dispatchers.IO){store.newDraft()});collection=false;switchView("Notes")
    }
    fun open(note: Note, query:String="") = action {
        saveJob?.cancel();if(!flush())return@action
        draft?.let { withContext(Dispatchers.IO){store.finishDraft(it)} }
        show(withContext(Dispatchers.IO){store.beginEdit(note)});collection=false
        NotesSearch.matches(editorText,query).firstOrNull()?.let(::selectMatch)
    }
    fun saveCopy() = action {
        store=NotesStore(NotesDatabase.get(getApplication()));withContext(Dispatchers.IO){store.initialize()}
        val copy=withContext(Dispatchers.IO){store.newDraft(content(), protected=draft?.note?.protected == true)}
        show(copy);flush()
    }
    fun editMetadata(change:(Note)->Note) = action {
        saveJob?.cancel();if(!flush())return@action
        draft?.let { current ->
            val updated=withContext(Dispatchers.IO){store.saveDraft(current.copy(note=change(current.note)))}
            draft=updated;status="Saving";flush()
        }
    }
    fun updatePreferences(value: NotesPreferences) = action {
        if(!flush())return@action
        withContext(Dispatchers.IO){store.preferences(value)};prefs=value
        if(value.moduleLocked) { draft=withContext(Dispatchers.IO){store.drafts().firstOrNull{it.id==draft?.id}} }
        start()
    }
    fun saveLabel(value:NoteLabel) = action { withContext(Dispatchers.IO){store.saveLabel(value)};labels=withContext(Dispatchers.IO){store.labels()} }
    fun removeLabel(label:NoteLabel) = action {
        if(!flush())return@action
        draft?.let{withContext(Dispatchers.IO){store.finishDraft(it)}};draft=null
        withContext(Dispatchers.IO){store.removeLabel(label.id)};labels=withContext(Dispatchers.IO){store.labels()}
        draft=null;collection=true
    }
    fun batch(targets: List<Note>, change:(Note)->Note) = action {
        val changed=withContext(Dispatchers.IO){store.batch(targets,change)}
        val deletions=targets.zip(changed).filter{(before,after)->before.deletedAt==null&&after.deletedAt!=null}
        undoJob?.cancel()
        undo=deletions.takeIf{it.isNotEmpty()}?.let{pairs->pairs.map{it.first} to pairs.map{it.second}}
        selected=emptySet()
        if(undo!=null)undoJob=viewModelScope.launch{delay(5000);undo=null}
    }
    fun undoBatch() = action { undo?.let{withContext(Dispatchers.IO){store.undo(it.first,it.second)}};undo=null }
    fun purge(targets:List<Note>) = action { withContext(Dispatchers.IO){store.db.withTransactionCompat { targets.forEach{store.purge(it)} }} }
    fun reorder(targets:List<Note>) = action {
        withContext(Dispatchers.IO){store.db.withTransactionCompat {
            store.reorder(targets)
            store.preferences(prefs.copy(sort="manual",notebook=null))
        }}
        prefs=prefs.copy(sort="manual",notebook=null);selected=emptySet()
    }
    fun toggleLabel(targets:List<Note>,label:NoteLabel) {
        val remove=targets.all{label.id in it.tags}
        batch(targets){it.copy(tags=if(remove)it.tags-label.id else it.tags+label.id)}
    }
    var checklistInsert by mutableIntStateOf(0); private set
    fun insertChecklist(){checklistInsert++;command("checklist")}
    fun switchView(value:String){view=value;query="";selected=emptySet()}

    fun persistBrowse(index:Int,offset:Int) = action {
        val value=prefs.copy(notebook=null,scrollIndex=index,scrollOffset=offset)
        withContext(Dispatchers.IO){store.preferences(value)};prefs=value
    }
    fun filtered(query:String):List<Note> {
        val names=labels.associate { it.id to it.name }
        val filtered=notes.filter { n ->
            (if(view=="Bin") n.deletedAt!=null else n.deletedAt==null) &&
                NotesSearch.contains(n.content.title+"\n"+n.content.searchableText+"\n"+n.tags.mapNotNull{names[it]}.joinToString(" "),query)
        }
        val comparator=when(prefs.sort){"created"->compareByDescending<Note>{it.createdAt};"title"->compareBy{it.content.preview.lowercase()};"manual"->compareBy{it.order};else->compareByDescending{it.updatedAt}}
        return filtered.sortedWith(compareByDescending<Note>{it.pinned}.then(comparator).thenBy{it.id})
    }
}

private suspend fun <T> NotesDatabase.withTransactionCompat(block:suspend()->T):T = this.withTransaction { block() }
