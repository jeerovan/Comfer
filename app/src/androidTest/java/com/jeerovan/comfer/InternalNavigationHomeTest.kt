package com.jeerovan.comfer

import android.app.Activity
import android.content.ComponentName
import android.content.pm.PackageManager
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.espresso.Espresso
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.jeerovan.comfer.journals.JournalActivity
import com.jeerovan.comfer.notes.NotesActivity
import com.jeerovan.comfer.notifications.NotificationInboxActivity
import com.jeerovan.comfer.tasks.TasksActivity
import org.junit.*
import org.junit.Assert.*

class InternalNavigationHomeTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val previous = mutableMapOf<String, Boolean?>()
    private var component: ComponentName? = null
    private var componentState = PackageManager.COMPONENT_ENABLED_STATE_DEFAULT

    @Before fun setup() {
        val context = compose.activity
        check(context.packageName.endsWith(".notificationtest"))
        compose.waitUntil(30_000) { StartupCoordinator.isReady }
        // Only unrelated guide prerequisites/feedback are supplied by this fixture.
        for (key in HomeGuideStep.entries.map { it.preferenceKey } + PreferenceManager.FEEDBACK_DIALOG) {
            previous[key] = if (PreferenceManager.hasKey(context, key)) PreferenceManager.getBoolean(context, key, false) else null
            PreferenceManager.setBoolean(context, key, key != HomeGuideStep.SETTINGS.preferenceKey)
        }
        component = ComponentName(context, SettingsActivity::class.java)
        componentState = context.packageManager.getComponentEnabledSetting(component!!)
        compose.activityRule.scenario.recreate()
        home()
    }
    @After fun restore() {
        val context = instrumentation.targetContext
        component?.let { context.packageManager.setComponentEnabledSetting(it, componentState, PackageManager.DONT_KILL_APP) }
        previous.forEach { (key, value) ->
            if (value == null) PreferenceManager.clear(context, key) else PreferenceManager.setBoolean(context, key, value)
        }
    }
    private fun resumed(type: Class<out Activity>): Activity? {
        var activity: Activity? = null
        instrumentation.runOnMainSync {
            activity = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).firstOrNull { type.isInstance(it) }
        }
        return activity
    }
    private fun home() {
        compose.waitUntil(15_000) { resumed(MainActivity::class.java) != null }
        compose.waitUntil(15_000) { compose.onAllNodesWithTag("home-gesture-surface").fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
    }
    private fun longPressHome() {
        compose.onNodeWithTag("home-gesture-surface").performTouchInput { longClick(Offset(width * .08f, height * .62f)) }
        compose.waitForIdle()
    }
    @Test fun rejectedSettingsGestureCanRetryWithoutLosingHomeOrGuide() {
        val context = compose.activity
        val homeTask = context.taskId
        context.packageManager.setComponentEnabledSetting(component!!, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
        try {
            longPressHome()
            assertNotNull("Rejected launch must leave Home resumed", resumed(MainActivity::class.java))
            assertFalse("Failed gesture must not consume Settings guide", PreferenceManager.getBoolean(context, HomeGuideStep.SETTINGS.preferenceKey, false))
        } finally {
            context.packageManager.setComponentEnabledSetting(component!!, componentState, PackageManager.DONT_KILL_APP)
        }
        longPressHome()
        compose.waitUntil(15_000) { resumed(SettingsActivity::class.java) != null }
        assertEquals(homeTask, resumed(SettingsActivity::class.java)!!.taskId)
        compose.waitUntil(5_000) { PreferenceManager.getBoolean(context, HomeGuideStep.SETTINGS.preferenceKey, false) }
        Espresso.pressBack()
        home()
        compose.onNodeWithContentDescription("Workspace").performClick()
        compose.onNodeWithTag("workspace-module-search").assertIsDisplayed()
        Espresso.pressBack()
        home()
    }
    @Test fun workspaceModulesAndInboxRetainNormalNavigation() {
        val homeTask = compose.activity.taskId
        for ((label, type) in listOf("Tasks" to TasksActivity::class.java, "Journal" to JournalActivity::class.java, "Notes" to NotesActivity::class.java)) {
            home()
            compose.onNodeWithContentDescription("Workspace").performClick()
            compose.onNodeWithContentDescription(label).performClick()
            compose.waitUntil(15_000) { resumed(type) != null }
            assertEquals(homeTask, resumed(type)!!.taskId)
            if (type == JournalActivity::class.java) compose.waitUntil(15_000) { compose.onAllNodesWithTag("journal-composer").fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
            if (type == NotesActivity::class.java) compose.waitUntil(15_000) { compose.onAllNodesWithTag("notes-search-row").fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
            Espresso.closeSoftKeyboard()
            Espresso.pressBack()
            home()
        }
        val density = compose.activity.resources.displayMetrics.density
        compose.onNodeWithTag("home-gesture-surface").performTouchInput {
            down(Offset(width * .08f, height * .62f))
            moveBy(Offset(0f, 100f * density), 200)
            moveBy(Offset(0f, -100f * density), 200)
            up()
        }
        compose.waitUntil(15_000) { resumed(NotificationInboxActivity::class.java) != null }
        assertEquals(homeTask, resumed(NotificationInboxActivity::class.java)!!.taskId)
        Espresso.pressBack()
        home()
    }
}
