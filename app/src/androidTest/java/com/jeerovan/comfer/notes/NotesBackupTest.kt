package com.jeerovan.comfer.notes

import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import com.jeerovan.comfer.BackupRestoreManager
import com.jeerovan.comfer.StartupCoordinator
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import java.io.File

class NotesBackupTest {
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var previous:NotesLocalSnapshot
    @Before fun before()=runBlocking {
        check(context.packageName.endsWith(".notificationtest"));StartupCoordinator.awaitReady()
        previous=NotesBackup.local(context)
        NotesStore(NotesDatabase.get(context)).apply{initialize();replace(NotesSnapshot())}; Unit
    }
    @After fun after()=runBlocking{NotesBackup.restoreLocal(context,previous);Unit}
    private suspend fun save(text:String):Note {
        val store=NotesStore(NotesDatabase.get(context));store.initialize()
        val draft=store.commit(store.newDraft(NoteContent(text=text)));store.finishDraft(draft);return draft.note
    }
    @Test fun realComferArchiveIncludesNotesAndKeepsConflictingVersion()=runBlocking {
        val original=save("before export")
        val file=File(context.cacheDir,"notes-global-test.zip")
        try {
            BackupRestoreManager.createBackup(context,Uri.fromFile(file),"en")
            assertTrue(BackupRestoreManager.inspectBackup(context,Uri.fromFile(file)).notesIncluded)
            NotesStore(NotesDatabase.get(context)).mutate(original.id,original.revision){it.copy(content=NoteContent(text="newer edit"))}
            BackupRestoreManager.restoreBackup(context,Uri.fromFile(file))
            assertEquals(setOf("before export","newer edit"),NotesBackup.snapshot(context).notes.map{it.content.text}.toSet())
        } finally {file.delete()}
    }
    @Test fun moduleProtectionRequiresPortableBackupPasswordEvenWithoutNotes() {
        val snapshot=NotesSnapshot(preferences=NotesPreferences(moduleLocked=true))
        try{NotesBackup.pack(snapshot,null);fail()}catch(_:IllegalArgumentException){}
        val (archive,data)=NotesBackup.pack(snapshot,"1234")
        assertTrue(NotesBackup.unpack(archive,"1234",data).preferences.moduleLocked)
    }
    @Test fun portableEncryptionWrongPasswordAndTamper() {
        val snapshot=NotesSnapshot(notes=listOf(Note(content=NoteContent(text="secret fixture"),protected=true)))
        val (archive,data)=NotesBackup.pack(snapshot,"1234")
        assertFalse(data.toString(Charsets.UTF_8).contains("secret fixture"))
        assertEquals(snapshot,NotesBackup.unpack(archive,"1234",data))
        try{NotesBackup.unpack(archive,"wrong",data);fail()}catch(_:IllegalArgumentException){}
        try{NotesBackup.unpack(archive,"1234",data.copyOf().also{it[it.lastIndex]=(it.last()+1).toByte()});fail()}catch(_:IllegalArgumentException){}
        try{NotesBackup.pack(snapshot,null);fail()}catch(_:IllegalArgumentException){}
    }
    @Test fun tenThousandRecordsFitOneArchiveAndMalformedReferencesRejected() {
        val snapshot=NotesSnapshot(notes=List(10_000){Note(id="note-$it",content=NoteContent(text="Synthetic $it हिन्दी 中文 " + "data ".repeat(20)))})
        val (archive,data)=NotesBackup.pack(snapshot,null)
        assertEquals(snapshot,NotesBackup.unpack(archive,null,data))
        try{NotesBackup.pack(snapshot.copy(notes=listOf(snapshot.notes.first().copy(notebook="missing"))),null);fail()}catch(_:IllegalArgumentException){}
    }
    @Test fun standaloneRoundTripAndUnsafePathsRejected()=runBlocking {
        save("standalone")
        val file=File(context.cacheDir,"notes-standalone-test.zip")
        try {
            NotesDocuments.export(context,Uri.fromFile(file),"1234")
            assertEquals("standalone",NotesDocuments.read(context,Uri.fromFile(file),"1234").notes.single().content.text)
            assertEquals("Completed",NotesDocuments.lastExport(context)!!.result)
            java.util.zip.ZipOutputStream(file.outputStream()).use{it.putNextEntry(java.util.zip.ZipEntry("../outside"));it.write(byteArrayOf(1));it.closeEntry()}
            try{NotesDocuments.read(context,Uri.fromFile(file),null);fail()}catch(_:IllegalArgumentException){}
            assertEquals("standalone",NotesBackup.snapshot(context).notes.single().content.text)
        }finally{file.delete()}
    }
    @Test fun encryptedRollbackRestoresWithoutUnlockingOrPlaintextJournal()=runBlocking {
        val note=save("rollback fixture")
        val local=NotesBackup.local(context)
        assertFalse(kotlinx.serialization.json.Json.encodeToString(NotesLocalSnapshot.serializer(),local).contains("rollback fixture"))
        NotesStore(NotesDatabase.get(context)).mutate(note.id,note.revision){it.copy(content=NoteContent(text="changed"))}
        NotesSession.lock();NotesBackup.restoreLocal(context,local)
        assertEquals("rollback fixture",NotesBackup.snapshot(context).notes.single().content.text)
    }
}
