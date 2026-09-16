package com.jeerovan.comfer

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.jeerovan.comfer.notifications.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*

/** Exercises real activity lifecycle and external navigation, not a generic Compose host. */
class NotificationNavigationGestureTest {
    @get:Rule val compose = createAndroidComposeRule<NotificationInboxActivity>()
    private val context get() = compose.activity
    private var granted = false
    private var originalAccess = ""
    private lateinit var previous: NotificationConfiguration
    private fun shell(command: String): String = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command).use {
        android.os.ParcelFileDescriptor.AutoCloseInputStream(it).bufferedReader().readText()
    }
    private fun await(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 35000
        while (!condition() && System.currentTimeMillis() < deadline) Thread.sleep(100)
        assertTrue("Timed out waiting for listener or activity", condition())
    }
    private fun post(app: String, id: Int, extras: String = "") {
        // Keep fixture delivery independent of a congested OEM background broadcast queue.
        shell("am broadcast --receiver-foreground -n com.jeerovan.fixtures.$app/com.jeerovan.fixtures.FixtureReceiver --ei id $id $extras")
    }
    private fun fixtures() = MyNotificationListenerService.snapshot.value.items.filter { it.app == "com.jeerovan.fixtures.mail" }
    @Before fun setup() {
        previous = NotificationPreferences.state.value
        granted = MyNotificationListenerService.hasAccess(context)
        originalAccess = shell("settings get secure enabled_notification_listeners").trim().takeUnless { it == "null" }.orEmpty()
        val component = "${context.packageName}/com.jeerovan.comfer.MyNotificationListenerService"
        if (android.os.Build.VERSION.SDK_INT >= 27) shell("cmd notification allow_listener $component")
        else shell("settings put secure enabled_notification_listeners ${(originalAccess.split(':').filter { it.isNotBlank() } + component).distinct().joinToString(":")}")
        MyNotificationListenerService.refresh(context, force = true)
        await { MyNotificationListenerService.snapshot.value.health == ListenerHealth.CONNECTED }
        runBlocking { NotificationPreferences.update { NotificationConfiguration(setup = true) } }
    }
    @After fun cleanup() {
        for (id in listOf(24, 26, 76, 77) + (60..67)) post("mail", id, "--es operation remove")
        runBlocking { NotificationPreferences.update { previous } }
        if (android.os.Build.VERSION.SDK_INT >= 27) {
            if (!granted) shell("cmd notification disallow_listener ${context.packageName}/com.jeerovan.comfer.MyNotificationListenerService")
        } else if (originalAccess.isEmpty()) shell("settings delete secure enabled_notification_listeners")
        else shell("settings put secure enabled_notification_listeners $originalAccess")
    }
    @Test fun selectionRetainsThumbReachAndSwipeGuideUsesDismissibleCard() {
        val prefs = context.getSharedPreferences(NOTIFICATION_GUIDE_PREFERENCES, android.content.Context.MODE_PRIVATE)
        val previousGuides = prefs.all
        try {
            prefs.edit().clear().commit()
            runBlocking { NotificationPreferences.update { it.copy(chronological = true) } }
            post("mail", 24, "--es title DismissibleGuide")
            await { fixtures().any { it.title == "DismissibleGuide" } }
            post("mail", 26, "--es title ProtectedGuide --es kind ongoing")
            await { fixtures().any { it.title == "ProtectedGuide" } }
            val protected = fixtures().first { it.title == "ProtectedGuide" }
            val dismissible = fixtures().first { it.title == "DismissibleGuide" }
            val list = compose.onNodeWithTag("notification-inbox-list")
            list.performTouchInput { swipeDown(startY = height * .1f, endY = height * .8f, durationMillis = 700) }
            val card = compose.onNodeWithTag("notification-card-${protected.key}")
            val lowered = card.getUnclippedBoundsInRoot().top.value
            assertTrue("Notification is pulled into reach", lowered > 150f)
            compose.onNodeWithTag("notification-guide-CARD_HOLD").assertIsDisplayed()
            card.performTouchInput { longClick() }
            compose.onNodeWithTag("notification-selected-actions").assertIsDisplayed()
            assertEquals("Selection must stay near the pulled-down position as the toolbar animates", lowered, card.getUnclippedBoundsInRoot().top.value, 24f)
            compose.onNodeWithTag("notification-guide-CARD_SWIPE").assertIsDisplayed()
            val hand = compose.onNodeWithTag("notification-guide-CARD_SWIPE").getUnclippedBoundsInRoot()
            val swipeCard = compose.onNodeWithTag("notification-card-${dismissible.key}").getUnclippedBoundsInRoot()
            assertTrue("Swipe guide targets dismissible card below protected first card", hand.top >= swipeCard.top && hand.bottom <= swipeCard.bottom)
            compose.onNodeWithTag("notification-select-${protected.key}", useUnmergedTree = true).performClick()
            assertEquals("Deselection must stay near the pulled-down position", lowered, card.getUnclippedBoundsInRoot().top.value, 24f)
            compose.onNodeWithTag("notification-guide-CARD_SWIPE").assertIsDisplayed()
        } finally {
            prefs.edit().clear().also { editor -> previousGuides.forEach { (key, value) -> if (value is Boolean) editor.putBoolean(key, value) } }.commit()
        }
    }

    @Test fun horizontalSwipeDismissesAndTapOpensSource() {
        post("mail", 24, "--es title SwipeFixture")
        await { fixtures().any { it.title == "SwipeFixture" } }
        val item = fixtures().first { it.title == "SwipeFixture" }
        compose.onNodeWithTag("notification-inbox-list").performScrollToNode(hasText("SwipeFixture"))
        val card = compose.onNodeWithTag("notification-card-${item.key}")
        val initialBounds = card.getUnclippedBoundsInRoot()
        card.performTouchInput { down(center); moveBy(androidx.compose.ui.geometry.Offset(-width * .15f, 0f)) }
        val draggedBounds = card.getUnclippedBoundsInRoot()
        assertTrue("The notification must visibly follow the swipe", draggedBounds.left < initialBounds.left)
        card.performTouchInput { cancel() }
        compose.waitForIdle()
        assertEquals(initialBounds.left.value, card.getUnclippedBoundsInRoot().left.value, .5f)
        assertTrue("A cancelled swipe must preserve the notification", fixtures().any { it.key == item.key })
        card.performTouchInput { swipeLeft() }
        compose.waitForIdle() // Advance the exit animation before polling Android's listener.
        await { fixtures().none { it.key == item.key } }
        post("mail", 26, "--es title OpenFixture")
        await { fixtures().any { it.title == "OpenFixture" } }
        compose.onNodeWithTag("notification-inbox-list").performScrollToNode(hasText("OpenFixture"))
        compose.onNodeWithText("OpenFixture").performClick()
        await { shell("dumpsys activity activities").lineSequence().any { (it.contains("mResumedActivity") || it.contains("topResumedActivity")) && it.contains("com.jeerovan.fixtures.mail") } }
        shell("input keyevent 4")
        await { shell("dumpsys activity activities").lineSequence().any { (it.contains("mResumedActivity") || it.contains("topResumedActivity")) && it.contains("NotificationInboxActivity") } }
        compose.waitForIdle()
        // Successful opening dismisses eligible records (Notification-Features.md).
        await { fixtures().none { it.title == "OpenFixture" } }
    }

    @Test fun returningFromAndroidSettingsRestoresSelectedNotification() {
        for (id in 60..67) post("mail", id, "--es title ReturnFixture$id")
        await { (60..67).all { id -> fixtures().any { it.title == "ReturnFixture$id" } } }
        val target = fixtures().first { it.title == "ReturnFixture60" }
        compose.onNodeWithTag("notification-inbox-list").performScrollToNode(hasText(target.title))
        compose.onNodeWithText(target.title).performTouchInput { longClick() }
        compose.onNodeWithText("More actions").assertIsDisplayed()
        compose.onNodeWithText("More actions").performClick()
        compose.onNodeWithTag("notification-inbox-list").performScrollToNode(hasText("Sound and notification settings"))
        compose.onNodeWithText("Sound and notification settings").performClick()
        await { shell("dumpsys activity activities").lineSequence().any { (it.contains("mResumedActivity") || it.contains("topResumedActivity")) && it.contains("com.android.settings") } }
        await { shell("dumpsys window").lineSequence().any { it.contains("mCurrentFocus") && it.contains("com.android.settings") } }
        InstrumentationRegistry.getInstrumentation().uiAutomation.waitForIdle(500, 5000)
        shell("input keyevent 4")
        await { shell("dumpsys activity activities").lineSequence().any { (it.contains("mResumedActivity") || it.contains("topResumedActivity")) && it.contains("NotificationInboxActivity") } }
        compose.waitUntil(10_000) { runCatching { compose.onNodeWithTag("notification-card-${target.key}").isDisplayed() }.getOrDefault(false) }
        compose.onNodeWithTag("notification-select-${target.key}", useUnmergedTree = true).assertIsOn()
        compose.onNodeWithTag("notification-selected-actions").assertIsDisplayed()
        compose.onNodeWithText("Cancel").assertDoesNotExist()
    }

    @Test fun groupTitleSelectsAllWhileCaretOnlyChangesExpansion() {
        post("mail", 76, "--es title GroupSelectFirst")
        post("mail", 77, "--es title GroupSelectSecond")
        await { fixtures().any { it.title == "GroupSelectFirst" } && fixtures().any { it.title == "GroupSelectSecond" } }
        val first = fixtures().first { it.title == "GroupSelectFirst" }
        val second = fixtures().first { it.title == "GroupSelectSecond" }
        val list = compose.onNodeWithTag("notification-inbox-list")
        list.performScrollToNode(hasText(first.title))
        compose.onNodeWithText(first.title).performTouchInput { longClick() }
        list.performScrollToNode(hasTestTag("group-select:${first.appId}"))
        compose.onNodeWithTag("group-select:${first.appId}").performClick()
        list.performScrollToNode(hasTestTag("group-select:${first.appId}"))
        compose.onNodeWithTag("group-select:${first.appId}").assertIsOn()
        compose.onNodeWithTag("group-expand:${first.appId}").performClick()
        compose.onNodeWithText(second.title).assertDoesNotExist()
        compose.onNodeWithTag("group-select:${first.appId}").assertIsOn()
        compose.onNodeWithTag("group-expand:${first.appId}").performClick()
        list.performScrollToNode(hasText(second.title))
        compose.onNodeWithTag("notification-select-${second.key}", useUnmergedTree = true).assertIsOn()
        list.performScrollToNode(hasTestTag("group-select:${first.appId}"))
        compose.onNodeWithTag("group-select:${first.appId}").performClick()
        compose.onNodeWithTag("notification-selected-actions").assertDoesNotExist()
        assertTrue(fixtures().any { it.key == first.key } && fixtures().any { it.key == second.key })
    }

    @Test fun selectionTracksDeselectAndSourceRemovalWithoutMovingText() {
        post("mail", 76, "--es title SelectionFirst")
        post("mail", 77, "--es title SelectionSecond")
        await { fixtures().any { it.title == "SelectionFirst" } && fixtures().any { it.title == "SelectionSecond" } }
        val first = fixtures().first { it.title == "SelectionFirst" }
        val second = fixtures().first { it.title == "SelectionSecond" }
        val list = compose.onNodeWithTag("notification-inbox-list")
        list.performScrollToNode(hasText(first.title))
        val before = compose.onNodeWithText(first.title).getUnclippedBoundsInRoot()
        compose.onNodeWithTag("notification-select-${first.key}", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithText(first.title).performTouchInput { longClick() }
        val after = compose.onNodeWithText(first.title).getUnclippedBoundsInRoot()
        assertEquals(before.left.value, after.left.value, .1f)
        assertEquals(before.right.value, after.right.value, .1f)
        val card = compose.onNodeWithTag("notification-card-${first.key}").getUnclippedBoundsInRoot()
        val indicator = compose.onNodeWithTag("notification-select-${first.key}", useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertEquals((card.top.value + card.bottom.value) / 2, (indicator.top.value + indicator.bottom.value) / 2, .5f)
        compose.onNodeWithText("Cancel").assertDoesNotExist()
        list.performScrollToNode(hasText(second.title))
        compose.onNodeWithTag("notification-select-${second.key}", useUnmergedTree = true).assertIsOff().performClick()
        compose.onNodeWithText("Cancel").assertIsDisplayed()
        compose.onNodeWithTag("notification-select-${second.key}", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Cancel").assertDoesNotExist()
        list.performScrollToNode(hasText(first.title))
        compose.onNodeWithTag("notification-select-${first.key}", useUnmergedTree = true).performClick()
        compose.onNodeWithTag("notification-selected-actions").assertDoesNotExist()
        compose.onNodeWithText(first.title).performTouchInput { longClick() }
        post("mail", 76, "--es operation remove")
        await { fixtures().none { it.key == first.key } }
        compose.onNodeWithTag("notification-selected-actions").assertDoesNotExist()
    }
}
