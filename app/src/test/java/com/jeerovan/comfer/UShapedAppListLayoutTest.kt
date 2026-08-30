package com.jeerovan.comfer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UShapedAppListLayoutTest {
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
