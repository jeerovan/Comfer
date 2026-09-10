package com.jeerovan.comfer

import androidx.compose.ui.graphics.Color
import org.junit.Assert.*
import org.junit.Test

class WidgetContrastTest {
    @Test fun matchingLightAndDarkShadowColorsAreRepaired() {
        assertEquals(Color.Black.copy(alpha = .85f), widgetShadowColor(Color.White, Color.White))
        assertEquals(Color.White.copy(alpha = .85f), widgetShadowColor(Color.Black, Color.Black))
    }
    @Test fun explicitOffAndContrastingCustomShadowsArePreserved() {
        assertEquals(Color.Transparent, widgetShadowColor(Color.White, Color.Transparent))
        val custom = Color(0xff102030).copy(alpha = .7f)
        assertEquals(custom, widgetShadowColor(Color.White, custom))
    }
    @Test fun automaticShadowsWorkForCustomColorsAndTransparency() {
        assertEquals(Color.Black.copy(alpha = .85f), widgetShadowColor(Color.Yellow))
        assertEquals(Color.White.copy(alpha = .85f), widgetShadowColor(Color.Blue))
        assertEquals(widgetShadowColor(Color.White), widgetShadowColor(Color.White.copy(alpha = .5f)))
    }
}
