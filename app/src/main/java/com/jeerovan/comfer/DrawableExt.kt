package com.jeerovan.comfer

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.painter.Painter
import kotlin.math.roundToInt

private const val MAX_DRAWABLE_BITMAP_EDGE = 512

internal data class BitmapDimensions(val width: Int, val height: Int)

internal fun constrainedBitmapDimensions(
    intrinsicWidth: Int,
    intrinsicHeight: Int,
    maxEdge: Int = MAX_DRAWABLE_BITMAP_EDGE,
): BitmapDimensions {
    require(maxEdge > 0) { "maxEdge must be positive" }
    val width = intrinsicWidth.coerceAtLeast(1)
    val height = intrinsicHeight.coerceAtLeast(1)
    val largestEdge = maxOf(width, height)
    if (largestEdge <= maxEdge) return BitmapDimensions(width, height)

    val scale = maxEdge.toFloat() / largestEdge
    return BitmapDimensions(
        width = (width * scale).roundToInt().coerceAtLeast(1),
        height = (height * scale).roundToInt().coerceAtLeast(1),
    )
}

internal fun Drawable.toOwnedBitmap(maxEdge: Int = MAX_DRAWABLE_BITMAP_EDGE): Bitmap? {
    require(maxEdge > 0) { "maxEdge must be positive" }
    return try {
        val dimensions = constrainedBitmapDimensions(intrinsicWidth, intrinsicHeight, maxEdge)
        val drawable = constantState?.newDrawable()?.mutate() ?: this
        drawable.drawOwnedBitmap(Bitmap.createBitmap(
            dimensions.width,
            dimensions.height,
            Bitmap.Config.ARGB_8888,
        ))
    } catch (_: OutOfMemoryError) {
        // Use the existing placeholder; do not allocate a smaller retry under memory pressure.
        null
    } catch (e: RuntimeException) {
        Log.w("DrawableBitmap", "Could not rasterize drawable", e)
        null
    }
}

/** Takes ownership of [bitmap], returning it on success or releasing it on every failed exit. */
internal fun Drawable.drawOwnedBitmap(bitmap: Bitmap): Bitmap {
    var completed = false
    try {
        val canvas = Canvas(bitmap)
        synchronized(this) {
            val originalBounds = Rect(bounds)
            try {
                setBounds(0, 0, canvas.width, canvas.height)
                draw(canvas)
            } finally {
                bounds = originalBounds
            }
        }
        bitmap.prepareToDraw()
        completed = true
        return bitmap
    } finally {
        if (!completed) bitmap.recycle()
    }
}

/**
 * Takes an owned, bounded snapshot instead of retaining a mutable Drawable and
 * asking it to draw during every Compose frame.
 */
@Composable
fun rememberDrawableBitmapPainter(drawable: Drawable?): Painter {
    return remember(drawable) {
        drawable?.toOwnedBitmap()?.asImageBitmap()?.let(::BitmapPainter)
            ?: ColorPainter(Color.Transparent)
    }
}
