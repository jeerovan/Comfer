package com.jeerovan.comfer

import android.content.res.Configuration
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.jeerovan.comfer.notifications.reachableHeightDp
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class SettingsReachLayoutTest {
    @get:Rule val compose = createAndroidComposeRule<SettingsActivity>()

    @Test fun settingsStartsAtTopPullsIntoReachAndUsesSystemBack() {
        // The first-run guide pulses; freeze its animation while inspecting layout.
        compose.mainClock.autoAdvance = false
        compose.mainClock.advanceTimeBy(1000)
        // AppCompat can give the Activity a different effective locale from
        // targetContext. Native window startup also runs outside this clock.
        fun currentHeading() = compose.onNodeWithText(
            compose.activity.getString(R.string.more_from_jeerovan), ignoreCase = true)
        compose.waitUntil(10_000) {
            compose.mainClock.advanceTimeBy(32)
            runCatching { currentHeading().assertIsDisplayed() }.isSuccess
        }
        val heading = currentHeading()
        val viewport = compose.onNodeWithTag("settings-viewport").getUnclippedBoundsInRoot()
        val boundary = viewport.bottom.value - reachableHeightDp((viewport.bottom - viewport.top).value)
        val initialTop = heading.assertIsDisplayed().getUnclippedBoundsInRoot().top.value
        // Landscape deliberately has no pull-into-reach offset. A short
        // viewport can also be entirely reachable without moving the list.
        val canPullIntoReach = compose.activity.resources.configuration.orientation !=
            Configuration.ORIENTATION_LANDSCAPE && boundary > viewport.top.value + 1f
        assertTrue("The initial heading must be inside the safe viewport", initialTop >= viewport.top.value)
        if (canPullIntoReach) assertTrue(initialTop < boundary)
        compose.onNodeWithTag("settings-list").performTouchInput { swipeDown(startY = height * .1f, endY = height * .9f, durationMillis = 600) }
        compose.mainClock.advanceTimeBy(600)
        val pulledTop = heading.assertIsDisplayed().getUnclippedBoundsInRoot().top.value
        if (canPullIntoReach) assertTrue(pulledTop >= boundary - 1f)
        else assertEquals("A fully reachable layout must not add a pull-down gap", initialTop, pulledTop, 1f)
        compose.onNodeWithTag("settings-back").assertDoesNotExist()
        if (canPullIntoReach) {
            compose.onNodeWithTag("settings-list").performTouchInput {
                swipeUp(startY = height * .8f, endY = height * .65f, durationMillis = 500)
            }
            compose.mainClock.advanceTimeBy(500)
            assertTrue(heading.getUnclippedBoundsInRoot().top.value < boundary)
        }
        val activity = compose.activity
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
        compose.waitUntil(5000) { activity.isFinishing }
        assertTrue(activity.isFinishing)
    }
}
