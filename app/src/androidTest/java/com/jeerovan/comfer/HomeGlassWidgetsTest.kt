package com.jeerovan.comfer

import android.graphics.Bitmap
import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.*
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.File

@RunWith(Parameterized::class)
class HomeGlassWidgetsTest(private val direction: LayoutDirection) {
    @get:Rule val compose = createComposeRule()
    companion object {
        @JvmStatic @Parameterized.Parameters(name = "{0}")
        fun directions() = listOf(arrayOf(LayoutDirection.Ltr), arrayOf(LayoutDirection.Rtl))
    }
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val tags = listOf("weather", "battery", "notifications")

    @Test fun sharedSwitchTintOpacityAndGeometryAcrossLayouts() {
        var glass by mutableStateOf(false)
        var tint by mutableStateOf(Color(0xff300808))
        var transparent by mutableStateOf(false)
        var vertical by mutableStateOf(false)
        var large by mutableStateOf(false)
        var light by mutableStateOf(false)
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalLayoutDirection provides direction,
                LocalDensity provides Density(density.density, if (large) 1.5f else 1f)) {
                MaterialTheme {
                    val settings = SettingsUiState(widgetGlassEffect = glass,
                        autoWallpapers = false, monochrome = false,
                        weatherTemperatureC = 27.0, weatherFontSize = 20,
                        batteryFontSize = 24, showBatteryIcon = true, showBatteryPercentage = true,
                        notificationSize = 16, notificationLayoutId = if (vertical) 2 else 1,
                        hasNotificationAccess = true,
                        weatherAlpha = if (transparent) 0 else 100,
                        batteryAlpha = if (transparent) 0 else 100,
                        notificationAlpha = if (transparent) 0 else 100)
                    Column(Modifier.fillMaxSize().background(if (light) Color(0xffe2dfd4) else Color(0xff142032)).testTag("host"),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center) {
                        Box(Modifier.testTag("weather")) {
                            WeatherWidget(settings, Color.White, false, onLocationChanged = {},
                                onTemperatureChanged = {}, glassBackground = tint)
                        }
                        Box(Modifier.testTag("battery")) {
                            BatteryStatusContent(settings, Color.White, false, BatteryState(65, false), tint)
                        }
                        Box(Modifier.testTag("notifications")) {
                            NotificationIconRow(settings = settings, foregroundColor = Color.White,
                                showBorder = false, glassBackground = tint)
                        }
                    }
                }
            }
        }
        awaitHostVisible()
        for (scaled in listOf(false, true)) for (column in listOf(false, true)) {
            compose.runOnIdle { large = scaled; vertical = column; glass = false }
            val bounds = tags.map { compose.onNodeWithTag(it).fetchSemanticsNode().boundsInRoot }
            val solid = tags.map(::capture)
            compose.runOnIdle { glass = true }
            val host = compose.onNodeWithTag("host").fetchSemanticsNode().boundsInRoot
            tags.forEachIndexed { i, tag ->
                val actual = compose.onNodeWithTag(tag).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
                assertEquals("Stable $tag placement", bounds[i], actual)
                assertEquals("Centered $tag", host.center.x, actual.center.x, .5f)
                assertTrue("Visible $tag", host.contains(actual.topLeft) && host.contains(actual.bottomRight))
                val shaded = capture(tag)
                if (solid[i].sameAs(shaded)) {
                    val dir = File(context.cacheDir, "widget-glass-evidence").apply { mkdirs() }
                    File(dir, "failure-$direction-$tag-solid.png").outputStream().use { solid[i].compress(Bitmap.CompressFormat.PNG, 100, it) }
                    File(dir, "failure-$direction-$tag-glass.png").outputStream().use { shaded.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    save("failure-$tag-$scaled-$column")
                }
                assertFalse("$tag responds to glass switch (large=$scaled vertical=$column)", solid[i].sameAs(shaded))
                shaded.recycle()
                solid[i].recycle()
            }
            compose.onAllNodesWithText("27°").assertCountEquals(1)
            compose.onAllNodesWithText("65%").assertCountEquals(1)
        }
        compose.runOnIdle { large = false; vertical = false }
        val red = tags.map(::capture)
        save("red")
        compose.runOnIdle { tint = Color(0xff081030) }
        tags.forEachIndexed { i, tag ->
            assertFalse("$tag follows background tint", red[i].sameAs(capture(tag)))
            red[i].recycle()
        }
        save("blue")
        compose.runOnIdle { tint = Color(0xffe2dfd4); light = true }
        tags.forEach { compose.onNodeWithTag(it).assertIsDisplayed() }
        save("light")
        compose.runOnIdle { tint = Color(0xff081030); light = false }
        compose.runOnIdle { glass = false }
        val off = tags.map(::capture)
        compose.runOnIdle { tint = Color(0xff300808) }
        tags.forEachIndexed { i, tag ->
            assertTrue("$tag retains chosen color with glass off", off[i].sameAs(capture(tag)))
            off[i].recycle()
        }
        compose.runOnIdle { glass = true; transparent = true }
        val hidden = capture("host")
        val pixels = IntArray(hidden.width * hidden.height)
        hidden.getPixels(pixels, 0, hidden.width, 0, 0, hidden.width, hidden.height)
        assertTrue("Zero opacity hides all widget fills and shadows", pixels.all { it == 0xff142032.toInt() })
        hidden.recycle()
    }

    @Test fun batteryWarningsAndWeatherDetailsSurviveGlass() {
        var battery by mutableStateOf(BatteryState(5, false))
        var temperature by mutableStateOf<Double?>(null)
        var icon by mutableStateOf(true)
        var percent by mutableStateOf(true)
        compose.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides direction) {
                MaterialTheme {
                    val settings = SettingsUiState(widgetGlassEffect = true, batteryFontSize = 40,
                        showBatteryIcon = icon, showBatteryPercentage = percent,
                        weatherTemperatureC = temperature, weatherUseFahrenheit = true)
                    Column(Modifier.fillMaxSize().background(Color(0xff142032)).testTag("host"),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center) {
                        Box(Modifier.testTag("battery-state")) {
                            BatteryStatusContent(settings, Color.White, false, battery, Color(0xff081030))
                        }
                        WeatherWidget(settings, Color.White, false, onLocationChanged = {}, onTemperatureChanged = {})
                    }
                }
            }
        }
        awaitHostVisible()
        fun hasRed(): Boolean {
            val bitmap = capture("battery-state")
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            bitmap.recycle()
            return pixels.any { android.graphics.Color.red(it) > android.graphics.Color.blue(it) + 50 &&
                android.graphics.Color.red(it) > android.graphics.Color.green(it) + 50 }
        }
        assertTrue("Low battery stays red", hasRed())
        compose.runOnIdle { battery = BatteryState(100, true) }
        assertTrue("Charging bolt stays red", hasRed())
        compose.runOnIdle { battery = BatteryState(100, false) }
        assertFalse("Normal battery uses wallpaper tint", hasRed())
        compose.runOnIdle { battery = BatteryState(0, false); icon = false }
        compose.onNodeWithText("0%").assertIsDisplayed()
        compose.runOnIdle { battery = BatteryState(-1, false) }
        compose.onNodeWithText("-1%").assertDoesNotExist()
        compose.runOnIdle { battery = BatteryState(65, false); icon = true; percent = false }
        compose.onNodeWithText("65%").assertDoesNotExist()
        compose.onNodeWithText("—°").assertIsDisplayed()
        compose.runOnIdle { temperature = 27.0 }
        compose.onNodeWithText("81°").performClick()
        compose.onNodeWithText(context.getString(R.string.weather_details_title)).assertIsDisplayed()
        compose.onNodeWithContentDescription(context.getString(R.string.weather_close_details)).performClick()
        compose.onNodeWithText("81°").assertIsDisplayed()
    }

    private fun awaitHostVisible() {
        compose.waitForIdle()
        val host = compose.onNodeWithTag("host").fetchSemanticsNode().boundsInWindow
        // The first activity's enter animation can outlast Compose idleness on both devices.
        compose.waitUntil(10_000) {
            val screen = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
            val ready = screen != null && screen.getPixel(host.left.toInt() + 4, host.top.toInt() + 4) == 0xff142032.toInt()
            screen?.recycle()
            ready
        }
    }

    private fun capture(tag: String): Bitmap {
        compose.waitForIdle()
        SystemClock.sleep(250)
        val b = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInWindow
        val screen = requireNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        return Bitmap.createBitmap(screen, b.left.toInt(), b.top.toInt(), b.width.toInt(), b.height.toInt())
            .also { if (it !== screen) screen.recycle() }
    }
    private fun save(name: String) {
        val bitmap = capture("host")
        val dir = File(context.cacheDir, "widget-glass-evidence").apply { mkdirs() }
        File(dir, "home-$direction-$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
