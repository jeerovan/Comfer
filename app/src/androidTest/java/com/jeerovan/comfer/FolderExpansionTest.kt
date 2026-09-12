package com.jeerovan.comfer

import android.graphics.drawable.ColorDrawable
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class FolderExpansionTest {
    @get:Rule val compose = createComposeRule()

    @Test fun circularIconsExpandAndRemainIndividuallyTappable() = verifyExpansion(true)
    @Test fun columnIconsExpandAndRemainIndividuallyTappable() = verifyExpansion(false)
    @Test fun circularHomeIconsRetreatAndReturn() = verifyHomeTransition(true)
    @Test fun columnHomeIconsRetreatAndReturn() = verifyHomeTransition(false)

    @Test fun drawerIconsConvergeBeforeFolderDisappears() {
        val children = (0..7).map {
            AppInfo(null, ColorDrawable(-1), "Child $it", 1f, "child_$it", null, null, null)
        }
        val active = mutableStateOf<String?>(null)
        compose.setContent {
            MaterialTheme {
                DrawerFolderLayout(active.value, mapOf("folder" to children), emptyList(), 48.dp,
                    CircleShape, { active.value = null }, false, null, false)
            }
        }
        compose.mainClock.autoAdvance = false
        repeat(2) {
            compose.runOnIdle { active.value = "folder" }
            compose.mainClock.advanceTimeBy(600)
            val center = compose.onNodeWithTag("home-search-button").fetchSemanticsNode().boundsInRoot.center
            val before = children.map { compose.onNodeWithContentDescription(it.label).fetchSemanticsNode().boundsInRoot }
            compose.onNodeWithTag("home-search-button").performTouchInput { click() }
            compose.mainClock.advanceTimeBy(240)
            children.forEachIndexed { index, child ->
                val moving = compose.onNodeWithContentDescription(child.label).fetchSemanticsNode().boundsInRoot
                assertTrue("Every icon converges toward Close", (moving.center - center).getDistance() <
                    (before[index].center - center).getDistance())
                assertTrue(moving.width < before[index].width)
            }
            // The last icon ends at 386ms; allow two frames for disposal, not a long tail.
            compose.mainClock.advanceTimeBy(180)
            children.forEach { compose.onNodeWithContentDescription(it.label).assertDoesNotExist() }
            compose.onNodeWithTag("home-search-button").assertDoesNotExist()
        }
    }

    private fun verifyHomeTransition(circular: Boolean) {
        val folder = AppInfo(null, ColorDrawable(-1), "Folder", 1f, "folder_parent", null, null, null)
        val home = folder.copy(label = "Home app", packageName = "home_app")
        val children = (0..7).map { folder.copy(label = "Child $it", packageName = "child_$it") }
        val active = mutableStateOf<String?>(null)
        compose.setContent {
            MaterialTheme {
                HomeFolderLayout(listOf(folder, home), mapOf(folder.packageName to children), active.value,
                    circular, emptyList(), 48.dp, CircleShape, { active.value = null },
                    false, null, false, { active.value = it })
            }
        }
        fun bounds(label: String) = compose.onNodeWithContentDescription(label).fetchSemanticsNode().boundsInRoot
        val homePosition = bounds("Home app")
        compose.mainClock.autoAdvance = false
        repeat(2) {
            compose.onNodeWithContentDescription("Folder").performTouchInput { click() }
            compose.mainClock.advanceTimeBy(128)
            val retreating = bounds("Home app")
            assertTrue(retreating.width < homePosition.width)
            assertTrue("Home icon shrinks around its own center",
                (retreating.center - homePosition.center).getDistance() < 1f)
            compose.mainClock.advanceTimeBy(400)
            compose.onNodeWithContentDescription("Home app").assertDoesNotExist()
            val childPosition = bounds("Child 7")
            compose.onNodeWithTag("home-search-button").performTouchInput { click() }
            compose.mainClock.advanceTimeBy(128)
            assertTrue(bounds("Child 7").width < childPosition.width)
            assertTrue((bounds("Child 7").center - childPosition.center).getDistance() > 1f)
            assertTrue(bounds("Home app").width < homePosition.width)
            assertTrue("Home icon reappears around its own center",
                (bounds("Home app").center - homePosition.center).getDistance() < 1f)
            compose.mainClock.advanceTimeBy(400)
            compose.onNodeWithContentDescription("Child 7").assertDoesNotExist()
            assertEquals(homePosition, bounds("Home app"))
        }
        // Closing before expansion finishes must settle cleanly back at home.
        compose.onNodeWithContentDescription("Folder").performTouchInput { click() }
        compose.mainClock.advanceTimeBy(80)
        compose.onNodeWithTag("home-search-button").performTouchInput { click() }
        compose.mainClock.advanceTimeBy(500)
        compose.onNodeWithContentDescription("Child 7").assertDoesNotExist()
        assertEquals(homePosition, bounds("Home app"))
    }

    private fun verifyExpansion(circular: Boolean) {
        val folder = AppInfo(null, ColorDrawable(-1), "Folder", 1f, "folder_parent", null, null, null)
        // Folder callbacks make individual hit targets observable without launching external apps.
        val children = (0..7).map { folder.copy(label = "Child $it", packageName = "folder_child_$it") }
        val active = mutableStateOf(false)
        var tapped: String? = null
        compose.setContent {
            MaterialTheme {
                val onTap: (String) -> Unit = {
                    if (it == folder.packageName) active.value = true else tapped = it
                }
                if (circular) CircularLayout(
                    if (active.value) children else listOf(folder), emptyList(), 48.dp, CircleShape,
                    { active.value = false }, false, null, false,
                    isFolderActive = active.value, onTappingFolder = onTap, showGestureGuide = false,
                    expansionKey = if (active.value) folder.packageName else null,
                ) else FiveColumnLayout(
                    if (active.value) children else listOf(folder), emptyList(), 48.dp, CircleShape,
                    { active.value = false }, false, null, false,
                    isFolderActive = active.value, onTappingFolder = onTap,
                    expansionKey = if (active.value) folder.packageName else null,
                )
            }
        }
        repeat(2) {
            compose.mainClock.autoAdvance = false
            compose.onNodeWithContentDescription("Folder").performTouchInput { click() }
            compose.mainClock.advanceTimeBy(96)
            val moving = compose.onNodeWithContentDescription("Child 7").fetchSemanticsNode().boundsInRoot
            compose.mainClock.advanceTimeBy(500)
            val settled = compose.onNodeWithContentDescription("Child 7").fetchSemanticsNode().boundsInRoot
            assertTrue("Icon should move out of the folder", (settled.center - moving.center).getDistance() > 1f)
            assertTrue("Icon should grow to full size", settled.width > moving.width)
            children.forEach { child ->
                compose.onNodeWithContentDescription(child.label).assertIsDisplayed().performTouchInput { click() }
                compose.runOnIdle { assertEquals(child.packageName, tapped) }
            }
            compose.runOnIdle { active.value = false }
            compose.mainClock.advanceTimeByFrame()
            compose.onNodeWithContentDescription("Folder").assertIsDisplayed()
        }
    }
}
