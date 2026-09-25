package com.jeerovan.comfer.spatial

import kotlin.math.abs

internal const val MAX_SPATIAL_LAYERS = 4

/** Full-canvas layer names, ordered back to front; preserves older one/two-layer caches. */
internal fun spatialLayerNames(count: Int): List<String> {
    require(count in 1..MAX_SPATIAL_LAYERS)
    return listOf("background", "foreground", "layer2", "layer3").take(count)
}

internal data class SubjectLayerPlan(val labels: IntArray, val depths: List<Float>)

/** Separate only disconnected cutouts with meaningful size and depth separation.
 * Never slice a connected subject into arbitrary depth bands. Labels are 1-based,
 * ordered far to near; zero denotes background. Tiny/similar-depth parts stay grouped. */
internal fun planSubjectLayers(mask: FloatArray, width: Int, height: Int, depth: SpatialDepth): SubjectLayerPlan {
    require(width > 0 && height > 0 && mask.size.toLong() == width.toLong() * height)
    require(mask.all { it.isFinite() && it in 0f..1f })
    val labels = IntArray(mask.size)
    val queue = IntArray(mask.size)
    data class Component(val id: Int, val area: Int, val mean: Float)
    val components = mutableListOf<Component>()
    fun single(): SubjectLayerPlan = SubjectLayerPlan(IntArray(mask.size) { if (mask[it] > 0.01f) 1 else 0 }, listOf(0.5f))
    for (start in mask.indices) {
        if (mask[start] <= 0.01f || labels[start] != 0) continue
        // Noisy masks should not create unbounded component bookkeeping or extra draw calls.
        if (components.size >= 512) return single()
        val id = components.size + 1
        var head = 0; var tail = 1; var weightedDepth = 0.0; var weight = 0.0
        queue[0] = start; labels[start] = id
        fun visit(index: Int) {
            if (labels[index] == 0 && mask[index] > 0.01f) {
                labels[index] = id
                queue[tail++] = index
            }
        }
        while (head < tail) {
            val i = queue[head++]
            val x = i % width; val y = i / width
            weightedDepth += depth.at(x.toFloat() / (width - 1).coerceAtLeast(1),
                y.toFloat() / (height - 1).coerceAtLeast(1)) * mask[i]
            weight += mask[i]
            // Eight-connected preserves diagonal strands/attached details as one subject.
            for (ny in (y - 1).coerceAtLeast(0)..(y + 1).coerceAtMost(height - 1))
                for (nx in (x - 1).coerceAtLeast(0)..(x + 1).coerceAtMost(width - 1)) visit(ny * width + nx)
        }
        components += Component(id, tail, (weightedDepth / weight).toFloat())
    }
    if (components.isEmpty()) return SubjectLayerPlan(labels, emptyList())
    data class Group(val ids: MutableList<Int>, var area: Int, var mean: Float)
    val groups = components.map { Group(mutableListOf(it.id), it.area, it.mean) }.sortedBy { it.mean }.toMutableList()
    fun merge(index: Int) {
        val left = groups[index]; val right = groups.removeAt(index + 1)
        left.mean = (left.mean * left.area + right.mean * right.area) / (left.area + right.area)
        left.area += right.area
        left.ids += right.ids
    }
    // Small disconnected fragments remain visible, but do not earn an additional layer.
    while (groups.size > 1) {
        val tiny = groups.indexOfFirst { it.area < mask.size * 0.01f }
        if (tiny < 0) break
        val neighbor = when (tiny) {
            0 -> 0
            groups.lastIndex -> tiny - 1
            else -> if (abs(groups[tiny].mean - groups[tiny - 1].mean) <= abs(groups[tiny + 1].mean - groups[tiny].mean)) tiny - 1 else tiny
        }
        merge(neighbor)
    }
    while (groups.size > 1) {
        val closest = (0 until groups.lastIndex).minBy { groups[it + 1].mean - groups[it].mean }
        if (groups.size <= MAX_SPATIAL_LAYERS - 1 && groups[closest + 1].mean - groups[closest].mean >= 0.12f) break
        merge(closest)
    }
    val mapping = IntArray(components.size + 1)
    groups.forEachIndexed { index, group -> group.ids.forEach { mapping[it] = index + 1 } }
    for (i in labels.indices) labels[i] = mapping[labels[i]]
    return SubjectLayerPlan(labels, groups.map { it.mean })
}
