package com.jeerovan.comfer

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.jeerovan.comfer.notifications.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*

class NotificationRuleEditorTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var previous: NotificationConfiguration
    @Before fun setup() = runBlocking {
        NotificationPreferences.initialize(InstrumentationRegistry.getInstrumentation().targetContext)
        previous = NotificationPreferences.state.value
        NotificationPreferences.update { it.copy(rules = emptyList()) }
        Unit
    }
    @After fun cleanup() = runBlocking { NotificationPreferences.update { previous }; Unit }
    @Test fun savingDismissRuleRequiresPreviewAndSeparateConsent() {
        compose.setContent {
            MaterialTheme { Column(Modifier.verticalScroll(rememberScrollState())) { NotificationRuleEditor(null, onSaved = {}) } }
        }
        compose.onNodeWithText("Save in test-only mode").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Contains text — one phrase per line").performScrollTo().performTextInput("sale")
        compose.onNodeWithText("Action: automatically dismiss").performScrollTo().assertExists()
        compose.onNodeWithText("Test only — record matches without acting").performScrollTo().performClick()
        compose.onNodeWithText("Save and enable rule").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Preview current notifications").performScrollTo().performClick()
        compose.onNodeWithText("Save and enable rule").performScrollTo().assertIsEnabled().performClick()
        compose.onNodeWithText("Enable automatic dismissal?").assertIsDisplayed()
        assertTrue(NotificationPreferences.state.value.rules.isEmpty())
        compose.onNodeWithText("Cancel").performClick()
        assertTrue(NotificationPreferences.state.value.rules.isEmpty())
    }
    @Test fun savedReferenceProvidesAppScopeMessageAndPreviewWithoutLiveHandles() {
        val copy = SavedNotification("saved-rule-test", "fixture.mail", 10, "Package shipped", "Delivery tomorrow", 1, 2)
        var saved = false
        compose.setContent {
            MaterialTheme { Column(Modifier.verticalScroll(rememberScrollState())) {
                NotificationRuleEditor(null, savedSource = copy, onSaved = { saved = true })
            } }
        }
        compose.onNodeWithText("Contains text — one phrase per line").performScrollTo().assertTextContains("Package shipped")
        compose.onNodeWithText("Use message").performScrollTo().performClick()
        compose.onNodeWithText("Contains text — one phrase per line").performScrollTo().assertTextContains("Delivery tomorrow")
        compose.onNodeWithText("Preview current notifications").performScrollTo().performClick()
        compose.onNodeWithTag("saved-rule-preview").performScrollTo().assertTextContains("Saved reference: Matches", substring = true)
        compose.onNodeWithText("Save in test-only mode").performScrollTo().performClick()
        compose.waitUntil(5000) { saved }
        val rule = NotificationPreferences.state.value.rules.single()
        assertEquals(copy.appId, rule.appId)
        assertNull(rule.channelId)
        assertEquals(RuleField.BODY, rule.field)
        assertEquals(listOf("Delivery tomorrow"), rule.terms)
        assertTrue(rule.observeOnly)
    }

}
