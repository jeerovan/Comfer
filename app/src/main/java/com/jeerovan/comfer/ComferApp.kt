package com.jeerovan.comfer

import android.app.Application
import androidx.work.*
import com.revenuecat.purchases.LogLevel
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.PurchasesConfiguration
import java.util.concurrent.TimeUnit

const val saveCrashes = false
const val isTesting = true
class ComferApp : Application() {

    override fun onCreate() {
        super.onCreate()
        if(saveCrashes) {
            Thread.setDefaultUncaughtExceptionHandler(CrashHandler(this))
        }
        setupImageWorker()
       }

    private fun setupImageWorker() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val periodicWorkRequest = PeriodicWorkRequestBuilder<ImageWorker>(20, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(applicationContext).enqueueUniquePeriodicWork(
            "ImageWorker",
            ExistingPeriodicWorkPolicy.REPLACE,
            periodicWorkRequest
        )
    }
}
