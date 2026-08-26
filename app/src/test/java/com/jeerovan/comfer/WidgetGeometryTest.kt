package com.jeerovan.comfer

import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetGeometryTest {
    @Test
    fun oversizedWidgetClampsWithoutAnInvertedRange() {
        assertEquals(0f, clampToAvailableRange(20f, 0f, -100f), 0f)
        assertEquals(8f, clampToAvailableRange(20f, 8f, 4f), 0f)
    }

    @Test
    fun oversizedSpanUsesFirstGridColumn() {
        assertEquals(0, maximumWidgetGridStart(gridColumns = 4, spanColumns = 6))
        assertEquals(2, maximumWidgetGridStart(gridColumns = 4, spanColumns = 2))
    }
}
