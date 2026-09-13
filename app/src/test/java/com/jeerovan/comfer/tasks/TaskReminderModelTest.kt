package com.jeerovan.comfer.tasks

import org.junit.Test
import org.junit.Assert.*
import java.time.LocalDate

class TaskReminderModelTest {
    private val task = TaskItem(id = "task", listId = "tasks", title = "Due", day = LocalDate.now().plusDays(1).toEpochDay(), minute = 540, version = 3)
    @Test fun staleOrRepeatedNotificationActionsCannotChangeNewerTask() {
        val state = TaskSnapshot(tasks = listOf(task))
        assertEquals(state, applyReminderAction(state, task.id, 2, "COMPLETE", 10, 10))
        val done = applyReminderAction(state, task.id, 3, "COMPLETE", 10, 10)
        assertNotNull(done.tasks.single().completedAt)
        assertEquals(done, applyReminderAction(done, task.id, 3, "COMPLETE", 10, 10))
    }
    @Test fun snoozeReplacesIntentAndInvalidatesOldAction() {
        val state = TaskSnapshot(tasks = listOf(task.copy(notifiedAt = 1)))
        val snoozed = applyReminderAction(state, task.id, 3, "SNOOZE", 30, 1000)
        assertEquals(1801000L, reminderAt(snoozed.tasks.single(), snoozed.preferences))
        assertEquals(4L, snoozed.tasks.single().version)
        assertEquals(snoozed, applyReminderAction(snoozed, task.id, 3, "COMPLETE", 10, 10))
    }
    @Test fun completedDisabledAndAcknowledgedTasksHaveNoAlarm() {
        listOf(task.copy(completedAt = 1), task.copy(reminder = false), task.copy(notifiedAt = 1)).forEach { assertNull(reminderAt(it, TaskPreferences())) }
        assertNull(reminderAt(task.copy(minute = null), TaskPreferences(dateOnlyReminders = false)))
    }
    @Test fun restoringDoesNotReplayPastButKeepsFutureAndRejectsOldActions() {
        val state = TaskSnapshot(tasks = listOf(task.copy(day = 1, notifiedAt = 42), task.copy(id = "future")))
        val portable = state.portableTasks()
        assertTrue(portable.tasks.all { it.notifiedAt == null })
        val restored = portable.restoredTasks(state)
        assertNull(reminderAt(restored.tasks.first(), restored.preferences))
        assertNotNull(reminderAt(restored.tasks.last(), restored.preferences))
        assertTrue(restored.tasks.all { it.version > 3 })
    }
    @Test fun parentNotificationCannotSilentlyCompleteUnfinishedChildren() {
        val state = TaskSnapshot(tasks = listOf(task, task.copy(id = "child", parentId = task.id)))
        assertEquals(state, applyReminderAction(state, task.id, task.version, "COMPLETE", 10, 10))
    }
    @Test fun explicitSnoozeWorksWhenDefaultDateOnlyRemindersAreOff() {
        val item = task.copy(minute = null, snoozedUntil = 2000)
        assertEquals(2000L, reminderAt(item, TaskPreferences(dateOnlyReminders = false)))
    }
}
