package com.jeerovan.comfer.notifications

import android.graphics.Bitmap
import android.graphics.Paint
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.size
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

internal fun notificationIconShadowColor(color: Color) =
    if (color.luminance() > .45f) Color.Black.copy(alpha = .85f) else Color.White.copy(alpha = .85f)

/** Bake a silhouette shadow once per icon/color/size, including on pre-Android-12 devices. */
@Composable
internal fun NotificationContrastIcon(painter: Painter, iconSize: Dp, color: Color, glass: Boolean = false) {
    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    val bitmap = remember(painter, iconSize, color, density, direction, glass) {
        renderNotificationContrastIcon(painter, iconSize, color, density, direction, glass).asImageBitmap()
    }

    Image(bitmap, contentDescription = null, modifier = Modifier.size(iconSize + 8.dp))
}

internal fun renderNotificationContrastIcon(painter: Painter, iconSize: Dp, color: Color,
    density: androidx.compose.ui.unit.Density, direction: androidx.compose.ui.unit.LayoutDirection,
    glass: Boolean = false): Bitmap {
    val size = with(density) { iconSize.roundToPx() }.coerceAtLeast(1)
    val padding = with(density) { 4.dp.roundToPx() }.coerceAtLeast(1)
    val icon = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    CanvasDrawScope().draw(density, direction, Canvas(icon.asImageBitmap()), Size(size.toFloat(), size.toFloat())) {
        with(painter) { draw(this@draw.size) }
    }

    // Rendering contract for future changes:
    // - Inspect the original pixels BEFORE any tint/gradient. A posted smallIcon can be
    //   opaque, adaptive, or multicolor; its Android Icon type does not prove tint safety.
    // - Recolor only single-RGB glyphs, whose detail is encoded entirely in alpha.
    //   Multiple visible colors (including black/white) are artwork: keep their RGB.
    // - Never blanket-tint artwork or apply the glass SRC_IN gradient to it. Both erase
    //   the logo against its background, leaving the white circle/square seen before.
    // - Keep opacity and silhouette shadows on BOTH paths. Changes here must retain the
    //   opaque/adaptive artwork, glass, opacity and posted-icon-update regressions.
    val tintable = icon.hasSingleVisibleColor()
    android.graphics.Canvas(icon).drawColor(
        if (tintable) color.toArgb() else Color.White.copy(alpha = color.alpha).toArgb(),
        if (tintable) android.graphics.PorterDuff.Mode.SRC_IN else android.graphics.PorterDuff.Mode.DST_IN,
    )
    val result = Bitmap.createBitmap(size + padding * 2, size + padding * 2, Bitmap.Config.ARGB_8888)
    val offset = IntArray(2)
    val shadow = icon.extractAlpha(Paint(Paint.ANTI_ALIAS_FLAG).apply {
        maskFilter = android.graphics.BlurMaskFilter(1.5f * density.density, android.graphics.BlurMaskFilter.Blur.NORMAL)
    }, offset)
    val canvas = android.graphics.Canvas(result)
    canvas.drawBitmap(shadow, padding + offset[0].toFloat(), padding + offset[1] + .75f * density.density,
        Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = notificationIconShadowColor(color).toArgb() })
    if (glass && tintable) {
        // Shade only the cached glyph; its alpha already includes user opacity.
        val stops = com.jeerovan.comfer.widgetGlassStops(color.copy(alpha = 1f))
        android.graphics.Canvas(icon).drawRect(0f, 0f, size.toFloat(), size.toFloat(),
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = android.graphics.LinearGradient(0f, 0f, 0f, size.toFloat(),
                    stops.map { it.second.toArgb() }.toIntArray(), stops.map { it.first }.toFloatArray(),
                    android.graphics.Shader.TileMode.CLAMP)
                xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.SRC_IN)
            })
    }
    canvas.drawBitmap(icon, padding.toFloat(), padding.toFloat(), Paint(Paint.ANTI_ALIAS_FLAG))
    shadow.recycle()
    icon.recycle()
    return result
}

/** Conservative: color variation must survive, even on grayscale or transparent artwork. */
private fun Bitmap.hasSingleVisibleColor(): Boolean {
    val pixels = IntArray(width * height)
    getPixels(pixels, 0, width, 0, 0, width, height)
    var firstRgb: Int? = null
    for (pixel in pixels) {
        if (pixel ushr 24 == 0) continue
        val rgb = pixel and 0x00ffffff
        if (firstRgb == null) firstRgb = rgb else if (rgb != firstRgb) return false
    }
    return true
}
