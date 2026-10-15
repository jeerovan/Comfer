package com.jeerovan.comfer

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.jeerovan.comfer.utils.CommonUtil.refreshWallpaper
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonIgnoreUnknownKeys

@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonIgnoreUnknownKeys
data class ImageData(
    val id:Int,
    val imageUrl: String,
    val depthUrl: String? = null,
    val spatialSceneUrl: String? = null,
)

internal enum class ImageWorkOutcome { SUCCESS, RETRY }

internal suspend fun runImageWorkPipeline(
    fetch: suspend () -> Boolean,
    download: suspend () -> Boolean,
): ImageWorkOutcome {
    if (!fetch()) return ImageWorkOutcome.RETRY
    return if (download()) ImageWorkOutcome.SUCCESS else ImageWorkOutcome.RETRY
}

internal suspend fun runScheduledImageWork(
    enabled: Boolean,
    manualChange: Boolean,
    frequency: String,
    lastSuccess: Long,
    now: Long,
    hasPendingImage: Boolean,
    fetch: suspend () -> Boolean,
    download: suspend () -> Boolean,
    recordSuccess: suspend () -> Unit,
    schedule: WallpaperSchedule = wallpaperSchedule(now),
    lastPeriod: String? = null,
    pendingPeriod: String? = null,
): ImageWorkOutcome {
    if (!enabled) return ImageWorkOutcome.SUCCESS
    // Daily follows solar boundaries, including delayed work, DST and restarts.
    // A stable period key also handles travel without rotating on tiny location updates.
    val due = lastSuccess <= 0 || now < lastSuccess || if (frequency == "Daily") {
        if (lastPeriod != null) lastPeriod != schedule.periodKey else lastSuccess < schedule.periodStart
    } else now - lastSuccess >= java.util.concurrent.TimeUnit.HOURS.toMillis(1)
    if (!manualChange && !hasPendingImage && !due) return ImageWorkOutcome.SUCCESS
    val reusePending = hasPendingImage && pendingPeriod == schedule.periodKey && !manualChange
    val outcome = runImageWorkPipeline(
        fetch = { if (reusePending) true else fetch() },
        download = download,
    )
    if (outcome == ImageWorkOutcome.SUCCESS) recordSuccess()
    return outcome
}

class ImageWorker(appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            (applicationContext as? ComferApp)?.initializeApplicationData()
            StartupCoordinator.awaitReady()
            WallpaperWorkCoordinator.runExclusive {
                val outcome = refreshWallpaper(applicationContext)
                com.jeerovan.comfer.spatial.LocalSpatialRepository.maintainCache(applicationContext)
                when (outcome) {
                    ImageWorkOutcome.SUCCESS -> Result.success()
                    ImageWorkOutcome.RETRY -> Result.retry()
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            Result.retry()
        } finally {
            WallpaperScheduler.schedule(applicationContext)
        }
    }
}
