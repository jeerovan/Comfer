package com.jeerovan.comfer

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.jeerovan.comfer.notifications.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*

class NotificationRuleCardTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var previous: NotificationConfiguration
    @Before fun setup() = runBlocking {
        NotificationPreferences.initialize(InstrumentationRegistry.getInstrumentation().targetContext)
        previous = NotificationPreferences.state.value
        NotificationPreferences.update { it.copy(rules = listOf(NotificationRule("card-test", "Offers", terms = listOf("sale")))) }
        Unit
    }
    @After fun restore() = runBlocking { NotificationPreferences.update { previous }; Unit }

    @Test fun switchLongPressAndConfirmedSwipePreserveRuleUntilConfirmation() {
        var edited: String? = null
        compose.setContent { MaterialTheme { NotificationRulesSettings { edited = it } } }
        val before = NotificationPreferences.state.value.rules.single().enabled
        compose.onNodeWithTag("rule-enabled:card-test").performClick()
        compose.waitUntil { NotificationPreferences.state.value.rules.single().enabled != before }
        compose.runOnIdle { assertNull(edited) }
        val card = compose.onNodeWithTag("rule-card:card-test")
        card.performTouchInput { longClick() }
        compose.runOnIdle { assertEquals("card-test", edited) }
        card.performTouchInput { swipeLeft() }
        compose.onNodeWithText("Delete rule?").assertIsDisplayed()
        assertEquals(1, NotificationPreferences.state.value.rules.size)
        compose.onNodeWithText("Cancel").performClick()
        card.assertIsDisplayed()
        assertEquals(1, NotificationPreferences.state.value.rules.size)
        card.performTouchInput { swipeRight() }
        compose.onNodeWithText("Delete").performClick()
        compose.waitUntil { NotificationPreferences.state.value.rules.isEmpty() }
        card.assertDoesNotExist()
    }
}
