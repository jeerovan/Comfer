package com.jeerovan.comfer

import android.graphics.drawable.ColorDrawable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertEquals

class UShapeCenterSelectionTest {
    @get:Rule val compose = createComposeRule()

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
