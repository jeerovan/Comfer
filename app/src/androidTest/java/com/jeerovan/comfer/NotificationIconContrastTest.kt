package com.jeerovan.comfer

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.unit.*
import com.jeerovan.comfer.notifications.renderNotificationContrastIcon
import org.junit.Test
import org.junit.Assert.*

class NotificationIconContrastTest {
    @Test fun glassShadesTheSilhouetteAndRespectsOpacityInBothDirections() {
        val painter = object : androidx.compose.ui.graphics.painter.Painter() {
            override val intrinsicSize = androidx.compose.ui.geometry.Size(48f, 48f)
            override fun androidx.compose.ui.graphics.drawscope.DrawScope.onDraw() {
                drawCircle(Color.White, radius = size.minDimension / 3)
            }
        }
        for (direction in listOf(LayoutDirection.Ltr, LayoutDirection.Rtl)) {
            val solid = renderNotificationContrastIcon(painter, 24.dp, Color.White, Density(2f), direction)
            val glass = renderNotificationContrastIcon(painter, 24.dp, Color.White, Density(2f), direction, glass = true)
            assertEquals(solid.width, glass.width)
            assertEquals(solid.height, glass.height)
            assertFalse("Glass changes the rendered icon", solid.sameAs(glass))
            assertNotEquals("Static lighting varies within the glyph", glass.getPixel(32, 20), glass.getPixel(32, 34))
            assertEquals("No rectangular plate outside the icon", 0, android.graphics.Color.alpha(glass.getPixel(8, 8)))
            val hidden = renderNotificationContrastIcon(painter, 24.dp, Color.Transparent, Density(2f), direction, glass = true)
            val pixels = IntArray(hidden.width * hidden.height)
            hidden.getPixels(pixels, 0, hidden.width, 0, 0, hidden.width, hidden.height)
            assertTrue("Opacity zero hides icon and shadow", pixels.all { android.graphics.Color.alpha(it) == 0 })
            solid.recycle(); glass.recycle(); hidden.recycle()
        }
    }
    @Test fun whiteIconHasDarkSilhouetteShadowOnWhiteBackground() = verify(Color.White, true)
    @Test fun blackIconHasLightSilhouetteShadowOnBlackBackground() = verify(Color.Black, false)
    private fun verify(color: Color, light: Boolean) {
        val bitmap = renderNotificationContrastIcon(ColorPainter(color), 24.dp, color, Density(2f), LayoutDirection.Ltr)
        val edge = bitmap.getPixel(6, bitmap.height / 2)
        val center = bitmap.getPixel(bitmap.width / 2, bitmap.height / 2)
        assertEquals(if (light) android.graphics.Color.WHITE else android.graphics.Color.BLACK, center)
        assertTrue("Shadow alpha must remain visible outside the silhouette", android.graphics.Color.alpha(edge) > 10)
        assertEquals(if (light) 0 else 255, android.graphics.Color.red(edge))
        bitmap.recycle()
    }
}
