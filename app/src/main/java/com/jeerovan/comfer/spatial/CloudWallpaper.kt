package com.jeerovan.comfer.spatial

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.datastore.preferences.core.stringPreferencesKey
import com.jeerovan.comfer.ImageData
import com.jeerovan.comfer.PreferenceManager
import com.jeerovan.comfer.settingsDataStore
import com.jeerovan.comfer.utils.CommonUtil
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import java.io.File

internal fun cloudWallpaperForPath(path: String?, metadata: String?): ImageData? =
    runCatching { metadata?.let { Json.decodeFromString<ImageData>(it) } }.getOrNull()
        ?.takeIf { path != null && File(path).name == "comfer_${it.id}.jpg" }

/** Detail responses can enrich a saved wallpaper without selecting or downloading another photo. */
internal suspend fun resolveCloudSpatialMetadata(
    image: ImageData,
    fetch: suspend (Int) -> ImageData,
): ImageData {
    if (!image.spatialSceneUrl.isNullOrBlank() || !image.depthUrl.isNullOrBlank()) return image
    val detail = fetch(image.id)
    require(detail.id == image.id) { "Wallpaper detail ID does not match" }
    return image.copy(
        depthUrl = detail.depthUrl?.takeIf { it.isNotBlank() },
        spatialSceneUrl = detail.spatialSceneUrl?.takeIf { it.isNotBlank() },
    )
}

@Composable
internal fun rememberCloudWallpaper(path: String?, motionEnabled: Boolean): ImageData? {
    val context = LocalContext.current.applicationContext
    val metadata by remember(context) {
        context.settingsDataStore.data.map { it[stringPreferencesKey(PreferenceManager.KEY_IMAGE_DATA)] }
            .distinctUntilChanged()
    }.collectAsState(initial = PreferenceManager.getImageDataString(context, null))
    val current = remember(path, metadata) { cloudWallpaperForPath(path, metadata) }
    LaunchedEffect(path, motionEnabled, current) {
        if (!motionEnabled || current == null) return@LaunchedEffect
        try {
            val resolved = resolveCloudSpatialMetadata(current) { id ->
                CommonUtil.fetchWallpaperDetails(context, id)
            }
            currentCoroutineContext().ensureActive()
            // A rotation or metadata refresh during the request owns the newer selection.
            if (resolved != current && PreferenceManager.getBackgroundImagePath(context) == path &&
                PreferenceManager.getImageData(context) == current) {
                PreferenceManager.setImageDataString(context, Json.encodeToString(resolved))
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            android.util.Log.w("CloudWallpaper", "Spatial metadata unavailable", error)
        }
    }
    return current
}
