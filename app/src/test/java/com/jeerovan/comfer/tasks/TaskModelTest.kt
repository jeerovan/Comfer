package com.jeerovan.comfer.tasks

import java.time.*
import kotlinx.serialization.encodeToString
import org.junit.Assert.*
import org.junit.Test

class TaskModelTest {
    private fun task(id: String = "one") = TaskItem(id = id, listId = "tasks", title = "Example")
    @Test fun validatesReferencesAndRequiredContent() {
        TaskSnapshot().validate()
        listOf(task().copy(title = " "), task().copy(listId = "missing"), task().copy(parentId = "one"),
            task().copy(minute = 10), task().copy(seriesId = "missing")).forEach {
            assertThrows(IllegalArgumentException::class.java) { TaskSnapshot(tasks = listOf(it)).validate() }
        }
        assertThrows(IllegalArgumentException::class.java) { TaskSnapshot(lists = emptyList()).validate() }
        assertThrows(IllegalArgumentException::class.java) { TaskSnapshot(lists = listOf(TaskList("tasks", "Tasks"), TaskList("two", " TASKS "))).validate() }
    }
    @Test fun monthsAndLeapYearsStayAnchored() {
        val monthly = TaskRepeat(RepeatUnit.MONTHLY)
        val anchor = LocalDate.of(2024, 1, 31)
        assertEquals(LocalDate.of(2024, 2, 29), occurrenceDay(anchor, monthly, 1))
        assertEquals(LocalDate.of(2024, 3, 31), occurrenceDay(anchor, monthly, 2))
        assertEquals(LocalDate.of(2025, 2, 28), occurrenceDay(LocalDate.of(2024, 2, 29), TaskRepeat(RepeatUnit.YEARLY), 1))
    }
    @Test fun selectedWeekdaysIntervalsAndInclusiveEnds() {
        val anchor = LocalDate.of(2026, 9, 16) // Wednesday
        val repeat = TaskRepeat(RepeatUnit.WEEKLY, interval = 2, weekdays = setOf(1, 5))
        assertEquals(LocalDate.of(2026, 9, 18), occurrenceDay(anchor, repeat, 0))
        assertEquals(LocalDate.of(2026, 9, 28), occurrenceDay(anchor, repeat, 1))
        assertNull(occurrenceDay(anchor, repeat.copy(count = 1), 1))
        assertEquals(anchor, occurrenceDay(anchor, TaskRepeat(endDay = anchor.toEpochDay()), 0))
        assertNull(occurrenceDay(anchor, TaskRepeat(endDay = anchor.toEpochDay()), 1))
    }
    @Test fun daylightSavingGapAndOverlapUseDocumentedInstant() {
        val zone = ZoneId.of("America/New_York")
        val gap = task().copy(day = LocalDate.of(2026, 3, 8).toEpochDay(), minute = 150)
        assertEquals(Instant.parse("2026-03-08T07:00:00Z").toEpochMilli(), gap.dueInstant(TaskPreferences(), zone))
        val overlap = task().copy(day = LocalDate.of(2026, 11, 1).toEpochDay(), minute = 90)
        assertEquals(Instant.parse("2026-11-01T05:30:00Z").toEpochMilli(), overlap.dueInstant(TaskPreferences(), zone))
    }
    @Test fun parentCompletionRequiresConsentAndMovesChildren() {
        val parent = task()
        val child = task("child").copy(parentId = parent.id)
        val state = TaskSnapshot(tasks = listOf(parent, child), lists = listOf(TaskList("tasks", "Tasks"), TaskList("other", "Other")))
        assertThrows(IllegalArgumentException::class.java) { state.completeTask(parent.id, true) }
        assertTrue(state.completeTask(parent.id, true, true).tasks.all { it.completedAt != null })
        assertTrue(state.saveTask(parent.copy(listId = "other")).tasks.all { it.listId == "other" })
        assertThrows(IllegalArgumentException::class.java) { state.saveTask(parent.copy(day = 1), TaskRepeat()) }
    }
    @Test fun deletingFirstAndFutureOccurrencesStopsSeriesWithoutCorruptingHistory() {
        val anchor = LocalDate.of(2026, 9, 1)
        val template = task().copy(day = anchor.toEpochDay())
        val series = TaskSeries(id = "series", template = taskJson.encodeToString(template), rule = taskJson.encodeToString(TaskRepeat()))
        val state = TaskSnapshot(series = listOf(series)).materialize(anchor.plusDays(2))
        assertEquals(4, state.tasks.size)
        val deleted = state.deleteTask("series:0", future = true).materialize(anchor.plusDays(20))
        deleted.validate()
        assertTrue(deleted.tasks.isEmpty())
        val later = state.deleteTask("series:2", future = true).materialize(anchor.plusDays(20))
        assertEquals(listOf(0, 1), later.tasks.map { it.occurrence })
        later.validate()
    }
    @Test fun dateOnlyBecomesOverdueNextLocalDay() {
        val day = LocalDate.of(2026, 9, 13)
        val item = task().copy(day = day.toEpochDay())
        assertFalse(item.isOverdue(day.atTime(23, 59).toInstant(ZoneOffset.UTC).toEpochMilli(), ZoneOffset.UTC))
        assertTrue(item.isOverdue(day.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(), ZoneOffset.UTC))
    }
    @Test fun reopeningChildAlsoReopensParentAndValidationStaysConsistent() {
        val parent = task().copy(completedAt = 100)
        val child = task("child").copy(parentId = parent.id, completedAt = 100)
        val reopened = TaskSnapshot(tasks = listOf(parent, child)).completeTask(child.id, false)
        reopened.validate()
        assertTrue(reopened.tasks.all { it.completedAt == null })
    }
    @Test fun lateCompletionAndClearingHistoryDoNotShiftOrEraseSeries() {
        val anchor = LocalDate.now().minusDays(2)
        val series = TaskSeries(id = "daily", template = taskJson.encodeToString(task().copy(day = anchor.toEpochDay())), rule = taskJson.encodeToString(TaskRepeat()))
        val state = TaskSnapshot(series = listOf(series)).materialize()
        val completed = state.completeTask("daily:0", true)
        assertEquals(anchor.plusDays(1).toEpochDay(), completed.tasks.first { it.id == "daily:1" }.day)
        val cleared = completed.copy(tasks = completed.tasks.filter { it.completedAt == null }).materialize()
        assertEquals(1, cleared.series.size)
        assertFalse(cleared.tasks.any { it.id == "daily:0" })
    }
    @Test fun duplicateOccurrencesAndCorruptSeriesTemplatesAreRejectedBeforeRestore() {
        val series = TaskSeries(id = "repeat", template = taskJson.encodeToString(task().copy(day = LocalDate.now().toEpochDay())), rule = taskJson.encodeToString(TaskRepeat()))
        assertThrows(IllegalArgumentException::class.java) { TaskSnapshot(series = listOf(series), tasks = listOf(task().copy(seriesId = "repeat"))).validate() }
        assertThrows(DateTimeException::class.java) { TaskSnapshot(series = listOf(series.copy(template = taskJson.encodeToString(task().copy(day = Long.MIN_VALUE))))).validate() }
    }
}
