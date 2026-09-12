package com.jeerovan.comfer

import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.platform.app.InstrumentationRegistry
import com.jeerovan.comfer.notifications.NotificationPreferences
import com.jeerovan.comfer.notifications.NotificationRuleEditor
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.Locale
import java.io.File

class LocalizedResourcesTest {
    @get:Rule val compose = createComposeRule()
    private val locales = listOf("ar", "az", "bn", "de", "el", "es", "fa", "fr", "he", "hi", "id", "it", "ja", "km", "ko", "lo", "mn", "mr", "my", "nl", "pt", "ro", "ru", "ta", "te", "tg", "th", "tk", "tr", "uk", "ur", "uz", "vi", "zh-CN")
    private fun localized(tag: String) = InstrumentationRegistry.getInstrumentation().targetContext.let { context ->
        context.createConfigurationContext(Configuration(context.resources.configuration).apply { setLocale(Locale.forLanguageTag(tag)) })
    }

    @Test fun everySupportedLocaleLoadsInboxGuideAndFormattedResults() {
        val english = localized("en")
        for (tag in locales) {
            val context = localized(tag)
            assertNotEquals("$tag Inbox must not fall back to English", english.getString(R.string.notification_inbox), context.getString(R.string.notification_inbox))
            assertNotEquals("$tag gesture guide must be translated", english.getString(R.string.guide_home_inbox_gesture), context.getString(R.string.guide_home_inbox_gesture))
            val guide = localizedGuideBody(context.resources, R.string.app_guide_tabs_body)
            assertTrue("$tag guide must use the Saved tab label", guide.contains(context.getString(R.string.notification_saved)))
            for (body in listOf(
                R.string.app_guide_start_body, R.string.app_guide_tabs_body,
                R.string.app_guide_gestures_body, R.string.app_guide_actions_body,
                R.string.app_guide_dismiss_body, R.string.app_guide_more_body,
                R.string.app_guide_settings_body, R.string.app_guide_quiet_body,
                R.string.app_guide_privacy_body, R.string.app_guide_howto_open_body,
                R.string.app_guide_howto_mute_body, R.string.app_guide_howto_focus_body,
                R.string.app_guide_howto_clear_body, R.string.app_guide_howto_recover_body,
            )) {
                assertFalse("$tag guide has unresolved labels", Regex("%[0-9]+\\\$s").containsMatchIn(localizedGuideBody(context.resources, body)))
            }
            val name = "Example App"
            assertTrue(context.getString(R.string.notification_enable_named_rule, name).contains(name))
            assertTrue(context.getString(R.string.notification_rule_preview_count, 2, 5).isNotBlank())
            assertTrue(context.getString(R.string.notification_rule_preview_item, name, "User content", "Match", "Reason").contains("User content"))
        }
    }

    @Test fun translatedRuleEditorRemainsUsableInRtlAndLongTextLocales() {
        var tag by mutableStateOf("de")
        NotificationPreferences.initialize(InstrumentationRegistry.getInstrumentation().targetContext)
        compose.setContent {
            val context = remember(tag) { localized(tag) }
            CompositionLocalProvider(
                LocalContext provides context,
                LocalConfiguration provides context.resources.configuration,
                LocalLayoutDirection provides if (tag == "ar") LayoutDirection.Rtl else LayoutDirection.Ltr,
            ) {
                key(tag) {
                    MaterialTheme {
                        Column(Modifier.verticalScroll(rememberScrollState())) {
                            NotificationRuleEditor(null, onSaved = {})
                        }
                    }
                }
            }
        }
        for (language in listOf("de", "ar", "hi", "ja")) {
            compose.runOnIdle { tag = language }
            val context = localized(language)
            compose.onNodeWithText(context.getString(R.string.ui_contains_text_one_phrase_per_line)).performScrollTo().performTextInput("example")
            androidx.test.espresso.Espresso.closeSoftKeyboard()
            compose.onNodeWithText(context.getString(R.string.ui_preview_current_notifications)).performScrollTo().performClick()
            compose.onNodeWithText(context.getString(R.string.ui_save_in_test_only_mode)).performScrollTo().assertIsDisplayed().assertIsEnabled()
            compose.waitForIdle()
            InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()?.let { bitmap ->
                File(context.cacheDir, "translation-$language.png").outputStream().use {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                }
                bitmap.recycle()
            }
        }
    }
}
