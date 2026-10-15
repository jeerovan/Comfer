package com.jeerovan.comfer

import android.content.Intent
import android.content.res.Resources
import android.os.SystemClock
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.text.layoutDirection
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.Locale

/** Release layout validation: run under real English/Arabic system locales and supported orientations. */
class HomeCenterLocaleSourceTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()

    @Test fun systemAndPickerLanguagesPreserveCenterGeometry() {
        val systemTag = Resources.getSystem().configuration.locales[0].toLanguageTag()
        val language = Locale.forLanguageTag(systemTag).language
        InstrumentationRegistry.getArguments().getString("expectedSystemLanguage")?.let {
            assertEquals("Device system language", it, language)
        }
        var previous = ""
        ActivityScenario.launch(GuideActivity::class.java).use { scenario ->
            scenario.onActivity { previous = AppCompatDelegate.getApplicationLocales().toLanguageTags() }
        }
        try {
            changeLanguage("")
            val inherited = listOf(true, false).map { capture(it, systemTag) }
            changeLanguage(systemTag)
            assertEquals(inherited, listOf(true, false).map { capture(it, systemTag) })
            val opposite = if (language == "ar") "en" else "ar"
            changeLanguage(opposite)
            assertEquals(inherited, listOf(true, false).map { capture(it, opposite) })
            changeLanguage("")
            assertEquals(inherited, listOf(true, false).map { capture(it, systemTag) })
        } finally {
            changeLanguage(previous)
        }
    }

    private fun capture(circular: Boolean, tag: String): Rect {
        val locale = Locale.forLanguageTag(tag)
        val expectedDirection = if (locale.layoutDirection == 1) LayoutDirection.Rtl else LayoutDirection.Ltr
        val folderActive = mutableStateOf(false)
        val workspaceCenter = mutableStateOf(false)
        var direction: LayoutDirection? = null
        var bounds = Rect.Zero
        var descriptions = emptyList<String>()
        ActivityScenario.launch(GuideActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                check(activity.packageName.endsWith(".notificationtest"))
                assertEquals(locale.language, activity.resources.configuration.locales[0].language)
                assertEquals(locale.layoutDirection, activity.resources.configuration.layoutDirection)
                descriptions = listOf(R.string.search, R.string.home_workspace, R.string.close).map(activity::getString)
                activity.setContent {
                    val currentDirection = LocalLayoutDirection.current
                    SideEffect { direction = currentDirection }
                    MaterialTheme {
                        Box(Modifier.fillMaxSize().testTag("home-center-host"), contentAlignment = Alignment.Center) {
                            if (circular) CircularLayout(
                                emptyList(), emptyList(), 48.dp, CircleShape, {}, false, null, false,
                                isFolderActive = folderActive.value, showGestureGuide = false,
                                workspaceCenter = workspaceCenter.value,
                            ) else FiveColumnLayout(
                                emptyList(), emptyList(), 48.dp, CircleShape, {}, false, null, false,
                                isFolderActive = folderActive.value, workspaceCenter = workspaceCenter.value,
                            )
                        }
                    }
                }
            }
            compose.waitForIdle()
            assertEquals(expectedDirection, direction)
            val host = compose.onNodeWithTag("home-center-host").fetchSemanticsNode().boundsInRoot
            val button = compose.onNodeWithTag("home-search-button").assertIsDisplayed()
            bounds = button.fetchSemanticsNode().boundsInRoot
            assertEquals("Physical horizontal center", host.center.x, bounds.center.x, 1f)
            assertEquals("Physical vertical center", host.center.y, bounds.center.y, 1f)
            assertTrue("Visible target fits host", host.contains(bounds.topLeft) && host.contains(bounds.bottomRight))
            assertTrue("Nonempty target", bounds.width > 0 && bounds.height > 0)
            for (mode in 0..2) {
                compose.runOnIdle {
                    workspaceCenter.value = mode == 1
                    folderActive.value = mode == 2
                }
                button.assertIsDisplayed().assertContentDescriptionEquals(descriptions[mode])
                assertEquals("Changing the icon preserves its target", bounds,
                    button.fetchSemanticsNode().boundsInRoot)
            }
        }
        return bounds
    }

    private fun changeLanguage(tag: String) {
        instrumentation.targetContext.startActivity(
            Intent(instrumentation.targetContext, LanguageUpdateActivity::class.java)
                .putExtra(LanguageUpdateActivity.EXTRA_LOCALE_TAG, tag)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        val deadline = SystemClock.uptimeMillis() + 10000
        do {
            var current = ""
            instrumentation.runOnMainSync { current = AppCompatDelegate.getApplicationLocales().toLanguageTags() }
            if (current == tag) return
            SystemClock.sleep(50)
        } while (SystemClock.uptimeMillis() < deadline)
        fail("Language picker did not apply '$tag'")
    }
}
