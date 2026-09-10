package com.jeerovan.comfer

import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.jeerovan.comfer.notifications.*
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.*

class NotificationSavedTabTest {
    @get:Rule val compose = createComposeRule()

    @Test fun savedTabFollowsActiveAndOffersSearch() {
        compose.setContent { MaterialTheme { NotificationInbox(onBack = {}) } }
        val active = compose.onNodeWithContentDescription("All apps").getUnclippedBoundsInRoot()
        val saved = compose.onNodeWithTag("saved-tab")
        assertTrue(saved.getUnclippedBoundsInRoot().left > active.left)
        assertTrue(saved.getUnclippedBoundsInRoot().left < compose.onNodeWithContentDescription("Settings").getUnclippedBoundsInRoot().left)
        saved.performClick()
        compose.onNodeWithTag("notification-inbox-list").performScrollToNode(hasTestTag("saved-search"))
        compose.onNodeWithTag("saved-search").performTextInput("delivery")
        compose.onNodeWithTag("saved-search").assertTextContains("delivery")
        compose.onNodeWithText("Open App", ignoreCase = true).assertDoesNotExist()
    }

    @Test fun savedCopyOpensSelectsAndSwipeDeletesOnlyIt() {
        var deleted = false
        var opened = false
        var selected = false
        compose.setContent {
            var visible by remember { mutableStateOf(true) }
            MaterialTheme {
                if (visible) SavedNotificationCard(SavedNotification("test", "fixture.mail", 0, "Delivery", "Tomorrow", 1, 2), onOpen = { opened = true }, onSelect = { selected = true }) {
                    deleted = true; visible = false; true
                }
            }
        }
        val card = compose.onNodeWithTag("saved-copy:test")
        card.performClick()
        compose.runOnIdle { assertTrue(opened); assertFalse(deleted) }
        card.performTouchInput { longClick() }
        compose.runOnIdle { assertTrue(selected); assertFalse(deleted) }
        card.performTouchInput { swipeLeft() }
        compose.waitUntil(5000) { deleted }
        card.assertDoesNotExist()
    }
    @Test fun savedLongBodyExpandsBeforeOpeningAndSelectionNeverOpens() {
        var opens = 0
        compose.setContent {
            var selected by remember { mutableStateOf(false) }
            MaterialTheme {
                SavedNotificationCard(SavedNotification("long", "fixture.mail", 0, "Long preview", "First line\nSecond line\nThird line", 1, 2),
                    selectionMode = selected, selected = selected, onSelect = { selected = !selected }, onOpen = { opens++ }, onDismiss = { true })
            }
        }
        val card = compose.onNodeWithTag("saved-copy:long")
        val initialHeight = card.getUnclippedBoundsInRoot().let { it.bottom - it.top }
        card.performClick()
        compose.runOnIdle { assertEquals(0, opens) }
        assertTrue(card.getUnclippedBoundsInRoot().let { it.bottom - it.top } > initialHeight)
        card.performClick()
        compose.runOnIdle { assertEquals(1, opens) }
        card.performTouchInput { longClick() }
        card.performClick()
        compose.onNodeWithTag("saved-select:long").assertDoesNotExist()
        compose.runOnIdle { assertEquals(1, opens) }
    }

    @Test fun historyGroupsSelectAndCollapseIndependently() {
        val copies = listOf(
            SavedNotification("chat1", "fixture.chat", 0, "Chat one", "Hello", 3, 3),
            SavedNotification("chat2", "fixture.chat", 0, "Chat two", "Hi", 2, 2),
            SavedNotification("mail1", "fixture.mail", 0, "Mail", "Delivery", 1, 1),
        )
        var selection by mutableStateOf(emptySet<String>())
        compose.setContent {
            var collapsed by remember { mutableStateOf(emptySet<String>()) }
            MaterialTheme {
                LazyColumn(Modifier.fillMaxSize()) {
                    savedNotificationItems(copies, false, collapsed, emptySet(), selection, true,
                        onCollapse = { collapsed = if (it in collapsed) collapsed - it else collapsed + it },
                        onSelect = { selection = if (it in selection) selection - it else selection + it },
                        onGroupSelect = { app ->
                            val ids = copies.filter { it.appId == app }.map { it.id }.toSet()
                            selection = if (selection.containsAll(ids)) selection - ids else selection + ids
                        }, onOpen = {}, onDelete = { true })
                }
            }
        }
        compose.onNodeWithTag("saved-copy:chat1").performTouchInput { longClick() }
        compose.onNodeWithTag("saved-select:chat1").assertIsOn()
        compose.onNodeWithTag("saved-select:chat2").assertIsOff()
        compose.onNodeWithTag("saved-group-select:0:fixture.chat").performClick()
        compose.runOnIdle { assertEquals(setOf("chat1", "chat2"), selection) }
        compose.onNodeWithTag("saved-group-expand:0:fixture.chat").performClick()
        compose.onNodeWithTag("saved-copy:chat1").assertDoesNotExist()
        compose.onNodeWithTag("saved-copy:mail1").assertExists()
        compose.onNodeWithTag("saved-group-expand:0:fixture.chat").performClick()
        compose.onNodeWithTag("saved-select:chat2").assertIsOn()
        compose.onNodeWithTag("saved-group-select:0:fixture.chat").performClick()
        compose.runOnIdle { assertTrue(selection.isEmpty()) }
        compose.onNodeWithTag("saved-select:chat1").assertDoesNotExist()
    }

}
