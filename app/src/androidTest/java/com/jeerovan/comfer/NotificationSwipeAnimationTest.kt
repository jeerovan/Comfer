package com.jeerovan.comfer

import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import com.jeerovan.comfer.notifications.NotificationSwipeContainer
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.*

class NotificationSwipeAnimationTest {
    @get:Rule val compose = createComposeRule()

    @Test fun leftDismissWaitsUntilEntireCardLeavesScreen() = verifyExit(left = true)
    @Test fun rightDismissWaitsUntilEntireCardLeavesScreen() = verifyExit(left = false)

    private fun verifyExit(left: Boolean) {
        var dismissed = false
        var lastLeft = 0f
        var lastRight = 0f
        var screenWidth = 0f
        var fullyOutsideAtDismiss = false
        compose.setContent {
            Box(Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
                NotificationSwipeContainer(enabled = true, onDismiss = {
                    fullyOutsideAtDismiss = if (left) lastRight <= 1f else lastLeft >= screenWidth - 1f
                    dismissed = true
                    false // Also verify that rejection restores the card.
                }) {
                    Box(Modifier.fillMaxWidth().height(80.dp).testTag("swipe-card").onGloballyPositioned {
                        lastLeft = it.positionInRoot().x
                        lastRight = lastLeft + it.size.width
                        screenWidth = it.findRootCoordinates().size.width.toFloat()
                    })
                }
            }
        }
        val card = compose.onNodeWithTag("swipe-card")
        val start = card.getUnclippedBoundsInRoot()
        compose.mainClock.autoAdvance = false
        card.performTouchInput {
            // Release while part of the card is still on-screen.
            swipe(if (left) centerRight else centerLeft, center, durationMillis = 120)
        }
        compose.mainClock.advanceTimeBy(32)
        compose.runOnIdle { assertFalse("Do not dismiss at finger release", dismissed) }
        val during = card.getUnclippedBoundsInRoot()
        assertTrue(if (left) during.left < start.left else during.left > start.left)
        compose.mainClock.advanceTimeBy(600)
        compose.runOnIdle {
            assertTrue("Dismissal callback must run after the animation", dismissed)
            assertTrue("The whole card must be outside the screen first", fullyOutsideAtDismiss)
        }
        compose.mainClock.advanceTimeBy(2000)
        assertEquals(start.left.value, card.getUnclippedBoundsInRoot().left.value, .5f)
        compose.mainClock.autoAdvance = true
    }
}
