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
        if(saveCrashes) {
            Thread.setDefaultUncaughtExceptionHandler(CrashHandler(this))
            LogcatRecorder(this).startLogging()
        }
        setupImageWorker()
        // Pre-warm SharedPreferences used by WidgetHostScreen composables on a
        // background thread so their first getSharedPreferences() call (which
        // parses XML from disk) never blocks the Main thread during composition.
        warmWidgetPrefs()
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

    private fun warmWidgetPrefs() {
        val widgetPrefsNames = listOf("widgets_center", "widgets_prefs_left", "widgets_prefs_right")
        Thread {
            for (name in widgetPrefsNames) {
                try { getSharedPreferences(name, MODE_PRIVATE) } catch (_: Exception) {}
            }
        }.start()
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
