package com.jeerovan.comfer.tasks

import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.jeerovan.comfer.R
import com.jeerovan.comfer.StartupCoordinator
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import java.time.LocalDate
import java.time.ZoneId

/** A single alarm is recomputed from committed data; no device alarm handles are backed up. */
internal object TaskReminders {
    const val CHANNEL = "task_reminders"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val requests = Channel<Context>(Channel.CONFLATED)
    init { scope.launch { for (context in requests) runCatching { reconcile(context) }.onFailure { Log.e("TaskReminders", "Reconciliation pending until next retry", it) } } }
    fun request(context: Context) { requests.trySend(context.applicationContext) }
    fun notificationsAllowed(context: Context): Boolean {
        val manager = context.getSystemService(NotificationManager::class.java)
        return NotificationManagerCompat.from(context).areNotificationsEnabled() && (Build.VERSION.SDK_INT < 26 || manager.getNotificationChannel(CHANNEL)?.importance != NotificationManager.IMPORTANCE_NONE)
    }
    fun exactAllowed(context: Context) = Build.VERSION.SDK_INT < 31 || context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
    private fun alarm(context: Context) = PendingIntent.getBroadcast(context, 4211, Intent(context, TaskReminderReceiver::class.java).setAction("comfer.tasks.ALARM"), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    suspend fun reconcile(context: Context) {
        StartupCoordinator.awaitReady()
        TaskStore.exclusive(context) { original ->
            val manager = context.getSystemService(NotificationManager::class.java)
            if(Build.VERSION.SDK_INT >= 26) manager.createNotificationChannel(NotificationChannel(CHANNEL, context.getString(R.string.tasks_channel), NotificationManager.IMPORTANCE_DEFAULT))
            var state = original.materialize()
            if(state != original) TaskStore.write(context, state)
            val now = System.currentTimeMillis()
            val summaryExtras = manager.activeNotifications.firstOrNull { it.tag == "tasks-summary" }?.notification?.extras
            val groupIds = summaryExtras?.getStringArrayList("taskIds").orEmpty()
            val groupVersions = summaryExtras?.getLongArray("taskVersions") ?: longArrayOf()
            var groupedTasks = groupIds.mapIndexedNotNull { index, id -> state.tasks.find { it.id == id && it.version == groupVersions.getOrNull(index) && it.completedAt == null && it.snoozedUntil == null && it.reminder } }
            val survivingChildren = mutableListOf<TaskItem>()
            manager.activeNotifications.filter { it.tag?.startsWith("task:") == true }.forEach { active ->
                val task = state.tasks.find { "task:${it.id}:${it.version}" == active.tag }
                if(task == null || task.completedAt != null || task.version != active.notification.extras.getLong("taskVersion") || !task.reminder || task.snoozedUntil != null) manager.cancel(active.tag, active.id)
                else survivingChildren += task
            }
            if(summaryExtras != null || survivingChildren.size > 1) groupedTasks = (groupedTasks + survivingChildren).distinctBy { it.id }
            // Cancelling a group summary can cancel its children too. Remove an obsolete
            // summary before posting any replacement child, never afterwards.
            if(groupedTasks.isEmpty() && summaryExtras != null) manager.cancel("tasks-summary", 4212)
            fun postSummary() {
                if(groupedTasks.isEmpty()) return
                manager.notify("tasks-summary", 4212, base(context, state.preferences)
                    .setContentTitle(context.getString(R.string.tasks_due_count, groupedTasks.size))
                    .setContentText(context.getString(R.string.tasks_open))
                    .setExtras(Bundle().apply { putStringArrayList("taskIds", ArrayList(groupedTasks.map { it.id })); putLongArray("taskVersions", groupedTasks.map { it.version }.toLongArray()) })
                    .setContentIntent(openIntent(context, null)).setGroup("comfer.tasks").setGroupSummary(true).build())
            }
            val due = state.tasks.filter { reminderAt(it, state.preferences)?.let { at -> at <= now } == true }
            if(due.isNotEmpty() && notificationsAllowed(context)) {
                // Missed reminders collapse to one stable summary, rather than replaying a history.
                val missed = due.filter { (reminderAt(it, state.preferences) ?: now) < now - 60000 }
                val fresh = due - missed.toSet()
                if(groupedTasks.isNotEmpty() || missed.isNotEmpty() || fresh.size > 1) {
                    groupedTasks = (groupedTasks + due).distinctBy { it.id }
                }
                if(fresh.size <= 5) fresh.forEach { item ->
                    val extras = Bundle().apply { putLong("taskVersion", item.version) }
                    manager.notify("task:${item.id}:${item.version}", 0, base(context, state.preferences)
                        .setContentTitle(item.title).setContentText(listOfNotNull(state.lists.find { it.id == item.listId }?.name, item.day?.let(::dateLabel), item.minute?.let(::timeLabel)).joinToString(" · ")).setExtras(extras)
                        .setContentIntent(openIntent(context, item.id)).setGroup(if(groupedTasks.isNotEmpty()) "comfer.tasks" else null)
                        .addAction(0, context.getString(R.string.tasks_complete), if(state.tasks.any { it.parentId == item.id && it.completedAt == null }) openIntent(context, item.id, true) else actionIntent(context, item, "COMPLETE"))
                        .addAction(0, context.getString(R.string.tasks_snooze), actionIntent(context, item, "SNOOZE", 10))
                        .build())
                }
                // Hand every notification (including summary-only delivery) to Android
                // before persisting acknowledgement. Stable tags make a crash retry safe.
                postSummary()
                val ids = due.map { it.id }.toSet()
                state = state.copy(tasks = state.tasks.map { if(it.id in ids) it.copy(notifiedAt = now, snoozedUntil = null) else it })
                TaskStore.write(context, state)
            }
            if(due.isEmpty() && notificationsAllowed(context)) postSummary()
            val alarms = context.getSystemService(AlarmManager::class.java)
            alarms.cancel(alarm(context))
            val midnight = LocalDate.now().plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val next = if(notificationsAllowed(context)) state.tasks.mapNotNull { reminderAt(it, state.preferences) }.filter { it > now }.minOrNull() else null
            // Midnight also generates the next calendar occurrence and refreshes date-only state.
            val needsRollover = state.series.any { !it.stopped }
            if(needsRollover || next != null) {
                val at = if(needsRollover) minOf(next ?: midnight, midnight) else next!!
                try { if(exactAllowed(context)) alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, alarm(context)) else alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, alarm(context)) }
                catch (_: SecurityException) { alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, alarm(context)) }
            }
        }
    }
    private fun base(context: Context, prefs: TaskPreferences): NotificationCompat.Builder = NotificationCompat.Builder(context, CHANNEL)
        .setSmallIcon(R.drawable.outline_search_24).setAutoCancel(true).setOnlyAlertOnce(true)
        .setVisibility(if(prefs.privateNotifications) NotificationCompat.VISIBILITY_PRIVATE else NotificationCompat.VISIBILITY_PUBLIC)
        .setPublicVersion(NotificationCompat.Builder(context, CHANNEL).setSmallIcon(R.drawable.outline_search_24).setContentTitle(context.getString(R.string.tasks_reminder_private)).build())
    private fun openIntent(context: Context, id: String?, complete: Boolean = false) = PendingIntent.getActivity(context, 0,
        Intent(context, TasksActivity::class.java).putExtra("task", id).putExtra("complete", complete).setData(Uri.parse("comfer://tasks/${Uri.encode(id ?: "all")}/$complete")), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    private fun actionIntent(context: Context, item: TaskItem, action: String, minutes: Int = 0) = PendingIntent.getBroadcast(context, 0,
        Intent(context, TaskReminderReceiver::class.java).setAction("comfer.tasks.$action").setData(Uri.parse("comfer://tasks/${Uri.encode(item.id)}/${item.version}/$action"))
            .putExtra("task", item.id).putExtra("version", item.version).putExtra("minutes", minutes), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    suspend fun act(context: Context, id: String, version: Long, action: String, minutes: Int = 10) {
        TaskStore.change(context) { state -> applyReminderAction(state, id, version, action, minutes, System.currentTimeMillis()) }
        reconcile(context)
    }
}

internal fun reminderAt(item: TaskItem, prefs: TaskPreferences): Long? {
    if(item.completedAt != null || !item.reminder) return null
    item.snoozedUntil?.let { return it }
    if(item.minute == null && !prefs.dateOnlyReminders) return null
    return if(item.notifiedAt == null) item.dueInstant(prefs) else null
}
internal fun applyReminderAction(state: TaskSnapshot, id: String, version: Long, action: String, minutes: Int, now: Long): TaskSnapshot {
    val item = state.tasks.find { it.id == id && it.version == version && it.completedAt == null } ?: return state
    return when(action) {
        "COMPLETE" -> if(state.tasks.any { it.parentId == id && it.completedAt == null }) state else state.completeTask(id, true)
        "SNOOZE" -> { require(minutes in listOf(10, 30, 60, 1440)); state.copy(tasks = state.tasks.map { if(it.id == id) it.copy(snoozedUntil = now + minutes * 60000L, reminder = true, notifiedAt = null, version = it.version + 1) else it }) }
        else -> state
    }
}

class TaskReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                StartupCoordinator.awaitReady()
                val id = intent.getStringExtra("task")
                if(id != null && intent.action in listOf("comfer.tasks.COMPLETE", "comfer.tasks.SNOOZE")) TaskReminders.act(context, id, intent.getLongExtra("version", -1), intent.action!!.substringAfterLast('.'), intent.getIntExtra("minutes", 10))
                else TaskReminders.reconcile(context)
            } catch(e: Exception) { Log.e("TaskReminders", "Reminder work will retry on next reconciliation", e) }
            finally { pending.finish() }
        }
    }
}
