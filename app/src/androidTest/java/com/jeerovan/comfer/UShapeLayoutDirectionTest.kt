package com.jeerovan.comfer

import android.graphics.drawable.ColorDrawable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class UShapeLayoutDirectionTest(private val direction: LayoutDirection) {
    @get:Rule val compose = createComposeRule()

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun directions() = listOf(arrayOf(LayoutDirection.Ltr), arrayOf(LayoutDirection.Rtl))
    }

    @Test fun selectedIconKeepsAbsoluteBoundsAndTapTargetAcrossSizesAndScroll() {
        var iconSize by mutableStateOf(48.dp)
        var hostHeight by mutableStateOf(640.dp)
        var position by mutableFloatStateOf(0f)
        var selected = -1
        var expected = Rect.Zero
        var tapped: String? = null
        val apps = List(50) { index ->
            AppInfo(null, ColorDrawable(android.graphics.Color.GRAY), "تطبيق $index",
                1f, "folder_$index", null, null, null)
        }
        compose.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides direction) {
                MaterialTheme {
                    Box(Modifier.size(360.dp, hostHeight).testTag("drawer-host")) {
                        UshapedAppList(apps, emptyList(), { selected = it }, position,
                            iconSize, CircleShape, { x, y, size ->
                                expected = Rect(x - size / 2, y - size / 2, x + size / 2, y + size / 2)
                            }, onTappingFolder = { tapped = it })
                    }
                }
            }
        }
        // Include the reported icon size, a smaller icon, and a short viewport.
        for ((size, height) in listOf(48.dp to 640.dp, 32.dp to 640.dp, 32.dp to 260.dp)) {
            compose.runOnIdle { iconSize = size; hostHeight = height }
            for (offset in listOf(0f, .03f, 8f, 12f, -12f, 20f, -20f)) {
                compose.runOnIdle { position = offset; tapped = null }
                compose.waitForIdle()
                val host = compose.onNodeWithTag("drawer-host").fetchSemanticsNode().boundsInRoot
                val icon = compose.onNodeWithContentDescription("تطبيق $selected")
                icon.assertIsDisplayed()
                val actual = icon.fetchSemanticsNode().boundsInRoot
                val context = "$direction / $size / height=$height / offset=$offset"
                // Compare absolute local bounds, not motion deltas: an RTL origin
                // shift can preserve every delta while drawing the arc off-screen.
                assertEquals("Left: $context", host.left + expected.left, actual.left, .01f)
                assertEquals("Top: $context", host.top + expected.top, actual.top, .01f)
                assertEquals("Width: $context", expected.width, actual.width, .01f)
                assertEquals("Height: $context", expected.height, actual.height, .01f)
                if (offset == 0f) {
                    assertEquals("Centered: $context", host.center.x, actual.center.x, .01f)
                }
                icon.performTouchInput { click() }
                compose.runOnIdle { assertEquals("Tap: $context", "folder_$selected", tapped) }
            }
        }
    }
}
