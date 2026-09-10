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
import kotlinx.serialization.json.*

@Serializable
data class NotificationConfiguration(
    val version: Int = 2,
    val setup: Boolean = false,
    val paused: Boolean = false,
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
            decodeNotificationConfiguration(prefs.getString("config", null) ?: "{}").also {
                require(it.version == 2 && it.reachFraction.isFinite() && it.reachFraction in .4f.. .65f && it.reachCap.isFinite() && it.reachCap in 160f..600f && it.reachWidth.isFinite() && it.reachWidth in 240f..600f)
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
        if (preferences?.edit()?.putString("config", Json { encodeDefaults = true }.encodeToString(next))?.commit() != true) return false
        mutable.value = next
        mutableRecoveryNeeded.value = false
        return true
    }
    suspend fun update(transform: (NotificationConfiguration) -> NotificationConfiguration): Boolean =
        writes.withLock { withContext(Dispatchers.IO) { save(transform = transform) } }
    suspend fun reset(): Boolean = writes.withLock {
        withContext(Dispatchers.IO) { save(reset = true) { NotificationConfiguration(setup = it.setup, paused = true) } }
    }
}

/** Retired visibility settings must never become destructive rules on upgrade. */
internal fun decodeNotificationConfiguration(raw: String): NotificationConfiguration {
    val document = Json.parseToJsonElement(raw).jsonObject
    val version = document["version"]?.jsonPrimitive?.int ?: 1
    require(version in 1..2)
    val migrated = if (version == 1) JsonObject(document.toMutableMap().apply {
        remove("hiddenUntil")
        put("version", JsonPrimitive(2))
        document["rules"]?.jsonArray?.let { rules ->
            // Version 1 omitted the default HIDE action when serializing.
            put("rules", JsonArray(rules.filter { it.jsonObject["action"]?.jsonPrimitive?.content == "DISMISS" }))
        }
        document["quietSchedule"]?.jsonObject?.let { schedule ->
            put("quietSchedule", JsonObject(schedule.toMutableMap().apply {
                remove("hiddenApps")
                if (schedule["deviceQuiet"]?.jsonPrimitive?.booleanOrNull == false) put("enabled", JsonPrimitive(false))
                put("deviceQuiet", JsonPrimitive(true))
            }))
        }
    }) else document
    return Json.decodeFromJsonElement<NotificationConfiguration>(migrated)
}
