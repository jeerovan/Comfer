package com.jeerovan.comfer.utils

import android.app.WallpaperManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import androidx.palette.graphics.Palette
import coil.Coil
import coil.request.ImageRequest
import coil.request.ImageResult
import com.jeerovan.comfer.ImageData
import com.jeerovan.comfer.LoggerManager
import com.jeerovan.comfer.PreferenceManager
import com.jeerovan.comfer.R
import com.jeerovan.comfer.toBitmap
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import okhttp3.ConnectionSpec
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.URLDecoder
import java.security.MessageDigest
import java.util.Calendar

object CommonUtil {

    fun isLightModeInHour(): Boolean {
        val calendar = Calendar.getInstance()
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        return hour > 7 && hour < 19
    }
    fun openUrl(url: String,context: Context) {
        try {
            var validUrl = url
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                validUrl = "http://$url"
            }
            val intent = Intent(Intent.ACTION_VIEW, validUrl.toUri())
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, "No application to open URL", Toast.LENGTH_SHORT).show()
        }
    }
    fun getUriPath(encodedUri: String?): String? {
        if(encodedUri != null) {
            val decodedUri = URLDecoder.decode(encodedUri, "UTF-8")
            return if (decodedUri.contains(":")) {
                decodedUri.split(":").last()
            } else {
                decodedUri
            }
        } else {
            return null
        }
    }
    fun copyFileFromUri(context: Context, sourceUri: Uri, destinationFile: File): Boolean {
        return try {
            // Open an InputStream from the source URI
            context.contentResolver.openInputStream(sourceUri)?.use { inputStream ->
                // Open a FileOutputStream to the destination file
                FileOutputStream(destinationFile).use { outputStream ->
                    // Copy the data from the input stream to the output stream
                    inputStream.copyTo(outputStream)
                }
            }
            true // Indicate success
        } catch (e: IOException) {
            e.printStackTrace()
            false // Indicate failure
        }
    }
    fun getFileNameFromUri(context: Context, uri: Uri): String? {
        var fileName: String? = null
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1) {
                    fileName = it.getString(nameIndex)
                }
            }
        }
        return fileName
    }
    fun getNextWallpaperImageUri(
        context: Context,
        directoryUri: Uri,
        currentWallpaperUri: Uri?
    ): Uri? {
        // Get a DocumentFile representing the directory from its Uri
        val directory = DocumentFile.fromTreeUri(context, directoryUri)

        // Check if the directory is valid and readable
        if (directory == null || !directory.isDirectory || !directory.canRead()) {
            return null
        }

        // Define common image file extensions
        val imageExtensions = setOf("jpg", "jpeg", "png", "bmp", "webp")

        // List files, filter for images, and sort them
        val imageFiles = directory.listFiles()
            .filter { it.isFile && it.name?.substringAfterLast('.')?.lowercase() in imageExtensions }
            .sortedBy { it.name }

        // If there are no image files, return null
        if (imageFiles.isEmpty()) {
            return null
        }

        // If there is no current wallpaper, return the first one
        if (currentWallpaperUri == null) {
            return imageFiles.first().uri
        }

        // Find the index of the current wallpaper
        val currentIndex = imageFiles.indexOfFirst { it.uri == currentWallpaperUri }

        // Determine the next index, looping back to the start if at the end
        val nextIndex = if (currentIndex == -1 || currentIndex == imageFiles.lastIndex) {
            0
        } else {
            currentIndex + 1
        }

        return imageFiles[nextIndex].uri
    }

    fun isColorDark(color: Color): Boolean {
        val darkness = 1 - (0.299 * color.red + 0.587 * color.green + 0.114 * color.blue)
        return darkness >= 0.5
    }
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

    fun isDefaultLauncher(context: Context): Boolean {
        val intent = Intent(Intent.ACTION_MAIN)
        intent.addCategory(Intent.CATEGORY_HOME)
        val resolveInfo = context.packageManager.resolveActivity(intent, 0)
        return resolveInfo?.activityInfo?.packageName == context.packageName
    }
    fun setBackgroundImageFromImageUri(context:Context,wallpaperDirectory:Uri) {
        val currentWallpaperImageUri = PreferenceManager.getBackgroundImageUri(context)
        val nextLocalImageUri = getNextWallpaperImageUri(
            context,
            wallpaperDirectory,
            currentWallpaperImageUri)
        if(nextLocalImageUri != null && currentWallpaperImageUri != nextLocalImageUri){
            PreferenceManager.setBackgroundImageUri(
                context,
                nextLocalImageUri
            )
            //copy file to app files
            val filename = getFileNameFromUri(context, nextLocalImageUri)
            if (filename != null) {
                // 2. Create a destination file in your app's private storage
                val destinationFile = File(context.filesDir, filename)
                // 3. Copy the file
                val success = copyFileFromUri(context, nextLocalImageUri, destinationFile)
                if (success) {
                    val newFilePath = destinationFile.absolutePath
                    val oldFilePath:String? = PreferenceManager.getBackgroundImagePath(context)
                    PreferenceManager.setBackgroundImagePath(
                        context,
                        newFilePath
                    )
                    PreferenceManager.setWallpaperApplied(context,false)
                    // delete old file
                    if(oldFilePath != null && oldFilePath != newFilePath) {
                        val oldFile = File(oldFilePath)
                        oldFile.delete()
                    }
                }
            }
        }
    }
    suspend fun fetchImageData(applicationContext: Context){
        val previousWallpaperApplied = PreferenceManager.getWallpaperApplied(applicationContext)
        if(!previousWallpaperApplied) return
        val logger = LoggerManager(applicationContext)
        val hour = PreferenceManager.getHour(applicationContext)
        if (hour > 0) {
            val hasPro = PreferenceManager.getPro(applicationContext)
            val wallpaperDirectory = PreferenceManager.getWallpaperDirectory(applicationContext)
            if(wallpaperDirectory != null && hasPro){
                val changeFrequency = PreferenceManager.getWallpaperFrequency(applicationContext)
                if (changeFrequency == "Hourly" || hour == 12){
                    setBackgroundImageFromImageUri(applicationContext,wallpaperDirectory.toUri())
                }
            } else {
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
                    logger.setLog("FetchImageData", e.toString())
                }
            }
        }
    }
    fun canSetLockScreenWallpaper(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
    }
    fun setWallpaperThemedColors(context: Context,bitmap: Bitmap){
        val palette = Palette.from(bitmap).generate()
        // Light theme colors
        val lightBg = palette.lightMutedSwatch?.rgb ?: Color.White.copy(alpha = 0.7f).toArgb()
        val lightFg = palette.lightMutedSwatch?.bodyTextColor
            ?: palette.darkVibrantSwatch?.rgb
            ?: Color.Black.toArgb()

        // Dark theme colors
        val darkBg = palette.darkMutedSwatch?.rgb ?: Color.Black.copy(alpha = 0.7f).toArgb()
        val darkFg = palette.darkMutedSwatch?.titleTextColor
            ?: palette.lightVibrantSwatch?.rgb
            ?: Color.White.toArgb()
        PreferenceManager.setThemedColors(context,
            lightBg,
            lightFg,
            darkBg,
            darkFg)
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
                        val filename = "comfer_${tempImageData.id}.jpg"
                        val file = File(applicationContext.filesDir, filename)
                        val stream = FileOutputStream(file)
                        drawable.toBitmap()
                            .compress(
                                Bitmap.CompressFormat.JPEG,
                                100,
                                stream
                            )
                        stream.close()
                        logger.setLog("DownloadImage","Downloaded: $filename")
                        val oldFilePath:String? = PreferenceManager.getBackgroundImagePath(applicationContext)
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
    fun setWallpaper(applicationContext: Context){
        val filePath = PreferenceManager.getBackgroundImagePath(applicationContext)
        val bitmap = BitmapFactory.decodeFile(filePath)
        if(bitmap != null) {
            if (isDefaultLauncher(applicationContext)) {
                val setWallpaperOnLockScreen =
                    PreferenceManager.getWallpaperOnLockScreen(applicationContext)
                val wallpaperManager =
                    WallpaperManager.getInstance(applicationContext)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    var flag = WallpaperManager.FLAG_SYSTEM
                    if (setWallpaperOnLockScreen) {
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
            setWallpaperThemedColors(applicationContext, bitmap)
        }
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
        var iconShape = shape
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

