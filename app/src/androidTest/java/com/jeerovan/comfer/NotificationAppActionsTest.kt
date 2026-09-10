package com.jeerovan.comfer

import android.os.Build
import android.provider.Settings
import com.jeerovan.comfer.notifications.notificationAppSettingsDestinations
import org.junit.Assert.*
import org.junit.Test

class NotificationAppActionsTest {
    @Test fun savedSettingsUseAppScopeWhileLiveSettingsPreferTheirChannel() {
        val saved = notificationAppSettingsDestinations("fixture.mail", null)
        val live = notificationAppSettingsDestinations("fixture.mail", "messages")
        if (Build.VERSION.SDK_INT >= 26) {
            assertEquals(Settings.ACTION_APP_NOTIFICATION_SETTINGS, saved.first().action)
            assertFalse(saved.any { it.hasExtra(Settings.EXTRA_CHANNEL_ID) })
            assertEquals(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS, live.first().action)
            assertEquals("messages", live.first().getStringExtra(Settings.EXTRA_CHANNEL_ID))
            assertEquals("fixture.mail", live.first().getStringExtra(Settings.EXTRA_APP_PACKAGE))
        }
        assertEquals("package:fixture.mail", saved.first { it.action == Settings.ACTION_APPLICATION_DETAILS_SETTINGS }.data.toString())
        assertEquals(Settings.ACTION_SETTINGS, saved.last().action)
    }
}
