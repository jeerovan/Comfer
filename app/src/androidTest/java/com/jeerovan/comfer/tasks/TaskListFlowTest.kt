package com.jeerovan.comfer.tasks

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.jeerovan.comfer.StartupCoordinator
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*

class TaskListFlowTest {
    @get:Rule val compose = createComposeRule()
    @Test fun addListSelectRenameAndCancelDeletionPreserveTasks() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        runBlocking { check(context.packageName.endsWith(".notificationtest")); StartupCoordinator.awaitReady(); TaskStore.exclusive(context) { TaskStore.write(context, TaskSnapshot()) } }
        compose.setContent { MaterialTheme { TasksScreen({}) } }
        compose.waitUntil(10000) { compose.onAllNodesWithTag("tasks-list-picker").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("tasks-list-picker").performClick()
        compose.onNodeWithText("Manage lists").assertDoesNotExist()
        compose.onNodeWithText("Add list").performClick()
        compose.onNodeWithContentDescription("Delete list").assertDoesNotExist()
        compose.onNodeWithContentDescription("Save").assertIsNotEnabled()
        compose.onNodeWithTag("tasks-list-name").performTextInput("Work")
        compose.onNodeWithContentDescription("Save").performClick()
        compose.waitUntil(10000) { TaskStore.state.value.lists.size == 2 }
        val workId = TaskStore.state.value.lists.first { it.name == "Work" }.id
        assertEquals(workId, TaskStore.state.value.preferences.selectedList)
        compose.onNodeWithTag("tasks-heading").assertTextContains("Work")
        compose.onNodeWithContentDescription("Add").performClick()
        compose.onNodeWithTag("task-title").performTextInput("Work task")
        compose.onNodeWithTag("task-save").performClick()
        compose.waitUntil(10000) { TaskStore.state.value.tasks.isNotEmpty() }
        assertEquals(workId, TaskStore.state.value.tasks.single().listId)
        compose.onNodeWithTag("tasks-heading").performClick()
        compose.onNodeWithText("Rename").assertIsDisplayed()
        compose.onNodeWithContentDescription("Delete list").assertIsDisplayed()
        compose.onNodeWithTag("tasks-list-name").assertTextContains("Work")
        compose.onNodeWithTag("tasks-list-name").performTextReplacement("Renamed work")
        compose.onNodeWithContentDescription("Save").performClick()
        compose.waitUntil(10000) { TaskStore.state.value.lists.any { it.id == workId && it.name == "Renamed work" } }
        compose.onNodeWithTag("tasks-heading").assertTextContains("Renamed work")
        compose.onNodeWithTag("tasks-heading").performClick()
        compose.onNodeWithContentDescription("Delete list").performClick()
        androidx.test.espresso.Espresso.pressBack()
        assertTrue(TaskStore.state.value.lists.any { it.id == workId })
        compose.onNodeWithContentDescription("Delete list").performClick()
        compose.onNode(hasText("Delete") and hasClickAction()).performClick()
        compose.waitUntil(10000) { TaskStore.state.value.lists.none { it.id == workId } }
        compose.onNodeWithText("Deleted").assertIsDisplayed()
        compose.onNodeWithText("Undo").performClick()
        compose.waitUntil(10000) { TaskStore.state.value.lists.any { it.id == workId } }
        assertEquals("Renamed work", TaskStore.state.value.lists.first { it.id == workId }.name)
        assertEquals(workId, TaskStore.state.value.tasks.single().listId)
    }
}
