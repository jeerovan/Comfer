package com.jeerovan.comfer

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class AppGuideTest {
    @get:Rule val compose = createAndroidComposeRule<GuideActivity>()

    @Test fun guideSectionsRemainReachableAndNotificationHelpScrolls() {
        compose.onNodeWithText("Navigation").assertIsSelected()
        compose.onNodeWithText("Notification Inbox").assertIsDisplayed().performClick()
        compose.onNodeWithText("Notification Inbox").assertIsSelected()
        val headings = listOf(
            R.string.app_guide_tabs_title, R.string.app_guide_actions_title,
            R.string.app_guide_more_title, R.string.app_guide_quiet_title,
            R.string.app_guide_howto_open_title, R.string.app_guide_howto_recover_title,
        )
        headings.forEach { id ->
            val label = compose.activity.getString(id)
            compose.onNodeWithTag("notification-guide-list").performScrollToNode(hasText(label))
            compose.onNodeWithText(label).assertIsDisplayed()
            compose.onNodeWithText("Navigation").assertIsDisplayed()
            compose.onNodeWithText("Notification Inbox").assertIsDisplayed()
        }
        compose.onNodeWithTag("notification-guide-list").performScrollToNode(hasText(compose.activity.getString(R.string.app_guide_tabs_title)))
        InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()?.let { bitmap ->
            java.io.File(compose.activity.getExternalFilesDir(null), "app-guide-tabs.png").outputStream().use {
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
        }
        compose.onNodeWithText("Navigation").performClick()
        compose.onNodeWithText("Navigation").assertIsSelected()
        val activity = compose.activity
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
        compose.waitUntil(5000) { activity.isFinishing }
        assertTrue(activity.isFinishing)
    }
}
