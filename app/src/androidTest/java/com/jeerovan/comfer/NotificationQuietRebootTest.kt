package com.jeerovan.comfer

import android.app.NotificationManager
import android.os.SystemClock
import android.provider.Settings
import androidx.test.platform.app.InstrumentationRegistry
import com.jeerovan.comfer.notifications.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import org.json.JSONObject
import org.junit.Test
import org.junit.Assert.*
import java.io.File

/** Explicit two-phase emulator test: prepare, adb reboot, then verify/restore. */
class NotificationQuietRebootTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val manager get() = context.getSystemService(NotificationManager::class.java)
    private val backup get() = File(context.noBackupFilesDir, "quiet-reboot-test.json")
    private fun shell(command: String) = instrumentation.uiAutomation.executeShellCommand(command).use {
        android.os.ParcelFileDescriptor.AutoCloseInputStream(it).bufferedReader().readText().trim()
    }
    private suspend fun await(condition: () -> Boolean) {
        val until = SystemClock.elapsedRealtime() + 20000
        while (!condition() && SystemClock.elapsedRealtime() < until) delay(100)
        assertTrue("Quiet=${NotificationQuietHours.state.value}, filter=${manager.currentInterruptionFilter}", condition())
    }
    @Test fun prepareFocusOnlyReboot() = runBlocking { prepare(false) }
    @Test fun prepareScheduleAndFocusReboot() = runBlocking { prepare(true) }
    private suspend fun prepare(schedule: Boolean) {
        assertTrue(android.os.Build.MODEL.contains("sdk"))
        assertFalse("Restore previous reboot fixture first", backup.exists())
        NotificationPreferences.initialize(context)
        val runtime = context.getSharedPreferences("notification_quiet_runtime", 0)
        assertTrue("Use isolated emulator without an existing runtime rule", runtime.all.isEmpty())
        backup.writeText(JSONObject().put("config", Json.encodeToString(NotificationPreferences.state.value))
            .put("access", manager.isNotificationPolicyAccessGranted)
            .put("filter", manager.currentInterruptionFilter)
            .put("boot", Settings.Global.getInt(context.contentResolver, "boot_count", -1))
            .put("schedule", schedule).toString())
        shell("cmd notification allow_dnd ${context.packageName}")
        await { manager.isNotificationPolicyAccessGranted }
        assertTrue(NotificationPreferences.update { NotificationConfiguration(setup = true,
            quietSchedule = QuietSchedule(enabled = schedule, startMinute = 0, endMinute = 0)) })
        assertTrue(NotificationQuietHours.focus(context, 15))
        await { manager.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL }
        assertTrue(NotificationQuietHours.state.value.focusUntil > System.currentTimeMillis())
    }
    @Test fun verifyAfterActualRebootAndRestore() = runBlocking {
        val saved = JSONObject(backup.readText())
        try {
            assertNotEquals(saved.getInt("boot"), Settings.Global.getInt(context.contentResolver, "boot_count", -1))
            NotificationPreferences.initialize(context)
            // Do not manually reconcile: Android's receiver/provider must recover the contribution.
            if (saved.getBoolean("schedule")) {
                await { manager.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL && NotificationQuietHours.state.value.health == QuietHealth.ACTIVE }
                assertTrue(NotificationPreferences.state.value.quietSchedule.enabled)
            } else {
                await { manager.currentInterruptionFilter == saved.getInt("filter") }
            }
            assertEquals("Manual focus cannot survive reboot", 0L, NotificationQuietHours.state.value.focusUntil)
        } finally { restore(saved) }
    }
    @Test fun restoreInterruptedRebootFixture() = runBlocking { if (backup.exists()) restore(JSONObject(backup.readText())) }
    private suspend fun restore(saved: JSONObject) {
        shell("cmd notification allow_dnd ${context.packageName}")
        await { manager.isNotificationPolicyAccessGranted }
        assertTrue(NotificationQuietHours.reset(context))
        NotificationPreferences.initialize(context)
        assertTrue(NotificationPreferences.update { Json.decodeFromString<NotificationConfiguration>(saved.getString("config")) })
        NotificationQuietHours.reconcile(context)
        shell("cmd notification ${if (saved.getBoolean("access")) "allow_dnd" else "disallow_dnd"} ${context.packageName}")
        assertTrue(backup.delete())
    }
}
