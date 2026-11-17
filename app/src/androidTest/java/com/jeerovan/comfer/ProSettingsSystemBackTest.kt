package com.jeerovan.comfer

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** The default Home must create the settings panel and own its Back hierarchy. */
class ProSettingsSystemBackTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    private fun systemBack() {
        instrumentation.uiAutomation.executeShellCommand("input keyevent 4").use {
            android.os.ParcelFileDescriptor.AutoCloseInputStream(it).bufferedReader().readText()
        }
        compose.waitForIdle()
    }

    private fun withHome(block: (ActivityScenario<MainActivity>, SettingsViewModel, String, String) -> Unit) = runBlocking {
        val context = instrumentation.targetContext
        check(context.packageName.endsWith(".notificationtest"))
        StartupCoordinator.awaitReady()
        // Only unrelated guide/feedback and built-in widget prerequisites are seeded.
        // Opening the panel and registering Back must happen through real Home.
        val seed = (HomeGuideStep.entries.map { it.preferenceKey } + PreferenceManager.FEEDBACK_DIALOG)
            .associateWith { "true" } + mapOf("analog_clock" to "false", "custom_widgets" to "false")
        val saved = (seed.keys + listOf("time_angle", "date_angle")).associateWith {
            PreferenceManager.getString(context, it, null)
        }
        suspend fun awaitValues(values: Map<String, String?>) = withTimeout(10_000) {
            context.settingsDataStore.data.first { preferences ->
                val stored = preferences.asMap().entries.associate { it.key.name to it.value.toString() }
                values.all { (key, value) -> stored[key] == value }
            }
        }
        try {
            seed.forEach { (key, value) -> PreferenceManager.setString(context, key, value) }
            awaitValues(seed)
            PreferenceManager.reload(context)
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                lateinit var model: SettingsViewModel
                scenario.onActivity { model = ViewModelProvider(it)[SettingsViewModel::class.java] }
                block(scenario, model, context.getString(R.string.icon_go_back), context.getString(R.string.title_advanced))
            }
        } finally {
            saved.forEach { (key, value) -> PreferenceManager.setString(context, key, value) }
            awaitValues(saved)
            PreferenceManager.reload(context)
        }
    }

    private fun openSettings(backLabel: String) {
        compose.waitUntil(20_000) {
            compose.onAllNodesWithTag("home-gesture-surface")
                .fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
        }
        val density = instrumentation.targetContext.resources.displayMetrics.density
        // Blank upper widget pane, not the lower Home shortcut surface.
        compose.onRoot().performTouchInput { longClick(Offset(center.x, 36f * density)) }
        compose.onNodeWithContentDescription(backLabel).assertIsDisplayed()
        compose.onNodeWithTag("home-gesture-surface").assertDoesNotExist()
    }

    @Test fun systemBackClosesRootAndPanelReopensAfterRecreation() = withHome { scenario, _, back, _ ->
        repeat(2) {
            openSettings(back)
            systemBack()
            compose.onNodeWithContentDescription(back).assertDoesNotExist()
            compose.onNodeWithTag("home-gesture-surface").assertIsDisplayed()
            scenario.recreate()
        }
    }

    @Test fun systemBackReturnsBothChildrenToBasicAndPreservesEdits() = withHome { _, model, back, advanced ->
        openSettings(back)
        for (index in 0..1) {
            compose.onAllNodesWithText(advanced)[index].performScrollTo().performClick()
            compose.onAllNodesWithText(advanced).assertCountEquals(0)
            val angle = compose.onNode(SemanticsMatcher("Angle slider") {
                it.config.getOrNull(SemanticsProperties.ProgressBarRangeInfo)?.range == 0f..360f
            })
            angle.performScrollTo().performSemanticsAction(SemanticsActions.SetProgress) { it(78f) }
            compose.waitUntil(10_000) { (if (index == 0) model.uiState.value.timeAngle else model.uiState.value.dateAngle) == 78 }
            systemBack()
            compose.onAllNodesWithText(advanced).assertCountEquals(2)
            compose.onNodeWithContentDescription(back).assertIsDisplayed()
            compose.onAllNodesWithText(advanced)[index].performScrollTo().performClick()
            assertEquals(78f, angle.fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo].current, 0f)
            // Existing on-screen Back must keep the same destination.
            compose.onNodeWithContentDescription(back).performClick()
            compose.onAllNodesWithText(advanced).assertCountEquals(2)
        }
        systemBack()
        compose.onNodeWithTag("home-gesture-surface").assertIsDisplayed()
    }

    @Test fun systemBackDismissesDialogBeforeItsSettingsParent() = withHome { _, _, back, _ ->
        openSettings(back)
        val fontLabel = instrumentation.targetContext.getString(R.string.title_font_style)
        compose.onAllNodesWithText(fontLabel)[0].performScrollTo().performClick()
        compose.onNode(isDialog()).assertExists()
        systemBack()
        compose.onNode(isDialog()).assertDoesNotExist()
        compose.onNodeWithContentDescription(back).assertIsDisplayed()
        systemBack()
        compose.onNodeWithTag("home-gesture-surface").assertIsDisplayed()
    }
}
