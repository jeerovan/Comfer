package com.jeerovan.comfer

import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsActivitySmokeTest {
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
