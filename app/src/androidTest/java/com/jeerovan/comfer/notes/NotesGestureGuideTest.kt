package com.jeerovan.comfer.notes

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class NotesGestureGuideTest(private val grid: Boolean, private val direction: LayoutDirection) {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val prefs get() = context.getSharedPreferences(NOTES_GUIDE_PREFERENCES, Context.MODE_PRIVATE)
    private var previous: Map<String, *> = emptyMap<String, Any>()
    private val first = Note(id="first",content=NoteContent("First", "Body"))
    private val second = Note(id="second",content=NoteContent("Second", "Body"))
    private var rows by mutableStateOf(listOf(first))
    private var enabled by mutableStateOf(true)
    private var opens = 0
    private var selections = 0
    private val drops = mutableListOf<List<String>>()
    private lateinit var progress: NotesGuideProgress

    @Before fun setup() {
        check(context.packageName.endsWith(".notificationtest"))
        previous = prefs.all; prefs.edit().clear().commit()
        progress = NotesGuideProgress(context)
    }
    @After fun restore() { prefs.edit().clear().also { e -> previous.forEach { (k,v) -> if(v is Boolean)e.putBoolean(k,v) } }.commit() }
    private fun show() {
        compose.mainClock.autoAdvance = false
        compose.setContent { CompositionLocalProvider(LocalLayoutDirection provides direction) { MaterialTheme {
            NotesReorderGrid(rows, grid, true, { drops += it.map(Note::id) }, { opens++ }, { selections++ },
                Modifier.width(320.dp).height(440.dp), topPadding=100.dp,
                guide=if(enabled)progress.next(rows.size) else null,
                onHold=progress::performedHold, onGuideFinished=progress::finishedDragGuide,
            ) { Text(it.content.title, Modifier.fillMaxWidth().height(80.dp)) }
        } } }
        compose.mainClock.advanceTimeBy(100)
    }

    @Test fun firstHoldPersistsUntilLongPressAndDoesNotConsumeTaps() {
        show()
        compose.mainClock.advanceTimeBy(15000)
        compose.onNodeWithTag("notes-guide-hold").assertIsDisplayed()
        compose.onNodeWithTag("notes-guide-hold").performTouchInput { click() }
        compose.mainClock.advanceTimeBy(100)
        assertEquals(1, opens)
        compose.onNodeWithTag("note-first").performTouchInput { down(center); advanceEventTime(100); cancel() }
        compose.mainClock.advanceTimeBy(100)
        compose.onNodeWithTag("notes-guide-hold").assertIsDisplayed()
        compose.onNodeWithTag("notes-guide-hold").performTouchInput { longClick() }
        compose.mainClock.advanceTimeBy(100)
        compose.onNodeWithTag("notes-guide-hold").assertDoesNotExist()
        assertEquals(1, selections)
        assertNull(NotesGuideProgress(context).next(1))
        assertEquals(NotesGuide.DRAG, NotesGuideProgress(context).next(2))
    }

    @Test fun secondNoteShowsFiveSecondGuideWithoutChangingNotes() {
        progress.performedHold()
        show()
        compose.onNodeWithTag("notes-guide-drag").assertDoesNotExist()
        compose.runOnIdle { rows=listOf(first, second) }
        compose.mainClock.advanceTimeBy(100)
        val source=compose.onNodeWithTag("note-first").fetchSemanticsNode().boundsInRoot
        val target=compose.onNodeWithTag("note-second").fetchSemanticsNode().boundsInRoot
        val initial=compose.onNodeWithTag("notes-guide-drag").fetchSemanticsNode().boundsInRoot
        assertEquals(source.center.x, initial.center.x, 1f)
        assertEquals(source.center.y, initial.center.y, 1f)
        compose.mainClock.advanceTimeBy(1500)
        val moved=compose.onNodeWithTag("notes-guide-drag").fetchSemanticsNode().boundsInRoot
        assertEquals(target.center.x, moved.center.x, 2f)
        assertEquals(target.center.y, moved.center.y, 2f)
        compose.mainClock.advanceTimeBy(3200)
        compose.onNodeWithTag("notes-guide-drag").assertExists()
        compose.mainClock.advanceTimeBy(400)
        compose.onNodeWithTag("notes-guide-drag").assertDoesNotExist()
        assertTrue(drops.isEmpty());assertEquals(listOf(first,second),rows)
        assertNull(NotesGuideProgress(context).next(2))
    }

    @Test fun holdTakesPriorityAndHiddenOrIneligibleDragDoesNotExpire() {
        rows=listOf(first,second.copy(pinned=true));show()
        compose.onNodeWithTag("notes-guide-hold").assertIsDisplayed()
        compose.onNodeWithTag("notes-guide-drag").assertDoesNotExist()
        compose.runOnIdle { progress.performedHold() }
        compose.mainClock.advanceTimeBy(6000)
        compose.onNodeWithTag("notes-guide-drag").assertDoesNotExist()
        assertEquals(NotesGuide.DRAG,NotesGuideProgress(context).next(2))
        compose.runOnIdle { rows=listOf(first,second);enabled=false }
        compose.mainClock.advanceTimeBy(6000)
        assertEquals(NotesGuide.DRAG,NotesGuideProgress(context).next(2))
        compose.runOnIdle { enabled=true }
        compose.mainClock.advanceTimeBy(100)
        compose.onNodeWithTag("notes-guide-drag").assertIsDisplayed()
        compose.runOnIdle { rows=listOf(first) }
        compose.mainClock.advanceTimeBy(6000)
        compose.onNodeWithTag("notes-guide-drag").assertDoesNotExist()
        assertEquals(NotesGuide.DRAG,NotesGuideProgress(context).next(2))
    }

    @Test fun dragGuideLeavesTheRealReorderGestureWorking() {
        progress.performedHold();rows=listOf(first,second);show()
        val from=compose.onNodeWithTag("note-first").fetchSemanticsNode().boundsInRoot.center
        val to=compose.onNodeWithTag("note-second").fetchSemanticsNode().boundsInRoot.center
        compose.onNodeWithTag("notes-guide-drag").performTouchInput {
            down(center);advanceEventTime(700);moveBy(to-from,300)
        }
        compose.mainClock.advanceTimeBy(400)
        compose.onNodeWithTag("notes-guide-drag").assertDoesNotExist()
        compose.onNodeWithTag("note-first").performTouchInput { up() }
        compose.mainClock.advanceTimeBy(1000)
        assertEquals(listOf(listOf("second","first")),drops)
    }

    companion object {
        @JvmStatic @Parameterized.Parameters(name="grid={0}, {1}")
        fun variants()=listOf(arrayOf(false,LayoutDirection.Ltr),arrayOf(false,LayoutDirection.Rtl),arrayOf(true,LayoutDirection.Ltr),arrayOf(true,LayoutDirection.Rtl))
    }
}
