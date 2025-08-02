package com.jeerovan.comfer.utils

import android.app.WallpaperManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.Log
import androidx.compose.ui.Alignment
import android.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.alpha
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
import androidx.core.graphics.createBitmap

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
            val setWallpaperOnLockScreen = PreferenceManager.getWallpaperOnLockScreen(applicationContext)
            val wallpaperManager =
                WallpaperManager.getInstance(applicationContext)
            val bitmap = BitmapFactory.decodeFile(filePath)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                var flag = WallpaperManager.FLAG_SYSTEM
                if(setWallpaperOnLockScreen){
                    flag = WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK
                }
                wallpaperManager.setBitmap(
                    bitmap,
                    null,
                    true,
                    flag
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
    fun Drawable.toBitmapSafely(width: Int = intrinsicWidth, height: Int = intrinsicHeight): Bitmap? {
        if (width <= 0 || height <= 0) return null
        val bitmap = createBitmap(width, height)
        val canvas = Canvas(bitmap)
        setBounds(0, 0, canvas.width, canvas.height)
        draw(canvas)
        return bitmap
    }

    fun extractDominantColorByFrequency(bitmap: Bitmap, defaultColor: Int): Int {
        // A map to store the frequency of each color.
        val colorCounts = mutableMapOf<Int, Int>()
        // For efficiency, get all pixels at once.
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        for (pixelColor in pixels) {
            // We only consider opaque pixels for the background color.
            // You can adjust the alpha threshold if needed (255 is fully opaque).
            if (android.graphics.Color.alpha(pixelColor) >= 255) {
                // Merge similar colors to get a more representative result.
                // This is a simple form of quantization.
                val representativeColor = ColorUtils.setAlphaComponent(pixelColor, 255)

                val count = colorCounts[representativeColor] ?: 0
                colorCounts[representativeColor] = count + 1
            }
        }
        // Find the color with the highest count.
        val dominantColor = colorCounts.maxByOrNull { it.value }?.key
        return dominantColor ?: defaultColor
    }
    fun findOutermostColor(bitmap: Bitmap, defaultColor: Int): Int {
        val width = bitmap.width
        val height = bitmap.height

        // Iterate through each pixel, row by row (y), then column by column (x)
        for (y in 0 until height) {
            for (x in 0 until width) {
                // Get the color of the pixel at the current coordinate
                val pixelColor = bitmap.getPixel(x, y)

                // Check the alpha component of the color.
                // A value greater than 0 means it is not fully transparent.
                if (pixelColor.alpha == 255) {
                    // Found the first non-transparent pixel, return its color immediately.
                    return pixelColor
                }
            }
        }

        // If the loop completes, all pixels were transparent. Return the default color.
        return defaultColor
    }
}
