package com.jeerovan.comfer

import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppRefreshBurstStressTest {
    @Test
    fun hundredRefreshRequestsStaySingleFlightAndCoalesce() = runBlocking {
        val application = ApplicationProvider.getApplicationContext<ComferApp>()
        application.initializeApplicationData()
        StartupCoordinator.awaitReady()
        val store = ViewModelStore()
        try {
            val viewModel = ViewModelProvider(
                store,
                ViewModelProvider.AndroidViewModelFactory.getInstance(application),
            )[AppInfoViewModel::class.java]

            awaitRefreshIdle(requireCompleted = true)
            PerformanceTrace.resetAppRefreshStats()
            val startedAt = SystemClock.elapsedRealtime()
            repeat(100) { viewModel.reloadList() }
            awaitRefreshIdle(requireCompleted = true)
            val elapsedMs = SystemClock.elapsedRealtime() - startedAt
            val stats = PerformanceTrace.appRefreshStats()
            Log.i(TAG, "requests=100 total=${stats.total} maxActive=${stats.maxActive} elapsedMs=$elapsedMs")

            assertTrue("Expected at most two coalesced refreshes, got ${stats.total}", stats.total in 1..2)
            assertEquals(1, stats.maxActive)
            assertEquals(0, stats.active)
        } finally {
            store.clear()
        }
    }

    private suspend fun awaitRefreshIdle(requireCompleted: Boolean) {
        withTimeout(60_000) {
            do {
                delay(50)
                val stats = PerformanceTrace.appRefreshStats()
            } while (stats.active != 0 || (requireCompleted && stats.total == 0))
        }
    }

    private companion object {
        const val TAG = "AppRefreshBurstStress"
    }
}
