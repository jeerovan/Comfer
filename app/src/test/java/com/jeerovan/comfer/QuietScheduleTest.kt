package com.jeerovan.comfer

import com.jeerovan.comfer.notifications.*
import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class QuietScheduleTest {
    private val utc = TimeZone.getTimeZone("UTC")
    private fun date(day: Int, hour: Int, minute: Int = 0): Long = Calendar.getInstance(utc).apply {
        clear(); set(2026, Calendar.SEPTEMBER, day, hour, minute)
    }.timeInMillis
    @Test fun overnightBelongsToStartingWeekday() {
        val monday = QuietSchedule(true, setOf(Calendar.MONDAY), 22 * 60, 7 * 60)
        assertFalse(quietWindow(monday, date(7, 21), utc).active)
        assertTrue(quietWindow(monday, date(7, 22), utc).active)
        assertTrue(quietWindow(monday, date(8, 6, 59), utc).active)
        assertFalse(quietWindow(monday, date(8, 7), utc).active)
        assertEquals(date(14, 22), quietWindow(monday, date(8, 7), utc).nextBoundary)
    }
    @Test fun sameDayAndDisabledHaveExactBoundarySemantics() {
        val day = QuietSchedule(true, (1..7).toSet(), 9 * 60, 17 * 60)
        assertEquals(date(8, 9), quietWindow(day, date(8, 8), utc).nextBoundary)
        assertTrue(quietWindow(day, date(8, 9), utc).active)
        assertFalse(quietWindow(day, date(8, 17), utc).active)
        assertNull(quietWindow(day.copy(enabled = false), date(8, 12), utc).nextBoundary)
    }
    @Test fun equalTimesRepresentOneFullLocalDay() {
        val day = QuietSchedule(true, setOf(Calendar.MONDAY), 9 * 60, 9 * 60)
        assertTrue(quietWindow(day, date(8, 8), utc).active)
        assertFalse(quietWindow(day, date(8, 9), utc).active)
    }
    @Test fun emptyWeekdaysNeverActivate() {
        assertEquals(QuietWindow(false, null), quietWindow(QuietSchedule(enabled = true, weekdays = emptySet()), date(7, 23), utc))
    }
    @Test fun zoneChangeReevaluatesLocalHoursAtSameInstant() {
        val schedule = QuietSchedule(true, (1..7).toSet(), 9 * 60, 17 * 60)
        val instant = date(8, 5)
        assertFalse(quietWindow(schedule, instant, utc).active)
        assertTrue(quietWindow(schedule, instant, TimeZone.getTimeZone("Asia/Kolkata")).active)
    }
    @Test fun daylightSavingTransitionsUseLocalBoundaries() {
        val zone = TimeZone.getTimeZone("America/New_York")
        fun local(month: Int, day: Int, hour: Int) = Calendar.getInstance(zone).apply {
            clear(); set(2026, month, day, hour, 0)
        }.timeInMillis
        val schedule = QuietSchedule(true, (1..7).toSet(), 22 * 60, 7 * 60)
        for ((month, day) in listOf(Calendar.MARCH to 8, Calendar.NOVEMBER to 1)) {
            assertTrue(quietWindow(schedule, local(month, day, 6), zone).active)
            assertEquals(local(month, day, 7), quietWindow(schedule, local(month, day, 6), zone).nextBoundary)
            assertFalse(quietWindow(schedule, local(month, day, 7), zone).active)
        }
    }
}
