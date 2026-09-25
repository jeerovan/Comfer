package com.jeerovan.comfer.spatial

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import coil.imageLoader
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.request.SuccessResult
import coil.size.Scale
import com.jeerovan.comfer.utils.copyStreamWithLimit
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.IOException

internal const val LOCAL_SCENE = 3
internal const val LOCAL_PATH = "local_path"
internal const val LOCAL_SPATIAL = "local_spatial"
internal const val PIPELINE_VERSION = "depth-v1-layers-v3"

internal fun spatialPreferences(context: Context) = context.getSharedPreferences("spatial_wallpapers", Context.MODE_PRIVATE)

internal enum class PreparationStage { MODEL, DEPTH, SUBJECT, SAVING }
internal sealed interface LocalSpatialStatus {
    data object Idle : LocalSpatialStatus
    data class Preparing(val source: String, val stage: PreparationStage, val progress: Float? = null) : LocalSpatialStatus
    data class Ready(val source: String, val directory: File) : LocalSpatialStatus
    data class Failed(val source: String) : LocalSpatialStatus
}

internal data class LocalLayer(val bitmap: Bitmap, val mesh: DepthMesh)
internal data class LocalScene(val layers: List<LocalLayer>)

/** App-owned work survives Activity recreation. A single mutex bounds native ML memory. */
internal object LocalSpatialRepository {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private var job: Job? = null
    private var request: String? = null
    private var requestedDepth: String? = null
    private var generation = 0L
    private val mutableStatus = MutableStateFlow<LocalSpatialStatus>(LocalSpatialStatus.Idle)
    val status = mutableStatus.asStateFlow()

    @Synchronized
    fun prepare(context: Context, path: String?, enabled: Boolean, retry: Boolean = false, invalidateCache: Boolean = false, cloudDepthUrl: String? = null) {
        val next = path?.takeIf { enabled }
        if (request == next && requestedDepth == cloudDepthUrl && !retry) return
        requestedDepth = cloudDepthUrl
        request = next
        val ticket = ++generation
        job?.cancel()
        mutableStatus.value = if (next == null) LocalSpatialStatus.Idle else
            LocalSpatialStatus.Preparing(next, PreparationStage.MODEL)
        if (next == null) return
        val app = context.applicationContext
        job = scope.launch {
            try {
                mutex.withLock {
                    ensureActive()
                    val root = File(app.noBackupFilesDir, "local-spatial").apply { mkdirs() }
                    val source = File.createTempFile("spatial-source-", ".image", app.cacheDir)
                    try {
                        File(next).inputStream().use { input -> source.outputStream().use { output ->
                            check(copyStreamWithLimit(input, output, 50L * 1024 * 1024))
                        } }
                        val suffix = if (cloudDepthUrl == null) PIPELINE_VERSION else "cloud-" +
                            java.security.MessageDigest.getInstance("SHA-256").digest(cloudDepthUrl.toByteArray()).joinToString("") { "%02x".format(it) }
                        val key = sha256(source) + "-" + suffix
                        val destination = File(root, key)
                        if (invalidateCache || !validScene(destination)) {
                            destination.deleteRecursively()
                            val staging = File(root, "$key.partial")
                            staging.deleteRecursively()
                            check(staging.mkdirs())
                            try {
                                if (cloudDepthUrl == null) generate(app, source.path, staging, ticket, next)
                                else generateCloud(app, source, cloudDepthUrl, staging)
                                ensureActive()
                                check(staging.renameTo(destination))
                            } finally { staging.deleteRecursively() }
                        }
                        ensureActive()
                        // Retain the active scene and two recently used scenes.
                        destination.setLastModified(System.currentTimeMillis())
                        root.listFiles()?.filter { it != destination && it.isDirectory }
                            ?.sortedByDescending { it.lastModified() }?.drop(2)?.forEach { it.deleteRecursively() }
                        publish(ticket, LocalSpatialStatus.Ready(next, destination))
                    } finally { source.delete() }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                android.util.Log.w("LocalSpatial", "Wallpaper preparation failed", error)
                publish(ticket, LocalSpatialStatus.Failed(next))
            } catch (_: OutOfMemoryError) {
                publish(ticket, LocalSpatialStatus.Failed(next))
            } catch (_: LinkageError) {
                publish(ticket, LocalSpatialStatus.Failed(next))
            }
        }
    }

    @Synchronized
    private fun publish(ticket: Long, value: LocalSpatialStatus) {
        if (generation == ticket) mutableStatus.value = value
    }

    private suspend fun generate(context: Context, sourceFile: String, output: File, ticket: Long, source: String = sourceFile) {
        val model = ensureDepthModel(context) {
            publish(ticket, LocalSpatialStatus.Preparing(source, PreparationStage.MODEL, it))
        }
        currentCoroutineContext().ensureActive()
        val bitmap = decodeLocalWallpaper(context, File(sourceFile))
        try {
            publish(ticket, LocalSpatialStatus.Preparing(source, PreparationStage.DEPTH))
            val depth = estimateDepth(model, bitmap)
            currentCoroutineContext().ensureActive()
            publish(ticket, LocalSpatialStatus.Preparing(source, PreparationStage.SUBJECT))
            var mask = try { segmentSubject(context, bitmap) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                android.util.Log.i("LocalSpatial", "Subject segmentation unavailable; using continuous depth", error)
                null
            }
            currentCoroutineContext().ensureActive()
            publish(ticket, LocalSpatialStatus.Preparing(source, PreparationStage.SAVING))
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            if (mask != null && !hasReliableSubjectBoundary(pixels, mask, bitmap.width, bitmap.height)) {
                android.util.Log.i("LocalSpatial", "Incomplete or low-contrast cutout; using continuous depth")
                mask = null
            }
            if (mask == null) {
                writeBitmap(bitmap, File(output, "background.png"))
                writeMesh(depth.mesh(), File(output, "background.depth"))
            } else {
                val filled = fillSubjectBackground(pixels, mask, bitmap.width, bitmap.height)
                val background = Bitmap.createBitmap(filled, bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
                try { writeBitmap(background, File(output, "background.png")) }
                finally { background.recycle() }
                for (i in pixels.indices) pixels[i] = (pixels[i] and 0x00ffffff) or
                    ((mask[i] * 255).toInt().coerceIn(0, 255) shl 24)
                val foreground = Bitmap.createBitmap(pixels, bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
                try { writeBitmap(foreground, File(output, "foreground.png")) }
                finally { foreground.recycle() }
                writeMesh(depth.mesh(false), File(output, "background.depth"))
                writeMesh(depth.mesh(true), File(output, "foreground.depth"))
            }
            currentCoroutineContext().ensureActive()
            // Written last; incomplete assets are never considered ready.
            File(output, "ready").writeText(if (mask == null) "1" else "2")
        } finally { bitmap.recycle() }
    }

    /** Static depth only: this path never downloads or invokes an ML model. */
    private suspend fun generateCloud(context: Context, source: File, url: String, output: File) {
        require(java.net.URI(url).scheme == "https")
        val connection = java.net.URL(url).openConnection().apply {
            connectTimeout = 15_000
            readTimeout = 20_000
        }
        val mesh = try {
            val bytes = java.io.ByteArrayOutputStream()
            connection.getInputStream().use { check(copyStreamWithLimit(it, bytes, 1024L * 1024)) }
            currentCoroutineContext().ensureActive()
            DepthMesh.parse(bytes.toString("UTF-8"))
        } finally { (connection as? java.net.HttpURLConnection)?.disconnect() }
        val bitmap = decodeLocalWallpaper(context, source)
        try { writeBitmap(bitmap, File(output, "background.png")) }
        finally { bitmap.recycle() }
        writeMesh(mesh, File(output, "background.depth"))
        File(output, "ready").writeText("1")
    }

    private fun writeBitmap(bitmap: Bitmap, file: File) {
        file.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
    }

    private fun writeMesh(mesh: DepthMesh, file: File) {
        file.bufferedWriter().use { writer ->
            writer.append("${mesh.columns} ${mesh.rows}\n")
            for (value in mesh.depths) writer.append(value.toString()).append(' ')
        }
    }

    private fun validScene(directory: File): Boolean = try {
        val count = File(directory, "ready").readText().toInt()
        count in 1..2 && (if (count == 2) listOf("background", "foreground") else listOf("background")).all {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(File(directory, "$it.png").path, bounds)
            DepthMesh.parse(File(directory, "$it.depth").readText())
            bounds.outWidth in 1..2048 && bounds.outHeight in 1..2048
        }
    } catch (_: Exception) { false }

    suspend fun loadScene(directory: File): LocalScene = withContext(Dispatchers.IO) {
        check(validScene(directory))
        val names = if (File(directory, "ready").readText() == "2") listOf("background", "foreground") else listOf("background")
        val layers = mutableListOf<LocalLayer>()
        try {
            for (name in names) {
                val mesh = DepthMesh.parse(File(directory, "$name.depth").readText())
                val bitmap = BitmapFactory.decodeFile(File(directory, "$name.png").path)
                    ?: throw IOException("Prepared wallpaper is unreadable")
                layers.add(LocalLayer(bitmap, mesh))
            }
            LocalScene(layers)
        } catch (error: Throwable) {
            layers.forEach { it.bitmap.recycle() }
            throw error
        }
    }
}

/** Coil applies EXIF orientation; the app owns the software bitmap and bounds decoding. */
internal suspend fun decodeLocalWallpaper(context: Context, file: File): Bitmap {
    val result = context.imageLoader.execute(ImageRequest.Builder(context).data(file)
        .size(2048, 2048).scale(Scale.FIT).allowHardware(false)
        .memoryCachePolicy(CachePolicy.DISABLED).diskCachePolicy(CachePolicy.DISABLED).build()) as? SuccessResult
        ?: throw IOException("Cannot decode wallpaper")
    val decoded = (result.drawable as? BitmapDrawable)?.bitmap ?: throw IOException("Unsupported image")
    val bitmap = decoded.copy(Bitmap.Config.ARGB_8888, false) ?: throw IOException("Cannot copy wallpaper")
    // The drawable is owned by this uncached request, but leave its lifetime to Coil/GC.
    return bitmap
}

