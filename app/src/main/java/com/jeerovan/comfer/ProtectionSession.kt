package com.jeerovan.comfer

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Shared verification: active modules stay unlocked; only time away counts. */
object ProtectionSession {
    const val PREFERENCE_KEY = "protection_timeout_seconds"
    const val DEFAULT_SECONDS = 120
    val options = listOf(30, DEFAULT_SECONDS, 300)
    private var context: Context? = null
    private val window = ProtectionWindow(SystemClock::elapsedRealtime)
    private var handler: Handler? = null
    private var keys: ProtectedContentKeys? = null
    private val expire = Runnable { authorized() }

    @Synchronized fun initialize(context: Context) {
        this.context = context.applicationContext
        if (handler == null) handler = Handler(Looper.getMainLooper())
        if (keys == null) keys = ProtectedContentKeys(java.io.File(context.noBackupFilesDir, "protected-content.key"), KeystoreRootKeyWrapper())
    }
    fun timeoutSeconds(): Int = validTimeout(context?.let {
        PreferenceManager.getInt(it, PREFERENCE_KEY, DEFAULT_SECONDS)
    } ?: DEFAULT_SECONDS)
    internal fun validTimeout(seconds: Int) = seconds.takeIf { it in options } ?: DEFAULT_SECONDS
    @Synchronized fun authorize() { window.authorize(timeoutSeconds()); schedule() }
    @Synchronized fun lock() { window.lock(); keys?.clear(); schedule() }
    @Synchronized fun authorized(): Boolean {
        val valid = window.authorized(timeoutSeconds())
        if (!valid) keys?.clear()
        schedule()
        return valid
    }
    @Synchronized fun enter(owner: Any) {
        if (!window.enter(owner, timeoutSeconds())) keys?.clear()
        schedule()
    }
    @Synchronized fun leave(owner: Any) { window.leave(owner); schedule() }
    private fun schedule() {
        handler?.removeCallbacks(expire)
        window.remainingMillis()?.let { handler?.postDelayed(expire, it.coerceAtLeast(1)) }
    }
    @Synchronized internal fun seal(bytes: ByteArray, namespace: String, identity: String): ByteArray {
        check(authorized()) { "Unlock protected content first" }
        return checkNotNull(keys).seal(bytes, namespace, identity)
    }
    @Synchronized internal fun open(bytes: ByteArray, namespace: String, identity: String): ByteArray {
        check(authorized()) { "Unlock protected content first" }
        return checkNotNull(keys).open(bytes, namespace, identity)
    }
    /** Called only after a successful native credential result. Warm both modules while
     * Keystore authentication is fresh, so switching modules later never needs a second PIN. */
    suspend fun completeAuthentication(context: Context) = withContext(Dispatchers.IO) {
        StartupCoordinator.awaitReady()
        val preparing = Any()
        enter(preparing)
        authorize()
        try {
            synchronized(this@ProtectionSession) { checkNotNull(keys).prepare() }
            com.jeerovan.comfer.notes.NotesStore(com.jeerovan.comfer.notes.NotesDatabase.get(context)).upgradeProtectedContent()
            com.jeerovan.comfer.journals.JournalDatabase.get(context).dao().upgradeProtectedContent()
        } catch (error: Exception) { lock(); throw error }
        finally { leave(preparing) }
    }
}

/** Clock-injected policy; entering a screen cannot revive expired authentication. */
internal class ProtectionWindow(private val now: () -> Long) {
    private val owners = mutableSetOf<Any>()
    private var verified = false
    private var leftAt: Long? = null
    private var timeoutSeconds = ProtectionSession.DEFAULT_SECONDS

    @Synchronized fun authorize(seconds: Int) {
        timeoutSeconds = ProtectionSession.validTimeout(seconds)
        verified = true
        leftAt = if (owners.isEmpty()) now() else null
    }
    @Synchronized fun lock() { verified = false; leftAt = null }
    @Synchronized fun enter(owner: Any, seconds: Int): Boolean {
        val valid = authorized(seconds)
        owners.add(owner)
        if (valid) leftAt = null
        return valid
    }
    @Synchronized fun leave(owner: Any) {
        if (owners.remove(owner) && owners.isEmpty() && verified) leftAt = now()
    }
    @Synchronized fun authorized(seconds: Int): Boolean {
        if (!verified) return false
        val elapsed = leftAt?.let { now() - it }
        if (elapsed != null && (elapsed < 0 || elapsed >= timeoutSeconds * 1000L)) { lock(); return false }
        timeoutSeconds = ProtectionSession.validTimeout(seconds)
        if (elapsed != null && elapsed >= timeoutSeconds * 1000L) { lock(); return false }
        return true
    }
    @Synchronized fun remainingMillis(): Long? = if (verified) leftAt?.let { timeoutSeconds * 1000L - (now() - it) } else null
}
