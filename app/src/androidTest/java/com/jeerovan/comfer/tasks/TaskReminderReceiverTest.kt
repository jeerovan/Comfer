package com.jeerovan.comfer.tasks

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.jeerovan.comfer.StartupCoordinator
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.io.IOException
import android.util.AtomicFile

class TaskReminderReceiverTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun inbox() = TaskReminderInbox(File(context.noBackupFilesDir, "task-reminder-inbox.json"))

    @Test fun stalledStartupFinishesBroadcastBeforeAndroidDeadline() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName.endsWith(".notificationtest"))
        withTimeout(20_000) { StartupCoordinator.awaitReady() }
        val finished = CompletableDeferred<Unit>()
        StartupCoordinator.markInitializing()
        try {
            context.sendOrderedBroadcast(
                Intent(context, TaskReminderReceiver::class.java)
                    .setAction("comfer.tasks.ALARM")
                    .addFlags(Intent.FLAG_RECEIVER_FOREGROUND),
                null,
                object : BroadcastReceiver() {
                    override fun onReceive(context: Context, intent: Intent) { finished.complete(Unit) }
                }, null, 0, null, null,
            )
            assertTrue("Receiver retained its broadcast while startup was stalled",
                withTimeoutOrNull(9_000) { finished.await(); true } == true)
        } finally {
            StartupCoordinator.markReady()
            withTimeout(15_000) { finished.await() }
            TaskReminders.reconcile(context)
        }
    }

    @Test fun failedStartupRetainsCompleteAndRecoveryPersistsIt() = runBlocking {
        withFixture { item ->
            StartupCoordinator.markFailed(IOException("Injected startup failure"))
            val finished = CompletableDeferred<Unit>()
            val job = TaskReminderDelivery.receive(context,
                Intent(context, TaskReminderReceiver::class.java).setAction("comfer.tasks.COMPLETE")
                    .putExtra("task", item.id).putExtra("version", item.version),
                { finished.complete(Unit) })
            withTimeout(3_000) { finished.await(); job.join() }
            assertNull(TaskStore.snapshot(context).tasks.single().completedAt)
            // A new store instance must read the action without relying on process memory.
            val saved = inbox().read().single()
            assertEquals(item.id, saved.task)
            assertEquals("COMPLETE", saved.action)
            val retried = CompletableDeferred<Unit>()
            val retryJob = TaskReminderDelivery.receive(context,
                Intent(context, TaskReminderReceiver::class.java).setAction(TaskReminderDelivery.RETRY),
                { retried.complete(Unit) })
            withTimeout(9_000) { retried.await(); retryJob.join() }
            assertTrue(StartupCoordinator.isReady)
            assertNotNull(TaskDatabase.get(context).dao().tasks().single().completedAt)
            assertTrue(inbox().read().isEmpty())
        }
    }

    @Test fun snoozeReplaysAfterCommitWithoutExtendingTimeOrApplyingTwice() = runBlocking {
        withFixture { item ->
            val receivedAt = System.currentTimeMillis()
            val command = PendingTaskReminder(task = item.id, version = item.version,
                action = "SNOOZE", minutes = 30, receivedAt = receivedAt)
            TaskReminderDelivery.accept(context, command)
            try {
                TaskReminderDelivery.reconcile(context) {
                    StartupCoordinator.markFailed(IOException("Injected failure after Room commit"))
                    throw IOException("Injected failure before acknowledgement")
                }
                fail("Injected failure was ignored")
            } catch (_: IOException) { }
            assertEquals(command, inbox().read().single())
            val committed = TaskDatabase.get(context).dao().tasks().single()
            assertEquals(item.version + 1, committed.version)
            assertEquals(receivedAt + 30 * 60_000L, committed.snoozedUntil)
            StartupCoordinator.markReady()
            TaskReminders.reconcile(context)
            assertEquals(committed, TaskDatabase.get(context).dao().tasks().single())
            assertTrue(inbox().read().isEmpty())
            // An old Complete intent must not undo a later edit/snooze.
            TaskReminderDelivery.accept(context, command.copy(receipt = "stale-complete", action = "COMPLETE"))
            TaskReminders.reconcile(context)
            assertEquals(committed, TaskDatabase.get(context).dao().tasks().single())
        }
    }

    @Test fun concurrentReceiptsSurviveReopenAndAreAcknowledgedTogether() = runBlocking {
        withFixture { item ->
            StartupCoordinator.markInitializing()
            coroutineScope {
                (1..12).map { i -> async(Dispatchers.IO) {
                    TaskReminderDelivery.accept(context, PendingTaskReminder(receipt = "receipt-$i",
                        task = item.id, version = item.version, action = "COMPLETE"))
                } }.awaitAll()
            }
            assertEquals(12, inbox().read().map { it.receipt }.toSet().size)
            StartupCoordinator.markReady()
            TaskReminders.reconcile(context)
            val completed = TaskDatabase.get(context).dao().tasks().single()
            assertNotNull(completed.completedAt)
            assertEquals(item.version + 1, completed.version)
            assertTrue(inbox().read().isEmpty())
        }
    }

    @Test fun interruptedInboxWriteRetainsPreviouslyAcceptedAction() {
        check(context.packageName.endsWith(".notificationtest"))
        val file = File(context.cacheDir, "reminder-inbox-atomic-test.json")
        val atomic = AtomicFile(file)
        try {
            val command = PendingTaskReminder(task = "preserved", version = 1, action = "COMPLETE")
            TaskReminderInbox(file).write(listOf(command))
            assertTrue(file.readText().contains("\"receivedAt\":"))
            atomic.startWrite().use { it.write("incomplete".toByteArray()) }
            assertEquals(listOf(command), TaskReminderInbox(file).read())
        } finally { atomic.delete() }
    }

    private suspend fun withFixture(test: suspend (TaskItem) -> Unit) {
        check(context.packageName.endsWith(".notificationtest"))
        withTimeout(20_000) { StartupCoordinator.awaitReady() }
        TaskReminders.reconcile(context)
        val previous = TaskStore.snapshot(context)
        val item = TaskItem(id = "receiver-recovery", listId = "tasks", title = "Retained action")
        TaskStore.exclusive(context) { TaskStore.write(context, TaskSnapshot(tasks = listOf(item))) }
        try { test(item) }
        finally {
            StartupCoordinator.markReady()
            TaskReminders.reconcile(context)
            TaskStore.exclusive(context) { TaskStore.write(context, previous) }
            TaskReminders.reconcile(context)
        }
    }
}
