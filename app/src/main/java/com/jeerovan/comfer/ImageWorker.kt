package com.jeerovan.comfer

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.jeerovan.comfer.utils.CommonUtil.downloadImage
import com.jeerovan.comfer.utils.CommonUtil.fetchImageData
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

class ImageWorker(appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            (applicationContext as? ComferApp)?.initializeApplicationData()
            StartupCoordinator.awaitReady()
            WallpaperWorkCoordinator.runExclusive {
                when (runImageWorkPipeline(
                    fetch = { fetchImageData(applicationContext) },
                    download = { downloadImage(applicationContext) },
                )) {
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
