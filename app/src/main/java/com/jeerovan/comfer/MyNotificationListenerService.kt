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
import kotlinx.coroutines.flow.update
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
        private val _activeNotifications = MutableStateFlow<List<StatusBarNotification>>(emptyList())
        val activeNotifications = _activeNotifications.asStateFlow()
        private val mutableSnapshot = MutableStateFlow(com.jeerovan.comfer.notifications.NotificationSnapshot())
        val snapshot = mutableSnapshot.asStateFlow()
        @Volatile private var connectedService: MyNotificationListenerService? = null

        fun hasAccess(context: android.content.Context): Boolean {
            val expected = android.content.ComponentName(context, MyNotificationListenerService::class.java)
            if (android.os.Build.VERSION.SDK_INT >= 27) return context.getSystemService(android.app.NotificationManager::class.java)
                .isNotificationListenerAccessGranted(expected)
            return android.provider.Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
                ?.split(':')?.any { android.content.ComponentName.unflattenFromString(it) == expected } == true
        }

        private val recoveryScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        private var recoveryJob: Job? = null
        private var lastRecoveryAttempt = Long.MIN_VALUE

        fun refresh(context: android.content.Context, force: Boolean = false) {
            val app = context.applicationContext
            recoveryScope.launch {
                if (!hasAccess(app)) {
                    recoveryJob?.cancel()
                    _activeNotifications.value = emptyList()
                    mutableSnapshot.update { it.copy(items = emptyList(), health = com.jeerovan.comfer.notifications.ListenerHealth.ACCESS_NEEDED) }
                    return@launch
                }
                connectedService?.syncCoordinator?.request()
                if (snapshot.value.health == com.jeerovan.comfer.notifications.ListenerHealth.CONNECTED && connectedService != null) return@launch
                if (recoveryJob?.isActive == true) return@launch
                val now = android.os.SystemClock.elapsedRealtime()
                if (!force && lastRecoveryAttempt != Long.MIN_VALUE && now - lastRecoveryAttempt < 60_000) {
                    mutableSnapshot.update { it.copy(items = emptyList(), health = com.jeerovan.comfer.notifications.ListenerHealth.RECOVERY_NEEDED) }
                    return@launch
                }
                lastRecoveryAttempt = now
                recoveryJob = recoveryScope.launch recovery@ {
                    val component = android.content.ComponentName(app, MyNotificationListenerService::class.java)
                    mutableSnapshot.update { it.copy(items = emptyList(), health = com.jeerovan.comfer.notifications.ListenerHealth.RECONNECTING) }
                    _activeNotifications.value = emptyList()
                    try {
                        android.util.Log.i("NotificationRecovery", "request_rebind")
                        requestRebind(component)
                        repeat(20) {
                            delay(250)
                            if (!hasAccess(app)) {
                                mutableSnapshot.update { it.copy(items = emptyList(), health = com.jeerovan.comfer.notifications.ListenerHealth.ACCESS_NEEDED) }
                                return@recovery
                            }
                            if (snapshot.value.health == com.jeerovan.comfer.notifications.ListenerHealth.CONNECTED && connectedService != null) return@recovery
                        }
                        // Android 7 can retain a dead binding across package updates. Announce
                        // a package change without ever disabling the service or changing access.
                        val manager = app.packageManager
                        val current = manager.getComponentEnabledSetting(component)
                        val defaultState = android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
                        val enabledState = android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                        if (android.os.Build.VERSION.SDK_INT <= 25 && (current == defaultState || current == enabledState)) {
                            android.util.Log.i("NotificationRecovery", "refresh_enabled_registration")
                            manager.setComponentEnabledSetting(component,
                                if (current == defaultState) enabledState else defaultState,
                                android.content.pm.PackageManager.DONT_KILL_APP)
                            requestRebind(component)
                        }
                        repeat(80) {
                            delay(250)
                            if (!hasAccess(app)) {
                                mutableSnapshot.update { it.copy(items = emptyList(), health = com.jeerovan.comfer.notifications.ListenerHealth.ACCESS_NEEDED) }
                                return@recovery
                            }
                            if (snapshot.value.health == com.jeerovan.comfer.notifications.ListenerHealth.CONNECTED && connectedService != null) return@recovery
                        }
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { /* Report a recoverable state, never a healthy empty inbox. */ }
                    mutableSnapshot.update { it.copy(items = emptyList(), health = if (hasAccess(app))
                        com.jeerovan.comfer.notifications.ListenerHealth.RECOVERY_NEEDED else com.jeerovan.comfer.notifications.ListenerHealth.ACCESS_NEEDED) }
                }
            }
        }

        suspend fun act(key: String, revision: Long, action: String, sessionId: String): String = withContext(Dispatchers.Main) {
            val service = connectedService ?: return@withContext "unavailable"
            if (action == "open") {
                val candidate = service.historyOpenCandidate(key, revision, sessionId)
                if (candidate != null && !com.jeerovan.comfer.notifications.NotificationHistory.retainOpened(candidate)) return@withContext "history_failed"
            }
            val actionItem = synchronized(service.lock) { service.ledger.current(key, revision) }
            var result = service.performAction(key, revision, action, sessionId)
            if (action == "open" && result in setOf("requested", "app_open_requested") && actionItem != null &&
                com.jeerovan.comfer.notifications.canManageNotification(actionItem, com.jeerovan.comfer.notifications.NotificationPreferences.state.value.protectedApps)) {
                val dismissal = service.performAction(key, revision, "dismiss", sessionId)
                if (dismissal !in setOf("requested", "removed")) result = "opened_dismiss_failed"
            }
            if (action == "dismiss" && result == "requested") {
                if (actionItem != null) com.jeerovan.comfer.notifications.NotificationHistory.dismissFromInbox(com.jeerovan.comfer.notifications.savedNotificationId(actionItem))
            }
            result
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val ledger = com.jeerovan.comfer.notifications.NotificationLedger()
    private val handles = linkedMapOf<String, StatusBarNotification>()
    private val lock = Any()
    private data class ActionIdentity(val content: android.app.PendingIntent?, val delete: android.app.PendingIntent?, val actions: List<List<Any?>>?)
    private val actionIdentities = mutableMapOf<String, Pair<ActionIdentity, Long>>()
    private var nextActionIdentity = 0L
    @Volatile private var connectionId = ""
    private fun actionIdentity(sbn: StatusBarNotification): Long {
        val notification = sbn.notification
        val identity = ActionIdentity(notification.contentIntent, notification.deleteIntent,
            notification.actions?.map { listOf(it.title?.toString(), it.actionIntent, it.remoteInputs?.map { input -> input.resultKey }) })
        val old = actionIdentities[sbn.key]
        if (old?.first == identity) return old.second
        val token = ++nextActionIdentity
        actionIdentities[sbn.key] = identity to token
        return token
    }
    private data class Event(val generation: Long, val posted: StatusBarNotification? = null, val removed: String? = null, val configGeneration: Long = com.jeerovan.comfer.notifications.NotificationPreferences.state.value.generation)
    private val events = kotlinx.coroutines.channels.Channel<Event>(256)
    private val syncCoordinator = DebouncedSyncCoordinator(serviceScope, 250L) { events.send(Event(connectionGeneration)) }
    private var periodicJob: Job? = null
    private var consumer: Job? = null
    @Volatile private var connectionGeneration = 0L
    @Volatile private var connected = false
    @Volatile private var overflow = false
    @Volatile private var dirty = false
    @Volatile private var lastSyncElapsed = 0L

    override fun onListenerConnected() {
        super.onListenerConnected()
        connectionGeneration++
        connectionId = java.util.UUID.randomUUID().toString()
        connected = true
        connectedService = this
        android.util.Log.i("NotificationRecovery", "listener_connected")
        com.jeerovan.comfer.notifications.NotificationPreferences.initialize(this)
        com.jeerovan.comfer.notifications.NotificationHistory.initialize(this)
        serviceScope.launch { com.jeerovan.comfer.notifications.NotificationPreferences.update { it.copy(setup = true) } }
        serviceScope.launch { com.jeerovan.comfer.notifications.NotificationQuietHours.reconcile(this@MyNotificationListenerService) }
        if (consumer == null) consumer = serviceScope.launch {
            for (event in events) {
                try {
                    if (!connected || event.generation != connectionGeneration) continue
                    if (event.posted == null && event.removed == null) {
                        val traceCookie = PerformanceTrace.notificationSyncStarted()
                        PerformanceTrace.beginAsync("notificationSync", traceCookie)
                        try {
                        val live = getActiveNotifications()?.toList() ?: throw IllegalStateException("Notification listener snapshot unavailable")
                        synchronized(lock) {
                            if (!connected || event.generation != connectionGeneration) return@synchronized
                            handles.clear(); live.forEach { handles[it.key] = it }
                            actionIdentities.keys.retainAll(handles.keys)
                            ledger.reconcile(live.map(::normalize))
                            overflow = false
                            dirty = false
                            lastSyncElapsed = android.os.SystemClock.elapsedRealtime()
                            publish(System.currentTimeMillis())
                        }
                        } finally {
                            PerformanceTrace.endAsync("notificationSync", traceCookie)
                            PerformanceTrace.notificationSyncFinished()
                        }
                    } else synchronized(lock) {
                        if (!connected || event.generation != connectionGeneration) return@synchronized
                        event.posted?.let {
                            handles[it.key] = it
                            val item = normalize(it)
                            ledger.put(item)
                            com.jeerovan.comfer.notifications.NotificationHistory.offer(item,
                                it.notification.visibility != android.app.Notification.VISIBILITY_SECRET &&
                                    !getSystemService(android.app.KeyguardManager::class.java).isDeviceLocked)
                            applyContentRules(item, event.configGeneration)
                        }
                        event.removed?.let { handles.remove(it); actionIdentities.remove(it); ledger.remove(it) }
                        dirty = true
                        // Capture revisions immediately; publish a reconciled UI snapshot after
                        // the existing debounce so a burst does not sort/recompose per callback.
                    }
                } catch (e: CancellationException) { throw e }
                catch (_: SecurityException) {
                    mutableSnapshot.value = mutableSnapshot.value.copy(items = emptyList(), health = com.jeerovan.comfer.notifications.ListenerHealth.RESTRICTED)
                    _activeNotifications.value = emptyList()
                }
                catch (_: Exception) {
                    val wasHealthy = snapshot.value.health == com.jeerovan.comfer.notifications.ListenerHealth.CONNECTED
                    mutableSnapshot.update { it.copy(items = emptyList(), health = if (it.health == com.jeerovan.comfer.notifications.ListenerHealth.RECOVERY_NEEDED) it.health else com.jeerovan.comfer.notifications.ListenerHealth.RECONNECTING) }
                    _activeNotifications.value = emptyList()
                    if (wasHealthy) refresh(this@MyNotificationListenerService)
                }
            }
        }
        syncCoordinator.start()
        syncCoordinator.request()
        periodicJob?.cancel()
        periodicJob = serviceScope.launch {
            while (isActive) {
                delay(1000L)
                if (dirty || android.os.SystemClock.elapsedRealtime() - lastSyncElapsed > 30_000L)
                    events.send(Event(connectionGeneration))
            }
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (!connected) return
        if (sbn != null && events.trySend(Event(connectionGeneration, posted = sbn)).isFailure) reportOverflow()
        syncCoordinator.request()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
        if (!connected) return
        if (sbn != null && events.trySend(Event(connectionGeneration, removed = sbn.key)).isFailure) reportOverflow()
        syncCoordinator.request()
    }

    private fun reportOverflow() {
        overflow = true; dirty = true
        mutableSnapshot.update { it.copy(reconciliationNeeded = true) }
    }

    private fun normalize(sbn: StatusBarNotification): com.jeerovan.comfer.notifications.NotificationItem =
        runCatching { normalizeContent(sbn) }.getOrElse {
            // One unsupported extras payload must not prevent other apps from being viewed.
            com.jeerovan.comfer.notifications.NotificationItem(
                sbn.key, sbn.packageName, sbn.user.hashCode(), "", "", sbn.postTime, sbn.groupKey,
                sbn.notification.flags and android.app.Notification.FLAG_GROUP_SUMMARY != 0,
                clearable = false, protected = true,
                actionSignature = actionIdentity(sbn),
                hasContentIntent = sbn.notification.contentIntent != null,
            )
        }

    private fun normalizeContent(sbn: StatusBarNotification): com.jeerovan.comfer.notifications.NotificationItem {
        val notification = sbn.notification
        val extras = notification.extras
        val rawTitle = extras?.getCharSequence(android.app.Notification.EXTRA_TITLE)?.toString().orEmpty()
        val title = rawTitle.take(2048)
        val messages = extras?.getParcelableArray(android.app.Notification.EXTRA_MESSAGES)
            ?.mapNotNull { (it as? android.os.Bundle)?.getCharSequence("text")?.toString() }
            ?.takeIf { it.isNotEmpty() }?.joinToString("\n")
        val body = messages ?: extras?.getCharSequence(android.app.Notification.EXTRA_BIG_TEXT)?.toString()
            ?: extras?.getCharSequence(android.app.Notification.EXTRA_TEXT)?.toString()
            ?: extras?.getCharSequenceArray(android.app.Notification.EXTRA_TEXT_LINES)?.joinToString("\n")
            ?: ""
        val protected = sbn.isOngoing || notification.category in setOf(
            android.app.Notification.CATEGORY_CALL, android.app.Notification.CATEGORY_ALARM,
            android.app.Notification.CATEGORY_TRANSPORT, android.app.Notification.CATEGORY_NAVIGATION,
            android.app.Notification.CATEGORY_SYSTEM,
        )
        return com.jeerovan.comfer.notifications.NotificationItem(
            sbn.key, sbn.packageName, sbn.user.hashCode(), title, body.take(8192), sbn.postTime,
            sbn.groupKey, notification.flags and android.app.Notification.FLAG_GROUP_SUMMARY != 0,
            sbn.isClearable, protected,
            actionSignature = actionIdentity(sbn),
            channelId = if (android.os.Build.VERSION.SDK_INT >= 26) notification.channelId else null,
            progress = extras?.getInt(android.app.Notification.EXTRA_PROGRESS, 0) ?: 0,
            progressMax = extras?.getInt(android.app.Notification.EXTRA_PROGRESS_MAX, 0) ?: 0,
            progressIndeterminate = extras?.getBoolean(android.app.Notification.EXTRA_PROGRESS_INDETERMINATE, false) ?: false,
            hasContentIntent = notification.contentIntent != null,
            previewAvailable = com.jeerovan.comfer.notifications.hasNotificationPreview(title, body, com.jeerovan.comfer.notifications.notificationRedactionPlaceholder(this)) &&
                (extras?.getCharSequence(android.app.Notification.EXTRA_TEXT)?.toString().orEmpty().let { deliveredText ->
                    deliveredText.isBlank() || com.jeerovan.comfer.notifications.hasNotificationPreview(title, deliveredText, com.jeerovan.comfer.notifications.notificationRedactionPlaceholder(this))
                }),
            contentComplete = rawTitle.length <= 2048 && body.length <= 8192 && notification.visibility != android.app.Notification.VISIBILITY_SECRET &&
                !getSystemService(android.app.KeyguardManager::class.java).isDeviceLocked,
        )
    }

    private fun publish(lastSync: Long?) {
        if (!connected) return
        mutableSnapshot.value = com.jeerovan.comfer.notifications.NotificationSnapshot(
            ledger.items(), com.jeerovan.comfer.notifications.ListenerHealth.CONNECTED, lastSync, overflow, connectionId,
        )
        // Compatibility projection for existing home badges; full records live in snapshot.
        _activeNotifications.value = handles.values.sortedByDescending { it.postTime }
            .distinctBy { it.user to it.packageName }
    }

    private fun applyContentRules(item: com.jeerovan.comfer.notifications.NotificationItem, generation: Long) {
        val config = com.jeerovan.comfer.notifications.NotificationPreferences.state.value
        if (config.paused || config.generation != generation) return
        val activity = com.jeerovan.comfer.notifications.NotificationRuleActivity
        config.rules.filter { it.enabled && it.observeOnly }.forEach { rule ->
            if (com.jeerovan.comfer.notifications.matchNotificationRule(rule, item, config.protectedApps).matches) activity.record(rule.id, "Test only: matched; no action")
        }
        val rule = com.jeerovan.comfer.notifications.firstNotificationRule(item, config) ?: return
        // Act only on fresh posted events, never on refresh/backlog or rule-editor preview.
        // Recheck Android's current content and the saved configuration just before cancellation.
        if (com.jeerovan.comfer.notifications.NotificationPreferences.state.value.generation != generation) return
        val current = getActiveNotifications(arrayOf(item.key))?.firstOrNull() ?: return
        if (normalize(current).copy(revision = 0) != item.copy(revision = 0)) {
            activity.record(rule.id, "Skipped: notification changed")
            return
        }
        try {
            cancelNotification(item.key)
            activity.record(rule.id, "Dismissal requested")
        } catch (_: Exception) { activity.record(rule.id, "Dismissal unavailable") }
    }

    private fun historyOpenCandidate(key: String, revision: Long, sessionId: String): com.jeerovan.comfer.notifications.NotificationItem? = synchronized(lock) {
        val config = com.jeerovan.comfer.notifications.NotificationPreferences.state.value
        val item = ledger.current(key, revision) ?: return@synchronized null
        if (!connected || sessionId != connectionId || !config.historyEnabled || item.summary || item.protected ||
            item.appId in config.historyExcludedApps || item.appId in config.protectedApps ||
            (!item.previewAvailable || !item.contentComplete || !com.jeerovan.comfer.notifications.hasNotificationPreview(item.title, item.text)) ||
            handles[key]?.notification?.visibility == android.app.Notification.VISIBILITY_SECRET ||
            getSystemService(android.app.KeyguardManager::class.java).isDeviceLocked) null else item
    }

    private fun performAction(key: String, revision: Long, action: String, sessionId: String): String = synchronized(lock) {
        if (!connected || sessionId != connectionId || mutableSnapshot.value.health != com.jeerovan.comfer.notifications.ListenerHealth.CONNECTED) return@synchronized "unavailable"
        val item = ledger.current(key, revision) ?: return@synchronized if (action == "dismiss" && ledger.items().none { it.key == key }) "removed" else "changed"
        try {
            val current = getActiveNotifications(arrayOf(key))?.firstOrNull() ?: return@synchronized "removed"
            if (normalize(current).copy(revision = 0) != item.copy(revision = 0)) {
                syncCoordinator.request(); return@synchronized "changed"
            }
            when (action) {
                "open" -> {
                    // Launch first. act() requests cancellation only after launch succeeds.
                    val intent = current.notification.contentIntent
                    if (intent != null) intent.send()
                    else return@synchronized openApp(item)
                }
                "dismiss" -> {
                    if (!item.clearable || item.protected || item.summary || item.appId in com.jeerovan.comfer.notifications.NotificationPreferences.state.value.protectedApps) return@synchronized "protected"
                    cancelNotification(key)
                }
                "snooze" -> {
                    if (android.os.Build.VERSION.SDK_INT < 26 || !item.clearable || item.protected || item.appId in com.jeerovan.comfer.notifications.NotificationPreferences.state.value.protectedApps) return@synchronized "unavailable"
                    snoozeNotification(key, 15 * 60_000L)
                }
                else -> return@synchronized "unavailable"
            }
            "requested"
        } catch (_: android.app.PendingIntent.CanceledException) { if (action == "open") openApp(item) else "expired" }
        catch (_: Exception) { "failed" }
    }

    private fun openApp(item: com.jeerovan.comfer.notifications.NotificationItem): String {
        if (item.profile != android.os.Process.myUserHandle().hashCode()) return "unavailable"
        return try {
            val intent = packageManager.getLaunchIntentForPackage(item.app) ?: return "unavailable"
            startActivity(intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
            "app_open_requested"
        } catch (_: Exception) { "failed" }
    }

    override fun onListenerDisconnected() {
        connectionGeneration++
        connected = false
        val ownsSnapshot = connectedService == null || connectedService === this
        if (connectedService === this) connectedService = null
        synchronized(lock) {
            if (ownsSnapshot) _activeNotifications.value = emptyList()
            handles.clear(); actionIdentities.clear(); ledger.reconcile(emptyList())
            if (ownsSnapshot) mutableSnapshot.value = mutableSnapshot.value.copy(items = emptyList(), health = if (hasAccess(this)) com.jeerovan.comfer.notifications.ListenerHealth.RECONNECTING else com.jeerovan.comfer.notifications.ListenerHealth.ACCESS_NEEDED)
        }
        syncCoordinator.stop(); periodicJob?.cancel()
        super.onListenerDisconnected()
        refresh(this)
    }

    override fun onDestroy() {
        connectionGeneration++
        connected = false
        val ownsSnapshot = connectedService == null || connectedService === this
        if (connectedService === this) connectedService = null
        synchronized(lock) {
            handles.clear(); actionIdentities.clear(); ledger.reconcile(emptyList())
            if (ownsSnapshot) _activeNotifications.value = emptyList()
            if (ownsSnapshot) mutableSnapshot.value = mutableSnapshot.value.copy(items = emptyList(), health = if (hasAccess(this)) com.jeerovan.comfer.notifications.ListenerHealth.RECONNECTING else com.jeerovan.comfer.notifications.ListenerHealth.ACCESS_NEEDED)
        }
        serviceScope.cancel(); events.close()
        super.onDestroy()
    }
}
