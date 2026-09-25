package com.jeerovan.comfer.spatial

import kotlin.math.floor
import kotlin.math.roundToInt

/** Relative inverse depth: larger values are nearer. No metric-distance claims. */
internal class SpatialDepth(val width: Int, val height: Int, val values: FloatArray) {
    init {
        require(width > 0 && height > 0 && width.toLong() * height == values.size.toLong())
        require(values.all { it.isFinite() && it in 0f..1f })
    }

    fun at(u: Float, v: Float): Float {
        val x = u.coerceIn(0f, 1f) * (width - 1)
        val y = v.coerceIn(0f, 1f) * (height - 1)
        val x0 = floor(x).toInt(); val y0 = floor(y).toInt()
        val x1 = (x0 + 1).coerceAtMost(width - 1); val y1 = (y0 + 1).coerceAtMost(height - 1)
        val top = values[y0 * width + x0] * (1 - (x - x0)) + values[y0 * width + x1] * (x - x0)
        val bottom = values[y1 * width + x0] * (1 - (x - x0)) + values[y1 * width + x1] * (x - x0)
        return top * (1 - (y - y0)) + bottom * (y - y0)
    }

    fun mesh(near: Boolean? = null): DepthMesh {
        val columns = 48; val rows = 96
        val samples = FloatArray((columns + 1) * (rows + 1)) { i ->
            val d = at((i % (columns + 1)).toFloat() / columns, (i / (columns + 1)).toFloat() / rows)
            when (near) { true -> 0.65f + d * 0.35f; false -> d * 0.35f; null -> d }
        }
        // Bound both directional slopes to prevent triangles folding at strong depth edges.
        repeat(2) {
            for (i in samples.indices) {
                if (i % (columns + 1) > 0) samples[i] = samples[i].coerceIn(samples[i-1] - 0.18f, samples[i-1] + 0.18f)
                if (i > columns) samples[i] = samples[i].coerceIn(samples[i-columns-1] - 0.18f, samples[i-columns-1] + 0.18f)
            }
            for (i in samples.lastIndex downTo 0) {
                if (i % (columns + 1) < columns) samples[i] = samples[i].coerceIn(samples[i+1] - 0.18f, samples[i+1] + 0.18f)
                if (i + columns + 1 < samples.size) samples[i] = samples[i].coerceIn(samples[i+columns+1] - 0.18f, samples[i+columns+1] + 0.18f)
            }
        }
        return DepthMesh(columns, rows, samples)
    }

    companion object {
        fun normalize(width: Int, height: Int, raw: FloatArray): SpatialDepth {
            require(raw.size.toLong() == width.toLong() * height && raw.isNotEmpty())
            require(raw.all { it.isFinite() })
            val sorted = raw.sortedArray()
            val low = sorted[(sorted.lastIndex * 0.02f).roundToInt()]
            val high = sorted[(sorted.lastIndex * 0.98f).roundToInt()]
            val range = high - low
            return SpatialDepth(width, height, FloatArray(raw.size) {
                if (range < 1e-6f) 0.5f else ((raw[it] - low) / range).coerceIn(0f, 1f)
            })
        }
    }
}

/** Fills subject pixels from exterior pixels; never samples the removed subject into the fill. */
internal fun fillSubjectBackground(pixels: IntArray, mask: FloatArray, width: Int, height: Int): IntArray {
    require(width > 0 && height > 0 && pixels.size == width * height && mask.size == pixels.size)
    require(mask.all { it.isFinite() && it in 0f..1f })
    val output = pixels.copyOf()
    val visited = BooleanArray(pixels.size)
    val queue = IntArray(pixels.size)
    var head = 0; var tail = 0
    // Exclude even soft/antialiased subject edges to avoid colored halos.
    for (i in pixels.indices) if (mask[i] <= 0.01f) {
        visited[i] = true
        queue[tail++] = i
    }
    require(tail > 0) { "No background pixels" }
    fun visit(from: Int, to: Int) {
        if (!visited[to]) {
            // Blend known neighbors along the advancing boundary. Pure nearest-pixel
            // propagation leaves conspicuous horizontal/vertical streaks on a reveal.
            val x = to % width; val y = to / width
            var red = 0; var green = 0; var blue = 0; var weight = 0
            for (ny in (y - 1).coerceAtLeast(0)..(y + 1).coerceAtMost(height - 1)) {
                for (nx in (x - 1).coerceAtLeast(0)..(x + 1).coerceAtMost(width - 1)) {
                    val neighbor = ny * width + nx
                    if (visited[neighbor]) {
                        val w = if (nx == x || ny == y) 2 else 1
                        val color = output[neighbor]
                        red += ((color ushr 16) and 255) * w
                        green += ((color ushr 8) and 255) * w
                        blue += (color and 255) * w
                        weight += w
                    }
                }
            }
            output[to] = if (weight == 0) output[from] else
                (0xff shl 24) or ((red / weight) shl 16) or ((green / weight) shl 8) or (blue / weight)
            visited[to] = true
            queue[tail++] = to
        }
    }
    while (head < tail) {
        val i = queue[head++]
        if (i % width > 0) visit(i, i - 1)
        if (i % width < width - 1) visit(i, i + 1)
        if (i >= width) visit(i, i - width)
        if (i + width < pixels.size) visit(i, i + width)
    }
    return output
}

/** Conservative cutout check: a mask cutting through similar-colored pixels may omit
 * attached parts (hat brims, hair, limbs). Prefer continuous depth to a ghost silhouette. */
internal fun hasReliableSubjectBoundary(pixels: IntArray, mask: FloatArray, width: Int, height: Int): Boolean {
    require(pixels.size == width * height && mask.size == pixels.size)
    val radius = (minOf(width, height) / 256).coerceIn(2, 6)
    var boundaries = 0
    var unsupported = 0
    fun inspect(x: Int, y: Int, dx: Int, dy: Int) {
        if (mask[(y + dy) * width + x + dx] >= 0.5f) return
        boundaries++
        val inside = pixels[(y - dy * radius) * width + x - dx * radius]
        val outside = pixels[(y + dy * radius) * width + x + dx * radius]
        val contrast = maxOf(kotlin.math.abs(((inside ushr 16) and 255) - ((outside ushr 16) and 255)),
            kotlin.math.abs(((inside ushr 8) and 255) - ((outside ushr 8) and 255)),
            kotlin.math.abs((inside and 255) - (outside and 255)))
        if (contrast < 24) unsupported++
    }
    for (y in radius until height - radius) for (x in radius until width - radius) {
        if (mask[y * width + x] >= 0.5f) {
            inspect(x, y, 1, 0); inspect(x, y, -1, 0)
            inspect(x, y, 0, 1); inspect(x, y, 0, -1)
        }
    }
    return boundaries > 0 && unsupported.toFloat() / boundaries <= 0.15f
}
