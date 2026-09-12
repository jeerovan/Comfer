package com.jeerovan.comfer

import androidx.test.platform.app.InstrumentationRegistry
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.jeerovan.comfer.notifications.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*

/** Requires both notification-fixtures debug APKs. Exercises the OS listener across packages. */
class NotificationListenerIntegrationTest {
    @get:Rule val compose = createComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private var originalAccess = ""
    private var originallyGranted = false
    private lateinit var originalConfiguration: NotificationConfiguration
    private fun shell(command: String): String = instrumentation.uiAutomation.executeShellCommand(command).use {
        android.os.ParcelFileDescriptor.AutoCloseInputStream(it).bufferedReader().readText()
    }
    private fun await(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 15_000
        while (!condition() && System.currentTimeMillis() < deadline) Thread.sleep(100)
        assertTrue("Timed out waiting for notification listener", condition())
    }
    private fun post(app: String, id: Int, extras: String = "") {
        // Keep fixture delivery independent of a congested OEM background broadcast queue.
        shell("am broadcast --receiver-foreground -n com.jeerovan.fixtures.$app/com.jeerovan.fixtures.FixtureReceiver --ei id $id $extras")
    }
    private fun fixtures() = MyNotificationListenerService.snapshot.value.items.filter { it.app.startsWith("com.jeerovan.fixtures.") }
    @Before fun setup() {
        NotificationPreferences.initialize(context)
        originalConfiguration = NotificationPreferences.state.value
        runBlocking { NotificationPreferences.update { it.copy(historyEnabled = false, rules = emptyList(), protectedApps = emptySet()) } }
        originallyGranted = MyNotificationListenerService.hasAccess(context)
        originalAccess = shell("settings get secure enabled_notification_listeners").trim().takeUnless { it == "null" }.orEmpty()
        val component = "${context.packageName}/com.jeerovan.comfer.MyNotificationListenerService"
        val access = (originalAccess.split(':').filter { it.isNotBlank() } + component).distinct().joinToString(":")
        if (android.os.Build.VERSION.SDK_INT >= 27) shell("cmd notification allow_listener $component")
        else {
            // API 24 can retain dead system-server bindings after an APK update.
            // Toggle only this component, preserving other listeners and restoring the grant below.
            if (originallyGranted) {
                val remaining = originalAccess.split(':').filter { it.isNotBlank() && it != component }.joinToString(":")
                if (remaining.isEmpty()) shell("settings delete secure enabled_notification_listeners")
                else shell("settings put secure enabled_notification_listeners $remaining")
                Thread.sleep(500) // allow the asynchronous secure-setting observer to unbind
            }
            shell("settings put secure enabled_notification_listeners $access")
        }
        // An APK update/instrumentation restart can leave an already-granted listener unbound.
        // Rewriting the same API-24 secure setting does not notify Android of a change.
        MyNotificationListenerService.refresh(context)
        await { MyNotificationListenerService.snapshot.value.health == ListenerHealth.CONNECTED }
        post("mail", 1, "--es operation clear"); post("chat", 1, "--es operation clear")
        await { fixtures().isEmpty() }
    }
    @After fun cleanup() {
        post("mail", 1, "--es operation clear"); post("chat", 1, "--es operation clear")
        runBlocking { NotificationPreferences.update { originalConfiguration } }
        if (android.os.Build.VERSION.SDK_INT >= 27) {
            val operation = if (originallyGranted) "allow_listener" else "disallow_listener"
            shell("cmd notification $operation ${context.packageName}/com.jeerovan.comfer.MyNotificationListenerService")
        } else if (originalAccess.isEmpty()) shell("settings delete secure enabled_notification_listeners")
        else shell("settings put secure enabled_notification_listeners $originalAccess")
    }
    @Test fun groupsAcrossAppsUpdatesAndDismissal() = runBlocking {
        post("mail", 1, "--es group mail --es title First")
        post("mail", 2, "--es group mail --es title Second")
        post("mail", 3, "--es group mail --es kind summary")
        post("chat", 1, "--es title Chat")
        await { fixtures().size == 4 }
        assertEquals(3, notificationChildren(fixtures()).size)
        assertEquals(2, notificationChildren(fixtures()).map { it.appId }.distinct().size)
        val before = fixtures().first { it.title == "First" }
        post("mail", 1, "--es group mail --es title Updated")
        await { fixtures().any { it.title == "Updated" } }
        assertEquals("changed", MyNotificationListenerService.act(before.key, before.revision, "dismiss", MyNotificationListenerService.snapshot.value.sessionId))
        val current = fixtures().first { it.title == "Updated" }
        assertEquals("requested", MyNotificationListenerService.act(current.key, current.revision, "dismiss", MyNotificationListenerService.snapshot.value.sessionId))
        await { fixtures().none { it.key == current.key } }
        assertTrue(fixtures().any { it.title == "Second" })
        assertTrue(fixtures().any { it.title == "Chat" })
    }
    @Test fun ongoingCannotBeDismissedAndApi24SnoozeIsUnavailable() = runBlocking {
        post("mail", 4, "--es kind ongoing --es title Protected")
        await { fixtures().any { it.title == "Protected" } }
        val item = fixtures().first { it.title == "Protected" }
        assertEquals("protected", MyNotificationListenerService.act(item.key, item.revision, "dismiss", MyNotificationListenerService.snapshot.value.sessionId))
        if (android.os.Build.VERSION.SDK_INT < 26) assertEquals("unavailable", MyNotificationListenerService.act(item.key, item.revision, "snooze", MyNotificationListenerService.snapshot.value.sessionId))
    }
    @Test fun priorityIsLocalAndContentNeverEntersConfiguration() = runBlocking {
        post("chat", 8, "--es title PrivateSentinel")
        await { fixtures().any { it.title == "PrivateSentinel" } }
        val item = fixtures().first { it.title == "PrivateSentinel" }
        assertTrue(NotificationPreferences.update { it.copy(pinned = setOf(item.appId)) })
        assertTrue(item.appId in NotificationPreferences.state.value.pinned)
        assertTrue(fixtures().any { it.key == item.key })
        assertFalse(context.getSharedPreferences("notification_configuration", 0).getString("config", "")!!.contains("PrivateSentinel"))
    }

    @Test fun burstReconcilesToLatestRecordAndAccessLossClearsLiveData() {
        post("chat", 9, "--ei updates 100 --es title Burst")
        await { fixtures().any { it.title == "Burst" } }
        // Android rate-limits source updates; its rejected posts are not listener events.
        Thread.sleep(2500)
        post("chat", 9, "--es title Burst --es text StableFinal")
        await { fixtures().any { it.title == "Burst" && it.text == "StableFinal" } }
        assertEquals(1, notificationChildren(fixtures()).size)
        val previousItem = fixtures().single()
        val previousSession = MyNotificationListenerService.snapshot.value.sessionId
        val component = "${context.packageName}/com.jeerovan.comfer.MyNotificationListenerService"
        val remaining = originalAccess.split(':').filter { it.isNotBlank() && it != component }.joinToString(":")
        if (android.os.Build.VERSION.SDK_INT >= 27) shell("cmd notification disallow_listener $component")
        else if (remaining.isEmpty()) shell("settings delete secure enabled_notification_listeners")
        else shell("settings put secure enabled_notification_listeners $remaining")
        await { MyNotificationListenerService.snapshot.value.items.isEmpty() && MyNotificationListenerService.snapshot.value.health != ListenerHealth.CONNECTED }
        if (android.os.Build.VERSION.SDK_INT >= 27) shell("cmd notification allow_listener $component")
        else shell("settings put secure enabled_notification_listeners ${(originalAccess.split(':').filter { it.isNotBlank() } + component).distinct().joinToString(":")}")
        await { MyNotificationListenerService.snapshot.value.health == ListenerHealth.CONNECTED && fixtures().any { it.key == previousItem.key } }
        assertEquals("unavailable", runBlocking {
            MyNotificationListenerService.act(previousItem.key, previousItem.revision, "dismiss", previousSession)
        })
    }

    @Test fun expiredOpenFallsBackToAppThenDismissesNotification() = runBlocking {
        post("mail", 12, "--es kind expired --es title Expired")
        await { fixtures().any { it.title == "Expired" } }
        val item = fixtures().first { it.title == "Expired" }
        assertEquals("app_open_requested", MyNotificationListenerService.act(item.key, item.revision, "open", MyNotificationListenerService.snapshot.value.sessionId))
        shell("input keyevent 4")
        await { fixtures().none { it.key == item.key } }
    }

    @Test fun successfulOpenDismissesAndRetainsEligibleHistory() = runBlocking {
        NotificationHistory.initialize(context)
        val title = "SavedOpen${System.nanoTime()}"
        NotificationPreferences.update { it.copy(historyEnabled = true, historySince = System.currentTimeMillis(), historyExcludedApps = emptySet()) }
        try {
            post("mail", 46, "--es title $title --es text SavedBody")
            await { fixtures().any { it.title == title } && NotificationHistory.records.value.any { it.title == title } }
            val item = fixtures().first { it.title == title }
            val copy = NotificationHistory.records.value.first { it.title == title }
            assertTrue(savedNotificationsMatching(listOf(copy), "", MyNotificationListenerService.snapshot.value.items).isEmpty())
            assertEquals("requested", MyNotificationListenerService.act(item.key, item.revision, "open", MyNotificationListenerService.snapshot.value.sessionId))
            await { fixtures().none { it.key == item.key } }
            assertTrue(savedNotificationsMatching(NotificationHistory.records.value, "", MyNotificationListenerService.snapshot.value.items).any { it.id == copy.id })
            shell("input keyevent 4")
            Unit
        } finally {
            NotificationHistory.refresh()
            NotificationHistory.records.value.filter { it.title == title }.forEach { NotificationHistory.delete(it.id) }
        }
    }

    @Test fun historyMoreActionsOpensSavedRuleEditorAndReturnsToSelection() = runBlocking {
        NotificationHistory.initialize(context)
        val title = "HistoryRule${System.nanoTime()}"
        NotificationPreferences.update { it.copy(historyEnabled = true, historySince = System.currentTimeMillis(), historyExcludedApps = emptySet()) }
        try {
            post("mail", 48, "--es title $title --es text SavedRuleBody")
            await { fixtures().any { it.title == title } && NotificationHistory.records.value.any { it.title == title } }
            val item = fixtures().first { it.title == title }
            MyNotificationListenerService.act(item.key, item.revision, "dismiss", MyNotificationListenerService.snapshot.value.sessionId)
            await { fixtures().none { it.key == item.key } }
            val copy = NotificationHistory.records.value.first { it.title == title }
            compose.setContent { MaterialTheme { NotificationInbox(onBack = {}) } }
            compose.onNodeWithTag("saved-tab").performClick()
            val list = compose.onNodeWithTag("notification-inbox-list")
            list.performScrollToNode(hasTestTag("saved-search"))
            compose.onNodeWithTag("saved-search").performTextInput(title)
            shell("input keyevent 4") // close keyboard
            list.performScrollToNode(hasTestTag("saved-copy:${copy.id}"))
            compose.onNodeWithTag("saved-copy:${copy.id}").performTouchInput { longClick() }
            compose.onNodeWithText("More actions").performClick()
            compose.onNodeWithTag("notification-app-actions").assertExists()
            list.performScrollToNode(hasText(context.getString(R.string.notification_protect)))
            compose.onNodeWithText(context.getString(R.string.notification_protect)).assertExists()
            list.performScrollToNode(hasText(context.getString(R.string.notification_sound_settings)))
            compose.onNodeWithText(context.getString(R.string.notification_sound_settings)).assertExists()
            list.performScrollToNode(hasText("Create content rule"))
            compose.onNodeWithText("Create content rule").performClick()
            list.performScrollToNode(hasText("Saved notification reference"))
            compose.onNodeWithText("Saved notification reference").assertIsDisplayed()
            shell("input keyevent 4")
            compose.waitForIdle()
            compose.onNodeWithText("Create content rule").assertExists()
            shell("input keyevent 4")
            compose.waitForIdle()
            compose.onNodeWithTag("saved-select:${copy.id}").assertIsOn()
            compose.onNodeWithTag("saved-selected-actions").assertIsDisplayed()
            compose.onNodeWithText("More actions").performClick()
            list.performScrollToNode(hasText(context.getString(R.string.notification_protect)))
            compose.onNodeWithText(context.getString(R.string.notification_protect)).performClick()
            compose.waitUntil(5000) { NotificationHistory.records.value.none { it.id == copy.id } }
            compose.onNodeWithTag("notification-app-actions").assertExists()
            list.performScrollToNode(hasText(context.getString(R.string.notification_unprotect)))
            compose.onNodeWithText(context.getString(R.string.notification_unprotect)).assertIsEnabled().performClick()
            compose.waitUntil(5000) { copy.appId !in NotificationPreferences.state.value.protectedApps }
            list.performScrollToNode(hasText("Create content rule"))
            compose.onNodeWithText("Create content rule").assertIsNotEnabled()
            list.performScrollToNode(hasText(context.getString(R.string.notification_sound_settings)))
            compose.onNodeWithText(context.getString(R.string.notification_sound_settings)).assertIsEnabled()
        } finally {
            NotificationHistory.refresh()
            NotificationHistory.records.value.filter { it.title == title }.forEach { NotificationHistory.delete(it.id) }
        }
        Unit
    }

    @Test fun failedOpenLeavesNotificationActive() = runBlocking {
        post("mail", 45, "--es kind expired --es title FailedOpen")
        await { fixtures().any { it.title == "FailedOpen" } }
        val item = fixtures().first { it.title == "FailedOpen" }
        post("mail", 0, "--es operation disable-launch")
        try {
            await { context.packageManager.getLaunchIntentForPackage(item.app) == null }
            assertEquals("unavailable", MyNotificationListenerService.act(item.key, item.revision, "open", MyNotificationListenerService.snapshot.value.sessionId))
            assertTrue(fixtures().any { it.key == item.key })
        } finally { post("mail", 0, "--es operation enable-launch") }
    }

    @Test fun openingProtectedNotificationLeavesItActive() = runBlocking {
        post("mail", 44, "--es kind ongoing --es title ProtectedOpen")
        await { fixtures().any { it.title == "ProtectedOpen" } }
        val item = fixtures().first { it.title == "ProtectedOpen" }
        assertTrue(MyNotificationListenerService.act(item.key, item.revision, "open", MyNotificationListenerService.snapshot.value.sessionId) in setOf("requested", "app_open_requested"))
        shell("input keyevent 4")
        assertTrue(fixtures().any { it.key == item.key })
    }

    @Test fun nativeSnoozeRemovesCurrentLiveItemWhenSupported() = runBlocking {
        if (android.os.Build.VERSION.SDK_INT < 26) return@runBlocking
        val id = (System.nanoTime() % Int.MAX_VALUE).toInt()
        post("chat", id, "--es title Snooze")
        await { fixtures().any { it.title == "Snooze" } }
        val item = fixtures().first { it.title == "Snooze" }
        assertEquals("requested", MyNotificationListenerService.act(item.key, item.revision, "snooze", MyNotificationListenerService.snapshot.value.sessionId))
        await { fixtures().none { it.key == item.key } }
        post("chat", id, "--es operation remove")
    }

    @Test fun longPressSelectsAndMultipleSelectionFiltersQuickActions() {
        post("mail", 23, "--es title GestureFixture")
        post("mail", 25, "--es title ProtectedFixture --es kind ongoing")
        await { fixtures().any { it.title == "GestureFixture" } && fixtures().any { it.title == "ProtectedFixture" } }
        val item = fixtures().first { it.title == "GestureFixture" }
        val protected = fixtures().first { it.title == "ProtectedFixture" }
        compose.setContent { MaterialTheme { NotificationInbox(onBack = {}, initialApp = item.appId) } }
        compose.onNodeWithTag("notification-inbox-list").performScrollToNode(hasText("GestureFixture"))
        val beforeSelection = compose.onNodeWithText("GestureFixture").getUnclippedBoundsInRoot()
        compose.onNodeWithText("GestureFixture").performTouchInput { longClick() }
        val afterSelection = compose.onNodeWithText("GestureFixture").getUnclippedBoundsInRoot()
        assertEquals(beforeSelection.left.value, afterSelection.left.value, .1f)
        assertEquals(beforeSelection.right.value, afterSelection.right.value, .1f)
        compose.onNodeWithTag("notification-selected-actions").assertIsDisplayed()
        compose.onNodeWithText("Cancel").assertDoesNotExist()
        compose.onNodeWithTag("notification-select-${item.key}", useUnmergedTree = true).assertIsOn()
        compose.onNodeWithText("More actions").assertIsDisplayed()
        compose.onNodeWithText("More actions").assertIsDisplayed()
        compose.onNodeWithTag("notification-inbox-list").performScrollToNode(hasText("ProtectedFixture"))
        compose.onNodeWithTag("notification-select-${protected.key}", useUnmergedTree = true).assertIsOff().performClick()
        compose.onNodeWithText("Cancel").assertIsDisplayed()
        compose.onNodeWithText("Cancel").assertIsDisplayed()
        compose.onNodeWithTag("notification-select-${protected.key}", useUnmergedTree = true).assertIsOn()
        compose.onNodeWithText("More actions").assertDoesNotExist()
        compose.onNodeWithText("Snooze").assertDoesNotExist()
        compose.onNodeWithText("Dismiss selected").assertDoesNotExist()
        compose.onNodeWithTag("notification-select-${protected.key}", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Cancel").assertDoesNotExist()
        compose.onNodeWithTag("notification-inbox-list").performScrollToNode(hasText("GestureFixture"))
        compose.onNodeWithTag("notification-select-${item.key}", useUnmergedTree = true).performClick()
        compose.onNodeWithTag("notification-selected-actions").assertDoesNotExist()
        compose.onNodeWithTag("notification-select-${item.key}", useUnmergedTree = true).assertDoesNotExist()
        assertTrue(fixtures().any { it.key == item.key })
    }

    @Test fun freshSelectionClearsOldChangeWarningBeforeMoreActions() {
        post("mail", 47, "--es title SelectionOriginal --es text OriginalBody")
        await { fixtures().any { it.title == "SelectionOriginal" } }
        val item = fixtures().first { it.title == "SelectionOriginal" }
        compose.setContent { MaterialTheme { NotificationInbox(onBack = {}, initialApp = item.appId) } }
        val list = compose.onNodeWithTag("notification-inbox-list")
        list.performScrollToNode(hasText("SelectionOriginal"))
        compose.onNodeWithText("SelectionOriginal").performTouchInput { longClick() }
        compose.onNodeWithText("More actions").performClick()
        compose.onNodeWithTag("notification-app-actions").assertExists()
        compose.onNodeWithText("Create content rule").assertExists()
        post("mail", 47, "--es title SelectionUpdated --es text UpdatedBody")
        await { fixtures().any { it.title == "SelectionUpdated" } }
        val warning = context.getString(R.string.notification_changed)
        list.performScrollToNode(hasText(warning))
        compose.onNodeWithText(warning).assertIsDisplayed()
        compose.onNodeWithText("Create content rule").assertDoesNotExist()
        list.performScrollToNode(hasText("SelectionUpdated"))
        compose.onNodeWithText("SelectionUpdated").performTouchInput { longClick() }
        compose.onNodeWithText("More actions").performClick()
        compose.onNodeWithText(warning).assertDoesNotExist()
        compose.onNodeWithTag("notification-app-actions").assertExists()
        compose.onNodeWithText("Create content rule").assertExists()
        assertTrue(fixtures().any { it.title == "SelectionUpdated" })
    }

    @Test fun groupsUseCaretsAndCanCollapseWithoutLosingChildren() {
        runBlocking { NotificationPreferences.update { it.copy(chronological = false) } }
        post("mail", 27, "--es title GroupOne --es group g")
        post("mail", 28, "--es title GroupTwo --es group g")
        post("mail", 29, "--es kind summary --es group g")
        await { fixtures().size == 3 }
        val appId = fixtures().first().appId
        compose.setContent { MaterialTheme { NotificationInbox(onBack = {}, initialApp = appId) } }
        val header = "app-header:$appId"
        compose.onNodeWithTag("notification-inbox-list").performScrollToNode(hasTestTag(header))
        compose.onNodeWithText("Fixture Mail").assertIsDisplayed()
        compose.onNodeWithText("Collapse").assertDoesNotExist()
        compose.onNodeWithContentDescription("Collapse", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("group-expand:$appId").performClick()
        compose.onNodeWithText("GroupOne").assertDoesNotExist()
        compose.onNodeWithText("GroupTwo").assertDoesNotExist()
        compose.onNodeWithTag("group-expand:$appId").performClick()
        compose.onNodeWithTag("notification-inbox-list").performScrollToNode(hasText("GroupOne"))
        compose.onNodeWithText("GroupOne").assertIsDisplayed()
        assertEquals(3, fixtures().size)
    }

    @Test fun groupTitleSelectsCollapsedChildrenWithoutChangingExpansionOrOtherGroups() {
        post("mail", 40, "--es title SelectMailOne")
        post("mail", 41, "--es title SelectMailTwo")
        post("chat", 42, "--es title SelectChat")
        await { notificationChildren(fixtures()).size == 3 }
        runBlocking { NotificationPreferences.update { it.copy(chronological = false) } }
        val mail = fixtures().first { it.title == "SelectMailOne" }
        val chat = fixtures().first { it.title == "SelectChat" }
        compose.setContent { MaterialTheme { NotificationInbox(onBack = {}) } }
        val list = compose.onNodeWithTag("notification-inbox-list")
        list.performScrollToNode(hasText(mail.title))
        compose.onNodeWithText(mail.title).performTouchInput { longClick() }
        list.performScrollToNode(hasTestTag("group-expand:${mail.appId}"))
        compose.onNodeWithTag("group-expand:${mail.appId}").performClick()
        compose.onNodeWithText("SelectMailTwo").assertDoesNotExist()
        val mailGroup = compose.onNodeWithTag("group-select:${mail.appId}")
        mailGroup.performClick()
        list.performScrollToNode(hasTestTag("group-select:${mail.appId}"))
        compose.onNodeWithTag("group-select:${mail.appId}").assertIsOn()
        compose.onNodeWithText("SelectMailTwo").assertDoesNotExist()
        list.performScrollToNode(hasText(chat.title))
        compose.onNodeWithText(chat.title).performClick()
        list.performScrollToNode(hasTestTag("group-select:${mail.appId}"))
        compose.onNodeWithTag("group-select:${mail.appId}").performClick()
        list.performScrollToNode(hasTestTag("group-select:${mail.appId}"))
        compose.onNodeWithTag("group-select:${mail.appId}").assertIsOff()
        compose.onNodeWithText("More actions").assertIsDisplayed() // Chat remains selected.
        list.performScrollToNode(hasTestTag("group-select:${chat.appId}"))
        compose.onNodeWithTag("group-select:${chat.appId}").assertIsOn().performClick()
        compose.onNodeWithTag("notification-selected-actions").assertDoesNotExist()
        assertEquals(3, notificationChildren(fixtures()).size)
    }

    @Test fun ownedFocusRuleExpiresAfterElapsedDeadline() = runBlocking {
        val runtime = context.getSharedPreferences("notification_quiet_runtime", 0)
        org.junit.Assume.assumeTrue(runtime.getString("rule_id", null) == null)
        val manager = context.getSystemService(android.app.NotificationManager::class.java)
        val hadAccess = manager.isNotificationPolicyAccessGranted
        if (!hadAccess && android.os.Build.VERSION.SDK_INT >= 27) shell("cmd notification allow_dnd ${context.packageName}")
        val previousFilter = manager.currentInterruptionFilter
        try {
            NotificationPreferences.update { it.copy(paused = false, quietSchedule = QuietSchedule()) }
            assertTrue(NotificationQuietHours.focus(context, 1))
            await { manager.currentInterruptionFilter != android.app.NotificationManager.INTERRUPTION_FILTER_ALL }
            assertTrue(runtime.edit().putLong("focus_elapsed", android.os.SystemClock.elapsedRealtime() - 1000).commit())
            assertTrue(NotificationQuietHours.reconcile(context))
            await { manager.currentInterruptionFilter == previousFilter }
            assertEquals(QuietHealth.OFF, NotificationQuietHours.state.value.health)
        } finally {
            NotificationQuietHours.reset(context)
            if (!hadAccess && android.os.Build.VERSION.SDK_INT >= 27) shell("cmd notification disallow_dnd ${context.packageName}")
        }
    }

    @Test fun focusCleanupPreservesOverlappingRule() = runBlocking {
        val runtime = context.getSharedPreferences("notification_quiet_runtime", 0)
        org.junit.Assume.assumeTrue("Do not disrupt an existing configured focus rule", runtime.getString("rule_id", null) == null)
        val manager = context.getSystemService(android.app.NotificationManager::class.java)
        val hadPolicyAccess = manager.isNotificationPolicyAccessGranted
        if (!hadPolicyAccess && android.os.Build.VERSION.SDK_INT >= 27) shell("cmd notification allow_dnd ${context.packageName}")
        assertTrue("Notification policy access is required for this emulator test", manager.isNotificationPolicyAccessGranted)
        val originalRules = manager.automaticZenRules.keys
        val originalFilter = manager.currentInterruptionFilter
        val unrelatedUri = android.net.Uri.parse("condition://${context.packageName}/test-preserved")
        val unrelated = manager.addAutomaticZenRule(android.app.AutomaticZenRule(
            "Unrelated test rule", android.content.ComponentName(context, ComferQuietConditionProvider::class.java),
            unrelatedUri, android.app.NotificationManager.INTERRUPTION_FILTER_PRIORITY, true,
        ))
        val before = originalRules + unrelated
        try {
            val condition = android.service.notification.Condition(unrelatedUri, "Unrelated test rule", android.service.notification.Condition.STATE_TRUE)
            if (android.os.Build.VERSION.SDK_INT >= 29) manager.setAutomaticZenRuleState(unrelated, condition)
            else {
                await { ComferQuietConditionProvider.instance != null }
                ComferQuietConditionProvider.instance!!.notifyCondition(condition)
            }
            await { manager.currentInterruptionFilter != android.app.NotificationManager.INTERRUPTION_FILTER_ALL }
            val previousFilter = manager.currentInterruptionFilter
            NotificationPreferences.update { it.copy(paused = false, quietSchedule = QuietSchedule()) }
            assertTrue(NotificationQuietHours.focus(context, 1))
            await { manager.currentInterruptionFilter != android.app.NotificationManager.INTERRUPTION_FILTER_ALL }
            val id = runtime.getString("rule_id", null)!!
            assertTrue(manager.automaticZenRules.keys.containsAll(before))
            assertTrue(manager.getAutomaticZenRule(id).isEnabled)
            // Simulate process recovery after a missed timer boundary, without waiting a minute.
            assertTrue(runtime.edit().putLong("focus_until", System.currentTimeMillis() - 1000)
                .putLong("focus_elapsed", android.os.SystemClock.elapsedRealtime() - 1000).commit())
            assertTrue(NotificationQuietHours.reconcile(context))
            await { NotificationQuietHours.state.value.health == QuietHealth.OFF && manager.currentInterruptionFilter == previousFilter }
            assertTrue(NotificationQuietHours.reset(context))
            assertEquals(before, manager.automaticZenRules.keys)
        } finally {
            NotificationQuietHours.reset(context)
            manager.removeAutomaticZenRule(unrelated)
            await { manager.currentInterruptionFilter == originalFilter }
            if (!hadPolicyAccess && android.os.Build.VERSION.SDK_INT >= 27) shell("cmd notification disallow_dnd ${context.packageName}")
        }
    }
}
