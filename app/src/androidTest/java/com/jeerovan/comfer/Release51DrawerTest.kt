package com.jeerovan.comfer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test

class Release51DrawerTest {
    @get:Rule val compose = createComposeRule()

    @Test fun gridKeepsStableKeysAcrossScrollReorderAndInventoryReplacement() {
        val original = List(80) { i -> AppInfo(
            null, android.graphics.drawable.ColorDrawable(android.graphics.Color.GRAY),
            "App $i", 1f, "folder_$i", null, null, null,
        ) }
        var apps by mutableStateOf(original)
        compose.setContent {
            MaterialTheme {
                Box(Modifier.size(360.dp, 640.dp)) {
                    AppDrawer(
                        isEditMode = false, apps = apps, canReOrder = true,
                        notificationPackages = emptyList(), onAppsReordered = { _, _ -> },
                        initialHeight = 180.dp, initialOffsetY = 60.dp,
                        horizontalSpacing = 8.dp, verticalSpacing = 8.dp,
                        enterEditMode = {}, exitEditMode = {}, iconSize = 32.dp,
                        iconShape = CircleShape, showAppTitles = false,
                    )
                }
            }
        }
        repeat(8) { iteration ->
            compose.onNode(hasScrollAction()).performScrollToIndex(30)
            compose.runOnIdle {
                apps = if (iteration % 2 == 0) original.reversed() else original.drop(10)
            }
            compose.waitForIdle()
            compose.onNode(hasScrollAction()).performScrollToIndex(0)
            compose.waitForIdle()
            compose.onNodeWithContentDescription(apps.first().label).assertIsDisplayed()
        }
    }
}
