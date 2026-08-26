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
import com.jeerovan.comfer.KeyTextObject
import com.jeerovan.comfer.ImageData
import com.jeerovan.comfer.PreferenceKeys
import com.jeerovan.comfer.PreferenceManager
import com.jeerovan.comfer.R
import com.jeerovan.comfer.dataStore
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.ConnectionSpec
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.URLDecoder
import java.net.ProxySelector
import java.security.MessageDigest
import java.text.Normalizer

private const val MAX_WALLPAPER_DIMENSION = 2048
internal const val MAX_WALLPAPER_SOURCE_BYTES = 25L * 1024L * 1024L
private val wallpaperProcessingDispatcher = Dispatchers.Default.limitedParallelism(1)

internal fun copyStreamWithLimit(
    input: InputStream,
    output: OutputStream,
    maxBytes: Long,
): Boolean {
    require(maxBytes >= 0)
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    var totalBytes = 0L
    while (true) {
        val count = input.read(buffer)
        if (count < 0) return true
        totalBytes += count
        if (totalBytes > maxBytes) return false
        output.write(buffer, 0, count)
    }
}

data class VibrantTextColorStyle(
    val textColor: Color,
    val shadowColor: Color
)

object CommonUtil {
    fun handleStartActivity(context:Context, intent:Intent?, options: ActivityOptions?){
        if (intent == null) return
        try {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (options != null) {
                context.startActivity(intent, options.toBundle())
            } else {
                context.startActivity(intent)
            }
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, "App not found", Toast.LENGTH_SHORT).show()
        } catch (e: SecurityException) {
            Toast.makeText(
                context,
                "App could not be launched. Please check your device App Launch settings.",
                Toast.LENGTH_LONG
            ).show()
        } catch (e: Exception) {
            Toast.makeText(context, "An unexpected error occurred while launching the app.", Toast.LENGTH_SHORT).show()
        }
    }
    fun openUrl(url: String, context: Context) {
        try {
            val validUrl = if (url.startsWith("http://") || url.startsWith("https://")) url else "http://$url"
            val intent = Intent(Intent.ACTION_VIEW, validUrl.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, "No application to open URL", Toast.LENGTH_SHORT).show()
        } catch (_: SecurityException) {
            Toast.makeText(context, "Permission denied to open URL", Toast.LENGTH_SHORT).show()
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
    suspend fun copyFileFromUri(context: Context, sourceUri: Uri, destinationFile: File): Boolean = withContext(Dispatchers.IO) {
        val copied = try {
            // Open an InputStream from the source URI
            context.contentResolver.openInputStream(sourceUri)?.use { inputStream ->
                // Open a FileOutputStream to the destination file
                FileOutputStream(destinationFile).use { outputStream ->
                    copyStreamWithLimit(
                        inputStream,
                        outputStream,
                        MAX_WALLPAPER_SOURCE_BYTES,
                    )
                }
            } ?: false
        } catch (e: IOException) {
            Log.e("WallpaperCopy", "Failed to copy local wallpaper", e)
            false
        }
        if (!copied) {
            destinationFile.delete()
        }
        return@withContext copied
    }
    suspend fun getFileNameFromUri(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
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
        return@withContext fileName
    }
    suspend fun getNextWallpaperImageUri(
        context: Context,
        directoryUri: Uri,
        currentWallpaperUri: Uri?
    ): Uri? = withContext(Dispatchers.IO) {
        // Get a DocumentFile representing the directory from its Uri
        val directory = DocumentFile.fromTreeUri(context, directoryUri)

        // Check if the directory is valid and readable
        if (directory == null || !directory.isDirectory || !directory.canRead()) {
            return@withContext null
        }

        // Define common image file extensions
        val imageExtensions = setOf("jpg", "jpeg", "png", "bmp", "webp")

        // List files, filter for images, and sort them
        val imageFiles = directory.listFiles()
            .filter { it.isFile && it.name?.substringAfterLast(".")?.lowercase() in imageExtensions }
            .sortedBy { it.name }

        // If there are no image files, return null
        if (imageFiles.isEmpty()) {
            return@withContext null
        }

        // If there is no current wallpaper, return the first one
        if (currentWallpaperUri == null) {
            return@withContext imageFiles.first().uri
        }

        // Find the index of the current wallpaper
        val currentIndex = imageFiles.indexOfFirst { it.uri == currentWallpaperUri }

        // Determine the next index, looping back to the start if at the end
        val nextIndex = if (currentIndex == -1 || currentIndex == imageFiles.lastIndex) {
            0
        } else {
            currentIndex + 1
        }

        return@withContext imageFiles[nextIndex].uri
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
        return try {
            val intent = Intent(Intent.ACTION_MAIN)
            intent.addCategory(Intent.CATEGORY_HOME)
            val resolveInfo = context.packageManager.resolveActivity(intent, 0)
            resolveInfo?.activityInfo?.packageName == context.packageName
        } catch (e: Exception) {
            Log.e("CommonUtil", "Error checking if default launcher", e)
            false // Default to false if we cannot determine status
        }
    }
    suspend fun setBackgroundImageFromImageUri(context:Context, wallpaperDirectory:Uri) {
        val currentWallpaperImageUri = PreferenceManager.getBackgroundImageUri(context)
        val nextLocalImageUri = getNextWallpaperImageUri(
            context,
            wallpaperDirectory,
            currentWallpaperImageUri)
        if(nextLocalImageUri != null && currentWallpaperImageUri != nextLocalImageUri){
            //copy file to app files
            val filename = getFileNameFromUri(context, nextLocalImageUri)
            if (filename != null) {
                // 2. Create a destination file in your app"s private storage
                val destinationFile = File(context.filesDir, filename)
                // 3. Copy the file
                val success = copyFileFromUri(context, nextLocalImageUri, destinationFile)
                if (success) {
                    PreferenceManager.setBackgroundImageUri(context, nextLocalImageUri)
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
    @Volatile
    private var httpClientCache: HttpClient? = null

    /**
     * Returns a cached HttpClient configured with the custom SSL trust store.
     * HttpClient creation (connection pool, SSL context) is expensive, so we
     * reuse a single instance per process instead of rebuilding it on every
     * fetchImageData() call.
     */
    private fun getHttpClient(context: Context): HttpClient {
        httpClientCache?.let { return it }
        return synchronized(this) {
            httpClientCache?.let { return it }
            val (sslSocketFactory, trustManager) = SSLHelper.createSslSocketFactory(
                context.applicationContext,
                R.raw.cacert
            )
            val connectionSpecs = listOf(
                ConnectionSpec.MODERN_TLS,
                ConnectionSpec.COMPATIBLE_TLS
            )
            HttpClient(OkHttp) {
                engine {
                    config {
                        sslSocketFactory(sslSocketFactory, trustManager)
                        connectionSpecs(connectionSpecs)
                        proxySelector(SafeProxySelector(ProxySelector.getDefault()))
                    }
                }
                install(ContentNegotiation) {
                    json(Json {
                        ignoreUnknownKeys = true
                    })
                }
            }.also { httpClientCache = it }
        }
    }

    /**
     * Closes the cached HttpClient. Safe to call when the app is being
     * torn down (e.g. from Application.onTerminate or a lifecycle observer).
     */
    fun closeHttpClient() {
        synchronized(this) {
            httpClientCache?.close()
            httpClientCache = null
        }
    }

    suspend fun fetchImageData(
        applicationContext: Context,
        manualChange: Boolean = false
    ): Boolean {
        val autoWallpapers = PreferenceManager.getAutoWallpapers(applicationContext)
        if(!autoWallpapers) return true
        val previousWallpaperApplied = PreferenceManager.getWallpaperApplied(applicationContext)
        if(!previousWallpaperApplied && !manualChange) {
            return true
        }
        val changeFrequency = PreferenceManager.getWallpaperFrequency(applicationContext)
        val hourNow = PreferenceManager.getHour(applicationContext)
        if (hourNow > 0 || manualChange) {
            val wallpaperDirectory = PreferenceManager.getWallpaperDirectory(applicationContext)
            if(wallpaperDirectory != null){
                if (changeFrequency == "Hourly" || hourNow == 3 || manualChange){
                    if(!manualChange)PreferenceManager.setHour(applicationContext, hourNow)
                    setBackgroundImageFromImageUri(applicationContext,wallpaperDirectory.toUri())
                }
            } else {
                if(changeFrequency == "Hourly" || hourNow == 7 || hourNow == 19 || manualChange) {
                    try {
                        val name = PreferenceManager.getUsername(applicationContext)
                        val client = getHttpClient(applicationContext)
                        val response: ImageData = client.get("https://comfer.jeerovan.com/api") {
                            parameter("name", name)
                            parameter("hour", hourNow)
                        }.body()
                        Log.i("FetchImageData", response.toString())
                        PreferenceManager.saveImageData(applicationContext, response)
                        if(!manualChange)PreferenceManager.setHour(applicationContext, hourNow)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.e("FetchImageData", e.toString())
                        return false
                    }
                }
            }
        }
        return true
    }
    fun getLaunchIntentSafe(context: Context, packageName: String): Intent? {
        return try {
            context.packageManager.getLaunchIntentForPackage(packageName)
        } catch (e: Exception) {
            Log.e("CommonUtil", "Error getting launch intent for $packageName", e)
            null
        }
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
                setWallpaperThemedColors(context, bitmap)
            } finally {
                // 5. Important: Recycle the bitmap immediately as we only needed it for colors
                bitmap.recycle()
            }
        }
    }
    suspend fun downloadImage(applicationContext: Context): Boolean {
        if (PreferenceManager.newImageAvailable(applicationContext)) {
            Log.i("DownloadImage", "Downloading New Image")
            val tempImageData: ImageData? =
                PreferenceManager.getTempImageData(applicationContext)
            if (tempImageData != null) {
                val imageUrl = tempImageData.imageUrl
                val (targetWidth, targetHeight) = getWallpaperTargetSize(applicationContext)
                val sourceFile = File.createTempFile(
                    "comfer_wallpaper_source_",
                    ".tmp",
                    applicationContext.cacheDir,
                )
                try {
                    if (!downloadFileWithinLimit(applicationContext, imageUrl, sourceFile)) {
                        return false
                    }
                    val bitmap = decodeWallpaperBitmap(sourceFile, targetWidth, targetHeight)
                        ?: return false
                    try {
                        val filename = "comfer_${tempImageData.id}.jpg"
                        val file = File(applicationContext.filesDir, filename)
                        FileOutputStream(file).use { stream ->
                            if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 92, stream)) {
                                file.delete()
                                return false
                            }
                        }
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
                        setWallpaperThemedColors(applicationContext, bitmap)
                        if (!applyWallpaperBitmap(applicationContext, bitmap, file.absolutePath)) {
                            return false
                        }
                    } finally {
                        bitmap.recycle()
                    }
                } finally {
                    sourceFile.delete()
                }
            } else {
                return false
            }
        } else if (PreferenceManager.getMonochrome(applicationContext)){
            val currentWallpaperFilePath = PreferenceManager.getBackgroundImagePath(applicationContext)
            if( PreferenceManager.getAppliedWallpaperImage(applicationContext) != currentWallpaperFilePath){
                if (!setWallpaper(applicationContext)) return false
            }
        }
        return true
    }
    suspend fun setWallpaper(context: Context): Boolean = withContext(Dispatchers.IO) {
        val filePath = PreferenceManager.getBackgroundImagePath(context)
            ?: return@withContext false
        val (targetWidth, targetHeight) = getWallpaperTargetSize(context)
        val bitmap = decodeWallpaperBitmap(File(filePath), targetWidth, targetHeight)
            ?: return@withContext false
        try {
            applyWallpaperBitmap(context, bitmap, filePath)
        } finally {
            bitmap.recycle()
        }
    }

    private suspend fun downloadFileWithinLimit(
        context: Context,
        imageUrl: String,
        destination: File,
    ): Boolean {
        return try {
            val response = getHttpClient(context).get(imageUrl)
            if (!response.status.isSuccess()) return false
            val channel = response.bodyAsChannel()
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var totalBytes = 0L
            try {
                FileOutputStream(destination).use { output ->
                    while (true) {
                        val count = channel.readAvailable(buffer, 0, buffer.size)
                        if (count < 0) break
                        totalBytes += count
                        if (totalBytes > MAX_WALLPAPER_SOURCE_BYTES) {
                            Log.e("DownloadImage", "Wallpaper exceeds source byte limit")
                            return false
                        }
                        output.write(buffer, 0, count)
                    }
                }
            } finally {
                channel.cancel(null)
            }
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("DownloadImage", "Wallpaper download failed", e)
            false
        }
    }

    private fun decodeWallpaperBitmap(
        file: File,
        targetWidth: Int,
        targetHeight: Int,
    ): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = calculateInSampleSize(bounds, targetWidth, targetHeight)
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        val decoded = BitmapFactory.decodeFile(file.absolutePath, decodeOptions) ?: return null
        val bitmap = if (decoded.width > targetWidth || decoded.height > targetHeight) {
            val scale = minOf(
                targetWidth.toFloat() / decoded.width,
                targetHeight.toFloat() / decoded.height
            )
            Bitmap.createScaledBitmap(
                decoded,
                (decoded.width * scale).toInt().coerceAtLeast(1),
                (decoded.height * scale).toInt().coerceAtLeast(1),
                true
            ).also { decoded.recycle() }
        } else {
            decoded
        }
        return bitmap
    }

    private suspend fun applyWallpaperBitmap(
        context: Context,
        bitmap: Bitmap,
        filePath: String,
    ): Boolean {
        if (isDefaultLauncher(context)) {
            val setWallpaperOnLockScreen = PreferenceManager.getWallpaperOnLockScreen(context)
            val wallpaperManager = WallpaperManager.getInstance(context)
            val flag = if (setWallpaperOnLockScreen) {
                WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK
            } else {
                WallpaperManager.FLAG_SYSTEM
            }
            try {
                wallpaperManager.setBitmap(bitmap, null, true, flag)
            } catch (e: IOException) {
                Log.e("CommonUtil", "Could not apply wallpaper", e)
                return false
            } catch (e: RuntimeException) {
                // Android 16 OEM builds have thrown IllegalArgumentException from
                // setBitmapWithCrops when their WallpaperDescription is invalid.
                Log.e("CommonUtil", "Wallpaper service rejected bitmap", e)
                return false
            }
            PreferenceManager.setAppliedWallpaperImage(context, filePath)
        } else {
            PreferenceManager.setAppliedWallpaperImage(context, null)
        }
        return true
    }

    private suspend fun setWallpaperThemedColors(context: Context, bitmap: Bitmap) {
        val colors = withContext(wallpaperProcessingDispatcher) {
            val palette = Palette.from(bitmap).generate()
            val textColors = getThemedColorForUpperHalf(palette)
            intArrayOf(
                palette.lightMutedSwatch?.rgb
                    ?: Color.White.copy(alpha = 0.7f).toArgb(),
                palette.lightMutedSwatch?.bodyTextColor
                    ?: palette.darkVibrantSwatch?.rgb
                    ?: Color.Black.toArgb(),
                palette.darkMutedSwatch?.rgb
                    ?: Color.Black.copy(alpha = 0.7f).toArgb(),
                palette.darkMutedSwatch?.titleTextColor
                    ?: palette.lightVibrantSwatch?.rgb
                    ?: Color.White.toArgb(),
                textColors.textColor.toArgb(),
                textColors.shadowColor.toArgb(),
            )
        }
        PreferenceManager.setThemedColors(
            context,
            colors[0],
            colors[1],
            colors[2],
            colors[3],
            colors[4],
            colors[5],
        )
        context.dataStore.edit { preferences ->
            preferences[PreferenceKeys.WALLPAPER_UPDATE] = System.currentTimeMillis()
        }
    }

    private fun getWallpaperTargetSize(context: Context): Pair<Int, Int> {
        val wallpaperManager = WallpaperManager.getInstance(context)
        val metrics = context.resources.displayMetrics
        val desiredWidth = wallpaperManager.desiredMinimumWidth
            .takeIf { it > 0 } ?: (metrics.widthPixels * 2)
        val desiredHeight = wallpaperManager.desiredMinimumHeight
            .takeIf { it > 0 } ?: metrics.heightPixels
        return desiredWidth.coerceIn(1, MAX_WALLPAPER_DIMENSION) to
            desiredHeight.coerceIn(1, MAX_WALLPAPER_DIMENSION)
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
