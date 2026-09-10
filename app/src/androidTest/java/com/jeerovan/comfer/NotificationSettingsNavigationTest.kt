package com.jeerovan.comfer

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.espresso.Espresso
import com.jeerovan.comfer.notifications.NotificationInbox
import org.junit.Rule
import org.junit.Test

class NotificationSettingsNavigationTest {
    @get:Rule val compose = createComposeRule()
    private fun open(text: String) {
        compose.onNodeWithTag("notification-inbox-list").performScrollToNode(hasText(text))
        compose.onNodeWithText(text).performClick()
    }
    @Test fun contextPagesUseNativeBackAndHideUnrelatedControls() {
        compose.setContent { MaterialTheme { NotificationInbox(onBack = {}) } }
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithText("Focus 15 min").assertDoesNotExist()
        open("Quiet hours")
        open("Focus timers")
        compose.onNodeWithText("Repeat on").assertDoesNotExist()
        compose.onNodeWithTag("notification-inbox-list").performScrollToNode(hasText("Focus 15 min"))
        compose.onNodeWithText("Focus 15 min").assertIsDisplayed()
        Espresso.pressBack()
        open("Schedules")
        open("Recurring schedule")
        compose.onNodeWithTag("notification-inbox-list").performScrollToNode(hasText("Repeat on"))
        compose.onNodeWithText("Repeat on").assertIsDisplayed()
        compose.onNodeWithText("Focus 15 min").assertDoesNotExist()
        Espresso.pressBack()
        compose.onNodeWithText("Recurring schedule").assertExists()
    }
}
