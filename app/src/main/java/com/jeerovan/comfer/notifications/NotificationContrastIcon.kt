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
internal fun NotificationContrastIcon(painter: Painter, iconSize: Dp, color: Color) {
    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    val bitmap = remember(painter, iconSize, color, density, direction) {
        renderNotificationContrastIcon(painter, iconSize, color, density, direction).asImageBitmap()
    }

    Image(bitmap, contentDescription = null, modifier = Modifier.size(iconSize + 8.dp))
}

internal fun renderNotificationContrastIcon(painter: Painter, iconSize: Dp, color: Color,
    density: androidx.compose.ui.unit.Density, direction: androidx.compose.ui.unit.LayoutDirection): Bitmap {
        val size = with(density) { iconSize.roundToPx() }.coerceAtLeast(1)
        val padding = with(density) { 4.dp.roundToPx() }.coerceAtLeast(1)
        val silhouette = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        CanvasDrawScope().draw(density, direction, Canvas(silhouette.asImageBitmap()), Size(size.toFloat(), size.toFloat())) {
            with(painter) { draw(this@draw.size, colorFilter = ColorFilter.tint(color)) }
        }
        val result = Bitmap.createBitmap(size + padding * 2, size + padding * 2, Bitmap.Config.ARGB_8888)
        val offset = IntArray(2)
        val shadow = silhouette.extractAlpha(Paint(Paint.ANTI_ALIAS_FLAG).apply {
            maskFilter = android.graphics.BlurMaskFilter(1.5f * density.density, android.graphics.BlurMaskFilter.Blur.NORMAL)
        }, offset)
        val canvas = android.graphics.Canvas(result)
        canvas.drawBitmap(shadow, padding + offset[0].toFloat(), padding + offset[1] + .75f * density.density,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = notificationIconShadowColor(color).toArgb() })
        canvas.drawBitmap(silhouette, padding.toFloat(), padding.toFloat(), Paint(Paint.ANTI_ALIAS_FLAG))
        shadow.recycle()
        silhouette.recycle()
        return result
}
