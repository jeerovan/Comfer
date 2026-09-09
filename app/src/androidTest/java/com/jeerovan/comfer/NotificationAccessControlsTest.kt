package com.jeerovan.comfer

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import com.jeerovan.comfer.notifications.NotificationInboxActivity
import org.junit.Assert.assertFalse
import org.junit.Assume.assumeFalse
import org.junit.Rule
import org.junit.Test

/** Run on a device with access disabled; never changes the user's permission. */
class NotificationAccessControlsTest {
    @get:Rule val compose = createAndroidComposeRule<NotificationInboxActivity>()

    @Test fun accessControlsRemainVisibleWithoutPermission() {
        assumeFalse(MyNotificationListenerService.hasAccess(compose.activity))
        fun checkControls() {
            compose.onNodeWithText("Notification access needed").assertIsDisplayed()
            compose.onNodeWithTag("notification-connection-actions").assertIsDisplayed()
            compose.onNodeWithText("Refresh connection", ignoreCase = true).assertIsDisplayed().assertHasClickAction()
            compose.onNodeWithText("Notification access", ignoreCase = true).assertIsDisplayed().assertHasClickAction()
        }
        checkControls()
        // Cover delayed listener/recovery updates without any user interaction.
        val start = android.os.SystemClock.elapsedRealtime()
        compose.waitUntil(40_000) { android.os.SystemClock.elapsedRealtime() - start >= 32_000 }
        checkControls()
        compose.onNodeWithText("Refresh connection", ignoreCase = true).performClick()
        checkControls()
        assertFalse(MyNotificationListenerService.hasAccess(compose.activity))
    }
}
