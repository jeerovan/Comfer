package com.jeerovan.comfer.tasks

import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import com.jeerovan.comfer.BackupRestoreManager
import com.jeerovan.comfer.StartupCoordinator
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import java.io.File
import java.time.LocalDate

/** Explicit export/restore stages permit real process/data removal and second-device transfer. */
class TaskTransferTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    @Test fun exportFixture() = runBlocking {
        Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("taskTransferStage") == "export")
        check(context.packageName.endsWith(".notificationtest")); StartupCoordinator.awaitReady()
        TaskStore.change(context) {
            val parent = TaskItem(id = "transfer-parent", listId = "tasks", title = "Portable parent", notes = "https://example.com/fixture", starred = true, day = LocalDate.now().plusDays(7).toEpochDay(), minute = 540)
            TaskSnapshot(tasks = listOf(parent, parent.copy(id = "transfer-child", title = "Portable child", parentId = parent.id, completedAt = 1234)))
        }
        BackupRestoreManager.createBackup(context, Uri.fromFile(File(context.getExternalFilesDir(null), "tasks-transfer.zip")), "en")
        Unit
    }
    @Test fun restoreIntoFreshData() = runBlocking {
        Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("taskTransferStage") == "restore")
        check(context.packageName.endsWith(".notificationtest")); StartupCoordinator.awaitReady()
        assertTrue(TaskStore.snapshot(context).tasks.isEmpty())
        BackupRestoreManager.restoreBackup(context, Uri.fromFile(File(context.getExternalFilesDir(null), "tasks-transfer.zip")))
        val state = TaskStore.snapshot(context)
        assertEquals(2, state.tasks.size)
        assertTrue(state.tasks.first { it.id == "transfer-parent" }.starred)
        assertEquals("transfer-parent", state.tasks.first { it.id == "transfer-child" }.parentId)
        assertEquals(1234L, state.tasks.first { it.id == "transfer-child" }.completedAt)
        assertNotNull(reminderAt(state.tasks.first { it.id == "transfer-parent" }, state.preferences))
    }
    @Test fun verifyAfterRestart() = runBlocking {
        Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("taskTransferStage") == "verify")
        check(context.packageName.endsWith(".notificationtest")); StartupCoordinator.awaitReady()
        val state = TaskStore.snapshot(context)
        assertEquals(2, state.tasks.size)
        assertTrue(state.tasks.first { it.id == "transfer-parent" }.starred)
        TaskReminders.reconcile(context)
        assertNotNull(reminderAt(state.tasks.first { it.id == "transfer-parent" }, state.preferences))
    }
}
