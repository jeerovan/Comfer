package com.jeerovan.comfer

import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso
import androidx.test.platform.app.InstrumentationRegistry
import com.jeerovan.comfer.tasks.*
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.time.LocalDate

/** Media capture through shipped screens; the capture body is skipped unless explicitly enabled. */
class PlayListingCaptureTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun captureListingScenes() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("playListingCapture") == "true")
        val context = compose.activity
        check(context.packageName.endsWith(".notificationtest"))
        compose.waitUntil(30000) { StartupCoordinator.isReady }
        val settings = PreferenceManager.snapshotForBackup()
        val tasks = runBlocking { TaskStore.snapshot(context) }
        val fixture = mutableMapOf(
            PreferenceManager.QUICK_APPS_LAYOUT to "circular",
            PreferenceManager.APP_DRAWER_LAYOUT to "circular",
            PreferenceManager.FEEDBACK_DIALOG to "true",
            PreferenceManager.TOP_BAR_VISIBLE to "true",
            PreferenceManager.MONOCHROME to "true",
            "time_font_size" to "82", "date_font_size" to "22",
            "time_font_color" to android.graphics.Color.rgb(22, 39, 53).toString(),
            "date_font_color" to android.graphics.Color.rgb(22, 39, 53).toString(),
            "time_font_name" to "Inter", "date_font_name" to "Inter",
            "time_font_weight" to "Normal",
        )
        HomeGuideStep.entries.forEach { fixture[it.preferenceKey] = "true" }
        listOf("app_drawer_circular_horizontal_swipe", "double_tap_circular_drawer",
            "app_drawer_circular_vertical_swipe", "circular_drawer_long_press_gesture",
            "search_swipe_down_gesture_key", "search_tab_swipe_gesture_key",
            "contact_swipe_gesture_key", "contact_double_tap_key").forEach { fixture[it] = "true" }
        val temporaryImages = listOf("comfer_white.jpg" to android.graphics.Color.WHITE,
            "comfer_black.jpg" to android.graphics.Color.BLACK).map { (name, color) ->
            val file = File(context.filesDir, name)
            val previous = if (file.exists()) file.readBytes() else null
            val image = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
            image.eraseColor(color)
            file.outputStream().use { image.compress(Bitmap.CompressFormat.JPEG, 100, it) }
            file to previous
        }
        try {
            runBlocking { PreferenceManager.replaceSnapshot(context, fixture) }
            compose.activityRule.scenario.recreate()
            home()
            capture("01-home")
            compose.onNodeWithTag("home-gesture-surface").performTouchInput { swipeUp() }
            compose.waitForIdle()
            capture("02-app-drawer")
            back()
            home()
            compose.onNodeWithContentDescription("Workspace").performClick()
            compose.onNodeWithTag("workspace-module-search").assertIsDisplayed()
            capture("03-workspace")
            compose.onNodeWithContentDescription("Search").performClick()
            compose.waitForIdle()
            Espresso.closeSoftKeyboard()
            compose.onNodeWithText("C", useUnmergedTree = true).performClick()
            capture("04-search")
            back()
            home()
            val today = LocalDate.now().toEpochDay()
            runBlocking {
                TaskStore.change(context) {
                    TaskSnapshot(
                        lists = listOf(TaskList("listing-day", "My day")),
                        tasks = listOf(
                            TaskItem("listing-1", "listing-day", "Plan a weekend walk", starred = true, day = today, reminder = false, position = 0),
                            TaskItem("listing-2", "listing-day", "Pick up groceries", day = today, reminder = false, position = 1),
                            TaskItem("listing-3", "listing-day", "Read a chapter", day = today, reminder = false, position = 2),
                            TaskItem("listing-4", "listing-day", "Water the plants", day = today, reminder = false, completedAt = System.currentTimeMillis(), position = 3),
                        ),
                        preferences = TaskPreferences(selectedList = "listing-day", guidanceDismissed = true,
                            swipeGuideShown = true, reorderGuideShown = true, listGuideShown = true),
                    )
                }
            }
            ActivityScenario.launch<TasksActivity>(Intent(context, TasksActivity::class.java)).use {
                compose.waitUntil(15000) {
                    compose.onAllNodesWithText("Plan a weekend walk").fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
                }
                capture("05-tasks")
            }
        } finally {
            try {
                runBlocking {
                    TaskStore.change(context) { tasks }
                    val restored = TaskStore.snapshot(context)
                    check(restored.copy(preferences = restored.preferences.copy(revision = tasks.preferences.revision)) == tasks)
                }
            } finally {
                try {
                    runBlocking { PreferenceManager.replaceSnapshot(context, settings) }
                    check(PreferenceManager.snapshotForBackup() == settings)
                } finally {
                    temporaryImages.forEach { (file, bytes) ->
                        if (bytes == null) file.delete() else file.writeBytes(bytes)
                    }
                }
            }
        }
    }

    private fun home() {
        compose.waitUntil(20000) {
            compose.onAllNodesWithContentDescription("Workspace").fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
        }
        compose.waitForIdle()
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        File(compose.activity.getExternalFilesDir(null), "play-listing/$name.png").apply {
            parentFile!!.mkdirs()
            outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        }
    }

    private fun back() {
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
    }
}
