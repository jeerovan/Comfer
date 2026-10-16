package com.jeerovan.comfer.journals

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.jeerovan.comfer.ProtectionSession
import com.jeerovan.comfer.StartupCoordinator
import com.jeerovan.comfer.cancelDeviceCredentialPrompt
import com.jeerovan.comfer.notes.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*

/** Lifecycle/session integration; private Journal cryptography uses the existing test cipher. */
class ProtectionActivityTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var notesBefore: NotesLocalSnapshot
    private lateinit var journalsBefore: JournalLocalSnapshot
    private lateinit var cipherBefore: JournalCipher
    private var notes: ActivityScenario<NotesActivity>? = null
    private var journal: ActivityScenario<JournalActivity>? = null

    @Before fun setup() = runBlocking {
        check(context.packageName.endsWith(".notificationtest"))
        StartupCoordinator.awaitReady()
        notesBefore = NotesBackup.local(context)
        journalsBefore = JournalArchiveMedia.localSnapshot(context)
        cipherBefore = JournalDatabase.get(context).cipher
        NotesStore(NotesDatabase.get(context)).replace(NotesSnapshot())
        JournalArchiveMedia.replaceLocal(context, JournalLocalSnapshot(emptyList(), emptyList()))
        JournalDatabase.get(context).cipher = TestJournalCipher()
        NotesSession.authorize()
        // Empty protected Notes exercises the activity gate without requiring the emulator PIN.
        NotesStore(NotesDatabase.get(context)).preferences(NotesPreferences(moduleLocked = true))
        JournalDatabase.get(context).dao().changeProtection(true)
        JournalDatabase.get(context).dao().insert(JournalEntry(text = "Protected journal fixture"))
    }
    @After fun cleanup(): Unit = runBlocking {
        try {
            notes?.close(); journal?.close()
        } finally {
            try {
                if (::cipherBefore.isInitialized) JournalDatabase.get(context).cipher = cipherBefore
                if (::journalsBefore.isInitialized) JournalArchiveMedia.replaceLocal(context, journalsBefore)
            } finally {
                try {
                    if (::notesBefore.isInitialized) NotesBackup.restoreLocal(context, notesBefore)
                } finally { ProtectionSession.lock() }
            }
        }
    }
    private fun assertNotesVisible() {
        try {
            compose.waitUntil(10000) { compose.onAllNodesWithContentDescription("New note").fetchSemanticsNodes().isNotEmpty() }
        } catch (error: Exception) {
            var state = ""
            notes?.onActivity { activity ->
                val model = androidx.lifecycle.ViewModelProvider(activity)[NotesViewModel::class.java]
                state = "ready=${model.ready}, busy=${model.busy}, collection=${model.collection}, unlocked=${NotesSession.unlocked()}, error=${model.error}"
            }
            throw AssertionError("Notes did not resume: $state; ${compose.onRoot().printToString()}", error)
        }
        assertTrue(NotesSession.unlocked())
    }
    private fun assertJournalVisible() {
        compose.waitUntil(10000) { compose.onAllNodesWithText("Protected journal fixture").fetchSemanticsNodes().isNotEmpty() }
        assertTrue(JournalProtection.authorized())
    }
    @Test fun openingSwitchingRecreatingAndReopeningReuseVerification() {
        notes = ActivityScenario.launch(NotesActivity::class.java)
        assertNotesVisible()
        repeat(5) { notes!!.recreate(); assertNotesVisible() }
        notes!!.close(); notes = null
        assertTrue(JournalProtection.authorized())
        journal = ActivityScenario.launch(JournalActivity::class.java)
        assertJournalVisible()
        journal!!.moveToState(Lifecycle.State.CREATED)
        journal!!.moveToState(Lifecycle.State.RESUMED)
        assertJournalVisible()
        journal!!.recreate(); assertJournalVisible()
        journal!!.close(); journal = ActivityScenario.launch(JournalActivity::class.java)
        assertJournalVisible()
        journal!!.close(); journal = null
        notes = ActivityScenario.launch(NotesActivity::class.java)
        assertNotesVisible()
    }
    @Test fun foregroundDoesNotExpireButTimeAwayDoes() {
        val key = ProtectionSession.PREFERENCE_KEY
        val previous = com.jeerovan.comfer.PreferenceManager.getString(context, key, null)
        try {
            com.jeerovan.comfer.PreferenceManager.setInt(context, key, 30)
            journal = ActivityScenario.launch(JournalActivity::class.java)
            assertJournalVisible()
            val started = android.os.SystemClock.elapsedRealtime()
            compose.waitUntil(40_000) { android.os.SystemClock.elapsedRealtime() - started >= 31_000 }
            assertJournalVisible()
            runBlocking {
                val dao = JournalDatabase.get(context).dao()
                val entry = dao.exportEntries().single()
                dao.update(entry.copy(text = "Saved after foreground timeout", revision = entry.revision + 1))
                assertEquals("Saved after foreground timeout", dao.entry(entry.id)!!.text)
            }
            journal!!.close(); journal = null
            val left = android.os.SystemClock.elapsedRealtime()
            compose.waitUntil(40_000) { android.os.SystemClock.elapsedRealtime() - left >= 31_000 }
            assertFalse(ProtectionSession.authorized())
            journal = ActivityScenario.launch(JournalActivity::class.java)
            cancelDeviceCredentialPrompt(compose) { journal!!.state == Lifecycle.State.DESTROYED }
            journal = null // The cancellation already destroyed this scenario.
        } finally { com.jeerovan.comfer.PreferenceManager.setString(context, key, previous) }
    }
    @Test fun expiredSharedVerificationPromptsAgainInBothModules() {
        ProtectionSession.lock() // Exact elapsed-time boundaries are covered by ProtectionWindowTest.
        notes = ActivityScenario.launch(NotesActivity::class.java)
        cancelDeviceCredentialPrompt(compose) { notes!!.state == Lifecycle.State.DESTROYED }
        notes = null
        journal = ActivityScenario.launch(JournalActivity::class.java)
        cancelDeviceCredentialPrompt(compose) { journal!!.state == Lifecycle.State.DESTROYED }
        journal = null
        assertFalse(ProtectionSession.authorized())
    }
}
