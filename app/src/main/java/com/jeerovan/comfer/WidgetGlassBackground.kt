package com.jeerovan.comfer

import android.app.WallpaperManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.annotation.RequiresApi
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal interface GlassWallpaperColors {
    suspend fun current(): Color?
    fun observe(onColor: (Color?) -> Unit): () -> Unit
}

@RequiresApi(27)
private class AndroidGlassWallpaperColors(private val manager: WallpaperManager) : GlassWallpaperColors {
    override suspend fun current(): Color? = withContext(Dispatchers.IO) {
        // This IPC can calculate colors, so never run it on the UI thread.
        try { manager.getWallpaperColors(WallpaperManager.FLAG_SYSTEM)?.primaryColor?.toArgb()?.let { Color(it) } }
        catch (_: RuntimeException) { null }
    }

    override fun observe(onColor: (Color?) -> Unit): () -> Unit {
        val listener = WallpaperManager.OnColorsChangedListener { colors, which ->
            if (which and WallpaperManager.FLAG_SYSTEM != 0) onColor(colors?.primaryColor?.toArgb()?.let { Color(it) })
        }
        try { manager.addOnColorsChangedListener(listener, Handler(Looper.getMainLooper())) }
        catch (_: RuntimeException) { return {} }
        return { try { manager.removeOnColorsChangedListener(listener) } catch (_: RuntimeException) { } }
    }
}

/** One snapshot on resume plus OS callbacks. No polling, bitmap reads, or frame loop. */
@Composable
internal fun rememberSystemGlassBackground(enabled: Boolean, source: GlassWallpaperColors? = null): Color? {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val scope = rememberCoroutineScope()
    val provider = remember(context, enabled, source) {
        if (!enabled) null else source ?: if (Build.VERSION.SDK_INT >= 27)
            AndroidGlassWallpaperColors(WallpaperManager.getInstance(context)) else null
    }
    var background by remember(provider) { mutableStateOf<Color?>(null) }
    DisposableEffect(provider, lifecycle) {
        if (provider == null) return@DisposableEffect onDispose { }
        var active = false
        var revision = 0
        var read: Job? = null
        var unsubscribe: (() -> Unit)? = null
        fun stop() {
            active = false
            revision++
            read?.cancel()
            unsubscribe?.invoke()
            unsubscribe = null
        }
        fun start() {
            if (active) return
            active = true
            unsubscribe = provider.observe { color ->
                if (active) {
                    revision++
                    background = color
                }
            }
            val expectedRevision = revision
            read = scope.launch {
                val color = provider.current()
                if (active && revision == expectedRevision) background = color
            }
        }
        val observer = LifecycleEventObserver { _, _ ->
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) start() else stop()
        }
        lifecycle.addObserver(observer)
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) start()
        onDispose {
            lifecycle.removeObserver(observer)
            stop()
        }
    }
    return if (enabled) background else null
}
