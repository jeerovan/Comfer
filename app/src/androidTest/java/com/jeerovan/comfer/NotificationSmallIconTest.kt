package com.jeerovan.comfer

import android.app.Notification
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.Icon
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.jeerovan.comfer.notifications.loadNotificationSmallIcon
import com.jeerovan.comfer.notifications.renderNotificationContrastIcon
import org.junit.Assert.*
import org.junit.Test

class NotificationSmallIconTest {
    @Test fun usesPostedSmallIconAndChangesWhenNotificationIconChanges() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        fun notification(x: Int): Notification {
            val bitmap = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)
            bitmap.setPixel(x, 8, Color.WHITE)
            return Notification.Builder(context).setSmallIcon(Icon.createWithBitmap(bitmap)).build()
        }
        val first = loadNotificationSmallIcon(context, notification(3))!!.toBitmap(16, 16)
        val second = loadNotificationSmallIcon(context, notification(12))!!.toBitmap(16, 16)
        assertEquals(Color.WHITE, first.getPixel(3, 8))
        assertEquals(Color.TRANSPARENT, first.getPixel(12, 8))
        assertEquals(Color.WHITE, second.getPixel(12, 8))
        assertEquals(Color.TRANSPARENT, second.getPixel(3, 8))
    }

    @Test fun missingSmallIconHasGenericFallback() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertNotNull(loadNotificationSmallIcon(context, null))
        assertNotNull(loadNotificationSmallIcon(context, Notification()))
    }

    @Test fun opaqueSmallIconKeepsLogoWithWhiteTintAndGlass() {
        // Include grayscale artwork: black/white detail also disappears under a blanket tint.
        for (background in listOf(Color.BLUE, Color.BLACK)) {
            verifyArtwork(Icon.createWithBitmap(artwork(background)), background)
        }
    }

    @Test @SdkSuppress(minSdkVersion = 26)
    fun adaptiveSmallIconKeepsLogoWithWhiteTintAndGlass() {
        verifyArtwork(Icon.createWithAdaptiveBitmap(artwork(Color.BLUE)), Color.BLUE)
    }

    private fun artwork(background: Int) = Bitmap.createBitmap(108, 108, Bitmap.Config.ARGB_8888).apply {
        eraseColor(background)
        android.graphics.Canvas(this).drawRect(44f, 44f, 64f, 64f,
            android.graphics.Paint().apply { color = Color.WHITE })
    }

    private fun verifyArtwork(icon: Icon, background: Int) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val notification = Notification.Builder(context).setSmallIcon(icon).build()
        val source = loadNotificationSmallIcon(context, notification)!!.toOwnedBitmap()!!
        try {
            for (glass in listOf(false, true)) {
                val rendered = renderNotificationContrastIcon(BitmapPainter(source.asImageBitmap()),
                    48.dp, androidx.compose.ui.graphics.Color.White, Density(2f), LayoutDirection.Ltr, glass)
                try {
                    val pixels = IntArray(rendered.width * rendered.height)
                    rendered.getPixels(pixels, 0, rendered.width, 0, 0, rendered.width, rendered.height)
                    assertTrue("Background artwork survives (glass=$glass)", pixels.count { it == background } > 100)
                    assertTrue("Logo survives (glass=$glass)", pixels.count { it == Color.WHITE } > 20)
                } finally { rendered.recycle() }
            }
        } finally { source.recycle() }
    }
}
