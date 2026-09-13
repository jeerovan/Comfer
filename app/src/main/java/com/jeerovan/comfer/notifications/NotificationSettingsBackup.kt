package com.jeerovan.comfer.notifications

import kotlinx.serialization.Serializable

/** Portable preferences only: never Android handles, notification content, or collection cursors. */
@Serializable
internal data class NotificationSettingsBackup(
    val version: Int = 1,
    val paused: Boolean = false,
    val pinned: Set<String> = emptySet(),
    val protectedApps: Set<String> = emptySet(),
    val chronological: Boolean = false,
    val quietSchedule: QuietSchedule = QuietSchedule(),
    val historyEnabled: Boolean = false,
    val historyDays: Int = 7,
    val historyExcludedApps: Set<String> = emptySet(),
    val rules: List<NotificationRule> = emptyList(),
) {
    fun validate() {
        require(version == 1)
        validateNotificationConfiguration(toConfiguration(0))
        (pinned + protectedApps + historyExcludedApps).forEach { require(it.isNotBlank() && it.length <= 600) }
        require(pinned.size <= 10000 && protectedApps.size <= 10000 && historyExcludedApps.size <= 10000)
        rules.forEach {
            require(it.appId == null || (it.appId.isNotBlank() && it.appId.length <= 600))
            require(it.channelId == null || (it.channelId.isNotBlank() && it.channelId.length <= 1000))
        }
    }

    fun toConfiguration(now: Long) = NotificationConfiguration(
        setup = true, paused = true, pinned = pinned, protectedApps = protectedApps,
        chronological = chronological, quietSchedule = quietSchedule,
        historyEnabled = historyEnabled, historySince = if (historyEnabled) now else 0,
        historyDays = historyDays, historyExcludedApps = historyExcludedApps, rules = rules,
    )
}

internal fun NotificationConfiguration.toSettingsBackup() = NotificationSettingsBackup(
    paused = paused, pinned = pinned, protectedApps = protectedApps,
    chronological = chronological, quietSchedule = quietSchedule,
    historyEnabled = historyEnabled, historyDays = historyDays,
    historyExcludedApps = historyExcludedApps, rules = rules,
).also { it.validate() }

internal fun validateNotificationConfiguration(value: NotificationConfiguration) = with(value) {
    require(version == 2 && reachFraction.isFinite() && reachFraction in .4f.. .65f && reachCap.isFinite() && reachCap in 160f..600f && reachWidth.isFinite() && reachWidth in 240f..600f)
    require(quietSchedule.startMinute in 0..1439 && quietSchedule.endMinute in 0..1439 && quietSchedule.weekdays.all { it in 1..7 })
    require(historyDays in setOf(1, 7, 30))
    require(rules.size <= 20 && rules.all(::validNotificationRule) && rules.map { it.id }.distinct().size == rules.size)
}
