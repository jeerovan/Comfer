package com.jeerovan.comfer

import android.app.Activity
import android.app.Application
import android.os.Bundle
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class LauncherSavedStateTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun removesOnlyFrameworkLifecycleStateWithoutMutatingInput() {
        assertNull(launcherSavedState(null))
        val state = Bundle().apply {
            putBundle("android:fragments", Bundle().apply { putString("oldClass", "vj4") })
            putBundle("androidx.savedstate.Registry", Bundle().apply { putInt("position", 7) })
            putString("selection", "preserved")
        }
        val restored = requireNotNull(launcherSavedState(state))
        assertFalse(restored.containsKey("android:fragments"))
        assertTrue(state.containsKey("android:fragments"))
        assertEquals(7, restored.getBundle("androidx.savedstate.Registry")!!.getInt("position"))
        assertEquals("preserved", restored.getString("selection"))
    }

    @Test fun homeRecreatesWithUnrestorableFrameworkStateAndKeepsSettings() {
        val application = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application
        var injected = false
        val callback = object : Application.ActivityLifecycleCallbacks {
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {
                if (activity is MainActivity) {
                    // Stand-in for an incompatible old framework parcel. This exercises
                    // the real recreation boundary, not a shipped-v56 upgrade artifact.
                    outState.putBundle("android:fragments", Bundle())
                    injected = true
                }
            }
            override fun onActivityCreated(a: Activity, b: Bundle?) = Unit
            override fun onActivityStarted(a: Activity) = Unit
            override fun onActivityResumed(a: Activity) = Unit
            override fun onActivityPaused(a: Activity) = Unit
            override fun onActivityStopped(a: Activity) = Unit
            override fun onActivityDestroyed(a: Activity) = Unit
        }
        application.registerActivityLifecycleCallbacks(callback)
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                compose.waitUntil(60_000) { StartupCoordinator.isReady }
                val settings = PreferenceManager.snapshotForBackup()
                scenario.recreate()
                assertTrue(injected)
                scenario.onActivity { assertFalse(it.isFinishing) }
                val restored = PreferenceManager.snapshotForBackup()
                // SettingsViewModel checks for updates on creation; that normal
                // bookkeeping increments this counter without changing user settings.
                val promptCounter = PreferenceManager.APP_UPDATE_PROMPT_COUNTER
                assertEquals(settings.filterKeys { it != promptCounter },
                    restored.filterKeys { it != promptCounter })
                assertTrue((restored[promptCounter]?.toInt() ?: 0) >=
                    (settings[promptCounter]?.toInt() ?: 0))
                compose.onNodeWithContentDescription("Workspace").assertIsDisplayed().performClick()
                compose.onNodeWithTag("workspace-module-search").assertIsDisplayed()
            }
        } finally {
            application.unregisterActivityLifecycleCallbacks(callback)
        }
    }
}
