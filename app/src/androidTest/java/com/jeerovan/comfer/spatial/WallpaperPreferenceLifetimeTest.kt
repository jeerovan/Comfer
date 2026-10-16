package com.jeerovan.comfer.spatial

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.os.Looper
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class WallpaperPreferenceLifetimeTest {
    @get:Rule val compose = createComposeRule()

    private class Preferences {
        private val base = InstrumentationRegistry.getInstrumentation().targetContext
        private val delegate = base.getSharedPreferences("wallpaper-preference-test", Context.MODE_PRIVATE)
        val operations = CopyOnWriteArrayList<Pair<String, Boolean>>()
        val listeners = CopyOnWriteArrayList<SharedPreferences.OnSharedPreferenceChangeListener>()
        @Volatile var enabled = true
        var read: () -> Boolean = { enabled }
        fun record(name: String) { operations += name to (Looper.myLooper() == Looper.getMainLooper()) }
        val prefs = object : SharedPreferences by delegate {
            override fun getBoolean(key: String?, defValue: Boolean): Boolean {
                record("read")
                return read()
            }
            override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
                record("register")
                listeners += listener
            }
            override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
                record("unregister")
                listeners -= listener
            }
        }
        val context = object : ContextWrapper(base) {
            override fun getApplicationContext(): Context = this
            override fun getSharedPreferences(name: String?, mode: Int): SharedPreferences {
                if (name != "spatial_wallpapers") return super.getSharedPreferences(name, mode)
                record("open")
                return prefs
            }
        }
        fun changed() { listeners.forEach { it.onSharedPreferenceChanged(prefs, LOCAL_SPATIAL) } }
    }

    private fun mount(preferences: Preferences, visible: State<Boolean>) {
        compose.setContent {
            CompositionLocalProvider(LocalContext provides preferences.context) {
                if (visible.value) Text(if (rememberLocalWallpaperChoice().spatial) "enabled" else "disabled")
                else Text("disposed")
            }
        }
    }

    private fun awaitText(text: String) {
        compose.waitUntil(5_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test fun preferenceAcquisitionReadsCallbacksAndCleanupStayOffMain() {
        val preferences = Preferences()
        val visible = mutableStateOf(true)
        mount(preferences, visible)
        awaitText("enabled")
        compose.runOnIdle { preferences.enabled = false; preferences.changed() }
        awaitText("disabled")
        compose.runOnIdle { visible.value = false }
        compose.waitUntil(5_000) { preferences.listeners.isEmpty() }
        assertTrue(preferences.operations.toString(), preferences.operations.isNotEmpty())
        assertTrue("Preference operations ran on Main: ${preferences.operations}",
            preferences.operations.none { it.second })
        assertTrue(preferences.operations.any { it.first == "unregister" })
    }

    @Test fun blockedReadLeavesMainResponsiveAndDisposalReleasesItsExactListener() {
        val preferences = Preferences()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val visible = mutableStateOf(true)
        preferences.read = {
            check(Looper.myLooper() != Looper.getMainLooper())
            entered.countDown()
            check(release.await(10, TimeUnit.SECONDS))
            true
        }
        try {
            mount(preferences, visible)
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            compose.onNodeWithText("disabled").assertIsDisplayed()
            compose.runOnIdle { visible.value = false }
            compose.onNodeWithText("disposed").assertIsDisplayed()
            assertEquals(1, preferences.listeners.size)
        } finally { release.countDown() }
        compose.waitUntil(5_000) { preferences.listeners.isEmpty() }
        compose.onNodeWithText("disposed").assertIsDisplayed()
        assertTrue(preferences.operations.none { it.second })
    }

    @Test fun readFailureIsConservativeAndTheNextPreferenceChangeRecovers() {
        val preferences = Preferences()
        val visible = mutableStateOf(true)
        val attempted = CountDownLatch(1)
        preferences.read = { attempted.countDown(); throw IllegalStateException("unavailable preference") }
        mount(preferences, visible)
        assertTrue(attempted.await(5, TimeUnit.SECONDS))
        compose.onNodeWithText("disabled").assertIsDisplayed()
        compose.runOnIdle { preferences.read = { true }; preferences.changed() }
        awaitText("enabled")
        compose.runOnIdle { visible.value = false }
        compose.waitUntil(5_000) { preferences.listeners.isEmpty() }
    }
}
