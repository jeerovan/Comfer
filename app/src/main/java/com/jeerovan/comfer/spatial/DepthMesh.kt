package com.jeerovan.comfer.spatial

import kotlin.math.max
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.abs
import kotlin.math.exp

/** Same 60-second orbit and 8% travel as AnimatedBackground. */
internal fun wallpaperOrbit(seconds: Double): Pair<Float, Float> {
    val angle = (seconds % 60.0) / 60.0 * 2.0 * Math.PI
    return (cos(angle) * 0.08).toFloat() to (sin(angle) * 0.08).toFloat()
}

/** A regular texture grid with artistic normalized depth (near = 1). */
internal class DepthMesh(val columns: Int, val rows: Int, val depths: FloatArray) {
    init {
        require(columns in 1..128 && rows in 1..128)
        require(depths.size == (columns + 1) * (rows + 1))
        require(depths.all { it.isFinite() && it in 0f..1f })
    }

    val vertices = FloatArray(depths.size * 2)

    /** Overscan exceeds the largest displacement, keeping every edge offscreen. */
    fun project(width: Float, height: Float, imageWidth: Int, imageHeight: Int,
                tiltX: Float, tiltY: Float, orbitX: Float = 0f, orbitY: Float = 0f): FloatArray {
        require(width > 0 && height > 0 && imageWidth > 0 && imageHeight > 0)
        // 12% per edge covers 8% orbit + 2.4% maximum depth displacement.
        val scale = max(width / imageWidth, height / imageHeight) * 1.24f
        val w = imageWidth * scale
        val h = imageHeight * scale
        val x = if (tiltX.isFinite()) tiltX.coerceIn(-1f, 1f) else 0f
        val y = if (tiltY.isFinite()) tiltY.coerceIn(-1f, 1f) else 0f
        val ox = if (orbitX.isFinite()) orbitX.coerceIn(-0.08f, 0.08f) else 0f
        val oy = if (orbitY.isFinite()) orbitY.coerceIn(-0.08f, 0.08f) else 0f
        for (row in 0..rows) for (col in 0..columns) {
            val i = row * (columns + 1) + col
            val displacement = 0.006f + 0.018f * depths[i]
            vertices[i*2] = (width-w)/2 + col*w/columns + width*(x*displacement + ox)
            vertices[i*2+1] = (height-h)/2 + row*h/rows + height*(y*displacement + oy)
        }
        return vertices
    }

    companion object {
        fun parse(text: String): DepthMesh {
            val tokens = text.trim().split(Regex("\\s+"))
            require(tokens.size >= 2)
            return DepthMesh(tokens[0].toInt(), tokens[1].toInt(),
                tokens.drop(2).map(String::toFloat).toFloatArray())
        }
    }
}

/** Successive device-relative tilts, independent of the starting posture or heading. */
internal class SpatialPose {
    private var baseline: FloatArray? = null
    private var rotation = -1
    fun reset() { baseline = null }

    fun update(matrix: FloatArray, screenRotation: Int): Pair<Float, Float> {
        require(matrix.size == 9)
        if (matrix.any { !it.isFinite() }) return 0f to 0f
        if (baseline == null || rotation != screenRotation) {
            baseline = matrix.copyOf()
            rotation = screenRotation
            return 0f to 0f
        }
        val b = baseline!!
        // Accumulate slow tilts but reject tiny sensor noise. Include all matrix
        // axes so turning around screen Z also refreshes the device reference.
        var difference = 0f
        for (i in matrix.indices) difference += (matrix[i] - b[i]) * (matrix[i] - b[i])
        if (difference < 0.000008f) return 0f to 0f // About 0.1 degree.
        val x = (b[0]*matrix[2] + b[3]*matrix[5] + b[6]*matrix[8]) / 0.31f
        val y = (b[1]*matrix[2] + b[4]*matrix[5] + b[7]*matrix[8]) / 0.31f
        // Never retain the resume pose: it can pin one axis at its clamp after
        // changing how the phone is held, hiding subsequent tilts on that axis.
        baseline = matrix.copyOf()
        val (sx, sy) = when (screenRotation) {
            1 -> -y to -x
            2 -> -x to y
            3 -> y to x
            else -> x to -y
        }
        return sx.coerceIn(-1f, 1f) to sy.coerceIn(-1f, 1f)
    }
}

/** A tilt nudges the picture; holding any pose smoothly returns it to center. */
internal class SpatialTiltAnimation {
    private val pose = SpatialPose()
    private var rotation = -1
    private var lastSample = Long.MIN_VALUE
    private var targetX = 0f
    private var targetY = 0f
    private var x = 0f
    private var y = 0f

    val isAtRest get() = targetX == 0f && targetY == 0f && x == 0f && y == 0f

    fun tilt(matrix: FloatArray, screenRotation: Int, now: Long) {
        require(matrix.size == 9)
        if (matrix.any { !it.isFinite() } || now <= lastSample) return
        if (rotation != screenRotation || lastSample != Long.MIN_VALUE && now - lastSample > 500_000_000L) {
            pose.reset()
            targetX = 0f; targetY = 0f; x = 0f; y = 0f
            rotation = screenRotation
        }
        lastSample = now
        val (dx, dy) = pose.update(matrix, screenRotation)
        targetX = (targetX + dx * 1.5f).coerceIn(-1f, 1f)
        targetY = (targetY + dy * 1.5f).coerceIn(-1f, 1f)
    }

    fun advanceBy(seconds: Double): Pair<Float, Float> {
        require(seconds.isFinite() && seconds >= 0.0)
        // Exact integration of a 100 ms follow and 600 ms return avoids frame-
        // rate dependence. Frames continue after sensor events stop, then sleep.
        val returning = exp(-seconds / 0.6).toFloat()
        val following = exp(-seconds / 0.1).toFloat()
        val blend = (0.6f / (0.6f - 0.1f)) * (returning - following)
        x = (x * following + targetX * blend).coerceIn(-1f, 1f)
        y = (y * following + targetY * blend).coerceIn(-1f, 1f)
        targetX *= returning
        targetY *= returning
        if (abs(x) < 0.0005f && abs(y) < 0.0005f &&
            abs(targetX) < 0.0005f && abs(targetY) < 0.0005f) {
            targetX = 0f; targetY = 0f; x = 0f; y = 0f
        }
        return x to y
    }
}
