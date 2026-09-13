package com.jeerovan.comfer.tasks

import androidx.test.platform.app.InstrumentationRegistry
import com.jeerovan.comfer.StartupCoordinator
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import java.time.LocalDate

class TaskPermissionTest {
    @Test fun deniedNotificationsPreservePendingTaskAndInexactFallback() = runBlocking {
        Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("taskPermissionStage") == "denied")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName.endsWith(".notificationtest")); StartupCoordinator.awaitReady()
        assertFalse(TaskReminders.notificationsAllowed(context))
        if(android.os.Build.VERSION.SDK_INT >= 31) assertFalse(TaskReminders.exactAllowed(context))
        val previous = TaskStore.snapshot(context)
        try {
            val task = TaskItem(id = "denied", listId = "tasks", title = "Saved without grants", day = LocalDate.now().toEpochDay(), snoozedUntil = System.currentTimeMillis() - 1000)
            TaskStore.change(context) { TaskSnapshot(tasks = listOf(task)) }
            TaskReminders.reconcile(context)
            assertEquals("Saved without grants", TaskStore.snapshot(context).tasks.single().title)
            assertNull(TaskStore.snapshot(context).tasks.single().notifiedAt)
            assertNotNull(reminderAt(TaskStore.snapshot(context).tasks.single(), TaskPreferences()))
        } finally { TaskStore.exclusive(context) { TaskStore.write(context, previous) } }
    }
}
