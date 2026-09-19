package com.jeerovan.comfer

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ProtectionTimeoutSettingTest {
    @get:Rule val compose = createComposeRule()

    @Test fun sharedSettingShowsDefaultAndAllThreeChoices() {
        val selected = mutableIntStateOf(120)
        compose.setContent { MaterialTheme { ProtectionTimeoutSetting(selected.intValue) { selected.intValue = it } } }
        compose.onNodeWithText("Protection Timeout").assertIsDisplayed()
        compose.onNodeWithText("For notes and journals.").assertIsDisplayed()
        compose.onNodeWithText("2 min").assertIsDisplayed()
        for ((label, seconds) in listOf("30 sec" to 30, "5 min" to 300, "2 min" to 120)) {
            compose.onNodeWithText("Protection Timeout").performClick()
            compose.onNodeWithText(label).performClick()
            compose.runOnIdle { assertEquals(seconds, selected.intValue) }
            compose.onNodeWithText(label).assertIsDisplayed()
        }
    }

    @Test fun timeoutPreferenceSurvivesReloadAndIsSharedByBothModules() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName.endsWith(".notificationtest"))
        StartupCoordinator.awaitReady()
        val previous = PreferenceManager.getString(context, ProtectionSession.PREFERENCE_KEY, null)
        try {
            PreferenceManager.setInt(context, ProtectionSession.PREFERENCE_KEY, 30)
            kotlinx.coroutines.withTimeout(5000) {
                context.settingsDataStore.data.first { prefs ->
                    prefs.asMap().any { it.key.name == ProtectionSession.PREFERENCE_KEY && it.value.toString() == "30" }
                }
            }
            PreferenceManager.reload(context)
            assertEquals(30, ProtectionSession.timeoutSeconds())
            com.jeerovan.comfer.notes.NotesSession.authorize()
            assertTrue(com.jeerovan.comfer.journals.JournalProtection.authorized())
            com.jeerovan.comfer.journals.JournalProtection.lock()
            assertFalse(com.jeerovan.comfer.notes.NotesSession.unlocked())
            com.jeerovan.comfer.journals.JournalProtection.authorize()
            assertTrue(com.jeerovan.comfer.notes.NotesSession.unlocked())
        } finally {
            ProtectionSession.lock()
            PreferenceManager.setString(context, ProtectionSession.PREFERENCE_KEY, previous)
        }
    }
}
