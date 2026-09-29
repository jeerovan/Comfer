package com.jeerovan.comfer.notes

import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*

class NotesGuideCreationTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val prefs get() = context.getSharedPreferences(NOTES_GUIDE_PREFERENCES, Context.MODE_PRIVATE)
    private lateinit var previous: NotesLocalSnapshot
    private var flags: Map<String, *> = emptyMap<String, Any>()
    private var activity: ActivityScenario<NotesActivity>? = null
    @Before fun setup() = runBlocking {
        check(context.packageName.endsWith(".notificationtest"))
        previous=NotesBackup.local(context);flags=prefs.all;prefs.edit().clear().commit()
        NotesSession.lock();NotesStore(NotesDatabase.get(context)).replace(NotesSnapshot())
    }
    @After fun restore() = runBlocking {
        activity?.close();NotesBackup.restoreLocal(context,previous)
        prefs.edit().clear().also { e -> flags.forEach { (k,v) -> if(v is Boolean)e.putBoolean(k,v) } }.commit()
        Unit
    }
    private fun waitForGuide(tag: String) {
        compose.waitUntil(10000) { compose.onNodeWithTag(tag).isDisplayed() }
    }
    private fun create(title: String) {
        compose.onNodeWithContentDescription(context.getString(com.jeerovan.comfer.R.string.module_new_note)).performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithTag("notes-editor").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("notes-editor").performTextReplacement(title)
        compose.waitUntil(10000) { runBlocking { NotesBackup.snapshot(context).notes.any { it.content.title==title } } }
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        androidx.test.espresso.Espresso.pressBack()
        compose.waitUntil(10000) { compose.onAllNodesWithTag("notes-search-row").fetchSemanticsNodes().isNotEmpty() }
    }
    @Test fun creatingNotesTeachesHoldThenDragAndKeepsLearningAcrossRecreation() {
        activity=ActivityScenario.launch(NotesActivity::class.java)
        compose.waitUntil(10000) { compose.onAllNodesWithTag("notes-search-row").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("notes-guide-hold").assertDoesNotExist()
        create("First guided note")
        waitForGuide("notes-guide-hold")
        activity!!.recreate()
        waitForGuide("notes-guide-hold")
        compose.onNodeWithTag("notes-guide-hold").performTouchInput { longClick() }
        compose.waitUntil(5000) { compose.onAllNodesWithTag("notes-guide-hold").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithTag("notes-action-bar").assertIsDisplayed()
        androidx.test.espresso.Espresso.pressBack()
        create("Second guided note")
        waitForGuide("notes-guide-drag")
        val before=runBlocking { NotesBackup.snapshot(context).notes }
        compose.waitUntil(8000) { prefs.getBoolean("drag",false) }
        compose.onNodeWithTag("notes-guide-drag").assertDoesNotExist()
        assertEquals(before,runBlocking { NotesBackup.snapshot(context).notes })
        activity!!.recreate()
        compose.waitUntil(10000) { compose.onAllNodesWithTag("notes-search-row").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("notes-guide-hold").assertDoesNotExist()
        compose.onNodeWithTag("notes-guide-drag").assertDoesNotExist()
    }

    @Test fun listDragCompletesGuideBeforeTimeoutAndKeepsItDismissed() = verifyEarlyDrag(false)
    @Test fun gridDragCompletesGuideBeforeTimeoutAndKeepsItDismissed() = verifyEarlyDrag(true)

    private fun verifyEarlyDrag(grid: Boolean) {
        prefs.edit().putBoolean("hold", true).commit()
        val first = Note(id="first", content=NoteContent("First", "Body"))
        val second = Note(id="second", content=NoteContent("Second", "Body"))
        runBlocking { NotesStore(NotesDatabase.get(context)).replace(NotesSnapshot(
            notes=listOf(first, second), preferences=NotesPreferences(grid=grid))) }
        activity=ActivityScenario.launch(NotesActivity::class.java)
        waitForGuide("notes-guide-drag")
        compose.mainClock.autoAdvance=false
        compose.onNodeWithTag("note-first").performTouchInput { down(center);advanceEventTime(100);cancel() }
        compose.mainClock.advanceTimeBy(100)
        assertFalse(prefs.getBoolean("drag", false))
        compose.onNodeWithTag("notes-guide-drag").assertIsDisplayed()
        val from=compose.onNodeWithTag("note-first").fetchSemanticsNode().boundsInRoot.center
        val to=compose.onNodeWithTag("note-second").fetchSemanticsNode().boundsInRoot.center
        compose.onNodeWithTag("note-first").performTouchInput {
            down(center);advanceEventTime(700);moveBy(to-from,200)
        }
        compose.mainClock.advanceTimeBy(100)
        assertTrue("A real drag must finish learning before the five-second timer", prefs.getBoolean("drag", false))
        compose.onNodeWithTag("note-first").performTouchInput { cancel() }
        compose.mainClock.advanceTimeBy(1000)
        compose.onNodeWithTag("notes-guide-drag").assertDoesNotExist()
        compose.mainClock.autoAdvance=true
        activity!!.recreate()
        compose.waitUntil(10000) { compose.onAllNodesWithTag("notes-search-row").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("notes-guide-drag").assertDoesNotExist()
        assertEquals(setOf("first", "second"), runBlocking { NotesBackup.snapshot(context).notes.map { it.id }.toSet() })
    }
}
