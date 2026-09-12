package com.jeerovan.comfer

import android.app.ActivityOptions
import android.view.View
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntRect
import kotlin.math.ceil
import kotlin.math.floor

/** Convert Compose window coordinates to the source View's coordinate space. */
internal fun iconLaunchBounds(bounds: Rect?, sourceOrigin: Offset): IntRect? {
    if (bounds == null || !listOf(bounds.left, bounds.top, bounds.right, bounds.bottom,
            sourceOrigin.x, sourceOrigin.y).all { it.isFinite() } ||
        bounds.width <= 0f || bounds.height <= 0f) return null
    return IntRect(
        floor(bounds.left - sourceOrigin.x).toInt(),
        floor(bounds.top - sourceOrigin.y).toInt(),
        ceil(bounds.right - sourceOrigin.x).toInt(),
        ceil(bounds.bottom - sourceOrigin.y).toInt()
    )
}

/** Android owns the window animation and respects the user's system animation settings. */
internal fun iconLaunchOptions(source: View, windowBounds: Rect?): ActivityOptions? {
    if (!source.isAttachedToWindow) return null
    val origin = IntArray(2)
    source.getLocationInWindow(origin)
    val bounds = iconLaunchBounds(windowBounds, Offset(origin[0].toFloat(), origin[1].toFloat()))
        ?: return null
    // Leave Intent.sourceBounds unset: Android fills it in with screen coordinates.
    return ActivityOptions.makeScaleUpAnimation(source, bounds.left, bounds.top, bounds.width, bounds.height)
}
