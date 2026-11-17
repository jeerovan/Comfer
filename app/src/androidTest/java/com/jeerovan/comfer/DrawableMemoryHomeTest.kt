package com.jeerovan.comfer

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Default Home and real UI, with only an inventory drawable replaced to induce the fault. */
class DrawableMemoryHomeTest {
    @get:Rule val compose = createEmptyComposeRule()
    @Test fun failedQuickIconKeepsHomeUsableAndNewDrawableRecovers() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var model: AppInfoViewModel
            scenario.onActivity {
                check(it.packageName.endsWith(".notificationtest"))
                model = ViewModelProvider(it)[AppInfoViewModel::class.java]
            }
            compose.waitUntil(60_000) {
                StartupCoordinator.isReady && PerformanceTrace.appRefreshStats().active == 0 && model.uiState.value.quickApps.isNotEmpty()
            }
            // A zero active count can occur while Home's initial reload is still
            // debouncing. Wait for the real refresh generation and visible
            // inventory to settle before replacing a test-owned in-memory icon.
            val generation = AppInfoViewModel::class.java.getDeclaredField("refreshGeneration").let {
                it.isAccessible = true
                it.get(model) as java.util.concurrent.atomic.AtomicLong
            }
            var lastGeneration = generation.get()
            var quietSince = android.os.SystemClock.elapsedRealtime()
            compose.waitUntil(30_000) {
                val now = android.os.SystemClock.elapsedRealtime()
                val currentGeneration = generation.get()
                val firstLabel = model.uiState.value.quickApps.firstOrNull()?.label
                val visible = firstLabel != null && compose.onAllNodesWithContentDescription(firstLabel)
                    .fetchSemanticsNodes().isNotEmpty() &&
                    compose.onAllNodesWithContentDescription(firstLabel)[0].isDisplayed()
                if (currentGeneration != lastGeneration || PerformanceTrace.appRefreshStats().active != 0 || !visible) {
                    lastGeneration = currentGeneration
                    quietSince = now
                }
                now - quietSince >= 1_000 // Covers the production 300 ms debounce; no fixed refresh count.
            }
            @Suppress("UNCHECKED_CAST")
            val state = AppInfoViewModel::class.java.getDeclaredField("_uiState").let {
                it.isAccessible = true
                it.get(model) as MutableStateFlow<AppInfoUiState>
            }
            val original = state.value
            val failing = MemoryFailureDrawable()
            val item = original.quickApps.first().copy(background = null, foreground = failing, label = "Memory test icon")
            try {
                compose.runOnIdle { state.value = original.copy(quickApps = listOf(item)) }
                val icon = compose.onNodeWithContentDescription(item.label)
                try {
                    compose.waitUntil(10_000) { icon.isDisplayed() }
                } catch (failure: Throwable) {
                    runCatching {
                        println("Memory icon draws=${failing.draws}; stillInjected=${state.value.quickApps.any { it.foreground === failing }}; refresh=${PerformanceTrace.appRefreshStats()}")
                    }.exceptionOrNull()?.let(failure::addSuppressed)
                    throw failure
                }
                icon.assertIsDisplayed()
                val bounds = icon.fetchSemanticsNode().boundsInRoot
                compose.runOnIdle { assertEquals("No repeated allocation attempts for the same source", 1, failing.draws) }
                compose.onNodeWithContentDescription("Workspace").performClick()
                compose.onNodeWithTag("workspace-module-search").assertIsDisplayed()
                Espresso.pressBack()
                compose.onNodeWithTag("home-gesture-surface").assertIsDisplayed()
                compose.runOnIdle { state.value = original.copy(quickApps = listOf(item.copy(foreground = ColorDrawable(Color.RED)))) }
                icon.assertIsDisplayed()
                assertEquals("Fallback does not change icon geometry", bounds, icon.fetchSemanticsNode().boundsInRoot)
                val pixels = icon.captureToImage().toPixelMap()
                val center = pixels[pixels.width / 2, pixels.height / 2]
                assertTrue("A new healthy source must render after failure", center.red > .9f && center.green < .1f && center.blue < .1f)
            } finally { compose.runOnIdle { state.value = original } }
            scenario.recreate()
            compose.waitUntil(30_000) { compose.onAllNodesWithContentDescription("Workspace").fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty() }
            compose.onAllNodesWithContentDescription("Memory test icon").assertCountEquals(0)
            compose.onNodeWithContentDescription("Workspace").performClick()
            compose.onNodeWithTag("workspace-module-search").assertIsDisplayed()
            Espresso.pressBack()
        }
    }
}
