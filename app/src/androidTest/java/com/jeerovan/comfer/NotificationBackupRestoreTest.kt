package com.jeerovan.comfer

import android.net.Uri
import android.content.SharedPreferences
import androidx.test.platform.app.InstrumentationRegistry
import com.jeerovan.comfer.notifications.*
import com.jeerovan.comfer.data.ComferRepository
import com.jeerovan.comfer.data.RoomDataSnapshot
import com.jeerovan.comfer.data.SettingEntity
import kotlinx.coroutines.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import org.junit.*
import org.junit.Assert.*
import java.io.File
import java.lang.reflect.Proxy
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/** Real ZIP + preferences + Room round trips, in the isolated notificationTest application. */
class NotificationBackupRestoreTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }
    private lateinit var previousSettings: Map<String, String>
    private lateinit var previousNotifications: String
    private lateinit var previousRoom: RoomDataSnapshot
    private lateinit var archive: File

    @Before fun setup() = runBlocking {
        check(context.packageName.endsWith(".notificationtest")) { "Run against the isolated notificationTest build" }
        withTimeout(20000) { StartupCoordinator.awaitReady() }
        previousSettings = PreferenceManager.snapshotForBackup()
        previousRoom = ComferRepository.snapshot(context)
        previousNotifications = NotificationPreferences.withBackupAccess(context) { snapshot() }
        PreferenceManager.replaceSnapshot(context, previousSettings + (PreferenceManager.AUTO_WALLPAPER to "false"))
        archive = File(context.cacheDir, "notification-roundtrip.zip")
    }

    @After fun cleanup() = runBlocking {
        NotificationPreferences.withBackupAccess(context) { rollback(previousNotifications) }
        PreferenceManager.replaceSnapshot(context, previousSettings)
        ComferRepository.replaceSnapshot(context, previousRoom)
        NotificationQuietHours.reconcile(context)
        archive.delete()
        File(context.filesDir, "backup_restore_journal.json").delete()
        Unit
    }

    private fun configured() = NotificationConfiguration(
        setup = true, paused = false, chronological = true,
        pinned = setOf("0:missing.app"), protectedApps = setOf("0:protected.app"),
        historyEnabled = true, historySince = 123, historyDays = 30,
        historyExcludedApps = setOf("0:private.app"),
        quietSchedule = QuietSchedule(enabled = true, weekdays = setOf(2, 4), startMinute = 120, endMinute = 300),
        rules = listOf(NotificationRule("rule-a", "Offers", terms = listOf("offer"), enabled = true, observeOnly = false)),
    )

    private suspend fun exportConfigured() {
        check(NotificationPreferences.update { configured() })
        assertTrue(BackupRestoreManager.createBackup(context, Uri.fromFile(archive), "en").notificationSettingsIncluded)
    }

    @Test fun exportedSettingsRestorePersistAndRemainPaused() = runBlocking {
        exportConfigured()
        val payload = ZipFile(archive).use { json.parseToJsonElement(it.getInputStream(it.getEntry("payload.json")).reader().readText()).jsonObject }
        val portable = payload.getValue("notifications").jsonObject
        assertFalse(portable.containsKey("historySince"))
        assertFalse(portable.containsKey("generation"))
        assertTrue(BackupRestoreManager.inspectBackup(context, Uri.fromFile(archive)).notificationSettingsIncluded)
        check(NotificationPreferences.update { NotificationConfiguration() })
        val now = System.currentTimeMillis()
        repeat(2) {
            BackupRestoreManager.restoreBackup(context, Uri.fromFile(archive))
            val actual = NotificationPreferences.state.value
            assertTrue(actual.paused)
            assertTrue(actual.historySince >= now)
            assertEquals(configured().toSettingsBackup().copy(paused = true), actual.toSettingsBackup())
            val persisted = context.getSharedPreferences("notification_configuration", 0).getString("config", null)!!
            assertEquals(actual, decodeNotificationConfiguration(persisted))
            assertFalse(File(context.filesDir, "backup_restore_journal.json").exists())
        }
    }

    @Test fun legacyBackupLeavesNotificationSettingsUntouched() = runBlocking {
        exportConfigured()
        rewritePayload { it.remove("notifications") }
        assertFalse(BackupRestoreManager.inspectBackup(context, Uri.fromFile(archive)).notificationSettingsIncluded)
        check(NotificationPreferences.update { NotificationConfiguration(pinned = setOf("0:keep.app")) })
        val before = NotificationPreferences.state.value
        BackupRestoreManager.restoreBackup(context, Uri.fromFile(archive))
        assertEquals(before, NotificationPreferences.state.value)
    }

    @Test fun malformedSettingsDoNotMutateAnyStore() = runBlocking {
        exportConfigured()
        rewritePayload { it["notifications"] = JsonObject(mapOf("version" to JsonPrimitive(99))) }
        val before = NotificationPreferences.state.value
        val settings = PreferenceManager.snapshotForBackup()
        try {
            BackupRestoreManager.restoreBackup(context, Uri.fromFile(archive))
            fail("Invalid configuration accepted")
        } catch (_: InvalidBackupException) { }
        assertEquals(before, NotificationPreferences.state.value)
        assertEquals(settings, PreferenceManager.snapshotForBackup())
    }

    @Test fun failedNotificationCommitRollsBackLauncherAndNotificationSettings() = runBlocking {
        exportConfigured()
        check(NotificationPreferences.update { NotificationConfiguration(pinned = setOf("0:keep.app")) })
        PreferenceManager.replaceSnapshot(context, PreferenceManager.snapshotForBackup() + ("restore-test" to "keep"))
        val before = NotificationPreferences.state.value.toSettingsBackup()
        val settings = PreferenceManager.snapshotForBackup()
        // Fail exactly the restored config commit; subsequent rollback commits must succeed.
        val room = ComferRepository.snapshot(context).let {
            it.copy(settings = it.settings + SettingEntity("restore-test", "keep", "String"))
        }
        ComferRepository.replaceSnapshot(context, room)
        val field = NotificationPreferences.javaClass.getDeclaredField("preferences").apply { isAccessible = true }
        val original = field.get(NotificationPreferences) as SharedPreferences
        val failed = AtomicBoolean(false)
        val proxy = Proxy.newProxyInstance(SharedPreferences::class.java.classLoader, arrayOf(SharedPreferences::class.java)) { _, method, args ->
            val result = method.invoke(original, *(args ?: emptyArray()))
            if (method.name != "edit") result else Proxy.newProxyInstance(
                SharedPreferences.Editor::class.java.classLoader, arrayOf(SharedPreferences.Editor::class.java),
            ) { editorProxy, editorMethod, editorArgs ->
                if (editorMethod.name == "commit" && failed.compareAndSet(false, true)) false
                else {
                    val answer = editorMethod.invoke(result, *(editorArgs ?: emptyArray()))
                    if (answer is SharedPreferences.Editor) editorProxy else answer
                }
            }
        }
        field.set(NotificationPreferences, proxy)
        try {
            try { BackupRestoreManager.restoreBackup(context, Uri.fromFile(archive)); fail("Failed commit accepted") }
            catch (_: IllegalStateException) { }
        } finally { field.set(NotificationPreferences, original) }
        assertTrue(failed.get())
        assertEquals(before, NotificationPreferences.state.value.toSettingsBackup())
        assertEquals(settings, PreferenceManager.snapshotForBackup())
        assertEquals(room, ComferRepository.snapshot(context))
        assertFalse(File(context.filesDir, "backup_restore_journal.json").exists())
    }

    @Test fun interruptedRestoreJournalRecoversNotificationConfiguration() = runBlocking {
        exportConfigured()
        val payload = ZipFile(archive).use { json.decodeFromString<BackupPayload>(it.getInputStream(it.getEntry("payload.json")).reader().readText()) }
        val raw = NotificationPreferences.withBackupAccess(context) { snapshot() }
        val journal = RestoreJournal(payload.settings, payload.room, raw)
        File(context.filesDir, "backup_restore_journal.json").writeText(json.encodeToString(journal))
        check(NotificationPreferences.update { NotificationConfiguration() })
        PreferenceManager.replaceSnapshot(context, payload.settings + ("interrupted" to "yes"))
        BackupRestoreManager.recoverInterruptedRestore(context)
        assertEquals(configured().toSettingsBackup(), NotificationPreferences.state.value.toSettingsBackup())
        assertEquals(payload.settings, PreferenceManager.snapshotForBackup())
        assertEquals(raw, context.getSharedPreferences("notification_configuration", 0).getString("config", null))
        assertFalse(File(context.filesDir, "backup_restore_journal.json").exists())
    }

    @Test fun preferenceEditsWaitForBackupAccess() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val holder = launch(Dispatchers.Default) {
            NotificationPreferences.withBackupAccess(context) { entered.complete(Unit); release.await() }
        }
        entered.await()
        val edit = async(start = CoroutineStart.UNDISPATCHED) { NotificationPreferences.update { it.copy(chronological = true) } }
        assertFalse(edit.isCompleted)
        release.complete(Unit)
        holder.join()
        assertTrue(edit.await())
        assertTrue(NotificationPreferences.state.value.chronological)
    }

    @Test fun validBackupRepairsRecoveryModeWithoutExportingCorruptDefaults() = runBlocking {
        exportConfigured()
        NotificationPreferences.withBackupAccess(context) { rollback("{invalid") }
        assertTrue(NotificationPreferences.recoveryNeeded.value)
        try {
            NotificationPreferences.withBackupAccess(context) { export() }
            fail("Corrupt configuration must not be backed up as defaults")
        } catch (_: IllegalStateException) { }
        BackupRestoreManager.restoreBackup(context, Uri.fromFile(archive))
        assertFalse(NotificationPreferences.recoveryNeeded.value)
        assertEquals(configured().toSettingsBackup().copy(paused = true), NotificationPreferences.state.value.toSettingsBackup())
    }

    private fun rewritePayload(change: (MutableMap<String, JsonElement>) -> Unit) {
        val (manifest, payload) = ZipFile(archive).use {
            json.decodeFromString<BackupManifest>(it.getInputStream(it.getEntry("manifest.json")).reader().readText()) to
                json.parseToJsonElement(it.getInputStream(it.getEntry("payload.json")).reader().readText()).jsonObject.toMutableMap()
        }
        change(payload)
        val bytes = JsonObject(payload).toString().toByteArray()
        val hash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }
        ZipOutputStream(archive.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("manifest.json"))
            zip.write(json.encodeToString(manifest.copy(formatVersion = 1, payloadSha256 = hash)).toByteArray())
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("payload.json")); zip.write(bytes); zip.closeEntry()
        }
    }
}
