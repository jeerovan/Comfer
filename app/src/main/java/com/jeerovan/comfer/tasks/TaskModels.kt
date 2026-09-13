package com.jeerovan.comfer.tasks

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.*
import java.util.UUID

internal val taskJson = Json { encodeDefaults = true; ignoreUnknownKeys = true }
internal fun taskId() = UUID.randomUUID().toString()

@Serializable @Entity(tableName = "task_lists")
data class TaskList(@PrimaryKey val id: String = taskId(), val name: String, val position: Int = 0)

@Serializable @Entity(tableName = "tasks")
data class TaskItem(
    @PrimaryKey val id: String = taskId(), val listId: String, val title: String,
    val notes: String = "", val parentId: String? = null, val starred: Boolean = false,
    val day: Long? = null, val minute: Int? = null, val reminder: Boolean = true,
    val completedAt: Long? = null, val position: Int = 0,
    val createdAt: Long = System.currentTimeMillis(), val updatedAt: Long = createdAt,
    val seriesId: String? = null, val occurrence: Int = 0,
    val snoozedUntil: Long? = null, val notifiedAt: Long? = null, val version: Long = 1,
)

@Serializable
enum class RepeatUnit { DAILY, WEEKLY, MONTHLY, YEARLY }
@Serializable
data class TaskRepeat(
    val unit: RepeatUnit = RepeatUnit.DAILY, val interval: Int = 1,
    val weekdays: Set<Int> = emptySet(), // ISO Monday=1
    val endDay: Long? = null, val count: Int? = null,
)

@Serializable @Entity(tableName = "task_series")
data class TaskSeries(@PrimaryKey val id: String = taskId(), val template: String, val rule: String, val nextIndex: Int = 0, val stopped: Boolean = false)

@Serializable @Entity(tableName = "task_preferences")
data class TaskPreferences(
    @PrimaryKey val id: Int = 1, val selectedList: String = "tasks", val defaultMinute: Int = 540,
    val dateOnlyReminders: Boolean = true, val privateNotifications: Boolean = true,
    val panelView: String = "today", val panelEnabled: Boolean = false,
    val sort: String = "manual", val revision: Long = 0,
    @androidx.room.ColumnInfo(defaultValue = "0") val guidanceDismissed: Boolean = false,
)

@Serializable
data class TaskSnapshot(
    val schema: Int = 1, val lists: List<TaskList> = listOf(TaskList("tasks", "Tasks")),
    val tasks: List<TaskItem> = emptyList(), val series: List<TaskSeries> = emptyList(),
    val preferences: TaskPreferences = TaskPreferences(),
) {
    fun validate() {
        require(schema == 1) { "Unsupported task data version" }
        require(lists.isNotEmpty()) { "Keep at least one list" }
        require(lists.map { it.id }.distinct().size == lists.size && tasks.map { it.id }.distinct().size == tasks.size && series.map { it.id }.distinct().size == series.size) { "Duplicate task identifiers" }
        require(lists.all { it.id.isNotBlank() && it.name.isNotBlank() && it.name.length <= 200 }) { "List name is required (up to 200 characters)" }
        require(lists.map { it.name.trim().lowercase(java.util.Locale.ROOT) }.distinct().size == lists.size) { "A list with this name already exists" }
        require(preferences.selectedList in lists.map { it.id } && preferences.defaultMinute in 0..1439)
        require(preferences.id == 1 && preferences.sort in setOf("manual", "date", "alphabetical", "starred") && preferences.panelView in setOf("today", "starred", "selected"))
        val byId = tasks.associateBy { it.id }
        val listIds = lists.mapTo(hashSetOf()) { it.id }
        val seriesIds = series.mapTo(hashSetOf()) { it.id }
        val occurrenceKeys = tasks.filter { it.seriesId != null }.map { it.seriesId to it.occurrence }
        require(occurrenceKeys.distinct().size == occurrenceKeys.size) { "Duplicate recurring occurrence" }
        tasks.forEach { task ->
            require(task.id.isNotBlank() && task.title.isNotBlank() && task.title.length <= 2000 && task.notes.length <= 100000) { "Enter a title (up to 2000 characters)" }
            require(task.version in 1 until Long.MAX_VALUE && task.occurrence >= 0)
            require(task.listId in listIds) { "Choose an existing list" }
            require(task.minute == null || task.day != null && task.minute in 0..1439) { "Choose a valid date and time" }
            task.day?.let { require(LocalDate.ofEpochDay(it).year in 1900..9999) { "Choose a date from 1900 to 9999" } }
            if (task.parentId != null) {
                val parent = byId[task.parentId]
                require(parent != null && parent.id != task.id && parent.parentId == null && parent.seriesId == null && task.seriesId == null && parent.listId == task.listId) { "Subtasks need a non-repeating parent in the same list" }
                require(parent.completedAt == null || task.completedAt != null) { "Reopen the parent before adding unfinished subtasks" }
            }
            require(task.seriesId == null || task.seriesId in seriesIds) { "Missing repeating task" }
            require(task.seriesId == null || task.id == "${task.seriesId}:${task.occurrence}") { "Invalid occurrence identity" }
        }
        series.forEach {
            val template = taskJson.decodeFromString<TaskItem>(it.template)
            val rule = taskJson.decodeFromString<TaskRepeat>(it.rule)
            require(it.id.isNotBlank() && template.title.isNotBlank() && template.title.length <= 2000 && template.notes.length <= 100000 && template.seriesId == null)
            require(template.minute == null || template.minute in 0..1439)
            require(template.parentId == null && template.day != null && lists.any { l -> l.id == template.listId })
            require(LocalDate.ofEpochDay(template.day).year in 1900..9999) { "Choose a date from 1900 to 9999" }
            require(rule.interval in 1..1000 && (rule.count == null || rule.count in 1..10000) && rule.weekdays.all { d -> d in 1..7 }) { "Invalid repeat interval" }
            require(rule.endDay == null || rule.endDay >= template.day) { "End date is before the first occurrence" }
            rule.endDay?.let { day -> require(LocalDate.ofEpochDay(day).year in 1900..9999) { "Choose an end date from 1900 to 9999" } }
            require(it.nextIndex >= 0 && (rule.count == null || it.nextIndex <= rule.count)) { "Invalid occurrence cursor" }
            if(!it.stopped) occurrenceDay(LocalDate.ofEpochDay(template.day), rule, it.nextIndex)?.let { next ->
                require(next.year in 1900..9999) { "Repeat extends beyond the supported date range" }
            }
        }
    }
}

internal fun TaskItem.dueInstant(prefs: TaskPreferences, zone: ZoneId = ZoneId.systemDefault()): Long? {
    if (day == null) return null
    val time = LocalDate.ofEpochDay(day).atTime(LocalTime.ofSecondOfDay((minute ?: prefs.defaultMinute) * 60L))
    val offsets = zone.rules.getValidOffsets(time)
    // Gap: the first valid local time after the gap. Overlap: the earlier occurrence.
    return if (offsets.isEmpty()) zone.rules.getTransition(time)!!.instant.toEpochMilli()
    else time.toInstant(offsets.first()).toEpochMilli()
}

internal fun TaskItem.isOverdue(now: Long, zone: ZoneId = ZoneId.systemDefault()): Boolean = completedAt == null && day != null &&
    if (minute == null) day < Instant.ofEpochMilli(now).atZone(zone).toLocalDate().toEpochDay()
    else (dueInstant(TaskPreferences(), zone) ?: Long.MAX_VALUE) < now

/** Calendar anchored occurrences: short months never shift the series' original day. */
internal fun occurrenceDay(anchor: LocalDate, repeat: TaskRepeat, index: Int): LocalDate? {
    require(index >= 0 && repeat.interval in 1..1000)
    if (repeat.count != null && index >= repeat.count) return null
    val date = when (repeat.unit) {
        RepeatUnit.DAILY -> anchor.plusDays(index.toLong() * repeat.interval)
        RepeatUnit.MONTHLY -> anchor.plusMonths(index.toLong() * repeat.interval)
        RepeatUnit.YEARLY -> anchor.plusYears(index.toLong() * repeat.interval)
        RepeatUnit.WEEKLY -> {
            val weekdays = repeat.weekdays.ifEmpty { setOf(anchor.dayOfWeek.value) }.sorted()
            val weekStart = anchor.minusDays(anchor.dayOfWeek.value - 1L)
            val firstWeek = weekdays.filter { it >= anchor.dayOfWeek.value }
            if (index < firstWeek.size) weekStart.plusDays(firstWeek[index] - 1L)
            else {
                val remaining = index - firstWeek.size
                weekStart.plusWeeks((remaining / weekdays.size + 1L) * repeat.interval).plusDays(weekdays[remaining % weekdays.size] - 1L)
            }
        }
    }
    return date.takeIf { repeat.endDay == null || it.toEpochDay() <= repeat.endDay }
}
