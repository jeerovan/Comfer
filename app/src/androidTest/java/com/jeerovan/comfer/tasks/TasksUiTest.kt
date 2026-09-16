package com.jeerovan.comfer.tasks

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.jeerovan.comfer.HomeFolderLayout
import com.jeerovan.comfer.WorkspaceModule
import com.jeerovan.comfer.StartupCoordinator
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*

class TasksUiTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    @Test fun circularWorkspaceOffersTasksAndSearch() = entry(true)
    @Test fun columnWorkspaceOffersTasksAndSearch() = entry(false)
    private fun entry(circular: Boolean) {
        val opened = androidx.compose.runtime.mutableStateOf(false)
        var selected: WorkspaceModule? = null
        compose.setContent { MaterialTheme { HomeFolderLayout(emptyList(), emptyMap(), null, circular, emptyList(), 48.dp, CircleShape,
            { opened.value = !opened.value }, false, null, false, {}, workspaceOpen = opened.value,
            onModuleSelected = { selected = it }) } }
        compose.onNodeWithContentDescription("Workspace").performClick()
        compose.onNodeWithContentDescription("Tasks").performClick()
        compose.runOnIdle { assertEquals(WorkspaceModule.TASKS, selected) }
        compose.onNodeWithContentDescription("Search").performClick()
        compose.runOnIdle { assertEquals(WorkspaceModule.SEARCH, selected) }
    }

    @Test fun smallScreenCapturesTaskAndReturnsToList() {
        runBlocking { check(context.packageName.endsWith(".notificationtest")); StartupCoordinator.awaitReady(); TaskStore.exclusive(context) { TaskStore.write(context, TaskSnapshot()) } }
        compose.setContent { MaterialTheme { Box(Modifier.size(320.dp, 480.dp)) { TasksScreen({}) } } }
        compose.waitUntil(10000) { compose.onAllNodesWithContentDescription("Add").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("Add").performClick()
        compose.onNodeWithTag("task-title").performTextInput("Test task")
        compose.onNodeWithTag("task-save").performClick()
        compose.waitUntil(10000) { TaskStore.state.value.tasks.any { it.title == "Test task" } }
        compose.onNodeWithTag("tasks-incomplete-list").performScrollToNode(hasText("Test task"))
        compose.onNodeWithText("Test task").assertIsDisplayed()
    }
    @Test fun shareCreatesOnlyADraftAndSurvivesStateRecreation() {
        runBlocking { check(context.packageName.endsWith(".notificationtest")); StartupCoordinator.awaitReady(); TaskStore.exclusive(context) { TaskStore.write(context, TaskSnapshot()) } }
        val restoration = StateRestorationTester(compose)
        restoration.setContent { MaterialTheme { TasksScreen({}, shared = "Shared title\nhttps://example.com/source") } }
        compose.waitUntil(10000) { compose.onAllNodesWithTag("task-title").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("task-title").assertTextContains("Shared title")
        assertTrue(TaskStore.state.value.tasks.isEmpty())
        compose.onNodeWithTag("task-title").performTextReplacement("Edited shared title")
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("task-title").assertTextContains("Edited shared title")
        assertTrue(TaskStore.state.value.tasks.isEmpty())
        compose.onNodeWithTag("task-save").performClick()
        compose.waitUntil(10000) { TaskStore.state.value.tasks.size == 1 }
        assertEquals("Edited shared title", TaskStore.state.value.tasks.single().title)
        assertTrue(TaskStore.state.value.tasks.single().notes.contains("https://example.com/source"))
    }
    @Test fun panelQuickAddKeepsDateStarAndSelectedList() {
        runBlocking { check(context.packageName.endsWith(".notificationtest")); StartupCoordinator.awaitReady(); TaskStore.exclusive(context) { TaskStore.write(context, TaskSnapshot()) } }
        val today = java.time.LocalDate.now().toEpochDay()
        compose.setContent { MaterialTheme { TasksScreen({}, quickAdd = true, initialStarred = true, quickDay = today) } }
        compose.waitUntil(10000) { compose.onAllNodesWithTag("task-title").fetchSemanticsNodes().isNotEmpty() }
        assertTrue(TaskStore.state.value.tasks.isEmpty())
        compose.onNodeWithTag("task-title").performTextInput("Panel capture")
        compose.onNodeWithTag("task-save").performClick()
        compose.waitUntil(10000) { TaskStore.state.value.tasks.size == 1 }
        val task = TaskStore.state.value.tasks.single()
        assertEquals(today, task.day)
        assertTrue(task.starred)
        assertEquals(TaskStore.state.value.preferences.selectedList, task.listId)
    }
}
