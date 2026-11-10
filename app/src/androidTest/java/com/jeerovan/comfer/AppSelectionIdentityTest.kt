package com.jeerovan.comfer

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.UserHandle
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class AppSelectionIdentityTest {
    @get:Rule val compose = createEmptyComposeRule()

    private fun intent() = Intent(
        InstrumentationRegistry.getInstrumentation().targetContext,
        AppSelectionActivity::class.java,
    ).putExtra("swipe_direction", "left").putExtra("gesture_pattern", "Center")

    private fun awaitInventory(scenario: ActivityScenario<AppSelectionActivity>): AppInfoViewModel {
        lateinit var model: AppInfoViewModel
        scenario.onActivity { model = ViewModelProvider(it)[AppInfoViewModel::class.java] }
        compose.waitUntil(60_000) {
            StartupCoordinator.isReady && PerformanceTrace.appRefreshStats().active == 0 &&
                model.uiState.value.let { it.quickApps + it.primaryApps + it.restApps }.isNotEmpty()
        }
        return model
    }

    @Test fun overlappingInventoryGroupsKeepDistinctActivitiesAndProfiles() {
        ActivityScenario.launch<AppSelectionActivity>(intent()).use { scenario ->
            val model = awaitInventory(scenario)
            // Reproduce the intermediate overlap produced by separate quick/primary/rest
            // publications. This is fault injection, not evidence of the refresh timing.
            @Suppress("UNCHECKED_CAST")
            val state = AppInfoViewModel::class.java.getDeclaredField("_uiState").let {
                it.isAccessible = true
                it.get(model) as MutableStateFlow<AppInfoUiState>
            }
            fun app(label: String, activity: String, profile: Int) = AppInfo(
                null, ColorDrawable(Color.GRAY), label, 1f, "fixture.app", null,
                ComponentName("fixture.app", activity),
                UserHandle.getUserHandleForUid(profile * 100000),
            )
            val personal = app("A personal", "Main", 0)
            val alias = app("B other activity", "Other", 0)
            val work = app("C work profile", "Main", 10)
            compose.runOnIdle {
                state.value = AppInfoUiState(
                    quickApps = listOf(personal),
                    primaryApps = listOf(personal.copy(), alias),
                    restApps = listOf(work, personal.copy(), alias.copy()),
                )
            }
            listOf(personal, alias, work).forEach {
                compose.onAllNodesWithContentDescription(it.label).assertCountEquals(1)
                compose.onNodeWithContentDescription(it.label).assertIsDisplayed()
            }
            compose.runOnIdle {
                state.value = AppInfoUiState(primaryApps = listOf(work, alias), restApps = listOf(work.copy()))
            }
            compose.onAllNodesWithContentDescription(personal.label).assertCountEquals(0)
            compose.onAllNodesWithContentDescription(work.label).assertCountEquals(1)
            compose.runOnIdle { state.value = AppInfoUiState() }
            compose.onAllNodesWithContentDescription(work.label).assertCountEquals(0)
        }
    }

    @Test fun defaultActivityInventorySurvivesRecreationAndReturnsSelection() {
        ActivityScenario.launchActivityForResult<AppSelectionActivity>(intent()).use { scenario ->
            awaitInventory(scenario)
            scenario.recreate()
            val model = awaitInventory(scenario)
            val selected = model.uiState.value.let { it.quickApps + it.primaryApps + it.restApps }
                .sortedBy { it.label }.first { it.foreground != null }
            compose.onNode(hasScrollAction()).performScrollToNode(hasContentDescription(selected.label))
            compose.onAllNodesWithContentDescription(selected.label).onFirst().performClick()
            val result = scenario.result
            assertEquals(Activity.RESULT_OK, result.resultCode)
            assertEquals(selected.packageName, result.resultData.getStringExtra("package_name"))
            assertEquals("left", result.resultData.getStringExtra("swipe_direction"))
            assertEquals("Center", result.resultData.getStringExtra("gesture_pattern"))
        }
    }
}
