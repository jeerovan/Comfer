package com.jeerovan.comfer.utils

import FlowerShape
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
import android.net.Uri
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
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
import com.jeerovan.comfer.LoggerManager
import com.jeerovan.comfer.R
import okhttp3.ConnectionSpec
import java.io.IOException

import java.security.cert.X509Certificate
import java.util.Calendar
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager

object CommonUtil {

    fun getFontWeightFromString(string: String): FontWeight {
        when (string) {
            "Light" -> return FontWeight.Light
            "Normal" -> return FontWeight.Normal
            "Bold" -> return FontWeight.Bold
        }
        return FontWeight.Normal
    }
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
        val previousWallpaperApplied = PreferenceManager.getWallpaperApplied(applicationContext)
        if(!previousWallpaperApplied) return;
        val logger = LoggerManager(applicationContext)
        val hour = PreferenceManager.getHour(applicationContext)
        if (hour > 0) {
            try {
                logger.setLog("FetchImageData", "Fetching")
                val name = PreferenceManager.getUsername(applicationContext)
                val (sslSocketFactory, trustManager) = SSLHelper.createSslSocketFactory(
                    applicationContext,
                    R.raw.cacert // Use the name of your certificate file
                )

                // 2. Define connection specs, including one for compatibility with older devices
                val connectionSpecs = listOf(
                    ConnectionSpec.MODERN_TLS,
                    ConnectionSpec.COMPATIBLE_TLS
                )
                val client = HttpClient(OkHttp) {
                    engine {
                        config {
                            // Attach the custom SSLSocketFactory
                            sslSocketFactory(sslSocketFactory, trustManager)

                            // Set the compatible connection specifications
                            connectionSpecs(connectionSpecs)
                        }
                    }
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
                logger.setLog("FetchImageData", response.toString())
                client.close()
                PreferenceManager.saveImageData(applicationContext, response)
                PreferenceManager.setHour(applicationContext, hour)
            } catch (e: Exception) {
                logger.setLog("FetchImageData",  e.toString())
            }
        }
    }
    fun canSetLockScreenWallpaper(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
    }
    fun setWallpaper(applicationContext: Context){
        if(isDefaultLauncher(applicationContext)){
            val filePath = PreferenceManager.getBackgroundImagePath(applicationContext)
            val setWallpaperOnLockScreen = PreferenceManager.getWallpaperOnLockScreen(applicationContext)
            val wallpaperManager =
                WallpaperManager.getInstance(applicationContext)
            val bitmap = BitmapFactory.decodeFile(filePath)
            if(bitmap != null){
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
    }
    suspend fun downloadImage(applicationContext: Context){
        val logger = LoggerManager(applicationContext)
        if (PreferenceManager.newImageAvailable(applicationContext)) {
            logger.setLog("DownloadImage", "Downloading New Image")
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
                        logger.setLog("DownloadImage","Downloaded: $filename")
                        PreferenceManager.setBackgroundImagePath(
                            applicationContext,
                            file.absolutePath
                        )
                        PreferenceManager.setImageDownloaded(applicationContext)
                        PreferenceManager.setWallpaperApplied(applicationContext,false)
                        // delete old file
                        if(oldFilePath != null && oldFilePath != file.absolutePath) {
                            val oldFile = File(oldFilePath)
                            oldFile.delete()
                            logger.setLog("DownloadImage","Deleted: $oldFilePath")
                        }
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

    @Throws(IOException::class)
    fun copyUriToInternalStorage(context: Context, uri: Uri, destinationFile: File) {
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            FileOutputStream(destinationFile).use { outputStream ->
                // Copy the bytes from the input stream to the output stream
                inputStream.copyTo(outputStream)
            }
        } ?: throw IOException("Unable to open input stream from URI: $uri")
    }

    fun getShapeFromString(iconShape:String?="circle"): Shape{
        return when (iconShape) {
            "cloud" -> {
                FlowerShape(angle = 45.0f)
            }
            "squircle" -> {
                RoundedCornerShape(0.0f)
            }
            "cutcorner" -> {
                CutCornerShape(0.dp)
            }
            "flower" -> {
                FlowerShape(petalCount = 7)
            }
            "circle" -> {
                CircleShape
            }
            else -> {
                CircleShape
            }
        }
    }
    fun getShapeFromShape(shape:Shape, size: Dp):Shape{
        var iconShape = shape;
        when (shape) {
            CircleShape -> {
                iconShape  = shape
            }
            is RoundedCornerShape -> {
                val cornerRadius = size * 0.425f
                iconShape = RoundedCornerShape(cornerRadius)
            }

            is CutCornerShape -> {
                val cornerCut = size * 0.225f
                iconShape = CutCornerShape(cornerCut)
            }
        }
        return iconShape
    }
}

