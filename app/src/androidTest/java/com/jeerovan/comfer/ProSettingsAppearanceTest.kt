package com.jeerovan.comfer

import android.content.Intent
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.Bitmap
import android.os.SystemClock
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.Surface
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.text.layoutDirection
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.jeerovan.comfer.ui.theme.ComferTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.Locale

/** Real resources and app language trampoline, with a component host matching Home's pane.
 * Default Home construction/Back/persistence are covered separately by ProSettingsSystemBackTest.
 * Run under both real system languages, rotations and normal/large system font size.
 */
class ProSettingsAppearanceTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val args get() = InstrumentationRegistry.getArguments()

    private fun changeLanguage(tag: String) {
        context.startActivity(Intent(context, LanguageUpdateActivity::class.java)
            .putExtra(LanguageUpdateActivity.EXTRA_LOCALE_TAG, tag).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        compose.waitUntil(10_000) {
            var current = ""
            instrumentation.runOnMainSync { current = AppCompatDelegate.getApplicationLocales().toLanguageTags() }
            current == tag
        }
    }

    @Test fun themedPanelsStayReadableReachableAndMatchLocaleSources() {
        check(context.packageName.endsWith(".notificationtest"))
        val systemTag = Resources.getSystem().configuration.locales[0].toLanguageTag()
        val systemLanguage = Locale.forLanguageTag(systemTag).language
        assertEquals(args.getString("expectedSystemLanguage"), systemLanguage)
        var previous = ""
        ActivityScenario.launch(GuideActivity::class.java).use { scenario ->
            scenario.onActivity { previous = AppCompatDelegate.getApplicationLocales().toLanguageTags() }
        }
        try {
            changeLanguage("")
            val inherited = capture(systemTag, "system")
            changeLanguage(systemTag)
            assertEquals("Same effective locale must give the same geometry", inherited, capture(systemTag, "app"))
            val opposite = if (systemLanguage == "ar") "en" else "ar"
            changeLanguage(opposite)
            capture(opposite, "opposite")
            changeLanguage("")
            assertEquals("Restoring follow-system restores geometry", inherited, capture(systemTag, "restored"))
        } finally { changeLanguage(previous) }
    }

    @Test fun colorSwatchOpensThemedPickerAndActionsStayReachable() {
        ActivityScenario.launch(GuideActivity::class.java).use { scenario ->
            var dark by mutableStateOf(false)
            var open by mutableStateOf(false)
            var saves = 0
            var savedAlpha = 0
            var savedColor = Color.Transparent
            lateinit var label: String
            lateinit var save: String
            lateinit var cancel: String
            scenario.onActivity { activity ->
                label = activity.getString(R.string.title_color)
                save = activity.getString(R.string.button_text_save)
                cancel = activity.getString(R.string.cancel_text)
                activity.setContent {
                    ComferTheme(darkTheme = dark) {
                        Surface {
                            Box(Modifier.width(320.dp)) {
                                ColorPickerSettingItem(label, Color.Blue.copy(alpha = .35f)) { open = true }
                                if (open) EnhancedColorPicker(listOf(Color.Blue, Color.Red), Color.Blue,
                                    onColorSelected = { color, alpha -> saves++; savedColor = color; savedAlpha = alpha },
                                    onDismissRequest = { open = false }, initialAlpha = 35)
                            }
                        }
                    }
                }
            }
            for (night in listOf(false, true)) {
                compose.runOnIdle { dark = night }
                val before = saves
                compose.onNodeWithText(label).performClick()
                compose.onNode(isDialog()).assertExists()
                screenshot("picker-$night-top")
                revealControl(compose.onNodeWithText(cancel)).performClick()
                compose.onNode(isDialog()).assertDoesNotExist()
                assertEquals("Cancel does not save", before, saves)
                compose.onNodeWithText(label).performClick()
                compose.onNode(SemanticsMatcher("Opacity") {
                    it.config.getOrNull(SemanticsProperties.ProgressBarRangeInfo)?.range == 0f..100f
                }).performScrollTo().assertIsDisplayed().performSemanticsAction(SemanticsActions.SetProgress) { it(74f) }
                revealControl(compose.onNodeWithText(save))
                screenshot("picker-$night-save")
                compose.onNodeWithText(save).performClick()
                compose.onNode(isDialog()).assertDoesNotExist()
                assertEquals(before + 1, saves)
                assertEquals(Color.Blue, savedColor)
                assertEquals(74, savedAlpha)
            }
        }
    }

    private fun revealControl(node: SemanticsNodeInteraction): SemanticsNodeInteraction {
        val minHeight = 48f * context.resources.displayMetrics.density - 1f
        if (!node.isDisplayed() || node.fetchSemanticsNode().boundsInRoot.height < minHeight) node.performScrollTo()
        node.assertIsDisplayed()
        assertTrue("Complete 48dp control is reachable", node.fetchSemanticsNode().boundsInRoot.height >= minHeight)
        return node
    }

    private fun capture(tag: String, source: String): List<Rect> {
        val result = mutableListOf<Rect>()
        val language = Locale.forLanguageTag(tag).language
        val rtl = Locale.forLanguageTag(tag).layoutDirection == 1
        ActivityScenario.launch(GuideActivity::class.java).use { scenario ->
            lateinit var model: SettingsViewModel
            lateinit var back: String
            var statusTop = 0
            var landscape = false
            var screen by mutableStateOf(0)
            var dark by mutableStateOf(false)
            scenario.onActivity { activity ->
                assertEquals(language, activity.resources.configuration.locales[0].language)
                model = ViewModelProvider(activity)[SettingsViewModel::class.java]
                back = activity.getString(R.string.icon_go_back)
                activity.setContent {
                    assertEquals(if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr, LocalLayoutDirection.current)
                    val insetTop = WindowInsets.statusBars.getTop(LocalDensity.current)
                    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
                    SideEffect { statusTop = insetTop; landscape = isLandscape }
                    ComferTheme(darkTheme = dark) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                            Box(Modifier.width(360.dp).heightIn(max = 400.dp).fillMaxHeight()) {
                                key(screen, dark) {
                                    when (screen) {
                                        0 -> BasicSettings(model, {}, {}, {})
                                        1 -> TimeAdvancedSettings(model, {})
                                        else -> DateAdvancedSettings(model, {})
                                    }
                                }
                            }
                        }
                    }
                }
            }
            for (night in listOf(false, true)) for (page in 0..2) {
                compose.runOnIdle { dark = night; screen = page }
                val panel = compose.onNodeWithTag("pro-settings-panel").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
                val title = compose.onNodeWithTag("pro-panel-title").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
                val backBounds = compose.onNodeWithContentDescription(back).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
                val density = context.resources.displayMetrics.density
                assertTrue("Back target at least 48dp", backBounds.width >= 48 * density - 1 && backBounds.height >= 48 * density - 1)
                if (landscape) assertTrue("Back must be below visible status bar: ${backBounds.top} < $statusTop", backBounds.top >= statusTop)
                assertTrue("Directional header", if (rtl) backBounds.left >= title.right else backBounds.right <= title.left)
                result += title; result += backBounds
                screenshot("$source-$language-$night-$page-top")
                if (page == 0) {
                    // Every label remains laid out; scroll each to expose clipping hidden below the fold.
                    val texts = compose.onAllNodes(SemanticsMatcher("Text layout") {
                        it.config.contains(SemanticsActions.GetTextLayoutResult)
                    } and hasAnyAncestor(hasTestTag("pro-settings-scroll")), useUnmergedTree = true)
                    val count = texts.fetchSemanticsNodes().size
                    for (index in 0 until if (source == "system" || source == "opposite") count else 0) {
                        val node = texts[index]
                        node.performScrollTo()
                        val layouts = mutableListOf<TextLayoutResult>()
                        node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
                        // Compose may retain a paragraph's available width while measuring the
                        // Text to its intrinsic width (608px allocation, 196px label). Compare
                        // rendered line extents and completeness, not that allocation flag.
                        fun fits(layout: TextLayoutResult): Boolean =
                            !layout.didOverflowHeight &&
                                (0 until layout.lineCount).all { line ->
                                    !layout.isLineEllipsized(line) &&
                                        layout.getLineRight(line) - layout.getLineLeft(line) <= layout.size.width + 1f
                                } && layout.getLineEnd(layout.lineCount - 1) == layout.layoutInput.text.length
                        if (layouts.any { !fits(it) }) screenshot("overflow-$language-$night-$page")
                        assertTrue("Text lines fit and are complete: ${layouts.map { it.layoutInput.text.text }}",
                            layouts.all { fits(it) })
                        val bounds = node.fetchSemanticsNode().boundsInRoot
                        assertTrue("Text within panel", bounds.left >= panel.left && bounds.right <= panel.right)
                    }
                    val reset = compose.onNodeWithText(context.createConfigurationContext(
                        Configuration(context.resources.configuration).apply { setLocale(Locale.forLanguageTag(tag)) }
                    ).getString(R.string.rest_widget_positions)).performScrollTo().assertIsDisplayed()
                    assertTrue("Final action reachable", reset.fetchSemanticsNode().boundsInRoot.bottom <= panel.bottom)
                } else {
                    val original = if (page == 1) model.uiState.value.timeLayoutId else model.uiState.value.dateLayoutId
                    try {
                        for (id in 1..if (page == 1) 3 else 2) {
                            val option = compose.onNodeWithTag("pro-layout-$id").performScrollTo().assertIsDisplayed()
                            option.performClick().assertIsSelected()
                            compose.waitUntil(5000) { (if (page == 1) model.uiState.value.timeLayoutId else model.uiState.value.dateLayoutId) == id }
                            val bounds = option.fetchSemanticsNode().boundsInRoot
                            assertTrue("Preview inside pane", bounds.left >= panel.left && bounds.right <= panel.right)
                        }
                        val radius = compose.onNode(SemanticsMatcher("Radius slider") {
                            it.config.getOrNull(SemanticsProperties.ProgressBarRangeInfo)?.range == -250f..250f
                        }).performScrollTo().assertIsDisplayed()
                        assertTrue(radius.fetchSemanticsNode().boundsInRoot.bottom <= panel.bottom)
                    } finally {
                        scenario.onActivity { if (page == 1) model.setTimeLayoutId(original) else model.setDateLayoutId(original) }
                    }
                }
                screenshot("$source-$language-$night-$page-bottom")
            }
        }
        return result
    }

    private fun screenshot(name: String) {
        compose.waitForIdle()
        val out = File(context.cacheDir, "pro-ui-evidence").apply { mkdirs() }
        instrumentation.uiAutomation.takeScreenshot()?.let { bitmap ->
            File(out, "${args.getString("capturePrefix") ?: "check"}-$name.png").outputStream().use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            bitmap.recycle()
        }
    }
}
