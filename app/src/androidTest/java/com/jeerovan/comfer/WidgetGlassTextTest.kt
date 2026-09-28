package com.jeerovan.comfer

import android.graphics.Bitmap
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.File

@RunWith(Parameterized::class)
class WidgetGlassTextTest(private val direction: LayoutDirection) {
    @get:Rule val compose = createComposeRule()
    companion object {
        @JvmStatic @Parameterized.Parameters(name = "{0}")
        fun directions() = listOf(arrayOf(LayoutDirection.Ltr), arrayOf(LayoutDirection.Rtl))
    }

    @Test fun glassPreservesBoundsSemanticsAndOpacityAcrossFontsAndSizes() {
        var glass by mutableStateOf(false)
        var color by mutableStateOf(Color.White)
        var large by mutableStateOf(false)
        var curved by mutableStateOf(false)
        val label = if (direction == LayoutDirection.Rtl) "الأحد ٢٧" else "Sun 27"
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalLayoutDirection provides direction,
                LocalDensity provides Density(density.density, if (large) 1.5f else 1f)) {
                MaterialTheme {
                    Box(Modifier.size(320.dp, 200.dp).background(Color(0xff142032)).testTag("host"),
                        contentAlignment = Alignment.Center) {
                        Box(Modifier.testTag("text")) {
                            EffectTextBlock(label, fontSize = 36.sp, fontWeight = FontWeight.Bold,
                                color = color, glass = glass, radius = if (curved) 150f else 0f)
                        }
                    }
                }
            }
        }
        for (scaled in listOf(false, true)) {
            compose.runOnIdle { large = scaled; glass = false }
            val solid = compose.onNodeWithTag("text").fetchSemanticsNode().boundsInRoot
            compose.runOnIdle { glass = true }
            val actual = compose.onNodeWithTag("text").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            assertEquals("Glass must not move or resize text", solid, actual)
            compose.onAllNodesWithText(label).assertCountEquals(1)
            val host = compose.onNodeWithTag("host").fetchSemanticsNode().boundsInRoot
            assertTrue(host.contains(actual.topLeft) && host.contains(actual.bottomRight))
        }
        for (onCurve in listOf(false, true)) {
            compose.runOnIdle { curved = onCurve; color = Color.Transparent }
            val bitmap = capture("host")
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            assertTrue("Zero opacity must hide fill, rim and shadow (curve=$onCurve): ${pixels.toSet().take(12).map { Integer.toHexString(it) }}", pixels.all { it == 0xff142032.toInt() })
        }
    }

    @Test fun captureClockAndDateLayoutsOnLightAndDarkWallpapers() {
        var glass by mutableStateOf(true)
        var light by mutableStateOf(false)
        var layout by mutableIntStateOf(1)
        var curved by mutableStateOf(false)
        compose.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides direction) {
                MaterialTheme {
                    val foreground = if (light) Color.Black else Color.White
                    val background = if (light) Color(0xffe2dfd4) else Color(0xff142032)
                    val settings = SettingsUiState(monochrome = true, timeFontSize = 64, widgetGlassEffect = glass,
                        timeFontWeight = "Bold", dateFontSize = 26, dateFontWeight = "Bold",
                        timeLayoutId = layout, dateLayoutId = if (layout == 3) 2 else 1,
                        timeRadius = if (curved) 150 else 0, dateRadius = if (curved) 150 else 0)
                    Column(Modifier.fillMaxSize().background(background).testTag("preview"),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center) {
                        Box(Modifier.testTag("date-widget")) { WidgetDate(settings, foreground, false, background) }
                        Spacer(Modifier.height(20.dp))
                        Box(Modifier.testTag("clock-widget")) { TextClock(settings, foreground, false, background) }
                    }
                }
            }
        }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val out = File(context.cacheDir, "widget-glass-evidence").apply { mkdirs() }
        for (bright in listOf(false, true)) for (variant in 1..3) {
            compose.runOnIdle { light = bright; layout = variant; glass = true }
            compose.onNodeWithTag("preview").assertIsDisplayed()
            val bitmap = capture("preview")
            File(out, "$direction-$bright-$variant.png").outputStream().use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            val glassDate = capture("date-widget")
            val glassClock = capture("clock-widget")
            compose.runOnIdle { glass = false }
            org.junit.Assert.assertFalse("Date responds to glass setting", glassDate.sameAs(capture("date-widget")))
            org.junit.Assert.assertFalse("Clock responds to glass setting", glassClock.sameAs(capture("clock-widget")))
            glassDate.recycle()
            glassClock.recycle()

        }
        compose.runOnIdle { curved = true; layout = 1; light = false; glass = true }
        val bitmap = capture("preview")
        File(out, "$direction-curved.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
    @Test fun changingWallpaperTintUpdatesBothWidgetsWithoutMovingThem() {
        var tint by mutableIntStateOf(0xff300808.toInt())
        var enabled by mutableStateOf(true)
        compose.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides direction) {
                MaterialTheme {
                    val settings = SettingsUiState(autoWallpapers = true, widgetGlassEffect = enabled,
                        timeFontSize = 64, themedColors = WallpaperThemeColors(0, 0, 0, 0, 0, 0, tint))
                    Column(Modifier.fillMaxSize().background(Color(0xff142032)),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center) {
                        Box(Modifier.testTag("tint-date")) { WidgetDate(settings, Color.White, false) }
                        Box(Modifier.testTag("tint-clock")) { TextClock(settings, Color.White, false) }
                    }
                }
            }
        }
        val dateBounds = compose.onNodeWithTag("tint-date").fetchSemanticsNode().boundsInRoot
        val redDate = capture("tint-date")
        val redClock = capture("tint-clock")
        compose.runOnIdle { tint = 0xff081030.toInt() }
        assertFalse("Date follows wallpaper color", redDate.sameAs(capture("tint-date")))
        assertFalse("Time follows wallpaper color", redClock.sameAs(capture("tint-clock")))
        assertEquals(dateBounds, compose.onNodeWithTag("tint-date").fetchSemanticsNode().boundsInRoot)
        compose.runOnIdle { enabled = false }
        val solidDate = capture("tint-date")
        compose.runOnIdle { tint = 0xff300808.toInt() }
        assertTrue("Glass off preserves chosen text color", solidDate.sameAs(capture("tint-date")))
        redDate.recycle(); redClock.recycle(); solidDate.recycle()
    }

    // UiAutomation also supports API 24, where window PixelCopy is unavailable.
    private fun capture(tag: String): Bitmap {
        compose.waitForIdle()
        android.os.SystemClock.sleep(250) // Allow the compositor to present the settled frame.
        val bounds = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInWindow
        val screen = requireNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        return Bitmap.createBitmap(screen, bounds.left.toInt(), bounds.top.toInt(),
            bounds.width.toInt(), bounds.height.toInt()).also { if (it !== screen) screen.recycle() }
    }

}
