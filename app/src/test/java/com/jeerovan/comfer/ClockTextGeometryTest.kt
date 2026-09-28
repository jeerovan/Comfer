package com.jeerovan.comfer

import org.junit.Assert.*
import org.junit.Test

class ClockTextGeometryTest {
    private fun cells(text: String) = text.map {
        ClockGlyphCell(it.toString(), when (it) { ':' -> 9f; ' ' -> 22f; else -> 70f }, -8f)
    }

    @Test fun digitsAndColonKeepMinimumSeparationEvenOnTightReverseCurves() {
        for (text in listOf("11:88", "١١:٨٨", "11 88")) for (radius in listOf(-250f, -150f, 0f, 150f, 250f)) {
            val result = clockLineGeometry(cells(text), -100f, 30f, 5f, radius)
            val boxes = result.glyphs.map { clockGlyphBounds(it, -100f, 30f) }
            boxes.zipWithNext().forEach { (a, b) ->
                assertTrue("$text radius=$radius: ${b.left - a.right}", b.left - a.right >= 4.99f)
            }
            assertEquals(text.filterNot(Char::isWhitespace), result.glyphs.joinToString("") { it.cell.text })
        }
    }

    @Test fun separatorSpaceIsExplicitAndRotatedBoundsContainEveryGlyph() {
        val plain = clockLineGeometry(cells("12 34"), -100f, 30f, 5f)
        val bounds = plain.glyphs.map { clockGlyphBounds(it, -100f, 30f) }
        assertEquals(32f, bounds[2].left - bounds[1].right, .01f)
        for (radius in listOf(-250f, 0f, 250f)) for (angle in listOf(0f, 45f, 90f, 180f, 270f, 360f)) {
            val layout = clockLineGeometry(cells("12:34"), -100f, 30f, 5f, radius, angle)
            layout.glyphs.map { clockGlyphBounds(it, -100f, 30f) }.forEach { box ->
                assertTrue(box.left >= -.01f && box.top >= -.01f)
                assertTrue(box.right <= layout.width + .01f && box.bottom <= layout.height + .01f)
            }
        }
    }

    @Test fun emptyClockDuringInitializationHasFiniteBounds() {
        val result = clockLineGeometry(emptyList(), -30f, 10f, 2f)
        assertEquals(0f, result.width, 0f)
        assertEquals(40f, result.height, 0f)
        assertTrue(result.glyphs.isEmpty())
    }
}
