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

    @Test fun fixtureMeshesAreValidAndHaveExpectedSceneDepth() {
        for (id in 1..2) {
            val mesh = DepthMesh.parse(File("src/androidTest/assets/spatial/wallpaper-$id.depth").readText())
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

    private fun rotationX(angle: Float) = floatArrayOf(1f, 0f, 0f,
        0f, cos(angle), -sin(angle), 0f, sin(angle), cos(angle))

    private fun multiply(a: FloatArray, b: FloatArray) = FloatArray(9) { i ->
        (0..2).sumOf { k -> (a[i / 3 * 3 + k] * b[k * 3 + i % 3]).toDouble() }.toFloat()
    }

    /** A deterministic frame clock, separate from the sensor's sample clock. */
    private class TiltTimeline {
        private val motion = SpatialTiltAnimation()
        private var frameTime = 0L
        val isAtRest get() = motion.isAtRest
        fun tilt(matrix: FloatArray, rotation: Int, sampleTime: Long) = motion.tilt(matrix, rotation, sampleTime)
        fun advance(now: Long): Pair<Float, Float> {
            val seconds = (now - frameTime).coerceAtLeast(0L) / 1_000_000_000.0
            frameTime = maxOf(frameTime, now)
            return motion.advanceBy(seconds)
        }
    }

    @Test fun bothTiltAxesRespondUprightAndFacingSky() {
        for (posture in listOf(0f, (Math.PI / 2).toFloat())) {
            val initial = rotationX(posture)
            for (sign in listOf(-1f, 1f)) {
                val horizontal = SpatialPose().apply { update(initial, 0) }
                    .update(multiply(initial, rotationY(sign * 0.1f)), 0)
                assertEquals(sign * sin(0.1f) / 0.31f, horizontal.first, 0.00001f)
                assertEquals(0f, horizontal.second, 0.00001f)
                val vertical = SpatialPose().apply { update(initial, 0) }
                    .update(multiply(initial, rotationX(sign * 0.1f)), 0)
                assertEquals(0f, vertical.first, 0.00001f)
                assertEquals(sign * sin(0.1f) / 0.31f, vertical.second, 0.00001f)
            }
        }
    }

    @Test fun holdingANewPoseStopsProducingTiltMovement() {
        val pose = SpatialPose()
        pose.update(rotationY(0f), 0)
        assertTrue(pose.update(rotationY(0.1f), 0).first > 0f)
        assertEquals(0f to 0f, pose.update(rotationY(0.1f), 0))
    }

    @Test fun smallTiltsStayResponsiveAfterLargePostureChanges() {
        val pose = SpatialPose()
        pose.update(rotationY(0f), 0)
        pose.update(rotationY((Math.PI / 2).toFloat()), 0)
        val next = pose.update(rotationY((Math.PI / 2).toFloat() + 0.1f), 0)
        assertEquals(sin(0.1f) / 0.31f, next.first, 0.00001f)
    }

    @Test fun turningAroundScreenNormalKeepsSubsequentTiltsDeviceRelative() {
        val pose = SpatialPose()
        pose.update(rotationY(0f), 0)
        val turned = floatArrayOf(0f, -1f, 0f, 1f, 0f, 0f, 0f, 0f, 1f)
        val rolled = pose.update(turned, 0)
        assertEquals(0f, rolled.first, 0f)
        assertEquals(0f, rolled.second, 0f)
        val tilted = pose.update(multiply(turned, rotationY(0.1f)), 0)
        assertEquals(sin(0.1f) / 0.31f, tilted.first, 0.00001f)
        assertEquals(0f, tilted.second, 0.00001f)
    }

    @Test fun tiltAnimationMovesBothAxesInEitherPostureAndEveryScreenRotation() {
        for (posture in listOf(0f, (Math.PI / 2).toFloat())) {
            for (rotation in 0..3) for (sign in listOf(-1f, 1f)) {
                for (aroundX in listOf(false, true)) {
                    val initial = rotationX(posture)
                    val motion = TiltTimeline()
                    motion.tilt(initial, rotation, 0L)
                    motion.tilt(multiply(initial, if (aroundX) rotationX(sign * 0.1f)
                        else rotationY(sign * 0.1f)), rotation, 20_000_000L)
                    val result = motion.advance(120_000_000L)
                    val horizontal = aroundX == (rotation == 1 || rotation == 3)
                    assertTrue("Missing motion: posture=$posture rotation=$rotation X=$aroundX",
                        kotlin.math.abs(if (horizontal) result.first else result.second) > 0.1f)
                    assertEquals(0f, if (horizontal) result.second else result.first, 0.00001f)
                }
            }
        }
    }

    @Test fun wallpaperAnimatesBackToExactCenterWithoutFurtherSensorEvents() {
        val motion = TiltTimeline()
        motion.tilt(rotationY(0f), 0, 0L)
        motion.tilt(rotationY(0.1f), 0, 20_000_000L)
        val peak = motion.advance(140_000_000L).first
        val returning = motion.advance(400_000_000L).first
        val nearlyCentered = motion.advance(900_000_000L).first
        assertTrue(peak > returning && returning > nearlyCentered && nearlyCentered > 0f)
        assertEquals(0f to 0f, motion.advance(5_000_000_000L))
        assertTrue(motion.isAtRest)
    }

    @Test fun holdingTiltedPoseDoesNotRestartOrPinTheAnimation() {
        val motion = TiltTimeline()
        motion.tilt(rotationY(0f), 0, 0L)
        val held = rotationY(0.15f)
        motion.tilt(held, 0, 20_000_000L)
        assertTrue(motion.advance(120_000_000L).first > 0f)
        for (ms in 140L..5_000L step 20L) {
            motion.tilt(held, 0, ms * 1_000_000L)
            motion.advance(ms * 1_000_000L)
        }
        assertTrue(motion.isAtRest)
    }

    @Test fun returnToCenterHasAGentlePaceWithoutLeavingResidualMotion() {
        val motion = TiltTimeline()
        motion.tilt(rotationY(0f), 0, 0L)
        motion.tilt(rotationY(0.1f), 0, 20_000_000L)
        val peak = motion.advance(180_000_000L).first
        val afterOneSecond = motion.advance(1_000_000_000L).first
        assertTrue("The return should remain visible after one second", afterOneSecond > 0.08f)
        assertTrue(afterOneSecond < peak)
        val nearCenter = motion.advance(3_000_000_000L).first
        assertTrue(nearCenter in 0f..0.01f)
        assertEquals(0f to 0f, motion.advance(5_000_000_000L))
        assertTrue(motion.isAtRest)
    }

    @Test fun returnAnimationIsIndependentOfFrameRate() {
        fun sampled(step: Long): Pair<Float, Float> {
            val motion = TiltTimeline()
            motion.tilt(rotationY(0f), 0, 0L)
            motion.tilt(rotationY(0.1f), 0, 20_000_000L)
            for (ms in (20L + step)..500L step step) motion.advance(ms * 1_000_000L)
            return motion.advance(500_000_000L)
        }
        assertEquals(sampled(8L).first, sampled(33L).first, 0.00001f)
    }

    @Test fun slowTiltsAccumulateButSmallStationaryNoiseStaysStill() {
        val pose = SpatialPose()
        pose.update(rotationY(0f), 0)
        for (angle in listOf(0.0003f, -0.0003f, 0.0001f, -0.0001f)) {
            assertEquals(0f to 0f, pose.update(rotationY(angle), 0))
        }
        var movements = 0
        for (step in 1..30) if (pose.update(rotationY(step * 0.0003f), 0).first > 0f) movements++
        assertTrue("Slow tilts must not be lost to a per-sample dead zone", movements > 0)
    }

    @Test fun displayRotationOrInterruptedSensorStreamRebasesAtCenter() {
        for (newRotation in listOf(0, 1, 2, 3)) {
            val motion = TiltTimeline()
            motion.tilt(rotationY(0f), 0, 0L)
            motion.tilt(rotationY(0.1f), 0, 20_000_000L)
            assertTrue(motion.advance(120_000_000L).first > 0f)
            val resumedAt = if (newRotation == 0) 1_000_000_000L else 140_000_000L
            motion.tilt(rotationX(1f), newRotation, resumedAt)
            assertEquals(0f to 0f, motion.advance(resumedAt))
        }
    }

    @Test fun invalidAndOutOfOrderSamplesCannotPoisonMotion() {
        val motion = TiltTimeline()
        motion.tilt(rotationY(0f), 0, 0L)
        motion.tilt(FloatArray(9) { Float.NaN }, 0, 10_000_000L)
        motion.tilt(rotationY(0.1f), 0, 20_000_000L)
        val before = motion.advance(120_000_000L)
        motion.tilt(rotationY(-1f), 0, 15_000_000L)
        motion.tilt(rotationY(-1f), 0, 20_000_000L)
        assertEquals(before, motion.advance(120_000_000L))
        assertEquals(0f to 0f, motion.advance(5_000_000_000L))
    }

    @Test fun animatedTiltKeepsDepthAndReturnsEveryVertexToItsOriginalPosition() {
        val motion = TiltTimeline()
        val mesh = DepthMesh(1, 1, floatArrayOf(0f, 1f, 0f, 1f))
        val centered = mesh.project(400f, 900f, 760, 1644, 0f, 0f).copyOf()
        motion.tilt(rotationX(0f), 0, 0L)
        motion.tilt(multiply(rotationX(0.1f), rotationY(0.1f)), 0, 20_000_000L)
        val (x, y) = motion.advance(120_000_000L)
        val moved = mesh.project(400f, 900f, 760, 1644, x, y).copyOf()
        assertTrue(moved[0] > centered[0] && moved[1] > centered[1])
        assertTrue(moved[2] - centered[2] > moved[0] - centered[0])
        val rest = motion.advance(5_000_000_000L)
        assertArrayEquals(centered, mesh.project(400f, 900f, 760, 1644, rest.first, rest.second), 0f)
    }

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
