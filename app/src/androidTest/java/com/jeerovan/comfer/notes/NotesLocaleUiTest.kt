package com.jeerovan.comfer.notes

import android.app.Application
import android.content.res.Configuration
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.platform.app.InstrumentationRegistry
import com.jeerovan.comfer.R
import org.junit.Rule
import org.junit.Test
import java.util.Locale

class NotesLocaleUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun lockedScreenAndRetainedFailureFollowResourceChanges() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val model = NotesViewModel(context.applicationContext as Application)
        model.error = "Could not copy this draft. Please retry."
        var tag by mutableStateOf("de")
        fun resources(language: String) = context.createConfigurationContext(
            Configuration(context.resources.configuration).apply { setLocale(Locale.forLanguageTag(language)) }
        ).resources
        compose.setContent {
            CompositionLocalProvider(
                LocalResources provides resources(tag),
                LocalLayoutDirection provides if (tag == "ar") LayoutDirection.Rtl else LayoutDirection.Ltr,
            ) {
                MaterialTheme { NotesScreen(model, {}, {}, locked = true) }
            }
        }
        for (language in listOf("de", "ar")) {
            compose.runOnIdle { tag = language }
            val local = resources(language)
            compose.onNodeWithText(local.getString(R.string.module_notes_is_locked)).assertIsDisplayed()
            compose.onNodeWithText(local.getString(R.string.module_could_not_copy_this_draft_please_retry)).assertIsDisplayed()
            compose.onNodeWithText(local.getString(R.string.module_unlock)).assertIsDisplayed()
            compose.onNodeWithText("Could not copy this draft. Please retry.").assertDoesNotExist()
            capture("locked-$language")
        }
    }

    @Test fun formattingSheetFollowsLanguageWhileOpen() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val model = NotesViewModel(context.applicationContext as Application)
        var tag by mutableStateOf("de")
        fun resources(language: String) = context.createConfigurationContext(
            Configuration(context.resources.configuration).apply { setLocale(Locale.forLanguageTag(language)) }
        ).resources
        compose.setContent {
            CompositionLocalProvider(
                LocalResources provides resources(tag),
                LocalLayoutDirection provides if (tag == "ar") LayoutDirection.Rtl else LayoutDirection.Ltr,
            ) {
                MaterialTheme { NotesEditorToolbar(model, {}, {}) }
            }
        }
        compose.onNodeWithContentDescription(resources("de").getString(R.string.module_text_formatting)).performClick()
        for (language in listOf("de", "ar")) {
            compose.runOnIdle { tag = language }
            val local = resources(language)
            compose.onNodeWithText(local.getString(R.string.module_body)).assertIsDisplayed()
            compose.onNodeWithContentDescription(local.getString(R.string.module_underline)).assertIsDisplayed()
            capture("formatting-$language")
        }
    }

    private fun capture(name: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        compose.waitForIdle()
        // Native window entrance transitions are outside Compose's test clock.
        android.os.SystemClock.sleep(350)
        val bitmap = requireNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        java.io.File(context.cacheDir, "notes-locale-$name.png").outputStream().use {
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
    }
}
