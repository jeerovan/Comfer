package com.jeerovan.comfer

import android.app.WallpaperManager
import android.os.Build
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

internal fun contrastingTitleColor(background: Color): Color =
    if ((background.luminance() + .05f) / .05f >= 1.05f / (background.luminance() + .05f)) Color.Black else Color.White

internal data class WallpaperSamples(val width: Int, val height: Int, val pixels: IntArray) {
    fun background(rect: Rect, screenWidth: Float, screenHeight: Float, motion: Boolean): Color? {
        if (screenWidth <= 0 || screenHeight <= 0 || rect.isEmpty) return null
        val scale = maxOf(screenWidth / width, screenHeight / height) * if (motion) 1.2f else 1f
        val left = (screenWidth - width * scale) / 2
        val top = (screenHeight - height * scale) / 2
        // Cover the motion envelope instead of chasing every animation frame.
        val dx = if (motion) screenWidth * .08f else 0f
        val dy = if (motion) screenHeight * .08f else 0f
        var red = 0f; var green = 0f; var blue = 0f
        for (y in 0..4) for (x in 0..8) {
            val px = (((rect.left - dx + (rect.width + dx * 2) * x / 8) - left) / scale).toInt().coerceIn(0, width - 1)
            val py = (((rect.top - dy + (rect.height + dy * 2) * y / 4) - top) / scale).toInt().coerceIn(0, height - 1)
            val c = Color(pixels[py * width + px])
            red += c.red; green += c.green; blue += c.blue
        }
        return Color(red / 45, green / 45, blue / 45)
    }
}

internal data class DrawerContrast(val samples: WallpaperSamples?, val fallback: Color?, val motion: Boolean)
internal val LocalDrawerContrast = staticCompositionLocalOf<DrawerContrast?> { null }
internal val LocalDrawerScrolling = compositionLocalOf { false }

@Composable
internal fun rememberDrawerContrast(path: String?, version: Int, motion: Boolean): DrawerContrast? {
    val context = LocalContext.current
    var systemRevision by remember { mutableIntStateOf(0) }
    DisposableEffect(context, path) {
        val manager = WallpaperManager.getInstance(context)
        val listener = if (path == null && Build.VERSION.SDK_INT >= 27) WallpaperManager.OnColorsChangedListener { _, _ -> systemRevision++ } else null
        if (listener != null) try { manager.addOnColorsChangedListener(listener, android.os.Handler(android.os.Looper.getMainLooper())) } catch (_: Exception) { }
        onDispose { if (listener != null) try { manager.removeOnColorsChangedListener(listener) } catch (_: Exception) { } }
    }
    val result by produceState<DrawerContrast?>(null, path, version, motion, systemRevision) {
        value = null
        value = withContext(Dispatchers.IO) {
            val samples = try {
                if (path == null) null else {
                    val request = ImageRequest.Builder(context).data(path).size(256).allowHardware(false)
                        .memoryCacheKey("drawer-contrast:$path:$version").diskCacheKey("launcher-wallpaper:$path:$version").build()
                    val drawable = (context.imageLoader.execute(request) as? SuccessResult)?.drawable
                    drawable?.toOwnedBitmap()?.let { bitmap ->
                        try {
                            val pixels = IntArray(bitmap.width * bitmap.height)
                            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
                            WallpaperSamples(bitmap.width, bitmap.height, pixels)
                        } finally { bitmap.recycle() }
                    }
                }
            } catch (cancelled: java.util.concurrent.CancellationException) { throw cancelled }
              catch (_: Exception) { null } catch (_: OutOfMemoryError) { null }
            val fallback = try {
                val colors = if (Build.VERSION.SDK_INT >= 27) WallpaperManager.getInstance(context).getWallpaperColors(WallpaperManager.FLAG_SYSTEM) else null
                if (colors != null && Build.VERSION.SDK_INT >= 31) {
                    if (colors.colorHints and android.app.WallpaperColors.HINT_SUPPORTS_DARK_TEXT != 0) Color.Black else Color.White
                } else colors?.primaryColor?.toArgb()?.let { contrastingTitleColor(Color(it)) }
            } catch (_: Exception) { null }
            DrawerContrast(samples, fallback, motion)
        }
    }
    return result
}

@Composable
internal fun adaptiveDrawerTitle(): Pair<Color, Modifier> {
    val contrast = LocalDrawerContrast.current
    val scrolling = LocalDrawerScrolling.current
    val view = LocalView.current
    var bounds by remember { mutableStateOf(Rect.Zero) }
    var selected by remember { mutableStateOf<Color?>(null) }
    LaunchedEffect(contrast, bounds, scrolling) {
        if (!scrolling) {
            delay(150)
            val location = IntArray(2); view.getLocationInWindow(location)
            selected = contrast?.samples?.background(bounds.translate(-location[0].toFloat(), -location[1].toFloat()), view.width.toFloat(), view.height.toFloat(), contrast.motion)
                ?.let(::contrastingTitleColor) ?: contrast?.fallback
        }
    }
    return (if (contrast == null) Color.White else selected ?: contrast.fallback ?: Color.White) to Modifier.onGloballyPositioned { bounds = it.boundsInWindow() }
}
