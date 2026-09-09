@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.jeerovan.comfer.notifications

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.ui.unit.dp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.jeerovan.comfer.R
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import java.text.DateFormat
import java.text.DateFormatSymbols
import java.util.Calendar
import java.util.Date

@Composable
fun NotificationQuietSettings() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val config by NotificationPreferences.state.collectAsState()
    val status by NotificationQuietHours.state.collectAsState()
    var start by rememberSaveable(config.quietSchedule.startMinute) { mutableIntStateOf(config.quietSchedule.startMinute) }
    var end by rememberSaveable(config.quietSchedule.endMinute) { mutableIntStateOf(config.quietSchedule.endMinute) }
    var days by rememberSaveable(config.quietSchedule.weekdays) { mutableStateOf(config.quietSchedule.weekdays) }
    var deviceQuiet by rememberSaveable(config.quietSchedule.deviceQuiet) { mutableStateOf(config.quietSchedule.deviceQuiet) }
    var hiddenApps by rememberSaveable(config.quietSchedule.hiddenApps) { mutableStateOf(config.quietSchedule.hiddenApps) }
    val snapshot by com.jeerovan.comfer.MyNotificationListenerService.snapshot.collectAsState()
    val manager = remember { context.getSystemService(android.app.NotificationManager::class.java) }
    var systemFilter by remember { mutableIntStateOf(manager.currentInterruptionFilter) }
    LaunchedEffect(Unit) { while (true) { systemFilter = manager.currentInterruptionFilter; delay(1000) } }
    var failed by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    fun operation(action: suspend () -> Boolean) {
        if (busy) return
        busy = true
        scope.launch { try { failed = !action() } finally { busy = false } }
    }
    fun time(minute: Int): String = DateFormat.getTimeInstance(DateFormat.SHORT).format(Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, minute / 60); set(Calendar.MINUTE, minute % 60)
    }.time)
    Column {
        Text(stringResource(R.string.notification_quiet_rule), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.notification_quiet_disclosure), style = MaterialTheme.typography.bodySmall)
        Text(stringResource(when (status.health) {
            QuietHealth.OFF -> R.string.notification_quiet_off
            QuietHealth.ACTIVE -> R.string.notification_quiet_active
            QuietHealth.SCHEDULED -> R.string.notification_quiet_scheduled
            QuietHealth.LOCAL_ONLY -> R.string.notification_quiet_local
            QuietHealth.UNAVAILABLE -> R.string.notification_quiet_unavailable
            QuietHealth.ACCESS_NEEDED -> R.string.notification_quiet_access
            QuietHealth.SYSTEM_DISABLED -> R.string.notification_quiet_disabled
            QuietHealth.CLEANUP_NEEDED -> R.string.notification_quiet_cleanup
            QuietHealth.FAILED -> R.string.notification_quiet_failed
        }))
        Text(stringResource(if (systemFilter == android.app.NotificationManager.INTERRUPTION_FILTER_ALL) R.string.notification_system_dnd_off else R.string.notification_system_dnd_on))
        Text(java.util.TimeZone.getDefault().displayName)
        if (failed) Text(stringResource(R.string.notification_quiet_failed))
        status.nextBoundary?.let { Text(stringResource(R.string.notification_quiet_next, DateFormat.getDateTimeInstance().format(Date(it)))) }
       TextButton(onClick = { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)) }) { Text(stringResource(R.string.notification_quiet_access)) }
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            for (minutes in listOf(15, 30, 60)) {
               TextButton(enabled = !busy && !config.paused, onClick = { operation { NotificationQuietHours.focus(context, minutes) } }) {
                    Text(stringResource(R.string.notification_focus_minutes, minutes))
                }
            }
            TextButton(enabled = !busy, onClick = { operation { NotificationQuietHours.endFocus(context) } }) { Text(stringResource(R.string.notification_end_focus)) }
        }
        Text(stringResource(R.string.notification_quiet_start, time(start)))
        Slider(start.toFloat(), onValueChange = { start = it.toInt() }, valueRange = 0f..1439f)
        Text(stringResource(R.string.notification_quiet_end, time(end)))
        Slider(end.toFloat(), onValueChange = { end = it.toInt() }, valueRange = 0f..1439f)
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            val labels = DateFormatSymbols.getInstance().shortWeekdays
            for (day in 1..7) {
                FilterChip(selected = day in days, onClick = { days = if (day in days) days - day else days + day }, label = { Text(labels[day]) })
            }
        }
        NotificationSettingToggle(stringResource(R.string.notification_schedule_device_quiet), deviceQuiet) { deviceQuiet = it }
        Text(stringResource(R.string.notification_schedule_hide_apps))
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            for (appId in (snapshot.items.map { it.appId } + hiddenApps).distinct()) key(appId) {
                FilterChip(selected = appId in hiddenApps, onClick = { hiddenApps = if (appId in hiddenApps) hiddenApps - appId else hiddenApps + appId },
                    label = { Text(appLabel(context, appId.substringAfter(':'))) })
            }
        }
       TextButton(enabled = !busy && days.isNotEmpty(), onClick = {
            operation {
                NotificationPreferences.update { it.copy(quietSchedule = QuietSchedule(true, days, start, end, deviceQuiet, hiddenApps)) } &&
                    NotificationQuietHours.reconcile(context, explicitEnable = true)
            }
        }) { Text(stringResource(R.string.notification_apply_quiet_schedule)) }
       TextButton(enabled = !busy, onClick = {
            operation {
                NotificationPreferences.update { it.copy(quietSchedule = it.quietSchedule.copy(enabled = false)) } &&
                    NotificationQuietHours.reconcile(context)
            }
        }) { Text(stringResource(R.string.notification_disable_quiet_schedule)) }
    }
}
