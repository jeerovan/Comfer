package com.jeerovan.comfer

import android.graphics.drawable.ColorDrawable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class WorkspaceMenuTest {
    @get:Rule val compose = createComposeRule()
    @Test fun circularUsesFolderMotionAndReverses() = verify(true)
    @Test fun columnsUseFolderMotionAndReverse() = verify(false)
    @Test fun circularRtlUsesFolderMotionAndReverses() = verify(true, true)
    @Test fun columnsRtlUseFolderMotionAndReverse() = verify(false, true)

    private fun verify(circular: Boolean, rtl: Boolean = false) {
        val folder = AppInfo(null, ColorDrawable(-1), "Folder", 1f, "folder_parent", null, null, null)
        val home = folder.copy(label = "Home app", packageName = "home_app")
        val child = home.copy(label = "Folder child", packageName = "folder_child")
        var activeFolder by mutableStateOf<String?>(null)
        var opened by mutableStateOf(false)
        var pending by mutableStateOf<WorkspaceModule?>(null)
        val launched = mutableListOf<WorkspaceModule>()
        compose.setContent { CompositionLocalProvider(LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr) { MaterialTheme {
            HomeFolderLayout(listOf(folder, home), mapOf(folder.packageName to listOf(child)), activeFolder,
                circular, emptyList(), 48.dp, CircleShape,
                { if (pending == null) { if (activeFolder != null) activeFolder = null else opened = !opened } },
                false, null, false, { activeFolder = it; opened = false }, workspaceOpen = opened,
                onModuleSelected = { if (opened && pending == null) { pending = it; opened = false } },
                onClosed = { pending?.let { launched += it; pending = null } })
        } } }
        fun homeBounds() = compose.onNodeWithContentDescription("Home app").fetchSemanticsNode().boundsInRoot
        fun moduleBounds() = compose.onNodeWithTag("workspace-module-journal").fetchSemanticsNode().boundsInRoot
        val original = homeBounds()
        val center = compose.onNodeWithContentDescription("Workspace").fetchSemanticsNode().boundsInRoot.center
        compose.mainClock.autoAdvance = false
        compose.onNodeWithContentDescription("Workspace").performTouchInput { click() }
        compose.mainClock.advanceTimeBy(128)
        assertTrue(homeBounds().width < original.width)
        assertTrue((homeBounds().center - original.center).getDistance() < 1f)
        val expanding = moduleBounds()
        compose.mainClock.advanceTimeBy(400)
        val expanded = moduleBounds()
        assertTrue((expanded.center - center).getDistance() > (expanding.center - center).getDistance())
        assertTrue(expanded.width > expanding.width)
        compose.onNodeWithContentDescription("Home app").assertDoesNotExist()
        compose.onNodeWithContentDescription("Close").performTouchInput { click() }
        compose.mainClock.advanceTimeBy(128)
        assertTrue((moduleBounds().center - center).getDistance() < (expanded.center - center).getDistance())
        assertTrue((homeBounds().center - original.center).getDistance() < 1f)
        assertTrue(homeBounds().width < original.width)
        compose.mainClock.advanceTimeBy(400)
        assertEquals(original, homeBounds())
        compose.onNodeWithTag("workspace-module-journal").assertDoesNotExist()
        // Rapid reversal must settle with just one center control and no orphan module icons.
        compose.onNodeWithContentDescription("Workspace").performTouchInput { click() }
        compose.mainClock.advanceTimeBy(80)
        compose.onNodeWithContentDescription("Close").performTouchInput { click() }
        compose.mainClock.advanceTimeBy(500)
        compose.onAllNodesWithTag("home-search-button").assertCountEquals(1)
        assertEquals(original, homeBounds())
        // Ordinary folders still open/close before another workspace launch.
        compose.onNodeWithContentDescription("Folder").performTouchInput { click() }
        compose.mainClock.advanceTimeBy(500)
        compose.onNodeWithContentDescription("Folder child").assertIsDisplayed()
        compose.onNodeWithContentDescription("Close").performTouchInput { click() }
        compose.mainClock.advanceTimeBy(500)
        for (module in WorkspaceModule.entries) {
            compose.onNodeWithContentDescription("Workspace").performTouchInput { click() }
            compose.mainClock.advanceTimeBy(500)
            compose.onNodeWithTag("workspace-module-${module.name.lowercase()}").performTouchInput { doubleClick() }
            compose.mainClock.advanceTimeBy(128)
            assertFalse("Launch waits for closing animation", module in launched)
            compose.mainClock.advanceTimeBy(500)
            compose.runOnIdle { assertEquals("One launch after closing: $module", 1, launched.count { it == module }) }
            assertEquals(original, homeBounds())
        }
    }
}
