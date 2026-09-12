package com.jeerovan.comfer

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

class HomeGestureClassifierTest {
    private fun stroke(a: Offset, b: Offset, c: Offset? = null, d: Offset? = null, e: Offset? = null): List<Offset> =
        listOf(a) + listOfNotNull(a, b, c, d, e).zipWithNext().flatMap { (from, to) ->
            (1..20).map { from + (to - from) * (it / 20f) }
        }
    private fun check(expected: HomeGesture, points: List<Offset>) {
        for (density in listOf(1f, 1.5f, 2.625f, 3.5f)) {
            val translated = points.map { (it + Offset(130f, 270f)) * density }
            assertEquals("density=$density, path=$points", expected, classifyHomeGesture(translated, density))
        }
    }
    @Test fun downwardReturnOpensInboxWithSmallDriftAndRoundedTurn() {
        check(HomeGesture.INBOX, stroke(Offset.Zero, Offset(8f, 100f), Offset(12f, 110f), Offset(16f, 100f), Offset(5f, 5f)))
        check(HomeGesture.INBOX, stroke(Offset.Zero, Offset(0f, 80f), Offset(0f, 20f)))
        check(HomeGesture.INBOX, stroke(Offset.Zero, Offset(0f, 120f), Offset(0f, -20f)))
    }
    @Test fun normalCardinalSwipesKeepTheirActions() {
        for ((end, action) in listOf(Offset(0f, 160f) to HomeGesture.DOWN, Offset(0f, -160f) to HomeGesture.UP,
            Offset(160f, 0f) to HomeGesture.RIGHT, Offset(-160f, 0f) to HomeGesture.LEFT)) check(action, stroke(Offset.Zero, end))
        check(HomeGesture.DOWN, stroke(Offset.Zero, Offset(0f, 120f), Offset(0f, 110f)))
    }
    @Test fun incompleteAndRepeatedReturnsDoNotFallThroughToVerticalSwipes() {
        check(HomeGesture.NONE, stroke(Offset.Zero, Offset(0f, 160f), Offset(0f, 90f)))
        check(HomeGesture.NONE, stroke(Offset.Zero, Offset(0f, 160f), Offset(0f, -60f)))
        check(HomeGesture.NONE, stroke(Offset.Zero, Offset(0f, 120f), Offset(0f, 40f), Offset(0f, 70f), Offset.Zero))
        check(HomeGesture.NONE, stroke(Offset.Zero, Offset(0f, -120f), Offset.Zero))
    }
    @Test fun sizeAndCorridorBoundariesRejectNearMisses() {
        check(HomeGesture.NONE, stroke(Offset.Zero, Offset(0f, 79f), Offset.Zero))
        check(HomeGesture.INBOX, stroke(Offset.Zero, Offset(32f, 100f), Offset(0f, 0f)))
        check(HomeGesture.NONE, stroke(Offset.Zero, Offset(33f, 100f), Offset.Zero))
        check(HomeGesture.NONE, stroke(Offset.Zero, Offset(0f, 80f), Offset(0f, 21f)))
        check(HomeGesture.NONE, listOf(Offset.Zero))
    }
    @Test fun allEightLStrokesRetainTheirFourDestinations() {
        val paths = listOf(
            Triple(Offset(0f, 120f), Offset(120f, 120f), HomeGesture.TOP_RIGHT),
            Triple(Offset(0f, 120f), Offset(-120f, 120f), HomeGesture.TOP_LEFT),
            Triple(Offset(0f, -120f), Offset(120f, -120f), HomeGesture.BOTTOM_RIGHT),
            Triple(Offset(0f, -120f), Offset(-120f, -120f), HomeGesture.BOTTOM_LEFT),
            Triple(Offset(120f, 0f), Offset(120f, 120f), HomeGesture.BOTTOM_LEFT),
            Triple(Offset(120f, 0f), Offset(120f, -120f), HomeGesture.TOP_LEFT),
            Triple(Offset(-120f, 0f), Offset(-120f, 120f), HomeGesture.BOTTOM_RIGHT),
            Triple(Offset(-120f, 0f), Offset(-120f, -120f), HomeGesture.TOP_RIGHT))
        paths.forEach { (corner, end, expected) -> check(expected, stroke(Offset.Zero, corner, end)) }
    }
    @Test fun bothCircleDirectionsRetainTheirShortcut() {
        for (direction in listOf(-1, 1)) check(HomeGesture.CIRCLE, (0..60).map {
            val angle = direction * 2 * PI * it / 60
            Offset((80 * cos(angle)).toFloat(), (80 * sin(angle)).toFloat())
        })
    }
}
