package com.jeerovan.comfer

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InventoryRefreshTrackerTest {
    @Test
    fun cancelledRequestRemainsPending() {
        val tracker = InventoryRefreshTracker()
        val cancelled = tracker.snapshot(hasInventory = false)

        val retry = tracker.snapshot(hasInventory = true)

        assertTrue(cancelled.shouldReload)
        assertTrue(retry.shouldReload)
    }

    @Test
    fun eventDuringRefreshIsNotClearedByOlderCompletion() {
        val tracker = InventoryRefreshTracker()
        val active = tracker.snapshot(hasInventory = false)

        tracker.requestReload()
        tracker.markCompleted(active.version)

        assertTrue(tracker.snapshot(hasInventory = true).shouldReload)
    }

    @Test
    fun completedLatestRequestAllowsVisualOnlyRefresh() {
        val tracker = InventoryRefreshTracker()
        val active = tracker.snapshot(hasInventory = false)
        tracker.markCompleted(active.version)

        assertFalse(tracker.snapshot(hasInventory = true).shouldReload)
    }
}
