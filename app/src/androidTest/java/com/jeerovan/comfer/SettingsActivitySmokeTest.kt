package com.jeerovan.comfer

import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsActivitySmokeTest {
    @Test
    fun settingsStaysInHomeTaskAndSystemBackResumesHome() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var home: MainActivity
            scenario.onActivity { activity ->
                home = activity
                SettingsActivity.open(activity)
            }
            var settings: SettingsActivity? = null
            val deadline = android.os.SystemClock.elapsedRealtime() + 10_000
            while (settings == null && android.os.SystemClock.elapsedRealtime() < deadline) {
                instrumentation.runOnMainSync {
                    settings = ActivityLifecycleMonitorRegistry.getInstance()
                        .getActivitiesInStage(Stage.RESUMED).filterIsInstance<SettingsActivity>().firstOrNull()
                }
                if (settings == null) Thread.sleep(50)
            }
            assertNotNull("Settings opens from home", settings)
            assertEquals("Settings must not create a separate task", home.taskId, settings!!.taskId)
            instrumentation.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
            instrumentation.waitForIdleSync()
            scenario.onActivity { activity ->
                assertEquals("Back reveals the existing home activity", home, activity)
                assertFalse(activity.isFinishing)
            }
        }
    }

    @Test
    fun settingsActivityLaunches() {
        repeat(5) {
            ActivityScenario.launch(SettingsActivity::class.java).use { scenario ->
                scenario.moveToState(Lifecycle.State.RESUMED)
                InstrumentationRegistry.getInstrumentation().waitForIdleSync()
                scenario.onActivity { activity ->
                    assertFalse(activity.isFinishing)
                }
            }
        }
    }
}
