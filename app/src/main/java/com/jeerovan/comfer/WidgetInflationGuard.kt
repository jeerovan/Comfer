package com.jeerovan.comfer

import android.content.Context

internal object WidgetInflationGuard {
    private const val PREFS_NAME = "widget_inflation_health"
    private const val STRIKE_PREFIX = "slow_strikes:"
    private const val QUARANTINE_AFTER_STRIKES = 2
    const val SLOW_THRESHOLD_MS = 1_000L

    /**
     * These OEM RemoteViews start asynchronous vendor code inside the launcher
     * process and then throw uncaught SecurityException failures. Since that work
     * happens after createView returns, it cannot be caught at the host boundary.
     */
    fun isKnownUnsafe(providerName: String): Boolean {
        val normalized = providerName.lowercase()
        return UNSAFE_PROVIDER_PREFIXES.any(normalized::startsWith)
    }

    private val UNSAFE_PROVIDER_PREFIXES = listOf(
        "com.hihonor.calendar/",
        "com.hihonor.gallery/",
        "com.huawei.android.totemweather/",
        "com.android.calendar/",
    )

    fun isQuarantined(context: Context, providerName: String): Boolean {
        return preferences(context).getInt(STRIKE_PREFIX + providerName, 0) >=
            QUARANTINE_AFTER_STRIKES
    }

    fun recordDuration(context: Context, providerName: String, durationMs: Long) {
        if (durationMs < SLOW_THRESHOLD_MS) return
        val prefs = preferences(context)
        val key = STRIKE_PREFIX + providerName
        prefs.edit().putInt(key, prefs.getInt(key, 0) + 1).apply()
    }

    fun clear(context: Context, providerName: String) {
        preferences(context).edit().remove(STRIKE_PREFIX + providerName).apply()
    }

    private fun preferences(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
