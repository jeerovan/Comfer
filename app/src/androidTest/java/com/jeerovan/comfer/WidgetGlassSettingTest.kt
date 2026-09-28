package com.jeerovan.comfer

import android.app.Application
import android.content.res.Configuration
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.util.Locale

@RunWith(Parameterized::class)
class WidgetGlassSettingTest(private val direction: LayoutDirection, private val automaticWallpaper: Boolean) {
    @get:Rule val compose = createComposeRule()
    companion object {
        @JvmStatic @Parameterized.Parameters(name = "{0}, automaticWallpaper={1}")
        fun cases() = listOf(LayoutDirection.Ltr, LayoutDirection.Rtl).flatMap { direction ->
            listOf(true, false).map { arrayOf(direction, it) }
        }
    }

    @Test fun colorSwitchTogglesIndependentlyAndSurvivesViewModelReload() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName.endsWith(".notificationtest"))
        StartupCoordinator.awaitReady()
        val keys = listOf("widget_glass_effect", "auto_wallpaper", "show_themed_text")
        val previous = keys.associateWith { PreferenceManager.getString(context, it, null) }
        val stores = mutableListOf<ViewModelStore>()
        fun createModel(): SettingsViewModel {
            val store = ViewModelStore().also { stores += it }
            return ViewModelProvider(store,
                ViewModelProvider.AndroidViewModelFactory(context.applicationContext as Application))[SettingsViewModel::class.java]
        }
        try {
            PreferenceManager.setBoolean(context, "widget_glass_effect", false)
            PreferenceManager.setBoolean(context, "auto_wallpaper", automaticWallpaper)
            PreferenceManager.setBoolean(context, "show_themed_text", true)
            lateinit var model: SettingsViewModel
            compose.runOnUiThread { model = createModel() }
            compose.waitUntil(10000) { !model.uiState.value.widgetGlassEffect }
            val locale = Locale.forLanguageTag(if (direction == LayoutDirection.Rtl) "ar" else "en")
            val configuration = Configuration(context.resources.configuration).apply { setLocale(locale) }
            val localized = context.createConfigurationContext(configuration)
            val label = localized.getString(R.string.title_glass_effect)
            val wallpaperLabel = localized.getString(R.string.title_wallpaper_colors)
            compose.setContent {
                CompositionLocalProvider(LocalContext provides localized, LocalConfiguration provides configuration,
                    LocalLayoutDirection provides direction) {
                    MaterialTheme { BasicSettings(model, {}, {}, {}) }
                }
            }
            val row = compose.onNodeWithText(label).performScrollTo().assertIsDisplayed()
            val toggle = compose.onNode(isToggleable() and hasAnyAncestor(hasText(label)))
            toggle.assertIsOff()
            if (automaticWallpaper) compose.onNodeWithText(wallpaperLabel).assertIsDisplayed()
            else compose.onNodeWithText(wallpaperLabel).assertDoesNotExist()
            val originalBounds = row.fetchSemanticsNode().boundsInRoot
            val switchBounds = toggle.fetchSemanticsNode().boundsInRoot
            assertTrue("Switch inside row", originalBounds.contains(switchBounds.center))
            assertTrue("Switch on trailing side", if (direction == LayoutDirection.Ltr)
                switchBounds.center.x > originalBounds.center.x else switchBounds.center.x < originalBounds.center.x)
            // Both the label row and the switch must be usable without changing wallpaper colors.
            row.performTouchInput { click() }
            toggle.assertIsOn()
            toggle.performClick().assertIsOff()
            row.performClick()
            toggle.assertIsOn()
            assertEquals("Stable placement", originalBounds, row.fetchSemanticsNode().boundsInRoot)
            assertTrue(model.uiState.value.showThemedText)
            withTimeout(5000) {
                context.settingsDataStore.data.first { prefs ->
                    prefs.asMap().any { it.key.name == "widget_glass_effect" && it.value.toString() == "true" }
                }
            }
            PreferenceManager.reload(context)
            lateinit var restored: SettingsViewModel
            compose.runOnUiThread { restored = createModel() }
            compose.waitUntil(10000) { restored.uiState.value.showThemedText }
            assertTrue(restored.uiState.value.widgetGlassEffect)
            compose.runOnUiThread { restored.setWidgetGlassEffect(false) }
            withTimeout(5000) {
                context.settingsDataStore.data.first { prefs ->
                    prefs.asMap().any { it.key.name == "widget_glass_effect" && it.value.toString() == "false" }
                }
            }
            PreferenceManager.reload(context)
            compose.runOnUiThread { restored = createModel() }
            compose.waitUntil(10000) { !restored.uiState.value.widgetGlassEffect }
        } finally {
            compose.runOnUiThread { stores.forEach { it.clear() } }
            previous.forEach { (key, value) -> PreferenceManager.setString(context, key, value) }
        }
    }
}
