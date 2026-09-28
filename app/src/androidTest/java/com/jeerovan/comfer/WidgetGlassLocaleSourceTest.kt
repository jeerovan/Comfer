package com.jeerovan.comfer

import android.content.Intent
import android.content.res.Resources
import android.graphics.Bitmap
import android.os.SystemClock
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.text.layoutDirection
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.Locale

/** Run once under each real system language; no layout-direction or locale injection. */
class WidgetGlassLocaleSourceTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()

    @Test fun systemAndInAppLanguageProduceTheSameWidgetGeometry() {
        val systemTag = Resources.getSystem().configuration.locales[0].toLanguageTag()
        val systemLanguage = Locale.forLanguageTag(systemTag).language
        InstrumentationRegistry.getArguments().getString("expectedSystemLanguage")?.let {
            assertEquals("Device system language", it, systemLanguage)
        }
        var previous = ""
        ActivityScenario.launch(GuideActivity::class.java).use { guide ->
            guide.onActivity { previous = AppCompatDelegate.getApplicationLocales().toLanguageTags() }
        }
        try {
            changeLanguage("")
            val inherited = capture(systemTag, "system-$systemLanguage")
            changeLanguage(systemTag)
            val explicit = capture(systemTag, "app-$systemLanguage-on-$systemLanguage")
            assertEquals("Selecting the system language inside the app must preserve widget geometry", inherited, explicit)

            val opposite = if (systemLanguage == "ar") "en" else "ar"
            changeLanguage(opposite)
            capture(opposite, "app-$opposite-on-$systemLanguage")

            changeLanguage("")
            assertEquals("Returning to system language", inherited,
                capture(systemTag, "system-$systemLanguage-restored"))
        } finally {
            changeLanguage(previous)
        }
    }

    private fun changeLanguage(tag: String) {
        // Exercise the same trampoline used by the language picker in Settings.
        instrumentation.targetContext.startActivity(
            Intent(instrumentation.targetContext, LanguageUpdateActivity::class.java)
                .putExtra(LanguageUpdateActivity.EXTRA_LOCALE_TAG, tag)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        val deadline = SystemClock.uptimeMillis() + 10000
        do {
            var current = ""
            instrumentation.runOnMainSync {
                current = AppCompatDelegate.getApplicationLocales().toLanguageTags()
            }
            if (current == tag) return
            SystemClock.sleep(50)
        } while (SystemClock.uptimeMillis() < deadline)
        fail("LanguageUpdateActivity did not apply '$tag'")
    }

    private fun capture(tag: String, name: String): List<List<Float>> {
        val expectedLanguage = Locale.forLanguageTag(tag).language
        val expectedDirection = if (Locale.forLanguageTag(tag).layoutDirection == 1)
            LayoutDirection.Rtl else LayoutDirection.Ltr
        var direction: LayoutDirection? = null
        var result = emptyList<List<Float>>()
        ActivityScenario.launch(GuideActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                assertEquals("Activity locale: $name", expectedLanguage,
                    activity.resources.configuration.locales[0].language)
                assertEquals("Activity direction: $name", if (expectedDirection == LayoutDirection.Rtl) 1 else 0,
                    activity.resources.configuration.layoutDirection)
                activity.setContent {
                    val actualDirection = LocalLayoutDirection.current
                    SideEffect { direction = actualDirection }
                    MaterialTheme {
                        androidx.compose.foundation.layout.Column(
                            Modifier.fillMaxSize().background(Color(0xff142032)).testTag("locale-widgets"),
                            horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                            verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                        ) {
                            val settings = SettingsUiState(monochrome = true, timeFontSize = 64)
                            Box(Modifier.testTag("date")) { WidgetDate(settings, Color.White, false) }
                            Box(Modifier.testTag("clock")) { TextClock(settings, Color.White, false) }
                            Box(Modifier.testTag("weather")) {
                                WeatherWidget(settings.copy(weatherTemperatureC = 27.0, weatherFontSize = 16),
                                    Color.White, false, onLocationChanged = {}, onTemperatureChanged = {})
                            }
                            Box(Modifier.testTag("battery")) {
                                BatteryStatusContent(settings.copy(batteryFontSize = 16, showBatteryIcon = true),
                                    Color.White, false, BatteryState(65, true))
                            }
                            Box(Modifier.testTag("notifications")) {
                                NotificationIconRow(settings = settings.copy(hasNotificationAccess = true, notificationSize = 16),
                                    foregroundColor = Color.White, showBorder = false)
                            }
                        }
                    }
                }
            }
            compose.waitForIdle()
            assertEquals("Inherited Compose direction: $name", expectedDirection, direction)
            val host = compose.onNodeWithTag("locale-widgets").fetchSemanticsNode().boundsInRoot
            result = listOf("date", "clock", "weather", "battery", "notifications").map { key ->
                val bounds = compose.onNodeWithTag(key).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
                assertEquals("Centered $key: $name", host.center.x, bounds.center.x, .5f)
                assertTrue("Visible $key: $name", host.contains(bounds.topLeft) && host.contains(bounds.bottomRight))
                // A live minute tick can change digit widths between locale captures.
                // Check its stable center/height; visibility is asserted above for every capture.
                if (key == "clock") listOf(bounds.top - host.top, bounds.height)
                else listOf(bounds.left - host.left, bounds.top - host.top, bounds.width, bounds.height)
            }
            val date = java.text.SimpleDateFormat("EEE MMM d", Locale.forLanguageTag(tag)).format(System.currentTimeMillis())
            compose.onNodeWithText(date).assertIsDisplayed()
            val out = File(instrumentation.targetContext.cacheDir, "widget-glass-evidence").apply { mkdirs() }
            SystemClock.sleep(250)
            instrumentation.uiAutomation.takeScreenshot()?.let { bitmap ->
                File(out, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                bitmap.recycle()
            }
            // Exercise the actual Pro settings control under both real locale sources.
            lateinit var model: SettingsViewModel
            lateinit var label: String
            scenario.onActivity { activity ->
                model = androidx.lifecycle.ViewModelProvider(activity)[SettingsViewModel::class.java]
                label = activity.getString(R.string.title_glass_effect)
                activity.setContent { MaterialTheme { BasicSettings(model, {}, {}, {}) } }
            }
            val row = compose.onNodeWithText(label).performScrollTo().assertIsDisplayed()
            val toggle = compose.onNode(isToggleable() and hasAnyAncestor(hasText(label)))
            val original = model.uiState.value.widgetGlassEffect
            try {
                row.performClick()
                compose.runOnIdle { assertEquals(!original, model.uiState.value.widgetGlassEffect) }
                toggle.performClick()
                compose.runOnIdle { assertEquals(original, model.uiState.value.widgetGlassEffect) }
                val bounds = row.fetchSemanticsNode().boundsInRoot
                val control = toggle.fetchSemanticsNode().boundsInRoot
                assertTrue("Switch visible inside row: $name", bounds.contains(control.center))
                assertTrue("Trailing switch: $name", if (expectedDirection == LayoutDirection.Rtl)
                    control.center.x < bounds.center.x else control.center.x > bounds.center.x)
                result = result + listOf(listOf(bounds.left, bounds.top, bounds.width, bounds.height))
                SystemClock.sleep(250)
                instrumentation.uiAutomation.takeScreenshot()?.let { bitmap ->
                    File(out, "settings-$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    bitmap.recycle()
                }
            } finally {
                scenario.onActivity { model.setWidgetGlassEffect(original) }
            }

            val originalLayout = model.uiState.value.timeLayoutId
            scenario.onActivity { activity ->
                activity.setContent { MaterialTheme { TimeAdvancedSettings(model, {}) } }
            }
            val preview = String.format(Locale.forLanguageTag(tag), "%02d:%02d", 12, 34)
            try {
                for ((id, text) in listOf(1 to preview, 2 to preview.replace(":", ""), 3 to preview.replace(':', '\n'))) {
                    val option = compose.onNodeWithText(text).performScrollTo().assertIsDisplayed()
                    option.performClick()
                    compose.waitUntil(5000) { model.uiState.value.timeLayoutId == id }
                    val bounds = option.fetchSemanticsNode().boundsInRoot
                    assertTrue("Usable layout target: $name/$id", bounds.width >= 48f && bounds.height >= 48f)
                }
            } finally {
                scenario.onActivity { model.setTimeLayoutId(originalLayout) }
                compose.waitUntil(5000) { model.uiState.value.timeLayoutId == originalLayout }
            }

            scenario.onActivity { activity ->
                activity.setContent {
                    MaterialTheme {
                        BoxWithConstraints(Modifier.fillMaxSize().testTag("height-editor")) {
                            val density = LocalDensity.current
                            var scales by remember { mutableStateOf(mapOf("time" to 1f, "date" to 1f)) }
                            var positions by remember { mutableStateOf<Map<String, Offset?>>(mapOf(
                                "time" to Offset(with(density) { maxWidth.toPx() } / 2, with(density) { 60.dp.toPx() }),
                                "date" to Offset(with(density) { maxWidth.toPx() } / 2, with(density) { 180.dp.toPx() }))) }
                            DraggableQuickWidgetsContainer(editMode = true, widgetIds = listOf("time", "date"),
                                widgetPositions = positions, onPositionChanged = { id, p -> positions = positions + (id to p) },
                                heightScales = scales, onHeightScaleChanged = { id, value -> scales = scales + (id to value) },
                                onEditModeChanged = {}, onWidgetLongPressShown = {},
                                composableContent = { id, _, height ->
                                    if (id == "time") ClockText(preview, 1, 40.sp, Color.White, heightScale = height ?: 1f)
                                    else WidgetDate(SettingsUiState(monochrome = true, dateFontSize = 18,
                                        dateHeightScale = height ?: 1f), Color.White, false)
                                })
                        }
                    }
                }
            }
            val editor = compose.onNodeWithTag("height-editor").fetchSemanticsNode().boundsInRoot
            for (id in listOf("time", "date")) {
                val before = compose.onNodeWithTag("quick-widget-$id").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
                assertEquals("Physical centering in $name", editor.center.x, before.center.x, 1f)
                val handle = compose.onNodeWithTag("widget-height-handle-$id").assertIsDisplayed()
                handle.performSemanticsAction(SemanticsActions.SetProgress) { it(1.5f) }
                val after = compose.onNodeWithTag("quick-widget-$id").fetchSemanticsNode().boundsInRoot
                val grip = handle.fetchSemanticsNode().boundsInRoot
                assertEquals("Height keeps the top edge in $name", before.top, after.top, 1f)
                assertEquals("Circle centered on physical right corner in $name", after.right, grip.center.x, 1f)
                assertEquals(after.bottom, grip.center.y, 1f)
                assertTrue("Resized widget visible in $name", editor.contains(after.topLeft) && editor.contains(after.bottomRight))
                result = result + listOf(listOf(after.left, after.top, after.width, after.height))
            }

        }
        return result
    }
}
