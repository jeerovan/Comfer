package com.jeerovan.comfer

import android.content.Intent
import android.app.PendingIntent
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class WallpaperSolarAlarmTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val keys = listOf(PreferenceManager.AUTO_WALLPAPER, PreferenceManager.WALLPAPER_FREQUENCY,
        PreferenceManager.WALLPAPER_DIRECTORY, PreferenceManager.BATTERY_SAVER_MODE,
        "weather_latitude", "weather_longitude", "solar_alarm_test_revision")
    private lateinit var original: Map<String, String?>

    @Before fun setup() = runBlocking {
        check(context.packageName.endsWith(".notificationtest"))
        withTimeout(25_000) { StartupCoordinator.awaitReady() }
        original = keys.associateWith { PreferenceManager.getString(context, it, null) }
        PreferenceManager.setBatterySaver(context, false)
        PreferenceManager.setAutoWallpapers(context, false)
        WallpaperScheduler.schedule(context)
        PreferenceManager.setWallpaperDirectory(context, null)
        PreferenceManager.clear(context, "weather_latitude")
        PreferenceManager.clear(context, "weather_longitude")
        PreferenceManager.setWallpaperFrequency(context, "Daily")
    }

    @After fun restore() {
        if (!::original.isInitialized) return
        PreferenceManager.setAutoWallpapers(context, false)
        WallpaperScheduler.schedule(context)
        original.forEach { (key, value) -> PreferenceManager.setString(context, key, value) }
    }

    @Test fun cloudSchedulesSunsetButLocalFolderSchedulesTheNextSunrise() {
        val zone = ZoneId.systemDefault()
        // Future fixtures avoid changing the device clock or firing a real download.
        val day = LocalDate.now(zone).plusDays(2)
        val noon = day.atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
        PreferenceManager.setAutoWallpapers(context, true)
        val sunset = day.atTime(18, 0).atZone(zone).toInstant().toEpochMilli()
        assertEquals(sunset, WallpaperScheduler.schedule(context, noon))
        assertEquals(listOf(sunset), registeredTriggers())

        PreferenceManager.setWallpaperDirectory(context, "content://test/local-wallpapers")
        val sunrise = day.plusDays(1).atTime(6, 0).atZone(zone).toInstant().toEpochMilli()
        assertEquals(sunrise, WallpaperScheduler.schedule(context, noon))
        assertEquals("Source changes replace the existing alarm", listOf(sunrise), registeredTriggers())
    }

    @Test fun savedLocationMovesTheAlarmAwayFromFixedClockHours() {
        val zone = ZoneId.systemDefault()
        val day = LocalDate.now(zone).plusDays(2)
        val noon = day.atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
        PreferenceManager.setString(context, "weather_latitude", "12.9716")
        PreferenceManager.setString(context, "weather_longitude", "77.5946")
        PreferenceManager.setAutoWallpapers(context, true)
        val expected = wallpaperSchedule(noon, zone, 12.9716, 77.5946).nextChange
        assertEquals(expected, WallpaperScheduler.schedule(context, noon))
        assertEquals(listOf(expected), registeredTriggers())
        assertNotEquals(day.atTime(18, 0).atZone(zone).toInstant().toEpochMilli(), expected)

        PreferenceManager.setWallpaperFrequency(context, "Hourly")
        assertNull(WallpaperScheduler.schedule(context, noon))
        assertTrue("Hourly keeps its existing worker without a solar alarm", registeredTriggers().isEmpty())
    }

    @Test fun deliveredBoundaryRechecksTheLatestAutoWallpaperChoice() {
        val zone = ZoneId.systemDefault()
        val future = LocalDate.now(zone).plusDays(2).atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
        PreferenceManager.setAutoWallpapers(context, true)
        WallpaperScheduler.schedule(context, future)
        assertEquals(1, registeredTriggers().size)
        PreferenceManager.setAutoWallpapers(context, false)
        assertFalse(PreferenceManager.getAutoWallpapers(context))
        val alarm = PendingIntent.getBroadcast(context, 0,
            Intent(context, WallpaperBoundaryReceiver::class.java).setAction(WallpaperScheduler.ACTION),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)
        assertNotNull("Deliver the registered alarm's actual PendingIntent", alarm)
        alarm!!.send()
        val deadline = SystemClock.uptimeMillis() + 10_000
        while (registeredTriggers().isNotEmpty() && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(50)
        assertTrue("A stale alarm must be cancelled after Auto is disabled", registeredTriggers().isEmpty())
    }

    @Test fun newerChoicesRemainVisibleWhileOlderBatchesAreSaved() = runBlocking {
        for (index in 0 until 100) {
            val enabled = index % 2 == 0
            PreferenceManager.setAutoWallpapers(context, enabled)
            delay(3)
            assertEquals("A completed older save must not revert a newer Auto choice", enabled,
                PreferenceManager.getAutoWallpapers(context))
        }
        val revision = SystemClock.elapsedRealtimeNanos().toString()
        PreferenceManager.setString(context, "solar_alarm_test_revision", revision)
        withTimeout(5_000) {
            context.settingsDataStore.data.first { preferences ->
                preferences.asMap().any { it.key.name == "solar_alarm_test_revision" && it.value.toString() == revision }
            }
        }
        PreferenceManager.reload(context)
        assertFalse("The newest choice must also survive a durable reload", PreferenceManager.getAutoWallpapers(context))
    }

    private fun registeredTriggers(): List<Long> {
        val dump = ParcelFileDescriptor.AutoCloseInputStream(
            instrumentation.uiAutomation.executeShellCommand("dumpsys alarm")).bufferedReader().use { it.readText() }
        // Android 37 calls the requested wall-clock trigger "origWhen" and
        // prints whenElapsed after it. Keep checking the active alarm and tag,
        // rather than cancellation history or an app-only return value.
        val pattern = Regex("(?m)^\\s*RTC_WAKEUP #\\d+: Alarm\\{[^\\n]*(?:when|origWhen) (\\d+) [^\\n]*" +
            Regex.escape(context.packageName) + "[^\\n]*\\}\\n\\s+tag=\\*walarm\\*:" + Regex.escape(WallpaperScheduler.ACTION))
        return pattern.findAll(dump).map { it.groupValues[1].toLong() }.toList()
    }
}
