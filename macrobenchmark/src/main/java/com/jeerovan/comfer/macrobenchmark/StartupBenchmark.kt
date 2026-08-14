package com.jeerovan.comfer.macrobenchmark

import android.content.ComponentName
import android.content.Intent
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.ExperimentalMacrobenchmarkApi
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiSelector
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StartupBenchmark {
    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test
    fun coldStartup() = startup(StartupMode.COLD)

    @Test
    fun warmStartup() = startup(StartupMode.WARM)

    private fun startup(mode: StartupMode) {
        rule.measureRepeated(
            packageName = PACKAGE_NAME,
            metrics = listOf(StartupTimingMetric()),
            compilationMode = CompilationMode.Partial(),
            startupMode = mode,
            iterations = 5,
            setupBlock = { pressHome() },
            measureBlock = { startActivityAndWait() },
        )
    }
}

@RunWith(AndroidJUnit4::class)
class LauncherInteractionBenchmark {
    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test
    fun appDrawerOpenAndCloseFrames() {
        rule.measureRepeated(
            packageName = PACKAGE_NAME,
            metrics = listOf(FrameTimingMetric()),
            compilationMode = CompilationMode.Partial(),
            iterations = 5,
            setupBlock = {
                startActivityAndWait()
                dismissImmersiveModeConfirmation()
            },
            measureBlock = {
                val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
                device.swipe(device.displayWidth / 2, device.displayHeight * 3 / 4,
                    device.displayWidth / 2, device.displayHeight / 4, 20)
                device.waitForIdle()
                device.swipe(device.displayWidth / 2, device.displayHeight / 4,
                    device.displayWidth / 2, device.displayHeight * 3 / 4, 20)
                device.waitForIdle()
            },
        )
    }

    @Test
    fun searchTypingFrames() {
        rule.measureRepeated(
            packageName = PACKAGE_NAME,
            metrics = listOf(FrameTimingMetric()),
            compilationMode = CompilationMode.Partial(),
            iterations = 5,
            setupBlock = {
                startActivityAndWait()
                dismissImmersiveModeConfirmation()
            },
            measureBlock = {
                val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
                val search = device.findObject(UiSelector().description("Search"))
                check(search.waitForExists(UI_WAIT_TIMEOUT_MS)) { "Search action is not visible" }
                search.bounds.let { device.click(it.centerX(), it.centerY()) }
                device.waitForIdle(SEARCH_IDLE_TIMEOUT_MS)

                // Reuse a key that produces several matches on the emulator fixture. A
                // singleton result launches by design, which would make later key lookups
                // depend on the device's installed-app inventory.
                repeat(3) {
                    val key = device.findObject(UiSelector().text("A"))
                    check(key.waitForExists(UI_WAIT_TIMEOUT_MS)) { "A key is not visible" }
                    key.bounds.let { device.click(it.centerX(), it.centerY()) }
                    val backspace = device.findObject(UiSelector().description("Backspace key"))
                    check(backspace.waitForExists(UI_WAIT_TIMEOUT_MS)) { "Backspace key is not visible" }
                    backspace.bounds.let { device.click(it.centerX(), it.centerY()) }
                }
                device.waitForIdle(SEARCH_IDLE_TIMEOUT_MS)
                device.pressBack()
            },
        )
    }

    @Test
    @OptIn(ExperimentalMacrobenchmarkApi::class)
    fun settingsSliderDragFrames() {
        rule.measureRepeated(
            packageName = PACKAGE_NAME,
            metrics = listOf(FrameTimingMetric()),
            // Keep configured launcher/widget data untouched on physical fixtures.
            compilationMode = CompilationMode.Ignore(),
            iterations = 5,
            setupBlock = {
                val intent = Intent().apply {
                    component = ComponentName(
                        PACKAGE_NAME,
                        "$PACKAGE_NAME.SliderBenchmarkActivity",
                    )
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivityAndWait(intent)
            },
            measureBlock = {
                val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
                val slider = device.findObject(
                    UiSelector().description(SLIDER_FIXTURE_DESCRIPTION),
                )
                check(slider.waitForExists(UI_WAIT_TIMEOUT_MS)) {
                    "Settings slider benchmark fixture is not visible"
                }
                val bounds = slider.bounds
                val left = bounds.left + 20
                val right = bounds.right - 20
                val y = bounds.centerY()
                var start = bounds.centerX()
                var end = right
                repeat(20) {
                    // Samsung's UiAutomator spends about 20 ms per step: twenty
                    // 25-step gestures produce the documented 10-second stress.
                    device.swipe(start, y, end, y, 25)
                    start = end
                    end = if (end == right) left else right
                }
                device.waitForIdle()
            },
        )
    }

    @Test
    @OptIn(ExperimentalMacrobenchmarkApi::class)
    fun configuredWidgetNavigationFrames() {
        rule.measureRepeated(
            packageName = PACKAGE_NAME,
            metrics = listOf(FrameTimingMetric()),
            // This scenario depends on app-private widget host IDs. Do not reset
            // package compilation/data around the configured physical fixture.
            compilationMode = CompilationMode.Ignore(),
            iterations = 5,
            setupBlock = {
                startActivityAndWait()
                dismissImmersiveModeConfirmation()
                val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
                closeWidgetScreenIfVisible(device)
            },
            measureBlock = {
                val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
                swipeQuickListLeft(device)
                val configuredWidget = device.findObject(
                    UiSelector().description(WIDGET_FIXTURE_DESCRIPTION),
                )
                check(configuredWidget.waitForExists(UI_WAIT_TIMEOUT_MS)) {
                    "Configured Digital clock widget is not visible"
                }
                device.waitForIdle()

                swipeQuickListRight(device)
                val search = device.findObject(UiSelector().description("Search"))
                check(search.waitForExists(UI_WAIT_TIMEOUT_MS)) {
                    "Launcher QuickListOverlay did not return"
                }
                device.waitForIdle()
            },
        )
    }
}

private fun closeWidgetScreenIfVisible(device: UiDevice) {
    val configuredWidget = device.findObject(
        UiSelector().description(WIDGET_FIXTURE_DESCRIPTION),
    )
    if (configuredWidget.waitForExists(SYSTEM_UI_WAIT_TIMEOUT_MS)) {
        swipeQuickListRight(device)
        device.waitForIdle()
    }
}

private fun swipeQuickListLeft(device: UiDevice) {
    device.swipe(
        device.displayWidth * 3 / 4,
        device.displayHeight * 5 / 6,
        device.displayWidth / 4,
        device.displayHeight * 5 / 6,
        20,
    )
}

private fun swipeQuickListRight(device: UiDevice) {
    device.swipe(
        device.displayWidth / 4,
        device.displayHeight * 5 / 6,
        device.displayWidth * 3 / 4,
        device.displayHeight * 5 / 6,
        20,
    )
}

private fun dismissImmersiveModeConfirmation() {
    val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    val confirmation = device.findObject(
        UiSelector().resourceId("com.android.systemui:id/ok"),
    )
    if (confirmation.waitForExists(SYSTEM_UI_WAIT_TIMEOUT_MS)) {
        confirmation.bounds.let { device.click(it.centerX(), it.centerY()) }
        device.waitForIdle()
    }
}

private const val PACKAGE_NAME = "com.jeerovan.comfer"
private const val UI_WAIT_TIMEOUT_MS = 5_000L
private const val SEARCH_IDLE_TIMEOUT_MS = 1_000L
private const val SYSTEM_UI_WAIT_TIMEOUT_MS = 500L
private const val WIDGET_FIXTURE_DESCRIPTION = "Digital clock"
private const val SLIDER_FIXTURE_DESCRIPTION = "Settings slider benchmark"
