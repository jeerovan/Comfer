package com.jeerovan.comfer.tasks

import android.content.Intent
import androidx.test.platform.app.InstrumentationRegistry
import com.jeerovan.comfer.StartupCoordinator
import kotlinx.coroutines.*
import kotlinx.serialization.encodeToString
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.io.IOException

/** Explicit two-stage harness: run prepare, kill the test app process, then run verify. */
class TaskReminderRestartTest {
    @Test fun pendingActionsSurviveProcessRestart() = runBlocking {
        val phase = InstrumentationRegistry.getArguments().getString("phase")
        org.junit.Assume.assumeTrue("Run this two-stage harness explicitly", phase in listOf("prepare", "verify"))
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName.endsWith(".notificationtest"))
        val saved = File(context.noBackupFilesDir, "reminder-restart-fixture.json")
        val receipts = File(context.noBackupFilesDir, "reminder-restart-receipts.json")
        val inbox = TaskReminderInbox(File(context.noBackupFilesDir, "task-reminder-inbox.json"))
        withTimeout(20_000) { StartupCoordinator.awaitReady() }
        when (phase) {
            "prepare" -> {
                check(!saved.exists()) { "Verify/restore the previous restart fixture first" }
                TaskReminders.reconcile(context)
                saved.writeText(taskJson.encodeToString(TaskStore.snapshot(context)))
                val items = listOf("COMPLETE", "SNOOZE").map { action ->
                    TaskItem(id = "restart-$action", listId = "tasks", title = "Restart $action")
                }
                TaskStore.exclusive(context) { TaskStore.write(context, TaskSnapshot(tasks = items)) }
                StartupCoordinator.markFailed(IOException("Injected failure before process restart"))
                for (item in items) {
                    val done = CompletableDeferred<Unit>()
                    val job = TaskReminderDelivery.receive(context,
                        Intent(context, TaskReminderReceiver::class.java)
                            .setAction("comfer.tasks.${item.id.substringAfter('-')}")
                            .putExtra("task", item.id).putExtra("version", item.version)
                            .putExtra("minutes", 30), { done.complete(Unit) })
                    withTimeout(9_000) { done.await(); job.join() }
                }
                assertEquals(2, inbox.read().size)
                TaskReminderInbox(receipts).write(inbox.read())
                assertTrue(TaskDatabase.get(context).dao().tasks().all { it.completedAt == null && it.snoozedUntil == null })
                // Intentionally leave startup failed and durable actions pending for the next process.
            }
            "verify" -> {
                check(saved.exists() && receipts.exists()) { "Run prepare first" }
                try {
                    withTimeout(20_000) {
                        while (TaskReminderDelivery.pending(context).isNotEmpty()) delay(50)
                    }
                    val items = TaskDatabase.get(context).dao().tasks().associateBy { it.id }
                    assertNotNull(items.getValue("restart-COMPLETE").completedAt)
                    val snooze = TaskReminderInbox(receipts).read().single { it.action == "SNOOZE" }
                    assertEquals(snooze.receivedAt + 30 * 60_000L, items.getValue("restart-SNOOZE").snoozedUntil)
                    assertEquals(2L, items.getValue("restart-SNOOZE").version)
                } finally {
                    TaskStore.exclusive(context) { TaskStore.write(context, taskJson.decodeFromString(saved.readText())) }
                    TaskReminders.reconcile(context)
                    saved.delete(); receipts.delete()
                }
            }
            else -> error("Select this harness explicitly with -e phase prepare|verify")
        }
    }
}
