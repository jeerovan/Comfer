package com.jeerovan.comfer.notes

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*

class NotesUiTest {
    @get:Rule val compose=createAndroidComposeRule<NotesActivity>()
    private fun blankEditor() {
        compose.waitUntil(10000){compose.onAllNodesWithContentDescription("New note").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithContentDescription("New note").performClick()
        compose.waitUntil(10000){compose.onAllNodesWithTag("notes-editor").fetchSemanticsNodes().isNotEmpty()}
    }
    @Test fun autosaveCollectionSearchAndEditorRoundTrip() {
        val marker="UI note ${System.nanoTime()}"
        blankEditor()
        compose.onNodeWithContentDescription("Close editor").assertDoesNotExist()
        compose.onNodeWithTag("notes-editor").performTextReplacement(marker)
        compose.waitUntil(10000){compose.onAllNodesWithText("Saved",useUnmergedTree=true).fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithContentDescription("All notes").performClick()
        compose.waitUntil(10000){compose.onAllNodesWithTag("notes-search").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("notes-search").performTextInput(marker)
        compose.waitUntil(10000){compose.onAllNodesWithText(marker,substring=true).fetchSemanticsNodes().size>=2}
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val note=runBlocking{NotesBackup.snapshot(context).notes.first{it.content.title==marker}}
        compose.onNodeWithTag("note-${note.id}").performClick()
        compose.waitUntil(5000){compose.onAllNodesWithTag("notes-editor").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("notes-editor").assertTextContains(marker)
        compose.onNodeWithContentDescription("All notes").performClick()
    }
    @Test fun longNoteAutosavesWithoutTruncation() {
        blankEditor()
        val text="word ".repeat(20_000)
        compose.onNodeWithTag("notes-editor").performTextReplacement("Long note\n"+text)
        compose.waitUntil(30000){compose.onAllNodesWithText("Saved",useUnmergedTree=true).fetchSemanticsNodes().isNotEmpty()}
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        assertTrue(runBlocking{NotesBackup.snapshot(context).notes.any{it.content.text==text}})
        compose.onNodeWithContentDescription("All notes").performClick()
        compose.waitUntil(10000){compose.onAllNodesWithTag("notes-search").fetchSemanticsNodes().isNotEmpty()}
    }
    @Test fun canvasSplitsTitleAndBodyAndUndoRestoresBoth() {
        blankEditor()
        val marker="Canvas ${System.nanoTime()}"
        compose.onNodeWithTag("notes-editor").performTextReplacement("$marker\nBody line\nLast line")
        compose.waitUntil(10000){compose.onAllNodesWithText("Saved",useUnmergedTree=true).fetchSemanticsNodes().isNotEmpty()}
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val saved=runBlocking{NotesBackup.snapshot(context).notes.first{it.content.title==marker}}
        assertEquals("Body line\nLast line",saved.content.text)
        compose.onNodeWithTag("notes-editor").performTextReplacement("Merged title")
        compose.onNodeWithContentDescription("Undo edit").performClick()
        compose.onNodeWithTag("notes-editor").assertTextContains("$marker\nBody line\nLast line")
        compose.onNodeWithContentDescription("All notes").performClick()
        compose.waitUntil(10000){compose.onAllNodesWithTag("notes-search").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("note-${saved.id}").performClick()
        compose.waitUntil(10000){compose.onAllNodesWithTag("notes-editor").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("notes-editor").assertTextContains("$marker\nBody line\nLast line")
    }
    @Test fun mixedChecklistAndProseAutosaveWithoutSeparateType() {
        blankEditor()
        compose.onNodeWithTag("notes-editor").performTextReplacement("Mixed note\nBefore\n[ ] Item\nAfter")
        val actions=compose.onNodeWithTag("notes-editor").fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsActions.CustomActions]
        compose.runOnIdle { actions.first{it.label.startsWith("Check item")}.action() }
        compose.waitUntil(10000){compose.onAllNodesWithText("Saved",useUnmergedTree=true).fetchSemanticsNodes().isNotEmpty()}
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val saved=runBlocking{NotesBackup.snapshot(context).notes.first{it.content.title=="Mixed note"}}
        assertFalse(saved.content.checklist)
        assertEquals("Before\n[x] Item\nAfter",saved.content.text)
        compose.onNodeWithContentDescription("All notes").performClick()
    }
}
