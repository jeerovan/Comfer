package com.jeerovan.comfer

import android.app.Application
import android.os.StrictMode
import androidx.work.*
import coil.ImageLoaderFactory
import java.util.concurrent.TimeUnit
import coil.ImageLoader

const val saveCrashes = false
const val saveLogs = false
class ComferApp : Application(), ImageLoaderFactory {

    override fun onCreate() {
        super.onCreate()
        // One-time import of legacy SharedPreferences into Room + DataStore.
        PrefMigrator.runOnce(this)
        // Load scalar settings snapshot (DataStore-backed) for sync reads.
        PreferenceManager.load(this)
        if(saveCrashes) {
            Thread.setDefaultUncaughtExceptionHandler(CrashHandler(this))
            LogcatRecorder(this).startLogging()
        }
        setupImageWorker()
    }

    private fun setupImageWorker() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .build()

        val periodicWorkRequest = PeriodicWorkRequestBuilder<ImageWorker>(20, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(applicationContext).enqueueUniquePeriodicWork(
            "ImageWorker",
            ExistingPeriodicWorkPolicy.KEEP,
            periodicWorkRequest
        )
    }

    // App-scoped singleton: the 3 AppWidgetHosts + AppWidgetManager are created
    // ONCE per process and reused across Activity recreations, avoiding repeated
    // Binder registration and host leaks.
    val widgetHostManager: WidgetHostManager by lazy {
        WidgetHostManager(applicationContext).apply { initHosts() }
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .crossfade(true) // Optional: Add a crossfade animation
            // Add any other global configurations for Coil here
            .build()
    }
}
