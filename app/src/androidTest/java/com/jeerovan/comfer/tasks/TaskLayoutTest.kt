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
    @Test fun largeTextRtlKeepsControlsAndWrappedSelectorReachable() {
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
    @Test fun searchReplacesBottomActionsAndCloseRestoresThem() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        runBlocking { check(context.packageName.endsWith(".notificationtest")); StartupCoordinator.awaitReady(); TaskStore.exclusive(context) {
            TaskStore.write(context,TaskSnapshot(tasks=listOf(TaskItem(id="search",listId="tasks",title="Search fixture")),preferences=TaskPreferences(guidanceDismissed=true)))
        } }
        compose.setContent { MaterialTheme { TasksScreen({}) } }
        compose.waitUntil(10000){compose.onAllNodesWithContentDescription("Add").fetchSemanticsNodes().isNotEmpty()}
        repeat(2) { attempt->
            compose.onNodeWithContentDescription("Search tasks").performClick()
            compose.onNodeWithTag("tasks-search-input").assertIsFocused()
            compose.onNodeWithContentDescription("Add").assertDoesNotExist()
            compose.onNode(hasContentDescription("Star") and hasAnyAncestor(hasTestTag("tasks-bottom-actions"))).assertDoesNotExist()
            // The search field intentionally shares the button's accessible label.
            compose.onNode(hasContentDescription("Search tasks") and !hasTestTag("tasks-search-input")).assertDoesNotExist()
            compose.onNodeWithTag("tasks-list-picker").assertDoesNotExist()
            val include=compose.onNodeWithContentDescription("Include completed").fetchSemanticsNode().boundsInRoot
            val input=compose.onNodeWithTag("tasks-search-input").fetchSemanticsNode().boundsInRoot
            assertTrue(include.bottom<=input.top)
            if(attempt==1) {
                compose.onNodeWithTag("tasks-search-input").performTextInput("No matching task")
                compose.onNodeWithText("Search fixture").assertDoesNotExist()
            }
            compose.onNodeWithContentDescription("Close search").performClick()
            compose.onNodeWithTag("tasks-search-input").assertDoesNotExist()
            compose.onNodeWithContentDescription("Include completed").assertDoesNotExist()
            compose.onNodeWithContentDescription("Search tasks").assertIsDisplayed()
            compose.onNodeWithContentDescription("Add").assertIsDisplayed()
            compose.onNode(hasContentDescription("Star") and hasAnyAncestor(hasTestTag("tasks-bottom-actions"))).assertIsDisplayed()
            compose.onNodeWithTag("tasks-list-picker").assertIsDisplayed()
            compose.onNodeWithText("Search fixture").assertExists()
        }
        compose.onNodeWithContentDescription("Search tasks").performClick()
        compose.onNodeWithTag("tasks-search-input").assert(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.EditableText,androidx.compose.ui.text.AnnotatedString("")))
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        androidx.test.espresso.Espresso.pressBack()
        compose.onNodeWithContentDescription("Add").assertIsDisplayed()
    }
    @Test fun optionsSheetCombinesSortAndPersistentSettings() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        runBlocking { check(context.packageName.endsWith(".notificationtest")); StartupCoordinator.awaitReady(); TaskStore.exclusive(context) { TaskStore.write(context, TaskSnapshot()) } }
        compose.setContent { MaterialTheme { TasksScreen({}) } }
        compose.waitUntil(10000) { compose.onAllNodesWithContentDescription("Task options").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("Task preferences").assertDoesNotExist()
        compose.onNodeWithContentDescription("Sort").assertDoesNotExist()
        compose.onNodeWithContentDescription("Task options").performClick()
        compose.onNodeWithText("My Order").assertExists()
        compose.waitUntil(5000){compose.onNodeWithText("Default reminder time").isDisplayed()}
        compose.onNodeWithText("Default reminder time").performScrollTo().assertIsDisplayed()
        val before=TaskStore.state.value.preferences.privateNotifications
        compose.onNodeWithContentDescription("Hide task text on lock screen").performScrollTo().performClick()
        compose.waitUntil(10000){TaskStore.state.value.preferences.privateNotifications!=before}
        androidx.test.espresso.Espresso.pressBack()
        compose.onNodeWithContentDescription("Add").assertIsDisplayed()
        compose.onNodeWithContentDescription("Task options").performClick()
        compose.onNodeWithContentDescription("Hide task text on lock screen").performScrollTo().assertIsToggleable()
        compose.onNodeWithText("Title").performScrollTo().performClick()
        compose.waitUntil(10000){TaskStore.state.value.preferences.sort=="alphabetical"}
        compose.onNodeWithTag("tasks-settings-heading").assertDoesNotExist()
    }

    @Test fun taskContentPullsIntoReachWhileHeaderStaysFixed() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        runBlocking { check(context.packageName.endsWith(".notificationtest")); StartupCoordinator.awaitReady(); TaskStore.exclusive(context) { TaskStore.write(context, TaskSnapshot(tasks=listOf(TaskItem(id="pinned-header",listId="tasks",title="Header fixture")),preferences=TaskPreferences(guidanceDismissed=true))) } }
        compose.setContent { MaterialTheme { TasksScreen({}) } }
        compose.waitUntil(10000) { compose.onAllNodesWithTag("tasks-heading").fetchSemanticsNodes().isNotEmpty() }
        val start = compose.onNodeWithTag("tasks-heading").fetchSemanticsNode().boundsInRoot.top
        val options = compose.onNodeWithContentDescription("Task options").fetchSemanticsNode().boundsInRoot.top
        val card = compose.onNodeWithTag("tasks-incomplete-card").fetchSemanticsNode().boundsInRoot.top
        compose.onNodeWithTag("tasks-list").performTouchInput { swipeDown(startY = height * .1f, endY = height * .9f, durationMillis = 600) }
        val pulled = compose.onNodeWithTag("tasks-heading").fetchSemanticsNode().boundsInRoot.top
        assertEquals(start,pulled,1f)
        assertEquals(options,compose.onNodeWithContentDescription("Task options").fetchSemanticsNode().boundsInRoot.top,1f)
        assertTrue(compose.onNodeWithTag("tasks-incomplete-card").fetchSemanticsNode().boundsInRoot.top > card + 50f)
        compose.onNodeWithTag("tasks-list").performTouchInput { swipeUp() }
        assertEquals(start,compose.onNodeWithTag("tasks-heading").fetchSemanticsNode().boundsInRoot.top,1f)
        compose.onNodeWithContentDescription("Task options").performClick()
        compose.onNodeWithText("My Order").assertExists()
        androidx.test.espresso.Espresso.pressBack()
        compose.onNodeWithContentDescription("Add").assertIsDisplayed()
    }

}
