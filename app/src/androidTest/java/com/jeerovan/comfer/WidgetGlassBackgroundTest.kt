package com.jeerovan.comfer

import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class WidgetGlassBackgroundTest {
    @get:Rule val compose = createComposeRule()
    private class Owner : LifecycleOwner {
        val registry = LifecycleRegistry(this)
        override val lifecycle: Lifecycle get() = registry
    }
    private class Source : GlassWallpaperColors {
        var reads = 0
        var subscriptions = 0
        var removals = 0
        var color = Color.Red
        var pending: CompletableDeferred<Color?>? = null
        var listener: ((Color?) -> Unit)? = null
        override suspend fun current(): Color? { reads++; return pending?.await() ?: color }
        override fun observe(onColor: (Color?) -> Unit): () -> Unit {
            subscriptions++; listener = onColor
            return { removals++; listener = null }
        }
    }

    @Test fun noPollingOrIdleRecompositionAndNoObserverWhilePausedOrDisabled() {
        val owner = Owner()
        val source = Source()
        var enabled by mutableStateOf(true)
        var actual: Color? = null
        var compositions = 0
        compose.runOnUiThread { owner.registry.currentState = Lifecycle.State.RESUMED }
        compose.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                val color = rememberSystemGlassBackground(enabled, source)
                SideEffect { actual = color; compositions++ }
            }
        }
        compose.waitUntil { actual == Color.Red }
        val settled = compositions
        compose.mainClock.advanceTimeBy(60_000)
        compose.runOnIdle {
            assertEquals(1, source.reads)
            assertEquals(1, source.subscriptions)
            assertEquals("Static glass must not keep recomposing", settled, compositions)
            source.listener?.invoke(Color.Blue)
        }
        compose.runOnIdle {
            assertEquals(Color.Blue, actual)
            owner.registry.currentState = Lifecycle.State.CREATED
        }
        compose.runOnIdle { assertNull(source.listener); assertEquals(1, source.removals) }
        compose.mainClock.advanceTimeBy(60_000)
        compose.runOnIdle {
            assertEquals(1, source.reads)
            source.color = Color.Green
            owner.registry.currentState = Lifecycle.State.RESUMED
        }
        compose.waitUntil { actual == Color.Green }
        compose.runOnIdle { enabled = false }
        compose.runOnIdle { assertNull(actual); assertNull(source.listener) }
        compose.mainClock.advanceTimeBy(60_000)
        compose.runOnIdle { assertEquals(2, source.reads); assertEquals(2, source.removals) }
    }

    @Test fun callbackWinsOverSlowInitialReadAndUnavailableColorClearsOldTint() {
        val owner = Owner()
        val source = Source().apply { pending = CompletableDeferred() }
        var actual: Color? = null
        compose.runOnUiThread { owner.registry.currentState = Lifecycle.State.RESUMED }
        compose.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                val color = rememberSystemGlassBackground(true, source)
                SideEffect { actual = color }
            }
        }
        compose.waitUntil { source.reads == 1 }
        compose.runOnIdle { source.listener?.invoke(Color.Blue) }
        compose.runOnIdle { source.pending!!.complete(Color.Red) }
        compose.runOnIdle { assertEquals(Color.Blue, actual); source.listener?.invoke(null) }
        compose.runOnIdle { assertNull(actual) }
    }
}
