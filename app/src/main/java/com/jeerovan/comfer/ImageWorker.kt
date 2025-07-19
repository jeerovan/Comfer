package com.jeerovan.comfer

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
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
        return try {
            val calendar = Calendar.getInstance()
            val hour = calendar.get(Calendar.HOUR_OF_DAY)
            val name = PreferenceManager.getUsername(applicationContext)
            val client = HttpClient(CIO) {
                install(ContentNegotiation) {
                    json(Json {
                        ignoreUnknownKeys = true
                    })
                }
            }
            val response: ImageData = client.get("https://jeerovan.com/api") {
                parameter("name",name)
                parameter("hour", hour)
            }.body()

            PreferenceManager.saveImageData(applicationContext, response)
            client.close()
            Log.d("ImageWorker", "Successfully fetched and saved image URL: ${response.imageUrl}")
            Result.success()
        } catch (e: Exception) {
            Log.e("ImageWorker", "Error fetching image", e)
            Result.failure()
        }
    }
}
