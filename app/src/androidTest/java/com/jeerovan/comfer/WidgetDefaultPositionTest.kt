package com.jeerovan.comfer

import android.content.Intent
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.Bitmap
import android.os.SystemClock
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.text.layoutDirection
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.jeerovan.comfer.utils.KeyboardLocale
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.Locale

class WidgetDefaultPositionTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()

    @Test fun cornersStayWithinWidgetPaneForSystemAndAppLanguages() {
        check(instrumentation.targetContext.packageName.endsWith(".notificationtest"))
        val systemTag = Resources.getSystem().configuration.locales[0].toLanguageTag()
        InstrumentationRegistry.getArguments().getString("expectedSystemLanguage")?.let {
            assertEquals(it, Locale.forLanguageTag(systemTag).language)
        }
        var previous = ""
        ActivityScenario.launch(GuideActivity::class.java).use { scenario ->
            scenario.onActivity { previous = AppCompatDelegate.getApplicationLocales().toLanguageTags() }
        }
        try {
            changeLanguage("")
            val inherited = checkPositions(systemTag, "system")
            changeLanguage(systemTag, usePicker = true)
            assertEquals("App and system language preserve geometry", inherited,
                checkPositions(systemTag, "app"))
            val opposite = if (Locale.forLanguageTag(systemTag).language == "ar") "en" else "ar"
            changeLanguage(opposite, usePicker = true)
            checkPositions(opposite, "opposite")
            changeLanguage("")
            assertEquals("Returning to the system default restores geometry", inherited,
                checkPositions(systemTag, "restored"))
        } finally {
            changeLanguage(previous)
        }
    }

    private fun changeLanguage(tag: String, usePicker: Boolean = false) {
        fun apply(selectedTag: String) {
            instrumentation.targetContext.startActivity(
                Intent(instrumentation.targetContext, LanguageUpdateActivity::class.java)
                    .putExtra(LanguageUpdateActivity.EXTRA_LOCALE_TAG, selectedTag)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        var selectedTag = tag
        if (usePicker) {
            val locale = KeyboardLocale.getSupportedLocales().first {
                it.language == Locale.forLanguageTag(tag).language
            }
            selectedTag = locale.toLanguageTag()
            ActivityScenario.launch(GuideActivity::class.java).use { scenario ->
                scenario.onActivity { activity ->
                    activity.setContent {
                        MaterialTheme {
                            LocaleSelectionDialog(onDismissRequest = {}, onLocaleSelected = { apply(it.toLanguageTag()) })
                        }
                    }
                }
                val label = locale.getDisplayName(locale)
                compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(label))
                compose.onNodeWithText(label).performClick()
            }
        } else {
            // The picker has no system-default row; restore it through its handler.
            apply(tag)
        }
        compose.waitUntil(10_000) {
            var current = ""
            instrumentation.runOnMainSync {
                current = AppCompatDelegate.getApplicationLocales().toLanguageTags()
            }
            current == selectedTag
        }
    }

    private fun checkPositions(tag: String, name: String): List<Rect> {
        val expected = Locale.forLanguageTag(tag)
        var fontSize by mutableIntStateOf(16)
        var positions by mutableStateOf<Map<String, Offset?>>(emptyMap())
        var density = 1f
        var direction: LayoutDirection? = null
        var writes = 0
        var result = emptyList<Rect>()
        ActivityScenario.launch(GuideActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                assertEquals(expected.language, activity.resources.configuration.locales[0].language)
                InstrumentationRegistry.getArguments().getString("expectedOrientation")?.let {
                    assertEquals("Activity orientation", if (it == "landscape") Configuration.ORIENTATION_LANDSCAPE
                        else Configuration.ORIENTATION_PORTRAIT, activity.resources.configuration.orientation)
                }
                activity.setContent {
                    density = LocalDensity.current.density
                    direction = LocalLayoutDirection.current
                    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
                    MaterialTheme {
                        LauncherTwoPaneLayout(landscape, 400.dp, Modifier.fillMaxSize()) {
                            DraggableQuickWidgetsContainer(
                                Modifier.fillMaxSize().background(Color(0xff142032)).testTag("widget-pane"),
                                editMode = false,
                                widgetIds = listOf("time", "date", "weather", "battery", "notifications"),
                                widgetPositions = positions,
                                onPositionChanged = { id, value -> writes++; positions = positions + (id to value) },
                                onEditModeChanged = {}, onWidgetLongPressShown = {},
                                composableContent = { id, _, _ ->
                                    val settings = SettingsUiState(monochrome = true,
                                        weatherTemperatureC = 27.0, weatherFontSize = fontSize,
                                        batteryFontSize = fontSize, showBatteryIcon = true,
                                        showBatteryPercentage = true, dateFontSize = 18)
                                    when (id) {
                                        "time" -> ClockText("12:34", 1, 40.sp, Color.White)
                                        "date" -> WidgetDate(settings, Color.White, false)
                                        "weather" -> WeatherWidget(settings, Color.White, false,
                                            onLocationChanged = {}, onTemperatureChanged = {})
                                        "battery" -> BatteryStatusContent(settings, Color.White, false, BatteryState(65, false))
                                        else -> Box(Modifier.size(48.dp, 20.dp))
                                    }
                                })
                            Box(Modifier.fillMaxSize().background(Color.Black).testTag("apps-pane"))
                        }
                    }
                }
            }
            compose.waitForIdle()
            assertEquals(if (expected.layoutDirection == 1) LayoutDirection.Rtl else LayoutDirection.Ltr, direction)
            fun bounds(id: String) = compose.onNodeWithTag("quick-widget-$id").assertIsDisplayed()
                .fetchSemanticsNode().boundsInRoot
            val pane = compose.onNodeWithTag("widget-pane").fetchSemanticsNode().boundsInRoot
            val inset = 16f * density
            fun assertCorners() {
                val weather = bounds("weather")
                val battery = bounds("battery")
                assertEquals("Weather uses the physical left of its pane", pane.left + inset, weather.left, 1f)
                assertEquals("Battery uses the physical right of its pane", pane.right - inset, battery.right, 1f)
                for (widget in listOf(weather, battery)) {
                    assertEquals("Top margin is relative to the widget pane", pane.top + inset, widget.top, 1f)
                    assertTrue("Widget remains fully visible", pane.contains(widget.topLeft) && pane.contains(widget.bottomRight))
                }
            }
            assertCorners()
            val column = listOf("time", "date", "notifications").map(::bounds)
            column.forEach { assertEquals(pane.center.x, it.center.x, 1f) }
            assertEquals("Corner widgets do not reserve space in the centered column", pane.center.y,
                (column.first().top + column.last().bottom) / 2f, 1f)
            result = listOf(bounds("weather"), bounds("battery"))

            compose.runOnIdle { fontSize = 24 }
            assertCorners()
            val saved = Offset(pane.width / 2f, pane.height / 2f)
            compose.runOnIdle { positions = mapOf("battery" to saved) }
            assertEquals("Saved placement overrides the default", pane.center.x, bounds("battery").center.x, 1f)
            assertEquals(pane.center.y, bounds("battery").center.y, 1f)
            compose.runOnIdle { fontSize = 18 }
            assertEquals("Remeasuring preserves saved placement", pane.center.x, bounds("battery").center.x, 1f)
            assertEquals(pane.center.y, bounds("battery").center.y, 1f)
            compose.runOnIdle { positions = emptyMap() }
            assertCorners()
            assertEquals("Measuring defaults never persists positions", 0, writes)

            val out = File(instrumentation.targetContext.cacheDir, "widget-default-evidence").apply { mkdirs() }
            SystemClock.sleep(200)
            instrumentation.uiAutomation.takeScreenshot()?.let { bitmap ->
                File(out, "$name-${expected.language}.png").outputStream().use {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                }
                bitmap.recycle()
            }
        }
        return result
    }
}
