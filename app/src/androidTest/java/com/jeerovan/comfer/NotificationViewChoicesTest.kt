package com.jeerovan.comfer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import com.jeerovan.comfer.notifications.NotificationViewChoices
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertEquals

class NotificationViewChoicesTest {
    @get:Rule val compose = createComposeRule()

    @Test fun choicesRemainVisibleInOneRowAndSelectionIsExplicit() {
        compose.setContent {
            var chronological by remember { mutableStateOf(false) }
            MaterialTheme {
                Box(Modifier.width(280.dp)) {
                    NotificationViewChoices(chronological) { chronological = it }
                }
            }
        }
        val grouped = compose.onNodeWithText("Grouped by app")
        val chronological = compose.onNodeWithText("Chronological")
        grouped.assertIsDisplayed().assertIsSelected()
        chronological.assertIsDisplayed().assertIsNotSelected()
        assertEquals(grouped.getUnclippedBoundsInRoot().top, chronological.getUnclippedBoundsInRoot().top)
        chronological.performClick().assertIsSelected()
        grouped.assertIsNotSelected().performClick().assertIsSelected()
        chronological.assertIsNotSelected()
    }
}
