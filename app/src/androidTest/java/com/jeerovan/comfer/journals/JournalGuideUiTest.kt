package com.jeerovan.comfer.journals

import android.app.Application
import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*

class JournalGuideUiTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val preferences get() = context.getSharedPreferences("journal_gesture_guides", Context.MODE_PRIVATE)
    private lateinit var previous: JournalSnapshot
    private lateinit var model: JournalViewModel
    private var previousStep = -1
    private val visible = mutableStateOf(true)

    @Before fun setup() {
        check(context.packageName.endsWith(".notificationtest"))
        previousStep = preferences.getInt("step", -1)
        preferences.edit().clear().commit()
        runBlocking {
            previous = JournalBackup.snapshot(context)
            JournalBackup.replace(context, JournalSnapshot(entries = emptyList(), drafts = emptyList(), media = emptyList()))
        }
        compose.runOnUiThread { model = JournalViewModel(context.applicationContext as Application) }
        compose.setContent { MaterialTheme { if (visible.value) JournalScreen(model, {}) } }
        compose.waitUntil(10000) { model.draft.value != null }
    }

    @After fun cleanup() {
        compose.runOnUiThread { visible.value = false; model.speech.interrupt() }
        compose.waitForIdle()
        runBlocking { JournalBackup.replace(context, previous) }
        preferences.edit().putInt("step", previousStep).commit()
    }

    private fun reopen() {
        compose.runOnIdle { visible.value = false }
        compose.waitForIdle()
        compose.runOnIdle { visible.value = true }
        compose.waitForIdle()
    }

    @Test fun guidesPersistAndAdvanceOnlyThroughRealActionsInOrder() {
        compose.onNodeWithTag("journal-guide-edit").assertDoesNotExist()
        compose.onNodeWithTag("journal-composer").performTextInput("Guide entry")
        compose.onNodeWithTag("journal-submit").performClick()
        compose.waitUntil(10000) { runBlocking { model.store.dao.exportEntries().size == 1 } }
        compose.onNodeWithTag("journal-guide-edit").assertIsDisplayed()
        compose.mainClock.advanceTimeBy(10000)
        compose.onNodeWithTag("journal-guide-edit").assertIsDisplayed()
        reopen()
        // Touch the actual decorative overlay: it must not intercept the entry tap.
        compose.onNodeWithTag("journal-guide-edit").performTouchInput { click() }
        compose.waitUntil(10000) { model.editing.value != null }
        compose.onNodeWithContentDescription("Cancel").performClick()
        compose.onNodeWithTag("journal-guide-delete").assertIsDisplayed()
        compose.onNodeWithText("Guide entry").performTouchInput { swipe(center, center.copy(x = center.x - 30f), 300) }
        compose.onNodeWithTag("journal-guide-delete").assertIsDisplayed()
        compose.mainClock.advanceTimeBy(10000)
        reopen()
        compose.onNodeWithTag("journal-guide-delete").assertIsDisplayed()
        compose.onNodeWithText("Guide entry").performTouchInput { swipeLeft() }
        compose.waitUntil(10000) { runBlocking { model.store.dao.exportEntries().single().deletedAt != null } }
        compose.onNodeWithTag("journal-guide-date").assertIsDisplayed()
        compose.onNodeWithTag("journal-guide-delete").assertDoesNotExist()
        compose.onNodeWithText("Undo").performClick()
        compose.onNodeWithTag("journal-guide-date").assertIsDisplayed()
        compose.mainClock.advanceTimeBy(10000)
        reopen()
        compose.onNodeWithTag("journal-guide-date").assertIsDisplayed()
        compose.onNodeWithTag("journal-date-row").performTouchInput { swipeRight() }
        compose.onNodeWithTag("journal-guide-date").assertDoesNotExist()
        reopen()
        compose.onNodeWithTag("journal-guide-edit").assertDoesNotExist()
        compose.onNodeWithTag("journal-guide-delete").assertDoesNotExist()
        compose.onNodeWithTag("journal-guide-date").assertDoesNotExist()
        assertNull(JournalGuideProgress(context).current)
    }

    @Test fun unrelatedActionsCannotSkipSteps() {
        compose.runOnIdle {
            val progress = JournalGuideProgress(context)
            progress.performed(JournalGuide.DATE)
            assertNull(progress.current)
            progress.activate()
            progress.performed(JournalGuide.DELETE)
            assertEquals(JournalGuide.EDIT, progress.current)
            progress.performed(JournalGuide.EDIT)
            progress.performed(JournalGuide.DATE)
            assertEquals(JournalGuide.DELETE, JournalGuideProgress(context).current)
            progress.activate()
            assertEquals(JournalGuide.DELETE, progress.current)
        }
    }
}
