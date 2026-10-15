package com.jeerovan.comfer

import android.view.SoundEffectConstants
import android.view.View
import android.view.ViewGroup
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoundEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.SoundEffect
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ActivityScenario
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Shared icon feedback; actual Workspace menu transitions live in [WorkspaceCenterSoundTest]. */
class SearchIconSoundTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val folderActive = mutableStateOf(false)
    private var clicks = 0
    private val sounds = mutableListOf<Int>()

    @Test fun tapsPlayOneSoundAndInvokeOneActionForSearchAndFolderClose() = withIcon {
        var expected = 0
        for (isFolder in listOf(false, true)) {
            compose.runOnIdle { folderActive.value = isFolder }
            repeat(3) {
                compose.onNodeWithTag("home-search-button").performTouchInput { click() }
                assertFeedback(++expected)
            }
        }
    }

    @Test fun cancelledPressAndDragStaySilentAndAllowTheNextTap() = withIcon {
        val button = compose.onNodeWithTag("home-search-button")
        button.performTouchInput { down(center); cancel() }
        assertFeedback(0)
        button.performTouchInput { swipe(center, center.copy(y = center.y - 150f), 300) }
        assertFeedback(0)
        button.performTouchInput { click() }
        assertFeedback(1)
    }

    private fun assertFeedback(expected: Int) = compose.runOnIdle {
        assertEquals("Exactly one action per completed tap", expected, clicks)
        assertEquals("Exactly one click sound per completed tap",
            List(expected) { SoundEffectConstants.CLICK }, sounds)
    }

    private fun withIcon(assertions: () -> Unit) {
        ActivityScenario.launch(GuideActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                check(activity.packageName.endsWith(".notificationtest")) {
                    "Run against the isolated notificationTest application"
                }
                activity.setContent {
                    val hostView = LocalView.current
                    // Observe both Compose's automatic feedback and a direct View sound call.
                    // Forward to the real host so the Samsung sound path is still exercised.
                    val soundView = remember(hostView) {
                        object : View(activity) {
                            override fun playSoundEffect(effect: Int) {
                                sounds += effect
                                hostView.playSoundEffect(effect)
                            }
                        }
                    }
                    DisposableEffect(soundView) {
                        // Native ripples require LocalView to have an attached parent.
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
                    CompositionLocalProvider(
                        LocalView provides soundView,
                        LocalSoundEffect provides feedback,
                    ) {
                        MaterialTheme {
                            Box(Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center) {
                                SearchIcon(
                                    48.dp, CircleShape, { clicks++ }, false, null, false,
                                    isFolderActive = folderActive.value,
                                )
                            }
                        }
                    }
                }
            }
            compose.waitForIdle()
            compose.onNodeWithTag("home-search-button").assertIsDisplayed()
            assertions()
        }
    }
}
