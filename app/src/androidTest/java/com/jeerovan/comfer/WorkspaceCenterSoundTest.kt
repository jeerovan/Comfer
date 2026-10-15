package com.jeerovan.comfer

import android.view.SoundEffectConstants
import android.view.View
import android.view.ViewGroup
import androidx.activity.compose.setContent
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
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

/** Exercise the three-circle Workspace control through its actual menu state changes. */
class WorkspaceCenterSoundTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun circularWorkspacePlaysOnceWhileOpeningClosingAndReversing() = verify(true)
    @Test fun columnsWorkspacePlaysOnceWhileOpeningClosingAndReversing() = verify(false)

    private fun verify(circular: Boolean) {
        var opened by mutableStateOf(false)
        var actions = 0
        var parentActions = 0
        val sounds = mutableListOf<Int>()
        var workspaceDescription = ""
        var closeDescription = ""
        ActivityScenario.launch(GuideActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                check(activity.packageName.endsWith(".notificationtest"))
                workspaceDescription = activity.getString(R.string.home_workspace)
                closeDescription = activity.getString(R.string.close)
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
                        // Keep the real native ripple path available while observing View calls.
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
                            Box(Modifier.fillMaxSize()
                                .pointerInput(Unit) {
                                    detectTapGestures(onDoubleTap = { parentActions++ },
                                        onLongPress = { parentActions++ })
                                }
                                .detectGestures(onSwipeUp = { parentActions++ }),
                                contentAlignment = Alignment.Center) {
                                HomeFolderLayout(
                                    emptyList(), emptyMap(), null, circular, emptyList(), 48.dp,
                                    CircleShape, { actions++; opened = !opened },
                                    false, null, false, {}, workspaceOpen = opened,
                                )
                            }
                        }
                    }
                }
            }
            compose.waitForIdle()
            compose.mainClock.autoAdvance = false
            fun expect(count: Int, expanded: Boolean) {
                compose.runOnIdle {
                    assertEquals("One state change per center tap", count, actions)
                    assertEquals("One sound per center tap, including menu transitions", count, sounds.size)
                    assertTrue(sounds.all { it == SoundEffectConstants.CLICK })
                    assertEquals(expanded, opened)
                }
                compose.onAllNodesWithTag("home-search-button").assertCountEquals(1)
                compose.onNodeWithTag("home-search-button")
                    .assertContentDescriptionEquals(if (expanded) closeDescription else workspaceDescription)
            }
            fun tap(count: Int, expanded: Boolean, settleMillis: Long = 400) {
                compose.onNodeWithTag("home-search-button").performTouchInput { click() }
                compose.mainClock.advanceTimeBy(settleMillis)
                expect(count, expanded)
            }
            expect(0, false)
            compose.onNodeWithTag("home-search-button").performTouchInput { down(center); cancel() }
            compose.mainClock.advanceTimeBy(100)
            expect(0, false)
            repeat(3) { cycle ->
                tap(cycle * 2 + 1, true)
                compose.onNodeWithTag("workspace-module-journal").assertIsDisplayed()
                tap(cycle * 2 + 2, false)
                compose.onNodeWithTag("workspace-module-journal").assertDoesNotExist()
            }
            // Closing during expansion must not replay feedback as outgoing content is removed.
            tap(7, true, 80)
            tap(8, false)
            compose.mainClock.advanceTimeBy(1000)
            expect(8, false)
            compose.onNodeWithTag("workspace-module-journal").assertDoesNotExist()
            compose.runOnIdle { assertEquals("Center taps keep their gesture owner", 0, parentActions) }
        }
    }
}
