package com.jeerovan.comfer.notifications

import kotlinx.serialization.Serializable
import java.util.Calendar
import java.util.TimeZone

@Serializable
data class QuietSchedule(
    val enabled: Boolean = false,
    /** Calendar weekday numbering: Sunday=1, Saturday=7. */
    val weekdays: Set<Int> = (1..7).toSet(),
    val startMinute: Int = 22 * 60,
    val endMinute: Int = 7 * 60,
    val deviceQuiet: Boolean = true,
)

data class QuietWindow(val active: Boolean, val nextBoundary: Long?)

/** Local wall-clock schedule; overnight windows belong to their starting weekday. */
fun quietWindow(schedule: QuietSchedule, now: Long, zone: TimeZone = TimeZone.getDefault()): QuietWindow {
    if (!schedule.enabled || schedule.weekdays.isEmpty()) return QuietWindow(false, null)
    require(schedule.startMinute in 0..1439 && schedule.endMinute in 0..1439 && schedule.weekdays.all { it in 1..7 })
    val base = Calendar.getInstance(zone).apply { timeInMillis = now; set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }
    var active = false
    val boundaries = mutableListOf<Long>()
    for (offset in -1..8) {
        val start = (base.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, offset) }
        if (start.get(Calendar.DAY_OF_WEEK) !in schedule.weekdays) continue
        start.set(Calendar.HOUR_OF_DAY, schedule.startMinute / 60); start.set(Calendar.MINUTE, schedule.startMinute % 60)
        val end = (start.clone() as Calendar).apply {
            if (schedule.endMinute <= schedule.startMinute) add(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, schedule.endMinute / 60); set(Calendar.MINUTE, schedule.endMinute % 60)
        }
        if (now >= start.timeInMillis && now < end.timeInMillis) active = true
        if (start.timeInMillis > now) boundaries += start.timeInMillis
        if (end.timeInMillis > now) boundaries += end.timeInMillis
    }
    return QuietWindow(active, boundaries.minOrNull())
}
