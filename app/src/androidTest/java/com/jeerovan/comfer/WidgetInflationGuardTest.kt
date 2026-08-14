package com.jeerovan.comfer

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WidgetInflationGuardTest {
    @Test
    fun twoSlowStrikesQuarantineAndManualRetryClearsProvider() {
        val context = ApplicationProvider.getApplicationContext<ComferApp>()
        val provider = "stress.fixture/.SlowWidgetProvider"
        WidgetInflationGuard.clear(context, provider)
        try {
            WidgetInflationGuard.recordDuration(
                context,
                provider,
                WidgetInflationGuard.SLOW_THRESHOLD_MS - 1,
            )
            assertFalse(WidgetInflationGuard.isQuarantined(context, provider))

            WidgetInflationGuard.recordDuration(
                context,
                provider,
                WidgetInflationGuard.SLOW_THRESHOLD_MS,
            )
            assertFalse(WidgetInflationGuard.isQuarantined(context, provider))

            WidgetInflationGuard.recordDuration(
                context,
                provider,
                WidgetInflationGuard.SLOW_THRESHOLD_MS,
            )
            assertTrue(WidgetInflationGuard.isQuarantined(context, provider))

            WidgetInflationGuard.clear(context, provider)
            assertFalse(WidgetInflationGuard.isQuarantined(context, provider))
        } finally {
            WidgetInflationGuard.clear(context, provider)
        }
    }
}
