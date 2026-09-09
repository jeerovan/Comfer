package com.jeerovan.comfer

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.jeerovan.comfer.notifications.reachableHeightDp
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class SettingsReachLayoutTest {
    @get:Rule val compose = createAndroidComposeRule<SettingsActivity>()

    @Test fun settingsStartReachableScrollAndUseSystemBack() {
        // The first-run guide pulses; freeze its animation while inspecting layout.
        compose.mainClock.autoAdvance = false
        compose.mainClock.advanceTimeBy(1000)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val heading = compose.onNodeWithText(context.getString(R.string.more_from_jeerovan), ignoreCase = true)
        val viewport = compose.onNodeWithTag("settings-viewport").getUnclippedBoundsInRoot()
        val boundary = viewport.bottom.value - reachableHeightDp((viewport.bottom - viewport.top).value)
        assertTrue(heading.getUnclippedBoundsInRoot().top.value >= boundary)
        compose.onNodeWithTag("settings-back").assertDoesNotExist()
        compose.onNodeWithTag("settings-list").performTouchInput {
            swipeUp(startY = height * .8f, endY = height * .65f, durationMillis = 500)
        }
        compose.mainClock.advanceTimeBy(500)
        assertTrue(heading.getUnclippedBoundsInRoot().top.value < boundary)
        val activity = compose.activity
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
        compose.waitUntil(5000) { activity.isFinishing }
        assertTrue(activity.isFinishing)
    }
}
