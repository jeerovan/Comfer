package com.jeerovan.comfer

import android.os.Build
import android.util.DisplayMetrics
import android.content.ActivityNotFoundException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CrashMitigationPolicyTest {
    @Test
    fun onlyExactHonorPowerSaveActivityIsRedirected() {
        assertEquals(
            HonorPowerSaveCompatibilityActivity::class.java.name,
            compatibilityActivityClassName(
                "com.hihonor.android.launcher.powersavemode.PowerSaveModeLauncher"
            ),
        )
        assertNull(compatibilityActivityClassName(SubscriptionActivity::class.java.name))
        assertNull(
            compatibilityActivityClassName(
                "com.hihonor.android.launcher.powersavemode.OtherActivity"
            )
        )
    }

    @Test
    fun workManagerRequiresNamespaceApiOnlyFromAndroid14() {
        assertTrue(
            isWorkManagerRuntimeSupported(
                sdkInt = Build.VERSION_CODES.TIRAMISU,
                hasNamespaceMethod = false,
            )
        )
        assertFalse(
            isWorkManagerRuntimeSupported(
                sdkInt = Build.VERSION_CODES.UPSIDE_DOWN_CAKE,
                hasNamespaceMethod = false,
            )
        )
        assertTrue(
            isWorkManagerRuntimeSupported(
                sdkInt = Build.VERSION_CODES.UPSIDE_DOWN_CAKE,
                hasNamespaceMethod = true,
            )
        )
    }

    @Test
    fun launcherIconDensityIsBoundedToUsefulRange() {
        assertEquals(
            DisplayMetrics.DENSITY_DEFAULT,
            requestedLauncherIconDensity(0),
        )
        assertEquals(240, requestedLauncherIconDensity(240))
        assertEquals(
            DisplayMetrics.DENSITY_XHIGH,
            requestedLauncherIconDensity(DisplayMetrics.DENSITY_XXXHIGH),
        )
    }

    @Test
    fun cachedWidgetViewCanOnlyBeReusedAfterDetach() {
        assertTrue(canReuseCachedWidgetView(hasParent = false))
        assertFalse(canReuseCachedWidgetView(hasParent = true))
    }

    @Test
    fun documentPickerLaunchFailuresAreContained() {
        assertTrue(launchDocumentPickerSafely {})
        assertFalse(
            launchDocumentPickerSafely {
                throw ActivityNotFoundException("no documents provider")
            }
        )
        assertFalse(
            launchDocumentPickerSafely {
                throw SecurityException("blocked by device policy")
            }
        )
    }
}
