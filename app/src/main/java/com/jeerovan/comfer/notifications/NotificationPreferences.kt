package com.jeerovan.comfer.notifications

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class NotificationConfiguration(
    val version: Int = 1,
    val setup: Boolean = false,
    val paused: Boolean = false,
    val hiddenUntil: Map<String, Long> = emptyMap(),
    val pinned: Set<String> = emptySet(),
    val protectedApps: Set<String> = emptySet(),
    val chronological: Boolean = false,
    // Legacy serialized reach fields are ignored; both screens use fixed reach defaults.
    val reachFraction: Float = .4f,
    val reachCap: Float = 360f,
    // Legacy serialized fields retained for compatibility; layouts always use full width.
    val reachWidth: Float = 420f,
    val leftHanded: Boolean = false,
    val generation: Long = 0,
    val quietSchedule: QuietSchedule = QuietSchedule(),
    val historyEnabled: Boolean = false,
    val historySince: Long = 0,
    val historyDays: Int = 7,
    val historyExcludedApps: Set<String> = emptySet(),
    val rules: List<NotificationRule> = emptyList(),
)

/** Dedicated configuration only. Never store notification content or Android handles here. */
object NotificationPreferences {
    private val mutable = MutableStateFlow(NotificationConfiguration())
    val state = mutable.asStateFlow()
    private val mutableRecoveryNeeded = MutableStateFlow(false)
    val recoveryNeeded = mutableRecoveryNeeded.asStateFlow()
    private var preferences: android.content.SharedPreferences? = null
    private val writes = Mutex()
    @Synchronized fun initialize(context: Context) {
        if (preferences != null) return
        val prefs = context.applicationContext.getSharedPreferences("notification_configuration", Context.MODE_PRIVATE)
        mutable.value = runCatching {
            Json.decodeFromString<NotificationConfiguration>(prefs.getString("config", null) ?: "{}").also {
                require(it.version == 1 && it.reachFraction.isFinite() && it.reachFraction in .4f.. .65f && it.reachCap.isFinite() && it.reachCap in 160f..600f && it.reachWidth.isFinite() && it.reachWidth in 240f..600f)
                require(it.quietSchedule.startMinute in 0..1439 && it.quietSchedule.endMinute in 0..1439 && it.quietSchedule.weekdays.all { day -> day in 1..7 })
                require(it.historyDays in setOf(1, 7, 30))
                require(it.rules.size <= 20 && it.rules.all(::validNotificationRule) && it.rules.map { rule -> rule.id }.distinct().size == it.rules.size)
            }
        }.getOrElse {
            mutableRecoveryNeeded.value = true
            NotificationConfiguration(setup = true, paused = true)
        }
        preferences = prefs
    }
    @Synchronized private fun save(reset: Boolean = false, transform: (NotificationConfiguration) -> NotificationConfiguration): Boolean {
        if (mutableRecoveryNeeded.value && !reset) return false
        val next = transform(mutable.value).copy(generation = mutable.value.generation + 1)
        if (preferences?.edit()?.putString("config", Json.encodeToString(next))?.commit() != true) return false
        mutable.value = next
        mutableRecoveryNeeded.value = false
        return true
    }
    suspend fun update(transform: (NotificationConfiguration) -> NotificationConfiguration): Boolean =
        writes.withLock { withContext(Dispatchers.IO) { save(transform = transform) } }
    suspend fun reset(): Boolean = writes.withLock {
        withContext(Dispatchers.IO) { save(reset = true) { NotificationConfiguration(setup = it.setup, paused = true) } }
    }
    fun isHidden(item: NotificationItem, now: Long): Boolean =
        isVisuallyHidden(item, state.value, now)
}

fun isVisuallyHidden(item: NotificationItem, configuration: NotificationConfiguration, now: Long): Boolean =
    (configuration.hiddenUntil[item.appId] ?: 0L) > now ||
        (!configuration.paused && item.appId in configuration.quietSchedule.hiddenApps && quietWindow(configuration.quietSchedule, now).active) ||
        firstNotificationRule(item, configuration)?.action == RuleAction.HIDE

fun NotificationConfiguration.showApp(appId: String): NotificationConfiguration = copy(
    hiddenUntil = hiddenUntil - appId,
    quietSchedule = quietSchedule.copy(hiddenApps = quietSchedule.hiddenApps - appId),
)
