package com.jeerovan.comfer.tasks

import kotlinx.serialization.encodeToString
import java.time.LocalDate

internal fun TaskSnapshot.saveTask(item: TaskItem, repeat: TaskRepeat? = null, future: Boolean = false): TaskSnapshot {
    val old = tasks.find { it.id == item.id }
    require(old == null || item.version == old.version) { "Task changed; reopen it before saving" }
    var updated = copy(tasks = tasks.map { if (it.parentId == item.id) it.copy(listId = item.listId, version = it.version + 1) else it })
    var removed = setOf(item.id)
    if (future && old?.seriesId != null) {
        removed = updated.tasks.filter { it.seriesId == old.seriesId && it.occurrence >= old.occurrence }.map { it.id }.toSet()
        updated = updated.copy(series = updated.series.map {
            if (it.id == old.seriesId) it.copy(stopped = true) else it
        })
    }
    val saved = item.copy(title = item.title.trim(), updatedAt = System.currentTimeMillis(), version = (old?.version ?: 0) + 1,
        notifiedAt = if (old?.day == item.day && old?.minute == item.minute && old?.reminder == item.reminder) item.notifiedAt else null,
        snoozedUntil = if (old?.day == item.day && old?.minute == item.minute) item.snoozedUntil else null)
    if (repeat != null) {
        require(saved.parentId == null && tasks.none { it.parentId == saved.id }) { "Tasks with subtasks cannot repeat" }
        require(saved.day != null) { "Choose a start date for repeating tasks" }
        val series = TaskSeries(template = taskJson.encodeToString(saved.copy(seriesId = null)), rule = taskJson.encodeToString(repeat))
        updated = updated.copy(tasks = updated.tasks.filterNot { it.id in removed }, series = updated.series + series)
        return updated.materialize()
    }
    return updated.copy(tasks = updated.tasks.filterNot { it.id in removed } + saved.copy(seriesId = if (future) null else saved.seriesId)).also { it.validate() }
}

internal fun TaskSnapshot.completeTask(id: String, complete: Boolean, includeChildren: Boolean = false): TaskSnapshot {
    val task = tasks.find { it.id == id } ?: error("Task is no longer available")
    require(!complete || includeChildren || tasks.none { it.parentId == id && it.completedAt == null }) { "Confirm completion of unfinished subtasks" }
    val now = System.currentTimeMillis()
    return copy(tasks = tasks.map {
        if (it.id == task.id || complete && it.parentId == task.id) {
            if ((it.completedAt != null) == complete) it else it.copy(completedAt = if (complete) now else null,
                notifiedAt = if (complete || (it.dueInstant(preferences) ?: Long.MAX_VALUE) <= now) now else null,
                snoozedUntil = null, version = it.version + 1, updatedAt = now)
        } else if(!complete && task.parentId == it.id && it.completedAt != null) it.copy(completedAt = null, notifiedAt = now, version = it.version + 1, updatedAt = now)
        else it
    }).materialize()
}

internal fun TaskSnapshot.deleteTask(id: String, future: Boolean = false): TaskSnapshot {
    val task = tasks.find { it.id == id } ?: return this
    val removed = tasks.filter { it.id == id || it.parentId == id || future && task.seriesId != null && it.seriesId == task.seriesId && it.occurrence >= task.occurrence }.map { it.id }.toSet()
    val nextSeries = if (future && task.seriesId != null) series.map {
        if (it.id == task.seriesId) it.copy(stopped = true) else it
    } else series
    // Retain series metadata for earlier occurrences, but stop future generation.
    return copy(tasks = tasks.filterNot { it.id in removed }, series = nextSeries)
}

internal fun TaskSnapshot.moveList(source: String, destination: String?, delete: Boolean = false): TaskSnapshot {
    require(lists.any { it.id == source }) { "List no longer exists" }
    require(!delete || lists.size > 1) { "Keep at least one list" }
    require(destination == null || destination != source && lists.any { it.id == destination }) { "Choose another list" }
    val removedLists = if (delete) lists.filterNot { it.id == source } else lists
    return copy(lists = removedLists,
        tasks = if (destination == null) tasks.filterNot { it.listId == source } else tasks.map { if (it.listId == source) it.copy(listId = destination, version = it.version + 1) else it },
        series = series.mapNotNull {
            val template = taskJson.decodeFromString<TaskItem>(it.template)
            if (template.listId != source) it else destination?.let { dest -> it.copy(template = taskJson.encodeToString(template.copy(listId = dest))) }
        },
        preferences = preferences.copy(selectedList = if (delete && preferences.selectedList == source) destination ?: removedLists.first().id else preferences.selectedList))
}

/** Persist missed occurrences plus one future occurrence, so history and series remain independent. */
internal fun TaskSnapshot.materialize(today: LocalDate = LocalDate.now()): TaskSnapshot {
    val rows = tasks.toMutableList()
    val ids = rows.mapTo(mutableSetOf()) { it.id }
    val changed = series.map { series ->
        if (series.stopped) return@map series
        val template = taskJson.decodeFromString<TaskItem>(series.template)
        val rule = taskJson.decodeFromString<TaskRepeat>(series.rule)
        var index = series.nextIndex
        // A future occurrence already exists; generate again only after its day passes/completion.
        if (rows.any { it.seriesId == series.id && it.completedAt == null && (it.day ?: Long.MIN_VALUE) > today.toEpochDay() }) return@map series
        while (index < Int.MAX_VALUE) {
            val day = occurrenceDay(LocalDate.ofEpochDay(template.day!!), rule, index) ?: break
            val id = "${series.id}:$index"
            if (ids.add(id)) rows += template.copy(id = id, seriesId = series.id, occurrence = index,
                day = day.toEpochDay(), completedAt = null, snoozedUntil = null, notifiedAt = null, version = 1)
            index++
            if (day.isAfter(today)) break
        }
        series.copy(nextIndex = index, stopped = occurrenceDay(LocalDate.ofEpochDay(template.day!!), rule, index) == null)
    }
    return copy(tasks = rows, series = changed)
}
