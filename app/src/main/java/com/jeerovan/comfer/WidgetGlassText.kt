package com.jeerovan.comfer

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp

/** A tinted clear/smoked glass base; preserve the user's opacity and solid-text mode. */
internal fun widgetGlassColor(settings: SettingsUiState, base: Color, systemBackground: Color? = null): Color {
    if (!settings.widgetGlassEffect) return base
    // Wallpaper colors already selects the palette's vibrant text color upstream.
    // Preserve it instead of replacing it with the often-neutral dominant background.
    if (settings.showThemedText && settings.themedColors != null &&
        (settings.autoWallpapers || settings.monochrome)) return base
    val background = if (settings.autoWallpapers || settings.monochrome)
        settings.themedColors?.glassTint?.let { Color(it) } else systemBackground
    if (background == null || background.alpha == 0f) return base
    val opaque = background.copy(alpha = 1f)
    val anchor = contrastingTitleColor(opaque)
    var tint = androidx.compose.ui.graphics.lerp(opaque, anchor, .72f)
    // Retain hue while keeping the underlying text color distinct from the wallpaper.
    for (step in 1..7) {
        val a = tint.luminance(); val b = opaque.luminance()
        if ((maxOf(a, b) + .05f) / (minOf(a, b) + .05f) >= 4.5f) break
        tint = androidx.compose.ui.graphics.lerp(opaque, anchor, (.72f + step * .04f).coerceAtMost(1f))
    }
    return tint.copy(alpha = base.alpha)
}

/** Static lighting inside the glyphs: no backdrop layer, blur, or animation. */
internal fun widgetGlassStops(color: Color, rim: Boolean = false): Array<Pair<Float, Color>> {
    fun tint(white: Float, opacity: Float) =
        lerp(color.copy(alpha = 1f), Color.White, white).copy(alpha = color.alpha * opacity)
    return if (rim) arrayOf(
        0f to tint(.65f, .95f),
        .42f to tint(.3f, .65f),
        .55f to tint(0f, .18f),
        1f to tint(.4f, .75f),
    ) else arrayOf(
        0f to tint(.3f, .95f),
        .4f to tint(.12f, .78f),
        .52f to tint(0f, .38f),
        .8f to tint(.08f, .58f),
        1f to tint(.2f, .85f),
    )
}

@Composable
internal fun WidgetGlassText(text: String, color: Color, style: TextStyle) {
    val fill = remember(color) { Brush.verticalGradient(*widgetGlassStops(color)) }
    val rim = remember(color) { Brush.verticalGradient(*widgetGlassStops(color, rim = true)) }
    val stroke = with(LocalDensity.current) { .65.dp.toPx() }
    // Keep layout/semantics at zero opacity without sending transparent gradients to the GPU.
    Box(Modifier.drawWithContent { if (color.alpha > 0f) drawContent() }) {
        Text(text, style = style.copy(brush = fill))
        // The second pass is decorative; accessibility still sees one text node.
        Text(text, modifier = Modifier.clearAndSetSemantics {},
            style = style.copy(brush = rim, shadow = null, drawStyle = Stroke(stroke)))
    }
}
