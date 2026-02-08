package com.jeerovan.comfer

import android.app.Application
import android.os.StrictMode
import androidx.work.*
import coil.ImageLoaderFactory
import java.util.concurrent.TimeUnit
import coil.ImageLoader
import com.revenuecat.purchases.LogLevel
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.PurchasesConfiguration

const val saveCrashes = false
const val saveLogs = false
const val isTesting = true
class ComferApp : Application(), ImageLoaderFactory {

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            StrictMode.setThreadPolicy(
                StrictMode.ThreadPolicy.Builder()
                    .detectDiskReads()
                    .detectDiskWrites()
                    .detectNetwork()
                    .detectCustomSlowCalls()
                    .penaltyLog()
                    .build()
            )
        }
        if(saveCrashes) {
            Thread.setDefaultUncaughtExceptionHandler(CrashHandler(this))
            LogcatRecorder(this).startLogging()
        }
        Purchases.logLevel = LogLevel.ERROR
        Purchases.configure(PurchasesConfiguration.Builder(this, "goog_alczWNGIWABONRuXvtRSKpPJFXi").build())
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

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .crossfade(true) // Optional: Add a crossfade animation
            // Add any other global configurations for Coil here
            .build()
    }
}
