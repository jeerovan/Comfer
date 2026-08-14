package com.jeerovan.comfer

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(FlowPreview::class)
internal class DebouncedSyncCoordinator(
    private val scope: CoroutineScope,
    debounceMillis: Long,
    private val sync: suspend () -> Unit,
) {
    private val requests = MutableSharedFlow<Unit>(
        replay = 1,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    private val debouncedRequests = requests.debounce(debounceMillis)
    private var job: Job? = null

    fun start() {
        job?.cancel()
        job = scope.launch { debouncedRequests.collect { sync() } }
    }

    fun request() {
        requests.tryEmit(Unit)
    }

    fun stop() {
        job?.cancel()
        job = null
    }
}

@OptIn(FlowPreview::class)
class MyNotificationListenerService : NotificationListenerService() {

    companion object {
        // Interval for the periodic resync that reconciles the snapshot with the
        // system's live set even when a remove event is missed by the listener.
        private const val RESYNC_INTERVAL_MS = 30_000L
        private const val EVENT_DEBOUNCE_MS = 250L

        private val _activeNotifications = MutableStateFlow<List<StatusBarNotification>>(emptyList())
        // Public live snapshot consumed by the launcher UI.
        val activeNotifications = _activeNotifications.asStateFlow()
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val syncCoordinator = DebouncedSyncCoordinator(
        scope = serviceScope,
        debounceMillis = EVENT_DEBOUNCE_MS,
        sync = ::syncActiveNotifications,
    )
    private var periodicJob: Job? = null

    override fun onListenerConnected() {
        super.onListenerConnected()
        syncCoordinator.stop()
        periodicJob?.cancel()
        syncCoordinator.start()
        requestSync()
        // Periodic resync so the launcher never shows a stale icon that the
        // status-bar shade no longer displays (e.g. a removal event we missed).
        periodicJob = serviceScope.launch {
            while (isActive) {
                delay(RESYNC_INTERVAL_MS)
                requestSync()
            }
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        requestSync()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
        requestSync()
    }

    /**
     * Recomputes the snapshot from the system's live active set, so the widgets
     * always match what the status-bar shade currently shows.
     */
    private fun requestSync() {
        syncCoordinator.request()
    }

    private suspend fun syncActiveNotifications() {
        val traceCookie = PerformanceTrace.notificationSyncStarted()
        PerformanceTrace.beginAsync("notificationSync", traceCookie)
        try {
            val live = withContext(Dispatchers.IO) { getActiveNotifications() }
            val snapshot = live
                ?.asSequence()
                ?.sortedByDescending { it.postTime }
                ?.distinctBy { it.packageName }
                ?.toList()
                ?: emptyList()
            val currentSignature = _activeNotifications.value.map { it.key to it.postTime }
            val newSignature = snapshot.map { it.key to it.postTime }
            if (currentSignature != newSignature) {
                _activeNotifications.value = snapshot
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Listener may have lost binding; keep last valid snapshot.
        } finally {
            PerformanceTrace.endAsync("notificationSync", traceCookie)
            PerformanceTrace.notificationSyncFinished()
        }
    }

    override fun onListenerDisconnected() {
        syncCoordinator.stop()
        periodicJob?.cancel()
        periodicJob = null
        super.onListenerDisconnected()
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}
