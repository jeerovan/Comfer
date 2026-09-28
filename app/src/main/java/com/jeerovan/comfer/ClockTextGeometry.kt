package com.jeerovan.comfer

import kotlin.math.*

internal const val CLOCK_MINUTE_SCALE = .7f

internal data class ClockGlyphCell(val text: String, val width: Float, val inkCenter: Float)
internal data class ClockGlyphPosition(
    val cell: ClockGlyphCell, val x: Float, val y: Float, val angle: Float,
)
internal data class ClockBounds(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width get() = right - left
    val height get() = bottom - top
}
internal data class ClockLineGeometry(
    val glyphs: List<ClockGlyphPosition>, val width: Float, val height: Float,
    val originX: Float = 0f, val originY: Float = 0f,
)

internal fun clockGlyphBounds(glyph: ClockGlyphPosition, ascent: Float, descent: Float): ClockBounds {
    val radians = Math.toRadians(glyph.angle.toDouble())
    val c = cos(radians).toFloat(); val s = sin(radians).toFloat()
    val points = listOf(-glyph.cell.width / 2 to ascent, glyph.cell.width / 2 to ascent,
        -glyph.cell.width / 2 to descent, glyph.cell.width / 2 to descent)
        .map { (x, y) -> (glyph.x + x * c - y * s) to (glyph.y + x * s + y * c) }
    return ClockBounds(points.minOf { it.first }, points.minOf { it.second },
        points.maxOf { it.first }, points.maxOf { it.second })
}

/** Pack complete glyph envelopes, including overhangs; fonts cannot collapse the gaps. */
internal fun clockLineGeometry(
    cells: List<ClockGlyphCell>, ascent: Float, descent: Float, gap: Float,
    radius: Float = 0f, angle: Float = 0f,
): ClockLineGeometry {
    if (cells.isEmpty()) return ClockLineGeometry(emptyList(), 0f, descent - ascent)
    val width = cells.sumOf { it.width.toDouble() }.toFloat() + gap * (cells.size - 1)
    // Preserve the existing curvature control, limiting the arc to avoid wrapping around itself.
    val curve = if (radius == 0f) Float.POSITIVE_INFINITY else
        max(1.02f.pow(500f - abs(radius)) - 1f, width / 2f)
    val sign = if (radius < 0) -1f else 1f
    var cursor = 0f
    var shift = 0f
    var previousRight: Float? = null
    var blankWidth = 0f
    val positions = mutableListOf<ClockGlyphPosition>()
    for (cell in cells) {
        val distance = cursor + cell.width / 2 - width / 2
        cursor += cell.width + gap
        if (cell.text.isBlank()) {
            blankWidth += cell.width + gap
            continue
        }
        val theta = if (curve.isInfinite()) 0f else distance / curve
        var glyph = ClockGlyphPosition(cell,
            (if (curve.isInfinite()) distance else curve * sin(theta)) + shift,
            if (curve.isInfinite()) 0f else sign * curve * (1f - cos(theta)),
            Math.toDegrees((sign * theta).toDouble()).toFloat())
        val bounds = clockGlyphBounds(glyph, ascent, descent)
        val extra = previousRight?.let { max(0f, it + gap + blankWidth - bounds.left) } ?: 0f
        shift += extra
        glyph = glyph.copy(x = glyph.x + extra)
        previousRight = bounds.right + extra
        blankWidth = 0f
        positions += glyph
    }
    if (positions.isEmpty()) return ClockLineGeometry(emptyList(), width, descent - ascent)
    val radians = Math.toRadians(angle.toDouble())
    val c = cos(radians).toFloat(); val s = sin(radians).toFloat()
    val rotated = positions.map { it.copy(x = it.x * c - it.y * s,
        y = it.x * s + it.y * c, angle = it.angle + angle) }
    val bounds = rotated.map { clockGlyphBounds(it, ascent, descent) }
    val left = bounds.minOf { it.left }; val top = bounds.minOf { it.top }
    return ClockLineGeometry(rotated.map { it.copy(x = it.x - left, y = it.y - top) },
        bounds.maxOf { it.right } - left, bounds.maxOf { it.bottom } - top, -left, -top)
}
