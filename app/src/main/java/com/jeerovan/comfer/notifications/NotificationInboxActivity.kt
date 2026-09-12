@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.jeerovan.comfer.notifications

import androidx.core.graphics.drawable.toBitmap
import androidx.compose.ui.graphics.asImageBitmap
import android.content.Context
import android.content.Intent
import android.app.ActivityOptions
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.draw.rotate
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.selection.triStateToggleable
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.semantics.Role
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.jeerovan.comfer.MyNotificationListenerService
import com.jeerovan.comfer.ui.theme.ComferTheme
import com.jeerovan.comfer.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class NotificationInboxActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        NotificationPreferences.initialize(this)
        enableEdgeToEdge()
        setContent { ComferTheme { NotificationInbox(onBack = ::finish, initialApp = intent.getStringExtra("app"), initialControls = intent.getBooleanExtra("controls", false)) } }
    }
    override fun onResume() {
        super.onResume()
        MyNotificationListenerService.refresh(this)
        lifecycleScope.launch { NotificationQuietHours.reconcile(this@NotificationInboxActivity) }
    }
    companion object {
        fun open(context: Context, app: String? = null, controls: Boolean = false) {
            val intent = Intent(context, NotificationInboxActivity::class.java)
                .putExtra("app", app)
                .putExtra("controls", controls)
            val transition = ActivityOptions.makeCustomAnimation(
                context,
                R.anim.notification_inbox_enter,
                R.anim.notification_inbox_underlay,
            )
            context.startActivity(intent, transition.toBundle())
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NotificationHomeEntry(
    iconSize: androidx.compose.ui.unit.Dp,
    hasAccess: Boolean,
    modifier: Modifier = Modifier,
    horizontal: Boolean = true,
    color: androidx.compose.ui.graphics.Color = LocalContentColor.current,
    showBorder: Boolean = false,
    maxVisibleIcons: Int = 5,
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    remember { NotificationPreferences.initialize(context); true }
    LaunchedEffect(hasAccess) { MyNotificationListenerService.refresh(context) }
    val config by NotificationPreferences.state.collectAsState()
    val recoveryNeeded by NotificationPreferences.recoveryNeeded.collectAsState()
    val snapshot by MyNotificationListenerService.snapshot.collectAsState()
    val activeNotifications by MyNotificationListenerService.activeNotifications.collectAsState()
    if (!hasAccess && !config.setup) return
    val groups = notificationChildren(snapshot.items)
        .groupBy { it.appId }
    val apps = groups.values.map { it.first() }.sortedByDescending { it.appId in config.pinned }
    val limit = maxVisibleIcons.coerceAtLeast(1)
    val visible = if (apps.size > limit + 1) apps.take(limit) else apps
    val targetSize = maxOf(32.dp, iconSize + 8.dp)
    val longSide = maxOf(iconSize * 8 + 8.dp, targetSize * minOf(apps.size.coerceAtLeast(1), limit + 1) + 8.dp)
    val listContent: LazyListScope.() -> Unit = {
        if (apps.isEmpty()) item {
            Box(Modifier.size(targetSize), contentAlignment = Alignment.Center) {
                NotificationContrastIcon(androidx.compose.ui.graphics.vector.rememberVectorPainter(Icons.Outlined.Inbox), iconSize, color)
            }
        } else items(visible, key = { it.appId }) { item ->
            val notification = activeNotifications.firstOrNull { it.key == item.key }?.notification
            val smallIcon = remember(item.key, item.revision, notification, resources.configuration) {
                loadNotificationSmallIcon(context, notification)
            }
            Box(Modifier.size(targetSize), contentAlignment = Alignment.Center) {
                val bitmap = remember(smallIcon) { smallIcon?.let { it.toBitmap() } }
                if (bitmap != null) NotificationContrastIcon(
                    remember(bitmap) { androidx.compose.ui.graphics.painter.BitmapPainter(bitmap.asImageBitmap()) }, iconSize, color)
            }
        }
        if (apps.size > visible.size) item {
            Box(Modifier.size(targetSize), contentAlignment = Alignment.Center) {
                Text("+${apps.size - visible.size}", color = color, style = LocalTextStyle.current.copy(shadow = androidx.compose.ui.graphics.Shadow(notificationIconShadowColor(color), androidx.compose.ui.geometry.Offset(0f, 1f), 4f)))
            }
        }
    }
    val inboxLabel = stringResource(R.string.notification_inbox)
    Box(modifier.size(width = if (horizontal) longSide else targetSize + 8.dp, height = if (horizontal) targetSize + 8.dp else longSide)
        .semantics(mergeDescendants = true) { contentDescription = inboxLabel }
        .clickable(role = androidx.compose.ui.semantics.Role.Button) { NotificationInboxActivity.open(context) }
        .border(if (showBorder) 2.dp else 0.dp, if (showBorder) color else androidx.compose.ui.graphics.Color.Transparent,
        androidx.compose.foundation.shape.RoundedCornerShape(8.dp)).padding(4.dp), contentAlignment = Alignment.Center) {
        if (horizontal) LazyRow(verticalAlignment = Alignment.CenterVertically, content = listContent)
        else LazyColumn(horizontalAlignment = Alignment.CenterHorizontally, content = listContent)
    }
}

internal fun loadNotificationSmallIcon(context: Context, notification: android.app.Notification?): android.graphics.drawable.Drawable? =
    runCatching { notification?.smallIcon?.loadDrawable(context)?.mutate() }.getOrNull()
        ?: androidx.core.content.res.ResourcesCompat.getDrawable(context.resources, android.R.drawable.ic_dialog_info, context.theme)

internal fun appLabel(context: Context, packageName: String): String = runCatching {
    context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(packageName, 0)).toString()
}.getOrDefault(packageName)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NotificationInbox(onBack: () -> Unit, initialApp: String? = null, initialControls: Boolean = false) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    val snapshot by MyNotificationListenerService.snapshot.collectAsState()
    val config by NotificationPreferences.state.collectAsState()
    val recoveryNeeded by NotificationPreferences.recoveryNeeded.collectAsState()
    val quietStatus by NotificationQuietHours.state.collectAsState()
    remember { NotificationHistory.initialize(context); true }
    val savedHistory by NotificationHistory.records.collectAsState()
    var historyLocked by remember { mutableStateOf(context.getSystemService(android.app.KeyguardManager::class.java).isDeviceLocked) }
    LaunchedEffect(Unit) { while (true) { historyLocked = context.getSystemService(android.app.KeyguardManager::class.java).isDeviceLocked; delay(500) } }

    LaunchedEffect(config.paused, config.quietSchedule) { NotificationQuietHours.reconcile(context) }
    var app by rememberSaveable { mutableStateOf(initialApp) }
    var savedTab by rememberSaveable { mutableStateOf(false) }
    var savedQuery by rememberSaveable { mutableStateOf("") }
    var savedSelections by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var savedCollapsed by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var savedDeleteConfirm by rememberSaveable { mutableStateOf(false) }
    val savedCopies = if (historyLocked) emptyList() else savedNotificationsMatching(savedHistory, savedQuery, snapshot.items)
    LaunchedEffect(savedCopies) {
        savedSelections = savedSelections.filter { id -> savedCopies.any { it.id == id } }
        savedDeleteConfirm = false
    }
    var settings by rememberSaveable { mutableStateOf(false) }
    var settingsPage by rememberSaveable { mutableStateOf("root") }
    var editingRuleId by rememberSaveable { mutableStateOf<String?>(null) }
    var ruleSourceKey by rememberSaveable { mutableStateOf<String?>(null) }
    var ruleSourceSavedId by rememberSaveable { mutableStateOf<String?>(null) }
    var ruleFromActions by rememberSaveable { mutableStateOf(false) }
    val savedActionSource = savedHistory.firstOrNull { it.id == ruleSourceSavedId }
    val settingsStateHolder = androidx.compose.runtime.saveable.rememberSaveableStateHolder()
    DisposableEffect(settings, settingsPage, config.historyEnabled, savedTab) {
        val activity = context as? android.app.Activity
        val secure = savedTab || config.historyEnabled || (settings && settingsPage in setOf("history", "rule_editor"))
        if (secure) activity?.window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { if (secure) activity?.window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE) }
    }
    val initialSelection = if (initialControls) snapshot.items.firstOrNull { it.appId == initialApp } else null
    var selectedKey by rememberSaveable { mutableStateOf(initialSelection?.key) }
    var selectedRevision by rememberSaveable { mutableLongStateOf(initialSelection?.revision ?: -1L) }
    var selectedSession by rememberSaveable { mutableStateOf(snapshot.sessionId) }
    var selectionSession by rememberSaveable { mutableStateOf(snapshot.sessionId) }
    var appControlsOnly by rememberSaveable { mutableStateOf(initialControls) }
    var confirmation by remember { mutableStateOf(false) }
    var actionsVisible by rememberSaveable { mutableStateOf(initialControls && initialSelection != null) }
    // Keep only app routing metadata when a source copy is deleted or expires.
    var actionApp by rememberSaveable { mutableStateOf(initialSelection?.app) }
    var actionProfile by rememberSaveable { mutableIntStateOf(initialSelection?.profile ?: 0) }
    var actionChannel by rememberSaveable { mutableStateOf<String?>(null) }
    var collapsedApps by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var awaitingSettingsReturn by rememberSaveable { mutableStateOf(false) }
    var returnRevision by remember { mutableIntStateOf(0) }
    var selections by rememberSaveable { mutableStateOf<Map<String, Long>>(initialSelection?.let { mapOf(it.key to it.revision) } ?: emptyMap()) }
    val selectionMode = selections.isNotEmpty()

    var bulkConfirm by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    var access by remember { mutableStateOf(MyNotificationListenerService.hasAccess(context)) }
    LaunchedEffect(Unit) { while (true) { delay(1000); access = MyNotificationListenerService.hasAccess(context) } }
    val selected = snapshot.items.firstOrNull { it.key == selectedKey && it.revision == selectedRevision && selectedSession == snapshot.sessionId && snapshot.health == ListenerHealth.CONNECTED }
    fun clearSelection() { savedSelections = emptyList(); savedDeleteConfirm = false; selections = emptyMap(); selectedKey = null; actionsVisible = false; bulkConfirm = false }
    LaunchedEffect(snapshot, selectedKey) {
        val currentSelections = selections.filter { (key, revision) ->
            snapshot.health == ListenerHealth.CONNECTED && selectionSession == snapshot.sessionId &&
                snapshot.items.any { it.key == key && it.revision == revision }
        }
        if (currentSelections.size != selections.size) {
            selections = currentSelections; bulkConfirm = false
            status = resources.getString(R.string.notification_changed)
        }
        if (selectedKey != null && selected == null) {
            selectedKey = currentSelections.keys.firstOrNull()
            selectedRevision = snapshot.items.firstOrNull { it.key == selectedKey }?.revision ?: -1L
            selectedSession = snapshot.sessionId
            confirmation = false; actionsVisible = false
        }
    }
    fun back() { when {
        actionsVisible && !settings -> actionsVisible = false
        !settings && savedTab && savedDeleteConfirm -> savedDeleteConfirm = false
        !settings && savedTab && savedSelections.isNotEmpty() -> savedSelections = emptyList()
        bulkConfirm -> bulkConfirm = false
        confirmation -> confirmation = false
        settings && settingsPage == "rule_editor" && ruleFromActions -> { settings = false; settingsPage = "root"; actionsVisible = true }
        settings && settingsPage != "root" -> settingsPage = notificationSettingsParent(settingsPage)
        settings -> settings = false
        savedTab -> savedTab = false
        selectionMode -> clearSelection()
        app != null -> app = null
        else -> onBack()
    } }
    BackHandler { back() }
    val inboxListState = rememberLazyListState()
    val savedListState = rememberLazyListState()
    val settingsListState = rememberLazyListState()
    val actionsListState = rememberLazyListState()
    val listState = if (settings) settingsListState else if (actionsVisible) actionsListState else if (savedTab) savedListState else inboxListState
    val screen = if (settings) "settings:$settingsPage" else if (actionsVisible) "actions:$actionProfile:$actionApp" else if (savedTab) "saved" else "inbox:$app"
    var lastScreen by rememberSaveable { mutableStateOf(screen) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && awaitingSettingsReturn) {
                awaitingSettingsReturn = false
                actionsVisible = false; settings = false
                returnRevision++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val records = notificationChildren(snapshot.items).filter { item ->
        app == null || item.appId == app
    }.sortedWith(if (config.chronological) compareByDescending<NotificationItem> { it.postedAt }
        else compareByDescending<NotificationItem> { it.appId in config.pinned }.thenBy { it.appId }.thenByDescending { it.postedAt })
    val displayRows = notificationDisplayRows(records, config.chronological, collapsedApps.toSet())
    val selectedItems = records.filter { selections[it.key] == it.revision && selectionSession == snapshot.sessionId }
    val canBatchManage = snapshot.health == ListenerHealth.CONNECTED && selectedItems.isNotEmpty() &&
        selectedItems.size == selections.size && selectedItems.all { canManageNotification(it, config.protectedApps) }
    LaunchedEffect(screen, returnRevision) {
        if (lastScreen != screen || returnRevision > 0) {
            val anchor = selectedKey ?: selections.keys.firstOrNull()
            val index = notificationAnchorIndex(displayRows, anchor)
            val statusRows = 5 + if (quietStatus.health == QuietHealth.CLEANUP_NEEDED || quietStatus.health == QuietHealth.FAILED) 1 else 0
            // Apply to the next layout, rather than scrolling the outgoing menu content.
            if (!settings && !savedTab && !actionsVisible && index >= 0) listState.requestScrollToItem(statusRows + index)
            else if (!settings && !actionsVisible && savedTab && savedSelections.isNotEmpty()) {
                val savedRows = savedNotificationRows(savedCopies, config.chronological, savedCollapsed.toSet(), config.pinned)
                val anchorCopy = savedCopies.firstOrNull { it.id in savedSelections }
                val savedIndex = savedRows.indexOfFirst { it.record?.id == anchorCopy?.id && it.record != null }
                    .takeIf { it >= 0 } ?: savedRows.indexOfFirst { it.record == null && it.appId == anchorCopy?.appId }
                listState.requestScrollToItem(if (savedIndex >= 0) statusRows + 3 + savedIndex else 0)
            } else listState.requestScrollToItem(0)
            lastScreen = screen
        }
    }
    fun clearSelectionNotice() {
        if (status == resources.getString(R.string.notification_changed)) status = ""
    }
    fun toggleSelection(item: NotificationItem) {
        clearSelectionNotice()
        selectionSession = snapshot.sessionId; selectedSession = snapshot.sessionId
        selections = if (item.key in selections) selections - item.key else selections + (item.key to item.revision)
        selectedKey = if (item.key in selections) item.key else selections.keys.firstOrNull()
        selectedRevision = records.firstOrNull { it.key == selectedKey }?.revision ?: -1L
        bulkConfirm = false
    }
    fun toggleGroup(appId: String) {
        val group = records.filter { it.appId == appId }
        if (group.isEmpty()) return
        clearSelectionNotice()
        val allSelected = group.all { selections[it.key] == it.revision }
        selectionSession = snapshot.sessionId; selectedSession = snapshot.sessionId
        selections = if (allSelected) selections - group.map { it.key }.toSet()
            else selections + group.associate { it.key to it.revision }
        selectedKey = selectedKey?.takeIf { it in selections } ?: selections.keys.firstOrNull()
        selectedRevision = records.firstOrNull { it.key == selectedKey }?.revision ?: -1L
        actionsVisible = false; confirmation = false; bulkConfirm = false
    }
    fun save(transform: (NotificationConfiguration) -> NotificationConfiguration) {
        scope.launch { if (!NotificationPreferences.update(transform)) status = resources.getString(R.string.notification_save_failed) }
    }
    suspend fun performAction(item: NotificationItem, action: String): Boolean {
        if (busy) return false
        val actionSession = snapshot.sessionId
        busy = true
        try {
            val result = MyNotificationListenerService.act(item.key, item.revision, action, actionSession)
            status = when (result) {
                "requested", "app_open_requested" -> ""
                "changed", "removed" -> resources.getString(R.string.notification_changed)
                "opened_dismiss_failed" -> "Opened, but could not dismiss the notification. You can retry by swiping it."
                "history_failed" -> resources.getString(R.string.notification_history_save_before_open_failed)
                else -> resources.getString(R.string.notification_action_unavailable)
            }
            clearSelection(); confirmation = false
            return result == "requested" || result == "app_open_requested"
        } finally {
            busy = false
        }
    }
    fun act(item: NotificationItem, action: String) { scope.launch { performAction(item, action) } }
    fun batchAction(action: String) {
        if (busy || !canBatchManage) return
        val batch = selections.toMap(); val session = selectionSession
        busy = true
        scope.launch {
            try {
                var requested = 0
                batch.forEach { (key, revision) -> if (MyNotificationListenerService.act(key, revision, action, session) == "requested") requested++ }
                status = if (requested == batch.size) "" else resources.getString(R.string.notification_action_unavailable)
                clearSelection()
            } finally { busy = false }
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface.copy(alpha = .8f)).safeDrawingPadding().imePadding()) {
        val landscape = maxWidth > maxHeight
        val startPadding = if (landscape) 0.dp else (maxHeight.value - reachableHeightDp(maxHeight.value)).coerceAtLeast(0f).dp
        Surface(Modifier.fillMaxSize().testTag("notification-inbox-panel"), color = androidx.compose.ui.graphics.Color.Transparent, contentColor = MaterialTheme.colorScheme.onSurface, tonalElevation = 0.dp) {
            Column(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
                key(screen) {
                LazyColumn(Modifier.weight(1f).testTag("notification-inbox-list"), state = listState,
                    contentPadding = PaddingValues(top = startPadding),
                    verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    item { Text(stringResource(R.string.notification_inbox), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(vertical = 8.dp)) }
                    item { if (!access || snapshot.health != ListenerHealth.CONNECTED) Text(stringResource(if (!access) R.string.notification_access_needed else if (snapshot.health == ListenerHealth.RESTRICTED) R.string.notification_restricted else if (snapshot.health == ListenerHealth.RECOVERY_NEEDED) R.string.notification_recovery_needed else R.string.notification_reconnecting), style = MaterialTheme.typography.bodySmall) }
                    item { if (snapshot.reconciliationNeeded) Text(stringResource(R.string.notification_reconciling), style = MaterialTheme.typography.bodySmall) }
                    item { if (recoveryNeeded) Text(stringResource(R.string.notification_configuration_recovery), style = MaterialTheme.typography.bodySmall) }
                    item { if (status.isNotEmpty()) Text(status, style = MaterialTheme.typography.bodySmall) }
                    if (quietStatus.health == QuietHealth.CLEANUP_NEEDED || quietStatus.health == QuietHealth.FAILED) item { Text(stringResource(if (quietStatus.health == QuietHealth.CLEANUP_NEEDED) R.string.notification_quiet_cleanup else R.string.notification_quiet_failed)) }
                    if (settings) {
                        item { Text(if (settingsPage == "root") "Notification settings" else when (settingsPage) {
                            "rule_editor" -> "Content rule"; "quiet" -> "Quiet hours"; "focus" -> "Focus timers"
                            "schedule" -> "Recurring schedule"; "filters" -> "Filters"; "history" -> "History"
                            else -> "Connection and privacy"
                        }, style = MaterialTheme.typography.titleMedium) }
                        if (settingsPage == "root") {
                            item { NotificationViewChoices(config.chronological) { chronological -> save { it.copy(chronological = chronological) } } }
                            item {
                                OutlinedButton(onClick = { save { it.copy(paused = !it.paused) } }) {
                                    Text(stringResource(if (config.paused) R.string.notification_resume_automation else R.string.notification_pause_automation))
                                }
                            }
                            item { SettingsDestination("Quiet hours", "${if (config.paused) "Paused" else "Enabled"} · focus timers and schedules", { settingsPage = "quiet" }) }
                            item { SettingsDestination("Filters", "Content dismissal rules", { settingsPage = "filters" }) }
                            item { SettingsDestination("History", if (config.historyEnabled) "Saving local copies" else "Local copies are off", { settingsPage = "history" }) }
                            item { SettingsDestination("Connection and privacy", "Refresh, Android settings and reset", { settingsPage = "connection" }) }
                        } else if (settingsPage in setOf("quiet", "focus", "schedule")) {
                            item { settingsStateHolder.SaveableStateProvider(settingsPage) { NotificationQuietSettings(settingsPage) { settingsPage = it } } }
                            if (settingsPage == "quiet") item { TextButton(onClick = { context.startActivity(Intent("android.settings.ZEN_MODE_SETTINGS").takeIf { it.resolveActivity(context.packageManager) != null } ?: Intent(Settings.ACTION_SOUND_SETTINGS)) }) { Text(stringResource(R.string.notification_quiet_settings)) } }
                        } else if (settingsPage == "history") {
                            item { settingsStateHolder.SaveableStateProvider(settingsPage) { NotificationHistorySettings() } }
                        } else if (settingsPage == "rule_editor") {
                            if (ruleSourceSavedId != null && historyLocked) item { Text("Unlock your device to view saved notifications.") }
                            else if (ruleSourceSavedId != null && savedActionSource == null) item { Text("This saved notification is no longer available.") }
                            else item { key(editingRuleId, ruleSourceKey, ruleSourceSavedId) {
                                NotificationRuleEditor(editingRuleId, source = snapshot.items.firstOrNull { it.key == ruleSourceKey }, savedSource = savedActionSource,
                                    onSaved = { if (ruleFromActions) { settings = false; settingsPage = "root" } else settingsPage = "filters" })
                            } }
                        } else if (settingsPage == "filters") {
                            item { NotificationRulesSettings(onEdit = { editingRuleId = it; ruleSourceKey = null; ruleSourceSavedId = null; ruleFromActions = false; settingsPage = "rule_editor" }) }

                        } else {
                            item { Text(stringResource(R.string.notification_last_sync, snapshot.lastSync?.let { java.text.DateFormat.getTimeInstance().format(java.util.Date(it)) } ?: "—")) }
                            item { Button(onClick = { runCatching { MyNotificationListenerService.refresh(context, force = true) }.onFailure { status = resources.getString(R.string.notification_action_unavailable) } }) { Text(stringResource(R.string.notification_refresh)) } }
                            item { Text(stringResource(R.string.notification_live_privacy), style = MaterialTheme.typography.bodySmall) }
                            item { TextButton(onClick = { confirmation = true }) { Text(stringResource(R.string.notification_reset)) } }
                        }
                    } else if (actionsVisible) {
                        val targetApp = actionApp
                        if (savedTab && historyLocked) item { Text("Unlock your device to view saved notifications.") }
                        else if (targetApp == null) item { Text("This app is no longer available.") }
                        else item {
                            val targetId = "$actionProfile:$targetApp"
                            NotificationAppActions(appLabel(context, targetApp), targetId in config.protectedApps,
                                canCreateRule = if (savedTab) savedActionSource != null else selected != null, busy = busy,
                                onCreateRule = {
                                    editingRuleId = null; ruleFromActions = true
                                    if (!savedTab) { ruleSourceKey = selected?.key; ruleSourceSavedId = null }
                                    actionsVisible = false; settings = true; settingsPage = "rule_editor"
                                },
                                onToggleProtection = {
                                    busy = true
                                    scope.launch {
                                        try {
                                            val updated = NotificationPreferences.update { it.copy(protectedApps =
                                                if (targetId in it.protectedApps) it.protectedApps - targetId else it.protectedApps + targetId) }
                                            status = if (updated && NotificationHistory.refresh()) "" else resources.getString(R.string.notification_save_failed)
                                        } finally { busy = false }
                                    }
                                },
                                onOpenSettings = {
                                    if (actionProfile != android.os.Process.myUserHandle().hashCode()) {
                                        status = resources.getString(R.string.notification_profile_settings)
                                    } else {
                                        awaitingSettingsReturn = true
                                        if (notificationAppSettingsDestinations(targetApp, actionChannel).none { runCatching { context.startActivity(it) }.isSuccess }) {
                                            awaitingSettingsReturn = false
                                            status = resources.getString(R.string.notification_action_unavailable)
                                        }
                                    }
                                })
                        }
                    } else if (savedTab) {
                        item { Text(stringResource(R.string.notification_saved), style = MaterialTheme.typography.titleMedium) }
                        item { OutlinedTextField(savedQuery, { savedQuery = it.take(256) }, label = { Text(stringResource(R.string.notification_saved_search)) }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("saved-search")) }
                        item { Text(stringResource(R.string.notification_saved_swipe_hint), style = MaterialTheme.typography.bodySmall) }
                        if (historyLocked) item { Text("Unlock your device to view saved notifications.") }
                        else {
                            val copies = savedCopies
                            if (copies.isEmpty()) item { Text(stringResource(R.string.notification_saved_empty)) }
                            savedNotificationItems(copies, config.chronological, savedCollapsed.toSet(), config.pinned,
                                savedSelections.toSet(), !busy,
                                onCollapse = { id -> savedCollapsed = if (id in savedCollapsed) savedCollapsed - id else savedCollapsed + id },
                                onSelect = { id -> savedSelections = if (id in savedSelections) savedSelections - id else savedSelections + id; savedDeleteConfirm = false },
                                onGroupSelect = { appId ->
                                    val ids = copies.filter { it.appId == appId }.map { it.id }
                                    savedSelections = if (ids.all { it in savedSelections }) savedSelections - ids.toSet() else (savedSelections + ids).distinct()
                                    savedDeleteConfirm = false
                                },
                                onOpen = { copy ->
                                    // Archived text has no reusable PendingIntent. Never launch into a different profile.
                                    val launched = copy.profile == android.os.Process.myUserHandle().hashCode() && runCatching {
                                        val intent = context.packageManager.getLaunchIntentForPackage(copy.app) ?: return@runCatching false
                                        context.startActivity(intent); true
                                    }.getOrDefault(false)
                                    status = if (launched) "" else "Could not open this app. It may be unavailable or in another profile."
                                },
                                onDelete = { copy ->
                                    val deleted = NotificationHistory.delete(copy.id)
                                    if (!deleted) status = "Could not delete saved notification."
                                    deleted
                                })
                        }
                    } else {
                        if (records.isEmpty() && access && snapshot.health == ListenerHealth.CONNECTED) item { Text(stringResource(R.string.notification_empty), modifier = Modifier.padding(12.dp)) }
                        items(displayRows, key = { it.key }) { row ->
                            Box(Modifier.animateItem(fadeInSpec = tween(200), placementSpec = tween(250), fadeOutSpec = tween(200))) {
                                val item = row.notification
                                if (item == null) {
                                    val expanded = row.appId !in collapsedApps
                                    val caretRotation by animateFloatAsState(if (expanded) 180f else 0f, tween(250), label = "group-caret")
                                    val group = records.filter { it.appId == row.appId }
                                    val selectedCount = group.count { selections[it.key] == it.revision }
                                    val groupState = when (selectedCount) {
                                        0 -> ToggleableState.Off
                                        group.size -> ToggleableState.On
                                        else -> ToggleableState.Indeterminate
                                    }
                                    Row(Modifier.fillMaxWidth().testTag(row.key), verticalAlignment = Alignment.CenterVertically) {
                                        Row(Modifier.weight(1f).heightIn(min = 48.dp).testTag("group-select:${row.appId}")
                                            .then(if (selectionMode) Modifier.triStateToggleable(groupState, role = Role.Checkbox, onClick = { toggleGroup(row.appId) }) else Modifier)
                                            .padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Text(appLabel(context, row.appId.substringAfter(':')), style = MaterialTheme.typography.titleMedium,
                                                color = if (selectionMode && groupState == ToggleableState.On) MaterialTheme.colorScheme.primary else LocalContentColor.current)
                                        }
                                        IconButton(modifier = Modifier.testTag("group-expand:${row.appId}"), onClick = {
                                            collapsedApps = if (expanded) collapsedApps + row.appId else collapsedApps - row.appId
                                        }) {
                                            Icon(Icons.Outlined.KeyboardArrowDown, modifier = Modifier.rotate(caretRotation), contentDescription = stringResource(if (expanded) R.string.notification_collapse else R.string.notification_expand))
                                        }
                                    }
                                } else {
                                    key(item.key, item.revision, snapshot.sessionId) {
                                        val preview = remember { NotificationBodyPreviewState() }
                                        NotificationSwipeContainer(
                                            enabled = !busy && snapshot.health == ListenerHealth.CONNECTED && canManageNotification(item, config.protectedApps),
                                            onDismiss = { performAction(item, "dismiss") },
                                        ) {
                                            Card(Modifier.fillMaxWidth().animateContentSize(tween(250)).testTag("notification-card-${item.key}").combinedClickable(
                                                onClick = { if (selectionMode) toggleSelection(item) else preview.tap { act(item, "open") } },
                                                onLongClick = {
                                                    clearSelectionNotice()
                                                    selectionSession = snapshot.sessionId; selectedSession = snapshot.sessionId
                                                    selections = selections + (item.key to item.revision)
                                                    selectedKey = item.key; selectedRevision = item.revision; actionsVisible = false
                                                },
                                            ), colors = CardDefaults.cardColors(containerColor = if (item.key in selections) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer)) {
                                                Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                                    val checked = item.key in selections
                                                    // Reserve the same space in both modes; only selection mode
                                                    // exposes a checkbox action and draws its indicator.
                                                    Box(Modifier.size(48.dp).then(if (selectionMode) Modifier
                                                        .testTag("notification-select-${item.key}")
                                                        .semantics { contentDescription = resources.getString(R.string.notification_select) }
                                                        .toggleable(value = checked, role = Role.Checkbox, onValueChange = { toggleSelection(item) }) else Modifier), contentAlignment = Alignment.Center) {
                                                        if (selectionMode) NotificationRingDot(checked)
                                                    }
                                                    Column(Modifier.weight(1f)) {
                                                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                            Text(item.title.ifEmpty { resources.getString(R.string.notification_preview_unavailable) }, modifier = Modifier.weight(1f).alignByBaseline(), style = MaterialTheme.typography.titleSmall)
                                                            Text(java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(java.util.Date(item.postedAt)), modifier = Modifier.alignByBaseline(), style = MaterialTheme.typography.labelMedium, maxLines = 1)
                                                        }
                                                        if (config.chronological) Text(appLabel(context, item.app), style = MaterialTheme.typography.labelMedium)
                                                        NotificationBodyPreview(item.text, preview)
                                                        if (item.progressIndeterminate) LinearProgressIndicator(Modifier.fillMaxWidth())
                                                        else if (item.progressMax > 0) LinearProgressIndicator(progress = { (item.progress.toFloat() / item.progressMax).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                    }
                }
                }
                // Permission is authoritative even if a late listener update reports connected.
                // Keep recovery actions outside the scrolling list so they remain reachable.
                if ((!access || snapshot.health != ListenerHealth.CONNECTED) && !settings && !savedTab) {
                    FlowRow(Modifier.fillMaxWidth().testTag("notification-connection-actions")) {
                       TextButton(modifier = Modifier.weight(1f), onClick = { MyNotificationListenerService.refresh(context, force = true) }) { Text(stringResource(R.string.notification_refresh)) }
                       TextButton(modifier = Modifier.weight(1f), onClick = { runCatching { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) } }) { Text(stringResource(R.string.notification_access_settings)) }
                    }
                }
                if (confirmation && settings) FlowRow(Modifier.fillMaxWidth()) {
                   TextButton(onClick = { confirmation = false }) { Text(stringResource(R.string.notification_cancel)) }
                   TextButton(onClick = { scope.launch { if (!NotificationQuietHours.reset(context) || !NotificationPreferences.reset()) status = resources.getString(R.string.notification_save_failed) }; confirmation = false }) { Text(stringResource(R.string.notification_confirm)) }
                }
                if (!settings && !actionsVisible && savedTab) {
                    NotificationActionBarTransition {
                        if (savedSelections.isNotEmpty() && !historyLocked) {
                            FlowRow(Modifier.fillMaxWidth().testTag("saved-selected-actions"), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                if (savedSelections.size == 1) TextButton(enabled = !busy, onClick = {
                                    val copy = savedCopies.firstOrNull { it.id == savedSelections.single() }
                                    if (copy != null) {
                                        ruleSourceSavedId = copy.id; ruleSourceKey = null; editingRuleId = null
                                        actionApp = copy.app; actionProfile = copy.profile; actionChannel = null
                                        clearSelectionNotice(); savedDeleteConfirm = false; settings = false; actionsVisible = true
                                    }
                                }) { Text(stringResource(R.string.notification_more)) }
                                TextButton(enabled = !busy, onClick = {
                                    if (savedSelections.size > 1 && !savedDeleteConfirm) savedDeleteConfirm = true
                                    else {
                                        val ids = savedSelections.toList()
                                        busy = true
                                        scope.launch {
                                            val failed = ids.filterNot { NotificationHistory.delete(it) }
                                            savedSelections = failed
                                            savedDeleteConfirm = false
                                            status = if (failed.isEmpty()) "" else "Could not delete some saved notifications."
                                            busy = false
                                        }
                                    }
                                }) { Text(if (savedDeleteConfirm) "Confirm delete selected" else "Delete") }
                                TextButton(enabled = !busy, onClick = { savedSelections = emptyList(); savedDeleteConfirm = false }) { Text(stringResource(R.string.notification_cancel)) }
                            }
                        }
                    }
                }
                if (!settings && !savedTab && !actionsVisible) {
                    NotificationActionBarTransition {
                        if (selectionMode) {
                            FlowRow(Modifier.fillMaxWidth().testTag("notification-selected-actions"), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                if (selectedItems.isNotEmpty()) {
                                    if (canBatchManage && android.os.Build.VERSION.SDK_INT >= 26) TextButton(enabled = !busy, onClick = { batchAction("snooze") }) { Text(stringResource(R.string.notification_quick_snooze)) }
                                    TextButton(enabled = !busy, onClick = {
                                        val apps = selectedItems.map { it.appId }.toSet()
                                        save { it.copy(pinned = if (apps.all { id -> id in it.pinned }) it.pinned - apps else it.pinned + apps) }
                                    }) { Text(stringResource(R.string.notification_quick_priority)) }
                                    if (selectedItems.size == 1) TextButton(onClick = {
                                        val item = selectedItems.single()
                                        clearSelectionNotice()
                                        selectedKey = item.key; selectedRevision = item.revision; selectedSession = snapshot.sessionId
                                        actionApp = item.app; actionProfile = item.profile; actionChannel = if (appControlsOnly) null else item.channelId
                                        ruleSourceSavedId = null; actionsVisible = true
                                    }) { Text(stringResource(R.string.notification_more)) }
                                    if (canBatchManage && selectedItems.size > 1) TextButton(enabled = !busy, onClick = { if (bulkConfirm) batchAction("dismiss") else bulkConfirm = true }) { Text(stringResource(if (bulkConfirm) R.string.notification_confirm_dismiss_selected else R.string.notification_dismiss_selected)) }
                                }
                                if (selections.size > 1) TextButton(onClick = { clearSelection() }) { Text(stringResource(R.string.notification_cancel)) }
                            }
                        }
                    }
                }
                HorizontalDivider()
                Row(Modifier.fillMaxWidth()) {
                    IconButton(modifier = Modifier.weight(1f), onClick = { clearSelection(); app = null; savedTab = false; settings = false; settingsPage = "root"; confirmation = false; inboxListState.requestScrollToItem(0) }) {
                        Icon(Icons.Outlined.Inbox, stringResource(R.string.notification_all), tint = if (!savedTab && !settings) MaterialTheme.colorScheme.primary else LocalContentColor.current)
                    }
                    IconButton(modifier = Modifier.weight(1f).testTag("saved-tab"), onClick = { clearSelection(); app = null; settings = false; settingsPage = "root"; savedTab = true; confirmation = false }) {
                        Icon(Icons.Outlined.Archive, stringResource(R.string.notification_saved), tint = if (savedTab && !settings) MaterialTheme.colorScheme.primary else LocalContentColor.current)
                    }
                    IconButton(modifier = Modifier.weight(1f), onClick = { if (settings && settingsPage != "root") settingsPage = "root" else { settings = !settings; settingsPage = "root" }; confirmation = false }) {
                        Icon(Icons.Outlined.Settings, stringResource(R.string.notification_settings_tab), tint = if (settings) MaterialTheme.colorScheme.primary else LocalContentColor.current)
                    }
                }
            }
        }
    }
}


/** Animate the list viewport as the action area grows or collapses to zero. */
@Composable
internal fun NotificationActionBarTransition(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxWidth().animateContentSize(animationSpec = tween(250))) {
        content()
    }
}
