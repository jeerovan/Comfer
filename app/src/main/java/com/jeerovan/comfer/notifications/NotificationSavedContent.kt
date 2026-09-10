package com.jeerovan.comfer.notifications

import android.content.Context
import java.util.Locale

private val unavailablePreviews = setOf(
    "preview unavailable", "no preview available", "content hidden", "contents hidden",
    "notification content hidden", "sensitive content hidden", "sensitive notification content hidden",
    "unlock to view notification", "content unavailable",
)

/** Compare delivered placeholders only. Never request or reconstruct Android's hidden content. */
internal fun hasNotificationPreview(title: String, text: String, platformPlaceholder: String? = null): Boolean {
    fun normalized(value: String) = value.trim().trimEnd('.', '…', '!').lowercase(Locale.ROOT)
    val placeholders = unavailablePreviews + listOfNotNull(platformPlaceholder?.let(::normalized))
    val fields = listOf(title, text).filter { it.isNotBlank() }.map(::normalized)
    return fields.isNotEmpty() && fields.none { it in placeholders }
}

internal fun notificationRedactionPlaceholder(context: Context): String? = runCatching {
    // This is Android's localized replacement text in redacted listener notifications.
    val id = context.resources.getIdentifier("redacted_notification_message", "string", "android")
    if (id == 0) null else context.resources.getString(id)
}.getOrNull()

internal fun savedNotificationsMatching(records: List<SavedNotification>, query: String, live: List<NotificationItem> = emptyList()): List<SavedNotification> {
    val term = query.trim()
    // Use the complete live snapshot, not the filtered/collapsed view: hiding an app
    // must not make its still-active notifications appear in history.
    val liveIds = live.map(::savedNotificationId).toSet()
    return records.filter { it.id !in liveIds && hasNotificationPreview(it.title, it.text) &&
        (term.isEmpty() || it.title.contains(term, true) || it.text.contains(term, true) || it.app.contains(term, true)) }
        .sortedByDescending { it.savedAt }
}
