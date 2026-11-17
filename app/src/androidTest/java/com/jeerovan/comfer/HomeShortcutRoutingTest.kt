package com.jeerovan.comfer

import android.content.pm.ActivityInfo
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Saved assignments are fixtures; default Home must load and dispatch them itself. */
class HomeShortcutRoutingTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private fun shell(command: String) = instrumentation.uiAutomation.executeShellCommand(command).use {
        android.os.ParcelFileDescriptor.AutoCloseInputStream(it).bufferedReader().readText()
    }
    private fun resumed(name: String) = shell("dumpsys activity activities").lineSequence().any {
        ("mResumedActivity" in it || "topResumedActivity" in it) && name in it
    }

    @Test fun savedShortcutsLaunchFromNaturalPathsAndSurviveRecreation() {
        val context = instrumentation.targetContext
        check(context.packageName.endsWith(".notificationtest"))
        compose.waitUntil(30_000) { StartupCoordinator.isReady }
        val ids = listOf("Center", "TopRight", "TopLeft", "BottomRight", "BottomLeft")
        val saved = ids.associateWith { PreferenceManager.getString(context, "Pattern_$it", null) }
        try {
            ids.forEach { PreferenceManager.setString(context, "Pattern_$it",
                if (it == "Center") "com.jeerovan.fixtures.mail" else "com.android.settings") }
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                var density = 1f
                lateinit var model: SettingsViewModel
                fun awaitHome() {
                    compose.waitUntil(20_000) { resumed("com.jeerovan.comfer.notificationtest/com.jeerovan.comfer.MainActivity") }
                    scenario.onActivity {
                        density = it.resources.displayMetrics.density
                        model = ViewModelProvider(it)[SettingsViewModel::class.java]
                    }
                    compose.waitUntil(30_000) {
                        ids.all { model.uiState.value.patternApps[it]?.packageName ==
                            if (it == "Center") "com.jeerovan.fixtures.mail" else "com.android.settings" } &&
                            compose.onAllNodesWithTag("home-gesture-surface").fetchSemanticsNodes().isNotEmpty()
                    }
                }
                for (orientation in listOf(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE)) {
                    scenario.onActivity { it.requestedOrientation = orientation }
                    val expectedOrientation = if (orientation == ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)
                        android.content.res.Configuration.ORIENTATION_PORTRAIT else android.content.res.Configuration.ORIENTATION_LANDSCAPE
                    compose.waitUntil(15_000) { context.resources.configuration.orientation == expectedOrientation }
                    awaitHome()
                    for ((index, id) in ids.withIndex()) {
                        compose.onNodeWithTag("home-gesture-surface").performTouchInput {
                            val origin = Offset(width * .5f, height * .48f)
                            if (id == "Center") {
                                down(origin + Offset(80f, 0f) * density)
                                for (i in 1..40) {
                                    val angle = 1.85 * kotlin.math.PI * i / 40
                                    moveTo(origin + Offset((80 * kotlin.math.cos(angle)).toFloat(),
                                        (34 * kotlin.math.sin(angle)).toFloat()) * density, if (i < 10) 24 else 10)
                                }
                            } else {
                                val sx = if (index == 2 || index == 4) -1 else 1
                                val sy = if (index >= 3) -1 else 1
                                down(origin)
                                for (point in listOf(Offset(2f, 44f), Offset(8f, 62f), Offset(24f, 77f), Offset(84f, 82f)))
                                    moveTo(origin + Offset(point.x * sx, point.y * sy) * density, 65)
                            }
                            up()
                        }
                        val target = if (id == "Center") "com.jeerovan.fixtures.mail" else "com.android.settings"
                        try {
                            compose.waitUntil(15_000) { resumed(target) }
                        } catch (failure: Throwable) {
                            println("Shortcut routing failed: id=$id orientation=$orientation target=$target")
                            throw failure
                        }
                        assertTrue("$id opens assigned app in orientation $orientation", resumed(target))
                        shell("input keyevent 4")
                        awaitHome()
                    }
                    scenario.recreate()
                    awaitHome()
                }
                scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
            }
        } finally {
            saved.forEach { (id, value) ->
                if (value == null) PreferenceManager.clear(context, "Pattern_$id")
                else PreferenceManager.setString(context, "Pattern_$id", value)
            }
        }
    }
}
