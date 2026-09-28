package com.jeerovan.comfer

import android.app.Application
import android.content.res.Configuration
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class WidgetResetTest(private val direction: LayoutDirection) {
    @get:Rule val compose = createComposeRule()
    companion object {
        @JvmStatic @Parameterized.Parameters(name = "{0}")
        fun directions() = listOf(arrayOf(LayoutDirection.Ltr), arrayOf(LayoutDirection.Rtl))
    }

    @Test fun resetActionRestoresFontSizesAndCurrentOrientationPositionsAfterReload() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        check(context.packageName.endsWith(".notificationtest"))
        StartupCoordinator.awaitReady()
        val ids = listOf("time", "date", "weather", "battery", "notifications")
        val orientations = WidgetLayoutOrientation.entries
        val positionKeys = orientations.associateWith { orientation ->
            ids.flatMap { id -> listOf("x", "y").map { widgetPositionPreferenceKey(id, it, orientation) } }
        }
        val seed = positionKeys.values.flatten().associateWith { "120.0" } + mapOf(
            "time_font_size" to "70", "date_font_size" to "18", "fixed_widget_positions" to "true",
            "time_height_scale" to "1.4", "date_height_scale" to "0.7", "widget_glass_effect" to "true",
            "analog_clock" to "false")
        val previous = seed.keys.associateWith { PreferenceManager.getString(context, it, null) }
        val stores = mutableListOf<ViewModelStore>()
        fun newModel(): SettingsViewModel {
            lateinit var result: SettingsViewModel
            instrumentation.runOnMainSync {
                val store = ViewModelStore().also { stores += it }
                result = ViewModelProvider(store, ViewModelProvider.AndroidViewModelFactory(
                    context.applicationContext as Application))[SettingsViewModel::class.java]
            }
            return result
        }
        try {
            seed.forEach { (key, value) -> PreferenceManager.setString(context, key, value) }
            withTimeout(10000) { context.settingsDataStore.data.first { p ->
                val values = p.asMap().entries.associate { it.key.name to it.value.toString() }
                seed.all { (key, value) -> values[key] == value }
            } }
            PreferenceManager.reload(context)
            val model = newModel()
            withTimeout(10000) { model.uiState.first { it.timeFontSize == 70 && it.dateFontSize == 18 } }
            val orientation = if (context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE)
                WidgetLayoutOrientation.LANDSCAPE else WidgetLayoutOrientation.PORTRAIT
            val other = orientations.first { it != orientation }
            val label = if (context.resources.configuration.locales[0].language == "ar") "إعادة ضبط الأدوات" else "Reset widgets"
            var preview by mutableStateOf<String?>(null)
            var reference by mutableStateOf(false)
            compose.setContent {
                CompositionLocalProvider(LocalLayoutDirection provides direction) {
                    MaterialTheme {
                        val settings by model.uiState.collectAsState()
                        if (preview == null) BasicSettings(model, {}, {}, {})
                        else Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            val rendered = if (reference) settings.copy(timeFontSize = 100, dateFontSize = 30,
                                timeHeightScale = 1f, dateHeightScale = 1f) else settings
                            Box(Modifier.widthIn(max = 280.dp).heightIn(max = 260.dp).testTag("reset-widget-preview")) {
                                if (preview == "time") WidgetClock(rendered.copy(showAnalog = false), Color.White, false)
                                else WidgetDate(rendered, Color.White, false)
                            }
                        }
                    }
                }
            }
            val action = compose.onNodeWithText(label).performScrollTo().assertIsDisplayed()
            assertTrue("Reset action has a 48dp touch target",
                action.fetchSemanticsNode().boundsInRoot.height >= 48f * context.resources.displayMetrics.density - 1f)
            repeat(2) {
                fun slider(range: ClosedFloatingPointRange<Float>) = compose.onNode(SemanticsMatcher("Slider range $range") {
                    it.config.getOrNull(SemanticsProperties.ProgressBarRangeInfo)?.range == range
                })
                // Exercise real slider changes, then reset without leaving Pro settings.
                slider(30f..150f).performScrollTo().performSemanticsAction(SemanticsActions.SetProgress) { it(140f) }
                slider(15f..60f).performScrollTo().performSemanticsAction(SemanticsActions.SetProgress) { it(55f) }
                compose.waitUntil(10000) { model.uiState.value.timeFontSize >= 138 && model.uiState.value.dateFontSize >= 53 }
                val generation = model.uiState.value.widgetResetGeneration
                action.performScrollTo().performClick()
                compose.waitUntil(10000) {
                    val s = model.uiState.value
                    s.widgetResetGeneration == generation + 1 && s.timeFontSize == 100 && s.dateFontSize == 30 &&
                        (if (orientation == WidgetLayoutOrientation.LANDSCAPE) s.landscapeWidgetPositions else s.widgetPositions).isEmpty()
                }
                for ((range, expected) in listOf((30f..150f) to 100f, (15f..60f) to 30f)) {
                    val progress = slider(range).performScrollTo().fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo]
                    assertEquals("Font-size slider returns to default", expected, progress.current, 2f)
                }
                for (id in listOf("time", "date")) {
                    compose.runOnIdle { preview = id; reference = false }
                    val actual = compose.onNodeWithTag("reset-widget-preview").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
                    compose.runOnIdle { reference = true }
                    val expected = compose.onNodeWithTag("reset-widget-preview").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
                    assertEquals("Reset restores visible $id width", expected.width, actual.width, 1f)
                    assertEquals("Reset restores visible $id height", expected.height, actual.height, 1f)
                }
                compose.runOnIdle { preview = null; reference = false }
            }
            withTimeout(10000) { context.settingsDataStore.data.first { p ->
                val values = p.asMap().entries.associate { it.key.name to it.value.toString() }
                values["time_font_size"] == "100" && values["date_font_size"] == "30" &&
                    values["time_height_scale"]?.toFloatOrNull() == 1f && values["date_height_scale"]?.toFloatOrNull() == 1f &&
                    positionKeys.getValue(orientation).none { it in values }
            } }
            PreferenceManager.reload(context)
            val restored = newModel()
            withTimeout(10000) { restored.uiState.first { s ->
                s.timeFontSize == 100 && s.dateFontSize == 30 && s.timeHeightScale == 1f &&
                    (if (other == WidgetLayoutOrientation.LANDSCAPE) s.landscapeWidgetPositions else s.widgetPositions).values.all { it != null }
            } }
            for (id in ids) {
                assertFalse("Current orientation cleared", restored.hasWidgetPosition(id, orientation))
                assertTrue("Other orientation retained", restored.hasWidgetPosition(id, other))
            }
            assertEquals(1f, restored.uiState.value.dateHeightScale, 0f)
            assertTrue("Glass preference retained", restored.uiState.value.widgetGlassEffect)
        } finally {
            instrumentation.runOnMainSync { stores.forEach { it.clear() } }
            previous.forEach { (key, value) -> PreferenceManager.setString(context, key, value) }
        }
    }
}
