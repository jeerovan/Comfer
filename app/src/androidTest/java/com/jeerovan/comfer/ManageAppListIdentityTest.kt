package com.jeerovan.comfer

import android.app.Application
import android.content.ComponentName
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ManageAppListIdentityTest {
    @get:Rule val compose = createComposeRule()
    private val store = ViewModelStore()

    @After fun close() { compose.runOnIdle { store.clear() } }

    @Test fun columnKeepsOneSelectableCardPerPackageAcrossUpdatesAndDirections() = checkList(false)
    @Test fun folderRowKeepsOneSelectableCardPerPackageAcrossUpdatesAndDirections() = checkList(true)

    private fun checkList(folder: Boolean) {
        fun app(label: String, pkg: String, activity: String) = AppInfo(
            null, ColorDrawable(Color.GRAY), label, 1f, pkg, null, ComponentName(pkg, activity), null,
        )
        val first = app("First app", "example.first", "FirstActivity")
        val alias = app("Alias of first", "example.first", "AliasActivity")
        val second = app("Second app", "example.second", "SecondActivity")
        var apps by mutableStateOf(listOf(first, first.copy(), alias, second, second.copy()))
        var direction by mutableStateOf(LayoutDirection.Ltr)
        var wide by mutableStateOf(false)
        val selected = mutableListOf<Pair<String, String>>()
        compose.setContent {
            val model = remember {
                AppInfoViewModel(InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application)
                    .also { store.put("apps", it) }
            }
            CompositionLocalProvider(LocalLayoutDirection provides direction) {
                MaterialTheme {
                    Box(Modifier.size(if (wide) 640.dp else 280.dp, if (wide) 280.dp else 520.dp)) {
                        if (folder) {
                            FolderAppListRow(apps, "folder_test", model, null, emptySet(),
                                40.dp, CircleShape, { list, pkg -> selected += list to pkg })
                        } else {
                            AppListColumn("Apps", apps, true, rememberLazyListState(),
                                listName = AppInfoManager.REST_APPS_LIST_NAME, viewModel = model,
                                selectedList = null, selectedPackageNames = emptySet(), iconSize = 40.dp,
                                iconShape = CircleShape, onItemSelect = { list, pkg -> selected += list to pkg },
                                onAddFolderClick = {}, folders = 0)
                        }
                    }
                }
            }
        }
        for (layout in listOf(LayoutDirection.Ltr, LayoutDirection.Rtl)) {
            for (horizontal in listOf(false, true)) {
                compose.runOnIdle {
                    direction = layout
                    wide = horizontal
                    apps = listOf(first, first.copy(), alias, second, second.copy())
                }
                compose.onAllNodesWithContentDescription(first.label).assertCountEquals(1)
                compose.onAllNodesWithContentDescription(alias.label).assertCountEquals(0)
                val firstNode = compose.onNodeWithContentDescription(first.label)
                val secondNode = compose.onNodeWithContentDescription(second.label)
                firstNode.assertIsDisplayed()
                secondNode.assertIsDisplayed()
                val a = firstNode.fetchSemanticsNode().boundsInRoot
                val b = secondNode.fetchSemanticsNode().boundsInRoot
                assertTrue(a.width > 0 && a.height > 0 && b.width > 0 && b.height > 0)
                if (folder) {
                    assertTrue(if (layout == LayoutDirection.Ltr) a.right <= b.left else b.right <= a.left)
                } else assertTrue(a.bottom <= b.top)
                val before = selected.size
                firstNode.performClick()
                assertEquals(before + 1, selected.size)
                assertEquals("example.first", selected.last().second)
                compose.runOnIdle { apps = listOf(second, alias, first) }
                compose.onAllNodesWithContentDescription(alias.label).assertCountEquals(1)
                compose.onAllNodesWithContentDescription(first.label).assertCountEquals(0)
                compose.runOnIdle { apps = emptyList() }
                compose.onAllNodesWithContentDescription(second.label).assertCountEquals(0)
            }
        }
    }
}
