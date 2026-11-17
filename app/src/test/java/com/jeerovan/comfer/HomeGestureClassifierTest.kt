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
    @Test fun unequalReturnsOpenInboxWithoutMatchingTheStartingPoint() {
        for ((down, up) in listOf(160f to 40f, 160f to 70f, 80f to 160f, 160f to 220f)) {
            val points = stroke(Offset.Zero, Offset(8f, down), Offset(12f, down - up))
            check(HomeGesture.INBOX, points)
            // Pauses and coalesced touch events must not impose a stroke ratio.
            check(HomeGesture.INBOX, points.flatMap { List(3) { _ -> it } })
            check(HomeGesture.INBOX, listOf(points.first(), Offset(8f, down), points.last()))
        }
        check(HomeGesture.INBOX, stroke(Offset.Zero, Offset(8f, 130f), Offset(16f, 140f), Offset(22f, 130f), Offset(12f, 80f)))
    }
    @Test fun incompleteAndRepeatedReturnsDoNotFallThroughToVerticalSwipes() {
        check(HomeGesture.NONE, stroke(Offset.Zero, Offset(0f, 160f), Offset(0f, 121f)))
        check(HomeGesture.NONE, stroke(Offset.Zero, Offset(0f, 120f), Offset(0f, 40f), Offset(0f, 70f), Offset.Zero))
        check(HomeGesture.NONE, stroke(Offset.Zero, Offset(0f, -120f), Offset.Zero))
    }
    @Test fun sizeAndCorridorBoundariesRejectNearMisses() {
        check(HomeGesture.NONE, stroke(Offset.Zero, Offset(0f, 79f), Offset.Zero))
        check(HomeGesture.INBOX, stroke(Offset.Zero, Offset(32f, 100f), Offset(0f, 0f)))
        check(HomeGesture.NONE, stroke(Offset.Zero, Offset(33f, 100f), Offset.Zero))
        check(HomeGesture.INBOX, stroke(Offset.Zero, Offset(0f, 80f), Offset(0f, 40f)))
        check(HomeGesture.NONE, stroke(Offset.Zero, Offset(0f, 80f), Offset(0f, 41f)))
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

    @Test fun unevenOvalAndWobblyNearlyClosedLoopsAreCircles() {
        for (direction in listOf(-1, 1)) {
            // A thumb-sized oval, traversed slowly on one side and quickly on the other.
            val oval = (0..80).flatMap { i ->
                val angle = direction * 2 * PI * i / 80
                val point = Offset((100 * cos(angle)).toFloat(), (42 * sin(angle)).toFloat())
                List(if (i in 15..35) 8 else 1) { point }
            }
            check(HomeGesture.CIRCLE, oval)
            check(HomeGesture.CIRCLE, (0..64).map { i ->
                val angle = direction * 1.85 * PI * i / 64
                val radius = 65 * (1 + .15 * sin(3 * angle))
                Offset((radius * cos(angle)).toFloat(), (radius * sin(angle)).toFloat())
            })
        }
    }

    @Test fun sparseAndRoundedCornersKeepAllFourDestinations() {
        val rounded = stroke(Offset.Zero, Offset(2f, 55f), Offset(10f, 78f), Offset(30f, 96f), Offset(105f, 102f))
        for ((sx, sy, expected) in listOf(
            Triple(1f, 1f, HomeGesture.TOP_RIGHT), Triple(-1f, 1f, HomeGesture.TOP_LEFT),
            Triple(1f, -1f, HomeGesture.BOTTOM_RIGHT), Triple(-1f, -1f, HomeGesture.BOTTOM_LEFT))) {
            check(expected, rounded.map { Offset(it.x * sx, it.y * sy) })
            check(expected, listOf(Offset.Zero, Offset(4f * sx, 45f * sy), Offset(55f * sx, 49f * sy)))
        }
    }

    @Test fun unfinishedLoopsAndShortSecondLegDoNotBecomeNavigation() {
        for (direction in listOf(-1, 1)) check(HomeGesture.NONE, (0..50).map {
            val angle = direction * 1.35 * PI * it / 50
            Offset((80 * cos(angle)).toFloat(), (80 * sin(angle)).toFloat())
        })
        check(HomeGesture.NONE, stroke(Offset.Zero, Offset(0f, 120f), Offset(30f, 120f)))
    }

    @Test fun slightlyCurvedSwipesRemainNavigationAndInvalidShapesStayInactive() {
        check(HomeGesture.RIGHT, stroke(Offset.Zero, Offset(60f, 8f), Offset(140f, 3f)))
        check(HomeGesture.UP, stroke(Offset.Zero, Offset(8f, -60f), Offset(3f, -140f)))
        check(HomeGesture.NONE, stroke(Offset.Zero, Offset(80f, 120f), Offset(160f, 0f), Offset.Zero))
        check(HomeGesture.NONE, stroke(Offset.Zero, Offset(90f, 80f), Offset(-40f, 20f), Offset(100f, -80f), Offset.Zero))
    }
}
