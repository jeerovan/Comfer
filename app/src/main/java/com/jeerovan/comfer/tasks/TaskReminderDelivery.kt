package com.jeerovan.comfer.tasks

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.util.AtomicFile
import com.jeerovan.comfer.ComferApp
import com.jeerovan.comfer.StartupCoordinator
import com.jeerovan.comfer.StartupState
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

@Serializable
internal data class PendingTaskReminder(
    val receipt: String = UUID.randomUUID().toString(),
    val task: String? = null,
    val version: Long = -1,
    val action: String? = null,
    val minutes: Int = 10,
    val receivedAt: Long = System.currentTimeMillis(),
) {
    init {
        require(action == null || action in listOf("COMPLETE", "SNOOZE"))
        require(action == null || !task.isNullOrBlank())
        require(action != "SNOOZE" || minutes in listOf(10, 30, 60, 1440))
    }
}

/** Independent of Room/startup migrations; contains IDs/actions, never task text. */
internal class TaskReminderInbox(private val file: File) {
    private val atomic = AtomicFile(file)
    // Dynamic constructor defaults (receipt/time) must never be regenerated on replay.
    private val json = Json { encodeDefaults = true }
    fun read(): List<PendingTaskReminder> {
        if (!file.exists() && !File(file.path + ".bak").exists()) return emptyList()
        val bytes = atomic.openRead().use { input ->
            val output = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(4096)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                check(output.size() + count <= MAX_BYTES) { "Reminder inbox is too large" }
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        }
        return json.decodeFromString(bytes.decodeToString())
    }
    fun write(commands: List<PendingTaskReminder>) {
        val bytes = json.encodeToString(commands).encodeToByteArray()
        check(bytes.size <= MAX_BYTES) { "Reminder inbox is too large" }
        val stream = atomic.startWrite()
        try { stream.write(bytes); atomic.finishWrite(stream) }
        catch (e: Exception) { atomic.failWrite(stream); throw e }
    }
    private companion object { const val MAX_BYTES = 1024 * 1024 }
}

internal object TaskReminderDelivery {
    const val RETRY = "comfer.tasks.RETRY"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val processing = Mutex()
    // Protect the inbox and retry-alarm cancellation together, including concurrent receivers.
    private val receipts = Any()
    private fun inbox(context: Context) = TaskReminderInbox(File(context.noBackupFilesDir, "task-reminder-inbox.json"))
    internal fun pending(context: Context): List<PendingTaskReminder> = synchronized(receipts) { inbox(context).read() }
    private fun retryIntent(context: Context) = PendingIntent.getBroadcast(context, 4213,
        Intent(context, TaskReminderReceiver::class.java).setAction(RETRY),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    private fun scheduleRetry(context: Context) {
        context.getSystemService(AlarmManager::class.java).setAndAllowWhileIdle(
            AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() + 60_000,
            retryIntent(context))
    }

    internal fun accept(context: Context, command: PendingTaskReminder) = synchronized(receipts) {
        val store = inbox(context)
        // Persist first. Startup/reboot recovery can replay even if scheduling then fails.
        store.write(store.read() + command)
        scheduleRetry(context)
    }

    fun receive(context: Context, intent: Intent, finish: () -> Unit): Job {
        val app = context.applicationContext
        return launchReminderBroadcast(scope, finish,
            onFailure = { android.util.Log.e("TaskReminders", "Saved reminder work awaits retry", it) }) {
            val action = intent.action?.substringAfterLast('.')
                ?.takeIf { intent.action in listOf("comfer.tasks.COMPLETE", "comfer.tasks.SNOOZE") }
            if (intent.action == RETRY) {
                synchronized(receipts) { scheduleRetry(app) }
                // This entry point is normally called on Main; keep initialization single-threaded.
                withContext(Dispatchers.Main) { (app as? ComferApp)?.initializeApplicationData() }
            } else {
                accept(app, PendingTaskReminder(task = intent.getStringExtra("task"),
                    version = intent.getLongExtra("version", -1), action = action,
                    minutes = intent.getIntExtra("minutes", 10)))
            }
            TaskReminders.reconcile(app)
        }
    }

    suspend fun reconcile(context: Context, updateNotifications: suspend () -> Unit) = withContext(Dispatchers.IO) {
        // Fail promptly on a failed attempt; activities retain their existing wait-for-retry contract.
        processing.withLock {
            val startup = StartupCoordinator.state.first { it !is StartupState.Initializing }
            if (startup is StartupState.Failed) throw IllegalStateException("Startup must recover before reminder work", startup.cause)
            val pending = synchronized(receipts) { inbox(context).read() }
            for (command in pending) {
                if (command.action != null) TaskStore.change(context) { state ->
                    applyReminderAction(state, command.task!!, command.version, command.action,
                        command.minutes, command.receivedAt)
                }
            }
            updateNotifications()
            // A crash after the Room commit but before acknowledgement safely replays by version.
            synchronized(receipts) {
                val store = inbox(context)
                val handled = pending.map { it.receipt }.toSet()
                val remaining = store.read().filterNot { it.receipt in handled }
                if (pending.isNotEmpty()) store.write(remaining)
                if (remaining.isEmpty()) context.getSystemService(AlarmManager::class.java).cancel(retryIntent(context))
            }
        }
    }
}
