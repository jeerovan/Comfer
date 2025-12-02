package com.jeerovan.comfer.utils

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.PI

/**
 * A custom Shape that resembles a smooth, organic pebble.
 *
 * @param complexity The number of anchor points to generate (higher = more "wobbly").
 * @param smoothness Controls the curvature of the path (0.0 = straight lines, 1.0 = very round).
 * @param irregularity How much the shape deviates from a perfect circle (0.0 to 1.0).
 * @param seed A random seed to generate consistent shapes across recompositions.
 */
class PebbleShape(
    private val complexity: Int = 6,
    private val smoothness: Float = 0.2f,
    private val irregularity: Float = 0.2f,
    private val seed: Long = 1L
) : Shape {

    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val path = Path()
        val center = size.center
        val radius = minOf(size.width, size.height) / 2f

        // Generate anchor points
        val points = generatePebblePoints(center, radius, complexity, irregularity, seed)

        if (points.isEmpty()) return Outline.Generic(path)

        // Draw smooth path through points
        path.moveTo(points[0].x, points[0].y)

        for (i in points.indices) {
            val p0 = points[i]
            val p1 = points[(i + 1) % points.size]

            // Calculate control points for smooth cubic bezier
            // Uses previous and next points to determine tangent
            val prev = points[(i - 1 + points.size) % points.size]
            val next = points[(i + 2) % points.size]

            val controlPoint1 = p0 + (p1 - prev) * smoothness
            val controlPoint2 = p1 - (next - p0) * smoothness

            path.cubicTo(
                controlPoint1.x, controlPoint1.y,
                controlPoint2.x, controlPoint2.y,
                p1.x, p1.y
            )
        }

        path.close()
        return Outline.Generic(path)
    }

    private fun generatePebblePoints(
        center: Offset,
        baseRadius: Float,
        count: Int,
        distortion: Float,
        seed: Long
    ): List<Offset> {
        val random = java.util.Random(seed)
        val points = mutableListOf<Offset>()

        for (i in 0 until count) {
            // Evenly distributed angles
            val angle = (2 * PI * i / count)

            // Vary the radius randomly to create the "pebble" look
            // We maintain at least (1 - distortion) of the radius to avoid concave artifacts
            val rVariance = random.nextFloat() * distortion
            val r = baseRadius * (1.0f - distortion / 2f + rVariance)

            val x = center.x + r * cos(angle).toFloat()
            val y = center.y + r * sin(angle).toFloat()
            points.add(Offset(x, y))
        }
        return points
    }
}
