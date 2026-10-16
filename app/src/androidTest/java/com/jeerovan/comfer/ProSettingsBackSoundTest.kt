package com.jeerovan.comfer

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.view.SoundEffectConstants
import android.view.View
import android.view.ViewGroup
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalSoundEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.SoundEffect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ProSettingsBackSoundTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun homeAndAdvancedBackButtonsPlayOnceAndCancelledPressesStaySilent() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName.endsWith(".notificationtest"))
        StartupCoordinator.awaitReady()
        val seed = (HomeGuideStep.entries.map { it.preferenceKey } + PreferenceManager.FEEDBACK_DIALOG)
            .associateWith { "true" } + mapOf("analog_clock" to "false", "custom_widgets" to "false")
        val previous = seed.keys.associateWith { PreferenceManager.getString(context, it, null) }
        suspend fun awaitValues(expected: Map<String, String?>) = withTimeout(10_000) {
            context.settingsDataStore.data.first { preferences ->
                val values = preferences.asMap().entries.associate { it.key.name to it.value.toString() }
                expected.all { (key, value) -> values[key] == value }
            }
        }
        try {
            seed.forEach { (key, value) -> PreferenceManager.setString(context, key, value) }
            awaitValues(seed)
            PreferenceManager.reload(context)
            val sounds = mutableListOf<Int>()
            var backLabel = ""
            var advancedLabel = ""
            var density = 1f
            ActivityScenario.launch(GuideActivity::class.java).use { scenario ->
                scenario.onActivity { activity ->
                    val model = ViewModelProvider(activity)[SettingsViewModel::class.java]
                    val manager = AppWidgetManager.getInstance(activity)
                    val host = AppWidgetHost(activity, 9157)
                    backLabel = activity.getString(R.string.icon_go_back)
                    advancedLabel = activity.getString(R.string.title_advanced)
                    density = activity.resources.displayMetrics.density
                    activity.setContent {
                        val hostView = LocalView.current
                        val soundView = remember(hostView) {
                            object : View(activity) {
                                override fun playSoundEffect(effect: Int) {
                                    sounds += effect
                                    hostView.playSoundEffect(effect)
                                }
                            }
                        }
                        DisposableEffect(soundView) {
                            val parent = activity.findViewById<ViewGroup>(android.R.id.content)
                            parent.addView(soundView, ViewGroup.LayoutParams(0, 0))
                            onDispose { parent.removeView(soundView) }
                        }
                        val feedback = remember(soundView) {
                            object : SoundEffect {
                                override fun playClickSound() {
                                    soundView.playSoundEffect(SoundEffectConstants.CLICK)
                                }
                            }
                        }
                        CompositionLocalProvider(LocalView provides soundView, LocalSoundEffect provides feedback) {
                            MaterialTheme {
                                Box(Modifier.fillMaxSize().testTag("pro-sound-home")) {
                                    QuickListOverlay(emptyList(), emptyMap(), manager, host, emptyList(), model,
                                        onSwipeUp = {}, onSwipeRight = {}, onSwipeLeft = {},
                                        onShowSearch = {}, onDoubleTap = {})
                                }
                            }
                        }
                    }
                }
                fun openSettings() {
                    compose.onNodeWithTag("pro-sound-home").performTouchInput {
                        // Blank space at the top of the built-in widget pane.
                        longClick(Offset(center.x, 36f * density))
                    }
                    compose.onNodeWithContentDescription(backLabel).assertIsDisplayed()
                }
                fun back(toHome: Boolean) {
                    compose.runOnIdle { sounds.clear() }
                    val button = compose.onNodeWithContentDescription(backLabel).assertIsDisplayed()
                    button.performTouchInput { down(center); cancel() }
                    compose.runOnIdle { assertEquals("Cancelled back press stays silent", emptyList<Int>(), sounds) }
                    button.assertIsDisplayed().performTouchInput { click() }
                    compose.runOnIdle {
                        assertEquals("One click sound per completed back tap", listOf(SoundEffectConstants.CLICK), sounds)
                    }
                    if (toHome) compose.onNodeWithContentDescription(backLabel).assertDoesNotExist()
                    else compose.onAllNodesWithText(advancedLabel).assertCountEquals(2)
                }
                repeat(2) {
                    openSettings()
                    back(toHome = true)
                    openSettings()
                    for (index in 0..1) {
                        compose.onAllNodesWithText(advancedLabel)[index].performScrollTo()
                            .performTouchInput { click() }
                        back(toHome = false)
                    }
                    back(toHome = true)
                }
            }
        } finally {
            previous.forEach { (key, value) -> PreferenceManager.setString(context, key, value) }
            awaitValues(previous)
        }
    }
}
