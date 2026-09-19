package com.jeerovan.comfer.journals

import android.content.res.Configuration
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class JournalCalendarPickerTest {
    @get:Rule val compose = createComposeRule()

    @Test fun compactScreenUsesCalendarAndConfirmsSelectedLocalDay() {
        val zone = ZoneId.of("America/Los_Angeles")
        val day = LocalDate.of(2026, 9, 19)
        var confirmed: Long? = null
        val config = Configuration(InstrumentationRegistry.getInstrumentation().targetContext.resources.configuration).apply { screenHeightDp = 400 }
        compose.setContent {
            CompositionLocalProvider(LocalConfiguration provides config) {
                MaterialTheme {
                    JournalDateTimeDialog(day.atStartOfDay(zone).toInstant().toEpochMilli(), zone.id, false,
                        onDismiss = {}, onConfirm = { confirmed = it })
                }
            }
        }
        compose.runOnIdle { assertNull(confirmed) }
        compose.onAllNodes(hasSetTextAction()).assertCountEquals(0)
        compose.onNodeWithText("Sunday, September 20, 2026", substring = false).performScrollTo()
        compose.waitForIdle()
        compose.onNodeWithText("Sunday, September 20, 2026", substring = false).assertIsDisplayed().performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Sunday, September 20, 2026", substring = false).assertIsSelected()
        compose.onNodeWithContentDescription("Save").assertDoesNotExist()
        compose.onNodeWithText("Cancel").assertDoesNotExist()
        compose.runOnIdle { assertEquals(day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli(), confirmed) }
    }
    @Test fun tappingHighlightedDayConfirmsWithoutChangingDate() {
        val zone = ZoneId.of("Asia/Kolkata")
        val value = LocalDate.of(2026, 9, 19).atStartOfDay(zone).toInstant().toEpochMilli()
        var confirmed: Long? = null
        compose.setContent {
            MaterialTheme {
                JournalDateTimeDialog(value, zone.id, false, {}, { confirmed = it })
            }
        }
        compose.runOnIdle { assertNull(confirmed) }
        compose.onNode(isSelected() and hasClickAction()).performScrollTo().performClick()
        compose.runOnIdle { assertEquals(value, confirmed) }
    }

    @Test fun calendarSelectionAdvancesToTimeWithoutConfirmingTimestamp() {
        val zone = ZoneId.of("Asia/Kolkata")
        val value = LocalDate.of(2026, 9, 19).atTime(14, 35).atZone(zone).toInstant().toEpochMilli()
        var confirmed: Long? = null
        compose.setContent {
            MaterialTheme {
                JournalDateTimeDialog(value, zone.id, true, {}, { confirmed = it })
            }
        }
        compose.onNode(isSelected() and hasClickAction()).performScrollTo().performClick()
        compose.onNodeWithTag("journal-date-dialog").assertDoesNotExist()
        compose.onNodeWithTag("journal-time-dialog").assertIsDisplayed()
        compose.runOnIdle { assertNull(confirmed) }
        compose.onNodeWithContentDescription("Save").performClick()
        compose.runOnIdle { assertEquals(value, confirmed) }
    }

    @Test fun backDismissesWithoutSelectingDate() {
        var dismissed = false
        var confirmed: Long? = null
        compose.setContent {
            MaterialTheme {
                JournalDateTimeDialog(0L, "UTC", false, { dismissed = true }, { confirmed = it })
            }
        }
        androidx.test.espresso.Espresso.pressBack()
        compose.runOnIdle {
            assertTrue(dismissed)
            assertNull(confirmed)
        }
    }
}
