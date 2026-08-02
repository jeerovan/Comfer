package com.jeerovan.comfer

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class MyNotificationListenerService : NotificationListenerService() {

    companion object {
        // Interval for the periodic resync that reconciles the snapshot with the
        // system's live set even when a remove event is missed by the listener.
        private const val RESYNC_INTERVAL_MS = 30_000L

        private val _activeNotifications = MutableStateFlow<List<StatusBarNotification>>(emptyList())
        // Public live snapshot consumed by the launcher UI.
        val activeNotifications = _activeNotifications.asStateFlow()
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onListenerConnected() {
        super.onListenerConnected()
        syncActiveNotifications()
        // Periodic resync so the launcher never shows a stale icon that the
        // status-bar shade no longer displays (e.g. a removal event we missed).
        serviceScope.launch {
            while (isActive) {
                delay(RESYNC_INTERVAL_MS)
                syncActiveNotifications()
            }
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        syncActiveNotifications()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
        syncActiveNotifications()
    }

    /**
     * Recomputes the snapshot from the system's live active set, so the widgets
     * always match what the status-bar shade currently shows.
     */
    private fun syncActiveNotifications() {
        serviceScope.launch(Dispatchers.IO) {
            try {
                val live = getActiveNotifications()
                _activeNotifications.value = live
                    ?.asSequence()
                    // The framework does not guarantee ordering; keep the newest
                    // notification per package so we never surface a stale one.
                    ?.sortedByDescending { it.postTime }
                    ?.groupBy { it.packageName }
                    ?.map { it.value.first() }
                    ?: emptyList()
            } catch (e: Exception) {
                // Handle SecurityException (listener lost binding) or other
                // errors; keep the last valid snapshot.
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}

