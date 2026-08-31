package com.jeerovan.comfer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class GestureGuideLayoutTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun swipeGuideMatchesPortraitTargetBounds() {
        verifyTargetBounds(width = 240, height = 480)
    }

    @Test
    fun swipeGuideMatchesLandscapeTargetBounds() {
        verifyTargetBounds(width = 480, height = 240)
    }

    @Test
    fun horizontalDrawerGuideIsFiftyDpAboveBottom() {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            Box(
                modifier = Modifier
                    .size(width = 240.dp, height = 480.dp)
                    .testTag(TARGET_TAG),
            ) {
                SwipeHelper(
                    start = SwipeDirection.LEFT,
                    end = SwipeDirection.RIGHT,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(HORIZONTAL_GUIDE_REGION_HEIGHT)
                        .align(androidx.compose.ui.Alignment.BottomCenter)
                        .testTag(GUIDE_TAG),
                    handModifier = Modifier.testTag(HAND_TAG),
                )
            }
        }

        composeRule.onNodeWithTag(GUIDE_TAG)
            .assertHeightIsEqualTo(HORIZONTAL_GUIDE_REGION_HEIGHT)
        val targetBounds = composeRule.onNodeWithTag(TARGET_TAG)
            .fetchSemanticsNode().boundsInRoot
        val guideBounds = composeRule.onNodeWithTag(GUIDE_TAG)
            .fetchSemanticsNode().boundsInRoot
        val handBounds = composeRule.onNodeWithTag(HAND_TAG)
            .fetchSemanticsNode().boundsInRoot

        assertEquals(targetBounds.bottom, guideBounds.bottom, BOUNDS_TOLERANCE_PX)
        assertEquals(guideBounds.center.y, handBounds.center.y, BOUNDS_TOLERANCE_PX)
    }

    private fun verifyTargetBounds(width: Int, height: Int) {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            Box(
                modifier = Modifier
                    .size(width.dp, height.dp)
                    .testTag(TARGET_TAG),
            ) {
                SwipeHelper(
                    start = SwipeDirection.BOTTOM,
                    end = SwipeDirection.TOP,
                    modifier = Modifier
                        .matchParentSize()
                        .testTag(GUIDE_TAG),
                    remainVisibleInsideTarget = true,
                    handModifier = Modifier.testTag(HAND_TAG),
                )
            }
        }

        val targetBounds = composeRule.onNodeWithTag(TARGET_TAG)
            .fetchSemanticsNode().boundsInRoot
        val guideBounds = composeRule.onNodeWithTag(GUIDE_TAG)
            .fetchSemanticsNode().boundsInRoot
        val handBounds = composeRule.onNodeWithTag(HAND_TAG)
            .fetchSemanticsNode().boundsInRoot

        assertEquals(targetBounds.left, guideBounds.left, BOUNDS_TOLERANCE_PX)
        assertEquals(targetBounds.top, guideBounds.top, BOUNDS_TOLERANCE_PX)
        assertEquals(targetBounds.right, guideBounds.right, BOUNDS_TOLERANCE_PX)
        assertEquals(targetBounds.bottom, guideBounds.bottom, BOUNDS_TOLERANCE_PX)
        assertEquals(targetBounds.center.x, handBounds.center.x, BOUNDS_TOLERANCE_PX)
        assertEquals(targetBounds.bottom, handBounds.bottom, BOUNDS_TOLERANCE_PX)
    }

    private companion object {
        const val TARGET_TAG = "gesture-target"
        const val GUIDE_TAG = "target-bound-swipe-guide"
        const val HAND_TAG = "swipe-guide-hand"
        const val BOUNDS_TOLERANCE_PX = 1f
        val HORIZONTAL_GUIDE_REGION_HEIGHT = 100.dp
    }
}
