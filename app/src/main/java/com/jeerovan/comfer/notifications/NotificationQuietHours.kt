package com.jeerovan.comfer.notifications

import android.app.AlarmManager
import android.app.AutomaticZenRule
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.service.notification.Condition
import android.service.notification.ConditionProviderService
import com.jeerovan.comfer.R
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class QuietHealth { OFF, ACTIVE, SCHEDULED, LOCAL_ONLY, UNAVAILABLE, ACCESS_NEEDED, SYSTEM_DISABLED, CLEANUP_NEEDED, FAILED }
data class QuietStatus(val health: QuietHealth = QuietHealth.OFF, val nextBoundary: Long? = null, val focusUntil: Long = 0)

/** Owns one automatic rule. Never sets global interruption filters or another owner's policy. */
object NotificationQuietHours {
    private val mutable = MutableStateFlow(QuietStatus())
    val state = mutable.asStateFlow()
    private val monitor = Any()
    private fun owner(context: Context) = ComponentName(context, ComferQuietConditionProvider::class.java)
    private fun conditionId(context: Context) = Uri.parse("condition://${context.packageName}/notification-quiet")
    private fun preferences(context: Context) = context.getSharedPreferences("notification_quiet_runtime", Context.MODE_PRIVATE)
    private fun bootCount(context: Context) = android.provider.Settings.Global.getInt(context.contentResolver, "boot_count", -1)

    suspend fun focus(context: Context, minutes: Int): Boolean = withContext(Dispatchers.IO) {
        synchronized(monitor) {
            if (minutes !in 1..1440) return@synchronized false
            val manager = context.getSystemService(NotificationManager::class.java)
            if (!manager.isNotificationPolicyAccessGranted) { mutable.value = QuietStatus(QuietHealth.ACCESS_NEEDED); return@synchronized false }
            if (!preferences(context).edit()
                    .putLong("focus_until", System.currentTimeMillis() + minutes * 60_000L)
                    .putLong("focus_elapsed", android.os.SystemClock.elapsedRealtime() + minutes * 60_000L)
                    .putInt("focus_boot", bootCount(context)).commit()) return@synchronized false
            reconcileSafely(context, explicitEnable = true)
        }
    }

    suspend fun endFocus(context: Context): Boolean = withContext(Dispatchers.IO) {
        synchronized(monitor) {
            if (!preferences(context).edit().remove("focus_until").remove("focus_elapsed").remove("focus_boot").commit()) return@synchronized false
            reconcileSafely(context)
        }
    }

    suspend fun reconcile(context: Context, explicitEnable: Boolean = false): Boolean = withContext(Dispatchers.IO) {
        synchronized(monitor) { reconcileSafely(context, explicitEnable) }
    }

    private fun reconcileSafely(context: Context, explicitEnable: Boolean = false): Boolean = try {
        reconcileLocked(context, explicitEnable)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        mutable.value = QuietStatus(QuietHealth.CLEANUP_NEEDED)
        false
    }

    private fun reconcileLocked(context: Context, explicitEnable: Boolean = false): Boolean {
        NotificationPreferences.initialize(context)
        val config = NotificationPreferences.state.value
        val prefs = preferences(context)
        val now = System.currentTimeMillis()
        val elapsedDeadline = prefs.getLong("focus_elapsed", 0)
        // A focus timer measures elapsed time, so moving the clock cannot extend it.
        // Reboot ends manual focus; recurring local-time schedules are recalculated below.
        val focusUntil = if (elapsedDeadline > 0 && prefs.getInt("focus_boot", -2) == bootCount(context))
            now + (elapsedDeadline - android.os.SystemClock.elapsedRealtime()).coerceAtLeast(0)
        else 0L
        val window = quietWindow(config.quietSchedule, now)
        val deviceSchedule = config.quietSchedule.enabled && config.quietSchedule.deviceQuiet
        val active = !config.paused && (focusUntil > now || (deviceSchedule && window.active))
        val next = if (config.paused) null else listOfNotNull(focusUntil.takeIf { it > now }, window.nextBoundary.takeIf { deviceSchedule }).minOrNull()
        val manager = context.getSystemService(NotificationManager::class.java)
        val ruleId = prefs.getString("rule_id", null)
        if (!deviceSchedule && focusUntil <= now && ruleId == null) {
            cancelAlarm(context)
            mutable.value = QuietStatus(if (config.quietSchedule.enabled && !config.paused) QuietHealth.LOCAL_ONLY else QuietHealth.OFF, window.nextBoundary, focusUntil)
            return true
        }
        if (!manager.isNotificationPolicyAccessGranted) {
            cancelAlarm(context)
            mutable.value = QuietStatus(if (ruleId != null) QuietHealth.CLEANUP_NEEDED else if (active || deviceSchedule) QuietHealth.ACCESS_NEEDED else QuietHealth.OFF, next, focusUntil)
            return ruleId == null && !active && !deviceSchedule
        }
        try {
            if (Build.VERSION.SDK_INT == 28 && active && manager.notificationPolicy.priorityCategories and NotificationManager.Policy.PRIORITY_CATEGORY_ALARMS == 0) {
                ruleId?.let { id -> manager.getAutomaticZenRule(id)?.takeIf { it.owner == owner(context) }?.let { rule ->
                    rule.isEnabled = false; manager.updateAutomaticZenRule(id, rule)
                } }
                cancelAlarm(context)
                mutable.value = QuietStatus(QuietHealth.UNAVAILABLE, next, focusUntil); return false
            }
            var id = ruleId
            var rule = id?.let { manager.getAutomaticZenRule(it) }
            if (rule != null && (rule.owner != owner(context) || rule.conditionId != conditionId(context))) {
                mutable.value = QuietStatus(QuietHealth.FAILED); return false
            }
            if (rule == null && explicitEnable) {
                // Recover a rule created before an interrupted local preference write.
                val existing = manager.automaticZenRules.entries.firstOrNull { it.value.owner == owner(context) && it.value.conditionId == conditionId(context) }
                val definition = AutomaticZenRule(
                    context.getString(R.string.notification_quiet_rule), owner(context), conditionId(context), NotificationManager.INTERRUPTION_FILTER_PRIORITY, true,
                ).apply {
                    if (Build.VERSION.SDK_INT >= 29) zenPolicy = android.service.notification.ZenPolicy.Builder().allowAlarms(true).build()
                }
                id = existing?.key ?: manager.addAutomaticZenRule(definition)
                if (!prefs.edit().putString("rule_id", id).commit()) {
                    id?.let { manager.removeAutomaticZenRule(it) }; mutable.value = QuietStatus(QuietHealth.FAILED); return false
                }
                rule = id?.let { manager.getAutomaticZenRule(it) }
            }
            if (rule != null && !rule.isEnabled && explicitEnable) {
                rule.isEnabled = true
                manager.updateAutomaticZenRule(id!!, rule)
            }
            if (id == null || rule == null || !rule.isEnabled) {
                cancelAlarm(context)
                mutable.value = QuietStatus(if (active || deviceSchedule) QuietHealth.SYSTEM_DISABLED else QuietHealth.OFF, next, focusUntil)
                return !active && !deviceSchedule
            }
            val condition = Condition(conditionId(context), context.getString(R.string.notification_quiet_rule), if (active) Condition.STATE_TRUE else Condition.STATE_FALSE)
            if (Build.VERSION.SDK_INT >= 29) manager.setAutomaticZenRuleState(id, condition)
            else {
                val provider = ComferQuietConditionProvider.instance
                if (provider != null) provider.notifyCondition(condition)
                else if (Build.VERSION.SDK_INT >= 26) ConditionProviderService.requestRebind(owner(context))
            }
            if (next != null) context.getSystemService(AlarmManager::class.java).setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, alarm(context))
            else cancelAlarm(context)
            mutable.value = QuietStatus(if (active) QuietHealth.ACTIVE else if (config.quietSchedule.enabled && !config.paused) {
                if (deviceSchedule) QuietHealth.SCHEDULED else QuietHealth.LOCAL_ONLY
            } else QuietHealth.OFF, next ?: window.nextBoundary, focusUntil)
            return true
        } catch (_: Exception) {
            // If scheduling/state publication failed, do not leave an active contribution
            // with no reliable expiry. A failed cleanup must remain visible and retryable.
            val cleaned = runCatching {
                val id = prefs.getString("rule_id", null)
                val owned = id?.let { manager.getAutomaticZenRule(it) }
                if (owned == null) true
                else if (owned.owner != owner(context) || owned.conditionId != conditionId(context)) false
                else { owned.isEnabled = false; manager.updateAutomaticZenRule(id, owned) }
            }.getOrDefault(false)
            mutable.value = QuietStatus(if (cleaned) QuietHealth.FAILED else QuietHealth.CLEANUP_NEEDED, next, focusUntil)
            return false
        }
    }

    /** Must succeed before configuration reset can discard an owned system contribution. */
    suspend fun reset(context: Context): Boolean = withContext(Dispatchers.IO) {
        synchronized(monitor) {
            val prefs = preferences(context)
            val manager = context.getSystemService(NotificationManager::class.java)
            try {
                val id = prefs.getString("rule_id", null)
                if (id != null) {
                    if (!manager.isNotificationPolicyAccessGranted) { mutable.value = QuietStatus(QuietHealth.CLEANUP_NEEDED); return@synchronized false }
                    val rule = manager.getAutomaticZenRule(id)
                    if (rule != null && (rule.owner != owner(context) || !manager.removeAutomaticZenRule(id))) return@synchronized false
                }
                cancelAlarm(context)
                if (!prefs.edit().clear().commit()) return@synchronized false
                mutable.value = QuietStatus()
                true
            } catch (_: Exception) { mutable.value = QuietStatus(QuietHealth.CLEANUP_NEEDED); false }
        }
    }

    private fun alarm(context: Context): PendingIntent = PendingIntent.getBroadcast(context, 701,
        Intent(context, NotificationQuietReceiver::class.java).setAction("${context.packageName}.NOTIFICATION_QUIET_BOUNDARY"),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    private fun cancelAlarm(context: Context) = context.getSystemService(AlarmManager::class.java).cancel(alarm(context))
}

class ComferQuietConditionProvider : ConditionProviderService() {
    companion object { @Volatile var instance: ComferQuietConditionProvider? = null; private set }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    override fun onConnected() { instance = this; scope.launch { NotificationQuietHours.reconcile(this@ComferQuietConditionProvider) } }
    override fun onSubscribe(conditionId: Uri?) { scope.launch { NotificationQuietHours.reconcile(this@ComferQuietConditionProvider) } }
    override fun onUnsubscribe(conditionId: Uri?) = Unit
    override fun onDestroy() { if (instance === this) instance = null; scope.cancel(); super.onDestroy() }
}

class NotificationQuietReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try { NotificationQuietHours.reconcile(context.applicationContext) } finally { pending.finish() }
        }
    }
}
