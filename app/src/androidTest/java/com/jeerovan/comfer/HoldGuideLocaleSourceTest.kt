package com.jeerovan.comfer

import android.content.Intent
import android.content.res.Resources
import android.os.SystemClock
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.text.layoutDirection
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.jeerovan.comfer.tasks.TaskGestureGuide
import com.jeerovan.comfer.tasks.TaskGuide
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.Locale

class HoldGuideLocaleSourceTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()

    @Test fun systemAndPickerLanguagesKeepHoldAndDragGuidesAligned() {
        val systemTag = Resources.getSystem().configuration.locales[0].toLanguageTag()
        InstrumentationRegistry.getArguments().getString("expectedSystemLanguage")?.let {
            assertEquals(it, Locale.forLanguageTag(systemTag).language)
        }
        var previous = ""
        ActivityScenario.launch(GuideActivity::class.java).use { it.onActivity {
            previous = AppCompatDelegate.getApplicationLocales().toLanguageTags()
        } }
        compose.mainClock.autoAdvance = false
        try {
            changeLanguage("")
            val inherited = capture(systemTag, "system")
            changeLanguage(systemTag)
            assertEquals(inherited, capture(systemTag, "picker"))
            val opposite = if (Locale.forLanguageTag(systemTag).language == "ar") "en" else "ar"
            changeLanguage(opposite)
            assertEquals(inherited, capture(opposite, "switched"))
            changeLanguage("")
            assertEquals(inherited, capture(systemTag, "restored"))
        } finally { changeLanguage(previous) }
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

    private fun capture(tag: String, source: String): List<Float> {
        val locale = Locale.forLanguageTag(tag)
        val expectedDirection = if (locale.layoutDirection == 1) LayoutDirection.Rtl else LayoutDirection.Ltr
        var direction: LayoutDirection? = null
        var geometry = emptyList<Float>()
        ActivityScenario.launch(GuideActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                assertEquals(locale.language, activity.resources.configuration.locales[0].language)
                activity.setContent {
                    val currentDirection = LocalLayoutDirection.current
                    SideEffect { direction = currentDirection }
                    MaterialTheme { Column(Modifier.fillMaxWidth().padding(top = 64.dp)) {
                        Box(Modifier.fillMaxWidth().height(90.dp).testTag("home-area"), contentAlignment = Alignment.TopCenter) {
                            LongPressHint(Modifier.testTag("home-guide"))
                        }
                        Box(Modifier.fillMaxWidth().height(240.dp).testTag("task-area"), contentAlignment = Alignment.TopCenter) {
                            TaskGestureGuide(TaskGuide.REORDER, 120f, onShown = {})
                        }
                    } }
                }
            }
            compose.mainClock.advanceTimeBy(48)
            compose.waitForIdle()
            assertEquals(expectedDirection, direction)
            compose.mainClock.advanceTimeBy(300)
            val home = compose.onNodeWithTag("home-guide").fetchSemanticsNode().boundsInRoot
            val hold = compose.onNodeWithTag("tasks-guide-reorder").fetchSemanticsNode().boundsInRoot
            val area = compose.onNodeWithTag("task-area").fetchSemanticsNode().boundsInRoot
            assertEquals(area.top, hold.top, 1f)
            assertEquals(area.center.x, hold.center.x, 1f)
            compose.onNodeWithTag("tasks-guide-reorder").assertWidthIsEqualTo(48.dp).assertHeightIsEqualTo(48.dp)
            captureImage("hold-$tag-$source")
            compose.mainClock.advanceTimeBy(900)
            val drag = compose.onNodeWithTag("tasks-guide-reorder").fetchSemanticsNode().boundsInRoot
            assertTrue(drag.top > hold.top)
            assertTrue(drag.bottom <= area.bottom)
            val ring = compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo) and
                hasAnyAncestor(hasTestTag("tasks-guide-reorder")), useUnmergedTree = true).fetchSemanticsNode()
            assertEquals(1f, ring.config[SemanticsProperties.ProgressBarRangeInfo].current, .01f)
            captureImage("drag-$tag-$source")
            geometry = listOf(home.width, home.height, hold.left, hold.top - area.top, hold.width, hold.height)
        }
        return geometry
    }

    private fun captureImage(name: String) {
        compose.waitForIdle()
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        java.io.File(instrumentation.targetContext.getExternalFilesDir(null), "$name.png").outputStream().use {
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
    }
}
