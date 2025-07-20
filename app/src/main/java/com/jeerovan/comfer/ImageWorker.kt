package com.jeerovan.comfer

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.serialization.kotlinx.json.*
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
        val hour = PreferenceManager.getHour(applicationContext)
        if (hour > 0) {
            return try {
                val name = PreferenceManager.getUsername(applicationContext)
                val client = HttpClient(OkHttp) {
                    install(ContentNegotiation) {
                        json(Json {
                            ignoreUnknownKeys = true
                        })
                    }
                }
                val response: ImageData = client.get("https://comfer.jeerovan.com/api") {
                    parameter("name", name)
                    parameter("hour", hour)
                }.body()
                PreferenceManager.saveImageData(applicationContext, response)
                PreferenceManager.setHour(applicationContext,hour)
                client.close()
                Result.success()
            } catch (e: Exception) {
                Log.e("ImageWorker", "Error fetching image", e)
                Result.failure()
            }
        }
        else { return Result.success()}
    }
}
