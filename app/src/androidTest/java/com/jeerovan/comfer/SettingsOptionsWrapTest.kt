package com.jeerovan.comfer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.*

class SettingsOptionsWrapTest {
    @get:Rule val compose = createComposeRule()

    @Test fun settingsRingIsCenteredAndWholeLabelTogglesIt() {
        val label = "Enable device quiet hours with a label that wraps onto multiple lines"
        compose.setContent {
            val checked = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
            MaterialTheme {
                Box(Modifier.width(180.dp)) {
                    com.jeerovan.comfer.notifications.NotificationSettingToggle(label, checked.value) { checked.value = it }
                }
            }
        }
        val row = compose.onNodeWithText(label).assertIsOff()
        val bounds = row.getUnclippedBoundsInRoot()
        val indicator = compose.onNodeWithTag("notification-setting-indicator", useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertEquals((bounds.top.value + bounds.bottom.value) / 2, (indicator.top.value + indicator.bottom.value) / 2, .5f)
        row.performClick().assertIsOn()
        row.performClick().assertIsOff()
    }

    @Test fun choicesWrapAndRemainTappableAtNarrowWidth() {
        var chosen = ""
        compose.setContent {
            MaterialTheme {
                Box(Modifier.width(180.dp)) {
                    SettingChoiceButtons("Style", "normal", arrayOf(
                        KeyTextObject("Normal", "normal"),
                        KeyTextObject("Light", "light"),
                        KeyTextObject("Bold", "bold"),
                    )) { chosen = it }
                }
            }
        }
        val normal = compose.onNodeWithText("Normal").assertIsDisplayed().getUnclippedBoundsInRoot()
        val bold = compose.onNodeWithText("Bold").assertIsDisplayed().getUnclippedBoundsInRoot()
        compose.onNodeWithText("Light").assertIsDisplayed()
        assertTrue("Options must wrap onto another line", bold.top > normal.top)
        compose.onNodeWithText("Bold").performClick()
        compose.runOnIdle { assertEquals("bold", chosen) }
    }
}
