package com.jeerovan.comfer

import org.junit.Assert.assertEquals
import org.junit.Test

class SwipeGuidePathTest {
    @Test
    fun quickListSwipeStaysVisibleAndCrossesTargetCenter() {
        val width = 240f
        val height = 240f
        val handSize = 48f
        val start = calculateSwipeEdgeOffset(
            direction = SwipeDirection.BOTTOM,
            width = width,
            height = height,
            handSize = handSize,
            outsideTarget = false,
        )
        val end = calculateSwipeEdgeOffset(
            direction = SwipeDirection.TOP,
            width = width,
            height = height,
            handSize = handSize,
            outsideTarget = false,
        )

        assertEquals(height - handSize, start.y, TOLERANCE)
        assertEquals(0f, end.y, TOLERANCE)
        assertEquals(width / 2f, start.x + handSize / 2f, TOLERANCE)
        assertEquals(width / 2f, end.x + handSize / 2f, TOLERANCE)

        val handCenterAtHalfway = (start.y + end.y) / 2f + handSize / 2f
        assertEquals(height / 2f, handCenterAtHalfway, TOLERANCE)
    }

    private companion object {
        const val TOLERANCE = 0.001f
    }
}
