package com.jeerovan.comfer.tasks

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.jeerovan.comfer.StartupCoordinator
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*

class TaskRedesignTest {
    @get:Rule val compose = createComposeRule()
    private fun show(tasks: List<TaskItem> = emptyList()) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        runBlocking { check(context.packageName.endsWith(".notificationtest")); StartupCoordinator.awaitReady(); TaskStore.exclusive(context) { TaskStore.write(context, TaskSnapshot(lists = listOf(TaskList("tasks", "Tasks"), TaskList("work", "Work", 1)), tasks = tasks, preferences = TaskPreferences(guidanceDismissed = true))) } }
        compose.setContent { MaterialTheme { TasksScreen({}) } }
        compose.waitUntil(10000) { compose.onAllNodesWithTag("tasks-list-picker").fetchSemanticsNodes().isNotEmpty() }
    }
    @Test fun emptyCompletedCardIsAbsentAcrossTaskStarredAndSearchViews() {
        show()
        compose.onNodeWithTag("tasks-completed-card").assertDoesNotExist()
        compose.onNodeWithContentDescription("Star").performClick()
        compose.onNodeWithTag("tasks-completed-card").assertDoesNotExist()
        compose.onNodeWithContentDescription("Search tasks").performClick()
        compose.onNodeWithContentDescription("Include completed").performClick()
        compose.onNodeWithTag("tasks-completed-card").assertDoesNotExist()
    }
    @Test fun completedCardUsesCurrentListStarAndSearchFilters() {
        show(listOf(TaskItem(id = "active", listId = "tasks", title = "Active", starred = true), TaskItem(id = "done", listId = "work", title = "Finished", completedAt = System.currentTimeMillis())))
        compose.onNodeWithTag("tasks-completed-card").assertDoesNotExist()
        compose.onNodeWithContentDescription("Star").performClick()
        compose.onNodeWithTag("tasks-completed-card").assertDoesNotExist()
        compose.onNodeWithContentDescription("Search tasks").performClick()
        compose.onNodeWithTag("tasks-completed-card").assertDoesNotExist()
        compose.onNodeWithContentDescription("Include completed").performClick()
        compose.onNodeWithTag("tasks-list").performScrollToNode(hasTestTag("tasks-completed-card"))
        compose.onNodeWithTag("tasks-completed-card").assertIsDisplayed()
        compose.onNodeWithTag("tasks-search-input").performTextInput("Active")
        compose.onNodeWithTag("tasks-completed-card").assertDoesNotExist()
        compose.onNodeWithContentDescription("Clear search").performClick()
        compose.onNodeWithTag("tasks-list").performScrollToNode(hasTestTag("tasks-completed-card"))
        compose.onNodeWithTag("tasks-completed-card").assertIsDisplayed()
        compose.onNodeWithContentDescription("Include completed").performClick()
        compose.onNodeWithTag("tasks-completed-card").assertDoesNotExist()
    }
    @Test fun searchCrossesListsAndToggleReturnsToSelectedList() {
        show(listOf(TaskItem(id = "other", listId = "work", title = "Elsewhere")))
        compose.onNodeWithText("Elsewhere").assertDoesNotExist()
        compose.onNodeWithContentDescription("Search tasks").performClick()
        compose.onNodeWithContentDescription("Clear search").assertDoesNotExist()
        compose.onNodeWithTag("tasks-search-input").performTextInput("Else")
        compose.onNodeWithContentDescription("Clear search").assertIsDisplayed()
        compose.onNodeWithTag("tasks-incomplete-list").performScrollToNode(hasText("Elsewhere"))
        compose.onNodeWithText("Elsewhere").assertIsDisplayed()
        compose.onNodeWithContentDescription("Search tasks").performClick()
        compose.onNodeWithText("Elsewhere").assertDoesNotExist()
    }
    @Test fun completedStartsCollapsedAndCanReopenDirectly() {
        show(listOf(TaskItem(id = "done", listId = "tasks", title = "Finished", completedAt = System.currentTimeMillis())))
        compose.onNodeWithText("Finished").assertDoesNotExist()
        compose.onNodeWithTag("tasks-list").performScrollToNode(hasTestTag("tasks-completed-toggle"))
        compose.onNodeWithTag("tasks-completed-toggle").performClick()
        compose.onNodeWithTag("tasks-completed-list").performScrollToNode(hasText("Finished"))
        compose.onNodeWithContentDescription("Reopen: Finished").performClick()
        compose.waitUntil(10000) { TaskStore.state.value.tasks.single().completedAt == null }
        compose.onNodeWithTag("tasks-completed-card").assertDoesNotExist()
    }
    @Test fun detailsMoveSheetMovesParentAndChildrenAndBackCancels() {
        show(listOf(TaskItem(id = "parent", listId = "tasks", title = "Parent"), TaskItem(id = "child", listId = "tasks", parentId = "parent", title = "Child")))
        compose.onNodeWithTag("tasks-incomplete-list").performScrollToNode(hasText("Parent"))
        compose.onNodeWithText("Parent").performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithContentDescription("Move to list").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("Move to list").performClick()
        androidx.test.espresso.Espresso.pressBack()
        assertTrue(TaskStore.state.value.tasks.all { it.listId == "tasks" })
        compose.onNodeWithContentDescription("Move to list").performClick()
        compose.onNodeWithText("Find list").assertDoesNotExist()
        compose.onNodeWithContentDescription("More options").assertDoesNotExist()
        compose.onAllNodesWithText("Work").onLast().performClick()
        assertTrue(TaskStore.state.value.tasks.all { it.listId == "tasks" })
        compose.onNodeWithTag("task-save").performClick()
        compose.waitUntil(10000) { TaskStore.state.value.tasks.all { it.listId == "work" } }
        assertEquals("parent", TaskStore.state.value.tasks.first { it.id == "child" }.parentId)
    }
    @Test fun scheduleAndRepeatStayDraftUntilSave() {
        show()
        compose.onNodeWithContentDescription("Add").performClick()
        compose.onNodeWithTag("task-title").performTextInput("Daily draft")
        compose.onNodeWithContentDescription("Schedule").performClick()
        compose.onNodeWithText("Today").performClick()
        compose.onNodeWithText("Repeat").performScrollTo().performClick()
        compose.onNodeWithText("Daily").performClick()
        assertTrue(TaskStore.state.value.tasks.isEmpty())
        compose.onNodeWithContentDescription("Done").performClick()
        compose.onNodeWithText("Daily").assertIsDisplayed()
        compose.onNodeWithText("Done").assertDoesNotExist()
        compose.onNodeWithTag("task-save").performClick()
        compose.waitUntil(10000) { TaskStore.state.value.series.size == 1 }
        assertTrue(TaskStore.state.value.tasks.any { it.title == "Daily draft" && it.day != null })
    }
    @Test fun sortUsesDueDateNotSnoozeAndPreservesManualOrder() {
        val today = java.time.LocalDate.now().toEpochDay()
        show(listOf(TaskItem(id = "late", listId = "tasks", title = "Later", day = today + 2, snoozedUntil = 1, starred = true, position = 0), TaskItem(id = "soon", listId = "tasks", title = "Sooner", day = today + 1, snoozedUntil = Long.MAX_VALUE, position = 1)))
        compose.onNodeWithContentDescription("Task options").performClick()
        compose.onNodeWithText("Reminder Date").performClick()
        compose.waitUntil(10000) { TaskStore.state.value.preferences.sort == "date" }
        compose.onNodeWithTag("tasks-incomplete-list").performScrollToNode(hasText("Sooner"))
        assertTrue(compose.onNodeWithText("Sooner").fetchSemanticsNode().boundsInRoot.top < compose.onNodeWithText("Later").fetchSemanticsNode().boundsInRoot.top)
        compose.onNodeWithTag("tasks-list").performScrollToIndex(0)
        compose.onNodeWithContentDescription("Task options").performClick()
        compose.onNodeWithText("Starred").performClick()
        compose.waitUntil(10000) { TaskStore.state.value.preferences.sort == "starred" }
        assertEquals(listOf("late", "soon"), TaskStore.state.value.tasks.sortedBy { it.position }.map { it.id })
    }
    @Test fun swipeDeleteCanBeUndoneAndParentCompletionNeedsConsent() {
        show(listOf(TaskItem(id = "solo", listId = "tasks", title = "Solo"), TaskItem(id = "parent", listId = "tasks", title = "Parent"), TaskItem(id = "child", listId = "tasks", parentId = "parent", title = "Child")))
        compose.onNodeWithTag("tasks-incomplete-list").performScrollToNode(hasText("Solo"))
        compose.onNodeWithText("Solo").performTouchInput { swipe(center, center + androidx.compose.ui.geometry.Offset(340f, 0f), 300) }
        compose.waitUntil(10000) { TaskStore.state.value.tasks.none { it.id == "solo" } }
        compose.onNodeWithText("Undo").performClick()
        compose.waitUntil(10000) { TaskStore.state.value.tasks.any { it.id == "solo" } }
        compose.onNodeWithTag("tasks-incomplete-list").performScrollToNode(hasText("Parent"))
        compose.onNodeWithContentDescription("Complete: Parent").performClick()
        assertTrue(TaskStore.state.value.tasks.all { it.completedAt == null })
        compose.onNodeWithText("Cancel").performClick()
        assertTrue(TaskStore.state.value.tasks.all { it.completedAt == null })
    }

    @Test fun unchangedDetailsCloseWithoutDiscardAndEditedDetailsAsk() {
        show(listOf(TaskItem(id = "one", listId = "tasks", title = "Unchanged")))
        compose.onNodeWithText("Unchanged").performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithTag("task-title").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("Cancel").performClick()
        compose.onNodeWithTag("tasks-list-picker").assertIsDisplayed()
        compose.onNodeWithText("Unchanged").performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithTag("task-title").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("task-title").performTextReplacement("Edited")
        compose.onNodeWithContentDescription("Cancel").performClick()
        compose.onNodeWithText("Discard").assertIsDisplayed()
        assertEquals("Unchanged", TaskStore.state.value.tasks.single().title)
    }
    @Test fun addingDoesNotShowUndoAndDeletedBannerExpires() {
        show()
        compose.onNodeWithContentDescription("Add").performClick()
        compose.onNodeWithTag("task-title").performTextInput("No saved banner")
        compose.onNodeWithTag("task-save").performClick()
        compose.waitUntil(10000) { TaskStore.state.value.tasks.size == 1 }
        compose.onNodeWithText("Undo").assertDoesNotExist()
        compose.onNodeWithTag("tasks-incomplete-list").performScrollToNode(hasText("No saved banner"))
        compose.onNodeWithText("No saved banner").performTouchInput { swipe(center, center + androidx.compose.ui.geometry.Offset(340f, 0f), 300) }
        compose.waitUntil(10000) { TaskStore.state.value.tasks.isEmpty() }
        compose.onNodeWithText("Deleted").assertIsDisplayed()
        compose.onNodeWithText("Undo").assertIsDisplayed()
        compose.waitUntil(7000) { compose.onAllNodesWithText("Deleted").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithText("Undo").assertDoesNotExist()
    }
    @Test fun completedRowsBelongToCompletedCard() {
        show(listOf(TaskItem(id = "done", listId = "tasks", title = "In the card", completedAt = System.currentTimeMillis())))
        compose.onNodeWithTag("tasks-completed-toggle").performClick()
        compose.onNode(hasText("In the card") and hasAnyAncestor(hasTestTag("tasks-completed-card"))).assertExists()
        compose.onAllNodesWithContentDescription("Reorder").assertCountEquals(0)
    }

    @Test fun newTaskCanChooseTimeBeforeDate() {
        show()
        compose.onNodeWithContentDescription("Add").performClick()
        compose.onNodeWithTag("task-title").performTextInput("Timed task")
        compose.onNodeWithContentDescription("Schedule").performClick()
        compose.onNodeWithText("Time").performClick()
        androidx.test.espresso.Espresso.onView(androidx.test.espresso.matcher.ViewMatchers.withText(android.R.string.ok)).perform(androidx.test.espresso.action.ViewActions.click())
        compose.onNodeWithContentDescription("Done").performClick()
        compose.onNodeWithTag("task-save").performClick()
        compose.waitUntil(10000) { TaskStore.state.value.tasks.size == 1 }
        assertNotNull(TaskStore.state.value.tasks.single().day)
        assertEquals(540, TaskStore.state.value.tasks.single().minute)
    }

    @Test fun dragWithinIncompleteCardPersistsNewOrder() {
        show(listOf(TaskItem(id = "first", listId = "tasks", title = "First", position = 0), TaskItem(id = "done", listId = "tasks", title = "Already done", completedAt = System.currentTimeMillis(), position = 1), TaskItem(id = "second", listId = "tasks", title = "Second", notes = "A longer row with notes to test measured drop positions", position = 2), TaskItem(id = "third", listId = "tasks", title = "Third", position = 3)))
        compose.onNode(hasText("First") and hasAnyAncestor(hasTestTag("tasks-incomplete-card"))).assertExists()
        compose.onNode(hasText("Second") and hasAnyAncestor(hasTestTag("tasks-incomplete-card"))).assertExists()
        val originalSecondTop = compose.onNodeWithText("Second").fetchSemanticsNode().boundsInRoot.top
        val distance = compose.onNodeWithText("Second").fetchSemanticsNode().boundsInRoot.center.y - compose.onNodeWithText("First").fetchSemanticsNode().boundsInRoot.center.y
        compose.onNodeWithText("First").performTouchInput {
            down(center); advanceEventTime(700)
            moveBy(androidx.compose.ui.geometry.Offset(0f, distance / 2), 150)
            moveBy(androidx.compose.ui.geometry.Offset(0f, distance / 2 + 20), 150)
        }
        compose.mainClock.advanceTimeBy(400)
        compose.onNodeWithTag("tasks-drop-target").assertIsDisplayed()
        compose.onNodeWithText("First").assertIsDisplayed()
        val screenshot = checkNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        java.io.File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "tasks-drag-preview.png").outputStream().use { screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        assertTrue(compose.onNodeWithText("Second").fetchSemanticsNode().boundsInRoot.top < originalSecondTop)
        assertEquals("first", TaskStore.state.value.tasks.filter { it.completedAt == null }.minBy { it.position }.id)
        compose.onNodeWithText("First").performTouchInput { up() }
        compose.waitUntil(10000) { TaskStore.state.value.tasks.filter { it.completedAt == null }.sortedBy { it.position }.first().id == "second" }
        assertEquals(listOf("second", "first", "third"), TaskStore.state.value.tasks.filter { it.completedAt == null }.sortedBy { it.position }.map { it.id })
        compose.onNodeWithText("First").performTouchInput { down(center); advanceEventTime(700); moveBy(androidx.compose.ui.geometry.Offset(0f, -distance), 300); cancel() }
        assertEquals(listOf("second", "first", "third"), TaskStore.state.value.tasks.filter { it.completedAt == null }.sortedBy { it.position }.map { it.id })
        compose.onNodeWithTag("tasks-drop-target").assertDoesNotExist()
    }

    @Test fun emptyListDeletionAlsoOffersUndo() {
        show()
        compose.onNodeWithTag("tasks-list-picker").performClick()
        compose.onAllNodesWithText("Work").onLast().performClick()
        compose.onNodeWithTag("tasks-heading").performClick()
        compose.onNodeWithTag("tasks-list-name").assertTextContains("Work")
        compose.onNodeWithContentDescription("Delete list").performClick()
        compose.waitUntil(10000) { TaskStore.state.value.lists.size == 1 }
        compose.onNodeWithText("Deleted").assertIsDisplayed()
        compose.onNodeWithText("Undo").performClick()
        compose.waitUntil(10000) { TaskStore.state.value.lists.size == 2 }
        assertTrue(TaskStore.state.value.lists.any { it.id == "work" && it.name == "Work" })
    }

    @Test fun completedDragPreviewsNeighborsAndPersistsOnlyOnDrop() {
        val completed = System.currentTimeMillis()
        show(listOf(TaskItem(id = "active", listId = "tasks", title = "Active", position = 1), TaskItem(id = "done-first", listId = "tasks", title = "Done first", completedAt = completed, position = 0), TaskItem(id = "done-second", listId = "tasks", title = "Done second", completedAt = completed, position = 2)))
        compose.onNodeWithTag("tasks-completed-toggle").performClick()
        compose.onNodeWithTag("tasks-list").performScrollToNode(hasTestTag("tasks-completed-card"))
        val originalTop = compose.onNodeWithText("Done second").fetchSemanticsNode().boundsInRoot.top
        val distance = compose.onNodeWithText("Done second").fetchSemanticsNode().boundsInRoot.center.y - compose.onNodeWithText("Done first").fetchSemanticsNode().boundsInRoot.center.y
        compose.onNodeWithText("Done first").performTouchInput { down(center); advanceEventTime(700); moveBy(androidx.compose.ui.geometry.Offset(0f, distance), 300) }
        compose.mainClock.advanceTimeBy(400)
        compose.onNodeWithTag("tasks-completed-drop-target").assertIsDisplayed()
        compose.onNodeWithText("Done first").assertIsDisplayed()
        assertTrue(compose.onNodeWithText("Done second").fetchSemanticsNode().boundsInRoot.top < originalTop)
        assertEquals("done-first", TaskStore.state.value.tasks.filter { it.completedAt != null }.minBy { it.position }.id)
        compose.onNodeWithText("Done first").performTouchInput { up() }
        compose.waitUntil(10000) { TaskStore.state.value.tasks.filter { it.completedAt != null }.minBy { it.position }.id == "done-second" }
        assertEquals(1, TaskStore.state.value.tasks.first { it.id == "active" }.position)
        assertTrue(TaskStore.state.value.tasks.filter { it.id.startsWith("done-") }.all { it.completedAt == completed })
        compose.onNodeWithText("Done first").performTouchInput { down(center); advanceEventTime(700); moveBy(androidx.compose.ui.geometry.Offset(0f, -distance), 300); cancel() }
        compose.onNodeWithTag("tasks-completed-drop-target").assertDoesNotExist()
        assertEquals("done-second", TaskStore.state.value.tasks.filter { it.completedAt != null }.minBy { it.position }.id)
    }

    @Test fun reminderNotificationSettingsOpensSystemNotificationPage() {
        show()
        compose.onNodeWithContentDescription("Add").performClick()
        compose.onNodeWithTag("task-title").performTextInput("Permission draft")
        compose.onNodeWithContentDescription("Schedule").performClick()
        compose.onNodeWithText("Today").performClick()
        compose.onNodeWithText("Notification settings").assertDoesNotExist()
        compose.onNodeWithContentDescription("Done").performClick()
        compose.onNodeWithContentDescription("Cancel").performClick()
        compose.onNodeWithText("Discard").performClick()
        compose.onNodeWithContentDescription("Task options").performClick()
        compose.onNodeWithText("Manual order").assertDoesNotExist()
        compose.onNodeWithText("Show Tasks panel on home").assertDoesNotExist()
        compose.onNodeWithText("Notification settings").performScrollTo().performClick()
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        fun activity(): String = android.os.ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand("dumpsys activity activities")).bufferedReader().use { it.readText() }
        compose.waitUntil(10000) { activity().lineSequence().any { it.contains("mResumedActivity") && it.contains("com.android.settings") && it.contains("AppNotificationSettings") } }
        android.os.ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand("input keyevent KEYCODE_BACK")).use { it.readBytes() }
        assertTrue(TaskStore.state.value.tasks.isEmpty())
    }

}
