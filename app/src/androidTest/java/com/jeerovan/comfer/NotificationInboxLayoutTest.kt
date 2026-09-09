package com.jeerovan.comfer

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.jeerovan.comfer.notifications.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*

class NotificationInboxLayoutTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var previous: NotificationConfiguration
    @Before fun setup() = runBlocking {
        NotificationPreferences.initialize(InstrumentationRegistry.getInstrumentation().targetContext)
        previous = NotificationPreferences.state.value
        NotificationPreferences.update { NotificationConfiguration(setup = true) }
        Unit
    }
    @After fun cleanup() = runBlocking { NotificationPreferences.update { previous }; Unit }
    @Test fun emptyVisibleHomeEntryOpensReachableInbox() {
        // A granted listener can contain Android's own notifications. Exercise an empty
        // visible projection without assuming the entire device's notification shade is empty.
        runBlocking {
            NotificationPreferences.update { configuration -> configuration.copy(
                hiddenUntil = MyNotificationListenerService.snapshot.value.items.associate { it.appId to Long.MAX_VALUE },
            ) }
        }
        compose.setContent { MaterialTheme { NotificationHomeEntry(24.dp, true) } }
        compose.onNodeWithContentDescription("Notification inbox").assertIsDisplayed().assertHasClickAction()
    }
    @Test fun bottomNavigationAndConfigurationFitCompactPortrait() {
        compose.setContent { MaterialTheme { NotificationInbox(onBack = {}) } }
        compose.onNodeWithContentDescription("Settings").assertIsDisplayed().performClick()
        compose.onNodeWithText("Pause automation").assertIsDisplayed().performClick()
        compose.onNodeWithText("Resume automation").assertIsDisplayed()
        compose.onNodeWithContentDescription("Back").assertDoesNotExist()
        val heading = compose.onNodeWithText("Notification inbox").getUnclippedBoundsInRoot()
        val back = compose.onNodeWithContentDescription("All apps").getUnclippedBoundsInRoot()
        assertTrue("Inbox must be in bottom portion", heading.top.value > 150f)
        assertTrue("Navigation must be below content", back.top > heading.bottom)
        InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()?.let { bitmap ->
            val file = java.io.File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "notification-portrait.png")
            file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    @Test fun enlargedTextKeepsControlsInsideFullHeightViewport() {
        compose.setContent {
            val density = androidx.compose.ui.platform.LocalDensity.current
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(density.density, 2f),
            ) { MaterialTheme { NotificationInbox(onBack = {}) } }
        }
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithTag("notification-inbox-list").performScrollToNode(hasText("Pause automation"))
        val panel = compose.onNodeWithTag("notification-inbox-panel").fetchSemanticsNode().boundsInRoot
        compose.onAllNodes(hasClickAction()).fetchSemanticsNodes().forEach { node ->
            val bounds = node.boundsInRoot
            if (bounds.width > 0 && bounds.height > 0) {
                assertTrue("Action outside safe viewport", bounds.top >= panel.top)
                assertTrue("Action below panel", bounds.bottom <= panel.bottom)
            }
        }
        compose.onNodeWithContentDescription("Back").assertDoesNotExist()
        compose.onNodeWithText("Pause automation").performClick()
        compose.onNodeWithText("Resume automation").assertIsDisplayed()
    }

    @Test fun fullHeightViewportStartsReachableAndScrollsAboveStartingBoundary() {
        compose.setContent { MaterialTheme { NotificationInbox(onBack = {}) } }
        compose.onNodeWithContentDescription("Settings").performClick()
        val panel = compose.onNodeWithTag("notification-inbox-panel").getUnclippedBoundsInRoot()
        val root = compose.onRoot().getUnclippedBoundsInRoot()
        assertTrue("Portrait fills safe height", (panel.bottom - panel.top).value > (root.bottom - root.top).value * .8f)
        val heading = compose.onNodeWithText("Notification inbox").getUnclippedBoundsInRoot()
        val boundary = panel.bottom.value - reachableHeightDp((panel.bottom - panel.top).value)
        assertTrue("First element starts within reach", heading.top.value >= boundary - 1f)
        val back = compose.onNodeWithContentDescription("All apps").getUnclippedBoundsInRoot()
        compose.onNodeWithTag("notification-inbox-list").performTouchInput {
            swipeUp(startY = height * .8f, endY = height * .65f, durationMillis = 500)
        }
        val moved = compose.onNodeWithText("Notification inbox").getUnclippedBoundsInRoot()
        assertTrue("Content moves into upper viewport", moved.top.value < boundary)
        assertEquals(back, compose.onNodeWithContentDescription("All apps").getUnclippedBoundsInRoot())
        compose.onNodeWithContentDescription("All apps").performClick()
        compose.waitUntil(5000) {
            compose.onNodeWithText("Notification inbox").getUnclippedBoundsInRoot().top.value >= boundary - 1f
        }
    }

}
