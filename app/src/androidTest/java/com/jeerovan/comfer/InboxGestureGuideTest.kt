package com.jeerovan.comfer

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class InboxGestureGuideTest {
    @get:Rule val compose = createComposeRule()

    @Test fun handDemonstratesOneContinuousDownAndReturn() {
        compose.mainClock.autoAdvance = false
        compose.setContent { InboxGestureHint() }
        fun hand() = compose.onNodeWithTag("home-inbox-guide-hand").fetchSemanticsNode().boundsInRoot
        val start = hand()
        compose.mainClock.advanceTimeBy(672)
        val bottom = hand()
        assertTrue("Hand should travel down at least one hand size", bottom.top > start.top + start.height)
        compose.mainClock.advanceTimeBy(512)
        val returned = hand()
        assertEquals(start.left, returned.left, 1f)
        assertEquals("Return to the same finger origin", start.top, returned.top, 2f)
    }

    @Test fun circularGuideCrossesSearchWithoutOpeningIt() = verifySearchAnchor(true)

    @Test fun columnGuideCrossesSearchWithoutOpeningIt() = verifySearchAnchor(false)

    private fun verifySearchAnchor(circular: Boolean) {
        var searches = 0
        var inboxes = 0
        var density = 1f
        compose.mainClock.autoAdvance = false
        compose.setContent {
            density = LocalDensity.current.density
            Box(Modifier.fillMaxSize().testTag("guide-home").detectGestures(onInbox = { inboxes++ }),
                contentAlignment = Alignment.BottomCenter) {
                Box(Modifier.padding(bottom = 64.dp)) {
                    if (circular) CircularLayout(emptyList(), emptyList(), 48.dp, CircleShape,
                        { searches++ }, false, null, false, showGestureGuide = false,
                        showInboxGestureGuide = true)
                    else FiveColumnLayout(emptyList(), emptyList(), 48.dp, CircleShape,
                        { searches++ }, false, null, false, showInboxGestureGuide = true)
                }
            }
        }
        val search = compose.onNodeWithTag("home-search-button").fetchSemanticsNode().boundsInRoot
        val start = compose.onNodeWithTag("home-inbox-guide-hand").fetchSemanticsNode().boundsInRoot.center
        assertEquals(search.center.x, start.x, 1f)
        assertTrue("Begin above Search", start.y < search.top)
        compose.mainClock.advanceTimeBy(672)
        val bottom = compose.onNodeWithTag("home-inbox-guide-hand").fetchSemanticsNode().boundsInRoot.center
        assertTrue("Cross Search", bottom.y > search.bottom)
        val home = compose.onNodeWithTag("guide-home").fetchSemanticsNode().boundsInRoot
        assertTrue("Hand clears the bottom navigation area", bottom.y + 24f * density < home.bottom - 24f * density)
        compose.onNodeWithTag("guide-home").performTouchInput {
            down(start)
            moveBy(Offset(0f, 120f * density), 200)
            moveBy(Offset(0f, -120f * density), 200)
            up()
        }
        compose.runOnIdle {
            assertEquals(1, inboxes)
            assertEquals(0, searches)
        }
    }

    @Test fun accessAndSequenceGateTheHintAndHintDoesNotConsumeTheGesture() {
        var access by mutableStateOf(false)
        var completed by mutableStateOf(HomeGuideStep.entries.toSet() - HomeGuideStep.INBOX - HomeGuideStep.CLOCK_LONG_PRESS)
        var opened = 0
        compose.setContent {
            val step = nextHomeGuideStep(completed, hasClock = true, hasNotificationAccess = access)
            Box(Modifier.fillMaxSize().testTag("guide-home").detectGestures(onInbox = {
                opened++
                if (step == HomeGuideStep.INBOX) completed = completed + HomeGuideStep.INBOX
            })) {
                if (step == HomeGuideStep.INBOX) InboxGestureHint()
            }
        }
        compose.onNodeWithTag("home-inbox-guide").assertDoesNotExist()
        compose.runOnIdle { access = true }
        compose.onNodeWithTag("home-inbox-guide").assertDoesNotExist()
        compose.runOnIdle { completed = completed + HomeGuideStep.CLOCK_LONG_PRESS }
        compose.onNodeWithTag("home-inbox-guide").assertIsDisplayed()
        compose.runOnIdle { access = false }
        compose.onNodeWithTag("home-inbox-guide").assertDoesNotExist()
        compose.runOnIdle { access = true }
        val hint = compose.onNodeWithTag("home-inbox-guide").fetchSemanticsNode().boundsInRoot
        val density = hint.width / 48f
        compose.onNodeWithTag("guide-home").performTouchInput {
            down(Offset(hint.center.x, hint.top + 20f * density))
            moveBy(Offset(0f, 40f * density), 200)
            moveBy(Offset(0f, -40f * density), 200)
            up()
        }
        compose.onNodeWithTag("home-inbox-guide").assertIsDisplayed()
        compose.runOnIdle { assertEquals(0, opened) }
        compose.mainClock.advanceTimeBy(10_000)
        compose.onNodeWithTag("home-inbox-guide").assertIsDisplayed()
        compose.onNodeWithTag("guide-home").performTouchInput {
            down(Offset(hint.center.x, hint.top + 20f * density))
            moveBy(Offset(0f, 120f * density), 200)
            moveBy(Offset(0f, -120f * density), 200)
            up()
        }
        compose.runOnIdle { assertEquals(1, opened) }
        compose.onNodeWithTag("home-inbox-guide").assertDoesNotExist()
    }
}
