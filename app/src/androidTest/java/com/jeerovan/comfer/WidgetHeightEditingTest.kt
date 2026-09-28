package com.jeerovan.comfer

import android.graphics.Bitmap
import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(Parameterized::class)
class WidgetHeightEditingTest(private val direction: LayoutDirection) {
    @get:Rule val compose = createComposeRule()
    companion object {
        @JvmStatic @Parameterized.Parameters(name = "{0}")
        fun directions() = listOf(arrayOf(LayoutDirection.Ltr), arrayOf(LayoutDirection.Rtl))
    }
    private val time get() = if (direction == LayoutDirection.Rtl) "٨٨:٨٨" else "88:88"

    @Test fun verticalStretchKeepsWidthColonGapAndStackedGap() {
        var scale by mutableFloatStateOf(1f)
        var layout by mutableIntStateOf(1)
        var glass by mutableStateOf(false)
        var family by mutableStateOf<FontFamily>(FontFamily.Default)
        compose.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides direction) {
                MaterialTheme {
                    Box(Modifier.fillMaxSize().background(Color.Black).testTag("host"), contentAlignment = Alignment.Center) {
                        Box(Modifier.testTag("clock").padding(8.dp)) {
                            ClockText(time, layout, 42.sp, Color.White, fontWeight = FontWeight.Bold,
                                fontFamily = family, glass = glass, heightScale = scale)
                        }
                    }
                }
            }
        }
        awaitVisible("clock")
        for (face in listOf(FontFamily.Default, FontFamily.Serif)) for (shaded in listOf(false, true)) for (variant in 1..3) {
            compose.runOnIdle { family = face; glass = shaded; layout = variant; scale = 1f }
            val normal = capture("clock")
            val originalWidth = compose.onNodeWithTag("clock").fetchSemanticsNode().boundsInRoot.width
            fun digitHeight(bitmap: Bitmap): Int {
                if (variant == 3) return runs(bitmap, false).first().let { it.last - it.first + 1 }
                val digit = runs(bitmap, true).first()
                val crop = Bitmap.createBitmap(bitmap, digit.first, 0, digit.last - digit.first + 1, bitmap.height)
                val rows = runs(crop, false)
                crop.recycle()
                return rows.last().last - rows.first().first + 1
            }
            fun gap(bitmap: Bitmap): Int {
                if (variant == 3) {
                    val rows = runs(bitmap, false)
                    assertEquals("Stacked lines stay separate", 2, rows.size)
                    return rows[1].first - rows[0].last - 1
                }
                val columns = runs(bitmap, true)
                assertEquals("Every digit and colon remains separated", if (variant == 1) 5 else 4, columns.size)
                if (variant != 1) return 0
                val colon = columns[2]
                val crop = Bitmap.createBitmap(bitmap, colon.first, 0, colon.last - colon.first + 1, bitmap.height)
                val rows = runs(crop, false)
                crop.recycle()
                assertEquals("Both colon dots visible", 2, rows.size)
                return rows[1].first - rows[0].last - 1
            }
            val originalGap = gap(normal)
            for (factor in listOf(2f, .5f)) {
                compose.runOnIdle { scale = factor }
                val changed = capture("clock")
                assertEquals("Vertical stretch keeps horizontal size", originalWidth,
                    compose.onNodeWithTag("clock").fetchSemanticsNode().boundsInRoot.width, 1f)
                assertEquals("Gap is independent of stretch: $direction/$variant/$shaded/$face/$factor",
                    originalGap.toFloat(), gap(changed).toFloat(), 3f)
                val changedRows = runs(changed, false)
                assertEquals("Characters stretch to the requested height: $direction/$variant/$shaded/$face/$factor",
                    digitHeight(normal) * factor, digitHeight(changed).toFloat(), 4f)
                assertTrue("Ink remains inside widget", changedRows.first().first > 0 && changedRows.last().last < changed.height - 1)
                val dir = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "widget-height-evidence").apply { mkdirs() }
                File(dir, "$direction-$variant-$factor-$shaded.png").outputStream().use { changed.compress(Bitmap.CompressFormat.PNG, 100, it) }
                changed.recycle()
            }
            normal.recycle()
        }
    }

    @Test fun cornerDragAnchorsTopCancelsAndLeavesBodyDraggable() {
        var editing by mutableStateOf(true)
        var scales by mutableStateOf(mapOf("time" to 1f, "date" to 1f))
        var positions by mutableStateOf<Map<String, Offset?>>(emptyMap())
        var writes = 0
        var density = 1f
        compose.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides direction) {
                density = LocalDensity.current.density
                MaterialTheme {
                    DraggableQuickWidgetsContainer(Modifier.fillMaxSize().background(Color.Black).testTag("host"),
                        editMode = editing, widgetIds = listOf("time", "date"), widgetPositions = positions,
                        onPositionChanged = { id, value -> positions = positions + (id to value) },
                        onEditModeChanged = { editing = it }, onWidgetLongPressShown = {},
                        heightScales = scales, onHeightScaleChanged = { id, value -> scales = scales + (id to value); writes++ },
                        composableContent = { id, _, height ->
                            if (id == "time") ClockText(time, 1, 40.sp, Color.White, heightScale = height ?: 1f)
                            else WidgetDate(SettingsUiState(monochrome = true, dateFontSize = 20, dateHeightScale = height ?: 1f), Color.White, false)
                        })
                }
            }
        }
        fun bounds(id: String) = compose.onNodeWithTag("quick-widget-$id").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val host = compose.onNodeWithTag("host").fetchSemanticsNode().boundsInRoot
        compose.waitUntil(10_000) { kotlin.math.abs(bounds("time").center.x - host.center.x) < 1f }
        for (id in listOf("time", "date")) {
            val initial = bounds(id)
            val other = if (id == "time") "date" else "time"
            val otherBefore = bounds(other)
            val handle = compose.onNodeWithTag("widget-height-handle-$id").assertIsDisplayed()
            val grip = handle.fetchSemanticsNode().boundsInRoot
            assertEquals("Circle center overlaps physical bottom-right", initial.right, grip.center.x, 1f)
            assertEquals(initial.bottom, grip.center.y, 1f)
            assertTrue("48dp touch target", grip.width >= 48f * density - 1 && grip.height >= 48f * density - 1)
            assertTrue("Entire handle visible", host.contains(grip.topLeft) && host.contains(grip.bottomRight))
            val frame = capture("host")
            val evidence = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,
                "widget-height-evidence").apply { mkdirs() }
            File(evidence, "corner-$direction-$id.png").outputStream().use { frame.compress(Bitmap.CompressFormat.PNG, 100, it) }
            frame.recycle()
            // Begin on the visible half outside the widget border.
            handle.performTouchInput { down(center + Offset(6f * density, 6f * density)); moveBy(Offset(0f, 30f * density), 250) }
            assertEquals("Live preview does not persist", 0, writes)
            val during = bounds(id)
            assertEquals("Top stays anchored", initial.top, during.top, 1.5f)
            assertEquals(initial.center.x, during.center.x, 1f)
            assertTrue("Drag expands height", during.height > initial.height + 5f)
            assertEquals("Resizing does not move another widget", otherBefore, bounds(other))
            handle.performTouchInput { cancel() }
            assertEquals("Cancel restores height", initial.height, bounds(id).height, 1f)
            assertEquals(initial.top, bounds(id).top, 1f)
            handle.performTouchInput { swipe(center, center + Offset(0f, 35f * density), 350) }
            compose.runOnIdle { assertEquals(1, writes); assertTrue(scales.getValue(id) > 1f) }
            assertEquals(initial.top, bounds(id).top, 1.5f)
            handle.performSemanticsAction(SemanticsActions.SetProgress) { it(.5f) }
            assertEquals(.5f, scales.getValue(id), 0f)
            handle.performSemanticsAction(SemanticsActions.SetProgress) { it(1f) }
            handle.performSemanticsAction(SemanticsActions.SetProgress) { it(10f) }
            assertEquals("Expansion stops at twice the chosen font size", 2f, scales.getValue(id), 0f)
            handle.performTouchInput { doubleClick() }
            compose.runOnIdle { assertEquals("Double tap restores normal height", 1f, scales.getValue(id), 0f) }
            compose.runOnIdle { writes = 0 }
        }
        val before = bounds("time")
        compose.onNodeWithTag("quick-widget-time").performTouchInput { swipe(Offset(12f * density, 12f * density), Offset(32f * density, 22f * density), 350) }
        val after = bounds("time")
        assertTrue("Body still moves the widget", after.center.x > before.center.x + 5f)
        compose.runOnIdle { editing = false }
        compose.onNodeWithTag("widget-height-handle-time").assertDoesNotExist()
        compose.onNodeWithTag("widget-height-handle-date").assertDoesNotExist()
    }

    @Test fun largeCurvedAndRotatedClocksStayInsideTheirBounds() {
        var scale by mutableFloatStateOf(1f)
        var layout by mutableIntStateOf(1)
        var angle by mutableFloatStateOf(0f)
        var radius by mutableFloatStateOf(0f)
        compose.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides direction) {
                MaterialTheme {
                    Box(Modifier.fillMaxSize().background(Color.Black).testTag("host"), contentAlignment = Alignment.Center) {
                        Box(Modifier.widthIn(max = 280.dp).heightIn(max = 240.dp).testTag("clock")) {
                            ClockText(time, layout, 160.sp, Color.White, fontFamily = FontFamily.Serif,
                                glass = true, heightScale = scale, angle = angle, radius = radius)
                        }
                    }
                }
            }
        }
        awaitVisible("clock")
        for (variant in 1..3) for ((rotation, curve) in listOf(0f to 0f, 45f to 0f, 0f to 250f, 0f to -250f)) {
            compose.runOnIdle { layout = variant; angle = rotation; radius = curve; scale = 1f }
            val width = compose.onNodeWithTag("clock").fetchSemanticsNode().boundsInRoot.width
            for (factor in listOf(.5f, 2f)) {
                compose.runOnIdle { scale = factor }
                val bounds = compose.onNodeWithTag("clock").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
                val host = compose.onNodeWithTag("host").fetchSemanticsNode().boundsInRoot
                assertEquals("Width stays fixed at size boundaries", width, bounds.width, 1f)
                assertTrue(host.contains(bounds.topLeft) && host.contains(bounds.bottomRight))
                val bitmap = capture("clock")
                val rows = runs(bitmap, false)
                assertTrue("No vertical clipping: $direction/$variant/$rotation/$curve/$factor $rows of ${bitmap.height}",
                    rows.isNotEmpty() && rows.first().first > 0 && rows.last().last < bitmap.height - 1)
                bitmap.recycle()
            }
        }
    }

    @Test fun dateScalesWithoutChangingItsWidth() {
        var scale by mutableFloatStateOf(1f)
        var layout by mutableIntStateOf(1)
        compose.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides direction) {
                MaterialTheme {
                    Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
                        Box(Modifier.testTag("date")) {
                            WidgetDate(SettingsUiState(monochrome = true, widgetGlassEffect = false,
                                dateFontSize = 20, dateLayoutId = layout, dateHeightScale = scale), Color.White, false)
                        }
                    }
                }
            }
        }
        awaitVisible("date")
        for (variant in 1..2) {
            compose.runOnIdle { layout = variant; scale = 1f }
            val before = compose.onNodeWithTag("date").fetchSemanticsNode().boundsInRoot
            compose.runOnIdle { scale = 2f }
            val after = compose.onNodeWithTag("date").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            assertEquals(before.width, after.width, 1f)
            assertTrue(after.height > before.height * 1.65f)
            compose.runOnIdle { scale = .5f }
            assertTrue(compose.onNodeWithTag("date").fetchSemanticsNode().boundsInRoot.height < before.height * .8f)
        }
    }

    private fun capture(tag: String): Bitmap {
        compose.waitForIdle()
        // API 24 can report Compose idle while Surface still holds the preceding frame.
        // Wait for native frame callbacks before sampling pixels, not just a fixed delay.
        val drawn = CountDownLatch(1)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val view = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                .first().window.decorView
            view.postInvalidateOnAnimation()
            view.postOnAnimation { view.postOnAnimation { drawn.countDown() } }
        }
        assertTrue("Native frame callbacks completed", drawn.await(5, TimeUnit.SECONDS))
        SystemClock.sleep(200)
        val b = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInWindow
        val screen = requireNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        return Bitmap.createBitmap(screen, b.left.toInt(), b.top.toInt(), b.width.toInt(), b.height.toInt())
            .also { if (it !== screen) screen.recycle() }
    }
    private fun awaitVisible(tag: String) = compose.waitUntil(10_000) {
        val bitmap = capture(tag)
        val ready = runs(bitmap, true).isNotEmpty()
        bitmap.recycle(); ready
    }
    private fun runs(bitmap: Bitmap, horizontal: Boolean): List<IntRange> {
        val length = if (horizontal) bitmap.width else bitmap.height
        val depth = if (horizontal) bitmap.height else bitmap.width
        val result = mutableListOf<IntRange>()
        var start = -1
        for (i in 0..length) {
            val occupied = i < length && (0 until depth).any { j ->
                val pixel = bitmap.getPixel(if (horizontal) i else j, if (horizontal) j else i)
                android.graphics.Color.red(pixel) > 60 && android.graphics.Color.green(pixel) > 60 && android.graphics.Color.blue(pixel) > 60
            }
            if (occupied && start < 0) start = i
            else if (!occupied && start >= 0) { result += start until i; start = -1 }
        }
        return result
    }
}
