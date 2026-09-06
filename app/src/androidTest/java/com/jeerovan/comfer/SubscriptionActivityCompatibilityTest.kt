package com.jeerovan.comfer

import android.content.Intent
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SubscriptionActivityCompatibilityTest {
    @Test
    fun obsoleteSubscriptionActivityFinishesImmediately() {
        ActivityScenario.launch(SubscriptionActivity::class.java).use { scenario ->
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()

            assertEquals(Lifecycle.State.DESTROYED, scenario.state)
        }
    }

    @Test
    fun obsoleteSubscriptionActivityRevealsUnderlyingSettings() {
        ActivityScenario.launch(SettingsActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.startActivity(Intent(activity, SubscriptionActivity::class.java))
            }

            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            scenario.moveToState(Lifecycle.State.RESUMED)

            assertEquals(Lifecycle.State.RESUMED, scenario.state)
        }
    }
}
