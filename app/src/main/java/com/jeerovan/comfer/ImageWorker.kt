package com.jeerovan.comfer

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.jeerovan.comfer.utils.CommonUtil.downloadImage
import com.jeerovan.comfer.utils.CommonUtil.fetchImageData
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.*

@Serializable
data class ImageData(
    val id:Int,
    val imageUrl: String,
    val theme: String,
    val color: String,
    val position: String,
    val paddingTop: Int,
    val paddingStart: Int,
    val paddingEnd: Int,
    val paddingBottom: Int)

class ImageWorker(appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        fetchImageData(applicationContext)
        delay(500)
        downloadImage(applicationContext)
        delay(500)
        return Result.success()
    }
}
