package com.jeerovan.comfer.utils

import android.app.WallpaperManager
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Build
import android.util.Log
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import coil.Coil
import coil.request.ImageRequest
import coil.request.ImageResult
import com.jeerovan.comfer.ImageData
import com.jeerovan.comfer.PreferenceManager
import com.jeerovan.comfer.toBitmap
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

object CommonUtil {

    fun randomCode(input: String, length: Int = 6): String {
        // Create SHA-256 hash
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(input.toByteArray())
        val hexString = hashBytes.joinToString("") { "%02x".format(it) }

        // Convert to alphanumeric and truncate
        return hexString
            .filter { it.isLetterOrDigit() }
            .take(length)
            .lowercase()
    }
    fun stringToColor(colorString: String): Color {
        return when (colorString) {
            "White" -> Color.White
            "Black" -> Color.Black
            "Red" -> Color.Red
            "Green" -> Color.Green
            "Blue" -> Color.Blue
            "Yellow" -> Color.Yellow
            "Cyan" -> Color.Cyan
            "Magenta" -> Color.Magenta
            "Gray" -> Color.Gray
            "DarkGray" -> Color.DarkGray
            "LightGray" -> Color.LightGray
            // Add more colors as needed
            else -> Color.Unspecified // A default or error case
        }
    }
    fun alignmentFromString(alignmentString:String):Alignment {
        return when (alignmentString) {
            "Center" -> Alignment.Center
            "TopStart" -> Alignment.TopStart
            "TopCenter" -> Alignment.TopCenter
            "TopEnd" -> Alignment.TopEnd
            "CenterStart" -> Alignment.CenterStart
            "CenterEnd" -> Alignment.CenterEnd
            else -> Alignment.TopCenter
        }
    }

    fun isDefaultLauncher(context: Context): Boolean {
        val intent = Intent(Intent.ACTION_MAIN)
        intent.addCategory(Intent.CATEGORY_HOME)
        val resolveInfo = context.packageManager.resolveActivity(intent, 0)
        return resolveInfo?.activityInfo?.packageName == context.packageName
    }
    suspend fun fetchImageData(applicationContext: Context){
        val hour = PreferenceManager.getHour(applicationContext)
        if (hour > 0) {
            try {
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
                Log.d("ImageWorker", response.toString())
                PreferenceManager.saveImageData(applicationContext, response)
                client.close()
                PreferenceManager.setHour(applicationContext, hour)
            } catch (e: Exception) {
                Log.e("FetchImageData", "Error fetching image", e)
            }
        }
    }
    fun setWallpaper(applicationContext: Context){
        if(isDefaultLauncher(applicationContext)){
            val filePath = PreferenceManager.getBackgroundImagePath(applicationContext)
            val wallpaperManager =
                WallpaperManager.getInstance(applicationContext)
            val bitmap = BitmapFactory.decodeFile(filePath)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                wallpaperManager.setBitmap(
                    bitmap,
                    null,
                    true,
                    WallpaperManager.FLAG_SYSTEM
                )
            } else {
                wallpaperManager.setBitmap(bitmap)
            }
        }
    }
    suspend fun downloadImage(applicationContext: Context){
        if (PreferenceManager.newImageAvailable(applicationContext)) {
            Log.i("MainViewModel", "Downloading New Image")
            val tempImageData: ImageData? =
                PreferenceManager.getTempImageData(applicationContext)
            if (tempImageData != null) {
                val imageUrl = tempImageData.imageUrl
                val request = ImageRequest.Builder(applicationContext)
                    .data(imageUrl)
                    .build()
                val result = Coil.imageLoader(applicationContext).execute(request)
                if (result is ImageResult) {
                    val drawable = result.drawable
                    if (drawable != null) {
                        val oldFilePath:String? = PreferenceManager.getBackgroundImagePath(applicationContext)
                        if(oldFilePath != null) {
                            val oldFile = File(oldFilePath)
                            oldFile.delete()
                        }
                        val filename = "comfer_${tempImageData.id}.jpg"
                        val file = File(applicationContext.filesDir, filename)
                        val stream = FileOutputStream(file)
                        drawable.toBitmap()
                            .compress(
                                android.graphics.Bitmap.CompressFormat.JPEG,
                                100,
                                stream
                            )
                        stream.close()
                        PreferenceManager.setBackgroundImagePath(
                            applicationContext,
                            file.absolutePath
                        )
                        PreferenceManager.setImageDownloaded(applicationContext)
                        setWallpaper(applicationContext)
                    }
                }
            }
        }
    }
}
