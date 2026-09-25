package com.jeerovan.comfer.spatial

import org.junit.Assert.*
import org.junit.Test

class SpatialDepthTest {
    @Test fun normalizationPreservesNearFarAndHandlesFlatImages() {
        val depth = SpatialDepth.normalize(2, 2, floatArrayOf(10f, 20f, 30f, 40f))
        assertEquals(0f, depth.at(0f, 0f), 0.0001f)
        assertEquals(1f, depth.at(1f, 1f), 0.0001f)
        assertEquals(0.5f, depth.at(0.5f, 0.5f), 0.0001f)
        assertTrue(SpatialDepth.normalize(2, 2, FloatArray(4) { 7f }).values.all { it == 0.5f })
        assertThrows(IllegalArgumentException::class.java) { SpatialDepth.normalize(1, 1, floatArrayOf(Float.NaN)) }
    }

    @Test fun meshDoesNotFoldAtExtremeDepthEdgesAndTilt() {
        val depth = SpatialDepth(97, 97, FloatArray(97 * 97) { if (it % 2 == 0) 0f else 1f })
        for (near in listOf(null, false, true)) {
            val mesh = depth.mesh(near)
            for ((w, h) in listOf(400f to 900f, 900f to 400f, 600f to 600f)) {
                for (x in listOf(-1f, 1f)) for (y in listOf(-1f, 1f)) {
                    val v = mesh.project(w, h, 2048, 1536, x, y, x * 0.08f, y * 0.08f)
                    fun area(a: Int, b: Int, c: Int) =
                        (v[2*b]-v[2*a])*(v[2*c+1]-v[2*a+1]) - (v[2*b+1]-v[2*a+1])*(v[2*c]-v[2*a])
                    for (row in 0 until mesh.rows) for (col in 0 until mesh.columns) {
                        val a = row * (mesh.columns + 1) + col
                        val d = a + mesh.columns + 1
                        assertTrue(area(a, a+1, d) > 0)
                        assertTrue(area(a+1, d+1, d) > 0)
                    }
                }
            }
        }
    }

    @Test fun backgroundFillDoesNotCopySubjectColorsOrModifyExterior() {
        val blue = 0xff0000ff.toInt(); val red = 0xffff0000.toInt()
        val pixels = IntArray(25) { blue }.also { it[12] = red; it[13] = red }
        val mask = FloatArray(25).also { it[12] = 1f; it[13] = 0.2f }
        val filled = fillSubjectBackground(pixels, mask, 5, 5)
        assertTrue(filled.all { it == blue })
        assertEquals(red, pixels[12])
        assertThrows(IllegalArgumentException::class.java) {
            fillSubjectBackground(pixels, FloatArray(25) { 1f }, 5, 5)
        }
    }

    @Test fun everyInactiveConditionStopsMotion() {
        assertTrue(wallpaperMotionActive(true, true, true, true))
        assertFalse(wallpaperMotionActive(false, true, true, true))
        assertFalse(wallpaperMotionActive(true, false, true, true))
        assertFalse(wallpaperMotionActive(true, true, false, true))
        assertFalse(wallpaperMotionActive(true, true, true, false))
    }

    @Test fun incompleteCutoutsFallBackInsteadOfLeavingGhostSubjectParts() {
        val pixels = IntArray(40 * 40) { 0xff0000ff.toInt() }
        val mask = FloatArray(pixels.size)
        for (y in 10..29) for (x in 10..29) {
            pixels[y * 40 + x] = 0xffff0000.toInt()
            mask[y * 40 + x] = 1f
        }
        assertTrue(hasReliableSubjectBoundary(pixels, mask, 40, 40))
        // A cutout that drops an attached half cuts directly through red subject pixels.
        for (y in 10..29) for (x in 20..29) mask[y * 40 + x] = 0f
        assertFalse(hasReliableSubjectBoundary(pixels, mask, 40, 40))
    }
}
