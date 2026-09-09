package com.jeerovan.comfer

import android.app.UiAutomation
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
        compose.onNodeWithContentDescription("Back").assertDoesNotExist()
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithText("Configuration").assertIsDisplayed()
        instrumentation.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
        compose.onNodeWithText("Configuration").assertDoesNotExist()
        assertTrue("First Back stays in the inbox", !activity.isFinishing)
        instrumentation.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
        compose.waitUntil(5000) { activity.isFinishing }
    }

    @Test fun landscapeUsesFullAvailablePanelAndPreservesSettingsOnRotation() {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        compose.onNodeWithContentDescription("Settings").performClick()
        try {
            automation.setRotation(UiAutomation.ROTATION_FREEZE_90)
            compose.waitUntil(10_000) {
                runCatching {
                    val root = compose.onRoot().getUnclippedBoundsInRoot()
                    root.right - root.left > root.bottom - root.top
                }.getOrDefault(false)
            }
            val panel = compose.onNodeWithTag("notification-inbox-panel").getUnclippedBoundsInRoot()
            val root = compose.onRoot().getUnclippedBoundsInRoot()
            assertTrue("Landscape panel fills safe window", (panel.bottom - panel.top).value > (root.bottom - root.top).value * .8f)
            compose.onNodeWithText("Configuration").assertIsDisplayed()
            compose.onNodeWithContentDescription("Back").assertDoesNotExist()
        } finally {
            automation.setRotation(UiAutomation.ROTATION_FREEZE_0)
            automation.setRotation(UiAutomation.ROTATION_UNFREEZE)
        }
    }
}
