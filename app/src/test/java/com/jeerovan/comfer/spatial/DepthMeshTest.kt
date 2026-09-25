package com.jeerovan.comfer.spatial

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import kotlin.math.cos
import kotlin.math.sin

class DepthMeshTest {
    @Test fun wholePictureOrbitKeepsOriginalPeriodAndTravel() {
        for ((seconds, expected) in listOf(0.0 to (0.08f to 0f),
            15.0 to (0f to 0.08f), 30.0 to (-0.08f to 0f), 45.0 to (0f to -0.08f))) {
            val actual = wallpaperOrbit(seconds)
            assertEquals(expected.first, actual.first, 0.00001f)
            assertEquals(expected.second, actual.second, 0.00001f)
            assertEquals(actual, wallpaperOrbit(seconds + 60.0))
        }
    }

    @Test fun orbitMovesEveryVertexEquallyWhilePreservingDepthParallax() {
        val mesh = DepthMesh(1, 1, floatArrayOf(0f, 1f, 0f, 1f))
        val tiltOnly = mesh.project(1000f, 2000f, 1000, 2000, 1f, -1f).copyOf()
        val combined = mesh.project(1000f, 2000f, 1000, 2000, 1f, -1f, 0.08f, -0.08f)
        for (i in 0..3) {
            assertEquals(80f, combined[2*i]-tiltOnly[2*i], 0.001f)
            assertEquals(-160f, combined[2*i+1]-tiltOnly[2*i+1], 0.001f)
        }
    }

    @Test fun foregroundMovesMoreThanBackground() {
        val mesh = DepthMesh(1, 1, floatArrayOf(0f, 1f, 0f, 1f))
        val rest = mesh.project(1000f, 2000f, 1000, 2000, 0f, 0f).copyOf()
        val tilted = mesh.project(1000f, 2000f, 1000, 2000, 1f, 0f)
        assertEquals(6f, tilted[0]-rest[0], 0.001f)
        assertEquals(24f, tilted[2]-rest[2], 0.001f)
        assertEquals(rest[1], tilted[1], 0f)
    }

    @Test fun overscanCoversViewportAtAllTiltLimitsAndAspectRatios() {
        for ((width, height) in listOf(400f to 900f, 900f to 400f, 600f to 600f)) {
            val mesh = DepthMesh(1, 1, FloatArray(4) { 1f })
            for (x in listOf(-1f, 1f)) for (y in listOf(-1f, 1f)) {
                // Both effects push toward the same edge: stricter than the circular orbit.
                val v = mesh.project(width, height, 760, 1644, x, y, x*0.08f, y*0.08f)
                assertTrue(v[0] < 0 && v[1] < 0)
                assertTrue(v[2] > width && v[3] < 0)
                assertTrue(v[4] < 0 && v[5] > height)
                assertTrue(v[6] > width && v[7] > height)
            }
        }
    }

    @Test fun clampsExcessiveTiltAndIgnoresNonFiniteInput() {
        val mesh = DepthMesh(1, 1, FloatArray(4) { 1f })
        val expected = mesh.project(400f, 900f, 760, 1644, 1f, -1f).copyOf()
        assertArrayEquals(expected, mesh.project(400f, 900f, 760, 1644, 100f, -100f), 0f)
        val neutral = mesh.project(400f, 900f, 760, 1644, 0f, 0f).copyOf()
        assertArrayEquals(neutral, mesh.project(400f, 900f, 760, 1644, Float.NaN, Float.POSITIVE_INFINITY), 0f)
    }

    @Test fun rejectsMalformedDepthMaps() {
        for (text in listOf("", "0 1 0 0", "1 1 0", "1 1 0 0 0 NaN", "1 1 0 0 0 2")) {
            assertThrows(IllegalArgumentException::class.java) { DepthMesh.parse(text) }
        }
    }

    @Test fun bundledMeshesAreValidAndHaveExpectedSceneDepth() {
        for (id in 1..2) {
            val mesh = DepthMesh.parse(File("src/main/assets/spatial/wallpaper-$id.depth").readText())
            assertEquals(48, mesh.columns)
            assertEquals(96, mesh.rows)
            fun at(x: Float, y: Float) = mesh.depths[(y*mesh.rows).toInt()*(mesh.columns+1)+(x*mesh.columns).toInt()]
            if (id == 1) assertTrue(at(0.1f, 0.75f) > at(0.5f, 0.45f))
            else assertTrue(at(0.5f, 0.65f) > at(0.9f, 0.3f)+0.5f)
            // Positive triangle areas prevent foldovers in either tilt direction.
            for (x in listOf(-1f, 1f)) for (y in listOf(-1f, 1f)) {
                val v = mesh.project(760f, 1644f, 760, 1644, x, y)
                fun area(a: Int, b: Int, c: Int) =
                    (v[b*2]-v[a*2])*(v[c*2+1]-v[a*2+1]) -
                        (v[b*2+1]-v[a*2+1])*(v[c*2]-v[a*2])
                for (row in 0 until mesh.rows) for (col in 0 until mesh.columns) {
                    val a = row*(mesh.columns+1)+col
                    val d = a+mesh.columns+1
                    assertTrue("Fold in scene $id at $row,$col", area(a,a+1,d)>0 && area(a+1,d+1,d)>0)
                }
            }
        }
    }

    private fun rotationY(angle: Float) = floatArrayOf(cos(angle), 0f, sin(angle),
        0f, 1f, 0f, -sin(angle), 0f, cos(angle))

    @Test fun calibratesAtAnyInitialPoseAndResetsOnResumeOrDisplayRotation() {
        val pose = SpatialPose()
        assertEquals(0f, pose.update(rotationY(0.6f), 0).first, 0.0001f)
        assertTrue(pose.update(rotationY(0.7f), 0).first > 0)
        pose.reset()
        assertEquals(0f, pose.update(rotationY(0.7f), 0).first, 0.0001f)
        assertEquals(0f, pose.update(rotationY(0.8f), 1).first, 0.0001f)
        assertTrue(pose.update(rotationY(0.9f), 1).second < 0)
    }

    @Test fun relativePoseDoesNotDependOnAbsoluteHeading() {
        for (initial in listOf(-2f, 0f, 2f)) {
            val pose = SpatialPose()
            pose.update(rotationY(initial), 0)
            val result = pose.update(rotationY(initial+0.1f), 0)
            assertEquals(sin(0.1f)/0.31f, result.first, 0.00001f)
            assertEquals(0f, result.second, 0f)
        }
    }
}
