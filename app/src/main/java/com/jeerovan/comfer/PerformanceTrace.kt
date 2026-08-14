package com.jeerovan.comfer

import android.os.Build
import android.os.Trace
import java.util.concurrent.atomic.AtomicInteger

internal object PerformanceTrace {
    private val activeAppRefreshes = AtomicInteger()
    private val maxActiveAppRefreshes = AtomicInteger()
    private val totalAppRefreshes = AtomicInteger()
    private val activeWallpaperPipelines = AtomicInteger()
    private val activeNotificationSyncs = AtomicInteger()
    private val activeContactQueries = AtomicInteger()
    private val iconLoads = AtomicInteger()

    fun begin(section: String) = Trace.beginSection(section.take(127))
    fun end() = Trace.endSection()
    fun beginAsync(section: String, cookie: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Trace.beginAsyncSection(section.take(127), cookie)
        }
    }
    fun endAsync(section: String, cookie: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Trace.endAsyncSection(section.take(127), cookie)
        }
    }

    fun appRefreshStarted(): Int {
        totalAppRefreshes.incrementAndGet()
        val active = updateCounter("activeAppRefresh", activeAppRefreshes, 1)
        maxActiveAppRefreshes.accumulateAndGet(active, ::maxOf)
        return active
    }
    fun appRefreshFinished() = updateCounter("activeAppRefresh", activeAppRefreshes, -1)
    fun wallpaperStarted() = updateCounter("activeWallpaper", activeWallpaperPipelines, 1)
    fun wallpaperFinished() = updateCounter("activeWallpaper", activeWallpaperPipelines, -1)
    fun notificationSyncStarted() = updateCounter("activeNotificationSync", activeNotificationSyncs, 1)
    fun notificationSyncFinished() = updateCounter("activeNotificationSync", activeNotificationSyncs, -1)
    fun contactQueryStarted() = updateCounter("activeContactQuery", activeContactQueries, 1)
    fun contactQueryFinished() = updateCounter("activeContactQuery", activeContactQueries, -1)
    fun resetIconLoads() {
        iconLoads.set(0)
        counter("iconLoads", 0)
    }
    fun iconLoaded() = updateCounter("iconLoads", iconLoads, 1)

    internal data class AppRefreshStats(val total: Int, val active: Int, val maxActive: Int)

    internal fun appRefreshStats() = AppRefreshStats(
        total = totalAppRefreshes.get(),
        active = activeAppRefreshes.get(),
        maxActive = maxActiveAppRefreshes.get(),
    )

    internal fun resetAppRefreshStats() {
        check(activeAppRefreshes.get() == 0) { "Cannot reset while app refresh is active" }
        totalAppRefreshes.set(0)
        maxActiveAppRefreshes.set(0)
    }
    fun counter(name: String, value: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) Trace.setCounter(name, value.toLong())
    }

    private fun updateCounter(name: String, counter: AtomicInteger, delta: Int): Int {
        val value = counter.addAndGet(delta).coerceAtLeast(0)
        if (value == 0) counter.set(0)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) Trace.setCounter(name, value.toLong())
        return value
    }
}
