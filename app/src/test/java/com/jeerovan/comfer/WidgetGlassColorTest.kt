package com.jeerovan.comfer

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import org.junit.Assert.*
import org.junit.Test

class WidgetGlassColorTest {
    private fun settings(background: Color?, enabled: Boolean = true) = SettingsUiState(
        autoWallpapers = true, widgetGlassEffect = enabled, showThemedText = false,
        themedColors = WallpaperThemeColors(0, 0, 0, 0, 0, 0, background?.toArgb()))

    @Test fun wallpaperHueChangesGlassIndependentlyOfWallpaperTextColors() {
        val red = widgetGlassColor(settings(Color(0xff300808)), Color.White)
        val blue = widgetGlassColor(settings(Color(0xff081030)), Color.White)
        assertNotEquals(red, blue)
        assertTrue(red.red > red.blue)
        assertTrue(blue.blue > blue.red)
    }

    @Test fun tintKeepsOpacityAndSeparatesFromLightDarkAndBoundaryBackgrounds() {
        for (background in listOf(Color.Black, Color.White, Color.Gray, Color.Red, Color.Cyan,
            Color(0xff747474), Color(0xff123456))) {
            val tint = widgetGlassColor(settings(background), Color.White.copy(alpha = .3f))
            assertEquals(.3f, tint.alpha, .005f)
            val a = tint.luminance(); val b = background.luminance()
            assertTrue("Base-color contrast for $background", (maxOf(a, b) + .05f) / (minOf(a, b) + .05f) >= 4.5f)
        }
        assertEquals(0f, widgetGlassColor(settings(Color.Blue), Color.Transparent).alpha, 0f)
    }

    @Test fun offOrUnavailableBackgroundPreservesTheExistingTextColor() {
        val original = Color(0xffcc88aa).copy(alpha = .6f)
        assertEquals(original, widgetGlassColor(settings(Color.Blue, enabled = false), original))
        assertEquals(original, widgetGlassColor(settings(null), original))
        assertEquals(original, widgetGlassColor(SettingsUiState(), original))
        assertEquals(original, widgetGlassColor(SettingsUiState(), original, Color.Transparent))
        assertNotEquals(original, widgetGlassColor(SettingsUiState(), original, Color(0xff002040)))
    }
}
