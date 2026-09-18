package com.jeerovan.comfer.notes

import android.app.Application
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.room.withTransaction
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*

/** Hold Room's writer so cancellation occurs inside flush's atomic commit, not during its debounce. */
class NotesCancellationTest {
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private val viewModels=ViewModelStore()
    private lateinit var model:NotesViewModel
    private lateinit var previous:NotesLocalSnapshot
    private val db get()=NotesDatabase.get(context)

    @Before fun setup()=runBlocking {
        check(context.packageName.endsWith(".notificationtest"))
        NotesSession.lock()
        previous=NotesBackup.local(context)
        val note=Note(id="cancellation",content=NoteContent("Title","Original"))
        NotesStore(db).replace(NotesSnapshot(notes=listOf(note)))
        withContext(Dispatchers.Main.immediate) {
            model=ViewModelProvider(viewModels,ViewModelProvider.AndroidViewModelFactory(context.applicationContext as Application))[NotesViewModel::class.java]
            model.start().join()
            model.open(note).join()
        }
    }

    @After fun cleanup()=runBlocking {
        withContext(Dispatchers.Main.immediate) { viewModels.clear() }
        NotesBackup.restoreLocal(context,previous)
        NotesSession.lock()
    }

    private suspend fun holdWriter(block:suspend (CompletableDeferred<Unit>)->Unit)=coroutineScope {
        val entered=CompletableDeferred<Unit>()
        val release=CompletableDeferred<Unit>()
        val writer=launch(Dispatchers.IO) { db.withTransaction { entered.complete(Unit);release.await() } }
        try {
            withTimeout(10000) { entered.await();block(release) }
        } finally { release.complete(Unit);writer.join() }
    }
    private suspend fun saved()=NotesStore(db).note("cancellation")!!.content.text

    @Test fun cancelledCommitDoesNotShowErrorAndLatestEditIsSaved()=runBlocking {
        holdWriter { release ->
            val saving=withContext(Dispatchers.Main.immediate) {
                model.updateCanvas(TextFieldValue("Title\nFirst edit"))
                model.retry() // Starts immediately on Main; its IO commit waits behind our writer.
            }
            withContext(Dispatchers.Main.immediate) {
                saving.cancel() // The same StandaloneCoroutine cancellation used by autosave.
                model.updateCanvas(TextFieldValue("Title\nLatest edit"))
            }
            release.complete(Unit)
            saving.join()
            withContext(Dispatchers.Main.immediate) {
                assertNull("Cancellation must not become a Notes error",model.error)
                assertNotEquals("Couldn't save",model.status)
                assertEquals("Latest edit",model.body.text)
                model.retry().join()
                assertEquals("Saved",model.status)
                assertNull(model.error)
            }
        }
        assertEquals("Latest edit",saved())
    }

    @Test fun backgroundCancellationFinishesDurableSaveAndResumeRecovers()=runBlocking {
        holdWriter { release ->
            val saving=withContext(Dispatchers.Main.immediate) {
                model.updateCanvas(TextFieldValue("Title\nBefore background"))
                model.retry()
            }
            withContext(Dispatchers.Main.immediate) {
                saving.cancel()
                model.background()
            }
            release.complete(Unit)
            saving.join()
            assertEquals("Before background",saved())
            withContext(Dispatchers.Main.immediate) {
                assertNull("A stopped editor must not receive a cancellation banner",model.error)
                model.start().join()
                assertEquals("Saved",model.status)
                assertNull(model.error)
                assertEquals("Before background",model.body.text)
                model.browse().join()
                assertTrue(model.collection)
            }
        }
    }

    @Test fun cancelledSaveWaiterDoesNotInterruptActiveCommit()=runBlocking {
        holdWriter { release ->
            val jobs=withContext(Dispatchers.Main.immediate) {
                model.updateCanvas(TextFieldValue("Title\nKeep this edit"))
                val first=model.retry()
                val waiting=model.retry()
                waiting.cancel()
                first to waiting
            }
            jobs.second.join()
            release.complete(Unit)
            jobs.first.join()
            withContext(Dispatchers.Main.immediate) {
                assertNull(model.error)
                assertEquals("Saved",model.status)
            }
        }
        assertEquals("Keep this edit",saved())
    }

    @Test fun realSaveFailureStillReportsErrorAndRetainsDraftForRetry()=runBlocking {
        // Exercise the real storage size guard rather than disguising cancellation as a generic failure.
        val oversized="x".repeat(1_000_001)
        withContext(Dispatchers.Main.immediate) {
            model.updateCanvas(TextFieldValue("Title\n$oversized"))
            assertFalse(model.flush())
            assertEquals("Couldn't save",model.status)
            assertTrue(model.error.orEmpty().contains("1 MB"))
            assertEquals(oversized,model.body.text)
        }
        assertEquals("Original",saved())
        withContext(Dispatchers.Main.immediate) {
            model.updateCanvas(TextFieldValue("Title\nRecovered edit"))
            assertTrue(model.flush())
            assertEquals("Saved",model.status)
        }
        assertEquals("Recovered edit",saved())
    }
}
