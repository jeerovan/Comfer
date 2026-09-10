@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.jeerovan.comfer.notifications

import android.content.Context
import android.content.Intent
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
            context.startActivity(Intent(context, NotificationInboxActivity::class.java).putExtra("app", app).putExtra("controls", controls))
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
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(1000); now = System.currentTimeMillis() } }
    if (!hasAccess && !config.setup) return
    val groups = notificationChildren(snapshot.items).filter { !isVisuallyHidden(it, config, now) }
        .groupBy { it.appId }
    val apps = groups.values.map { it.first() }.sortedByDescending { it.appId in config.pinned }
    val limit = maxVisibleIcons.coerceAtLeast(1)
    val visible = if (apps.size > limit + 1) apps.take(limit) else apps
    val targetSize = maxOf(32.dp, iconSize + 8.dp)
    val longSide = maxOf(iconSize * 8 + 8.dp, targetSize * minOf(apps.size.coerceAtLeast(1), limit + 1) + 8.dp)
    val listContent: LazyListScope.() -> Unit = {
        if (apps.isEmpty()) item {
            Box(Modifier.size(targetSize), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.Inbox, null, modifier = Modifier.size(iconSize), tint = color)
            }
        } else items(visible, key = { it.appId }) { item ->
            val notification = activeNotifications.firstOrNull { it.key == item.key }?.notification
            val smallIcon = remember(item.key, item.revision, notification, resources.configuration) {
                loadNotificationSmallIcon(context, notification)
            }
            Box(Modifier.size(targetSize), contentAlignment = Alignment.Center) {
                androidx.compose.ui.viewinterop.AndroidView(factory = { android.widget.ImageView(it).apply {
                    scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
                    importantForAccessibility = android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO
                } }, update = { view ->
                    view.setImageDrawable(smallIcon)
                    // Android's status bar uses the notification's small-icon silhouette,
                    // not the application's launcher icon or its adaptive background.
                    view.clearColorFilter()
                    view.imageTintList = android.content.res.ColorStateList.valueOf(color.copy(alpha = 1f).toArgb())
                    view.alpha = color.alpha
                }, modifier = Modifier.size(iconSize))
            }
        }
        if (apps.size > visible.size) item {
            Box(Modifier.size(targetSize), contentAlignment = Alignment.Center) {
                Text("+${apps.size - visible.size}", color = color)
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
    var historyLocked by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) { while (true) { historyLocked = context.getSystemService(android.app.KeyguardManager::class.java).isDeviceLocked; delay(500) } }

    LaunchedEffect(config.paused, config.quietSchedule) { NotificationQuietHours.reconcile(context) }
    var app by rememberSaveable { mutableStateOf(initialApp) }
    var hidden by rememberSaveable { mutableStateOf(false) }
    var savedTab by rememberSaveable { mutableStateOf(false) }
    var savedQuery by rememberSaveable { mutableStateOf("") }
    var settings by rememberSaveable { mutableStateOf(false) }
    var settingsPage by rememberSaveable { mutableStateOf("root") }
    var editingRuleId by rememberSaveable { mutableStateOf<String?>(null) }
    var ruleSourceKey by rememberSaveable { mutableStateOf<String?>(null) }
    val settingsStateHolder = androidx.compose.runtime.saveable.rememberSaveableStateHolder()
    DisposableEffect(settings, settingsPage, config.historyEnabled, savedTab) {
        val activity = context as? android.app.Activity
        val secure = savedTab || config.historyEnabled || (settings && settingsPage in setOf("history", "search", "rule_editor"))
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
    var customDays by rememberSaveable { mutableIntStateOf(0) }
    var customMinute by rememberSaveable { mutableIntStateOf(9 * 60) }
    var collapsedApps by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var awaitingSettingsReturn by rememberSaveable { mutableStateOf(false) }
    var returnRevision by remember { mutableIntStateOf(0) }
    var selections by rememberSaveable { mutableStateOf<Map<String, Long>>(initialSelection?.let { mapOf(it.key to it.revision) } ?: emptyMap()) }
    val selectionMode = selections.isNotEmpty()

    var bulkConfirm by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var access by remember { mutableStateOf(MyNotificationListenerService.hasAccess(context)) }
    LaunchedEffect(Unit) { while (true) { delay(1000); now = System.currentTimeMillis(); access = MyNotificationListenerService.hasAccess(context) } }
    val selected = snapshot.items.firstOrNull { it.key == selectedKey && it.revision == selectedRevision && selectedSession == snapshot.sessionId && snapshot.health == ListenerHealth.CONNECTED }
    fun clearSelection() { selections = emptyMap(); selectedKey = null; actionsVisible = false; bulkConfirm = false }
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
        bulkConfirm -> bulkConfirm = false
        confirmation -> confirmation = false
        actionsVisible -> actionsVisible = false
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
    val listState = if (settings) settingsListState else if (savedTab) savedListState else if (actionsVisible) actionsListState else inboxListState
    val screen = if (settings) "settings:$settingsPage" else if (savedTab) "saved" else if (actionsVisible) "actions:$selectedKey" else "inbox:$app:$hidden"
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
        (app == null || item.appId == app) && (isVisuallyHidden(item, config, now) == hidden)
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
            else listState.requestScrollToItem(0)
            lastScreen = screen
        }
    }
    fun toggleSelection(item: NotificationItem) {
        selectionSession = snapshot.sessionId; selectedSession = snapshot.sessionId
        selections = if (item.key in selections) selections - item.key else selections + (item.key to item.revision)
        selectedKey = if (item.key in selections) item.key else selections.keys.firstOrNull()
        selectedRevision = records.firstOrNull { it.key == selectedKey }?.revision ?: -1L
        bulkConfirm = false
    }
    fun toggleGroup(appId: String) {
        val group = records.filter { it.appId == appId }
        if (group.isEmpty()) return
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
            status = resources.getString(when (result) {
                "requested" -> R.string.notification_action_requested
                "app_open_requested" -> R.string.notification_app_open_requested
                "changed", "removed" -> R.string.notification_changed
                "history_failed" -> R.string.notification_history_save_before_open_failed
                else -> R.string.notification_action_unavailable
            })
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
                status = resources.getString(R.string.notification_bulk_result, requested, batch.size - requested)
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
                            "rule_editor" -> "Content rule"; "quiet" -> "Quiet hours"; "focus" -> "Focus timers"; "schedules" -> "Schedules"
                            "schedule" -> "Recurring schedule"; "filters" -> "Filters"; "history" -> "History"
                            "search" -> "Search"; else -> "Connection and privacy"
                        }, style = MaterialTheme.typography.titleMedium) }
                        if (settingsPage == "root") {
                            item { NotificationViewChoices(config.chronological) { chronological -> save { it.copy(chronological = chronological) } } }
                            item { SettingsDestination("Quiet hours", "${if (config.paused) "Paused" else "Enabled"} · focus timers and schedules", { settingsPage = "quiet" }) }
                            item { SettingsDestination("Filters", "Content rules and hidden apps", { settingsPage = "filters" }) }
                            item { SettingsDestination("History", if (config.historyEnabled) "Saving local copies" else "Local copies are off", { settingsPage = "history" }) }
                            item { SettingsDestination("Search", "Search the Saved tab", { clearSelection(); settings = false; savedTab = true }) }
                            item { SettingsDestination("Connection and privacy", "Refresh, Android settings and reset", { settingsPage = "connection" }) }
                        } else if (settingsPage in setOf("quiet", "focus", "schedules", "schedule")) {
                            item { settingsStateHolder.SaveableStateProvider(settingsPage) { NotificationQuietSettings(settingsPage) { settingsPage = it } } }
                            if (settingsPage == "quiet") item { TextButton(onClick = { context.startActivity(Intent("android.settings.ZEN_MODE_SETTINGS").takeIf { it.resolveActivity(context.packageManager) != null } ?: Intent(Settings.ACTION_SOUND_SETTINGS)) }) { Text(stringResource(R.string.notification_quiet_settings)) } }
                        } else if (settingsPage == "history" || settingsPage == "search") {
                            item { settingsStateHolder.SaveableStateProvider(settingsPage) { NotificationHistorySettings() } }
                        } else if (settingsPage == "rule_editor") {
                            item { NotificationRuleEditor(editingRuleId, source = snapshot.items.firstOrNull { it.key == ruleSourceKey }, onSaved = { settingsPage = "filters" }) }
                        } else if (settingsPage == "filters") {
                            item { NotificationRulesSettings(onEdit = { editingRuleId = it; ruleSourceKey = null; settingsPage = "rule_editor" }) }
                            item { Text("Manually hidden apps", style = MaterialTheme.typography.titleSmall) }
                            item { Text(stringResource(R.string.notification_hide_scope)) }
                            if (config.hiddenUntil.none { it.value > now }) item { Text("No manually hidden apps") }
                            config.hiddenUntil.filterValues { it > now }.forEach { (appId, _) -> item(key = "hidden:$appId") {
                                TextButton(onClick = { save { it.showApp(appId) } }) { Text(stringResource(R.string.notification_resume_app, appLabel(context, appId.substringAfter(':')))) }
                            } }
                        } else {
                            item { Text(stringResource(R.string.notification_last_sync, snapshot.lastSync?.let { java.text.DateFormat.getTimeInstance().format(java.util.Date(it)) } ?: "—")) }
                            item { Button(onClick = { runCatching { MyNotificationListenerService.refresh(context, force = true) }.onFailure { status = resources.getString(R.string.notification_action_unavailable) } }) { Text(stringResource(R.string.notification_refresh)) } }
                            item { Text(stringResource(R.string.notification_live_privacy), style = MaterialTheme.typography.bodySmall) }
                            item { TextButton(onClick = { confirmation = true }) { Text(stringResource(R.string.notification_reset)) } }
                        }
                    } else if (savedTab) {
                        item { Text(stringResource(R.string.notification_saved), style = MaterialTheme.typography.titleMedium) }
                        item { OutlinedTextField(savedQuery, { savedQuery = it.take(256) }, label = { Text(stringResource(R.string.notification_saved_search)) }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("saved-search")) }
                        item { Text(stringResource(R.string.notification_saved_swipe_hint), style = MaterialTheme.typography.bodySmall) }
                        if (historyLocked) item { Text("Unlock your device to view saved notifications.") }
                        else {
                            val copies = savedNotificationsMatching(savedHistory, savedQuery, snapshot.items)
                            if (copies.isEmpty()) item { Text(stringResource(R.string.notification_saved_empty)) }
                            items(copies, key = { "saved:${it.id}" }) { copy ->
                                SavedNotificationCard(copy) {
                                    val deleted = NotificationHistory.delete(copy.id)
                                    if (!deleted) status = "Could not dismiss saved notification."
                                    deleted
                                }
                            }
                        }
                    } else if (actionsVisible && selected != null) {
                        item { TextButton(onClick = { editingRuleId = null; ruleSourceKey = selected.key; actionsVisible = false; settings = true; settingsPage = "rule_editor" }) { Text("Create content rule") } }
                        item { Text(appLabel(context, selected.app), style = MaterialTheme.typography.titleMedium) }
                        item { Text(stringResource(R.string.notification_hide_scope)) }
                        for (minutes in listOf(15, 60, 1440)) item {
                           TextButton(onClick = { save { it.copy(hiddenUntil = it.hiddenUntil + (selected.appId to now + minutes * 60_000L)) }; clearSelection() }) {
                                Text(stringResource(R.string.notification_hide_minutes, minutes))
                            }
                        }
                        item { TextButton(onClick = {
                            val tomorrow = java.util.Calendar.getInstance().apply { timeInMillis = now; add(java.util.Calendar.DAY_OF_YEAR, 1); set(java.util.Calendar.HOUR_OF_DAY, 9); set(java.util.Calendar.MINUTE, 0); set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0) }.timeInMillis
                            save { it.copy(hiddenUntil = it.hiddenUntil + (selected.appId to tomorrow)) }; clearSelection()
                        }) { Text(stringResource(R.string.notification_hide_tomorrow)) } }
                        item {
                            val deadline = java.util.Calendar.getInstance().apply { timeInMillis = now; add(java.util.Calendar.DAY_OF_YEAR, customDays); set(java.util.Calendar.HOUR_OF_DAY, customMinute / 60); set(java.util.Calendar.MINUTE, customMinute % 60); set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0) }.timeInMillis
                            Text(stringResource(R.string.notification_custom_resume, java.text.DateFormat.getDateTimeInstance().format(java.util.Date(deadline))))
                            Text(stringResource(R.string.notification_days_from_now, customDays))
                            Slider(customDays.toFloat(), onValueChange = { customDays = it.toInt() }, valueRange = 0f..30f, steps = 29)
                            Text(stringResource(R.string.notification_resume_clock))
                            Slider(customMinute.toFloat(), onValueChange = { customMinute = it.toInt() }, valueRange = 0f..1439f)
                           TextButton(enabled = deadline > now, onClick = { save { it.copy(hiddenUntil = it.hiddenUntil + (selected.appId to deadline)) }; clearSelection() }) { Text(stringResource(R.string.notification_apply_resume)) }
                        }
                        item { TextButton(onClick = { save { it.copy(hiddenUntil = it.hiddenUntil + (selected.appId to Long.MAX_VALUE)) }; clearSelection() }) { Text(stringResource(R.string.notification_hide_always)) } }
                        item { TextButton(onClick = { save { it.showApp(selected.appId) }; clearSelection() }) { Text(stringResource(R.string.notification_show)) } }
                        item { TextButton(onClick = { save { it.copy(protectedApps = if (selected.appId in it.protectedApps) it.protectedApps - selected.appId else it.protectedApps + selected.appId) } }) { Text(stringResource(if (selected.appId in config.protectedApps) R.string.notification_unprotect else R.string.notification_protect)) } }
                        item { TextButton(onClick = {
                            if (selected.profile != android.os.Process.myUserHandle().hashCode()) {
                                status = resources.getString(R.string.notification_profile_settings); return@TextButton
                            }
                            val destinations = buildList {
                                if (android.os.Build.VERSION.SDK_INT >= 26) {
                                    if (selected.channelId != null && !appControlsOnly) add(Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, selected.app).putExtra(Settings.EXTRA_CHANNEL_ID, selected.channelId))
                                    add(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, selected.app))
                                }
                                add(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:" + selected.app)))
                                add(Intent(Settings.ACTION_SETTINGS))
                            }
                            awaitingSettingsReturn = true
                            if (destinations.none { destination -> runCatching { context.startActivity(destination) }.isSuccess }) {
                                awaitingSettingsReturn = false
                                status = resources.getString(R.string.notification_action_unavailable)
                            }
                        }) { Text(stringResource(R.string.notification_sound_settings)) } }
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
                                        NotificationSwipeContainer(
                                            enabled = !busy && snapshot.health == ListenerHealth.CONNECTED && canManageNotification(item, config.protectedApps),
                                            onDismiss = { performAction(item, "dismiss") },
                                        ) {
                                            Card(Modifier.fillMaxWidth().testTag("notification-card-${item.key}").combinedClickable(
                                                onClick = { if (selectionMode) toggleSelection(item) else act(item, "open") },
                                                onLongClick = {
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
                                                        Text((if (config.chronological) appLabel(context, item.app) + " · " else "") + java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(java.util.Date(item.postedAt)), style = MaterialTheme.typography.labelMedium)
                                                        Text(item.title.ifEmpty { resources.getString(R.string.notification_preview_unavailable) }, style = MaterialTheme.typography.titleSmall)
                                                        Text(item.text, maxLines = 2)
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
                if (!settings && !savedTab && !actionsVisible) {
                    NotificationActionBarTransition {
                        if (selectionMode) {
                            FlowRow(Modifier.fillMaxWidth().testTag("notification-selected-actions"), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                if (selectedItems.isNotEmpty()) {
                                    TextButton(enabled = !busy, onClick = {
                                        val apps = selectedItems.map { it.appId }.toSet()
                                        save { previous -> if (hidden) apps.fold(previous) { c, id -> c.showApp(id) } else previous.copy(hiddenUntil = previous.hiddenUntil + apps.associateWith { now + 3_600_000 }) }
                                        clearSelection()
                                    }) { Text(stringResource(if (hidden) R.string.notification_show else R.string.notification_quick_hide)) }
                                    if (canBatchManage && android.os.Build.VERSION.SDK_INT >= 26) TextButton(enabled = !busy, onClick = { batchAction("snooze") }) { Text(stringResource(R.string.notification_quick_snooze)) }
                                    TextButton(enabled = !busy, onClick = {
                                        val apps = selectedItems.map { it.appId }.toSet()
                                        save { it.copy(pinned = if (apps.all { id -> id in it.pinned }) it.pinned - apps else it.pinned + apps) }
                                    }) { Text(stringResource(R.string.notification_quick_priority)) }
                                    if (selectedItems.size == 1) TextButton(onClick = {
                                        val item = selectedItems.single()
                                        selectedKey = item.key; selectedRevision = item.revision; selectedSession = snapshot.sessionId; actionsVisible = true
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
                    IconButton(modifier = Modifier.weight(1f), onClick = { clearSelection(); app = null; savedTab = false; settings = false; settingsPage = "root"; hidden = false; confirmation = false; inboxListState.requestScrollToItem(0) }) {
                        Icon(Icons.Outlined.Inbox, stringResource(R.string.notification_all), tint = if (!hidden && !savedTab && !settings) MaterialTheme.colorScheme.primary else LocalContentColor.current)
                    }
                    IconButton(modifier = Modifier.weight(1f), onClick = { clearSelection(); hidden = if (savedTab) true else !hidden; savedTab = false; settings = false; settingsPage = "root"; confirmation = false }) {
                        Icon(if (hidden) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff, stringResource(if (hidden) R.string.notification_active else R.string.notification_hidden), tint = if (hidden && !savedTab && !settings) MaterialTheme.colorScheme.primary else LocalContentColor.current)
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
