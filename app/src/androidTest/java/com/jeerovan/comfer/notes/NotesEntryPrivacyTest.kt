package com.jeerovan.comfer.notes

import androidx.test.core.app.ActivityScenario
import org.junit.Assert.assertFalse
import org.junit.Test

class NotesEntryPrivacyTest {
    @Test fun freshEntryDoesNotInheritBackupAuthorization() {
        NotesSession.authorize()
        ActivityScenario.launch(NotesActivity::class.java).use { scenario ->
            scenario.onActivity { assertFalse(NotesSession.unlocked()) }
        }
    }
}
