package com.jeerovan.comfer

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class InboxHomeGestureRoutingTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun quickListReturnOpensInboxAndBackReturnsHome() {
        compose.waitUntil(30_000) { compose.onAllNodesWithTag("home-gesture-surface").fetchSemanticsNodes().isNotEmpty() }
        val density = compose.activity.resources.displayMetrics.density
        compose.onNodeWithTag("home-gesture-surface").performTouchInput {
            val start = Offset(width * .08f, height * .62f)
            down(start)
            moveBy(Offset(0f, 100f * density), 200)
            moveBy(Offset(0f, -100f * density), 200)
            up()
        }
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        fun shell(command: String) = instrumentation.uiAutomation.executeShellCommand(command).use {
            android.os.ParcelFileDescriptor.AutoCloseInputStream(it).bufferedReader().readText()
        }
        fun resumed(name: String): Boolean = shell("dumpsys activity activities").lineSequence().any {
            ("mResumedActivity" in it || "topResumedActivity" in it) && name in it
        }
        compose.waitUntil(15_000) { resumed("NotificationInboxActivity") }
        assertTrue(resumed("NotificationInboxActivity"))
        shell("input keyevent 4")
        compose.waitUntil(15_000) { resumed("com.jeerovan.comfer.MainActivity") || resumed("com.jeerovan.comfer/.MainActivity") }
    }
}
