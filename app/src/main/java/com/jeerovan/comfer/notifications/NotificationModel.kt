package com.jeerovan.comfer.notifications

/** Platform handles stay in the listener; only immutable, bounded text reaches the UI. */
data class NotificationItem(
    val key: String,
    val app: String,
    val profile: Int,
    val title: String,
    val text: String,
    val postedAt: Long,
    val group: String?,
    val summary: Boolean,
    val clearable: Boolean,
    val protected: Boolean,
    val revision: Long = 0,
    val actionSignature: Long = 0,
    val channelId: String? = null,
    val progress: Int = 0,
    val progressMax: Int = 0,
    val progressIndeterminate: Boolean = false,
    val hasContentIntent: Boolean = false,
    val contentComplete: Boolean = true,
    val previewAvailable: Boolean = true,
) {
    val appId: String get() = "$profile:$app"
}

enum class ListenerHealth { CONNECTED, RECONNECTING, ACCESS_NEEDED, RESTRICTED, RECOVERY_NEEDED }
data class NotificationSnapshot(
    val items: List<NotificationItem> = emptyList(),
    val health: ListenerHealth = ListenerHealth.RECONNECTING,
    val lastSync: Long? = null,
    val reconciliationNeeded: Boolean = false,
    val sessionId: String = "",
)

fun notificationChildren(items: List<NotificationItem>): List<NotificationItem> {
    val childGroups = items.filterNot { it.summary }.mapNotNull { item ->
        item.group?.let { Triple(item.profile, item.app, it) }
    }.toSet()
    return items.filterNot { it.summary && Triple(it.profile, it.app, it.group) in childGroups }
        .sortedWith(compareByDescending<NotificationItem> { it.postedAt }.thenBy { it.key })
}

/** A content update with unchanged postTime must still invalidate an open action menu. */
class NotificationLedger {
    private val records = linkedMapOf<String, NotificationItem>()
    private var revision = 0L
    fun put(item: NotificationItem) {
        val old = records[item.key]
        records[item.key] = if (old?.copy(revision = 0) == item.copy(revision = 0)) old
        else item.copy(revision = ++revision)
    }
    fun remove(key: String) { records.remove(key) }
    fun reconcile(items: List<NotificationItem>) {
        val keys = items.map { it.key }.toSet()
        records.keys.retainAll(keys)
        items.forEach(::put)
    }
    fun items(): List<NotificationItem> = records.values.toList()
    fun current(key: String, revision: Long): NotificationItem? = records[key]?.takeIf { it.revision == revision }
}

fun reachableHeightDp(heightDp: Float): Float =
    minOf(heightDp.coerceAtLeast(0f) * .4f, 360f)

/** Stable list entries keep app headers distinct from source notification keys. */
data class NotificationDisplayRow(
    val key: String,
    val appId: String,
    val notification: NotificationItem? = null,
    val count: Int = 0,
)

fun notificationDisplayRows(records: List<NotificationItem>, chronological: Boolean, collapsed: Set<String>): List<NotificationDisplayRow> =
    if (chronological) records.map { NotificationDisplayRow(it.key, it.appId, it) }
    else records.groupBy { it.appId }.flatMap { (appId, children) ->
        listOf(NotificationDisplayRow("app-header:$appId", appId, count = children.size)) +
            if (appId in collapsed) emptyList() else children.map { NotificationDisplayRow(it.key, appId, it) }
    }

fun canManageNotification(item: NotificationItem, protectedApps: Set<String>): Boolean =
    item.clearable && !item.protected && !item.summary && item.appId !in protectedApps

/** An absent selection must not match the null notification on an app header. */
fun notificationAnchorIndex(rows: List<NotificationDisplayRow>, selectedKey: String?): Int =
    if (selectedKey == null) -1 else rows.indexOfFirst { it.notification?.key == selectedKey }
