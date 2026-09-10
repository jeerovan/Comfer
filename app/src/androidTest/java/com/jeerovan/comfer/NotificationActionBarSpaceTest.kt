package com.jeerovan.comfer

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.jeerovan.comfer.notifications.NotificationActionBarTransition
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import androidx.compose.ui.unit.dp

class NotificationActionBarSpaceTest {
    @get:Rule val compose = createComposeRule()
    @Test fun listAnimatesInBothDirectionsAndEmptyBarUsesNoSpace() {
        val selected = mutableStateOf(false)
        compose.setContent {
            MaterialTheme {
                Column(Modifier.fillMaxSize()) {
                    Box(Modifier.weight(1f).fillMaxWidth().testTag("list"))
                    NotificationActionBarTransition {
                        if (selected.value) Column {
                            TextButton(modifier = Modifier.height(48.dp), onClick = {}) { Text("Hide") }
                            TextButton(modifier = Modifier.height(48.dp), onClick = {}) { Text("Cancel") }
                        }
                    }
                }
            }
        }
        val list = compose.onNodeWithTag("list")
        val before = list.getUnclippedBoundsInRoot()
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { selected.value = true }
        compose.mainClock.advanceTimeBy(96)
        val entering = list.getUnclippedBoundsInRoot()
        compose.mainClock.advanceTimeBy(400)
        val shown = list.getUnclippedBoundsInRoot()
        assertTrue(entering.bottom < before.bottom && entering.bottom > shown.bottom)
        compose.runOnIdle { selected.value = false }
        compose.mainClock.advanceTimeBy(96)
        val leaving = list.getUnclippedBoundsInRoot()
        assertTrue(leaving.bottom > shown.bottom && leaving.bottom < before.bottom)
        compose.mainClock.advanceTimeBy(400)
        compose.onNodeWithText("Cancel").assertDoesNotExist()
        assertEquals(before, list.getUnclippedBoundsInRoot())
        compose.mainClock.autoAdvance = true
    }
}
