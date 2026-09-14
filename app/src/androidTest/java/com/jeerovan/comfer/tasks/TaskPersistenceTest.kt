package com.jeerovan.comfer.tasks

import android.net.Uri
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.platform.app.InstrumentationRegistry
import com.jeerovan.comfer.*
import com.jeerovan.comfer.data.ComferRepository
import kotlinx.coroutines.*
import kotlinx.serialization.encodeToString
import org.junit.*
import org.junit.Assert.*
import java.io.File
import java.time.LocalDate
import java.util.zip.ZipFile
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import java.security.MessageDigest

class TaskPersistenceTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var previous: TaskSnapshot
    @Before fun setup() = runBlocking {
        check(context.packageName.endsWith(".notificationtest"))
        withTimeout(20000) { StartupCoordinator.awaitReady() }
        previous = TaskStore.snapshot(context)
        TaskStore.exclusive(context) { TaskStore.write(context, TaskSnapshot()) }
    }
    @After fun cleanup() = runBlocking { TaskStore.exclusive(context) { TaskStore.write(context, previous) }; Unit }
    @Test fun newDatabaseAndReopenPreserveLauncherAndTaskData() = runBlocking {
        val launcher = ComferRepository.snapshot(context)
        val item = TaskItem(id = "persist", listId = "tasks", title = "Survives reopen", notes = "https://example.com", starred = true)
        TaskStore.change(context) { it.saveTask(item) }
        val reopened = Room.databaseBuilder(context, TaskDatabase::class.java, File(context.noBackupFilesDir, "tasks.db").absolutePath).build()
        try { assertEquals("Survives reopen", reopened.dao().tasks().single().title); assertTrue(reopened.dao().tasks().single().starred) } finally { reopened.close() }
        assertEquals(launcher, ComferRepository.snapshot(context))
        assertTrue(File(context.noBackupFilesDir, "tasks.db").isFile)
    }
    @Test fun concurrentAddsAndRepeatedCompletionDoNotLoseRows() = runBlocking {
        coroutineScope { (1..25).map { i -> async { TaskStore.change(context) { it.saveTask(TaskItem(id = "t$i", listId = "tasks", title = "Task $i")) } } }.awaitAll() }
        repeat(3) { TaskStore.change(context) { it.completeTask("t1", true) } }
        assertEquals(25, TaskStore.snapshot(context).tasks.size)
        assertEquals(1, TaskStore.snapshot(context).tasks.count { it.completedAt != null })
    }
    @Test fun invalidChangesAndAbortedRoomTransactionLeavePriorState() = runBlocking {
        TaskStore.change(context) { it.saveTask(TaskItem(id = "safe", listId = "tasks", title = "Keep")) }
        val before = TaskStore.snapshot(context)
        try { TaskStore.change(context) { it.copy(lists = emptyList()) }; fail("Must reject") } catch(_: IllegalArgumentException) { }
        val db = TaskDatabase.get(context)
        try { db.withTransaction { db.dao().clearTasks(); error("Injected failure after delete") } } catch(_: IllegalStateException) { }
        assertEquals(before, TaskStore.snapshot(context))
        assertEquals(before.tasks, db.dao().tasks())
    }
    @Test fun undoRestoresChildStatesAndKeepsUnrelatedEdits() = runBlocking {
        val parent = TaskItem(id = "p", listId = "tasks", title = "Parent")
        TaskStore.change(context) { it.copy(tasks = listOf(parent, parent.copy(id = "child", title = "Child", parentId = "p"))) }
        val token = TaskStore.change(context) { it.completeTask("p", true, true) }
        TaskStore.change(context) { it.saveTask(TaskItem(id = "other", listId = "tasks", title = "Later")) }
        TaskStore.undo(context, token)
        assertEquals(3, TaskStore.snapshot(context).tasks.size)
        assertTrue(TaskStore.snapshot(context).tasks.all { it.completedAt == null })
    }
    @Test fun undoNewSelectedListRestoresValidSelection() = runBlocking {
        val token = TaskStore.change(context) { it.copy(lists = it.lists + TaskList("work", "Work"), preferences = it.preferences.copy(selectedList = "work")) }
        TaskStore.undo(context, token)
        val restored = TaskStore.snapshot(context)
        restored.validate()
        assertEquals("tasks", restored.preferences.selectedList)
        assertEquals(1, restored.lists.size)
    }
    @Test fun actualArchiveRestoresListsSeriesAndFutureIntentWithoutOldDeliveryState() = runBlocking {
        val item = TaskItem(id = "future", listId = "tasks", title = "Future task", day = LocalDate.now().plusDays(5).toEpochDay(), minute = 630, starred = true)
        TaskStore.change(context) { it.saveTask(item, TaskRepeat(RepeatUnit.MONTHLY, count = 3)) }
        val expected = TaskStore.snapshot(context)
        val archive = File(context.cacheDir, "tasks-roundtrip.zip")
        try {
            val summary = BackupRestoreManager.createBackup(context, Uri.fromFile(archive), "en")
            assertEquals(expected.tasks.size, summary.taskCount)
            ZipFile(archive).use { zip -> assertTrue(zip.getInputStream(zip.getEntry("payload.json")).reader().readText().contains("Future task")) }
            TaskStore.change(context) { TaskSnapshot() }
            repeat(2) { BackupRestoreManager.restoreBackup(context, Uri.fromFile(archive)) }
            val restored = TaskStore.snapshot(context)
            assertEquals(expected.lists, restored.lists)
            assertEquals(expected.series, restored.series)
            assertEquals(expected.tasks.map { it.title }, restored.tasks.map { it.title })
            assertTrue(restored.tasks.all { reminderAt(it, restored.preferences) != null })
            assertTrue(restored.tasks.all { it.version > expected.tasks.first().version })
        } finally { archive.delete() }
    }
    @Test fun journalRecoveryRestoresTasksAlongsideLauncher() = runBlocking {
        val room = ComferRepository.snapshot(context)
        val previousSettings = PreferenceManager.snapshotForBackup()
        val before = TaskSnapshot(tasks = listOf(TaskItem(id = "recover", listId = "tasks", title = "Recovered")))
        // Use a real archive payload to obtain the launcher's portable Room representation.
        val archive = File(context.cacheDir, "tasks-journal.zip")
        try {
            BackupRestoreManager.createBackup(context, Uri.fromFile(archive), "en")
            val payload = ZipFile(archive).use { zip -> taskJson.decodeFromString<BackupPayload>(zip.getInputStream(zip.getEntry("payload.json")).reader().readText()) }
            File(context.filesDir, "backup_restore_journal.json").writeText(taskJson.encodeToString(RestoreJournal(previousSettings, payload.room, tasks = before)))
            BackupRestoreManager.recoverInterruptedRestore(context)
            assertEquals(before, TaskStore.snapshot(context))
            assertEquals(room, ComferRepository.snapshot(context))
        } finally { archive.delete() }
    }
    @Test fun missingSectionPreservesTasksAndExplicitEmptyReplacesThem() = runBlocking {
        val archive = File(context.cacheDir, "tasks-compat.zip")
        try {
            BackupRestoreManager.createBackup(context, Uri.fromFile(archive), "en")
            val (manifest, payload) = ZipFile(archive).use { zip ->
                taskJson.decodeFromString<BackupManifest>(zip.getInputStream(zip.getEntry("manifest.json")).reader().readText()) to
                    taskJson.decodeFromString<BackupPayload>(zip.getInputStream(zip.getEntry("payload.json")).reader().readText())
            }
            fun rewrite(value: BackupPayload) {
                val bytes = taskJson.encodeToString(value).toByteArray()
                val hash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }
                ZipOutputStream(archive.outputStream()).use { zip ->
                    zip.putNextEntry(ZipEntry("manifest.json")); zip.write(taskJson.encodeToString(manifest.copy(payloadSha256 = hash)).toByteArray()); zip.closeEntry()
                    zip.putNextEntry(ZipEntry("payload.json")); zip.write(bytes); zip.closeEntry()
                }
            }
            TaskStore.change(context) { it.saveTask(TaskItem(id = "keep", listId = "tasks", title = "Keep me")) }
            rewrite(payload.copy(tasks = null))
            assertNull(BackupRestoreManager.inspectBackup(context, Uri.fromFile(archive)).taskCount)
            BackupRestoreManager.restoreBackup(context, Uri.fromFile(archive))
            assertEquals("Keep me", TaskStore.snapshot(context).tasks.single().title)
            rewrite(payload.copy(tasks = TaskSnapshot()))
            assertEquals(0, BackupRestoreManager.inspectBackup(context, Uri.fromFile(archive)).taskCount)
            BackupRestoreManager.restoreBackup(context, Uri.fromFile(archive))
            assertTrue(TaskStore.snapshot(context).tasks.isEmpty())
            rewrite(payload.copy(tasks = TaskSnapshot(lists = emptyList())))
            try { BackupRestoreManager.restoreBackup(context, Uri.fromFile(archive)); fail("Invalid archive accepted") } catch(_: InvalidBackupException) { }
            assertTrue(TaskStore.snapshot(context).tasks.isEmpty())
        } finally { archive.delete() }
    }
    @Test fun deliverySnoozeAndStaleActionAreIdempotentOnDevice() = runBlocking {
        val item = TaskItem(id = "reminder", listId = "tasks", title = "Synthetic reminder", snoozedUntil = System.currentTimeMillis() - 1, day = LocalDate.now().minusDays(1).toEpochDay())
        TaskStore.change(context) { it.saveTask(item) }
        TaskReminders.reconcile(context)
        val manager = context.getSystemService(android.app.NotificationManager::class.java)
        withTimeout(3000) { while(manager.activeNotifications.none { it.tag == "tasks-summary" }) delay(50) }
        // The persisted date changed at first save; set due snooze in a separate mutation.
        TaskStore.change(context) { it.copy(tasks = it.tasks.map { task -> task.copy(snoozedUntil = System.currentTimeMillis() - 1, notifiedAt = null, version = task.version + 1) }) }
        TaskReminders.reconcile(context)
        val delivered = TaskStore.snapshot(context).tasks.single()
        assertNotNull(delivered.notifiedAt)
        withTimeout(3000) { while(manager.activeNotifications.none { it.tag == "task:reminder:${delivered.version}" }) delay(50) }
        TaskReminders.act(context, delivered.id, delivered.version, "SNOOZE", 30)
        val snoozed = TaskStore.snapshot(context).tasks.single()
        assertTrue(snoozed.snoozedUntil!! > System.currentTimeMillis())
        assertFalse(manager.activeNotifications.any { it.tag?.startsWith("task:reminder:") == true })
        TaskReminders.act(context, delivered.id, delivered.version, "COMPLETE")
        assertNull(TaskStore.snapshot(context).tasks.single().completedAt)
        TaskReminders.act(context, snoozed.id, snoozed.version, "COMPLETE")
        assertNotNull(TaskStore.snapshot(context).tasks.single().completedAt)
    }
    @Test fun versionOneMigrationKeepsExistingListsAndTasks() = runBlocking {
        val file = File(context.cacheDir, "tasks-migration.db")
        file.delete()
        val schema = org.json.JSONObject(InstrumentationRegistry.getInstrumentation().context.assets.open("com.jeerovan.comfer.tasks.TaskDatabase/1.json").reader().readText()).getJSONObject("database")
        android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            val entities = schema.getJSONArray("entities")
            for(i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i)
                db.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", entity.getString("tableName")))
            }
            db.execSQL("INSERT INTO task_lists VALUES ('tasks','Keep this list',0)")
            db.execSQL("INSERT INTO task_preferences VALUES (1,'tasks',540,1,1,'today',0,'manual',12)")
            db.version = 1
        }
        val migrated = Room.databaseBuilder(context, TaskDatabase::class.java, file.absolutePath).addMigrations(TaskDatabase.MIGRATION_1_2).build()
        try {
            assertEquals("Keep this list", migrated.dao().lists().single().name)
            assertEquals(12L, migrated.dao().preferences()!!.revision)
            assertFalse(migrated.dao().preferences()!!.guidanceDismissed)
        } finally { migrated.close(); file.delete() }
    }
    @Test fun largeSnapshotPersistsWithoutLosingOrderOrNotes() = runBlocking {
        val rows = (0 until 2500).map { i -> TaskItem(id = "bulk:$i", listId = "tasks", title = "Task $i", notes = "Offline note $i", position = i) }
        TaskStore.change(context) { it.copy(tasks = rows) }
        val persisted = TaskDatabase.get(context).dao().tasks()
        assertEquals(2500, persisted.size)
        assertEquals("Offline note 2499", persisted.last().notes)
        assertEquals((0 until 2500).toList(), persisted.map { it.position })
    }
    @Test fun taskWriteFailureRollsBackAllStores() = runBlocking {
        val archive = File(context.cacheDir, "task-failure.zip")
        val db = TaskDatabase.get(context)
        try {
            TaskStore.change(context) { it.saveTask(TaskItem(id = "reject", listId = "tasks", title = "Reject restore")) }
            BackupRestoreManager.createBackup(context, Uri.fromFile(archive), "en")
            TaskStore.change(context) { TaskSnapshot(tasks = listOf(TaskItem(id = "keep", listId = "tasks", title = "Keep"))) }
            val before = TaskStore.snapshot(context)
            val settings = PreferenceManager.snapshotForBackup()
            val room = ComferRepository.snapshot(context)
            val notification = com.jeerovan.comfer.notifications.NotificationPreferences.withBackupAccess(context) { snapshot() }
            db.openHelper.writableDatabase.execSQL("CREATE TEMP TRIGGER reject_task_restore BEFORE INSERT ON tasks WHEN NEW.title='Reject restore' BEGIN SELECT RAISE(ABORT,'Injected task write failure'); END")
            try { BackupRestoreManager.restoreBackup(context, Uri.fromFile(archive)); fail("Failed write accepted") } catch(_: android.database.sqlite.SQLiteException) { }
            assertEquals(before, TaskStore.snapshot(context))
            assertEquals(settings, PreferenceManager.snapshotForBackup())
            assertEquals(room, ComferRepository.snapshot(context))
            assertEquals(notification, com.jeerovan.comfer.notifications.NotificationPreferences.withBackupAccess(context) { snapshot() })
            assertFalse(File(context.filesDir, "backup_restore_journal.json").exists())
        } finally { db.openHelper.writableDatabase.execSQL("DROP TRIGGER IF EXISTS reject_task_restore"); archive.delete() }
    }
    @Test fun futureAlarmActuallyDeliversWithoutForegroundActivity() = runBlocking {
        org.junit.Assume.assumeTrue(TaskReminders.exactAllowed(context) && TaskReminders.notificationsAllowed(context))
        val task = TaskItem(id = "alarm-probe", listId = "tasks", title = "Synthetic alarm probe", day = LocalDate.now().toEpochDay(), snoozedUntil = System.currentTimeMillis() + 5000)
        TaskStore.change(context) { it.copy(tasks = listOf(task)) }
        TaskReminders.reconcile(context)
        withTimeout(45000) { while(TaskStore.snapshot(context).tasks.single().notifiedAt == null) delay(250) }
        assertTrue(context.getSystemService(android.app.NotificationManager::class.java).activeNotifications.any { it.tag?.startsWith("task:alarm-probe:") == true })
    }
    @Test fun scheduledDateTimeNotificationCompleteButtonPersistsCompletion() = runBlocking {
        assertTrue("Exact alarm access required for this delivery test", TaskReminders.exactAllowed(context))
        assertTrue("Notification posting must be enabled for this test", TaskReminders.notificationsAllowed(context))
        val zone = java.time.ZoneId.systemDefault()
        var due = java.time.ZonedDateTime.now(zone).plusMinutes(1).withSecond(0).withNano(0)
        if(due.toInstant().toEpochMilli() < System.currentTimeMillis() + 3000) due = due.plusMinutes(1)
        val item = TaskItem(id = "date-time-button", listId = "tasks", title = "Scheduled date/time test", day = due.toLocalDate().toEpochDay(), minute = due.hour * 60 + due.minute)
        val manager = context.getSystemService(android.app.NotificationManager::class.java)
        TaskStore.change(context) { it.saveTask(item) }
        TaskReminders.reconcile(context)
        assertNull(TaskStore.snapshot(context).tasks.single().snoozedUntil)
        assertNull(TaskStore.snapshot(context).tasks.single().notifiedAt)
        assertFalse(manager.activeNotifications.any { it.tag?.startsWith("task:${item.id}:") == true })
        // Wait for AlarmManager/receiver; do not trigger reconciliation at delivery time.
        withTimeout(90000) { while(manager.activeNotifications.none { it.tag?.startsWith("task:${item.id}:") == true }) delay(200) }
        val notification = manager.activeNotifications.single { it.tag?.startsWith("task:${item.id}:") == true }.notification
        assertEquals(item.title, notification.extras.getString(android.app.Notification.EXTRA_TITLE))
        assertTrue(System.currentTimeMillis() >= due.toInstant().toEpochMilli())
        val complete = notification.actions.single { it.title.toString() == context.getString(R.string.tasks_complete) }
        complete.actionIntent.send()
        withTimeout(10000) { while(TaskStore.snapshot(context).tasks.single().completedAt == null || manager.activeNotifications.any { it.tag?.startsWith("task:${item.id}:") == true }) delay(100) }
        val completed = TaskStore.snapshot(context).tasks.single()
        assertNotNull(completed.completedAt)
        assertEquals(completed, TaskDatabase.get(context).dao().tasks().single())
        assertNull(reminderAt(completed, TaskStore.snapshot(context).preferences))
        complete.actionIntent.send()
        delay(500)
        assertEquals(completed, TaskStore.snapshot(context).tasks.single())
    }

}
