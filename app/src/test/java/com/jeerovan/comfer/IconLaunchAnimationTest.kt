package com.jeerovan.comfer

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntRect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IconLaunchAnimationTest {
    @Test fun removesWindowInsetsWithoutMovingTheIconOrigin() {
        assertEquals(IntRect(20, 30, 84, 94),
            iconLaunchBounds(Rect(30f, 54f, 94f, 118f), Offset(10f, 24f)))
    }

    @Test fun preservesFractionalScalingAndPartiallyOffscreenOrigins() {
        assertEquals(IntRect(-11, 19, 40, 70),
            iconLaunchBounds(Rect(-10.5f, 43.5f, 39.5f, 93.5f), Offset(0f, 24f)))
    }

    @Test fun missingEmptyInvertedAndNonFiniteBoundsUseDefaultTransition() {
        for (bounds in listOf(null, Rect.Zero, Rect(5f, 5f, 4f, 9f),
            Rect(0f, 0f, Float.NaN, 10f), Rect(0f, 0f, 10f, Float.POSITIVE_INFINITY))) {
            assertNull(iconLaunchBounds(bounds, Offset.Zero))
        }
        assertNull(iconLaunchBounds(Rect(0f, 0f, 10f, 10f), Offset(Float.NaN, 0f)))
    }
}
