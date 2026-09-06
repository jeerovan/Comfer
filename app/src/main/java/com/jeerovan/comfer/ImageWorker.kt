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
    val imageUrl: String
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
): ImageWorkOutcome {
    if (!enabled) return ImageWorkOutcome.SUCCESS
    val interval = java.util.concurrent.TimeUnit.HOURS.toMillis(
        if (frequency == "Daily") 24 else 1,
    )
    // A timestamp survives midnight, missed worker windows and process restarts.
    // A backwards clock adjustment must not suspend rotation indefinitely.
    val due = lastSuccess <= 0 || now < lastSuccess || now - lastSuccess >= interval
    if (!manualChange && !hasPendingImage && !due) return ImageWorkOutcome.SUCCESS
    val outcome = runImageWorkPipeline(
        fetch = { if (hasPendingImage && !manualChange) true else fetch() },
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
                when (refreshWallpaper(applicationContext)) {
                    ImageWorkOutcome.SUCCESS -> Result.success()
                    ImageWorkOutcome.RETRY -> Result.retry()
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            Result.retry()
        }
    }
}
