package com.jeerovan.comfer

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.down
import androidx.compose.ui.test.moveTo
import androidx.compose.ui.test.up
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SettingSliderTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun tenSecondRapidDragCommitsOnlyOnGestureFinish() {
        var commitCount = 0
        var committedValue = -1
        composeRule.setContent {
            MaterialTheme {
                SettingSlider(
                    label = "Stress slider",
                    value = 50,
                    range = 0f..100f,
                    modifier = Modifier.testTag(SLIDER_TAG),
                    onValueChange = {
                        commitCount++
                        committedValue = it
                    },
                )
            }
        }

        composeRule.onNodeWithTag(SLIDER_TAG).performTouchInput {
            val inset = 2f
            val usableWidth = width - inset * 2
            down(center)
            repeat(1_000) { event ->
                val fraction = (event % 100).toFloat() / 99f
                moveTo(
                    Offset(inset + usableWidth * fraction, center.y),
                    delayMillis = 10,
                )
            }
            up()
        }

        composeRule.runOnIdle {
            assertEquals(1, commitCount)
            assertEquals(100, committedValue)
        }
    }

    private companion object {
        const val SLIDER_TAG = "stress-slider"
    }
}
