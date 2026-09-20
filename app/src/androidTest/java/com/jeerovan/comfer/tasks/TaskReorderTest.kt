package com.jeerovan.comfer.tasks

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class TaskReorderTest {
    @get:Rule val compose = createComposeRule()
    private val drops = mutableListOf<Pair<String, String>>()
    private val first = TaskItem(id = "first", listId = "tasks", title = "First")
    private val second = first.copy(id = "second", title = "Second")
    private fun show(rows: List<TaskItem>, enabled: Boolean = true) {
        compose.setContent {
            MaterialTheme {
                TasksReorderList(rows, enabled, rememberLazyListState(), Modifier.width(320.dp).heightIn(max = 500.dp), "slot", onDrop = { id, target -> drops += id to target }) { item ->
                    Text(item.title, Modifier.fillMaxWidth().height(if(item.id == "second") 120.dp else 64.dp))
                }
            }
        }
    }
    private fun dragToSecond() {
        val distance = compose.onNodeWithTag("task-second").fetchSemanticsNode().boundsInRoot.center.y - compose.onNodeWithTag("task-first").fetchSemanticsNode().boundsInRoot.center.y
        compose.onNodeWithTag("task-first").performTouchInput { down(Offset(center.x, 10f)); advanceEventTime(700); moveBy(Offset(0f, distance), 300) }
        compose.mainClock.advanceTimeBy(400)
    }
    @Test fun cancellationRestoresUnequalRowsWithoutCommitting() {
        show(listOf(first, second))
        val before = compose.onNodeWithTag("task-second").fetchSemanticsNode().boundsInRoot
        dragToSecond()
        compose.onNodeWithTag("slot").assertExists()
        assertTrue(compose.onNodeWithTag("task-second").fetchSemanticsNode().boundsInRoot.top < before.top)
        assertTrue(drops.isEmpty())
        compose.onNodeWithTag("task-first").performTouchInput { cancel() }
        compose.waitForIdle()
        compose.onNodeWithTag("slot").assertDoesNotExist()
        assertEquals(before.top, compose.onNodeWithTag("task-second").fetchSemanticsNode().boundsInRoot.top, 1f)
        assertTrue(drops.isEmpty())
    }
    @Test fun cannotReorderAcrossListsOrParents() {
        show(listOf(first, second.copy(listId = "other"), first.copy(id = "child", parentId = "second", title = "Child")))
        dragToSecond()
        compose.onNodeWithTag("task-first").performTouchInput { up() }
        compose.waitForIdle()
        assertTrue(drops.isEmpty())
        assertTrue(compose.onNodeWithTag("task-first").fetchSemanticsNode().boundsInRoot.top < compose.onNodeWithTag("task-second").fetchSemanticsNode().boundsInRoot.top)
    }
    @Test fun nonManualSortDoesNotStartReorder() {
        show(listOf(first, second), enabled = false)
        dragToSecond()
        compose.onNodeWithTag("task-first").performTouchInput { up() }
        compose.onNodeWithTag("slot").assertDoesNotExist()
        assertTrue(drops.isEmpty())
    }
    @Test fun expandedChildrenStayWithTheirParentDuringPreview() {
        show(listOf(first, first.copy(id = "child", parentId = "first", title = "Child"), second))
        dragToSecond()
        val slot = compose.onNodeWithTag("slot").fetchSemanticsNode().boundsInRoot
        val child = compose.onNodeWithTag("task-child").fetchSemanticsNode().boundsInRoot
        assertEquals(slot.bottom, child.top, 1f)
        assertTrue(compose.onNodeWithTag("task-second").fetchSemanticsNode().boundsInRoot.top < slot.top)
        compose.onNodeWithTag("task-first").performTouchInput { up() }
        compose.waitForIdle()
        assertEquals(listOf("first" to "second"), drops)
    }
}
