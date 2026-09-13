package com.jeerovan.comfer.tasks

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.*
import androidx.test.platform.app.InstrumentationRegistry
import com.jeerovan.comfer.StartupCoordinator
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*

class TaskLayoutTest {
    @get:Rule val compose = createComposeRule()
    @Test fun largeTextRtlKeepsFiveIconControlsAndWrappedSelectorReachable() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "قائمة العمل والمواعيد المهمة"
        runBlocking { check(context.packageName.endsWith(".notificationtest")); StartupCoordinator.awaitReady(); TaskStore.exclusive(context) { TaskStore.write(context, TaskSnapshot(lists = listOf(TaskList("tasks", name)))) } }
        compose.setContent { MaterialTheme {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f), LocalLayoutDirection provides LayoutDirection.Rtl) {
                Box(Modifier.size(320.dp, 480.dp)) { TasksScreen({}) }
            }
        } }
        compose.waitUntil(10000) { compose.onAllNodesWithContentDescription("Add").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("Star").assertIsDisplayed()
        compose.onNodeWithContentDescription("Add").assertIsDisplayed()
        compose.onNodeWithText(name).assertIsDisplayed()
        compose.onNodeWithTag("tasks-list-picker").performClick()
        compose.onAllNodesWithText(name).onLast().assertIsDisplayed()
    }
    @Test fun shortViewportCanScrollToFinalTaskWithoutLosingAdd() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        runBlocking { check(context.packageName.endsWith(".notificationtest")); StartupCoordinator.awaitReady(); TaskStore.exclusive(context) { TaskStore.write(context, TaskSnapshot(tasks = (1..80).map { TaskItem(id = "layout:$it", listId = "tasks", title = "Item $it", position = it) })) } }
        compose.setContent { MaterialTheme { Box(Modifier.size(320.dp, 320.dp)) { TasksScreen({}) } } }
        compose.waitUntil(10000) { compose.onAllNodesWithTag("tasks-list").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("tasks-list").performScrollToNode(hasTestTag("tasks-incomplete-card"))
        compose.onNodeWithTag("tasks-incomplete-list").performScrollToNode(hasText("Item 80"))
        compose.onNodeWithText("Item 80").assertIsDisplayed()
        compose.onNodeWithContentDescription("Add").assertIsDisplayed()
    }
    @Test fun settingsStartsWithinBottomReachAndPaddingScrollsAway() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        runBlocking { check(context.packageName.endsWith(".notificationtest")); StartupCoordinator.awaitReady(); TaskStore.exclusive(context) { TaskStore.write(context, TaskSnapshot()) } }
        compose.setContent { MaterialTheme { TasksScreen({}) } }
        compose.waitUntil(10000) { compose.onAllNodesWithContentDescription("Task preferences").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("Task preferences").performClick()
        val heading = compose.onNodeWithTag("tasks-settings-heading").fetchSemanticsNode().boundsInRoot
        val viewport = compose.onNodeWithTag("tasks-settings-scroll").fetchSemanticsNode().boundsInRoot
        val bottom = compose.onNodeWithContentDescription("Task preferences").fetchSemanticsNode().boundsInRoot.bottom
        val density = context.resources.displayMetrics.density
        assertTrue(heading.top > viewport.top + 40 * density)
        assertTrue(bottom - heading.top <= 360 * density)
        compose.onNodeWithTag("tasks-settings-scroll").performTouchInput { swipeUp() }
        assertTrue(compose.onNodeWithTag("tasks-settings-heading").fetchSemanticsNode().boundsInRoot.top < heading.top)
        compose.onNodeWithContentDescription("Add").assertIsDisplayed()
    }

}
