package com.jeerovan.comfer

import android.content.Context
import com.jeerovan.comfer.spatial.LocalSpatialRepository
import com.jeerovan.comfer.spatial.resolveCloudSpatialMetadata
import com.jeerovan.comfer.utils.CommonUtil
import com.jeerovan.comfer.utils.decodeBoundedWallpaper
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
private data class PreloadedWallpaper(val image: ImageData, val isDay: Boolean, val spatialChecked: Boolean = false)

/** A single opt-in preview, separate from the selected wallpaper and its preferences.
 * Callers hold WallpaperWorkCoordinator; interrupted downloads are never published. */
internal class CloudWallpaperPreload(private val context: Context) {
    private val directory = File(context.noBackupFilesDir, "wallpaper-preload")
    private val photo = File(directory, "image.source")
    private val metadata = File(directory, "ready.json")
    private val scene = File(directory, "scene")

    private fun saved(): PreloadedWallpaper? = runCatching {
        Json.decodeFromString<PreloadedWallpaper>(metadata.readText()).takeIf { photo.isFile && photo.length() > 0 }
    }.getOrNull()

    fun imageFor(schedule: WallpaperSchedule): ImageData? = saved()?.takeIf { it.isDay == schedule.isDay }?.image

    fun sourceFor(image: ImageData): File? = photo.takeIf { saved()?.image == image }

    private fun save(value: PreloadedWallpaper) {
        val temporary = File(directory, "ready.partial")
        try {
            temporary.writeText(Json.encodeToString(value))
            check(temporary.renameTo(metadata))
        } finally { temporary.delete() }
    }

    suspend fun prepare(
        schedule: WallpaperSchedule,
        fetch: suspend (Int) -> ImageData = { CommonUtil.fetchCloudWallpaper(context, it) },
        download: suspend (String, File) -> Boolean = { url, file -> CommonUtil.downloadFileWithinLimit(context, url, file) },
        details: suspend (Int) -> ImageData = { CommonUtil.fetchWallpaperDetails(context, it) },
        spatial: suspend (File, ImageData, File) -> Unit = { source, image, output ->
            LocalSpatialRepository.generateCloudAssets(context, source, image, output)
        },
    ): Boolean {
        var current = saved()
        if (current == null) {
            directory.deleteRecursively()
            check(directory.mkdirs())
            val image = fetch(schedule.cloudRequestHour)
            val temporary = File(directory, "image.partial")
            try {
                if (!download(image.imageUrl, temporary)) return false
                val decoded = decodeBoundedWallpaper(temporary, 2048, 2048) ?: return false
                decoded.recycle()
                currentCoroutineContext().ensureActive()
                check(temporary.renameTo(photo))
                current = PreloadedWallpaper(image, schedule.isDay)
                save(current)
            } finally { temporary.delete() }
        }
        // Keep the first prepared photo until opt-in; do not rotate hidden previews.
        if (!current.spatialChecked) {
            current = current.copy(image = resolveCloudSpatialMetadata(current.image, details), spatialChecked = true)
            save(current)
        }
        if (current.image.spatialSceneUrl.isNullOrBlank() && current.image.depthUrl.isNullOrBlank()) return true
        if (LocalSpatialRepository.validScene(scene)) return true
        val staging = File(directory, "scene.partial")
        staging.deleteRecursively()
        check(staging.mkdirs())
        try {
            spatial(photo, current.image, staging)
            currentCoroutineContext().ensureActive()
            check(LocalSpatialRepository.validScene(staging))
            scene.deleteRecursively()
            check(staging.renameTo(scene))
        } finally { staging.deleteRecursively() }
        return true
    }

    suspend fun adoptScene(image: ImageData, destination: File) {
        if (sourceFor(image) != null && LocalSpatialRepository.validScene(scene)) {
            LocalSpatialRepository.cacheCloudScene(context, destination, image, scene)
        }
    }

    fun clear() { directory.deleteRecursively() }
}
