package com.jeerovan.comfer

import org.junit.Assert.*
import org.junit.Test

class WidgetHeightTest {
    @Test fun boundsAndInvalidRestoredValuesAreSafe() {
        assertEquals(.5f, widgetHeightScale(-10f), 0f)
        assertEquals(2f, widgetHeightScale(50f), 0f)
        for (value in listOf(.5f, 1f, 1.5f, 2f)) assertEquals(value, widgetHeightScale(value), 0f)
        for (value in listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY))
            assertEquals(1f, widgetHeightScale(value), 0f)
    }
    @Test fun dateAndTimeHaveIndependentSettings() {
        assertNotEquals(widgetHeightPreferenceKey("date"), widgetHeightPreferenceKey("time"))
        assertEquals(1f, SettingsUiState().dateHeightScale, 0f)
        assertEquals(1f, SettingsUiState().timeHeightScale, 0f)
    }
    @Test(expected = IllegalArgumentException::class)
    fun unrelatedWidgetsCannotAcquireAHeightSetting() { widgetHeightPreferenceKey("battery") }
}
