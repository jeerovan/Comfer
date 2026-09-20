package com.jeerovan.comfer.tasks

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.test.platform.app.InstrumentationRegistry
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
                    Surface { TasksReorderList(listOf(item, item.copy(id = "two", title = "Other task")), true, rememberLazyListState(), Modifier.width(360.dp).height(400.dp), "slot", onDrop = { _, _ -> events += "move" }) { row ->
                    TaskRow(row, TaskSnapshot(tasks = listOf(item)), Modifier.fillMaxWidth().testTag(if(row.id == "one") "row" else "other-row"), reveal, { reveal = it }, false, {}, { events += "open" }, { events += "complete" }, { events += "star" }, { events += "menu" }, onMove = { events += "move-sheet" }, onDelete = { events += "delete" })
                    } }
                }
            }
        }
    }
    @Test fun tapsOpenWithoutStarringAndStationaryHoldDoesNothing() {
        show()
        compose.onNodeWithText("Gesture task").performTouchInput { click() }
        compose.runOnIdle { assertEquals(listOf("open"), events) }
        compose.onNodeWithText("Gesture task").performTouchInput { doubleClick() }
        compose.runOnIdle { assertEquals(listOf("open", "open", "open"), events) }
        compose.onNodeWithText("Gesture task").performTouchInput { longClick() }
        compose.runOnIdle { assertEquals(listOf("open", "open", "open"), events) }
        compose.onAllNodesWithContentDescription("Star").onFirst().performClick()
        compose.runOnIdle { assertEquals("star", events.last()); assertEquals(4, events.size) }
    }
    @Test fun partialSwipeAndCancellationDoNotMutate() {
        show()
        compose.onNodeWithText("Gesture task").performTouchInput { swipe(center, center + Offset(-70f * scale, 0f), 300) }
        compose.runOnIdle { assertTrue(events.isEmpty()) }
        compose.onNodeWithText("Schedule").assertDoesNotExist()
        compose.onNodeWithText("Gesture task").performTouchInput { down(center); moveBy(Offset(70f * scale, 0f), 150); cancel() }
        compose.runOnIdle { assertTrue(events.isEmpty()) }
    }
    @Test fun draggingKeepsTheRestingBackgroundColor() {
        show()
        fun background(): Int {
            compose.waitForIdle()
            val bounds = compose.onNodeWithTag("row").fetchSemanticsNode().boundsInWindow
            val image = checkNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
            val colors = mutableMapOf<Int, Int>()
            for(y in bounds.top.toInt() until bounds.bottom.toInt() step 4) {
                for(x in bounds.left.toInt() until bounds.right.toInt() step 4) {
                    val color = image.getPixel(x, y)
                    colors[color] = (colors[color] ?: 0) + 1
                }
            }
            image.recycle()
            return colors.maxBy { it.value }.key
        }
        // Window enter animation is independent of the Compose test clock.
        android.os.SystemClock.sleep(600)
        val resting = background()
        compose.onNodeWithText("Gesture task").performTouchInput {
            down(center); moveBy(Offset(40f * scale, 0f), 150)
        }
        assertEquals(resting, background())
        compose.onNodeWithText("Gesture task").performTouchInput { cancel() }
        compose.onNodeWithText("Gesture task").performTouchInput {
            down(center); advanceEventTime(700); moveBy(Offset(0f, 10f * scale), 150)
        }
        assertEquals(resting, background())
        compose.onNodeWithText("Gesture task").performTouchInput { cancel() }
    }
    @Test fun rtlFullSwipeDeletesOnlyOnce() {
        show(true)
        compose.onNodeWithText("Gesture task").performTouchInput { swipe(center, center + Offset(-170f * scale, 0f), 300) }
        compose.runOnIdle { assertEquals(listOf("delete"), events) }
    }
    @Test fun holdAndDragReordersWithoutOpeningMoveSheet() {
        show()
        compose.onNodeWithText("Gesture task").performTouchInput {
            down(center); advanceEventTime(700); moveBy(Offset(0f, 56f * scale), 300); up()
        }
        compose.runOnIdle { assertEquals(1, events.size); assertEquals("move", events.single()) }
    }

}
