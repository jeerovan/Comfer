package com.jeerovan.comfer.journals

import androidx.test.platform.app.InstrumentationRegistry
import com.jeerovan.comfer.*
import com.jeerovan.comfer.notifications.NotificationPreferences
import com.jeerovan.comfer.tasks.TaskStore
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Test
import org.junit.Assert.*
import java.io.File

/** Run after synthetic transfer, seed a durable pre-restore checkpoint, kill, then verify recovery. */
class JournalRestoreCheckpointTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    @Test fun seedInterruptedReplacement() = runBlocking {
        check(context.packageName.endsWith(".notificationtest")); StartupCoordinator.awaitReady()
        val expected = JournalArchiveMedia.localSnapshot(context)
        check(expected.entries.any { it.image != null }) { "Run the synthetic transfer first" }
        File(context.filesDir, "journal-checkpoint-expected.json").writeText(Json.encodeToString(expected))
        val original = BackupRestoreManager.readAndValidateArchive(context.packageName, File(context.filesDir, "journal-transfer.zip"), false)
        val record = RestoreJournal(PreferenceManager.snapshotForBackup(), original.payload.room,
            NotificationPreferences.withBackupAccess(context) { snapshot() }, TaskStore.snapshot(context), expected)
        val atomic = android.util.AtomicFile(File(context.filesDir, "backup_restore_journal.json"))
        val output = atomic.startWrite()
        output.write(Json.encodeToString(record).toByteArray()); atomic.finishWrite(output)
        expected.entries.mapNotNull { it.image }.forEach { JournalMedia(context).file(it).setLastModified(System.currentTimeMillis() - 2L * 24 * 60 * 60 * 1000) }
        JournalArchiveMedia.replaceLocal(context, JournalLocalSnapshot(listOf(JournalEntry(text = "Partial replacement")), emptyList()))
        assertEquals("Partial replacement", JournalDatabase.get(context).dao().exportEntries().single().text)
    }
    @Test fun verifyColdRestoreRecovery() = runBlocking {
        check(context.packageName.endsWith(".notificationtest")); StartupCoordinator.awaitReady()
        val file = File(context.filesDir, "journal-checkpoint-expected.json")
        check(file.isFile) { "Run checkpoint seed and force-stop first" }
        val expected = Json.decodeFromString<JournalLocalSnapshot>(file.readText())
        assertEquals(expected, JournalArchiveMedia.localSnapshot(context))
        JournalMedia(context).collect(JournalDatabase.get(context))
        expected.entries.mapNotNull { it.image }.forEach { assertTrue(JournalMedia(context).file(it).isFile) }
        assertFalse(File(context.filesDir, "backup_restore_journal.json").exists())
        file.delete(); Unit
    }
}
