@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package com.jeerovan.comfer.notifications

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
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
            if (!NotificationPreferences.update(transform) || !NotificationHistory.refresh()) message = "Could not save changes. Try again."
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (message.isNotEmpty()) Text(message)
        run {
            Text("Save local history", style = MaterialTheme.typography.titleMedium)
            Text("Optional encrypted copies on this device. Captures future notification posts and updates only. Turning this off stops capture but keeps saved copies until expiry or deletion. Copies cannot reopen the original notification action.", style = MaterialTheme.typography.bodySmall)
            NotificationSettingToggle("Save notification history", config.historyEnabled) {
                if (it) consent = true else update { c -> c.copy(historyEnabled = false) }
            }
            Text("Keep copies for")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (n in listOf(1, 7, 30)) FilterChip(selected = config.historyDays == n, onClick = { update { it.copy(historyDays = n) } }, label = { Text(if (n == 1) "24 hours" else "$n days") })
            }
            Text("At most 500 copies (under 8 MiB of encrypted records). Oldest copies expire first. Notification text is never included in configuration backups.", style = MaterialTheme.typography.bodySmall)
            Text("Exclude apps from history", style = MaterialTheme.typography.titleSmall)
            Text("Excluding an app also deletes its saved copies. Protected apps and critical or ongoing notifications are excluded automatically.", style = MaterialTheme.typography.bodySmall)
            val apps = (snapshot.items.map { it.appId } + records.map { it.appId } + config.historyExcludedApps).distinct()
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (id in apps) FilterChip(selected = id in config.historyExcludedApps, onClick = {
                    update { it.copy(historyExcludedApps = if (id in it.historyExcludedApps) it.historyExcludedApps - id else it.historyExcludedApps + id) }
                }, label = { Text(appLabel(context, id.substringAfter(':'))) })
            }
            OutlinedButton(onClick = { deleting = true }) { Text("Delete all saved history") }
        }
        if (failed) Text("History capture paused: storage or encryption is unavailable. Existing files have been preserved. Delete saved history to reset storage; then enable capture again.", color = MaterialTheme.colorScheme.error)
        Text("Capture can miss events while access is unavailable, the phone is locked or the listener is overloaded. Earlier dismissed notifications cannot be recovered.", style = MaterialTheme.typography.bodySmall)
        if (gap) Text("Some events were skipped during this session.")
        Text("View and search copies in the Saved tab. Swipe a copy there to delete it.", style = MaterialTheme.typography.bodySmall)
    }
    if (consent) AlertDialog(onDismissRequest = { consent = false }, title = { Text("Save notification copies?") }, text = {
        Text("Notification titles and messages may contain private information. Comfer will encrypt future eligible copies locally for the selected retention period. No copies are uploaded or backed up. Secret notifications and events received while the device is locked are skipped. You can stop capture or delete copies at any time.")
    }, confirmButton = { TextButton(enabled = !failed, onClick = { consent = false; update { it.copy(historyEnabled = true, historySince = System.currentTimeMillis()) } }) { Text("Enable history") } }, dismissButton = { TextButton(onClick = { consent = false }) { Text("Cancel") } })
    if (deleting) AlertDialog(onDismissRequest = { deleting = false }, title = { Text("Delete saved copies?") }, text = { Text("This only removes Comfer’s local copies. Live Android notifications are unchanged. Capture remains ${if (config.historyEnabled) "enabled" else "off"}.") }, confirmButton = {
        TextButton(onClick = {
            deleting = false
            scope.launch { if (!NotificationHistory.clear()) message = "Could not delete saved history." }
        }) { Text("Delete") }
    }, dismissButton = { TextButton(onClick = { deleting = false }) { Text("Cancel") } })
}
