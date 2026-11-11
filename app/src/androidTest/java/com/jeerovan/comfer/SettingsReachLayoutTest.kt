package com.jeerovan.comfer

import android.content.res.Configuration
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class SettingsReachLayoutTest {
    @get:Rule val compose = createAndroidComposeRule<SettingsActivity>()

    @Test fun categoryPullsIntoReachWithoutMovingBottomBackControl() {
        compose.onNodeWithTag("settings-category-HOME").performClick()
        compose.onNodeWithTag("settings-title").assertTextEquals(compose.activity.getString(R.string.title_home_screen))
        val list = compose.onNodeWithTag("settings-list")
        list.performScrollToIndex(0)
        val first = compose.onNodeWithText(compose.activity.getString(R.string.title_custom_widgets))
        val viewport = list.getUnclippedBoundsInRoot()
        val navigation = compose.onNodeWithTag("settings-navigation").getUnclippedBoundsInRoot()
        val initialTop = first.assertIsDisplayed().getUnclippedBoundsInRoot().top.value
        val boundary = viewport.bottom.value - 360f
        val canPull = compose.activity.resources.configuration.orientation != Configuration.ORIENTATION_LANDSCAPE &&
            boundary > viewport.top.value + 1f && initialTop < boundary
        assertTrue(initialTop >= viewport.top.value)
        list.performTouchInput { swipeDown(startY = height * .1f, endY = height * .9f, durationMillis = 600) }
        val pulledTop = first.assertIsDisplayed().getUnclippedBoundsInRoot().top.value
        if (canPull) assertTrue("First controls enter thumb reach", pulledTop >= boundary - 1f)
        else assertEquals("Landscape/short content does not shift", initialTop, pulledTop, 1f)
        assertEquals(navigation, compose.onNodeWithTag("settings-navigation").getUnclippedBoundsInRoot())
        compose.onNodeWithTag("settings-back").assertIsDisplayed()
        if (canPull) {
            list.performTouchInput { swipeUp(startY = height * .8f, endY = height * .55f, durationMillis = 500) }
            assertTrue(first.getUnclippedBoundsInRoot().top.value < pulledTop)
        }
        compose.onNodeWithTag("settings-back").performClick()
        compose.onNodeWithTag("settings-category-HOME").assertIsDisplayed()
    }
}
