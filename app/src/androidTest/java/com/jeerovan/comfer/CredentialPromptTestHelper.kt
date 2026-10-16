package com.jeerovan.comfer

import android.os.ParcelFileDescriptor
import android.os.SystemClock
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.test.platform.app.InstrumentationRegistry

/** OEM credential screens can replace the caller's title and consume Back for the IME. */
internal fun cancelDeviceCredentialPrompt(compose: ComposeTestRule, finished: () -> Boolean) {
    val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
    fun shell(command: String) = automation.executeShellCommand(command).use {
        ParcelFileDescriptor.AutoCloseInputStream(it).bufferedReader().readText()
    }
    fun promptVisible() = shell("dumpsys activity activities").lineSequence().any { line ->
        ("mResumedActivity" in line || "topResumedActivity" in line) &&
            ("ConfirmLockPassword" in line || "ConfirmLockPattern" in line ||
                "ConfirmDeviceCredential" in line)
    }
    compose.waitUntil(10_000) { promptVisible() }
    shell("input keyevent KEYCODE_BACK")
    var lastBack = SystemClock.elapsedRealtime()
    var retries = 0
    compose.waitUntil(10_000) {
        // ActivityScenario has no stable Lifecycle.State during STARTED; its
        // getState() throws until the native credential transition completes.
        val complete = try { finished() } catch (_: NullPointerException) { false }
        if (complete) true else {
            val now = SystemClock.elapsedRealtime()
            if (retries < 2 && now - lastBack >= 1_000 && promptVisible()) {
                shell("input keyevent KEYCODE_BACK")
                lastBack = now
                retries++
            }
            false
        }
    }
}
