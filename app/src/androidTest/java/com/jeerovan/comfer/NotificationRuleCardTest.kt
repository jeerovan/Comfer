package com.jeerovan.comfer

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.jeerovan.comfer.notifications.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class NotificationRuleCardTest(private val direction: LayoutDirection) {
    companion object {
        @JvmStatic @Parameterized.Parameters(name = "{0}")
        fun directions() = listOf(arrayOf(LayoutDirection.Ltr), arrayOf(LayoutDirection.Rtl))
    }
    @get:Rule val compose = createComposeRule()
    private val guidePrefs get() = InstrumentationRegistry.getInstrumentation().targetContext.getSharedPreferences(NOTIFICATION_GUIDE_PREFERENCES, Context.MODE_PRIVATE)
    private var guideWasCompleted = false
    private lateinit var previous: NotificationConfiguration
    @Before fun setup() = runBlocking {
        NotificationPreferences.initialize(InstrumentationRegistry.getInstrumentation().targetContext)
        guideWasCompleted = guidePrefs.getBoolean("RULE_SWIPE", false)
        guidePrefs.edit().remove("RULE_SWIPE").commit()
        previous = NotificationPreferences.state.value
        NotificationPreferences.update { it.copy(rules = listOf(NotificationRule("card-test", "Offers", terms = listOf("sale")))) }
        Unit
    }
    @After fun restore() = runBlocking { NotificationPreferences.update { previous }; guidePrefs.edit().putBoolean("RULE_SWIPE", guideWasCompleted).commit(); Unit }

    @Test fun swipeCompletesGuideEvenWhenDeletionIsCancelled() {
        var edited: String? = null
        var mounted by mutableStateOf(true)
        compose.setContent { CompositionLocalProvider(LocalLayoutDirection provides direction) {
            MaterialTheme { if (mounted) NotificationRulesSettings { edited = it } }
        } }
        val before = NotificationPreferences.state.value.rules.single().enabled
        compose.onNodeWithTag("rule-enabled:card-test").performClick()
        compose.waitUntil { NotificationPreferences.state.value.rules.single().enabled != before }
        compose.runOnIdle { assertNull(edited) }
        val card = compose.onNodeWithTag("rule-card:card-test")
        card.performTouchInput { click() }
        compose.runOnIdle { assertEquals("card-test", edited) }
        compose.onNodeWithTag("notification-guide-RULE_SWIPE").assertIsDisplayed()
        card.performTouchInput { swipe(center, center.copy(x = center.x - 10f), durationMillis = 1000) }
        compose.onNodeWithTag("notification-guide-RULE_SWIPE").assertIsDisplayed()
        assertFalse(guidePrefs.getBoolean("RULE_SWIPE", false))
        card.performTouchInput { swipeLeft() }
        compose.onNodeWithText("Delete rule?").assertIsDisplayed()
        assertEquals(1, NotificationPreferences.state.value.rules.size)
        assertTrue("Recognizing the swipe teaches it before deletion", guidePrefs.getBoolean("RULE_SWIPE", false))
        compose.onNodeWithText("Cancel").performClick()
        card.assertIsDisplayed()
        assertEquals(1, NotificationPreferences.state.value.rules.size)
        compose.onNodeWithTag("notification-guide-RULE_SWIPE").assertDoesNotExist()
        compose.runOnIdle { mounted = false }
        compose.runOnIdle { mounted = true }
        compose.onNodeWithTag("notification-guide-RULE_SWIPE").assertDoesNotExist()
        card.performTouchInput { swipeRight() }
        compose.onNodeWithText("Delete").performClick()
        compose.waitUntil { NotificationPreferences.state.value.rules.isEmpty() }
        card.assertDoesNotExist()
        compose.waitUntil { guidePrefs.getBoolean("RULE_SWIPE", false) }
    }

    @Test fun rightSwipeAlsoCompletesGuideWithoutDeletingRule() {
        compose.setContent { CompositionLocalProvider(LocalLayoutDirection provides direction) {
            MaterialTheme { NotificationRulesSettings {} }
        } }
        compose.onNodeWithTag("notification-guide-RULE_SWIPE").assertIsDisplayed()
        compose.onNodeWithTag("rule-card:card-test").performTouchInput { swipeRight() }
        compose.onNodeWithText("Delete rule?").assertIsDisplayed()
        assertTrue(guidePrefs.getBoolean("RULE_SWIPE", false))
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithTag("notification-guide-RULE_SWIPE").assertDoesNotExist()
        assertEquals("card-test", NotificationPreferences.state.value.rules.single().id)
    }
}
