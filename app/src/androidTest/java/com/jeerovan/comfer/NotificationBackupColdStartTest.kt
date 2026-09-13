package com.jeerovan.comfer

import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import com.jeerovan.comfer.notifications.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assume.assumeTrue
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** Two explicit stages with adb pull / pm clear / adb push between them. Normal suites skip these. */
class NotificationBackupColdStartTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val archive get() = File(context.getExternalFilesDir(null), "notification-transfer.zip")
    private fun requireStage(stage: String) {
        assumeTrue(InstrumentationRegistry.getArguments().getString("notificationBackupStage") == stage)
        check(context.packageName.endsWith(".notificationtest"))
    }

    @Test fun exportFixture() = runBlocking {
        requireStage("export")
        withTimeout(20000) { StartupCoordinator.awaitReady() }
        PreferenceManager.replaceSnapshot(context, mapOf(PreferenceManager.AUTO_WALLPAPER to "false"))
        NotificationPreferences.initialize(context)
        check(NotificationPreferences.update {
            NotificationConfiguration(pinned = setOf("0:uninstalled.example"), chronological = true,
                historyEnabled = true, historySince = 5, historyDays = 30,
                rules = listOf(NotificationRule("portable", "Portable rule", terms = listOf("offer"), enabled = true, observeOnly = false)))
        })
        BackupRestoreManager.createBackup(context, Uri.fromFile(archive), "en")
        assertTrue(archive.length() > 0)
    }

    @Test fun restoreIntoFreshAppData() = runBlocking {
        requireStage("restore")
        withTimeout(20000) { StartupCoordinator.awaitReady() }
        NotificationPreferences.initialize(context)
        assertTrue("Must clear isolated app data between stages", NotificationPreferences.state.value.pinned.isEmpty())
        val before = System.currentTimeMillis()
        BackupRestoreManager.restoreBackup(context, Uri.fromFile(archive))
        val restored = NotificationPreferences.state.value
        assertEquals(setOf("0:uninstalled.example"), restored.pinned)
        assertTrue(restored.chronological)
        assertTrue(restored.paused)
        assertTrue(restored.historyEnabled)
        assertEquals(30, restored.historyDays)
        assertTrue(restored.historySince >= before)
        assertEquals("portable", restored.rules.single().id)
        assertTrue(restored.rules.single().enabled)
        assertFalse(restored.rules.single().observeOnly)
        assertEquals(restored, decodeNotificationConfiguration(context.getSharedPreferences("notification_configuration", 0).getString("config", null)!!))
        assertFalse(context.getSharedPreferences("notification_quiet_runtime", 0).contains("rule_id"))
    }
}
