package com.jeerovan.comfer.journals

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*

class JournalActivityTest {
    @get:Rule val compose = createEmptyComposeRule()
    private var scenario: ActivityScenario<JournalActivity>? = null
    private lateinit var previous: JournalLocalSnapshot
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    @Before fun setup() {
        check(context.packageName.endsWith(".notificationtest"))
        runBlocking {
            com.jeerovan.comfer.StartupCoordinator.awaitReady()
            previous = JournalArchiveMedia.localSnapshot(context)
            JournalArchiveMedia.replaceLocal(context, JournalLocalSnapshot(emptyList(), emptyList()))
        }
        runBlocking { JournalProtection.setEnabled(context, false) }
        context.getSharedPreferences("journal-position", 0).edit().clear().commit()
        scenario = ActivityScenario.launch(JournalActivity::class.java)
        // Synthetic fixture only: allow screenshot verification without weakening app privacy.
        scenario!!.onActivity { it.window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE) }
        compose.waitUntil(10000) { compose.onAllNodes(hasTestTag("journal-composer") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
    }
    @After fun cleanup() {
        scenario?.close()
        runBlocking { JournalArchiveMedia.replaceLocal(context, previous) }
    }
    @Test fun reopeningShowsNewPromptAndLatestEntryAtBottom() {
        val dao = JournalDatabase.get(context).dao()
        val firstPrompt = runBlocking { dao.draft()!!.prompt }
        val prompts = listOf(com.jeerovan.comfer.R.string.journal_prompt_0, com.jeerovan.comfer.R.string.journal_prompt_1, com.jeerovan.comfer.R.string.journal_prompt_2, com.jeerovan.comfer.R.string.journal_prompt_3)
        compose.onNodeWithText(context.getString(prompts[firstPrompt])).assertIsDisplayed()
        compose.onNodeWithText("Journal entry").assertDoesNotExist()
        scenario!!.close()
        runBlocking { repeat(30) { dao.insert(JournalEntry(id = "opening-$it", text = "Entry $it", createdAt = it.toLong())) } }
        context.getSharedPreferences("journal-position", 0).edit().putInt("index", 0).putInt("offset", 0).commit()
        scenario = ActivityScenario.launch(JournalActivity::class.java)
        compose.waitUntil(10000) { runBlocking { dao.draft()!!.prompt != firstPrompt } }
        compose.waitUntil(10000) { compose.onAllNodesWithText("Entry 29").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Entry 29").assertIsDisplayed()
        compose.onNodeWithText("Entry 0").assertDoesNotExist()
        val latest = compose.onNodeWithTag("journal-time-opening-29").fetchSemanticsNode().boundsInRoot
        val feed = compose.onNodeWithTag("journal-feed").fetchSemanticsNode().boundsInRoot
        // The timestamp is the last element in the card. Compare its trailing
        // edge using the card/feed padding in dp, not a text-to-feed pixel gap
        // that changes with density and the timestamp's height.
        val bottomPadding = 32f * context.resources.displayMetrics.density
        assertEquals("Latest entry must occupy the bottom of the feed", feed.bottom - bottomPadding, latest.bottom, 2f)
        val older = compose.onNodeWithText("Entry 28").fetchSemanticsNode().boundsInRoot
        val newestText = compose.onNodeWithText("Entry 29").fetchSemanticsNode().boundsInRoot
        assertTrue("Older entries must appear above the latest entry", older.bottom < newestText.top)
        val secondPrompt = runBlocking { dao.draft()!!.prompt }
        compose.onNodeWithText(context.getString(prompts[secondPrompt])).assertIsDisplayed()
        scenario!!.recreate()
        compose.waitForIdle()
        assertEquals("Rotation must not change the prompt", secondPrompt, runBlocking { dao.draft()!!.prompt })
    }

    @Test fun actualActivityKeepsComposerAndActionsAboveKeyboard() {
        compose.onNodeWithTag("journal-composer").performTextInput((1..12).joinToString("\n") { "Synthetic line $it" })
        compose.onNodeWithTag("journal-submit").assertIsDisplayed()
        compose.onNodeWithText("Journal", substring = false).assertIsDisplayed()
        val bounds = compose.onNodeWithTag("journal-submit").fetchSemanticsNode().boundsInRoot
        assertTrue(bounds.top >= 0)
        compose.waitForIdle()
        InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()?.let { bitmap ->
            java.io.File(context.filesDir, "journal-activity-keyboard.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
        }
        compose.onNodeWithTag("journal-submit").performClick()
        compose.waitUntil(10000) { runBlocking { JournalDatabase.get(context).dao().exportEntries().size == 1 } }
        assertTrue(runBlocking { JournalDatabase.get(context).dao().draft()!!.text.isEmpty() })
    }

    @Test fun recreationKeepsDraftWithoutOpeningDiscardDialog() {
        compose.onNodeWithTag("journal-composer").performTextInput("Rotation draft")
        scenario!!.recreate()
        compose.waitUntil(10000) { compose.onAllNodes(hasTestTag("journal-composer") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("journal-composer").assertTextContains("Rotation draft")
        compose.onNodeWithText("Save your changes?").assertDoesNotExist()
    }

    @Test fun landscapeKeyboardKeepsSubmitReachable() {
        scenario!!.onActivity { it.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
        compose.waitUntil(10000) { context.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE }
        compose.waitUntil(10000) { compose.onAllNodes(hasTestTag("journal-composer") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("journal-composer").performTextInput("Landscape draft")
        compose.onNodeWithTag("journal-submit").assertIsDisplayed()
        compose.onNodeWithText("Journal", substring = false).assertIsDisplayed()
        scenario!!.onActivity { it.window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE) }
        compose.waitForIdle()
        InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()?.let { bitmap ->
            java.io.File(context.filesDir, "journal-activity-landscape.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
        }
        compose.onNodeWithTag("journal-submit").performClick()
        compose.waitUntil(10000) { runBlocking { JournalDatabase.get(context).dao().exportEntries().size == 1 } }
    }

    @Test fun protectJournalsRequiresConfiguredDeviceCredentials() {
        compose.onNodeWithContentDescription("Journal options").performClick()
        compose.onNodeWithText("Settings", substring = false).performClick()
        compose.onNodeWithText("Protect Journals").assertIsDisplayed()
        compose.onNode(isToggleable()).performClick()
        if (context.getSystemService(android.app.KeyguardManager::class.java).isDeviceSecure) {
            awaitCredentialPromptAndCancel()
        } else compose.onNodeWithText(context.getString(com.jeerovan.comfer.R.string.journal_lock_setup)).assertIsDisplayed()
        assertFalse(runBlocking { JournalProtection.requiresAuthentication(context) })
    }

    @Test fun protectionEnabledWhileAwayRequiresAuthenticationOnReturn() {
        scenario!!.moveToState(androidx.lifecycle.Lifecycle.State.CREATED)
        runBlocking { val dao = JournalDatabase.get(context).rawDao(); dao.state(dao.storageState()!!.copy(moduleLocked = true)) }
        JournalProtection.restoreState(context, true)
        scenario!!.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
        if (context.getSystemService(android.app.KeyguardManager::class.java).isDeviceSecure) {
            awaitCredentialPromptAndCancel()
        } else {
            compose.waitUntil(10000) { compose.onAllNodesWithText(context.getString(com.jeerovan.comfer.R.string.journal_lock_unavailable)).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("journal-composer").assertDoesNotExist()
        }
        runBlocking { val dao = JournalDatabase.get(context).rawDao(); dao.state(dao.storageState()!!.copy(moduleLocked = false)) }
        JournalProtection.restoreState(context, false)
    }
    private fun awaitCredentialPromptAndCancel() {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        compose.waitUntil(10000) {
            automation.rootInActiveWindow?.findAccessibilityNodeInfosByText("Unlock Journal")?.isNotEmpty() == true
        }
        android.os.ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand("input keyevent 4")).use { it.readBytes() }
        compose.waitUntil(10000) { scenario!!.state == androidx.lifecycle.Lifecycle.State.DESTROYED }
    }
}
