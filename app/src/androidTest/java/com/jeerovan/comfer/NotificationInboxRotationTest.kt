package com.jeerovan.comfer

import android.app.UiAutomation
import android.content.res.Configuration
import android.os.SystemClock
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.jeerovan.comfer.notifications.NotificationInboxActivity
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertTrue

/** Uses the production activity so Android recreation restores its actual content. */
class NotificationInboxRotationTest {
    @get:Rule val compose = createAndroidComposeRule<NotificationInboxActivity>()

    @Test fun systemBackClosesSettingsThenInboxWithoutBackButton() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val activity = compose.activity
        val heading = activity.getString(R.string.ui_notification_settings)
        compose.onNodeWithContentDescription(activity.getString(R.string.notification_back)).assertDoesNotExist()
        compose.onNodeWithContentDescription(activity.getString(R.string.notification_settings_tab)).performClick()
        compose.onNodeWithText(heading).assertIsDisplayed()
        instrumentation.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
        compose.onNodeWithText(heading).assertDoesNotExist()
        assertTrue("First Back stays in the inbox", !activity.isFinishing)
        instrumentation.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
        compose.waitUntil(5000) { activity.isFinishing }
    }

    @Test fun landscapeUsesFullAvailablePanelAndPreservesSettingsOnRotation() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val automation = instrumentation.uiAutomation
        val originalRotation = compose.activity.windowManager.defaultDisplay.rotation
        val heading = compose.activity.getString(R.string.ui_notification_settings)
        compose.onNodeWithContentDescription(compose.activity.getString(R.string.notification_settings_tab)).performClick()
        fun rotate(rotation: Int, orientation: Int) {
            automation.setRotation(rotation)
            // Wait for Android's replacement window before asking Compose for a frame.
            // Querying the outgoing root during recreation can wait forever for its draw.
            val deadline = SystemClock.uptimeMillis() + 10_000
            var ready = false
            while (!ready && SystemClock.uptimeMillis() < deadline) {
                compose.activityRule.scenario.onActivity { activity ->
                    val view = activity.window.decorView
                    ready = activity.resources.configuration.orientation == orientation &&
                        activity.hasWindowFocus() && view.isAttachedToWindow &&
                        (view.width > view.height) == (orientation == Configuration.ORIENTATION_LANDSCAPE)
                }
                if (!ready) SystemClock.sleep(50)
            }
            assertTrue("Rotated activity must have a focused, laid-out window", ready)
            instrumentation.waitForIdleSync()
        }
        try {
            if (compose.activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE)
                rotate(UiAutomation.ROTATION_FREEZE_0, Configuration.ORIENTATION_PORTRAIT)
            rotate(UiAutomation.ROTATION_FREEZE_90, Configuration.ORIENTATION_LANDSCAPE)
            val bounds = compose.onRoot().getUnclippedBoundsInRoot()
            assertTrue("Compose must use the landscape window", bounds.right - bounds.left > bounds.bottom - bounds.top)
            val panel = compose.onNodeWithTag("notification-inbox-panel").getUnclippedBoundsInRoot()
            val root = compose.onRoot().getUnclippedBoundsInRoot()
            assertTrue("Landscape panel fills safe window", (panel.bottom - panel.top).value > (root.bottom - root.top).value * .8f)
            compose.onNodeWithText(heading).assertIsDisplayed()
            compose.onNodeWithContentDescription(compose.activity.getString(R.string.notification_back)).assertDoesNotExist()
        } finally {
            automation.setRotation(originalRotation)
            automation.setRotation(UiAutomation.ROTATION_UNFREEZE)
        }
    }
}
