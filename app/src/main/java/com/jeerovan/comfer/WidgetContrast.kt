package com.jeerovan.comfer

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.min

/** Preserve intentional shadow-off and contrasting custom colors; repair low-contrast shadows. */
internal fun widgetShadowColor(foreground: Color, preferred: Color? = null): Color {
    if (preferred != null && preferred.alpha == 0f) return Color.Transparent
    val light = foreground.luminance()
    if (preferred != null) {
        val other = preferred.luminance()
        if ((max(light, other) + .05f) / (min(light, other) + .05f) >= 3f) return preferred
    }
    return (if (light > .45f) Color.Black else Color.White).copy(alpha = .85f)
}

/** Soft contour for custom Canvas graphics, without API-dependent hardware blur. */
internal fun DrawScope.widgetHalo(foreground: Color, draw: DrawScope.(Color, Float) -> Unit) {
    val shadow = widgetShadowColor(foreground)
    for ((radius, opacity) in listOf(3f to .12f, 2f to .2f, 1f to .4f)) {
        draw(shadow.copy(alpha = opacity * foreground.alpha), radius.dp.toPx())
    }
}
