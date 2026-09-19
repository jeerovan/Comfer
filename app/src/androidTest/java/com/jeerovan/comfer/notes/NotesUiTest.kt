package com.jeerovan.comfer.notes

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*

class NotesUiTest {
    @get:Rule val compose=createAndroidComposeRule<NotesActivity>()
    private fun keyboardVisible():Boolean {
        var visible=false
        compose.runOnUiThread {
            visible=androidx.core.view.ViewCompat.getRootWindowInsets(compose.activity.window.decorView)
                ?.isVisible(androidx.core.view.WindowInsetsCompat.Type.ime())==true
        }
        return visible
    }
    private fun waitForSaved(timeout:Long=10000,predicate:(Note)->Boolean) {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        compose.waitUntil(timeout){runBlocking{NotesBackup.snapshot(context).notes.any(predicate)}}
        compose.onNodeWithText("Saved",useUnmergedTree=true).assertDoesNotExist()
    }
    private fun blankEditor() {
        compose.waitUntil(10000){compose.onAllNodesWithContentDescription("New note").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithContentDescription("New note").performClick()
        compose.waitUntil(10000){compose.onAllNodesWithTag("notes-editor").fetchSemanticsNodes().isNotEmpty()}
    }
    @Test fun searchStartsCollapsedAndCloseResetsItEvenWhenEmpty() {
        compose.waitUntil(10000){compose.onAllNodesWithContentDescription("New note").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("notes-search").assertDoesNotExist()
        compose.onNodeWithTag("notes-action-bar").assertDoesNotExist()
        val search=compose.onNodeWithContentDescription("Search notes").fetchSemanticsNode().boundsInRoot
        val add=compose.onNodeWithContentDescription("New note").fetchSemanticsNode().boundsInRoot
        val options=compose.onNodeWithContentDescription("Notes options").fetchSemanticsNode().boundsInRoot
        val title=compose.onNodeWithText("Notes",substring=false).fetchSemanticsNode().boundsInRoot
        assertTrue(search.right<=add.left)
        assertEquals(search.center.y,add.center.y,1f)
        assertEquals(title.center.y,options.center.y,1f)
        assertTrue(options.bottom<search.top)
        repeat(2) { attempt->
            compose.onNodeWithContentDescription("Search notes").performClick()
            compose.onNodeWithTag("notes-search").assertIsFocused()
            compose.onNodeWithContentDescription("New note").assertDoesNotExist()
            if(attempt==1)compose.onNodeWithTag("notes-search").performTextInput("missing-${System.nanoTime()}")
            compose.waitUntil(5000){keyboardVisible()}
            compose.onNodeWithContentDescription("Close search").performClick()
            compose.waitUntil(5000){!keyboardVisible()}
            compose.onNodeWithTag("notes-search").assertDoesNotExist()
            compose.onNodeWithContentDescription("New note").assertIsDisplayed()
        }
        compose.onNodeWithContentDescription("Search notes").performClick()
        compose.onNodeWithTag("notes-search").assert(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.EditableText,androidx.compose.ui.text.AnnotatedString("")))
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        androidx.test.espresso.Espresso.pressBack()
        compose.onNodeWithTag("notes-search").assertDoesNotExist()
        compose.onNodeWithContentDescription("New note").performClick()
        compose.onNodeWithTag("notes-editor").assertExists()
    }
    @Test fun autosaveCollectionSearchAndEditorRoundTrip() {
        val marker="UI note ${System.nanoTime()}"
        blankEditor()
        compose.onNodeWithContentDescription("Close editor").assertDoesNotExist()
        compose.onNodeWithContentDescription("All notes").assertDoesNotExist()
        compose.onNodeWithTag("notes-editor").performTextReplacement(marker)
        waitForSaved { it.content.title==marker }
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        androidx.test.espresso.Espresso.pressBack()
        compose.waitUntil(10000){compose.onAllNodesWithTag("notes-search-row").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithContentDescription("Search notes").performClick()
        compose.onNodeWithTag("notes-search").performTextInput(marker)
        compose.waitUntil(10000){compose.onAllNodesWithText(marker,substring=true).fetchSemanticsNodes().size>=2}
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val note=runBlocking{NotesBackup.snapshot(context).notes.first{it.content.title==marker}}
        compose.onNodeWithTag("note-${note.id}").performClick()
        compose.waitUntil(5000){compose.onAllNodesWithTag("notes-editor").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("notes-editor").assertTextContains(marker)
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        androidx.test.espresso.Espresso.pressBack()
    }
    @Test fun longNoteAutosavesWithoutTruncation() {
        blankEditor()
        val text="word ".repeat(20_000)
        compose.onNodeWithTag("notes-editor").performTextReplacement("Long note\n"+text)
        waitForSaved(30000) { it.content.title=="Long note" && it.content.text==text }
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        assertTrue(runBlocking{NotesBackup.snapshot(context).notes.any{it.content.text==text}})
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        androidx.test.espresso.Espresso.pressBack()
        compose.waitUntil(10000){compose.onAllNodesWithTag("notes-search-row").fetchSemanticsNodes().isNotEmpty()}
    }
    @Test fun canvasSplitsTitleAndBodyAndUndoRestoresBoth() {
        blankEditor()
        val marker="Canvas ${System.nanoTime()}"
        compose.onNodeWithTag("notes-editor").performTextReplacement("$marker\nBody line\nLast line")
        waitForSaved { it.content.title==marker && it.content.text=="Body line\nLast line" }
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val saved=runBlocking{NotesBackup.snapshot(context).notes.first{it.content.title==marker}}
        assertEquals("Body line\nLast line",saved.content.text)
        compose.onNodeWithTag("notes-editor").performTextReplacement("Merged title")
        compose.onNodeWithContentDescription("Undo edit").performClick()
        compose.onNodeWithTag("notes-editor").assertTextContains("$marker\nBody line\nLast line")
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        androidx.test.espresso.Espresso.pressBack()
        compose.waitUntil(10000){compose.onAllNodesWithTag("notes-search-row").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("note-${saved.id}").performClick()
        compose.waitUntil(10000){compose.onAllNodesWithTag("notes-editor").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("notes-editor").assertTextContains("$marker\nBody line\nLast line")
    }
    @Test fun mixedChecklistAndProseAutosaveWithoutSeparateType() {
        blankEditor()
        compose.onNodeWithTag("notes-editor").performTextReplacement("Mixed note\nBefore\n[ ] Item\nAfter")
        val actions=compose.onNodeWithTag("notes-editor").fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsActions.CustomActions]
        compose.runOnIdle { actions.first{it.label.startsWith("Check item")}.action() }
        waitForSaved { it.content.title=="Mixed note" && it.content.text=="Before\n[x] Item\nAfter" }
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val saved=runBlocking{NotesBackup.snapshot(context).notes.first{it.content.title=="Mixed note"}}
        assertFalse(saved.content.checklist)
        assertEquals("Before\n[x] Item\nAfter",saved.content.text)
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        androidx.test.espresso.Espresso.pressBack()
    }
}
