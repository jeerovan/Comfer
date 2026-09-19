package com.jeerovan.comfer.tasks

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.jeerovan.comfer.StartupCoordinator
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*

class TaskGuideUiTest {
    @get:Rule val compose = createComposeRule()
    private fun show(tasks: List<TaskItem> = emptyList(), listGuideShown: Boolean = true) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        runBlocking {
            check(context.packageName.endsWith(".notificationtest"))
            StartupCoordinator.awaitReady()
            TaskStore.exclusive(context) { TaskStore.write(context, TaskSnapshot(tasks = tasks, preferences = TaskPreferences(listGuideShown = listGuideShown))) }
        }
        compose.setContent { MaterialTheme { TasksScreen({}) } }
        compose.waitUntil(10000) { compose.onAllNodesWithContentDescription("Add").fetchSemanticsNodes().isNotEmpty() }
    }
    private fun capture(name: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        java.io.File(instrumentation.targetContext.getExternalFilesDir(null), name).outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
    private fun add(title: String) {
        compose.onNodeWithContentDescription("Add").performClick()
        compose.onNodeWithTag("task-title").performTextInput(title)
        compose.onNodeWithTag("task-save").performClick()
        compose.waitUntil(10000) { TaskStore.state.value.tasks.any { it.title == title } }
    }
    @Test fun creationShowsSwipeThenReorderOnceWithoutChangingTasks() {
        show()
        compose.onNodeWithTag("tasks-guide-swipe").assertDoesNotExist()
        add("First")
        compose.onNodeWithTag("tasks-guide-swipe").assertIsDisplayed()
        capture("task-swipe-guide.png")
        val first = TaskStore.state.value.tasks.single()
        compose.waitUntil(15000) { TaskStore.state.value.preferences.swipeGuideShown }
        compose.onNodeWithTag("tasks-guide-swipe").assertDoesNotExist()
        assertEquals(first, TaskStore.state.value.tasks.single())
        add("Second")
        compose.onNodeWithTag("tasks-guide-reorder").assertIsDisplayed()
        capture("task-reorder-guide.png")
        val before = TaskStore.state.value.tasks
        compose.waitUntil(15000) { TaskStore.state.value.preferences.reorderGuideShown }
        assertEquals(before, TaskStore.state.value.tasks)
        compose.onNodeWithContentDescription("Task options").performClick()
        androidx.test.espresso.Espresso.pressBack()
        compose.onNodeWithTag("tasks-guide-swipe").assertDoesNotExist()
        compose.onNodeWithTag("tasks-guide-reorder").assertDoesNotExist()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        runBlocking {
            val saved = TaskDatabase.get(context).dao().preferences()!!
            assertTrue(saved.swipeGuideShown && saved.reorderGuideShown)
        }
    }
    @Test fun guideDoesNotBlockTaskTap() {
        show(listOf(TaskItem(id = "one", listId = "tasks", title = "Tap through")))
        compose.onNodeWithTag("tasks-guide-swipe").assertIsDisplayed()
        compose.onNodeWithTag("tasks-guide-swipe").performTouchInput { click() }
        compose.onNodeWithTag("task-title").assertIsDisplayed()
        assertEquals("Tap through", TaskStore.state.value.tasks.single().title)
    }
    @Test fun firstOpenGuidesListNameAndTapOpensRenameDelete() {
        show(listGuideShown = false)
        compose.onNodeWithTag("tasks-guide-list").assertIsDisplayed()
        compose.onNodeWithTag("tasks-guide-swipe").assertDoesNotExist()
        capture("task-list-guide.png")
        compose.onNodeWithTag("tasks-guide-list").performTouchInput { click() }
        compose.onNodeWithTag("tasks-list-name").assertTextContains("Tasks")
        compose.onNodeWithContentDescription("Delete list").assertIsNotEnabled()
        compose.waitUntil(10000) { TaskStore.state.value.preferences.listGuideShown }
        androidx.test.espresso.Espresso.pressBack()
        compose.onNodeWithTag("tasks-guide-list").assertDoesNotExist()
        assertEquals("Tasks", TaskStore.state.value.lists.single().name)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        runBlocking { assertTrue(TaskDatabase.get(context).dao().preferences()!!.listGuideShown) }
    }
    @Test fun listHintFinishesWithoutRequiringAnEdit() {
        show(listGuideShown = false)
        compose.onNodeWithTag("tasks-guide-list").assertIsDisplayed()
        compose.waitUntil(15000) { TaskStore.state.value.preferences.listGuideShown }
        compose.onNodeWithTag("tasks-guide-list").assertDoesNotExist()
        assertEquals("Tasks", TaskStore.state.value.lists.single().name)
    }
    @Test fun tapHandPulsesOnTheListName() = verifyMotion(TaskGuide.LIST)
    @Test fun swipeHandTravelsSideways() = verifyMotion(TaskGuide.SWIPE)
    @Test fun reorderHandHoldsThenTravelsVertically() = verifyMotion(TaskGuide.REORDER)
    private fun verifyMotion(kind: TaskGuide) {
        // Disable auto-advance before composing: the test policy cancels infinite
        // animations created while auto-advance is enabled.
        compose.mainClock.autoAdvance = false
        compose.setContent { MaterialTheme {
            TaskGestureGuide(kind, 120f, androidx.compose.ui.Modifier, onShown = {})
        } }
        compose.mainClock.advanceTimeByFrame()
        val positions = (0..10).map {
            compose.mainClock.advanceTimeBy(200)
            val bounds = compose.onNodeWithTag("tasks-guide-${kind.name.lowercase()}").fetchSemanticsNode().boundsInRoot
            when(kind) { TaskGuide.SWIPE -> bounds.center.x; TaskGuide.REORDER -> bounds.center.y; TaskGuide.LIST -> bounds.width }
        }
        assertTrue("Hand positions: $positions", positions.max() - positions.min() > if(kind == TaskGuide.LIST) 1f else 10f)
    }

}
