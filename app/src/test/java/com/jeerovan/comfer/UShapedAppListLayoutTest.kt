package com.jeerovan.comfer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UShapedAppListLayoutTest {
    @Test
    fun appIndexesWrapAcrossLargePositiveAndNegativeScrollOffsets() {
        assertEquals(4, wrapAppIndex(-1, 5))
        assertEquals(4, wrapAppIndex(-16, 5))
        assertEquals(1, wrapAppIndex(16, 5))
        assertEquals(2, wrapAppIndex(Long.MIN_VALUE, 5))
        assertEquals(2, wrapAppIndex(Long.MAX_VALUE, 5))
        assertEquals(Int.MAX_VALUE - 1, wrapAppIndex(-1, Int.MAX_VALUE))
    }

    @Test(expected = IllegalArgumentException::class)
    fun appIndexRejectsEmptyLists() {
        wrapAppIndex(0, 0)
    }

    @Test
    fun logicalItemIdentityMovesContinuouslyToThePreviousSlot() {
        val visibleIconCount = 9
        for (slot in 1 until visibleIconCount) {
            assertEquals(
                uShapeLogicalIndex(10, visibleIconCount, slot),
                uShapeLogicalIndex(11, visibleIconCount, slot - 1),
            )
        }
    }

    @Test
    fun portraitKeepsWidthBasedArc() {
        val layout = calculateUShapeLayoutParams(
            width = 720f,
            height = 1280f,
            sidePadding = 36f,
            topPadding = 140f,
            smallIconSize = 96f,
            largeIconSize = 156f,
            minimumGap = 12f,
        )

        assertNotNull(layout)
        requireNotNull(layout)
        assertFalse(layout.isHeightConstrained)
        assertEquals(276f, layout.arcRadius, 0.01f)
        assertEquals(36f, layout.leftColumnX, 0.01f)
        assertEquals(588f, layout.rightColumnX, 0.01f)
    }

    @Test
    fun landscapeCapsArcToAvailableHeight() {
        val height = 720f
        val sidePadding = 36f
        val bottomPadding = 12f
        val layout = calculateUShapeLayoutParams(
            width = 1184f,
            height = height,
            sidePadding = sidePadding,
            topPadding = 140f,
            smallIconSize = 96f,
            largeIconSize = 156f,
            minimumGap = 12f,
        )

        assertNotNull(layout)
        requireNotNull(layout)
        assertTrue(layout.isHeightConstrained)
        assertEquals(412f, layout.arcRadius, 0.01f)
        assertTrue(layout.numTopIcons > 1)
        assertTrue(layout.numSideIcons > 0)

        val firstSideIconBottom =
            layout.sideColumnY + layout.verticalSpacingPx + layout.smallIconPx
        assertTrue(firstSideIconBottom <= height - bottomPadding)
        assertEquals(
            layout.leftColumnX,
            layout.width - layout.rightColumnX - layout.smallIconPx,
            0.01f,
        )
    }

    @Test
    fun layoutRejectsSpaceTooSmallForTheConfiguredIcons() {
        val layout = calculateUShapeLayoutParams(
            width = 300f,
            height = 180f,
            sidePadding = 24f,
            topPadding = 80f,
            smallIconSize = 96f,
            largeIconSize = 156f,
            minimumGap = 12f,
        )

        assertEquals(null, layout)
    }
}
