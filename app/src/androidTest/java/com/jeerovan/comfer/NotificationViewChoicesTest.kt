package com.jeerovan.comfer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import android.graphics.Bitmap
import androidx.test.platform.app.InstrumentationRegistry
import com.jeerovan.comfer.notifications.NotificationViewChoices
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertTrue

class NotificationViewChoicesTest {
    @get:Rule val compose = createComposeRule()

    @Test fun choicesRemainVisibleInOneRowAndSelectionIsExplicit() {
        compose.setContent {
            var chronological by remember { mutableStateOf(false) }
            MaterialTheme {
                Box(Modifier.width(280.dp)) {
                    NotificationViewChoices(chronological) { chronological = it }
                }
            }
        }
        val grouped = compose.onNodeWithText("Grouped by app")
        val chronological = compose.onNodeWithText("Chronological")
        grouped.assertIsDisplayed().assertIsSelected()
        chronological.assertIsDisplayed().assertIsNotSelected()
        val groupedBounds = grouped.getUnclippedBoundsInRoot()
        val chronologicalBounds = chronological.getUnclippedBoundsInRoot()
        // Wrapped labels can have different touch-target bounds at larger font scales.
        // Both choices must still occupy the same row without overlapping horizontally.
        assertTrue(groupedBounds.top < chronologicalBounds.bottom && chronologicalBounds.top < groupedBounds.bottom)
        assertTrue(groupedBounds.right <= chronologicalBounds.left)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.uiAutomation.takeScreenshot()?.let { bitmap ->
            File(instrumentation.targetContext.cacheDir, "translation-view-choices.png").outputStream().use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            bitmap.recycle()
        }
        chronological.performClick().assertIsSelected()
        grouped.assertIsNotSelected().performClick().assertIsSelected()
        chronological.assertIsNotSelected()
    }
}
