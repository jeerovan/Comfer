package com.jeerovan.comfer

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Serializes process-wide wallpaper download, decode, palette, and Binder work. */
object WallpaperWorkCoordinator {
    private val mutex = Mutex()

    suspend fun <T> runExclusive(block: suspend () -> T): T {
        return withContext(Dispatchers.IO) {
            mutex.withLock {
                val traceCookie = PerformanceTrace.wallpaperStarted()
                PerformanceTrace.beginAsync("wallpaperPipeline", traceCookie)
                try {
                    block()
                } finally {
                    PerformanceTrace.endAsync("wallpaperPipeline", traceCookie)
                    PerformanceTrace.wallpaperFinished()
                }
            }
        }
    }
}
