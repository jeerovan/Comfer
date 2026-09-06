package com.jeerovan.comfer

import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class WallpaperScheduleTest {
    private val hour = TimeUnit.HOURS.toMillis(1)

    private class Fixture {
        var lastSuccess = 0L
        var fetched = 0
        var applied = 0
        var fetchSucceeds = true
        var applySucceeds = true
        var pending = false

        suspend fun run(now: Long, frequency: String = "Hourly", manual: Boolean = false,
                        enabled: Boolean = true): ImageWorkOutcome = runScheduledImageWork(
            enabled = enabled,
            manualChange = manual,
            frequency = frequency,
            lastSuccess = lastSuccess,
            now = now,
            hasPendingImage = pending,
            fetch = { fetched++; if (fetchSucceeds) pending = true; fetchSucceeds },
            download = { applied++; if (applySucceeds) pending = false; applySucceeds },
            recordSuccess = { lastSuccess = now },
        )
    }

    @Test
    fun hourlyRotationRepeatsWithoutAnyActivityCallbackIncludingMidnight() = runBlocking {
        val fixture = Fixture()
        for (h in 23L..26L) assertEquals(ImageWorkOutcome.SUCCESS, fixture.run(h * hour))
        assertEquals(4, fixture.fetched)
        assertEquals(4, fixture.applied)
    }

    @Test
    fun repeatedWorkerPollsDoNotRotateBeforeInterval() = runBlocking {
        val fixture = Fixture()
        fixture.run(10 * hour)
        fixture.run(10 * hour + hour / 3)
        fixture.run(11 * hour - 1)
        assertEquals(1, fixture.fetched)
        fixture.run(11 * hour)
        assertEquals(2, fixture.fetched)
    }

    @Test
    fun dailyRotationSurvivesSameClockHourOnFollowingDaysAndDelayedWork() = runBlocking {
        val fixture = Fixture()
        fixture.run(3 * hour, "Daily")
        fixture.run(26 * hour, "Daily")
        assertEquals(1, fixture.fetched)
        fixture.run(27 * hour, "Daily")
        fixture.run(80 * hour, "Daily") // Slept through the old 3 AM window.
        assertEquals(3, fixture.fetched)
    }

    @Test
    fun savedTimestampControlsRotationAfterProcessRestart() = runBlocking {
        val firstProcess = Fixture()
        firstProcess.run(10 * hour)
        val nextProcess = Fixture().apply { lastSuccess = firstProcess.lastSuccess }
        nextProcess.run(10 * hour + 1)
        assertEquals(0, nextProcess.fetched)
        nextProcess.run(11 * hour)
        assertEquals(1, nextProcess.fetched)
    }

    @Test
    fun failedFetchDoesNotConsumeDueTime() = runBlocking {
        val fixture = Fixture().apply { fetchSucceeds = false }
        assertEquals(ImageWorkOutcome.RETRY, fixture.run(10 * hour))
        assertEquals(0L, fixture.lastSuccess)
        assertEquals(0, fixture.applied)
        fixture.fetchSucceeds = true
        assertEquals(ImageWorkOutcome.SUCCESS, fixture.run(10 * hour + 1))
        assertEquals(2, fixture.fetched)
    }

    @Test
    fun failedApplyRetriesPendingImageThenResumesRotation() = runBlocking {
        val fixture = Fixture().apply { applySucceeds = false }
        assertEquals(ImageWorkOutcome.RETRY, fixture.run(10 * hour))
        assertEquals(0L, fixture.lastSuccess)
        fixture.applySucceeds = true
        assertEquals(ImageWorkOutcome.SUCCESS, fixture.run(10 * hour + 1))
        assertEquals(1, fixture.fetched)
        fixture.run(11 * hour + 1)
        assertEquals(2, fixture.fetched)
        assertEquals(3, fixture.applied)
    }

    @Test
    fun pendingImageFromOldVersionIsRecoveredBeforeNextInterval() = runBlocking {
        val fixture = Fixture().apply { lastSuccess = 10 * hour; pending = true }
        fixture.run(10 * hour + 1)
        assertEquals(0, fixture.fetched)
        assertEquals(1, fixture.applied)
    }

    @Test
    fun manualRefreshBypassesIntervalAndFrequencyChangesTakeEffect() = runBlocking {
        val fixture = Fixture()
        fixture.run(10 * hour, "Daily")
        fixture.run(10 * hour + 1, "Daily", manual = true)
        assertEquals(2, fixture.fetched)
        fixture.run(11 * hour + 1, "Hourly")
        assertEquals(3, fixture.fetched)
    }

    @Test
    fun disabledRotationDoesNotFetchOrApplyPendingPhotos() = runBlocking {
        val fixture = Fixture().apply { pending = true }
        fixture.run(10 * hour, enabled = false)
        assertEquals(0, fixture.fetched)
        assertEquals(0, fixture.applied)
        assertEquals(0L, fixture.lastSuccess)
    }

    @Test
    fun BackwardClockChangeDoesNotBlockRotation() = runBlocking {
        val fixture = Fixture().apply { lastSuccess = 20 * hour }
        fixture.run(10 * hour)
        assertEquals(1, fixture.fetched)
    }

    @Test(expected = CancellationException::class)
    fun cancellationNeverRecordsSuccess(): Unit = runBlocking {
        runScheduledImageWork(true, false, "Hourly", 0, hour, false,
            fetch = { true },
            download = { throw CancellationException("worker stopped") },
            recordSuccess = { throw AssertionError("Cancelled work cannot advance schedule") },
        )
    }
}
