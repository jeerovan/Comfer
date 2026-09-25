package com.jeerovan.comfer.spatial

import org.junit.Assert.*
import org.junit.Test

class SpatialLayersTest {
    private fun plan(depths: List<Float>, connected: Boolean = false): SubjectLayerPlan {
        val width = 100; val height = 20
        val mask = FloatArray(width * height)
        val values = FloatArray(mask.size)
        depths.forEachIndexed { index, depth ->
            for (y in 5..14) for (x in index * 20 + 2..index * 20 + 11) {
                mask[y * width + x] = 1f
                values[y * width + x] = depth
            }
        }
        if (connected) for (x in 2..(depths.lastIndex * 20 + 11)) mask[10 * width + x] = 1f
        return planSubjectLayers(mask, width, height, SpatialDepth(width, height, values))
    }

    @Test fun separateSubjectsAtDifferentDepthsProduceFourOrderedLayers() {
        val result = plan(listOf(0.9f, 0.2f, 0.55f))
        assertEquals(3, result.depths.size)
        assertTrue(result.depths.zipWithNext().all { (a, b) -> a < b })
        assertEquals(3, result.labels[10 * 100 + 5])
        assertEquals(1, result.labels[10 * 100 + 25])
        assertEquals(2, result.labels[10 * 100 + 45])
        assertEquals(300, result.labels.count { it != 0 })
    }

    @Test fun similarDepthOrConnectedSubjectsDoNotEarnExtraLayers() {
        assertEquals(1, plan(listOf(0.45f, 0.5f, 0.55f)).depths.size)
        assertEquals(1, plan(listOf(0.1f, 0.5f, 0.9f), connected = true).depths.size)
        assertEquals(2, plan(listOf(0.1f, 0.9f)).depths.size)
    }

    @Test fun excessSubjectsMergeWithoutDroppingPixels() {
        val result = plan(listOf(0.1f, 0.35f, 0.65f, 0.9f))
        assertEquals(3, result.depths.size)
        assertEquals(400, result.labels.count { it != 0 })
    }

    @Test fun tinyFragmentsStayVisibleWithoutAddingLayers() {
        val mask = FloatArray(10000)
        for (y in 20..39) for (x in 20..39) mask[y * 100 + x] = 1f
        mask[9000] = 0.2f
        val result = planSubjectLayers(mask, 100, 100, SpatialDepth(1, 1, floatArrayOf(0.5f)))
        assertEquals(1, result.depths.size)
        assertEquals(1, result.labels[9000])
        assertEquals(401, result.labels.count { it != 0 })
    }

    @Test fun emptyAndInvalidMasksHaveDefinedBehavior() {
        val depth = SpatialDepth(1, 1, floatArrayOf(0.5f))
        assertTrue(planSubjectLayers(FloatArray(16), 4, 4, depth).depths.isEmpty())
        assertThrows(IllegalArgumentException::class.java) { planSubjectLayers(floatArrayOf(Float.NaN), 1, 1, depth) }
        assertThrows(IllegalArgumentException::class.java) { planSubjectLayers(FloatArray(3), 2, 2, depth) }
    }

    @Test fun cloudManifestValidatesVersionLayersAndUrls() {
        val asset = """{"imageUrl":"https://example.com/layer.png","depthUrl":"https://example.com/layer.depth"}"""
        val valid = """{"version":1,"layers":[${List(4) { asset }.joinToString(",")}],"futureField":true}"""
        assertEquals(4, SpatialSceneManifest.parse(valid).layers.size)
        for (bad in listOf(valid.replace("\"version\":1", "\"version\":2"),
            valid.replace("https://", "http://"), """{"version":1,"layers":[]}""",
            """{"version":1,"layers":[${List(5) { asset }.joinToString(",") }]}""")) {
            assertThrows(IllegalArgumentException::class.java) { SpatialSceneManifest.parse(bad) }
        }
        assertEquals(listOf("background", "foreground"), spatialLayerNames(2))
    }
}
