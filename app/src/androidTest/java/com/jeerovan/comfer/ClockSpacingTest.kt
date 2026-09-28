package com.jeerovan.comfer

import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.Typeface
import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.text.font.FontFamily
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
class ClockSpacingTest(private val direction: LayoutDirection) {
    @get:Rule val compose = createComposeRule()
    companion object {
        @JvmStatic @Parameterized.Parameters(name = "{0}")
        fun directions() = listOf(arrayOf(LayoutDirection.Ltr), arrayOf(LayoutDirection.Rtl))
    }

    @Test fun allVariantsHaveVisibleGapsAndProportionalMinutesWithoutClipping() {
        var variant by mutableIntStateOf(1)
        var fontSize by mutableIntStateOf(64)
        var fontScale by mutableFloatStateOf(1f)
        var family by mutableStateOf<FontFamily>(FontFamily.Default)
        var time by mutableStateOf(if (direction == LayoutDirection.Rtl) "١٢:٣٤" else "12:34")
        var glass by mutableStateOf(false)
        val families = listOf(FontFamily.Default, FontFamily.Serif,
            FontFamily(Typeface.create("serif", Typeface.BOLD_ITALIC)))
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalLayoutDirection provides direction,
                LocalDensity provides Density(density.density, fontScale)) {
                MaterialTheme {
                    Box(Modifier.fillMaxSize().background(Color.Black).testTag("host"), contentAlignment = Alignment.Center) {
                        Box(Modifier.widthIn(max = 280.dp).heightIn(max = 270.dp).testTag("clock")) {
                            ClockText(time, variant, fontSize.sp, Color.White,
                                fontFamily = family, fontWeight = FontWeight.Bold, glass = glass)
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        // Wait for the activity's first frame, not just its Compose tree.
        compose.waitUntil(10_000) {
            val bitmap = capture()
            val ready = inkRuns(bitmap, horizontal = true).isNotEmpty()
            bitmap.recycle()
            ready
        }
        for (face in families) for (size in listOf(30, 150)) for (layout in 1..3) {
            compose.runOnIdle {
                family = face; fontSize = size; variant = layout; fontScale = if (size == 150) 1.5f else 1f
                // Identical digits isolate spacing from differences in the font's glyph shapes.
                time = if (layout != 1) { if (direction == LayoutDirection.Rtl) "٨٨:٨٨" else "88:88" }
                    else if (direction == LayoutDirection.Rtl) "١٢:٣٤" else "12:34"
            }
            val bitmap = capture()
            val columns = inkRuns(bitmap, horizontal = true)
            val rows = inkRuns(bitmap, horizontal = false)
            val evidence = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "clock-spacing-evidence").apply { mkdirs() }
            File(evidence, "spacing-$direction-$size-$layout-${families.indexOf(face)}.png").outputStream().use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            if (layout != 3) {
                assertEquals("One visible group per character: layout=$layout size=$size family=$face groups=$columns", if (layout == 1) 5 else 4, columns.size)
                columns.zipWithNext().forEach { (a, b) ->
                    assertTrue("Every character including colon needs a clear gap", b.first - a.last > 1)
                }
                if (layout == 2) {
                    val centers = columns.map { (it.first + it.last) / 2f }
                    val gaps = centers.zipWithNext { a, b -> b - a }
                    assertTrue("No-colon digits have equal spacing, including hours/minutes: $gaps",
                        gaps.max() - gaps.min() <= 2f)
                }
            } else {
                assertEquals("Two separated lines", 2, rows.size)
                val hour = rows[0].last - rows[0].first + 1
                val minute = rows[1].last - rows[1].first + 1
                assertTrue("Minutes visibly smaller: $minute / $hour", minute.toFloat() / hour in .50f.. .86f)
                for (row in rows) {
                    val line = Bitmap.createBitmap(bitmap, 0, row.first, bitmap.width, row.last - row.first + 1)
                    val groups = inkRuns(line, horizontal = true)
                    assertEquals(2, groups.size)
                    assertTrue(groups[1].first - groups[0].last > 1)
                    line.recycle()
                }
            }
            assertTrue("Ink clear of horizontal edges", columns.first().first > 0 && columns.last().last < bitmap.width - 1)
            assertTrue("Ink clear of vertical edges", rows.first().first > 0 && rows.last().last < bitmap.height - 1)
            val host = compose.onNodeWithTag("host").fetchSemanticsNode().boundsInRoot
            val clock = compose.onNodeWithTag("clock").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            assertEquals(host.center.x, clock.center.x, .6f)
            assertTrue(host.contains(clock.topLeft) && host.contains(clock.bottomRight))
            compose.onAllNodesWithText(time).assertCountEquals(1)
            bitmap.recycle()
        }
        // The restored paragraph painter must fit the same slots, even with italic overhangs.
        for (layout in 1..3) {
            compose.runOnIdle {
                glass = true; variant = layout; fontSize = 64; fontScale = 1f; family = families.last()
                time = if (direction == LayoutDirection.Rtl) "٨٨:٨٨" else "88:88"
            }
            val bitmap = capture()
            if (layout == 3) {
                val rows = inkRuns(bitmap, false, 20)
                assertEquals("Glass keeps two separated lines", 2, rows.size)
                for (row in rows) {
                    val line = Bitmap.createBitmap(bitmap, 0, row.first, bitmap.width, row.last - row.first + 1)
                    assertEquals("Glass keeps digit gaps", 2, inkRuns(line, true, 20).size)
                    line.recycle()
                }
            } else assertEquals("Glass keeps all character gaps", if (layout == 1) 5 else 4,
                inkRuns(bitmap, true, 20).size)
            bitmap.recycle()
        }
        compose.runOnIdle { variant = 1; fontSize = 64; fontScale = 1f; time = "11:11" }
        val first = compose.onNodeWithTag("clock").fetchSemanticsNode().boundsInRoot
        compose.runOnIdle { time = "88:88" }
        assertEquals("Minute ticks keep the same numeral slots", first, compose.onNodeWithTag("clock").fetchSemanticsNode().boundsInRoot)
    }

    @Test fun glassCurvesRotationAndOpacityPreserveTheClockEnvelope() {
        var glass by mutableStateOf(false)
        var radius by mutableFloatStateOf(0f)
        var angle by mutableFloatStateOf(0f)
        var variant by mutableIntStateOf(1)
        var color by mutableStateOf(Color.White)
        val time = if (direction == LayoutDirection.Rtl) "١٢:٣٤" else "12:34"
        compose.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides direction) {
                MaterialTheme {
                    Box(Modifier.fillMaxSize().background(Color.Black).testTag("host"), contentAlignment = Alignment.Center) {
                        Box(Modifier.widthIn(max = 280.dp).heightIn(max = 270.dp).testTag("clock")) {
                            ClockText(time, variant, 80.sp, color, fontFamily = FontFamily.Serif,
                                fontWeight = FontWeight.Bold, radius = radius, angle = angle, glass = glass)
                        }
                    }
                }
            }
        }
        for (curve in listOf(-250f, 0f, 250f)) for (layout in 1..3) {
            compose.runOnIdle { radius = curve; angle = if (curve == 0f) 45f else 0f; variant = layout; glass = false }
            val bounds = compose.onNodeWithTag("clock").fetchSemanticsNode().boundsInRoot
            val solid = capture()
            compose.runOnIdle { glass = true }
            val shaded = capture()
            assertEquals(bounds, compose.onNodeWithTag("clock").fetchSemanticsNode().boundsInRoot)
            assertFalse("Glass still changes the digits", solid.sameAs(shaded))
            val dir = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "clock-spacing-evidence").apply { mkdirs() }
            File(dir, "$direction-$curve-$layout.png").outputStream().use { shaded.compress(Bitmap.CompressFormat.PNG, 100, it) }
            solid.recycle(); shaded.recycle()
        }
        compose.runOnIdle { color = Color.Transparent }
        val transparent = capture()
        assertTrue("Zero opacity hides every glyph and rim", inkRuns(transparent, true).isEmpty())
        transparent.recycle()
    }

    @Test fun actualFontInkIncludingOverhangsFitsItsAssignedCell() {
        for (face in listOf(Typeface.DEFAULT, Typeface.create("serif", Typeface.BOLD_ITALIC))) {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = face; textSize = 180f }
            for (value in listOf("11:88", "١١:٨٨", "11 88", "1234", "١٢٣٤")) {
                val plan = measureClockLine(value, paint, 2f, 0f, 0f)
                if (value.all(Char::isDigit)) {
                    val steps = plan.geometry.glyphs.zipWithNext { a, b -> b.x - a.x }
                    assertEquals("Mixed-width numerals keep uniform slots", steps.min(), steps.max(), .01f)
                }
                for (glyph in plan.geometry.glyphs) {
                    val ink = android.graphics.Rect()
                    paint.getTextBounds(glyph.cell.text, 0, glyph.cell.text.length, ink)
                    assertTrue(ink.left - glyph.cell.inkCenter >= -glyph.cell.width / 2 - .01f)
                    assertTrue(ink.right - glyph.cell.inkCenter <= glyph.cell.width / 2 + .01f)
                }
            }
        }
    }

    @Test fun straightClockKeepsTheOriginalGlassLighting() {
        val digit = if (direction == LayoutDirection.Rtl) "٨" else "8"
        var color by mutableStateOf(Color.White)
        var shadow by mutableStateOf(Color.Black)
        compose.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides direction) {
                MaterialTheme {
                    Row(Modifier.fillMaxSize().background(Color.Black).testTag("comparison"),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.testTag("reference").padding(8.dp)) {
                            EffectTextBlock(digit, 80.sp, color,
                                shadowColor = shadow.toArgb(), glass = true)
                        }
                        Box(Modifier.testTag("clock").padding(8.dp)) {
                            ClockText(digit, 1, 80.sp, color, shadowColor = shadow, glass = true)
                        }
                    }
                }
            }
        }
        // Compare the lighting down the glyph, independently of spacing and paragraph padding.
        fun profile(bitmap: Bitmap): List<Float> = (0 until bitmap.height).map { y ->
            val pixels = (0 until bitmap.width).map { x -> android.graphics.Color.red(bitmap.getPixel(x, y)) }
                .filter { it > 20 }.sorted()
            if (pixels.size < 3) 0f else pixels[pixels.size / 2].toFloat()
        }.let { rows -> rows.dropWhile { it == 0f }.dropLastWhile { it == 0f } }
        for ((tint, backing) in listOf(Color.White to Color.Black,
            Color.White.copy(alpha = .7f) to Color.Black,
            Color(0xffd6e3c4) to Color(0xff302340))) {
            compose.runOnIdle { color = tint; shadow = backing }
            val host = compose.onNodeWithTag("comparison").fetchSemanticsNode().boundsInRoot
            for (tag in listOf("reference", "clock")) {
                val bounds = compose.onNodeWithTag(tag).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
                assertTrue("Comparison visible in portrait and landscape: $tag",
                    host.contains(bounds.topLeft) && host.contains(bounds.bottomRight))
            }
            // API 24 can report Compose idle before the new activity's first Surface frame.
            compose.waitUntil(10_000) {
                val frame = capture("reference")
                val columns = inkRuns(frame, true, 20)
                val rows = inkRuns(frame, false, 20)
                val ready = columns.isNotEmpty() && rows.isNotEmpty() &&
                    columns.first().first > 0 && columns.last().last < frame.width - 1 &&
                    rows.first().first > 0 && rows.last().last < frame.height - 1
                if (!ready) {
                    val out = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,
                        "clock-spacing-evidence").apply { mkdirs() }
                    File(out, "comparison-wait-$direction.png").outputStream().use {
                        frame.compress(Bitmap.CompressFormat.PNG, 100, it)
                    }
                }
                frame.recycle()
                ready
            }
            val reference = capture("reference")
            val actual = capture()
            val evidence = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,
                "clock-spacing-evidence").apply { mkdirs() }
            for ((name, bitmap) in listOf("reference" to reference, "restored" to actual)) {
                File(evidence, "glass-$direction-${tint.alpha}-$name.png").outputStream().use {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                }
            }
            val before = profile(reference)
            val after = profile(actual)
            assertTrue("Same visible glyph height", kotlin.math.abs(before.size - after.size) <= 1)
            val difference = before.indices.map { i ->
                kotlin.math.abs(before[i] - after[(i * after.size / before.size).coerceAtMost(after.lastIndex)])
            }.average()
            reference.recycle(); actual.recycle()
            assertTrue("Keep pre-spacing glass lighting for $tint; average difference=$difference", difference < 6.0)
        }
    }

    private fun capture(tag: String = "clock"): Bitmap {
        compose.waitForIdle()
        SystemClock.sleep(250)
        val bounds = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInWindow
        val screen = requireNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        return Bitmap.createBitmap(screen, bounds.left.toInt(), bounds.top.toInt(), bounds.width.toInt(), bounds.height.toInt())
            .also { if (it !== screen) screen.recycle() }
    }

    private fun inkRuns(bitmap: Bitmap, horizontal: Boolean, threshold: Int = 100): List<IntRange> {
        val length = if (horizontal) bitmap.width else bitmap.height
        val depth = if (horizontal) bitmap.height else bitmap.width
        val occupied = (0 until length).map { i -> (0 until depth).any { j ->
            val pixel = bitmap.getPixel(if (horizontal) i else j, if (horizontal) j else i)
            android.graphics.Color.red(pixel) > threshold && android.graphics.Color.green(pixel) > threshold && android.graphics.Color.blue(pixel) > threshold
        } }
        val runs = mutableListOf<IntRange>()
        var start = -1
        for (i in 0..length) {
            if (i < length && occupied[i]) { if (start < 0) start = i }
            else if (start >= 0) { runs += start until i; start = -1 }
        }
        return runs
    }
}
