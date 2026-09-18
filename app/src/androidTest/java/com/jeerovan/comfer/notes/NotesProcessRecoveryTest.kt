package com.jeerovan.comfer.notes

import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.Assume.assumeTrue
import java.io.File

/** Run separately with am force-stop between these two methods, using the isolated package. */
class NotesProcessRecoveryTest {
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private val marker get()=File(context.noBackupFilesDir,"notes-process-fixture-ids")
    @Test fun persistCommittedNoteAndPendingDraft()=runBlocking {
        assumeTrue("Explicit two-process fixture only", InstrumentationRegistry.getArguments().getString("notesProcessFixture")=="write")
        check(context.packageName.endsWith(".notificationtest"))
        val store=NotesStore(NotesDatabase.get(context));store.initialize()
        val committed=store.commit(store.newDraft(NoteContent(text="Committed before process stop")))
        val pending=store.saveDraft(committed.copy(note=committed.note.copy(content=NoteContent(text="Pending recovery after process stop"))))
        marker.writeText(pending.id+"\n"+pending.note.id)
    }
    @Test fun verifyRecoveryInNewProcess()=runBlocking {
        assumeTrue("Explicit two-process fixture only", InstrumentationRegistry.getArguments().getString("notesProcessFixture")=="read")
        check(context.packageName.endsWith(".notificationtest"))
        val ids=marker.readLines();val store=NotesStore(NotesDatabase.get(context));store.initialize()
        try {
            assertEquals("Committed before process stop",store.note(ids[1])!!.content.text)
            val pending=store.drafts().first{it.id==ids[0]}
            assertEquals("Pending recovery after process stop",pending.note.content.text)
            val saved=store.commit(pending)
            assertEquals("Pending recovery after process stop",store.note(ids[1])!!.content.text)
            store.finishDraft(saved)
        }finally{store.dao.deleteDraft(ids[0]);store.dao.delete(ids[1]);marker.delete()}
        Unit
    }
}
