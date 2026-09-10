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
fun NotificationQuietSettings(page: String = "quiet", navigate: (String) -> Unit = {}) {
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
    var hasDndAccess by remember { mutableStateOf(manager.isNotificationPolicyAccessGranted) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            systemFilter = manager.currentInterruptionFilter
            hasDndAccess = manager.isNotificationPolicyAccessGranted
            now = System.currentTimeMillis()
            delay(1000)
        }
    }
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
    val labels = DateFormatSymbols.getInstance().shortWeekdays
    val schedule = config.quietSchedule
    val dirty = start != schedule.startMinute || end != schedule.endMinute || days != schedule.weekdays ||
        deviceQuiet != schedule.deviceQuiet || hiddenApps != schedule.hiddenApps
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (page == "quiet") {
        Text(stringResource(R.string.notification_automation_title), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(if (config.paused) R.string.notification_automation_paused else R.string.notification_automation_running), style = MaterialTheme.typography.bodySmall)
        OutlinedButton(enabled = !busy, onClick = {
            operation { NotificationPreferences.update { it.copy(paused = !it.paused) } && NotificationQuietHours.reconcile(context) }
        }) { Text(stringResource(if (config.paused) R.string.notification_resume_automation else R.string.notification_pause_automation)) }
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
        if (!hasDndAccess) {
            Text(stringResource(R.string.notification_dnd_permission_help), style = MaterialTheme.typography.bodySmall)
            Button(onClick = { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)) }) { Text(stringResource(R.string.notification_quiet_access)) }
        }
        SettingsDestination("Focus timers", "Start a short DND break", { navigate("focus") })
        SettingsDestination("Schedules", "Manage recurring quiet hours", { navigate("schedules") })
        Text(stringResource(R.string.notification_quiet_disclosure), style = MaterialTheme.typography.bodySmall)
        }
        if (page == "focus") {
        Text(stringResource(R.string.notification_focus_title), style = MaterialTheme.typography.titleMedium)
        if (config.paused) Text(stringResource(R.string.notification_automation_paused))
        if (!hasDndAccess) Button(onClick = { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)) }) { Text(stringResource(R.string.notification_quiet_access)) }
        Text(stringResource(R.string.notification_focus_help), style = MaterialTheme.typography.bodySmall)
        Text(if (status.focusUntil > now) stringResource(R.string.notification_focus_until, DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(status.focusUntil))) else stringResource(R.string.notification_focus_none))
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            for (minutes in listOf(15, 30, 60)) {
               OutlinedButton(enabled = !busy && !config.paused && hasDndAccess, onClick = { operation { NotificationQuietHours.focus(context, minutes) } }) {
                    Text(stringResource(R.string.notification_focus_minutes, minutes))
                }
            }
            if (status.focusUntil > now) OutlinedButton(enabled = !busy, onClick = { operation { NotificationQuietHours.endFocus(context) } }) { Text(stringResource(R.string.notification_end_focus)) }
        }
        }
        if (page == "schedules") {
            Text(stringResource(if (schedule.enabled) R.string.notification_schedule_enabled else R.string.notification_schedule_disabled))
            SettingsDestination("Recurring schedule", "Days, times and actions", { navigate("schedule") })
        }
        if (page == "schedule") {
        if (config.paused) Text(stringResource(R.string.notification_automation_paused))
        if (!hasDndAccess && deviceQuiet) Button(onClick = { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)) }) { Text(stringResource(R.string.notification_quiet_access)) }
        Text(stringResource(R.string.notification_schedule_title), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(if (schedule.enabled) R.string.notification_schedule_enabled else R.string.notification_schedule_disabled))
        if (schedule.enabled) Text(stringResource(R.string.notification_schedule_summary, time(schedule.startMinute), time(schedule.endMinute), schedule.weekdays.sorted().joinToString { labels[it] }), style = MaterialTheme.typography.bodySmall)
        Text(stringResource(R.string.notification_schedule_help), style = MaterialTheme.typography.bodySmall)
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = {
                android.app.TimePickerDialog(context, { _, hour, minute -> start = hour * 60 + minute }, start / 60, start % 60, android.text.format.DateFormat.is24HourFormat(context)).apply {
                    show()
                    window?.setGravity(android.view.Gravity.BOTTOM)
                }
            }) { Text(stringResource(R.string.notification_quiet_start, time(start))) }
            OutlinedButton(onClick = {
                android.app.TimePickerDialog(context, { _, hour, minute -> end = hour * 60 + minute }, end / 60, end % 60, android.text.format.DateFormat.is24HourFormat(context)).apply {
                    show()
                    window?.setGravity(android.view.Gravity.BOTTOM)
                }
            }) { Text(stringResource(R.string.notification_quiet_end, time(end))) }
        }
        Text(stringResource(R.string.notification_schedule_days))
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            for (day in 1..7) {
                FilterChip(selected = day in days, onClick = { days = if (day in days) days - day else days + day }, label = { Text(labels[day]) })
            }
        }
        Text(stringResource(R.string.notification_schedule_actions), style = MaterialTheme.typography.titleSmall)
        NotificationSettingToggle(stringResource(R.string.notification_schedule_device_quiet), deviceQuiet) { deviceQuiet = it }
        Text(stringResource(R.string.notification_schedule_hide_apps))
        Text(stringResource(R.string.notification_hide_scope), style = MaterialTheme.typography.bodySmall)
        if (snapshot.items.isEmpty() && hiddenApps.isEmpty()) Text(stringResource(R.string.notification_schedule_empty_apps), style = MaterialTheme.typography.bodySmall)
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            for (appId in (snapshot.items.map { it.appId } + hiddenApps).distinct()) key(appId) {
                FilterChip(selected = appId in hiddenApps, onClick = { hiddenApps = if (appId in hiddenApps) hiddenApps - appId else hiddenApps + appId },
                    label = { Text(appLabel(context, appId.substringAfter(':'))) })
            }
        }
        val valid = days.isNotEmpty() && start != end && (deviceQuiet || hiddenApps.isNotEmpty())
        if (dirty) Text(stringResource(R.string.notification_schedule_unsaved), style = MaterialTheme.typography.bodySmall)
        if (!valid) Text(stringResource(R.string.notification_schedule_invalid), style = MaterialTheme.typography.bodySmall)
        Button(enabled = !busy && valid && (!deviceQuiet || hasDndAccess), onClick = {
            operation {
                NotificationPreferences.update { it.copy(quietSchedule = QuietSchedule(true, days, start, end, deviceQuiet, hiddenApps)) } &&
                    NotificationQuietHours.reconcile(context, explicitEnable = true)
            }
        }) { Text(stringResource(R.string.notification_apply_quiet_schedule)) }
       if (schedule.enabled) OutlinedButton(enabled = !busy, onClick = {
            operation {
                NotificationPreferences.update { it.copy(quietSchedule = it.quietSchedule.copy(enabled = false)) } &&
                    NotificationQuietHours.reconcile(context)
            }
        }) { Text(stringResource(R.string.notification_disable_quiet_schedule)) }
        Text(stringResource(R.string.notification_quiet_disclosure), style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** Two explicit choices retain selected-state semantics and wrap their text if needed. */
@Composable
internal fun NotificationViewChoices(chronological: Boolean, onSelect: (Boolean) -> Unit) {
    Column {
        Text(stringResource(R.string.notification_view_title), style = MaterialTheme.typography.titleMedium)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(modifier = Modifier.weight(1f), selected = !chronological,
                onClick = { onSelect(false) }, label = { Text(stringResource(R.string.notification_grouped)) })
            FilterChip(modifier = Modifier.weight(1f), selected = chronological,
                onClick = { onSelect(true) }, label = { Text(stringResource(R.string.notification_chronological)) })
        }
    }
}
