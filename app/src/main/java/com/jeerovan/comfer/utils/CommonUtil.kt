package com.jeerovan.comfer.utils

import android.app.ActivityOptions
import android.app.WallpaperManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.util.Log
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
import androidx.datastore.preferences.core.edit
import androidx.documentfile.provider.DocumentFile
import androidx.palette.graphics.Palette
import coil.Coil
import coil.request.ImageRequest
import coil.request.ImageResult
import com.jeerovan.comfer.KeyTextObject
import com.jeerovan.comfer.ImageData
import com.jeerovan.comfer.PreferenceKeys
import com.jeerovan.comfer.PreferenceManager
import com.jeerovan.comfer.R
import com.jeerovan.comfer.dataStore
import com.jeerovan.comfer.isTesting
import com.jeerovan.comfer.toBitmap
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.ConnectionSpec
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.URLDecoder
import java.security.MessageDigest
import java.text.Normalizer

data class VibrantTextColorStyle(
    val textColor: Color,
    val shadowColor: Color
)

object CommonUtil {
    fun handleStartActivity(context:Context, intent:Intent?, options: ActivityOptions?){
        try {
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                intent.addFlags(Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                if (options != null){
                    context.startActivity(intent,options.toBundle())
                } else {
                    context.startActivity(intent)
                }
            } else {
                // Optionally, handle the case where the intent is null
            }
        } catch (e: SecurityException) {
            // The permission was denied by the system.
            // Inform the user gracefully instead of crashing.
            e.printStackTrace() // Log the error for debugging
            Toast.makeText(
                context,
                "App could not be launched. Please check your device App Launch settings.",
                Toast.LENGTH_LONG
            ).show()
        } catch (e: Exception) {
            // Catch other potential exceptions for robustness
            e.printStackTrace()
            Toast.makeText(
                context,
                "An unexpected error occurred while launching the app.",
                Toast.LENGTH_SHORT
            ).show()
        }
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
            .filter { it.isFile && it.name?.substringAfterLast(".")?.lowercase() in imageExtensions }
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
    fun String.removeAccents(): String {
        val normalized = Normalizer.normalize(this, Normalizer.Form.NFD)
        return normalized.replace(Regex("\\p{Mn}+"), "") // Remove non-spacing marks
    }
    fun doesMatchSearch(query: String, text: String?): Boolean {
        if (query.isBlank()) return true
        if (text == null) return false
        val cleanQuery = query.removeAccents()
        val cleanText = text.removeAccents()
        return cleanText.contains(cleanQuery, ignoreCase = true)
    }
    fun isDefaultLauncher(context: Context): Boolean {
        val intent = Intent(Intent.ACTION_MAIN)
        intent.addCategory(Intent.CATEGORY_HOME)
        val resolveInfo = context.packageManager.resolveActivity(intent, 0)
        return resolveInfo?.activityInfo?.packageName == context.packageName
    }
    suspend fun setBackgroundImageFromImageUri(context:Context, wallpaperDirectory:Uri) {
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
                // 2. Create a destination file in your app"s private storage
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
                    if(oldFilePath != null && oldFilePath != newFilePath) {
                        val oldFile = File(oldFilePath)
                        oldFile.delete()
                    }
                    setWallpaperThemedColors(context, File(newFilePath))
                    withContext(Dispatchers.IO){
                        setWallpaper(context)
                    }
                }
            }
        }
    }
    suspend fun fetchImageData(applicationContext: Context,manualChange: Boolean = false){
        val autoWallpapers = PreferenceManager.getAutoWallpapers(applicationContext)
        if(!autoWallpapers) return
        val previousWallpaperApplied = PreferenceManager.getWallpaperApplied(applicationContext)
        if(!previousWallpaperApplied && !manualChange) {
            return
        }
        val changeFrequency = PreferenceManager.getWallpaperFrequency(applicationContext)
        val hour = PreferenceManager.getHour(applicationContext)
        if (hour > 0 || manualChange) {
            val hasPro = PreferenceManager.getPro(applicationContext)
            val wallpaperDirectory = PreferenceManager.getWallpaperDirectory(applicationContext)
            val actualHour = if(isTesting){
                if(PreferenceManager.isLightHour(applicationContext)) 10 else 20
            } else hour
            if(wallpaperDirectory != null && hasPro){
                if (changeFrequency == "Hourly" || hour == 3 || manualChange){
                    if(!manualChange)PreferenceManager.setHour(applicationContext, hour)
                    setBackgroundImageFromImageUri(applicationContext,wallpaperDirectory.toUri())
                }
            } else {
                if(changeFrequency == "Hourly" || hour == 7 || hour == 19 || manualChange) {
                    try {
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
                            parameter("hour", actualHour)
                        }.body()
                        Log.i("FetchImageData", response.toString())
                        client.close()
                        PreferenceManager.saveImageData(applicationContext, response)
                        if(!manualChange)PreferenceManager.setHour(applicationContext, hour)
                    } catch (e: Exception) {
                        Log.e("FetchImageData", e.toString())
                    }
                }
            }
        }
    }
    fun canSetLockScreenWallpaper(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
    }
    suspend fun setWallpaperThemedColors(context: Context, file: File){
        withContext(Dispatchers.IO) {
            if (!file.exists()) return@withContext null

            // 1. Calculate dimensions without loading the whole image into memory
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeFile(file.absolutePath, options)

            // 2. Calculate optimal inSampleSize
            // Target size ~512px is more than enough for accurate color extraction
            // Palette internally resizes to ~100px-200px anyway
            options.inSampleSize = calculateInSampleSize(options, 512, 512)

            // 3. Decode the downsampled bitmap
            options.inJustDecodeBounds = false
            val bitmap = BitmapFactory.decodeFile(file.absolutePath, options) ?: return@withContext null
            try {
                // Optional: resizeBitmapArea is the internal limiter (default is usually fine)
                // but since we already downsampled, we can just generate.
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
                val textColors = getThemedColorForUpperHalf(palette)
                PreferenceManager.setThemedColors(
                    context,
                    lightBg,
                    lightFg,
                    darkBg,
                    darkFg,
                    textColors.textColor.toArgb(),
                    textColors.shadowColor.toArgb()
                )
                //signal to update
                context.dataStore.edit { preferences ->
                    preferences[PreferenceKeys.WALLPAPER_UPDATE] = System.currentTimeMillis()
                }
            } finally {
                // 5. Important: Recycle the bitmap immediately as we only needed it for colors
                bitmap.recycle()
            }
        }
    }
    suspend fun downloadImage(applicationContext: Context){
        if (PreferenceManager.newImageAvailable(applicationContext)) {
            Log.i("DownloadImage", "Downloading New Image")
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
                        Log.i("DownloadImage","Downloaded: $filename")
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
                            Log.i("DownloadImage","Deleted: $oldFilePath")
                        }
                        setWallpaperThemedColors(applicationContext, file)
                        withContext(Dispatchers.IO){
                            setWallpaper(applicationContext)
                        }
                    }
                }
            }
        } else if (PreferenceManager.getMonochrome(applicationContext)){
            val currentWallpaperFilePath = PreferenceManager.getBackgroundImagePath(applicationContext)
            if( PreferenceManager.getAppliedWallpaperImage(applicationContext) != currentWallpaperFilePath){
                withContext(Dispatchers.IO) {
                    setWallpaper(applicationContext)
                }
            }
        }
    }
    suspend fun reloadWallpaper(applicationContext: Context){
        withContext(Dispatchers.IO) {
            setWallpaper(applicationContext)
        }
    }
    fun setWallpaper(context: Context){
        val filePath = PreferenceManager.getBackgroundImagePath(context)
        val bitmap = BitmapFactory.decodeFile(filePath)
        if(bitmap != null) {
            if (isDefaultLauncher(context)) {
                val setWallpaperOnLockScreen =
                    PreferenceManager.getWallpaperOnLockScreen(context)
                val wallpaperManager =
                    WallpaperManager.getInstance(context)
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
                PreferenceManager.setAppliedWallpaperImage(context,filePath)
            } else {
                PreferenceManager.setAppliedWallpaperImage(context,null)
            }
        }
    }

    fun getShapeFromString(iconShape:String?="circle"): Shape{
        return when (iconShape) {
            "pebble" -> {
                PebbleShape()
            }
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
    fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val (height: Int, width: Int) = options.run { outHeight to outWidth }
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            val halfHeight: Int = height / 2
            val halfWidth: Int = width / 2

            // Calculate the largest inSampleSize value that is a power of 2 and keeps both
            // height and width larger than the requested height and width.
            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }
    fun getKeyTextObject(option: String,context: Context) : KeyTextObject {
        return when(option) {
            "H12" -> KeyTextObject(option,option)
            "H24" -> KeyTextObject(option,option)
            "Hourly" -> KeyTextObject(context.getString(R.string.update_frequency_hour),option)
            "Daily" -> KeyTextObject(context.getString(R.string.update_frequency_day),option)
            "Light" -> KeyTextObject(context.getString(R.string.font_weight_light),option)
            "Normal" -> KeyTextObject(context.getString(R.string.font_weight_normal),option)
            "Bold" -> KeyTextObject(context.getString(R.string.font_weight_bold),option)
            else -> KeyTextObject(option,option)
        }
    }

    fun getThemedColorForUpperHalf(palette: Palette): VibrantTextColorStyle {
        // 1. Try to get the main Vibrant color for text
        // If null, fallback to LightVibrant or a safe default like White
        val textSwatch = palette.vibrantSwatch ?: palette.lightVibrantSwatch
        val textColor = textSwatch?.rgb?.let { Color(it) } ?: Color.White

        val shadowColor = if(isColorDark(textColor)) Color.White else Color.Black
        return VibrantTextColorStyle(textColor, shadowColor)
    }
}

