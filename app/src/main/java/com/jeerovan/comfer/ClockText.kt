package com.jeerovan.comfer

import android.graphics.Paint
import android.graphics.Rect
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.resolveAsTypeface
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.cos
import kotlin.math.sin

internal data class ClockLinePlan(val paint: Paint, val geometry: ClockLineGeometry)

/** Numerals use stable slots sized from actual ink, not a font's kerning or space glyph. */
internal fun measureClockLine(text: String, paint: Paint, density: Float, radius: Float, angle: Float): ClockLinePlan {
    fun ink(value: String) = Rect().also { paint.getTextBounds(value, 0, value.length, it) }
    val characters = text.codePoints().toArray().map { String(Character.toChars(it)) }
    val digits = characters.filter { it.codePointAt(0).let(Character::isDigit) }
        .flatMap { digit ->
            val zero = digit.codePointAt(0) - Character.digit(digit.codePointAt(0), 10)
            (0..9).map { String(Character.toChars(zero + it)) }
        }.distinct()
    val samples = (characters.filterNot { it.isBlank() } + digits).distinct()
    val bounds = samples.associateWith(::ink)
    val digitWidth = digits.maxOfOrNull { max(paint.measureText(it), bounds.getValue(it).width().toFloat()) } ?: 0f
    val cells = characters.map { char ->
        if (char.isBlank()) ClockGlyphCell(char, paint.textSize * .22f, 0f)
        else {
            val b = bounds.getValue(char)
            ClockGlyphCell(char, if (Character.isDigit(char.codePointAt(0))) digitWidth
                else max(paint.measureText(char), b.width().toFloat()), b.exactCenterX())
        }
    }
    // Use the whole numeral set for stable ticks, but exclude unrelated font line padding.
    val ascent = bounds.values.minOfOrNull { it.top.toFloat() } ?: paint.fontMetrics.ascent
    val descent = bounds.values.maxOfOrNull { it.bottom.toFloat() } ?: paint.fontMetrics.descent
    // Reserve room for the glass rim as well as at least one dp of visible separation.
    val gap = max(density, paint.textSize * .04f) + density
    return ClockLinePlan(paint, clockLineGeometry(cells, ascent, descent, gap, radius, angle))
}

@Composable
internal fun ClockText(
    time: String, layoutId: Int, fontSize: TextUnit, color: Color,
    fontWeight: FontWeight = FontWeight.Normal, fontFamily: FontFamily = FontFamily.Default,
    angle: Float = 0f, radius: Float = 0f, shadowColor: Color = Color.Transparent,
    glass: Boolean = false,
    semanticText: String = time,
) {
    val density = LocalDensity.current
    val resolver = LocalFontFamilyResolver.current
    val typeface = remember(resolver, fontFamily, fontWeight) {
        resolver.resolveAsTypeface(fontFamily, fontWeight, FontStyle.Normal)
    }.value
    val fontPx = with(density) { fontSize.toPx() }.coerceAtLeast(1f)
    val lines = remember(time, layoutId, fontPx, typeface, fontWeight, density, angle, radius) {
        val parts = time.split(':', limit = 2)
        val texts = when (layoutId) {
            2 -> listOf(time.replace(":", "") to 1f)
            3 -> listOf(parts.first() to 1f, parts.getOrElse(1) { "" } to CLOCK_MINUTE_SCALE)
            else -> listOf(time to 1f)
        }
        texts.map { (text, scale) ->
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.typeface = typeface
                textSize = fontPx * scale
                isFakeBoldText = fontWeight.weight >= FontWeight.Bold.weight && !typeface.isBold
            }
            measureClockLine(text, paint, density.density, radius, angle)
        }
    }
    val padding = with(density) { 3.dp.toPx() }
    val lineGap = max(with(density) { 2.dp.toPx() }, fontPx * .08f)
    val width = (lines.maxOf { it.geometry.width } + padding * 2).coerceAtLeast(1f)
    val height = lines.sumOf { it.geometry.height.toDouble() }.toFloat() +
        lineGap * (lines.size - 1) + padding * 2
    val shadow = widgetShadowColor(color, shadowColor).let { it.copy(alpha = it.alpha * color.alpha) }
    val straightGlass = glass && angle == 0f && radius == 0f
    val textMeasurer = rememberTextMeasurer()
    // Keep the original Compose paragraph painter, including its font padding, gradient
    // coordinates and shadows. Only glyph placement comes from the spacing geometry.
    val paragraphs = remember(lines, straightGlass, textMeasurer, fontFamily, fontWeight, typeface) {
        if (!straightGlass) emptyList() else lines.map { line ->
            line.geometry.glyphs.associate { glyph ->
                glyph.cell.text to textMeasurer.measure(
                    glyph.cell.text,
                    style = TextStyle(
                        fontSize = with(density) { line.paint.textSize.toSp() },
                        fontFamily = fontFamily, fontWeight = fontWeight, fontStyle = FontStyle.Normal,
                    ),
                    softWrap = false, overflow = TextOverflow.Visible,
                )
            }
        }
    }
    val fill = remember(color) { Brush.verticalGradient(*widgetGlassStops(color)) }
    val rim = remember(color) { Brush.verticalGradient(*widgetGlassStops(color, rim = true)) }
    Canvas(Modifier
        // Keep a single readable time for accessibility in every visual variation.
        .semantics { this.text = AnnotatedString(semanticText) }
        .layout { measurable, constraints ->
            val fit = minOf(1f, constraints.maxWidth / width, constraints.maxHeight / height)
            val w = constraints.constrainWidth(ceil(width * fit).toInt())
            val h = constraints.constrainHeight(ceil(height * fit).toInt())
            val placeable = measurable.measure(Constraints.fixed(w, h))
            layout(w, h) { placeable.place(0, 0) }
        }) {
        if (color.alpha == 0f) return@Canvas
        val fit = minOf(1f, size.width / width, size.height / height)
        val canvas = drawContext.canvas.nativeCanvas
        canvas.save()
        canvas.translate((size.width - width * fit) / 2, (size.height - height * fit) / 2)
        canvas.scale(fit, fit)
        var top = padding
        for ((lineIndex, line) in lines.withIndex()) {
            val (paint, geometry) = line
            val left = (width - geometry.width) / 2
            if (straightGlass) {
                val textShadow = if (shadow.alpha > 0f)
                    Shadow(shadow, Offset(0f, .75.dp.toPx()), 1.5.dp.toPx()) else Shadow.None
                for (outline in listOf(false, true)) for (glyph in geometry.glyphs) {
                    val paragraph = paragraphs[lineIndex].getValue(glyph.cell.text)
                    // Compose includes paragraph leading/tracking; align its baseline and
                    // center the glyph in the existing stable numeral slot.
                    val leading = (paragraph.size.width - paint.measureText(glyph.cell.text)) / 2f
                    drawText(paragraph, brush = if (outline) rim else fill,
                        topLeft = Offset(left + glyph.x - glyph.cell.inkCenter - leading,
                            top + glyph.y - paragraph.firstBaseline),
                        shadow = if (outline) Shadow.None else textShadow,
                        drawStyle = if (outline) Stroke(.65.dp.toPx()) else androidx.compose.ui.graphics.drawscope.Fill)
                }
                top += geometry.height + lineGap
                continue
            }
            fun shader(rim: Boolean): android.graphics.Shader {
                val stops = widgetGlassStops(color, rim)
                // Match EffectTextBlock: lighting spans font ascent/descent around the
                // original baseline and rotates with the clock, not its tight ink envelope.
                val radians = Math.toRadians(angle.toDouble())
                val sine = sin(radians).toFloat()
                val cosine = cos(radians).toFloat()
                val metrics = paint.fontMetrics
                return android.graphics.LinearGradient(
                    geometry.originX - metrics.ascent * sine, geometry.originY + metrics.ascent * cosine,
                    geometry.originX - metrics.descent * sine, geometry.originY + metrics.descent * cosine,
                    stops.map { it.second.toArgb() }.toIntArray(), stops.map { it.first }.toFloatArray(),
                    android.graphics.Shader.TileMode.CLAMP)
            }
            paint.style = Paint.Style.FILL
            paint.color = color.toArgb()
            if (shadow.alpha > 0f) paint.setShadowLayer(1.5.dp.toPx(), 0f, .75.dp.toPx(), shadow.toArgb())
            else paint.clearShadowLayer()
            // Keep one font-based shader across the line while placing each glyph separately.
            canvas.save()
            canvas.translate(left, top)
            fun drawGlyphs() {
                for (glyph in geometry.glyphs) {
                    canvas.save()
                    canvas.translate(glyph.x, glyph.y)
                    canvas.rotate(glyph.angle)
                    // Compensate the shader for the glyph transform so lighting stays on the line.
                    paint.shader?.setLocalMatrix(android.graphics.Matrix().apply {
                        setRotate(-glyph.angle)
                        preTranslate(-glyph.x, -glyph.y)
                    })
                    canvas.drawText(glyph.cell.text, -glyph.cell.inkCenter, 0f, paint)
                    canvas.restore()
                }
            }
            paint.shader = if (glass) shader(false) else null
            if (glass) paint.alpha = 255
            drawGlyphs()
            if (glass) {
                paint.clearShadowLayer()
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = .65.dp.toPx()
                paint.shader = shader(true)
                drawGlyphs()
            }
            paint.style = Paint.Style.FILL
            paint.shader = null
            canvas.restore()
            top += geometry.height + lineGap
        }
        canvas.restore()
    }
}
