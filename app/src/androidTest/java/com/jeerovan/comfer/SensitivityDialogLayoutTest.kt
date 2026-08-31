package com.jeerovan.comfer

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test

class SensitivityDialogLayoutTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun compactDialogShowsActionsAtLandscapeHeight() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val cancelText = context.getString(R.string.cancel_text)
        val saveText = context.getString(R.string.button_text_save)

        composeRule.setContent {
            MaterialTheme {
                SensitivityDialogContent(
                    speed = 1f,
                    onSpeedChange = {},
                    onDismiss = {},
                    onSave = {},
                    compact = true,
                    modifier = Modifier
                        .width(420.dp)
                        .height(240.dp)
                )
            }
        }

        composeRule.onNodeWithText(cancelText).assertIsDisplayed()
        composeRule.onNodeWithText(saveText).assertIsDisplayed()
    }
}
