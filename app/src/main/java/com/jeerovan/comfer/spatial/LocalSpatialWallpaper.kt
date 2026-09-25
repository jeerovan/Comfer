package com.jeerovan.comfer.spatial

import android.content.SharedPreferences
import android.graphics.Paint
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ViewInAr
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.jeerovan.comfer.AnimatedBackground
import com.jeerovan.comfer.R
import kotlinx.coroutines.CancellationException
import java.io.File

internal data class LocalWallpaperChoice(val path: String?, val spatial: Boolean)
internal data class LocalSpatialHint(val pending: Boolean, val failed: Boolean, val progress: Float?, val retry: () -> Unit)

@Composable
internal fun rememberLocalWallpaperChoice(): LocalWallpaperChoice {
    val context = LocalContext.current.applicationContext
    val prefs = remember(context) { spatialPreferences(context) }
    fun read() = LocalWallpaperChoice(null, prefs.getBoolean(LOCAL_SPATIAL, false))
    var choice by remember(prefs) { mutableStateOf(read()) }
    DisposableEffect(prefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> choice = read() }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        choice = read()
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    return choice
}

@Composable
fun LocalWallpaperSetting() {
    val context = LocalContext.current
    val choice = rememberLocalWallpaperChoice()
    val modelState by DepthModelDownload.state.collectAsState()
    LaunchedEffect(Unit) { DepthModelDownload.check(context.applicationContext) }
    LaunchedEffect(choice.spatial) {
        if (choice.spatial) DepthModelDownload.start(context.applicationContext)
    }
    fun setEnabled(enabled: Boolean) {
        spatialPreferences(context).edit().putBoolean(LOCAL_SPATIAL, enabled).apply()
        if (enabled) DepthModelDownload.start(context.applicationContext)
        else LocalSpatialRepository.prepare(context, null, false)
    }
    ListItem(
        headlineContent = { Text(stringResource(R.string.local_spatial_title)) },
        supportingContent = { Text(stringResource(when (modelState) {
            DepthModelState.READY -> R.string.local_spatial_description
            DepthModelState.DOWNLOADING -> R.string.local_spatial_downloading
            DepthModelState.FAILED -> R.string.local_spatial_model_failed
            else -> R.string.local_spatial_download_description
        })) },
        leadingContent = { Icon(Icons.Outlined.ViewInAr, contentDescription = null) },
        trailingContent = { Switch(checked = choice.spatial, onCheckedChange = ::setEnabled) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable { setEnabled(!choice.spatial) },
    )
}

/** Applies completed assets in place. There is deliberately no preview or confirmation screen. */
@Composable
internal fun renderLocalSpatialWallpaper(motionEnabled: Boolean, width: Float, height: Float,
    path: String? = null, ownWallpapers: Boolean = true, cloudDepthUrl: String? = null): LocalSpatialHint {
    val context = LocalContext.current.applicationContext
    val preference = rememberLocalWallpaperChoice()
    val choice = LocalWallpaperChoice(path, if (ownWallpapers) preference.spatial else motionEnabled && cloudDepthUrl != null)
    val status by LocalSpatialRepository.status.collectAsState()
    val active = rememberWallpaperActive()
    LaunchedEffect(choice, cloudDepthUrl) {
        LocalSpatialRepository.prepare(context, choice.path, choice.spatial, cloudDepthUrl = if (ownWallpapers) null else cloudDepthUrl)
    }
    val ready = (status as? LocalSpatialStatus.Ready)?.takeIf { it.source == choice.path && choice.spatial }
    var loadFailed by remember(ready) { mutableStateOf(false) }
    val loaded by produceState<Pair<LocalSpatialStatus.Ready, LocalScene>?>(null, ready) {
        value = null
        if (ready != null) {
            try { value = ready to LocalSpatialRepository.loadScene(ready.directory) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { loadFailed = true }
            catch (_: OutOfMemoryError) { loadFailed = true }
        }
    }
    val scene = loaded?.takeIf { it.first == ready }?.second
    // Rendered bitmaps follow Compose/RenderThread lifetime; do not recycle them during a draw.
    val pose = rememberSpatialTilt(choice.spatial && scene != null && active, choice.path)
    val orbit = rememberWallpaperOrbit(motionEnabled, active)
    val reveal = remember(choice.path) { Animatable(0f) }
    LaunchedEffect(scene, active) {
        if (scene == null) reveal.snapTo(0f)
        else if (active) reveal.animateTo(1f, tween(350))
        else reveal.snapTo(1f)
    }
    val paint = remember { Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG) }
    Box(Modifier.fillMaxSize()) {
        // Keep this composed through preparation and reveal so its orbit phase is preserved.
        if (scene == null || reveal.value < 1f) AnimatedBackground(choice.path?.let(::File), 0, motionEnabled, width, height,
            externalOrbit = orbit, spatialOverscan = choice.spatial)
        if (scene != null) Canvas(Modifier.fillMaxSize()) {
            if (size.width <= 0 || size.height <= 0) return@Canvas
            val tilt = pose.offset.value
            val o = if (motionEnabled) wallpaperOrbit(orbit.value) else 0f to 0f
            paint.alpha = (reveal.value * 255).toInt()
            for (layer in scene!!.layers) {
                drawContext.canvas.nativeCanvas.drawBitmapMesh(layer.bitmap, layer.mesh.columns, layer.mesh.rows,
                    layer.mesh.project(size.width, size.height, layer.bitmap.width, layer.bitmap.height,
                        tilt.x, tilt.y, o.first, o.second), 0, null, 0, paint)
            }
        }
    }
    val pending = choice.spatial && choice.path != null &&
        (status is LocalSpatialStatus.Preparing || ready != null && (scene == null || reveal.value < 1f) && !loadFailed || status is LocalSpatialStatus.Idle)
    val failed = choice.spatial && choice.path != null &&
        (loadFailed || (status as? LocalSpatialStatus.Failed)?.source == choice.path)
    return LocalSpatialHint(pending, failed, (status as? LocalSpatialStatus.Preparing)?.progress) {
        loadFailed = false
        LocalSpatialRepository.prepare(context, choice.path, true, retry = true, invalidateCache = ready != null, cloudDepthUrl = if (ownWallpapers) null else cloudDepthUrl)
    }
}

@Composable
internal fun SpatialProgressHint(modifier: Modifier = Modifier, active: Boolean, failed: Boolean,
                                 progress: Float?, onRetry: () -> Unit) {
    val label = stringResource(if (failed) R.string.local_spatial_retry else R.string.local_spatial_preparing)
    Box(modifier.size(48.dp).background(Color.Black.copy(alpha = 0.65f), CircleShape)
        .then(if (failed) Modifier.clickable(onClickLabel = label, onClick = onRetry) else Modifier)
        .semantics(mergeDescendants = true) {
            contentDescription = label
            liveRegion = LiveRegionMode.Polite
            if (!failed) progressBarRangeInfo = progress?.let { ProgressBarRangeInfo(it, 0f..1f) }
                ?: ProgressBarRangeInfo.Indeterminate
        }, contentAlignment = Alignment.Center) {
        Icon(Icons.Outlined.ViewInAr, null, Modifier.size(20.dp), tint = Color.White)
        if (!failed) {
            if (progress != null || !active) CircularProgressIndicator(
                progress = { progress ?: 0.25f }, modifier = Modifier.size(36.dp).clearAndSetSemantics {},
                color = Color.White, strokeWidth = 2.dp)
            else CircularProgressIndicator(Modifier.size(36.dp).clearAndSetSemantics {}, color = Color.White, strokeWidth = 2.dp)
        } else Text("!", color = Color.White, modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp))
    }
}
