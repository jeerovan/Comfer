package com.jeerovan.comfer.tasks

import kotlinx.serialization.encodeToString

/** Runtime delivery acknowledgements and stale-action versions are installation-local. */
internal fun TaskSnapshot.portableTasks(): TaskSnapshot = copy(
    tasks = tasks.map { it.copy(notifiedAt = null, version = 1) },
    series = series.map { series -> series.copy(template = taskJson.encodeToString(taskJson.decodeFromString<TaskItem>(series.template).copy(notifiedAt = null, version = 1))) },
    preferences = preferences.copy(revision = 0),
)

internal fun TaskSnapshot.restoredTasks(previous: TaskSnapshot, now: Long = System.currentTimeMillis()): TaskSnapshot {
    validate()
    val version = maxOf(previous.tasks.maxOfOrNull { it.version } ?: 0, previous.preferences.revision) + 1
    val restored = materialize()
    return restored.copy(tasks = restored.tasks.map { item ->
        val due = item.snoozedUntil ?: item.dueInstant(preferences)
        item.copy(version = version, notifiedAt = if(due != null && due <= now) now else null,
            snoozedUntil = item.snoozedUntil?.takeIf { it > now })
    }, preferences = preferences.copy(revision = version))
}
