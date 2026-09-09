package com.jeerovan.comfer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.provider.Settings
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.jeerovan.comfer.notifications.ListenerHealth
import com.jeerovan.comfer.notifications.NotificationInboxActivity
import org.junit.Assert.*
import org.junit.Test

/** Run after APK installation with access already granted. No grant toggles or fixture packages. */
class NotificationListenerRecoveryTest {
    @Test fun openingInboxRecoversGrantedListenerAndLoadsRealNotification() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertTrue("Precondition: access already granted", MyNotificationListenerService.hasAccess(context))
        val grantBefore = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = "recovery-test"
        if (android.os.Build.VERSION.SDK_INT >= 26) manager.createNotificationChannel(NotificationChannel(channel, "Recovery test", NotificationManager.IMPORTANCE_LOW))
        val builder = if (android.os.Build.VERSION.SDK_INT >= 26) Notification.Builder(context, channel) else Notification.Builder(context)
        val id = 719043
        manager.notify(id, builder.setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Listener recovery fixture").setContentText("Synthetic recovery test").build())
        try {
            ActivityScenario.launch(NotificationInboxActivity::class.java).use {
                val deadline = android.os.SystemClock.elapsedRealtime() + 35_000
                while (android.os.SystemClock.elapsedRealtime() < deadline) {
                    val state = MyNotificationListenerService.snapshot.value
                    if (state.health == ListenerHealth.CONNECTED && state.items.any { item -> item.app == context.packageName && item.title == "Listener recovery fixture" }) break
                    Thread.sleep(100)
                }
                val state = MyNotificationListenerService.snapshot.value
                assertEquals(ListenerHealth.CONNECTED, state.health)
                assertTrue(state.items.any { it.app == context.packageName && it.title == "Listener recovery fixture" })
                assertNotNull(state.lastSync)
                assertEquals("Recovery must preserve notification access", grantBefore,
                    Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners"))
            }
        } finally {
            manager.cancel(id)
            if (android.os.Build.VERSION.SDK_INT >= 26) manager.deleteNotificationChannel(channel)
        }
    }
}
