package com.jeerovan.comfer.notes

import androidx.test.core.app.ActivityScenario
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Test

class NotesEntryPrivacyTest {
    @After fun lockSession() = NotesSession.lock()

    @Test fun freshEntryKeepsUnexpiredSharedVerification() {
        NotesSession.authorize()
        ActivityScenario.launch(NotesActivity::class.java).use { scenario ->
            scenario.onActivity { assertTrue(NotesSession.unlocked()) }
        }
    }

    @Test fun freshEntryDoesNotReviveLockedVerification() {
        NotesSession.authorize()
        NotesSession.lock()
        ActivityScenario.launch(NotesActivity::class.java).use { scenario ->
            scenario.onActivity { assertFalse(NotesSession.unlocked()) }
        }
    }
}
