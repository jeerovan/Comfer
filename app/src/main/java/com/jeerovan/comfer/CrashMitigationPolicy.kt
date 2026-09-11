package com.jeerovan.comfer

import android.app.Activity
import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.DisplayMetrics
import androidx.annotation.RequiresApi
import androidx.core.app.CoreComponentFactory

private const val HONOR_POWER_SAVE_ACTIVITY =
    "com.hihonor.android.launcher.powersavemode.PowerSaveModeLauncher"

/**
 * Honor firmware can ask Comfer's class loader to instantiate this launcher
 * activity. Redirect only that exact, telemetry-proven class name.
 */
internal fun compatibilityActivityClassName(requestedClassName: String): String? =
    if (requestedClassName == HONOR_POWER_SAVE_ACTIVITY) {
        HonorPowerSaveCompatibilityActivity::class.java.name
    } else {
        null
    }

internal fun isWorkManagerRuntimeSupported(
    sdkInt: Int,
    hasNamespaceMethod: Boolean,
): Boolean = sdkInt < Build.VERSION_CODES.UPSIDE_DOWN_CAKE || hasNamespaceMethod

internal fun requestedLauncherIconDensity(actualDensityDpi: Int): Int =
    actualDensityDpi.coerceIn(
        DisplayMetrics.DENSITY_DEFAULT,
        DisplayMetrics.DENSITY_XHIGH,
    )

internal fun canReuseCachedWidgetView(hasParent: Boolean): Boolean = !hasParent

internal inline fun launchDocumentPickerSafely(launch: () -> Unit): Boolean =
    try {
        launch()
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    }

@RequiresApi(Build.VERSION_CODES.P)
@SuppressLint("RestrictedApi")
class ComferAppComponentFactory : CoreComponentFactory() {
    override fun instantiateActivity(
        classLoader: ClassLoader,
        className: String,
        intent: Intent?,
    ): Activity {
        return if (compatibilityActivityClassName(className) != null) {
            HonorPowerSaveCompatibilityActivity()
        } else {
            super.instantiateActivity(classLoader, className, intent)
        }
    }
}

/**
 * No UI or Honor behavior is emulated. This activity exists only to terminate a
 * malformed OEM task that was delivered to Comfer's process/class loader.
 */
class HonorPowerSaveCompatibilityActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (isTaskRoot) {
            finishAndRemoveTask()
        } else {
            finish()
        }
    }
}
