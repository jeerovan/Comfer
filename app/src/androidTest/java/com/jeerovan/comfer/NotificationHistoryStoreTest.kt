package com.jeerovan.comfer

import androidx.test.platform.app.InstrumentationRegistry
import com.jeerovan.comfer.notifications.*
import org.junit.Test
import org.junit.Assert.*
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking

class NotificationHistoryStoreTest {
    @Test fun openedNotificationRetainsInboxCopyAndRulesDismissOnlyFutureMatches() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        NotificationPreferences.initialize(context)
        NotificationHistory.initialize(context)
        val previous = NotificationPreferences.state.value
        val title = "History integration ${UUID.randomUUID()}"
        val manager = context.getSystemService(android.app.NotificationManager::class.java)
        val openAction = "${context.packageName}.HISTORY_OPEN_TEST"
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(context: android.content.Context?, intent: android.content.Intent?) { manager.cancel(551701) }
        }
        androidx.core.content.ContextCompat.registerReceiver(context, receiver, android.content.IntentFilter(openAction), androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED)
        val openIntent = android.app.PendingIntent.getBroadcast(context, 551701, android.content.Intent(openAction).setPackage(context.packageName), android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE)
        fun shell(command: String) = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command).use {
            android.os.ParcelFileDescriptor.AutoCloseInputStream(it).bufferedReader().readText().trim()
        }
        val component = "${context.packageName}/com.jeerovan.comfer.MyNotificationListenerService"
        val originalAccess = shell("settings get secure enabled_notification_listeners").takeUnless { it == "null" }.orEmpty()
        val originallyGranted = MyNotificationListenerService.hasAccess(context)
        try {
            if (android.os.Build.VERSION.SDK_INT >= 27) shell("cmd notification allow_listener $component")
            else {
                val other = originalAccess.split(':').filter { it.isNotBlank() && it != component }.joinToString(":")
                if (other.isEmpty()) shell("settings delete secure enabled_notification_listeners") else shell("settings put secure enabled_notification_listeners $other")
                kotlinx.coroutines.delay(500)
                shell("settings put secure enabled_notification_listeners ${(originalAccess.split(':').filter { it.isNotBlank() } + component).distinct().joinToString(":")}")
            }
            NotificationPreferences.update { it.copy(historyEnabled = true, historySince = System.currentTimeMillis(), historyExcludedApps = emptySet(), protectedApps = emptySet(), rules = emptyList(), paused = false) }
            MyNotificationListenerService.refresh(context, force = true)
            repeat(100) {
                if (MyNotificationListenerService.snapshot.value.health != ListenerHealth.CONNECTED) kotlinx.coroutines.delay(100)
            }
            assertEquals(ListenerHealth.CONNECTED, MyNotificationListenerService.snapshot.value.health)
            val builder = if (android.os.Build.VERSION.SDK_INT >= 26) {
                manager.createNotificationChannel(android.app.NotificationChannel("history-test", "History fixture", android.app.NotificationManager.IMPORTANCE_LOW))
                android.app.Notification.Builder(context, "history-test")
            } else android.app.Notification.Builder(context)
            manager.notify(551701, builder.setContentIntent(openIntent).setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle(title).setContentText("Retained local copy").setVisibility(android.app.Notification.VISIBILITY_PUBLIC).build())
            repeat(100) { if (NotificationHistory.records.value.none { it.title == title }) kotlinx.coroutines.delay(100) }
            assertTrue(NotificationHistory.records.value.any { it.title == title })
            repeat(100) { if (MyNotificationListenerService.snapshot.value.items.none { it.title == title }) kotlinx.coroutines.delay(100) }
            val live = MyNotificationListenerService.snapshot.value.items.first { it.title == title }
            assertEquals("requested", MyNotificationListenerService.act(live.key, live.revision, "open", MyNotificationListenerService.snapshot.value.sessionId))
            repeat(100) { if (MyNotificationListenerService.snapshot.value.items.any { it.title == title }) kotlinx.coroutines.delay(100) }
            assertFalse(MyNotificationListenerService.snapshot.value.items.any { it.title == title })
            val saved = NotificationHistory.records.value.first { it.title == title }
            assertTrue(saved.keepInInbox)
            assertTrue(retainedInboxCopies(NotificationHistory.records.value, MyNotificationListenerService.snapshot.value.items, true).any { it.id == saved.id })
            NotificationHistory.dismissFromInbox(saved.id)
            assertTrue(NotificationHistory.records.value.any { it.id == saved.id && !it.keepInInbox })
            assertFalse(retainedInboxCopies(NotificationHistory.records.value, emptyList(), true).any { it.id == saved.id })

            manager.notify(551702, builder.setContentTitle("$title rule existing").build())
            repeat(100) { if (MyNotificationListenerService.snapshot.value.items.none { it.title == "$title rule existing" }) kotlinx.coroutines.delay(100) }
            val rule = NotificationRule("integration-rule", "Fixture rule", field = RuleField.TITLE, terms = listOf("$title rule"), action = RuleAction.DISMISS, enabled = true, observeOnly = false)
            NotificationPreferences.update { it.copy(rules = listOf(rule)) }
            MyNotificationListenerService.refresh(context, force = true)
            kotlinx.coroutines.delay(600)
            assertTrue(manager.activeNotifications.any { it.id == 551702 })
            manager.notify(551703, builder.setContentTitle("$title rule future").build())
            repeat(100) { if (NotificationRuleActivity.state.value.none { it.ruleId == rule.id && it.outcome == "Dismissal requested" }) kotlinx.coroutines.delay(100) }
            assertTrue(NotificationRuleActivity.state.value.any { it.ruleId == rule.id && it.outcome == "Dismissal requested" })
            assertFalse(manager.activeNotifications.any { it.id == 551703 })
            assertTrue(manager.activeNotifications.any { it.id == 551702 })
        } finally {
            manager.cancel(551701); manager.cancel(551702); manager.cancel(551703)
            context.unregisterReceiver(receiver); openIntent.cancel()
            NotificationHistory.refresh()
            NotificationHistory.records.value.filter { it.title.startsWith(title) }.forEach { NotificationHistory.delete(it.id) }
            NotificationPreferences.update { previous }
            if (android.os.Build.VERSION.SDK_INT >= 27) shell("cmd notification ${if (originallyGranted) "allow_listener" else "disallow_listener"} $component")
            else if (originalAccess.isEmpty()) shell("settings delete secure enabled_notification_listeners") else shell("settings put secure enabled_notification_listeners $originalAccess")
        }
    }

    @Test fun encryptedCopiesSurviveReopenAndDeletionAndRejectLostKeys() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.noBackupFilesDir, "history-test-${UUID.randomUUID()}")
        val cipher = NotificationHistoryCipher("history.test.${UUID.randomUUID()}")
        try {
            val store = NotificationHistoryStore(directory, cipher)
            assertTrue(store.read().isEmpty())
            val copy = SavedNotification("test", "fixture.chat", 0, "PrivateSentinel", "Local message", 1, 2)
            store.write(copy)
            assertFalse(directory.listFiles()!!.single().readBytes().decodeToString().contains("PrivateSentinel"))
            assertEquals(listOf(copy), NotificationHistoryStore(directory, cipher).read())
            store.delete(copy.id)
            assertTrue(store.read().isEmpty())
            store.write(copy)
            cipher.deleteKey()
            assertTrue(runCatching { store.read() }.isFailure)
            assertTrue(directory.listFiles()!!.isNotEmpty())
            store.clear()
            assertTrue(store.read().isEmpty())
        } finally { directory.deleteRecursively(); cipher.deleteKey() }
    }

    @Test fun retentionExclusionsAndCountBudgetApplyBeforeShowingCopies() {
        val now = 100 * 86_400_000L
        val records = (1..510).map { SavedNotification("$it", "fixture", 0, "Title", "Message", now, now - it) }
        val config = NotificationConfiguration(historyDays = 1)
        assertEquals(500, retainedHistory(records, config, now).size)
        assertTrue(retainedHistory(records, config.copy(historyExcludedApps = setOf("0:fixture")), now).isEmpty())
        assertTrue(retainedHistory(records, config.copy(protectedApps = setOf("0:fixture")), now).isEmpty())
        assertTrue(retainedHistory(records, config, now + 2 * 86_400_000L).isEmpty())
    }

    @Test fun captureRequiresConsentAndSkipsExcludedProtectedAndLockedEvents() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        NotificationPreferences.initialize(context)
        val previous = NotificationPreferences.state.value
        NotificationHistory.initialize(context)
        val id = "history-test-${UUID.randomUUID()}"
        val item = NotificationItem(id, id, 0, "Capture sentinel", "Body", System.currentTimeMillis(), null, false, true, false)
        try {
            NotificationPreferences.update { it.copy(historyEnabled = false) }
            NotificationHistory.offer(item, true)
            NotificationHistory.refresh()
            assertFalse(NotificationHistory.records.value.any { it.app == id })
            NotificationPreferences.update { it.copy(historyEnabled = true, historySince = System.currentTimeMillis(), historyExcludedApps = setOf(item.appId)) }
            NotificationHistory.offer(item, true)
            NotificationHistory.refresh()
            assertFalse(NotificationHistory.records.value.any { it.app == id })
            NotificationPreferences.update { it.copy(historyExcludedApps = emptySet()) }
            NotificationHistory.offer(item.copy(protected = true), true)
            NotificationHistory.offer(item.copy(previewAvailable = false), true)
            NotificationHistory.offer(item.copy(contentComplete = false), true)
            NotificationHistory.offer(item.copy(title = "", text = ""), true)
            NotificationHistory.offer(item.copy(text = "Sensitive notification content hidden"), true)
            NotificationHistory.offer(item, false)
            NotificationHistory.refresh()
            assertFalse(NotificationHistory.records.value.any { it.app == id })
            NotificationHistory.offer(item, true)
            NotificationHistory.refresh()
            assertEquals(1, NotificationHistory.records.value.count { it.app == id })
            NotificationHistory.records.value.filter { it.app == id }.forEach { NotificationHistory.delete(it.id) }
        } finally { NotificationPreferences.update { previous } }
    }
}
