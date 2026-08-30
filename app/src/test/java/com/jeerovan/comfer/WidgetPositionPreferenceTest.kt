package com.jeerovan.comfer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class WidgetPositionPreferenceTest {
    @Test
    fun landscapeUsesEqualPanesWithoutDroppingOddPixel() {
        val panes = calculateLauncherPaneSizes(
            width = 721,
            height = 400,
            isLandscape = true,
            portraitSecondPaneHeight = 300,
        )

        assertEquals(360, panes.firstWidth)
        assertEquals(361, panes.secondWidth)
        assertEquals(721, panes.firstWidth + panes.secondWidth)
        assertEquals(400, panes.firstHeight)
        assertEquals(400, panes.secondHeight)
    }

    @Test
    fun portraitKeepsFixedSecondPaneHeight() {
        val panes = calculateLauncherPaneSizes(
            width = 400,
            height = 900,
            isLandscape = false,
            portraitSecondPaneHeight = 400,
        )

        assertEquals(400, panes.firstWidth)
        assertEquals(500, panes.firstHeight)
        assertEquals(400, panes.secondWidth)
        assertEquals(400, panes.secondHeight)
    }

    @Test
    fun portraitClampsSecondPaneOnShortWindows() {
        val panes = calculateLauncherPaneSizes(
            width = 400,
            height = 320,
            isLandscape = false,
            portraitSecondPaneHeight = 400,
        )

        assertEquals(0, panes.firstHeight)
        assertEquals(320, panes.secondHeight)
    }

    @Test
    fun portraitKeepsExistingPreferenceKeys() {
        assertEquals(
            "widget_weather_x",
            widgetPositionPreferenceKey(
                id = "weather",
                axis = "x",
                orientation = WidgetLayoutOrientation.PORTRAIT,
            ),
        )
        assertEquals(
            "widget_weather_y",
            widgetPositionPreferenceKey(
                id = "weather",
                axis = "y",
                orientation = WidgetLayoutOrientation.PORTRAIT,
            ),
        )
    }

    @Test
    fun landscapeUsesIndependentPreferenceKeys() {
        val portraitKey = widgetPositionPreferenceKey(
            id = "weather",
            axis = "x",
            orientation = WidgetLayoutOrientation.PORTRAIT,
        )
        val landscapeKey = widgetPositionPreferenceKey(
            id = "weather",
            axis = "x",
            orientation = WidgetLayoutOrientation.LANDSCAPE,
        )

        assertEquals("widget_landscape_weather_x", landscapeKey)
        assertNotEquals(portraitKey, landscapeKey)
    }

    @Test
    fun invalidAxisIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            widgetPositionPreferenceKey(
                id = "weather",
                axis = "z",
                orientation = WidgetLayoutOrientation.LANDSCAPE,
            )
        }
    }
}
