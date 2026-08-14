package com.jeerovan.comfer

import java.util.concurrent.atomic.AtomicLong

internal data class InventoryRefreshRequest(
    val version: Long,
    val shouldReload: Boolean
)

/**
 * Tracks inventory work with monotonic versions. A cancelled refresh never
 * marks its request complete, and a package event arriving during a refresh
 * remains pending for the next generation.
 */
internal class InventoryRefreshTracker {
    private val requestedVersion = AtomicLong(1L)
    private val completedVersion = AtomicLong(0L)

    fun requestReload() {
        requestedVersion.incrementAndGet()
    }

    fun snapshot(hasInventory: Boolean): InventoryRefreshRequest {
        val requested = requestedVersion.get()
        return InventoryRefreshRequest(
            version = requested,
            shouldReload = !hasInventory || requested > completedVersion.get()
        )
    }

    fun markCompleted(version: Long) {
        while (true) {
            val completed = completedVersion.get()
            if (completed >= version || completedVersion.compareAndSet(completed, version)) {
                return
            }
        }
    }
}
