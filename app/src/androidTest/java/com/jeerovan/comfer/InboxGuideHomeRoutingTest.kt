package com.jeerovan.comfer

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

class InboxGuideHomeRoutingTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val previous = mutableMapOf<String, Boolean?>()

    @After fun restoreOnlyGuidePreferences() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        previous.forEach { (key, value) ->
            if (value == null) PreferenceManager.clear(context, key)
            else PreferenceManager.setBoolean(context, key, value)
        }
    }

    @Test fun finalGuideCompletesOnInboxGestureAndStaysCompletedAfterRecreation() {
        compose.waitUntil(30_000) { StartupCoordinator.isReady }
        val context = compose.activity
        assumeTrue("Uses existing notification access; never grants it", MyNotificationListenerService.hasAccess(context))
        for (key in HomeGuideStep.entries.map { it.preferenceKey } + PreferenceManager.FEEDBACK_DIALOG) {
            previous[key] = if (PreferenceManager.hasKey(context, key))
                PreferenceManager.getBoolean(context, key, false) else null
            PreferenceManager.setBoolean(context, key, key != HomeGuideStep.INBOX.preferenceKey)
        }
        compose.activityRule.scenario.recreate()
        compose.waitUntil(30_000) { compose.onAllNodesWithTag("home-inbox-guide").fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
        compose.onNodeWithTag("home-inbox-guide").assertIsDisplayed()
        val density = compose.activity.resources.displayMetrics.density
        val search = compose.onNodeWithTag("home-search-button").fetchSemanticsNode().boundsInRoot
        val surface = compose.onNodeWithTag("home-gesture-surface").fetchSemanticsNode().boundsInRoot
        val origin = Offset(search.center.x - surface.left,
            search.center.y - surface.top - maxOf(40f * density, search.height / 2 + 12f * density) - 42f * density)
        compose.onNodeWithTag("home-gesture-surface").performTouchInput {
            down(origin)
            moveBy(Offset(0f, 120f * density), 200)
            moveBy(Offset(0f, -120f * density), 200)
            up()
        }
        compose.waitUntil(10_000) {
            PreferenceManager.getBoolean(context, HomeGuideStep.INBOX.preferenceKey, false)
        }
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        fun shell(command: String) = instrumentation.uiAutomation.executeShellCommand(command).use {
            android.os.ParcelFileDescriptor.AutoCloseInputStream(it).bufferedReader().readText()
        }
        compose.waitUntil(15_000) { shell("dumpsys activity activities").lineSequence().any {
            ("mResumedActivity" in it || "topResumedActivity" in it) && "NotificationInboxActivity" in it
        } }
        shell("input keyevent 4")
        compose.waitUntil(15_000) { compose.onAllNodesWithTag("home-gesture-surface").fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
        compose.onNodeWithTag("home-inbox-guide").assertDoesNotExist()
        compose.activityRule.scenario.recreate()
        compose.waitUntil(15_000) { compose.onAllNodesWithTag("home-gesture-surface").fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
        compose.onNodeWithTag("home-inbox-guide").assertDoesNotExist()
    }
}
