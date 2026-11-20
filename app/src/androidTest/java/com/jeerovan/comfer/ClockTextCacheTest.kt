package com.jeerovan.comfer

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ClockTextCacheTest {
    @get:Rule val compose = createComposeRule()

    @Test fun minuteChangesReuseBothNumeralSizesAndConfigurationChangesStayFresh() {
        lateinit var measurer: TextMeasurer
        val density = mutableStateOf(Density(2f, 1f))
        val direction = mutableStateOf(LayoutDirection.Ltr)
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides density.value,
                LocalLayoutDirection provides direction.value) {
                val current = rememberClockTextMeasurer()
                SideEffect { measurer = current }
                Box(Modifier.testTag("clock-cache-host"))
            }
        }
        fun layout(text: String, size: Float, family: FontFamily = FontFamily.Default) =
            measurer.measure(text, style = TextStyle(fontSize = size.sp,
                fontFamily = family, fontWeight = FontWeight.Normal),
                softWrap = false, overflow = TextOverflow.Visible)
        for (rtl in listOf(false, true)) {
            compose.runOnIdle { direction.value = if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr }
            compose.runOnIdle {
                val digits = if (rtl) "٠١٢٣٤٥٦٧٨٩" else "0123456789"
                // Across minute ticks either row can use any numeral. Revisit the earliest
                // paragraph after all glyphs: repeated shaping here is avoidable churn.
                val warmed = listOf(80f, 80f * CLOCK_MINUTE_SCALE).flatMap { size ->
                    (digits + ":").map { glyph -> Triple(glyph.toString(), size, layout(glyph.toString(), size)) }
                }
                for ((text, size, first) in warmed) {
                    val again = layout(text, size)
                    assertSame("Reuse shaped $text at $size in $direction", first.multiParagraph, again.multiParagraph)
                    assertEquals(first.size, again.size)
                }
                val original = layout("8", 80f)
                val changed = layout("8", 80f, FontFamily.Serif)
                assertNotSame("Font changes must not reuse old paragraphs", original.multiParagraph, changed.multiParagraph)
            }
        }
        lateinit var oldLayout: TextLayoutResult
        val oldMeasurer = measurer
        compose.runOnIdle { oldLayout = layout("8", 80f); density.value = Density(2f, 1.5f) }
        // Wait for the changed composition locals to be committed, not just the old
        // empty fixture's idle state, before measuring through the published helper.
        compose.waitUntil(5_000) { measurer !== oldMeasurer }
        compose.runOnIdle {
            val updated = layout("8", 80f)
            val uncached = measurer.measure("8", style = TextStyle(fontSize = 80.sp,
                fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal),
                softWrap = false, overflow = TextOverflow.Visible, skipCache = true)
            assertEquals(1.5f, updated.layoutInput.density.fontScale)
            assertNotSame("Config changes must discard the old paragraph", oldLayout.multiParagraph, updated.multiParagraph)
            assertEquals("Cache must match fresh SDK layout at the new scale", uncached.size, updated.size)
            assertEquals(uncached.firstBaseline, updated.firstBaseline, .01f)
            println("Clock scale probe: old=${oldLayout.size}, updated=${updated.size}, uncached=${uncached.size}")
        }
    }
}
