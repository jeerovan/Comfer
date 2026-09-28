package com.jeerovan.comfer

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.layout
import kotlin.math.ceil

internal const val MIN_WIDGET_HEIGHT = .5f
internal const val MAX_WIDGET_HEIGHT = 2f
internal fun widgetHeightScale(value: Float): Float =
    if (value.isFinite()) value.coerceIn(MIN_WIDGET_HEIGHT, MAX_WIDGET_HEIGHT) else 1f

internal fun widgetHeightPreferenceKey(id: String): String {
    require(id == "time" || id == "date")
    return "${id}_height_scale"
}

/** The renderer reports the pixels that stretch; padding and clock line gaps stay fixed. */
internal val LocalWidgetStretchHeight = staticCompositionLocalOf<((Float) -> Unit)?> { null }

@Composable
internal fun VerticalTextScale(scale: Float, content: @Composable () -> Unit) {
    val report = LocalWidgetStretchHeight.current
    Box(Modifier.layout { measurable, constraints ->
        val child = measurable.measure(constraints.copy(minHeight = 0))
        val applied = minOf(widgetHeightScale(scale), constraints.maxHeight.toFloat() / child.height.coerceAtLeast(1))
        report?.invoke(child.height.toFloat())
        layout(child.width, ceil(child.height * applied).toInt().coerceIn(constraints.minHeight, constraints.maxHeight)) {
            child.placeWithLayer(0, 0) {
                scaleY = applied
                transformOrigin = TransformOrigin(0f, 0f)
            }
        }
    }) { content() }
}
