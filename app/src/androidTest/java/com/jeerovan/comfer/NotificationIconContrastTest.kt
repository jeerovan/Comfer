package com.jeerovan.comfer

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.unit.*
import com.jeerovan.comfer.notifications.renderNotificationContrastIcon
import org.junit.Test
import org.junit.Assert.*

class NotificationIconContrastTest {
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
