package com.jeerovan.comfer

import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.os.SystemClock
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.text.layoutDirection
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.jeerovan.comfer.notifications.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.Locale

class NotificationRuleGuideLocaleSourceTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()

    @Test fun swipeLearnsWithoutDeletingAcrossSystemAndPickerLanguages() = runBlocking {
        val context = instrumentation.targetContext
        val prefs = context.getSharedPreferences(NOTIFICATION_GUIDE_PREFERENCES, Context.MODE_PRIVATE)
        val learned = prefs.getBoolean("RULE_SWIPE", false)
        NotificationPreferences.initialize(context)
        val previousConfig = NotificationPreferences.state.value
        val systemTag = Resources.getSystem().configuration.locales[0].toLanguageTag()
        InstrumentationRegistry.getArguments().getString("expectedSystemLanguage")?.let {
            assertEquals(it, Locale.forLanguageTag(systemTag).language)
        }
        var previousLanguage = ""
        ActivityScenario.launch(GuideActivity::class.java).use { it.onActivity {
            previousLanguage = AppCompatDelegate.getApplicationLocales().toLanguageTags()
        } }
        try {
            NotificationPreferences.update { it.copy(rules = listOf(NotificationRule("locale-rule", "Offers", terms = listOf("sale")))) }
            val opposite = if (Locale.forLanguageTag(systemTag).language == "ar") "en" else "ar"
            for (tag in listOf("", systemTag, opposite, "")) {
                changeLanguage(tag)
                prefs.edit().remove("RULE_SWIPE").commit()
                verifySwipe(tag.ifEmpty { systemTag })
            }
        } finally {
            NotificationPreferences.update { previousConfig }
            prefs.edit().putBoolean("RULE_SWIPE", learned).commit()
            changeLanguage(previousLanguage)
        }
    }

    private fun changeLanguage(tag: String) {
        // Exercise the same trampoline used by the language picker in Settings.
        instrumentation.targetContext.startActivity(
            Intent(instrumentation.targetContext, LanguageUpdateActivity::class.java)
                .putExtra(LanguageUpdateActivity.EXTRA_LOCALE_TAG, tag)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        val deadline = SystemClock.uptimeMillis() + 10000
        do {
            var current = ""
            instrumentation.runOnMainSync {
                current = AppCompatDelegate.getApplicationLocales().toLanguageTags()
            }
            if (current == tag) return
            SystemClock.sleep(50)
        } while (SystemClock.uptimeMillis() < deadline)
        fail("LanguageUpdateActivity did not apply '$tag'")
    }

    private fun verifySwipe(tag: String) {
        val locale = Locale.forLanguageTag(tag)
        var direction: LayoutDirection? = null
        var cancelLabel = ""
        ActivityScenario.launch(GuideActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                assertEquals(locale.language, activity.resources.configuration.locales[0].language)
                cancelLabel = activity.getString(R.string.notification_cancel)
                activity.setContent {
                    val currentDirection = LocalLayoutDirection.current
                    SideEffect { direction = currentDirection }
                    MaterialTheme { Box(Modifier.fillMaxSize().padding(24.dp)) { NotificationRulesSettings {} } }
                }
            }
            compose.waitForIdle()
            assertEquals(if (locale.layoutDirection == 1) LayoutDirection.Rtl else LayoutDirection.Ltr, direction)
            val card = compose.onNodeWithTag("rule-card:locale-rule")
            val bounds = card.fetchSemanticsNode().boundsInRoot
            val guide = compose.onNodeWithTag("notification-guide-RULE_SWIPE").fetchSemanticsNode().boundsInRoot
            assertTrue(guide.left >= bounds.left && guide.right <= bounds.right && guide.top >= bounds.top && guide.bottom <= bounds.bottom)
            card.performTouchInput { if (locale.layoutDirection == 1) swipeRight() else swipeLeft() }
            compose.onNodeWithText(cancelLabel).performClick()
            compose.onNodeWithTag("notification-guide-RULE_SWIPE").assertDoesNotExist()
            card.assertIsDisplayed()
            assertEquals(bounds, card.fetchSemanticsNode().boundsInRoot)
            assertEquals("locale-rule", NotificationPreferences.state.value.rules.single().id)
        }
    }
}
