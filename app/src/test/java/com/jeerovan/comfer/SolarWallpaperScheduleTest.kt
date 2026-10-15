package com.jeerovan.comfer

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.*
import org.junit.Test

class SolarWallpaperScheduleTest {
    private fun at(value: String) = Instant.parse(value).toEpochMilli()
    private val utc = ZoneOffset.UTC

    @Test fun newYorkSummerUsesActualSunriseAndSunset() {
        val times = solarTimes(LocalDate.parse("2026-06-21"), ZoneId.of("America/New_York"), 40.7128, -74.0060)
        // NYC summer solstice: about 05:25/20:31 EDT, well outside 07:00/19:00.
        assertTrue(times.sunrise!! in at("2026-06-21T09:20:00Z")..at("2026-06-21T09:30:00Z"))
        assertTrue(times.sunset!! in at("2026-06-22T00:25:00Z")..at("2026-06-22T00:36:00Z"))
    }

    @Test fun cloudChangesExactlyAtEachBoundaryAndKeepsNightAcrossMidnight() {
        val before = wallpaperSchedule(at("2026-06-21T05:59:59.999Z"), utc)
        val sunrise = wallpaperSchedule(at("2026-06-21T06:00:00Z"), utc)
        val beforeSunset = wallpaperSchedule(at("2026-06-21T17:59:59.999Z"), utc)
        val sunset = wallpaperSchedule(at("2026-06-21T18:00:00Z"), utc)
        val midnight = wallpaperSchedule(at("2026-06-22T00:00:00Z"), utc)
        assertFalse(before.isDay)
        assertTrue(sunrise.isDay)
        assertEquals(sunrise.periodKey, beforeSunset.periodKey)
        assertFalse(sunset.isDay)
        assertEquals(sunset.periodKey, midnight.periodKey)
        assertEquals(at("2026-06-22T06:00:00Z"), midnight.nextChange)
    }

    @Test fun localImagesHaveOneSunrisePeriodAndNeverASunsetPeriod() {
        val morning = wallpaperSchedule(at("2026-06-21T06:00:00Z"), utc, ownWallpapers = true)
        val night = wallpaperSchedule(at("2026-06-22T02:00:00Z"), utc, ownWallpapers = true)
        val next = wallpaperSchedule(at("2026-06-22T06:00:00Z"), utc, ownWallpapers = true)
        assertEquals(morning.periodKey, night.periodKey)
        assertNotEquals(night.periodKey, next.periodKey)
        assertEquals(at("2026-06-22T06:00:00Z"), morning.nextChange)
    }

    @Test fun solarPhaseOverridesTheServersFixedClockHour() {
        val zone = ZoneId.of("America/New_York")
        val earlySummerDay = wallpaperSchedule(at("2026-06-21T10:00:00Z"), zone, 40.7128, -74.0060)
        val earlyWinterNight = wallpaperSchedule(at("2026-12-21T22:30:00Z"), zone, 40.7128, -74.0060)
        assertEquals(12, earlySummerDay.cloudRequestHour) // 06:00 local, already daylight.
        assertEquals(0, earlyWinterNight.cloudRequestHour) // 17:30 local, already dark.
    }

    @Test fun timezoneAndDaylightSavingChangesDoNotUseA24HourInterval() {
        val zone = ZoneId.of("America/New_York")
        val spring = wallpaperSchedule(at("2026-03-07T12:00:00Z"), zone, ownWallpapers = true)
        val fall = wallpaperSchedule(at("2026-10-31T12:00:00Z"), zone, ownWallpapers = true)
        assertEquals(23 * 3_600_000L, spring.nextChange - spring.periodStart)
        assertEquals(25 * 3_600_000L, fall.nextChange - fall.periodStart)
        val india = wallpaperSchedule(at("2026-06-21T00:30:00Z"), ZoneId.of("Asia/Kolkata"))
        assertTrue(india.isDay)
    }

    @Test fun dateLineAndFractionalOffsetLocationsKeepEventsOnTheirCivilDate() {
        for ((zone, lat, lon) in listOf(Triple("Pacific/Kiritimati", 1.87, -157.4),
                Triple("Asia/Kolkata", 12.97, 77.59), Triple("Pacific/Auckland", -36.85, 174.76))) {
            val date = LocalDate.parse("2026-06-21")
            val timeZone = ZoneId.of(zone)
            val times = solarTimes(date, timeZone, lat, lon)
            assertEquals(date, Instant.ofEpochMilli(times.sunrise!!).atZone(timeZone).toLocalDate())
            assertEquals(date, Instant.ofEpochMilli(times.sunset!!).atZone(timeZone).toLocalDate())
        }
    }

    @Test fun polarDayAndNightHaveSafeDailyRecoveryWithoutInventingOppositeLight() {
        val zone = ZoneId.of("Europe/Oslo")
        for ((time, daylight) in listOf("2026-06-21T12:00:00Z" to true, "2026-12-21T12:00:00Z" to false)) {
            val now = at(time)
            val schedule = wallpaperSchedule(now, zone, 78.22, 15.65)
            assertEquals(daylight, schedule.isDay)
            assertTrue(schedule.periodStart <= now)
            assertTrue(schedule.nextChange > now)
            assertTrue(schedule.nextChange - now <= 25 * 3_600_000L)
        }
    }

    @Test fun missingOrInvalidLocationUsesLocalFallbackWithoutFailing() {
        val now = at("2026-06-21T14:00:00Z")
        val expected = wallpaperSchedule(now, utc)
        for ((lat, lon) in listOf(Double.NaN to 0.0, 91.0 to 0.0, 0.0 to Double.POSITIVE_INFINITY)) {
            assertEquals(expected, wallpaperSchedule(now, utc, lat, lon))
        }
    }

    @Test fun smallLocationUpdatesDoNotCreateAnotherRotationInTheSamePeriod() {
        val now = at("2026-06-21T14:00:00Z")
        val old = wallpaperSchedule(now, utc, 12.97, 77.59)
        val updated = wallpaperSchedule(now, utc, 12.971, 77.591)
        assertEquals(old.periodKey, updated.periodKey)
    }
}
