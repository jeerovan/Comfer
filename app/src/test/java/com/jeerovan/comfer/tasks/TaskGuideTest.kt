package com.jeerovan.comfer.tasks

import org.junit.Assert.*
import org.junit.Test

class TaskGuideTest {
    private val first = TaskItem(id = "first", listId = "tasks", title = "First")
    private val second = TaskItem(id = "second", listId = "tasks", title = "Second")
    @Test fun emptyAndCompletedTasksDoNotTriggerGuides() {
        assertNull(nextTaskGuide(emptyList(), TaskPreferences()))
        assertNull(nextTaskGuide(listOf(first.copy(completedAt = 1)), TaskPreferences()))
    }
    @Test fun firstIncompleteTaskTriggersSwipeBeforeReorder() {
        assertEquals(TaskGuide.SWIPE, nextTaskGuide(listOf(first), TaskPreferences()))
        assertEquals(TaskGuide.SWIPE, nextTaskGuide(listOf(first, second), TaskPreferences()))
        assertNull(nextTaskGuide(listOf(first), TaskPreferences(swipeGuideShown = true)))
    }
    @Test fun reorderNeedsTwoIncompleteSiblingsAndManualSort() {
        val prefs = TaskPreferences(swipeGuideShown = true)
        assertEquals(TaskGuide.REORDER, nextTaskGuide(listOf(first, second), prefs))
        assertNull(nextTaskGuide(listOf(first, second.copy(completedAt = 1)), prefs))
        assertNull(nextTaskGuide(listOf(first, second.copy(listId = "other")), prefs))
        assertNull(nextTaskGuide(listOf(first, second), prefs.copy(sort = "date")))
        assertNull(nextTaskGuide(listOf(first, second), prefs.copy(reorderGuideShown = true)))
    }
    @Test fun guideProgressRoundTripsAndOlderPreferencesDefaultSafely() {
        val prefs = TaskPreferences(swipeGuideShown = true, reorderGuideShown = true, listGuideShown = true)
        assertEquals(prefs, taskJson.decodeFromString<TaskPreferences>(taskJson.encodeToString(TaskPreferences.serializer(), prefs)))
        val old = taskJson.decodeFromString<TaskPreferences>("{}")
        assertFalse(old.swipeGuideShown)
        assertFalse(old.reorderGuideShown)
        assertFalse(old.listGuideShown)
    }
}
