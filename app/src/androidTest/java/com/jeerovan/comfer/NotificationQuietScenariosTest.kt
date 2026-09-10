package com.jeerovan.comfer

import android.app.NotificationManager
import android.app.AutomaticZenRule
import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import androidx.test.platform.app.InstrumentationRegistry
import com.jeerovan.comfer.notifications.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.delay
import org.junit.*
import org.junit.Assert.*

/** Emulator-only scenarios. Preserve existing rules, runtime, configuration and policy grants. */
class NotificationQuietScenariosTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val manager get() = context.getSystemService(NotificationManager::class.java)
    private val runtime get() = context.getSharedPreferences("notification_quiet_runtime", Context.MODE_PRIVATE)
    private lateinit var originalConfig: NotificationConfiguration
    private lateinit var originalRuntime: Map<String, *>
    private var originalRule: AutomaticZenRule? = null
    private var originalId: String? = null
    private var originalPolicyAccess = false
    private var originalGrant = ""
    private var originalListeners = ""
    private var baseline = NotificationManager.INTERRUPTION_FILTER_ALL
    private fun shell(command: String) = instrumentation.uiAutomation.executeShellCommand(command).use {
        android.os.ParcelFileDescriptor.AutoCloseInputStream(it).bufferedReader().readText().trim()
    }
    private suspend fun await(timeout: Long = 15000, condition: () -> Boolean) {
        val until = SystemClock.elapsedRealtime() + timeout
        while (!condition() && SystemClock.elapsedRealtime() < until) delay(100)
        assertTrue("Timed out: ${NotificationQuietHours.state.value}; Android filter=${manager.currentInterruptionFilter}", condition())
    }
    private suspend fun grant(enabled: Boolean) {
        if (android.os.Build.VERSION.SDK_INT >= 27) {
            if (!enabled) shell("cmd notification disallow_listener ${context.packageName}/com.jeerovan.comfer.MyNotificationListenerService")
            shell("cmd notification ${if (enabled) "allow_dnd" else "disallow_dnd"} ${context.packageName}")
            await { manager.isNotificationPolicyAccessGranted == enabled }
            return
        }
        if (!enabled) {
            // Android 7 notification-listener access also grants policy access.
            val remaining = originalListeners.split(':').filter { it.isNotBlank() && !it.startsWith("${context.packageName}/") }
            if (remaining.isEmpty()) shell("settings delete secure enabled_notification_listeners")
            else shell("settings put secure enabled_notification_listeners ${remaining.joinToString(":")}")
        }
        val values = originalGrant.split(':').filter { it.isNotBlank() && it != context.packageName }.toMutableList()
        if (enabled) values += context.packageName
        if (values.isEmpty()) shell("settings delete secure enabled_notification_policy_access_packages")
        else shell("settings put secure enabled_notification_policy_access_packages ${values.joinToString(":")}")
        await { manager.isNotificationPolicyAccessGranted == enabled }
    }
    @Before fun setup() = runBlocking {
        assertTrue("Run these tests on an emulator only", android.os.Build.FINGERPRINT.contains("generic") || android.os.Build.MODEL.contains("sdk"))
        NotificationPreferences.initialize(context)
        originalPolicyAccess = manager.isNotificationPolicyAccessGranted
        originalConfig = NotificationPreferences.state.value
        originalRuntime = runtime.all.toMap()
        originalGrant = shell("settings get secure enabled_notification_policy_access_packages").takeUnless { it == "null" }.orEmpty()
        originalListeners = shell("settings get secure enabled_notification_listeners").takeUnless { it == "null" }.orEmpty()
        grant(true)
        originalId = runtime.getString("rule_id", null)
        originalRule = originalId?.let { manager.getAutomaticZenRule(it) }
        originalId?.let { id -> if (originalRule != null) assertTrue(manager.removeAutomaticZenRule(id)) }
        assertTrue(NotificationPreferences.update { NotificationConfiguration(setup = true) })
        assertTrue(runtime.edit().clear().commit())
        delay(500)
        baseline = manager.currentInterruptionFilter
        Unit
    }
    @After fun restore() = runBlocking {
        if (!::originalConfig.isInitialized) return@runBlocking
        grant(true)
        assertTrue(NotificationQuietHours.reset(context))
        val editor = runtime.edit().clear()
        originalRuntime.forEach { (key, value) -> when (value) {
            is String -> editor.putString(key, value)
            is Long -> editor.putLong(key, value)
            is Int -> editor.putInt(key, value)
            is Boolean -> editor.putBoolean(key, value)
        } }
        assertTrue(editor.commit())
        assertTrue(NotificationPreferences.update { originalConfig })
        originalRule?.let {
            val restoredId = manager.addAutomaticZenRule(it)
            assertNotNull(restoredId)
            assertTrue(runtime.edit().putString("rule_id", restoredId).commit())
        }
        NotificationQuietHours.reconcile(context, explicitEnable = originalId != null && originalRule == null)
        if (android.os.Build.VERSION.SDK_INT >= 27) {
            val listener = "${context.packageName}/com.jeerovan.comfer.MyNotificationListenerService"
            shell("cmd notification ${if (originalListeners.split(':').contains(listener)) "allow_listener" else "disallow_listener"} $listener")
            shell("cmd notification ${if (originalPolicyAccess) "allow_dnd" else "disallow_dnd"} ${context.packageName}")
        } else {
            if (originalListeners.isEmpty()) shell("settings delete secure enabled_notification_listeners")
            else shell("settings put secure enabled_notification_listeners $originalListeners")
            if (originalGrant.isEmpty()) shell("settings delete secure enabled_notification_policy_access_packages")
            else shell("settings put secure enabled_notification_policy_access_packages $originalGrant")
        }
        Unit
    }
    private fun fullDay(device: Boolean = true) = QuietSchedule(enabled = true, startMinute = 0, endMinute = 0, deviceQuiet = device)
    @Test fun invalidDurationsDoNotStartFocus() = runBlocking {
        assertFalse(NotificationQuietHours.focus(context, 0))
        assertFalse(NotificationQuietHours.focus(context, 1441))
        assertEquals(0L, runtime.getLong("focus_elapsed", 0))
    }
    @Test fun missingDndAccessRejectsFocus() = runBlocking {
        grant(false)
        assertFalse(NotificationQuietHours.focus(context, 15))
        assertEquals(QuietHealth.ACCESS_NEEDED, NotificationQuietHours.state.value.health)
        assertEquals(0L, runtime.getLong("focus_elapsed", 0))
    }
    @Test fun focusCanBeReplacedAndEnded() = runBlocking {
        assertTrue(NotificationQuietHours.focus(context, 15))
        await { manager.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL }
        val first = runtime.getLong("focus_elapsed", 0)
        assertTrue(NotificationQuietHours.focus(context, 30))
        assertTrue(runtime.getLong("focus_elapsed", 0) > first + 14 * 60000)
        assertTrue(NotificationQuietHours.endFocus(context))
        assertEquals(QuietHealth.OFF, NotificationQuietHours.state.value.health)
        await { manager.currentInterruptionFilter == baseline }
    }
    @Test fun realOneMinuteTimerExpiresViaAndroidAlarm() = runBlocking {
        val started = SystemClock.elapsedRealtime()
        assertTrue(NotificationQuietHours.focus(context, 1))
        await { manager.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL }
        // No manual reconciliation: exercise AlarmManager -> receiver -> condition provider.
        await(100000) { NotificationQuietHours.state.value.health == QuietHealth.OFF && manager.currentInterruptionFilter == baseline }
        java.io.File(context.noBackupFilesDir, "quiet-normal-timing.txt").writeText("${SystemClock.elapsedRealtime() - started}")
    }
    @Test fun pauseResumeAndScheduleOverlapPreserveQuietUntilAllEnd() = runBlocking {
        assertTrue(NotificationPreferences.update { it.copy(quietSchedule = fullDay()) })
        assertTrue(NotificationQuietHours.reconcile(context, explicitEnable = true))
        await { manager.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL }
        assertTrue(NotificationQuietHours.focus(context, 15))
        assertTrue(NotificationQuietHours.endFocus(context))
        assertEquals(QuietHealth.ACTIVE, NotificationQuietHours.state.value.health)
        assertTrue(NotificationPreferences.update { it.copy(paused = true) })
        assertTrue(NotificationQuietHours.reconcile(context))
        await { manager.currentInterruptionFilter == baseline }
        assertTrue(NotificationPreferences.update { it.copy(paused = false) })
        assertTrue(NotificationQuietHours.reconcile(context))
        await { manager.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL }
        assertTrue(NotificationPreferences.update { it.copy(quietSchedule = QuietSchedule()) })
        assertTrue(NotificationQuietHours.reconcile(context))
        await { manager.currentInterruptionFilter == baseline }
    }
    @Test fun externalDisableRequiresExplicitEnable() = runBlocking {
        assertTrue(NotificationQuietHours.focus(context, 15))
        val id = runtime.getString("rule_id", null)!!
        val rule = manager.getAutomaticZenRule(id)
        rule.isEnabled = false
        assertTrue(manager.updateAutomaticZenRule(id, rule))
        await { !manager.getAutomaticZenRule(id).isEnabled }
        assertFalse("Unexpected state after external disable: ${NotificationQuietHours.state.value}; runtime=${runtime.all}", NotificationQuietHours.reconcile(context))
        assertEquals(QuietHealth.SYSTEM_DISABLED, NotificationQuietHours.state.value.health)
        assertFalse(manager.getAutomaticZenRule(id).isEnabled)
        assertTrue(NotificationQuietHours.reconcile(context, explicitEnable = true))
        assertTrue(manager.getAutomaticZenRule(id).isEnabled)
    }
    @Test fun clockChangeCannotExtendTimerAndRebootMarkerEndsIt() = runBlocking {
        assertTrue(NotificationQuietHours.focus(context, 15))
        assertTrue(runtime.edit().putLong("focus_until", System.currentTimeMillis() + 365L * 86400000).commit())
        assertTrue(NotificationQuietHours.reconcile(context))
        assertTrue(NotificationQuietHours.state.value.focusUntil <= System.currentTimeMillis() + 15 * 60000)
        val boot = Settings.Global.getInt(context.contentResolver, "boot_count", -1)
        assertTrue(runtime.edit().putInt("focus_boot", boot - 1).commit())
        assertTrue(NotificationQuietHours.reconcile(context))
        assertEquals(QuietHealth.OFF, NotificationQuietHours.state.value.health)
        await { manager.currentInterruptionFilter == baseline }
    }
    @Test fun futureScheduleIsArmedWithoutActivatingDnd() = runBlocking {
        val calendar = java.util.Calendar.getInstance()
        val minute = calendar.get(java.util.Calendar.HOUR_OF_DAY) * 60 + calendar.get(java.util.Calendar.MINUTE)
        val schedule = QuietSchedule(enabled = true, startMinute = (minute + 10) % 1440, endMinute = (minute + 20) % 1440)
        assertTrue(NotificationPreferences.update { it.copy(quietSchedule = schedule) })
        assertTrue(NotificationQuietHours.reconcile(context, explicitEnable = true))
        assertEquals(QuietHealth.SCHEDULED, NotificationQuietHours.state.value.health)
        assertTrue(NotificationQuietHours.state.value.nextBoundary!! > System.currentTimeMillis())
        assertEquals(baseline, manager.currentInterruptionFilter)
    }
    @Test fun endingFocusPreservesAnotherActiveRule() = runBlocking {
        assertTrue(NotificationQuietHours.focus(context, 15))
        await { ComferQuietConditionProvider.instance != null }
        val uri = android.net.Uri.parse("condition://${context.packageName}/scenario-overlap")
        val extra = manager.addAutomaticZenRule(AutomaticZenRule("Independent scenario rule",
            android.content.ComponentName(context, ComferQuietConditionProvider::class.java), uri,
            NotificationManager.INTERRUPTION_FILTER_PRIORITY, true))
        try {
            val condition = android.service.notification.Condition(uri, "Independent scenario rule", android.service.notification.Condition.STATE_TRUE)
            if (android.os.Build.VERSION.SDK_INT >= 29) manager.setAutomaticZenRuleState(extra, condition)
            else ComferQuietConditionProvider.instance!!.notifyCondition(condition)
            delay(500)
            assertTrue(NotificationQuietHours.endFocus(context))
            delay(500)
            assertEquals(QuietHealth.OFF, NotificationQuietHours.state.value.health)
            assertNotNull(manager.getAutomaticZenRule(extra))
            assertTrue(manager.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL)
        } finally { manager.removeAutomaticZenRule(extra) }
    }

    @Test fun focusExpiresDuringForcedDoze() = runBlocking {
        val started = SystemClock.elapsedRealtime()
        org.junit.Assume.assumeTrue(android.os.Build.VERSION.SDK_INT >= 29)
        assertTrue(NotificationQuietHours.focus(context, 1))
        await { manager.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL }
        try {
            shell("dumpsys battery unplug")
            shell("input keyevent KEYCODE_SLEEP")
            shell("dumpsys deviceidle force-idle deep")
            assertEquals("IDLE", shell("dumpsys deviceidle get deep"))
            await(100000) { NotificationQuietHours.state.value.health == QuietHealth.OFF && manager.currentInterruptionFilter == baseline }
            java.io.File(context.noBackupFilesDir, "quiet-precise-doze-timing.txt").writeText("${SystemClock.elapsedRealtime() - started}")
        } finally {
            shell("dumpsys deviceidle unforce")
            shell("dumpsys battery reset")
            shell("input keyevent KEYCODE_WAKEUP")
            shell("wm dismiss-keyguard")
        }
    }

    @Test fun observeExtendedDozeTimerDelivery() = runBlocking {
        org.junit.Assume.assumeTrue(android.os.Build.VERSION.SDK_INT >= 29)
        val started = SystemClock.elapsedRealtime()
        assertTrue(NotificationQuietHours.focus(context, 1))
        try {
            shell("dumpsys battery unplug")
            shell("input keyevent KEYCODE_SLEEP")
            shell("dumpsys deviceidle force-idle deep")
            assertEquals("IDLE", shell("dumpsys deviceidle get deep"))
            await(180000) { NotificationQuietHours.state.value.health == QuietHealth.OFF && manager.currentInterruptionFilter == baseline }
            val elapsed = SystemClock.elapsedRealtime() - started
            java.io.File(context.noBackupFilesDir, "quiet-doze-observation.txt").writeText("One-minute timer ended after ${elapsed} ms in forced Doze.")
        } finally {
            shell("dumpsys deviceidle unforce")
            shell("dumpsys battery reset")
            shell("input keyevent KEYCODE_WAKEUP")
            shell("wm dismiss-keyguard")
        }
    }

}
