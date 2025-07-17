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
data class ImageResponse(val imageUrl: String)

class ImageWorker(appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val calendar = Calendar.getInstance()
            val date = calendar.get(Calendar.DAY_OF_MONTH)
            val hour = calendar.get(Calendar.HOUR_OF_DAY)

            val client = HttpClient(CIO) {
                install(ContentNegotiation) {
                    json(Json {
                        ignoreUnknownKeys = true
                    })
                }
            }

            val response: ImageResponse = client.get("https://example.com/image") {
                parameter("date", date)
                parameter("hour", hour)
            }.body()

            PreferenceManager.setImageUrl(applicationContext, response.imageUrl)
            client.close()
            Log.d("ImageWorker", "Successfully fetched and saved image URL: ${response.imageUrl}")
            Result.success()
        } catch (e: Exception) {
            Log.e("ImageWorker", "Error fetching image", e)
            Result.failure()
        }
    }
}
