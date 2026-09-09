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
}
