package com.jeerovan.comfer

import android.graphics.drawable.ColorDrawable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertEquals

class UShapeCenterSelectionTest {
    @get:Rule val compose = createComposeRule()

    @Test fun tinyScrollStepsPreserveRenderedPositionAndSizeInBothDirections() {
        var position by mutableFloatStateOf(0f)
        var selected = -1
        var expected = Rect.Zero
        var tapped: String? = null
        val apps = List(30) { index ->
            AppInfo(null, ColorDrawable(android.graphics.Color.GRAY), "App $index",
                1f, "folder_$index", null, null, null)
        }
        compose.setContent {
            MaterialTheme {
                Box(Modifier.size(360.dp, 640.dp)) {
                    UshapedAppList(apps, emptyList(), { selected = it }, position,
                        32.dp, CircleShape, { x, y, size ->
                            expected = Rect(x - size / 2, y - size / 2, x + size / 2, y + size / 2)
                        }, onTappingFolder = { tapped = it })
                }
            }
        }
        // Compare deltas so the host's window insets do not affect the assertion.
        val initial = compose.onNodeWithContentDescription("App $selected").fetchSemanticsNode().boundsInRoot
        val initialExpected = expected
        for (offset in listOf(.01f, .02f, .03f, -.01f, -.02f, -.03f, 0f, 8f, 12f, 20f, -8f, -12f)) {
            compose.runOnIdle { position = offset }
            compose.waitForIdle()
            val actual = compose.onNodeWithContentDescription("App $selected").fetchSemanticsNode().boundsInRoot
            assertEquals("Horizontal subpixel motion at $offset", expected.left - initialExpected.left,
                actual.left - initial.left, .002f)
            assertEquals("Vertical subpixel motion at $offset", expected.top - initialExpected.top,
                actual.top - initial.top, .002f)
            assertEquals("Subpixel scaling at $offset", expected.width, actual.width, .002f)
        }
        compose.onNodeWithContentDescription("App $selected").performTouchInput { click() }
        compose.runOnIdle { assertEquals("folder_$selected", tapped) }
    }

    @Test fun nearestCenterIconUpdatesThroughoutTheTransitionInBothDirections() {
        var position by mutableFloatStateOf(0f)
        var selected = -1
        val apps = List(30) { index ->
            AppInfo(null, null, "App $index", 1f, "fixture.$index", ColorDrawable(android.graphics.Color.GRAY), null, null)
        }
        compose.setContent {
            MaterialTheme {
                Box(Modifier.size(360.dp, 640.dp)) {
                    UshapedAppList(apps, emptyList(), { selected = it }, position, 32.dp, CircleShape, { _, _, _ -> })
                }
            }
        }
        var initial = -1
        compose.runOnIdle { initial = selected }
        fun check(offset: Float, expected: Int) {
            compose.runOnIdle { position = offset }
            compose.waitForIdle()
            compose.runOnIdle { assertEquals(wrapAppIndex(expected.toLong(), apps.size), selected) }
        }
        check(8f, initial)
        check(12f, initial + 1)
        check(20f, initial + 1)
        check(-8f, initial)
        check(-12f, initial - 1)
    }
}
