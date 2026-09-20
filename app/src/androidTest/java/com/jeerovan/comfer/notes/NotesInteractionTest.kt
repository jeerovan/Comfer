package com.jeerovan.comfer.notes

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*

class NotesInteractionTest {
    @get:Rule val compose=createEmptyComposeRule()
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var previous:NotesLocalSnapshot
    private var activity:ActivityScenario<NotesActivity>?=null
    private val label=NoteLabel(id="label-fixture",name="Work",tag=true)
    private fun fixture(grid:Boolean=false) = NotesSnapshot(
        notes=listOf(
            Note(id="pin",content=NoteContent("Pinned fixture","Top"),pinned=true,order=0,updatedAt=4),
            Note(id="a",content=NoteContent("Alpha","Alpha body"),order=1,updatedAt=3),
            Note(id="b",content=NoteContent("Bravo","Bravo body"),order=2,updatedAt=2),
            Note(id="c",content=NoteContent("Charlie","Charlie body"),order=3,updatedAt=1),
            Note(id="bin",content=NoteContent("Binned Alpha","Only in Bin"),deletedAt=System.currentTimeMillis())
        ),labels=listOf(NoteLabel(Note.INBOX,"Inbox"),label),preferences=NotesPreferences(grid=grid))
    @Before fun before()=runBlocking {
        check(context.packageName.endsWith(".notificationtest"));NotesSession.lock()
        previous=NotesBackup.local(context)
        NotesStore(NotesDatabase.get(context)).replace(fixture())
    }
    @After fun after()=runBlocking {activity?.close();NotesSession.lock();NotesBackup.restoreLocal(context,previous);Unit}
    private fun launch(grid:Boolean=false) {
        if(grid)runBlocking {NotesStore(NotesDatabase.get(context)).replace(fixture(true))}
        activity=ActivityScenario.launch(NotesActivity::class.java)
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        compose.waitUntil(10000){compose.onAllNodesWithTag("note-a").fetchSemanticsNodes().isNotEmpty()&&compose.onAllNodesWithTag("note-b").fetchSemanticsNodes().isNotEmpty()}
    }
    private fun hold(id:String)=compose.onNodeWithTag("note-$id").performTouchInput{longClick(durationMillis=750)}
    private fun saved(id:String)=runBlocking{NotesBackup.snapshot(context).notes.first{it.id==id}}
    private fun pullForThumbReach():androidx.compose.ui.geometry.Rect {
        val original=compose.onNodeWithTag("note-pin").fetchSemanticsNode().boundsInRoot
        compose.onRoot().performTouchInput {
            swipe(original.center,original.center+androidx.compose.ui.geometry.Offset(0f,240f),600)
        }
        compose.waitUntil(5000) { compose.onNodeWithTag("note-pin").fetchSemanticsNode().boundsInRoot.top>original.top+50f }
        compose.waitForIdle()
        return original
    }
    @Test fun thumbReachListTapUsesVisibleCardAndEmptySpaceIsInactive()=thumbReachTap(false)
    @Test fun thumbReachGridTapUsesVisibleCardAndEmptySpaceIsInactive()=thumbReachTap(true)
    private fun thumbReachTap(grid:Boolean) {
        launch(grid)
        val original=pullForThumbReach()
        // Tap where the first card used to be: reach padding must not retain its touch target.
        compose.onRoot().performTouchInput { click(androidx.compose.ui.geometry.Offset(original.center.x,original.top+8f)) }
        compose.onNodeWithTag("notes-editor").assertDoesNotExist()
        compose.onRoot().performTouchInput { longClick(androidx.compose.ui.geometry.Offset(original.center.x,original.top+8f),750) }
        compose.onNodeWithContentDescription("Note color").assertDoesNotExist()
        val visible=compose.onNodeWithTag("note-a").fetchSemanticsNode().boundsInRoot
        compose.onRoot().performTouchInput { click(visible.center) }
        compose.waitUntil(5000) { compose.onAllNodesWithTag("notes-editor").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("notes-editor").assertTextContains("Alpha\nAlpha body")
    }
    @Test fun thumbReachListLongPressSelectsVisibleCard()=thumbReachHold(false)
    @Test fun thumbReachGridLongPressSelectsVisibleCard()=thumbReachHold(true)
    private fun thumbReachHold(grid:Boolean) {
        launch(grid);pullForThumbReach()
        val visible=compose.onNodeWithTag("note-a").fetchSemanticsNode().boundsInRoot
        compose.onRoot().performTouchInput { longClick(visible.center,750) }
        compose.onNodeWithContentDescription("Note color").performClick()
        compose.onNodeWithText("Blue",substring=false).performClick()
        compose.waitUntil(5000) { saved("a").color=="blue" }
        assertEquals("default",saved("pin").color)
        assertEquals("default",saved("b").color)
        assertEquals("default",saved("c").color)
    }
    @Test fun thumbReachCollapseRestoresTouchTargets() {
        launch();val original=pullForThumbReach()
        val moved=compose.onNodeWithTag("note-pin").fetchSemanticsNode().boundsInRoot
        compose.onRoot().performTouchInput { swipe(moved.center,moved.center-androidx.compose.ui.geometry.Offset(0f,moved.top-original.top),600) }
        // Touch-slop may leave a few pixels of reach space; target the actual settled position.
        compose.waitUntil(5000) { compose.onNodeWithTag("note-pin").fetchSemanticsNode().boundsInRoot.top<moved.top-40f }
        val visible=compose.onNodeWithTag("note-a").fetchSemanticsNode().boundsInRoot
        compose.onRoot().performTouchInput { click(visible.center) }
        compose.waitUntil(5000) { compose.onAllNodesWithTag("notes-editor").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("notes-editor").assertTextContains("Alpha\nAlpha body")
    }
    @Test fun onlyNotesAndBinSearchIsScopedAndBottomRowsAreOrdered() {
        launch()
        compose.onNodeWithContentDescription("Notebooks and views").assertDoesNotExist()
        compose.onNodeWithContentDescription("New checklist").assertDoesNotExist()
        val bar=compose.onNodeWithContentDescription("Notes options").fetchSemanticsNode().boundsInRoot
        val search=compose.onNodeWithTag("notes-search-row").fetchSemanticsNode().boundsInRoot
        assertTrue(bar.bottom<=search.top)
        compose.onNodeWithContentDescription("Search notes").performClick()
        compose.onNodeWithTag("notes-search").performTextInput("Alpha")
        compose.waitUntil(10000){compose.onAllNodesWithTag("note-b").fetchSemanticsNodes().isEmpty()}
        compose.onNodeWithTag("note-a").assertExists();compose.onNodeWithTag("note-bin").assertDoesNotExist()
        compose.onNodeWithContentDescription("Notes options").performClick()
        // The native keyboard can still be hiding after Compose finishes the sheet animation.
        compose.waitUntil(5000){compose.onNodeWithText("Deleted notes are kept for 7 days").isDisplayed()}
        compose.onNodeWithText("Deleted notes are kept for 7 days").assertIsDisplayed()
        compose.onNodeWithText("Bin",substring=false).assertIsDisplayed().performClick()
        compose.waitUntil(10000){compose.onAllNodesWithTag("note-bin").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText("Empty",substring=false).assertExists()
        compose.onNodeWithTag("notes-search").performTextInput("Alpha")
        compose.onNodeWithTag("note-a").assertDoesNotExist()
        compose.onNodeWithTag("note-bin").assertExists()
    }
    @Test fun selectionActionsAnimateInTheSameBottomRow() {
        launch()
        val initial = compose.onNodeWithTag("notes-bottom-actions").fetchSemanticsNode().boundsInRoot
        compose.mainClock.autoAdvance = false
        hold("a")
        compose.mainClock.advanceTimeBy(80)
        // Both contents exist during the transition, inside one shared bottom slot.
        compose.onNodeWithTag("notes-action-bar").assertExists()
        compose.onNodeWithTag("notes-search-row").assertExists()
        compose.mainClock.advanceTimeBy(400)
        compose.onNodeWithContentDescription("Search notes").assertDoesNotExist()
        compose.onNodeWithContentDescription("New note").assertDoesNotExist()
        val selected = compose.onNodeWithTag("notes-bottom-actions").fetchSemanticsNode().boundsInRoot
        assertEquals(initial.top, selected.top, 1f)
        assertEquals(initial.bottom, selected.bottom, 1f)
        compose.onNodeWithContentDescription("Clear selection").performClick()
        compose.mainClock.advanceTimeBy(80)
        compose.onNodeWithTag("notes-action-bar").assertExists()
        compose.onNodeWithTag("notes-search-row").assertExists()
        compose.mainClock.advanceTimeBy(400)
        compose.onNodeWithTag("notes-action-bar").assertDoesNotExist()
        compose.onNodeWithContentDescription("Search notes").assertIsDisplayed()
        compose.onNodeWithContentDescription("New note").assertIsDisplayed()
        compose.mainClock.autoAdvance = true
    }
    @Test fun holdSelectsAndColorLabelPinPersist() {
        launch();hold("a")
        compose.onNodeWithContentDescription("Note color").performClick()
        compose.onNodeWithText("Blue",substring=false).performClick()
        compose.waitUntil(10000){saved("a").color=="blue"}
        hold("a");compose.onNodeWithContentDescription("Manage labels").performClick()
        compose.onNodeWithContentDescription("Select label Work").performClick()
        compose.waitUntil(10000){label.id in saved("a").tags}
        androidx.test.espresso.Espresso.pressBack()
        hold("a");compose.onNodeWithContentDescription("Pin selected").performClick()
        compose.waitUntil(10000){saved("a").pinned}
        compose.onNodeWithText("Undo",substring=false).assertDoesNotExist()
        val packed=NotesBackup.pack(runBlocking{NotesBackup.snapshot(context)},null)
        assertEquals("blue",NotesBackup.unpack(packed.first,null,packed.second).notes.first{it.id=="a"}.color)
    }
    @Test fun labelSelectionIsSeparateFromEditingAndAdding() {
        launch();hold("a");compose.onNodeWithContentDescription("Manage labels").performClick()
        compose.onNodeWithContentDescription("Select label Work").assertIsOff().performClick()
        compose.waitUntil(10000){label.id in saved("a").tags}
        compose.onNodeWithContentDescription("Select label Work").assertIsOn().performClick()
        compose.waitUntil(10000){label.id !in saved("a").tags}
        compose.onNodeWithText("Work",substring=false).performClick()
        compose.onNodeWithContentDescription("Delete label; keep notes").assertExists()
        compose.onNode(hasSetTextAction() and hasText("Work")).performTextReplacement("Personal")
        compose.onNodeWithContentDescription("Save name").performClick()
        compose.waitUntil(10000){compose.onAllNodesWithContentDescription("Select label Personal").fetchSemanticsNodes().isNotEmpty()}
        assertTrue(saved("a").tags.isEmpty())
        compose.onNodeWithContentDescription("Add label").performClick()
        compose.onNodeWithContentDescription("Delete label; keep notes").assertDoesNotExist()
        compose.onNode(hasSetTextAction() and hasText("Label")).performTextInput("Home")
        compose.onNodeWithContentDescription("Save name").performClick()
        compose.waitUntil(10000){compose.onAllNodesWithContentDescription("Select label Home").fetchSemanticsNodes().isNotEmpty()}
    }
    @Test fun onlyDeletionOffersUndoAndExpires() {
        launch();hold("a");compose.onNodeWithContentDescription("Move selected to Bin").performClick()
        compose.waitUntil(10000){saved("a").deletedAt!=null}
        compose.onNodeWithText("Deleted",substring=false).assertExists()
        compose.onNodeWithText("Undo",substring=false).performClick()
        compose.waitUntil(10000){saved("a").deletedAt==null}
        compose.waitUntil(10000){compose.onAllNodesWithTag("note-a").fetchSemanticsNodes().isNotEmpty()}
        hold("a");compose.onNodeWithContentDescription("Move selected to Bin").performClick()
        compose.waitUntil(10000){saved("a").deletedAt!=null}
        compose.waitUntil(8000){compose.onAllNodesWithText("Undo",substring=false).fetchSemanticsNodes().isEmpty()}
        compose.onNodeWithText("Deleted",substring=false).assertDoesNotExist()
    }
    @Test fun heldDragMakesRoomBeforeRelease()=livePreview(false)
    @Test fun gridHeldDragMakesRoomBeforeRelease()=livePreview(true)
    @Test fun thumbReachListDragKeepsCardUnderFingerAndReservesVisibleSlot()=livePreview(false,true)
    @Test fun thumbReachGridDragKeepsCardUnderFingerAndReservesVisibleSlot()=livePreview(true,true)
    private fun livePreview(grid:Boolean,thumbReach:Boolean=false) {
        runBlocking {NotesStore(NotesDatabase.get(context)).replace(fixture(grid).copy(notes=fixture().notes.map{if(it.id=="a")it.copy(content=NoteContent("Alpha","One\nTwo\nThree\nFour"))else it}))}
        launch()
        if(thumbReach)pullForThumbReach()
        val originalA=compose.onNodeWithTag("note-a").fetchSemanticsNode().boundsInRoot
        val originalB=compose.onNodeWithTag("note-b").fetchSemanticsNode().boundsInRoot
        compose.onRoot().performTouchInput {
            down(originalA.center);advanceEventTime(750)
            moveTo(originalB.center);advanceEventTime(32)
        }
        compose.mainClock.advanceTimeBy(600)
        compose.waitForIdle()
        val shiftedB=compose.onNodeWithTag("note-b").fetchSemanticsNode().boundsInRoot
        assertTrue("Neighbor must make room while finger is still down: $originalB -> $shiftedB",shiftedB.top<originalB.top-20f)
        val slot=compose.onNodeWithTag("note-slot-a").fetchSemanticsNode().boundsInRoot
        assertEquals("Reserve the full height of the dragged note",originalA.height,slot.height,1f)
        assertTrue("Neighbor must stay outside the reserved slot",shiftedB.bottom<=slot.top)
        val heldA=compose.onNodeWithTag("note-a").fetchSemanticsNode().boundsInRoot
        assertEquals("Held note must remain under the finger",originalB.center.y,heldA.center.y,8f)
        val screenshot=InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        java.io.File(context.cacheDir,"notes-held-${if(thumbReach)"reach-" else ""}${if(grid)"grid" else "list"}.png").outputStream().use{screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
        screenshot.recycle()
        compose.onRoot().performTouchInput{up()}
        compose.waitUntil(10000){saved("b").order<saved("a").order}
        compose.waitUntil(5000){compose.onAllNodesWithTag("note-slot-a").fetchSemanticsNodes().isEmpty()}
        assertEquals("Released card must settle in its visible slot",slot.top,compose.onNodeWithTag("note-a").fetchSemanticsNode().boundsInRoot.top,2f)
    }
    @Test fun draggingOverAnotherCardKeepsBackgroundOpaque() {
        runBlocking {NotesStore(NotesDatabase.get(context)).replace(fixture().copy(notes=fixture().notes.map{when(it.id){
            "a"->it.copy(content=NoteContent("Alpha","One\nTwo\nThree\nFour"))
            "b"->it.copy(color="blue")
            else->it
        }}))}
        launch()
        val original=compose.onNodeWithTag("note-a").fetchSemanticsNode().boundsInRoot
        var captures=0
        fun background(bounds:androidx.compose.ui.geometry.Rect):Int {
            val bitmap=InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
            // Bounds are root-relative; account for the activity's system-bar offset.
            val root=compose.onRoot().fetchSemanticsNode().boundsInWindow
            java.io.File(context.cacheDir,"notes-opacity-${captures++}.png").outputStream().use{bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
            return bitmap.getPixel((root.left+bounds.center.x).toInt(),(root.top+bounds.bottom-12).toInt()).also{bitmap.recycle()}
        }
        // Native activity/window transitions are outside Compose's test clock.
        InstrumentationRegistry.getInstrumentation().uiAutomation.waitForIdle(500,5000)
        val before=background(original)
        compose.onRoot().performTouchInput{down(original.center);advanceEventTime(750);moveTo(original.center+androidx.compose.ui.geometry.Offset(0f,70f))}
        compose.mainClock.advanceTimeBy(200);compose.waitForIdle()
        val during=compose.onNodeWithTag("note-a").fetchSemanticsNode().boundsInRoot
        assertEquals(original.top+70f,during.top,2f)
        assertEquals("Underlying blue card must not show through",before,background(during))
        compose.onRoot().performTouchInput{cancel()}
    }
    private fun drag(grid:Boolean) {
        launch(grid)
        val from=compose.onNodeWithTag("note-a").fetchSemanticsNode().boundsInRoot.center
        val to=compose.onNodeWithTag("note-b").fetchSemanticsNode().boundsInRoot.center
        compose.onRoot().performTouchInput{
            down(from);advanceEventTime(750)
            for(i in 1..12){moveTo(from+(to-from)*(i/12f));advanceEventTime(24)}
            up()
        }
        compose.waitUntil(10000){saved("b").order<saved("a").order}
        compose.onNodeWithContentDescription("Note color").assertDoesNotExist()
        assertTrue(saved("pin").pinned)
        assertEquals("manual",runBlocking{NotesBackup.snapshot(context).preferences.sort})
        activity?.recreate()
        compose.waitUntil(10000){compose.onAllNodesWithTag("note-b").fetchSemanticsNodes().isNotEmpty()}
        val a=compose.onNodeWithTag("note-a").fetchSemanticsNode().boundsInRoot
        val b=compose.onNodeWithTag("note-b").fetchSemanticsNode().boundsInRoot
        assertTrue(if(grid) b.top<a.top || b.left<a.left else b.top<a.top)
    }
    @Test fun listDragReordersWithoutSelecting()=drag(false)
    @Test fun gridDragReordersWithoutSelecting()=drag(true)
    @Test fun cancelledDragDoesNotPersistOrderOrSelect() {
        launch()
        val from=compose.onNodeWithTag("note-a").fetchSemanticsNode().boundsInRoot.center
        val to=compose.onNodeWithTag("note-b").fetchSemanticsNode().boundsInRoot.center
        compose.onRoot().performTouchInput{down(from);advanceEventTime(750);moveTo(to);advanceEventTime(100);cancel()}
        assertEquals(1L,saved("a").order)
        assertEquals(2L,saved("b").order)
        compose.onNodeWithContentDescription("Note color").assertDoesNotExist()
    }
    @Test fun insertAndTapInlineCheckboxAlongsideText() {
        launch();compose.onNodeWithTag("note-a").performClick()
        compose.waitUntil(10000){compose.onAllNodesWithTag("notes-editor").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("notes-editor").performTextReplacement("Mixed\nBefore\n[ ] Milk\nAfter")
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        val layouts=mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
        compose.onNodeWithTag("notes-editor").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult){it(layouts)}
        val bounds=layouts.last().getBoundingBox("Mixed\nBefore\n".length)
        compose.onNodeWithTag("notes-editor").performTouchInput{click(bounds.center)}
        compose.waitUntil(10000){saved("a").content.text=="Before\n[x] Milk\nAfter"}
        compose.onNodeWithContentDescription("Insert checklist").performScrollTo().performClick()
        compose.onNodeWithTag("notes-editor").performTextInput("New item")
        compose.waitUntil(10000){saved("a").content.text.contains("[ ] ")&&saved("a").content.text.contains("New item")}
        assertFalse(saved("a").content.checklist)
    }
    @Test fun legacyChecklistOpensAsMixedNoteWithoutLosingChecks() {
        runBlocking {NotesStore(NotesDatabase.get(context)).replace(fixture().copy(notes=listOf(Note(id="a",content=NoteContent(title="Old list",text="Prose",checklist=true,items=listOf(NoteItem(text="One",checked=true),NoteItem(text="Two")))))))}
        activity=ActivityScenario.launch(NotesActivity::class.java)
        compose.waitUntil(10000){compose.onAllNodesWithTag("note-a").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("note-a").performClick()
        compose.waitUntil(10000){!saved("a").content.checklist}
        assertEquals("Prose\n[x] One\n[ ] Two",saved("a").content.text)
        compose.onNodeWithTag("notes-editor").assertTextContains("Old list\nProse\n[x] One\n[ ] Two")
    }
}
