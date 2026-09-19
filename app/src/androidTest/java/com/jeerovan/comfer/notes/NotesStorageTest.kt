package com.jeerovan.comfer.notes

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.GCMParameterSpec

class NotesStorageTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var db: NotesDatabase
    private lateinit var store: NotesStore
    private lateinit var crypto: TestCipher
    class TestCipher : NotesCipher {
        private val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        var failNote = false
        var upgradeRequired = false
        var sealsUntilFailure = Int.MAX_VALUE
        override fun needsSessionUpgrade(bytes: ByteArray, protected: Boolean) = upgradeRequired && protected
        override fun seal(bytes: ByteArray, identity: String, protected: Boolean): ByteArray {
            check(sealsUntilFailure-- > 0) { "Injected upgrade failure" }
            if(failNote && identity.contains(":note:")) throw java.io.IOException("Injected full storage")
            if(protected) NotesSession.requireUnlocked()
            val c=Cipher.getInstance("AES/GCM/NoPadding"); c.init(Cipher.ENCRYPT_MODE,key); c.updateAAD(identity.toByteArray())
            return c.iv + c.doFinal(bytes)
        }
        override fun open(bytes: ByteArray, identity: String, protected: Boolean): ByteArray {
            if(protected) NotesSession.requireUnlocked()
            val c=Cipher.getInstance("AES/GCM/NoPadding"); c.init(Cipher.DECRYPT_MODE,key,GCMParameterSpec(128,bytes.copyOfRange(0,12)));c.updateAAD(identity.toByteArray())
            return c.doFinal(bytes.copyOfRange(12,bytes.size))
        }
    }
    @Before fun setup() = runBlocking {
        db=Room.inMemoryDatabaseBuilder(context,NotesDatabase::class.java).build()
        crypto=TestCipher();store=NotesStore(db,crypto);store.initialize()
    }
    @After fun close() { db.close();NotesSession.lock() }
    private suspend fun saved(text:String):NoteDraft = store.commit(store.saveDraft(store.newDraft().let { it.copy(note=it.note.copy(content=NoteContent(text=text))) }))
    @Test fun sessionKeyUpgradePreservesContentAndRollsBackPartialFailure() = runBlocking {
        NotesSession.authorize()
        store.preferences(store.preferences().copy(moduleLocked = true))
        val first = saved("First protected content")
        val second = saved("Second protected content")
        val beforeRows = db.dao().page()
        val beforeDrafts = store.drafts()
        crypto.upgradeRequired = true
        crypto.sealsUntilFailure = 1
        try { store.upgradeProtectedContent(); fail("Partial upgrade committed") } catch (_: IllegalStateException) { }
        beforeRows.forEach { assertArrayEquals(it.payload, db.dao().note(it.id)!!.payload) }
        assertEquals(beforeDrafts, store.drafts())
        crypto.sealsUntilFailure = Int.MAX_VALUE
        store.upgradeProtectedContent()
        assertEquals(first.note, store.note(first.note.id))
        assertEquals(second.note, store.note(second.note.id))
        assertEquals(beforeDrafts, store.drafts())
        beforeRows.forEach { assertFalse(it.payload.contentEquals(db.dao().note(it.id)!!.payload)) }
    }
    @Test fun durableLongUnicodeDraftAndCommittedStateSurviveNewStore() = runBlocking {
        val text="हिन्दी 中文 <literal> 👩🏽‍💻\n".repeat(6000)
        val draft=saved(text)
        val reopened=NotesStore(db,crypto)
        assertEquals(text,reopened.note(draft.note.id)!!.content.text)
        assertEquals(draft,reopened.drafts().single())
        assertFalse(db.dao().note(draft.note.id)!!.payload.toString(Charsets.UTF_8).contains("literal"))
        store.finishDraft(draft);assertTrue(store.drafts().isEmpty())
    }
    @Test fun oversizeInputIsRejectedWithoutChangingDurableContent() = runBlocking {
        val first=saved("keep this version")
        val input=first.copy(note=first.note.copy(content=NoteContent(text="x".repeat(1_000_001))))
        try{store.saveDraft(input);fail()}catch(e:IllegalArgumentException){assertTrue(e.message!!.contains("copy"))}
        assertEquals("keep this version",store.note(first.note.id)!!.content.text)
        assertEquals(first,store.drafts().single())
        assertEquals(1_000_001,input.note.content.text.length)
    }
    @Test fun failedCommitRetainsDraftAndOldNote() = runBlocking {
        val first=saved("old")
        val next=store.saveDraft(first.copy(note=first.note.copy(content=NoteContent(text="new"))))
        crypto.failNote=true
        try { store.commit(next);fail() } catch(_:java.io.IOException) { }
        assertEquals("old",store.note(first.note.id)!!.content.text)
        assertEquals("new",store.drafts().single().note.content.text)
        crypto.failNote=false
        assertEquals("new",store.commit(next).note.content.text)
    }
    @Test fun staleWritesAndDatasetReplacementCannotOverwriteNewerEdits() = runBlocking {
        val first=saved("initial")
        val second=store.saveDraft(first.copy(note=first.note.copy(content=NoteContent(text="newer"))))
        try {store.saveDraft(first);fail()} catch(_:NotesConflict){}
        store.mutate(first.note.id,first.note.revision) { it.copy(pinned=true) }
        try {store.commit(second);fail()} catch(_:NotesConflict){}
        assertEquals("newer",store.drafts().single().note.content.text)
        val snapshot=store.snapshot();store.replace(snapshot)
        try {store.commit(second);fail()} catch(_:NotesConflict){}
        assertEquals(snapshot.notes,NotesStore(db,crypto).snapshot().notes)
    }
    @Test fun emptyCaptureAndLegacyChecklistPreserveContent() = runBlocking {
        val empty=store.newDraft();val committed=store.commit(empty)
        assertNull(store.note(empty.note.id));store.finishDraft(committed)
        val content=NoteContent(checklist=true,items=listOf(NoteItem(text="first"),NoteItem(text="second")))
        val checked=content.copy(items=content.items.map { it.copy(checked=true) })
        assertEquals("[x] first\n[x] second",checked.asText().text)
        val note=saved("placeholder")
        val updated=store.mutate(note.note.id,note.note.revision){it.copy(content=checked)}
        assertNull(updated.deletedAt);assertFalse(updated.archived)
    }
    @Test fun batchFailureRollsBackAndUndoProtectsLaterEdits() = runBlocking {
        val a=saved("a").note;val b=saved("b").note
        store.mutate(b.id,b.revision){it.copy(pinned=true)}
        try {store.batch(listOf(a,b)){it.copy(deletedAt=1)};fail()}catch(_:NotesConflict){}
        assertNull(store.note(a.id)!!.deletedAt)
        val after=store.batch(listOf(a)){it.copy(deletedAt=1)}
        store.mutate(a.id,after.single().revision){it.copy(pinned=true)}
        try {store.undo(listOf(a),after);fail()}catch(_:NotesConflict){}
        assertTrue(store.note(a.id)!!.pinned)
    }
    @Test fun privateCopyUsesProtectedStorageFromItsFirstWrite() = runBlocking {
        NotesSession.authorize()
        val copy=store.newDraft(NoteContent(text="private copied draft"),protected=true)
        assertTrue(db.dao().draft(copy.id)!!.protected)
        NotesSession.lock()
        assertTrue(store.drafts().isEmpty())
        try{store.snapshot();fail()}catch(_:NotesLocked){}
    }
    @Test fun protectedDraftAndContentUnavailableAfterRelock() = runBlocking {
        val draft=saved("private fixture")
        NotesSession.authorize()
        val protected=store.saveDraft(draft.copy(note=draft.note.copy(protected=true)))
        store.commit(protected)
        NotesSession.lock()
        assertTrue(store.drafts().isEmpty())
        try {store.note(draft.note.id);fail()}catch(_:NotesLocked){}
        try {store.snapshot();fail()}catch(_:NotesLocked){}
        NotesSession.authorize();assertEquals("private fixture",store.note(draft.note.id)!!.content.text)
    }
    @Test fun ciphertextRejectsRowSubstitution() = runBlocking {
        val first=saved("first");val second=saved("second")
        val a=db.dao().note(first.note.id)!!;val b=db.dao().note(second.note.id)!!
        db.dao().put(b.copy(payload=a.payload))
        try {store.note(second.note.id);fail()}catch(_:java.security.GeneralSecurityException){}
        assertEquals("first",store.note(first.note.id)!!.content.text)
    }
    @Test fun moduleLockReencryptsExistingNotesAndDraftsAtomically() = runBlocking {
        val first=saved("module fixture")
        NotesSession.authorize();store.preferences(store.preferences().copy(moduleLocked=true))
        assertTrue(db.dao().note(first.note.id)!!.protected)
        assertTrue(db.dao().drafts().single().protected)
        NotesSession.lock()
        try {store.note(first.note.id);fail()}catch(_:NotesLocked){}
        NotesSession.authorize();store.preferences(store.preferences().copy(moduleLocked=false))
        assertFalse(db.dao().note(first.note.id)!!.protected)
        assertEquals("module fixture",store.note(first.note.id)!!.content.text)
    }
    @Test fun failedModuleLockRollsBackPolicyAndPayloads() = runBlocking {
        val first=saved("keep accessible")
        NotesSession.authorize();crypto.failNote=true
        try {store.preferences(store.preferences().copy(moduleLocked=true));fail()}catch(_:java.io.IOException){}
        crypto.failNote=false;NotesSession.lock()
        assertFalse(store.preferences().moduleLocked)
        assertEquals("keep accessible",store.note(first.note.id)!!.content.text)
    }
    @Test fun binRetainsUntilSevenDayBoundary() = runBlocking {
        val first=saved("seven day fixture");store.finishDraft(first)
        val deletedAt=1000L
        store.mutate(first.note.id,first.note.revision){it.copy(deletedAt=deletedAt)}
        val expiry=deletedAt+7L*24*60*60*1000
        store.cleanup(expiry-1);assertNotNull(store.note(first.note.id))
        store.cleanup(expiry);assertNull(store.note(first.note.id))
    }
    @Test fun legacyItemProtectionBecomesReversibleModuleProtection() = runBlocking {
        NotesSession.authorize()
        val draft=store.newDraft(NoteContent(text="legacy private"),protected=true)
        val saved=store.commit(draft)
        store.normalizeLegacyProtection()
        assertTrue(store.preferences().moduleLocked)
        assertFalse(store.note(saved.note.id)!!.protected)
        assertFalse(store.drafts().single().note.protected)
        assertTrue(db.dao().note(saved.note.id)!!.protected)
        NotesSession.lock()
        try{store.note(saved.note.id);fail()}catch(_:NotesLocked){}
        NotesSession.authorize()
        store.preferences(store.preferences().copy(moduleLocked=false))
        NotesSession.lock()
        assertEquals("legacy private",store.note(saved.note.id)!!.content.text)
        assertFalse(db.dao().note(saved.note.id)!!.protected)
    }
    @Test fun trashExpirySkipsActiveDraftAndNotebookDeletionKeepsNotes() = runBlocking {
        val first=saved("notebook fixture");store.finishDraft(first)
        val notebook=NoteLabel(name="Work");store.saveLabel(notebook)
        val moved=store.mutate(first.note.id,first.note.revision){it.copy(notebook=notebook.id)}
        store.removeLabel(notebook.id)
        val fallback=store.note(first.note.id)!!;assertEquals(Note.INBOX,fallback.notebook)
        val draft=store.beginEdit(fallback)
        val deleted=store.mutate(fallback.id,fallback.revision){it.copy(deletedAt=1)}
        store.cleanup(31L*24*60*60*1000)
        assertNotNull(store.note(first.note.id))
        db.dao().deleteDraft(draft.id)
        store.cleanup(31L*24*60*60*1000)
        assertNull(store.note(first.note.id))
    }
    @Test fun replacementBackupRecoversAfterDeviceKeyLossAndUsesFreshKeys() = runBlocking {
        val original=NotesSnapshot(notes=listOf(Note(content=NoteContent(text="recoverable synthetic note"))))
        val real=NotesStore(db,KeystoreNotesCipher());real.replace(original,rotateKey=true)
        val oldGeneration=db.dao().state()!!.keyGeneration
        val portable=NotesBackup.pack(NotesStore(db,KeystoreNotesCipher()).snapshot(),null)
        java.security.KeyStore.getInstance("AndroidKeyStore").apply{load(null);deleteEntry("comfer.notes.local.v2.$oldGeneration")}
        try{NotesStore(db,KeystoreNotesCipher()).snapshot();fail()}catch(_:IllegalStateException){}
        val restored=NotesBackup.unpack(portable.first,null,portable.second)
        NotesStore(db,KeystoreNotesCipher()).replace(restored,rotateKey=true)
        assertNotEquals(oldGeneration,db.dao().state()!!.keyGeneration)
        assertEquals(original,NotesStore(db,KeystoreNotesCipher()).snapshot())
    }
    @Test fun realKeystoreRoundTripAndMissingKeyDoNotUsePlaintext() {
        val c=KeystoreNotesCipher();val bytes="synthetic Notes".toByteArray()
        val encrypted=c.seal(bytes,"fixture",false)
        assertArrayEquals(bytes,c.open(encrypted,"fixture",false))
        assertFalse(encrypted.toString(Charsets.UTF_8).contains("synthetic"))
        NotesSession.lock()
        try {c.seal(bytes,"fixture",true);fail()}catch(_:NotesLocked){}
    }
}
