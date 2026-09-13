package com.jeerovan.comfer.tasks

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.*
import org.junit.*
import org.junit.Assert.*

class TaskGestureTest {
    @get:Rule val compose = createComposeRule()
    private val events = mutableListOf<String>()
    private var scale = 1f
    private fun show(rtl: Boolean = false) {
        compose.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalLayoutDirection provides if(rtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                    scale = LocalDensity.current.density
                    var reveal by remember { mutableIntStateOf(0) }
                    val item = TaskItem(id = "one", listId = "tasks", title = "Gesture task")
                    TaskRow(item, TaskSnapshot(tasks = listOf(item)), Modifier.width(360.dp).testTag("row"), reveal, { reveal = it }, false, {}, { events += "open" }, { events += "complete" }, { events += "star" }, { events += "menu" }, { events += "move:$it" }, onMove = { events += "move-sheet" }, onDelete = { events += "delete" })
                }
            }
        }
    }
    @Test fun doubleTapStarsSingleTapOpensAndStationaryHoldDoesNothing() {
        show()
        compose.onNodeWithText("Gesture task").performTouchInput { doubleClick() }
        compose.runOnIdle { assertEquals(listOf("star"), events) }
        compose.onNodeWithText("Gesture task").performTouchInput { click() }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(600)
        compose.runOnIdle { assertEquals(listOf("star", "open"), events) }
        compose.onNodeWithText("Gesture task").performTouchInput { longClick() }
        compose.runOnIdle { assertEquals(listOf("star", "open"), events) }
    }
    @Test fun partialSwipeAndCancellationDoNotMutate() {
        show()
        compose.onNodeWithText("Gesture task").performTouchInput { swipe(center, center + Offset(-70f * scale, 0f), 300) }
        compose.runOnIdle { assertTrue(events.isEmpty()) }
        compose.onNodeWithText("Schedule").assertDoesNotExist()
        compose.onNodeWithText("Gesture task").performTouchInput { down(center); moveBy(Offset(70f * scale, 0f), 150); cancel() }
        compose.runOnIdle { assertTrue(events.isEmpty()) }
    }
    @Test fun rtlFullSwipeDeletesOnlyOnce() {
        show(true)
        compose.onNodeWithText("Gesture task").performTouchInput { swipe(center, center + Offset(-170f * scale, 0f), 300) }
        compose.runOnIdle { assertEquals(listOf("delete"), events) }
    }
    @Test fun holdAndDragReordersWithoutOpeningMoveSheet() {
        show()
        compose.onNodeWithText("Gesture task").performTouchInput {
            down(center); advanceEventTime(700); moveBy(Offset(0f, 120f * scale), 300); up()
        }
        compose.runOnIdle { assertEquals(1, events.size); assertTrue(events.single().startsWith("move:")) }
    }

}
