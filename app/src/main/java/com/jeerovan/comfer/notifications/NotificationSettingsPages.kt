@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package com.jeerovan.comfer.notifications

import com.jeerovan.comfer.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

internal fun notificationSettingsParent(page: String): String = when (page) {
    "focus", "schedule" -> "quiet"
    "rule_editor" -> "filters"
    else -> "root"
}

@Composable
internal fun SettingsDestination(title: String, subtitle: String, onClick: () -> Unit) {
    OutlinedCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall)
            }
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null)
        }
    }
}

@Composable
internal fun NotificationHistorySettings() {
    val context = LocalContext.current
    val resources = LocalResources.current
    remember { NotificationHistory.initialize(context); true }
    val config by NotificationPreferences.state.collectAsState()
    val records by NotificationHistory.records.collectAsState()
    val failed by NotificationHistory.failed.collectAsState()
    val gap by NotificationHistory.captureGap.collectAsState()
    val snapshot by com.jeerovan.comfer.MyNotificationListenerService.snapshot.collectAsState()
    val scope = rememberCoroutineScope()
    var consent by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { NotificationHistory.refresh() }
    fun update(transform: (NotificationConfiguration) -> NotificationConfiguration) {
        scope.launch {
            if (!NotificationPreferences.update(transform) || !NotificationHistory.refresh()) message = resources.getString(R.string.ui_could_not_save_changes_try_again)
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (message.isNotEmpty()) Text(message)
        run {
            Text(stringResource(R.string.ui_save_local_history), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.notification_history_help), style = MaterialTheme.typography.bodySmall)
            NotificationSettingToggle(stringResource(R.string.ui_save_notification_history), config.historyEnabled) {
                if (it) consent = true else update { c -> c.copy(historyEnabled = false) }
            }
            Text(stringResource(R.string.ui_keep_copies_for))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (n in listOf(1, 7, 30)) FilterChip(selected = config.historyDays == n, onClick = { update { it.copy(historyDays = n) } }, label = { Text(stringResource(when (n) { 1 -> R.string.notification_history_day_1; 7 -> R.string.notification_history_day_7; else -> R.string.notification_history_day_30 })) })
            }
            Text(stringResource(R.string.notification_history_limits), style = MaterialTheme.typography.bodySmall)
            Text(stringResource(R.string.ui_exclude_apps_from_history), style = MaterialTheme.typography.titleSmall)
            Text(stringResource(R.string.notification_history_exclusions_help), style = MaterialTheme.typography.bodySmall)
            val apps = (snapshot.items.map { it.appId } + records.map { it.appId } + config.historyExcludedApps).distinct()
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (id in apps) FilterChip(selected = id in config.historyExcludedApps, onClick = {
                    update { it.copy(historyExcludedApps = if (id in it.historyExcludedApps) it.historyExcludedApps - id else it.historyExcludedApps + id) }
                }, label = { Text(appLabel(context, id.substringAfter(':'))) })
            }
            OutlinedButton(onClick = { deleting = true }) { Text(stringResource(R.string.ui_delete_all_saved_history)) }
        }
        if (failed) Text(stringResource(R.string.notification_history_storage_failed), color = MaterialTheme.colorScheme.error)
        Text(stringResource(R.string.notification_history_capture_limits), style = MaterialTheme.typography.bodySmall)
        if (gap) Text(stringResource(R.string.ui_some_events_were_skipped_during_this_session))
        Text(stringResource(R.string.notification_history_saved_help), style = MaterialTheme.typography.bodySmall)
    }
    if (consent) AlertDialog(onDismissRequest = { consent = false }, title = { Text(stringResource(R.string.ui_save_notification_copies)) }, text = {
        Text(stringResource(R.string.notification_history_consent))
    }, confirmButton = { TextButton(enabled = !failed, onClick = { consent = false; update { it.copy(historyEnabled = true, historySince = System.currentTimeMillis()) } }) { Text(stringResource(R.string.ui_enable_history)) } }, dismissButton = { TextButton(onClick = { consent = false }) { Text(stringResource(R.string.notification_cancel)) } })
    if (deleting) AlertDialog(onDismissRequest = { deleting = false }, title = { Text(stringResource(R.string.ui_delete_saved_copies)) }, text = { Text(stringResource(if (config.historyEnabled) R.string.notification_history_delete_enabled else R.string.notification_history_delete_disabled)) }, confirmButton = {
        TextButton(onClick = {
            deleting = false
            scope.launch { if (!NotificationHistory.clear()) message = resources.getString(R.string.ui_could_not_delete_saved_history) }
        }) { Text(stringResource(R.string.ui_delete)) }
    }, dismissButton = { TextButton(onClick = { deleting = false }) { Text(stringResource(R.string.notification_cancel)) } })
}
