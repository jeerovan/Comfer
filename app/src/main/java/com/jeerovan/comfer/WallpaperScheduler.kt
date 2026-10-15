package com.jeerovan.comfer

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

internal fun wallpaperSchedule(context: Context, now: Long): WallpaperSchedule {
    val directory = PreferenceManager.getWallpaperDirectory(context)
    val schedule = wallpaperSchedule(now,
        latitude = PreferenceManager.getString(context, "weather_latitude", null)?.toDoubleOrNull(),
        longitude = PreferenceManager.getString(context, "weather_longitude", null)?.toDoubleOrNull(),
        ownWallpapers = directory != null)
    return schedule.copy(periodKey = "${directory ?: "cloud"}:${schedule.periodKey}")
}

/** A solar alarm supplements periodic recovery work; no extra permission or service. */
internal object WallpaperScheduler {
    const val ACTION = "com.jeerovan.comfer.WALLPAPER_SOLAR_BOUNDARY"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun request(context: Context, refresh: Boolean = false) {
        val app = context.applicationContext
        scope.launch {
            StartupCoordinator.awaitReady()
            schedule(app)
            if (refresh && PreferenceManager.getAutoWallpapers(app)) enqueue(app)
        }
    }

    @Synchronized
    fun schedule(context: Context, now: Long = System.currentTimeMillis()): Long? {
        return try {
            val alarms = context.getSystemService(AlarmManager::class.java)
            val intent = PendingIntent.getBroadcast(context, 0,
                Intent(context, WallpaperBoundaryReceiver::class.java).setAction(ACTION)
                    .addFlags(Intent.FLAG_RECEIVER_FOREGROUND),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            if (!PreferenceManager.getAutoWallpapers(context) || PreferenceManager.getWallpaperFrequency(context) != "Daily") {
                alarms.cancel(intent)
                null
            } else {
                val next = wallpaperSchedule(context, now).nextChange
                // Android may defer background delivery for power management.
                // Home also refreshes at the boundary while it is visible.
                alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, intent)
                next
            }
        } catch (error: Exception) {
            Log.e("WallpaperScheduler", "Could not schedule the next solar boundary", error)
            null // Periodic work and foreground checks can recover.
        }
    }

    internal fun enqueue(context: Context) {
        try {
            WorkManager.getInstance(context).enqueueUniqueWork("WallpaperSolarBoundary", ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<ImageWorker>()
                    .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
                    .build()).result.get(2, TimeUnit.SECONDS)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: LinkageError) {
            Log.e("WallpaperScheduler", "Wallpaper WorkManager is incompatible", error)
        } catch (error: Exception) {
            Log.e("WallpaperScheduler", "Could not enqueue solar wallpaper work", error)
        }
    }
}

class WallpaperBoundaryReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in setOf(WallpaperScheduler.ACTION, Intent.ACTION_BOOT_COMPLETED,
                Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_MY_PACKAGE_REPLACED)) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                (context.applicationContext as? ComferApp)?.initializeApplicationData()
                withTimeout(6_000) { StartupCoordinator.awaitReady() }
                WallpaperScheduler.schedule(context.applicationContext)
                if (PreferenceManager.getAutoWallpapers(context)) WallpaperScheduler.enqueue(context.applicationContext)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Log.e("WallpaperScheduler", "Could not reconcile solar wallpaper work", error)
            } finally {
                pending.finish()
            }
        }
    }
}
