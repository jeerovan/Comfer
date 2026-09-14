package com.jeerovan.comfer

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.jeerovan.comfer.notifications.*
import org.junit.*
import org.junit.Assert.*

class NotificationGestureGuideTest {
    @get:Rule val compose = createComposeRule()
    private val prefs get() = InstrumentationRegistry.getInstrumentation().targetContext.getSharedPreferences("notification_gesture_guides", Context.MODE_PRIVATE)
    private var previous: Map<String, *> = emptyMap<String, Any>()
    @Before fun setup() { previous = prefs.all; prefs.edit().clear().commit(); compose.mainClock.autoAdvance = false }
    @After fun cleanup() { prefs.edit().clear().also { e -> previous.forEach { (k, v) -> if (v is Boolean) e.putBoolean(k, v) } }.commit() }

    @Test fun guidesSequencePersistAndDoNotReplayAfterRemount() {
        var show by mutableStateOf(true)
        compose.setContent { MaterialTheme { if (show) NotificationGuideTarget(listOf(NotificationGuide.CARD_HOLD, NotificationGuide.CARD_SWIPE), true) {
            Card(Modifier.fillMaxWidth().height(160.dp)) { Text("Notification") }
        } } }
        compose.mainClock.advanceTimeBy(100)
        compose.onNodeWithTag("notification-guide-CARD_HOLD").assertIsDisplayed()
        compose.mainClock.advanceTimeBy(6200)
        compose.onNodeWithTag("notification-guide-CARD_SWIPE").assertIsDisplayed()
        compose.mainClock.advanceTimeBy(6200)
        compose.onNodeWithTag("notification-guide-CARD_SWIPE").assertDoesNotExist()
        assertTrue(prefs.getBoolean("CARD_HOLD", false)); assertTrue(prefs.getBoolean("CARD_SWIPE", false))
        compose.runOnIdle { show = false }; compose.mainClock.advanceTimeBy(32)
        compose.runOnIdle { show = true }; compose.mainClock.advanceTimeBy(32)
        compose.onNodeWithTag("notification-guide-CARD_HOLD").assertDoesNotExist()
    }

    @Test fun disabledTargetDoesNotTeachAndSwipeMovesWithoutConsumingTap() {
        var enabled by mutableStateOf(false)
        var taps = 0
        compose.setContent { MaterialTheme { NotificationGuideTarget(listOf(NotificationGuide.RULE_SWIPE), enabled) {
            Card(Modifier.fillMaxWidth().height(160.dp).testTag("target").clickable { taps++ }) { Text("Rule") }
        } } }
        compose.mainClock.advanceTimeBy(6500)
        assertFalse(prefs.getBoolean("RULE_SWIPE", false))
        compose.onNodeWithTag("notification-guide-RULE_SWIPE").assertDoesNotExist()
        compose.runOnIdle { enabled = true }; compose.mainClock.advanceTimeBy(100)
        val before = compose.onNodeWithTag("notification-guide-RULE_SWIPE").getUnclippedBoundsInRoot().left
        compose.mainClock.advanceTimeBy(1000)
        val after = compose.onNodeWithTag("notification-guide-RULE_SWIPE").getUnclippedBoundsInRoot().left
        assertTrue(after < before)
        compose.onNodeWithTag("target").performTouchInput { click() }
        compose.runOnIdle { assertEquals(1, taps) }
    }
}
