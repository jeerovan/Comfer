package com.jeerovan.comfer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import com.jeerovan.comfer.notifications.*
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.*

class NotificationBodyPreviewTest {
    @get:Rule val compose = createComposeRule()
    @Test fun singleLineOpensImmediately() = verify("Short message", false)
    @Test fun wrappedTextExpandsBeforeOpening() = verify("A long notification message that wraps onto several lines at the available card width.", true)
    @Test fun explicitNewlinesExpandBeforeOpening() = verify("First line\nSecond line\nThird line", true)
    private fun verify(body: String, expandable: Boolean) {
        var opens = 0
        compose.setContent {
            val state = remember { NotificationBodyPreviewState() }
            MaterialTheme {
                Box(Modifier.width(160.dp).testTag("preview").clickable { state.tap { opens++ } }) {
                    NotificationBodyPreview(body, state)
                }
            }
        }
        val card = compose.onNodeWithTag("preview")
        val height = card.getUnclippedBoundsInRoot().let { it.bottom - it.top }
        card.performClick()
        compose.runOnIdle { assertEquals(if (expandable) 0 else 1, opens) }
        if (expandable) {
            assertTrue(card.getUnclippedBoundsInRoot().let { it.bottom - it.top } > height)
            card.performClick()
            compose.runOnIdle { assertEquals(1, opens) }
        }
    }
}
