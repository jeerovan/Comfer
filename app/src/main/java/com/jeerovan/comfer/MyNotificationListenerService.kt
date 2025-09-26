package com.jeerovan.comfer

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class MyNotificationListenerService : NotificationListenerService() {

    companion object {
        // Expose a flow of the full StatusBarNotification objects
        private val _activeNotifications = MutableStateFlow<List<StatusBarNotification>>(emptyList())
        val activeNotifications = _activeNotifications.asStateFlow()
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        updateActiveNotifications()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        updateActiveNotifications()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
        updateActiveNotifications()
    }

    private fun updateActiveNotifications() {
        // Get active notifications, group by package name to ensure uniqueness,
        // and emit the first notification for each app.
        _activeNotifications.value = activeNotifications
            .groupBy { it.packageName }
            .map { it.value.first() }
    }
}
