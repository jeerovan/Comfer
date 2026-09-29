package com.jeerovan.comfer

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.jeerovan.comfer.tasks.TaskGestureGuide
import com.jeerovan.comfer.tasks.TaskGuide
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class HoldGestureGuideTest(private val direction: LayoutDirection) {
    @get:Rule val compose = createComposeRule()
    private val pulses = mutableListOf<HapticFeedbackType>()
    private val haptic = object : HapticFeedback {
        override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) { pulses += hapticFeedbackType }
    }
    private fun progress(tag: String) = compose.onNode(
        SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo) and hasAnyAncestor(hasTestTag(tag)),
        useUnmergedTree = true,
    ).fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo].current

    @Test fun ringFillsBeforeDragAndStaysFullDuringMovementWithoutDemoHaptics() {
        compose.mainClock.autoAdvance = false
        compose.setContent { CompositionLocalProvider(LocalLayoutDirection provides direction, LocalHapticFeedback provides haptic) {
            MaterialTheme { Column(Modifier.width(300.dp)) {
                Box(Modifier.fillMaxWidth().height(80.dp).testTag("home-area"), contentAlignment = Alignment.TopCenter) {
                    LongPressHint(Modifier.testTag("home-hold"))
                }
                Box(Modifier.fillMaxWidth().height(240.dp).testTag("task-area"), contentAlignment = Alignment.TopCenter) {
                    TaskGestureGuide(TaskGuide.REORDER, 120f, onShown = {})
                }
            } }
        } }
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeBy(300)
        val before = compose.onNodeWithTag("tasks-guide-reorder").fetchSemanticsNode().boundsInRoot
        val area = compose.onNodeWithTag("task-area").fetchSemanticsNode().boundsInRoot
        assertEquals(area.center.x, before.center.x, 1f)
        assertEquals(area.top, before.top, 1f)
        compose.onNodeWithTag("home-hold").assertWidthIsEqualTo(58.dp).assertHeightIsEqualTo(58.dp)
        compose.onNodeWithTag("tasks-guide-reorder").assertWidthIsEqualTo(48.dp).assertHeightIsEqualTo(48.dp)
        assertTrue(progress("home-hold") in .1f..0.9f)
        assertEquals(progress("home-hold"), progress("tasks-guide-reorder"), .03f)
        compose.mainClock.advanceTimeBy(420)
        assertEquals(1f, progress("tasks-guide-reorder"), .01f)
        assertEquals(before.top, compose.onNodeWithTag("tasks-guide-reorder").fetchSemanticsNode().boundsInRoot.top, 1f)
        compose.mainClock.advanceTimeBy(500)
        val moving = compose.onNodeWithTag("tasks-guide-reorder").fetchSemanticsNode().boundsInRoot
        assertTrue(moving.top > before.top + 10f)
        assertTrue(moving.bottom <= area.bottom)
        assertEquals(before.center.x, moving.center.x, 1f)
        assertEquals(1f, progress("tasks-guide-reorder"), .01f)
        assertTrue(pulses.isEmpty())
    }

    @Test fun guideLeavesTapAndHoldToTheRealTarget() {
        var taps = 0
        var holds = 0
        compose.setContent { CompositionLocalProvider(LocalLayoutDirection provides direction, LocalHapticFeedback provides haptic) {
            MaterialTheme { Box(Modifier.size(160.dp).combinedClickable(onClick = { taps++ }, onLongClick = { holds++ }), contentAlignment = Alignment.Center) {
                LongPressHint(Modifier.testTag("hint"))
            } }
        } }
        compose.onNodeWithTag("hint", useUnmergedTree = true).performTouchInput { click() }
        compose.runOnIdle { assertEquals(1, taps); assertEquals(0, holds); assertTrue(pulses.isEmpty()) }
        compose.onNodeWithTag("hint", useUnmergedTree = true).performTouchInput { longClick() }
        compose.runOnIdle { assertEquals(1, holds); assertEquals(listOf(HapticFeedbackType.LongPress), pulses) }
    }

    @Test fun disabledAnimationsKeepAFullRingAtTheOrigin() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val resolver = instrumentation.targetContext.contentResolver
        val original = android.provider.Settings.Global.getString(resolver, "animator_duration_scale")
        fun scale(value: String) {
            instrumentation.uiAutomation.executeShellCommand("settings put global animator_duration_scale $value").use {
                java.io.FileInputStream(it.fileDescriptor).readBytes()
            }
        }
        try {
            scale("0")
            compose.mainClock.autoAdvance = false
            compose.setContent { CompositionLocalProvider(LocalLayoutDirection provides direction) { MaterialTheme {
                Box(Modifier.size(300.dp).testTag("area"), contentAlignment = Alignment.TopCenter) {
                    TaskGestureGuide(TaskGuide.REORDER, 120f, onShown = {})
                }
            } } }
            compose.mainClock.advanceTimeBy(1200)
            val area = compose.onNodeWithTag("area").fetchSemanticsNode().boundsInRoot
            val hand = compose.onNodeWithTag("tasks-guide-reorder").fetchSemanticsNode().boundsInRoot
            assertEquals(area.top, hand.top, 1f)
            assertEquals(area.center.x, hand.center.x, 1f)
            assertEquals(1f, progress("tasks-guide-reorder"), .01f)
        } finally { scale(original ?: "1") }
    }

    companion object {
        @JvmStatic @Parameterized.Parameters(name = "{0}")
        fun directions() = listOf(arrayOf(LayoutDirection.Ltr), arrayOf(LayoutDirection.Rtl))
    }
}
