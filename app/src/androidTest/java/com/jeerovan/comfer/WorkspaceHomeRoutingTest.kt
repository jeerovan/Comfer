package com.jeerovan.comfer

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.espresso.Espresso
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.*
import org.junit.Assert.*

class WorkspaceHomeRoutingTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val previous = mutableMapOf<String, Boolean?>()
    private var layout: String? = null
    private fun shell(command: String) = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command).use {
        android.os.ParcelFileDescriptor.AutoCloseInputStream(it).bufferedReader().readText()
    }
    @Before fun setup() {
        val context = compose.activity
        check(context.packageName.endsWith(".notificationtest"))
        compose.waitUntil(30000) { StartupCoordinator.isReady }
        layout = PreferenceManager.getQuickAppsLayout(context)
        for (key in HomeGuideStep.entries.map { it.preferenceKey } + PreferenceManager.FEEDBACK_DIALOG) {
            previous[key] = if (PreferenceManager.hasKey(context, key)) PreferenceManager.getBoolean(context, key, false) else null
            PreferenceManager.setBoolean(context, key, true)
        }
    }
    @After fun restore() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        previous.forEach { (key, value) -> if (value == null) PreferenceManager.clear(context, key) else PreferenceManager.setBoolean(context, key, value) }
        layout?.let { PreferenceManager.setQuickAppsLayout(context, it) }
    }
    @Test fun circularRoutesAndBackCloseWorkspace() = verify("circular")
    @Test fun columnsRouteAndBackCloseWorkspace() = verify("columns")
    private fun verify(layout: String) {
        PreferenceManager.setQuickAppsLayout(compose.activity, layout)
        compose.activityRule.scenario.recreate()
        fun home() { compose.waitUntil(15000) { compose.onAllNodesWithContentDescription("Workspace").fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() } }
        fun open() { home(); compose.onNodeWithContentDescription("Workspace").performClick(); compose.onNodeWithTag("workspace-module-search").assertIsDisplayed() }
        fun resumed(name: String) { compose.waitUntil(15000) { shell("dumpsys activity activities").lineSequence().any { ("mResumedActivity" in it || "topResumedActivity" in it) && name in it } } }
        home()
        compose.onNodeWithText("Search").assertDoesNotExist()
        open()
        Espresso.pressBack()
        home()
        compose.onNodeWithTag("workspace-module-search").assertDoesNotExist()
        open()
        compose.onNodeWithContentDescription("Tasks").performClick()
        resumed("TasksActivity")
        shell("input keyevent 4")
        home()
        open()
        compose.onNodeWithContentDescription("Journal").performClick()
        resumed("JournalActivity")
        compose.waitUntil(15000) { compose.onAllNodesWithTag("journal-composer").fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
        compose.waitForIdle()
        shell("input keyevent 4")
        home()
        open()
        compose.onNodeWithContentDescription("Notes").performClick()
        resumed("NotesActivity")
        compose.waitUntil(10000) { compose.onAllNodesWithTag("notes-search").fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
        Espresso.closeSoftKeyboard()
        shell("input keyevent 4")
        home()
        resumed("MainActivity")
        open()
        compose.onNodeWithContentDescription("Search").performClick()
        compose.waitUntil(15000) { compose.onAllNodesWithText("Search").fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
        compose.onNodeWithTag("workspace-module-search").assertDoesNotExist()
        Espresso.pressBack()
        home()
    }
}
