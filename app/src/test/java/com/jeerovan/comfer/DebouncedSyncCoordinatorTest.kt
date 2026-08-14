package com.jeerovan.comfer

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class DebouncedSyncCoordinatorTest {
    @Test
    fun hundredEventBurstRunsOneSync() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        var syncCount = 0
        val coordinator = DebouncedSyncCoordinator(scope, 20L) { syncCount++ }
        try {
            coordinator.start()
            repeat(100) { coordinator.request() }
            delay(100)
            assertEquals(1, syncCount)
        } finally {
            coordinator.stop()
            scope.cancel()
        }
    }

    @Test
    fun stopCancelsPendingSync() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        var syncCount = 0
        val coordinator = DebouncedSyncCoordinator(scope, 100L) { syncCount++ }
        try {
            coordinator.start()
            coordinator.request()
            coordinator.stop()
            delay(150)
            assertEquals(0, syncCount)
        } finally {
            scope.cancel()
        }
    }
}
