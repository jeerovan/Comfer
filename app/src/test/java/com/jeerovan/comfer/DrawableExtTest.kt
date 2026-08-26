package com.jeerovan.comfer

import org.junit.Assert.assertEquals
import org.junit.Test

class DrawableExtTest {
    @Test
    fun invalidIntrinsicSizeUsesMinimumBitmapDimensions() {
        assertEquals(
            BitmapDimensions(1, 1),
            constrainedBitmapDimensions(intrinsicWidth = -1, intrinsicHeight = 0),
        )
    }

    @Test
    fun oversizedDrawablePreservesAspectRatioWithinLimit() {
        assertEquals(
            BitmapDimensions(512, 256),
            constrainedBitmapDimensions(intrinsicWidth = 4_096, intrinsicHeight = 2_048),
        )
    }

    @Test
    fun smallDrawableKeepsItsIntrinsicDimensions() {
        assertEquals(
            BitmapDimensions(96, 48),
            constrainedBitmapDimensions(intrinsicWidth = 96, intrinsicHeight = 48),
        )
    }
}
