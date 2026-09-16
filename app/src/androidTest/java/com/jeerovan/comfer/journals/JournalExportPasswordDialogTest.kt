package com.jeerovan.comfer.journals

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class JournalExportPasswordDialogTest {
    @get:Rule val compose = createComposeRule()

    @Test fun validatesOnlyOnSubmitAndAcceptsFourMatchingCharacters() {
        var accepted: String? = null
        compose.setContent { MaterialTheme { JournalExportPasswordDialog({}, { accepted = it }) } }
        val error = "Password must contain at least 4 characters"
        compose.onNodeWithText(error).assertDoesNotExist()
        val submit = compose.onNodeWithText("Backup", ignoreCase = true)
        submit.assertIsEnabled().performClick()
        compose.onNodeWithText(error).assertIsDisplayed()
        val password = compose.onNodeWithText("Journal export password", substring = false)
        val confirmation = compose.onNodeWithText("Repeat password")
        password.performTextInput("123")
        confirmation.performTextInput("123")
        compose.onNodeWithText(error).assertDoesNotExist()
        submit.performClick()
        compose.onNodeWithText(error).assertIsDisplayed()
        compose.runOnIdle { assertNull(accepted) }
        password.performTextReplacement("1234")
        submit.performClick()
        compose.onNodeWithText("Passwords do not match").assertIsDisplayed()
        confirmation.performTextReplacement("1234")
        submit.performClick()
        compose.runOnIdle { assertEquals("1234", accepted) }
    }
}
