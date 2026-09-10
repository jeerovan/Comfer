package com.jeerovan.comfer

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.jeerovan.comfer.notifications.*
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.*

class NotificationSavedTabTest {
    @get:Rule val compose = createComposeRule()

    @Test fun savedTabFollowsHiddenAndOffersSearch() {
        compose.setContent { MaterialTheme { NotificationInbox(onBack = {}) } }
        val hidden = compose.onNodeWithContentDescription("Hidden").getUnclippedBoundsInRoot()
        val saved = compose.onNodeWithTag("saved-tab")
        assertTrue(saved.getUnclippedBoundsInRoot().left > hidden.left)
        assertTrue(saved.getUnclippedBoundsInRoot().left < compose.onNodeWithContentDescription("Settings").getUnclippedBoundsInRoot().left)
        saved.performClick()
        compose.onNodeWithTag("notification-inbox-list").performScrollToNode(hasTestTag("saved-search"))
        compose.onNodeWithTag("saved-search").performTextInput("delivery")
        compose.onNodeWithTag("saved-search").assertTextContains("delivery")
        compose.onNodeWithText("Open App", ignoreCase = true).assertDoesNotExist()
    }

    @Test fun savedCopyIgnoresTapsAndSwipeDeletesIt() {
        var deleted = false
        compose.setContent {
            var visible by remember { mutableStateOf(true) }
            MaterialTheme {
                if (visible) SavedNotificationCard(SavedNotification("test", "fixture.mail", 0, "Delivery", "Tomorrow", 1, 2)) {
                    deleted = true; visible = false; true
                }
            }
        }
        val card = compose.onNodeWithTag("saved-copy:test")
        card.assertHasNoClickAction()
        card.performTouchInput { click(); doubleClick(); longClick() }
        compose.runOnIdle { assertFalse(deleted) }
        card.performTouchInput { swipeLeft() }
        compose.waitUntil(5000) { deleted }
        card.assertDoesNotExist()
    }
}
