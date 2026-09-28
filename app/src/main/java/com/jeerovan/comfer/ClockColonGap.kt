package com.jeerovan.comfer

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.RectF
import kotlin.math.cos
import kotlin.math.sin

internal data class ColonGap(val top: Float, val bottom: Float) {
    val size get() = bottom - top
    val middle get() = (top + bottom) / 2f
}

/** Inspect vector contours once per font, never sample a bitmap or poll the screen. */
internal fun measureColonGap(paint: Paint): ColonGap? {
    val path = Path().also { paint.getTextPath(":", 0, 1, 0f, 0f, it) }
    val measure = PathMeasure(path, false)
    val bounds = mutableListOf<RectF>()
    do {
        val contour = Path()
        if (measure.getSegment(0f, measure.length, contour, true)) {
            bounds += RectF().also { contour.computeBounds(it, true) }
        }
    } while (measure.nextContour())
    val bands = mutableListOf<RectF>()
    for (bound in bounds.sortedBy { it.top }) {
        val previous = bands.lastOrNull()
        if (previous != null && bound.top <= previous.bottom) previous.union(bound)
        else bands += bound
    }
    return bands.zipWithNext { a, b -> ColonGap(a.bottom, b.top) }
        .filter { it.size > 0f }.maxByOrNull { it.size }
}

/** The surrounding line stretches vertically; draw each dot closer to retain its empty gap. */
internal inline fun drawWithFixedColonGap(
    canvas: Canvas, gap: ColonGap?, scale: Float, angle: Float, rimWidth: Float,
    draw: () -> Unit,
) {
    if (gap == null || scale == 1f) { draw(); return }
    val radians = Math.toRadians(angle.toDouble())
    val shift = (scale - 1f) / scale * (gap.size - rimWidth).coerceAtLeast(0f) / 2f * cos(radians).toFloat()
    for (upper in listOf(true, false)) {
        canvas.save()
        val delta = if (upper) shift else -shift
        // Translate in the screen's vertical direction, including for rotated/curved clocks.
        canvas.translate(delta * sin(radians).toFloat(), delta * cos(radians).toFloat())
        canvas.clipRect(-1_000_000f, if (upper) -1_000_000f else gap.middle,
            1_000_000f, if (upper) gap.middle else 1_000_000f)
        draw()
        canvas.restore()
    }
}
