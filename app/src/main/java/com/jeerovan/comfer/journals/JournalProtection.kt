package com.jeerovan.comfer.journals

import android.app.KeyguardManager
import android.content.Context

/** Device credentials guard presentation; Android private storage guards files. This is not a
 * separate at-rest database cipher. Portable exports use their own password-derived encryption. */
object JournalProtection {
    @Volatile private var authorizedUntil = 0L
    fun authorize() { authorizedUntil = android.os.SystemClock.elapsedRealtime() + 300_000 }
    fun requireAuthorization() { check(android.os.SystemClock.elapsedRealtime() < authorizedUntil) { "Unlock Journal before this operation" } }

    fun enabled(context: Context) = context.getSharedPreferences("journal-privacy", Context.MODE_PRIVATE).getBoolean("locked", false)
    internal fun restoreState(context: Context, enabled: Boolean) {
        check(context.getSharedPreferences("journal-privacy", Context.MODE_PRIVATE).edit().putBoolean("locked", enabled).commit())
    }
    fun setEnabled(context: Context, enabled: Boolean) {
        if (enabled) require(context.getSystemService(KeyguardManager::class.java).isDeviceSecure) { "Set a device PIN, pattern or password first" }
        restoreState(context, enabled)
    }
}
