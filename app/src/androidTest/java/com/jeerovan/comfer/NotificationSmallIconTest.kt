package com.jeerovan.comfer

import android.app.Notification
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.Icon
import androidx.core.graphics.drawable.toBitmap
import androidx.test.platform.app.InstrumentationRegistry
import com.jeerovan.comfer.notifications.loadNotificationSmallIcon
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
}
