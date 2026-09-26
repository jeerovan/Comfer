package com.jeerovan.comfer

import android.content.Intent
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.drawable.ColorDrawable
import android.os.SystemClock
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.Locale

/** Run once under each real system language; no layout-direction or locale injection. */
class AppDrawerLocaleSourceTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()

    @Test fun systemAndInAppLanguageProduceTheSameDrawerGeometry() {
        val systemTag = Resources.getSystem().configuration.locales[0].toLanguageTag()
        val systemLanguage = Locale.forLanguageTag(systemTag).language
        InstrumentationRegistry.getArguments().getString("expectedSystemLanguage")?.let {
            assertEquals("Device system language", it, systemLanguage)
        }
        var previous = ""
        ActivityScenario.launch(GuideActivity::class.java).use { guide ->
            guide.onActivity { previous = AppCompatDelegate.getApplicationLocales().toLanguageTags() }
        }
        try {
            changeLanguage("")
            val inherited = capture(systemTag, "system-$systemLanguage")
            changeLanguage(systemTag)
            val explicit = capture(systemTag, "app-$systemLanguage-on-$systemLanguage")
            assertEquals("Selecting the system language inside the app must preserve geometry", inherited, explicit)

            val opposite = if (systemLanguage == "ar") "en" else "ar"
            changeLanguage(opposite)
            val switched = capture(opposite, "app-$opposite-on-$systemLanguage")
            assertEquals("Language direction must not move the physical drawer path", inherited, switched)

            changeLanguage("")
            assertEquals("Returning to system language", inherited,
                capture(systemTag, "system-$systemLanguage-restored"))
        } finally {
            changeLanguage(previous)
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

    private fun capture(tag: String, name: String): List<List<Float>> {
        val expectedLanguage = Locale.forLanguageTag(tag).language
        val expectedDirection = if (Locale.forLanguageTag(tag).layoutDirection == 1)
            LayoutDirection.Rtl else LayoutDirection.Ltr
        var selected = -1
        var direction: LayoutDirection? = null
        var tapped: String? = null
        val apps = List(50) { index ->
            AppInfo(null, ColorDrawable(android.graphics.Color.HSVToColor(floatArrayOf(index * 37f % 360, .7f, .9f))),
                "Locale fixture $index", 1f, "folder_$index", null, null, null)
        }
        var result = emptyList<List<Float>>()
        ActivityScenario.launch(GuideActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                assertEquals("Activity locale: $name", expectedLanguage,
                    activity.resources.configuration.locales[0].language)
                assertEquals("Activity direction: $name", if (expectedDirection == LayoutDirection.Rtl) 1 else 0,
                    activity.resources.configuration.layoutDirection)
                activity.setContent {
                    val actualDirection = LocalLayoutDirection.current
                    SideEffect { direction = actualDirection }
                    MaterialTheme {
                        Box(Modifier.fillMaxSize().background(Color(0xff142032)).testTag("locale-drawer")) {
                            UshapedAppList(apps, emptyList(), { selected = it }, 0f,
                                48.dp, CircleShape, { _, _, _ -> }, onTappingFolder = { tapped = it })
                        }
                    }
                }
            }
            compose.waitForIdle()
            assertEquals("Inherited Compose direction: $name", expectedDirection, direction)
            val host = compose.onNodeWithTag("locale-drawer").fetchSemanticsNode().boundsInRoot
            val selectedNode = compose.onNodeWithContentDescription("Locale fixture $selected").assertIsDisplayed()
            val center = selectedNode.fetchSemanticsNode().boundsInRoot
            assertEquals("Selected icon centered: $name", host.center.x, center.center.x, .01f)
            selectedNode.performTouchInput { click() }
            compose.runOnIdle { assertEquals("Selected icon hit target: $name", "folder_$selected", tapped) }
            result = compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.ContentDescription))
                .fetchSemanticsNodes().filter {
                    it.config[SemanticsProperties.ContentDescription].any { label -> label.startsWith("Locale fixture ") }
                }.map { it.boundsInRoot }.filter { it.width > 0 && it.height > 0 }
                .map { listOf(it.left - host.left, it.top - host.top, it.width, it.height) }
                .sortedWith(compareBy({ it[1] }, { it[0] }))
            assertTrue("Left side visible: $name", result.any { it[0] + it[2] / 2 < host.width / 3 })
            assertTrue("Right side visible: $name", result.any { it[0] + it[2] / 2 > host.width * 2 / 3 })
            val out = File(instrumentation.targetContext.cacheDir, "drawer-locale-evidence").apply { mkdirs() }
            File(out, "$name.json").writeText(JSONObject().put("locale", tag)
                .put("direction", direction.toString()).put("bounds", JSONArray(result)).toString(2))
            instrumentation.uiAutomation.takeScreenshot()?.let { bitmap ->
                File(out, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                bitmap.recycle()
            }
        }
        return result
    }
}
