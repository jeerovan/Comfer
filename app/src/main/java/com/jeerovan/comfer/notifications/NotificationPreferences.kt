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
            decodeNotificationConfiguration(prefs.getString("config", null) ?: "{}").also(::validateNotificationConfiguration)
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

    /** Holds ordinary preference edits until the complete backup/restore operation finishes. */
    internal suspend fun <T> withBackupAccess(context: Context, block: suspend BackupAccess.() -> T): T =
        writes.withLock {
            withContext(Dispatchers.IO) {
                initialize(context)
                BackupAccess().block()
            }
        }

    internal class BackupAccess internal constructor() {
        fun export(): NotificationSettingsBackup {
            check(!recoveryNeeded.value) { "Notification configuration needs recovery before backup" }
            return state.value.toSettingsBackup()
        }

        // Keep raw local data for rollback, including malformed data awaiting user recovery.
        fun snapshot(): String = preferences!!.getString("config", null) ?: "{}"

        fun restore(value: NotificationSettingsBackup) {
            value.validate()
            check(save(reset = true) { value.toConfiguration(System.currentTimeMillis()) }) {
                "Could not persist restored notification settings"
            }
        }

        fun rollback(raw: String) {
            val restored = runCatching { decodeNotificationConfiguration(raw).also(::validateNotificationConfiguration) }
            check(preferences!!.edit().putString("config", raw).commit()) { "Could not recover notification settings" }
            mutable.value = restored.getOrElse { NotificationConfiguration(setup = true, paused = true) }
                .copy(generation = mutable.value.generation + 1)
            mutableRecoveryNeeded.value = restored.isFailure
        }
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
