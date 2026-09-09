package com.jeerovan.comfer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.*

class UShapeScrollGestureTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var scroll: UShapeScrollState
    private var speed by mutableFloatStateOf(1f)
    private var enabled by mutableStateOf(true)
    private var downs = 0
    private var iconTaps = 0
    private var doubleTaps = 0
    private var longPresses = 0
    private var touchSlop = 0f

    private fun show() {
        compose.setContent {
            touchSlop = androidx.compose.ui.platform.LocalViewConfiguration.current.touchSlop
            scroll = rememberUShapeScrollState()
            Box(Modifier.fillMaxSize().testTag("drawer")
                .pointerInput(Unit) { detectTapGestures(onDoubleTap = { doubleTaps++ }, onLongPress = { longPresses++ }) }
                .uShapeScrollGestures(scroll, enabled, 50, speed, {}, { downs++ })) {
                Box(Modifier.size(64.dp).testTag("app-icon").clickable { iconTaps++ })
            }
        }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
    }

    @Test fun slowOnePixelMovesRemainPreciseAndSettle() {
        show()
        val drawer = compose.onNodeWithTag("drawer")
        drawer.performTouchInput {
            down(center)
            // Cross Android's slop with just one pixel left over. Doing the entire
            // approach as separate host/device calls can accidentally trigger long press.
            moveBy(Offset(touchSlop + 1f, 0f), delayMillis = 200)
        }
        repeat(100) {
            drawer.performTouchInput { moveBy(Offset(1f, 0f), delayMillis = 16) }
            compose.mainClock.advanceTimeByFrame()
        }
        var dragged = 0f
        compose.runOnIdle { dragged = scroll.offset; assertTrue("Slow movement must register", dragged > 10f) }
        assertTrue("Precision drag should retain the chosen 0.05 × 8 gain", dragged in 39f..41f)
        drawer.performTouchInput { advanceEventTime(400); up() }
        compose.mainClock.advanceTimeBy(6000)
        compose.runOnIdle {
            assertFalse(scroll.isSettling)
            assertEquals(0f, scroll.offset % U_SHAPE_ICON_STEP, .01f)
            assertTrue("A slow adjustment must stay near the chosen icon", kotlin.math.abs(scroll.offset - dragged) <= U_SHAPE_ICON_STEP)
        }
    }

    @Test fun touchStopsFlingImmediatelyAndNextSwipeKeepsItsMomentum() {
        show()
        compose.runOnIdle { scroll.settle(50, 700f) }
        compose.mainClock.advanceTimeBy(120)
        val drawer = compose.onNodeWithTag("drawer")
        drawer.performTouchInput { down(center) }
        var stopped = 0f
        compose.runOnIdle { stopped = scroll.offset; assertTrue(stopped > 0f) }
        compose.mainClock.advanceTimeBy(200)
        compose.runOnIdle { assertEquals(stopped, scroll.offset, .001f) }
        repeat(10) {
            drawer.performTouchInput { moveBy(Offset(-10f, 0f), delayMillis = 16) }
            compose.mainClock.advanceTimeByFrame()
        }
        drawer.performTouchInput { up() }
        var released = 0f
        compose.runOnIdle { released = scroll.offset; assertTrue(released < stopped) }
        compose.mainClock.advanceTimeBy(80)
        compose.runOnIdle { assertTrue("An old stop job must not kill the new fling", scroll.offset < released) }
    }

    @Test fun updatedSpeedAppliesWithoutRecreatingGestureHandler() {
        show()
        val drawer = compose.onNodeWithTag("drawer")
        fun drag(): Float {
            var before = 0f
            compose.runOnIdle { before = scroll.offset }
            drawer.performTouchInput { down(center); moveBy(Offset(100f, 0f), delayMillis = 100) }
            compose.mainClock.advanceTimeBy(32)
            var moved = 0f
            compose.runOnIdle { moved = scroll.offset - before }
            drawer.performTouchInput { cancel() }
            compose.runOnIdle { scroll.stop() }
            return moved
        }
        val normal = drag()
        compose.runOnIdle { speed = 2f }
        compose.mainClock.advanceTimeByFrame()
        val faster = drag()
        assertTrue(faster > normal)
        assertTrue("Sensitivity cannot bypass the visual speed cap", faster <= U_SHAPE_MAX_SPEED * .032f + .01f)
    }

    @Test fun childTapBrakesAndTapLongPressAndVerticalGesturesStillWork() {
        show()
        compose.runOnIdle { scroll.settle(50, 500f) }
        compose.mainClock.advanceTimeBy(80)
        compose.onNodeWithTag("app-icon").performTouchInput { click() }
        var stopped = 0f
        compose.runOnIdle { assertEquals(1, iconTaps); stopped = scroll.offset }
        compose.mainClock.advanceTimeBy(300)
        compose.runOnIdle { assertEquals(stopped, scroll.offset, .001f) }
        val drawer = compose.onNodeWithTag("drawer")
        drawer.performTouchInput { doubleClick(center) }
        drawer.performTouchInput { longClick(center) }
        drawer.performTouchInput { swipe(center, center + Offset(0f, 180f), 300) }
        compose.runOnIdle {
            assertEquals(1, doubleTaps)
            assertEquals(1, longPresses)
            assertEquals(1, downs)
            assertEquals(stopped, scroll.offset, .001f)
        }
    }

    @Test fun disablingForFolderOrDialogStopsMotion() {
        show()
        compose.runOnIdle { scroll.settle(50, 500f) }
        compose.mainClock.advanceTimeBy(80)
        compose.runOnIdle { enabled = false }
        compose.mainClock.advanceTimeByFrame()
        var stopped = 0f
        compose.runOnIdle { stopped = scroll.offset }
        compose.onNodeWithTag("drawer").performTouchInput { swipeLeft() }
        compose.mainClock.advanceTimeBy(500)
        compose.runOnIdle { assertEquals(stopped, scroll.offset, .001f) }
    }

    @Test fun holdingBeforeReleaseSettlesWithoutStaleFlingAndWrapsAtRest() {
        show()
        val drawer = compose.onNodeWithTag("drawer")
        drawer.performTouchInput { down(center) }
        repeat(10) {
            drawer.performTouchInput { moveBy(Offset(-10f, 0f), delayMillis = 16) }
            compose.mainClock.advanceTimeByFrame()
        }
        compose.mainClock.advanceTimeBy(400)
        drawer.performTouchInput { advanceEventTime(400); up() }
        var target = 0f
        compose.runOnIdle {
            val nearest = kotlin.math.round(scroll.offset / 20f) * 20f
            target = ((nearest % 1000f) + 1000f) % 1000f
        }
        compose.mainClock.advanceTimeBy(6000)
        compose.runOnIdle {
            assertEquals("A paused release must only snap to the nearest icon", target, scroll.offset, .01f)
            scroll.settle(50, -600f)
        }
        compose.mainClock.advanceTimeBy(10000)
        compose.runOnIdle {
            assertTrue(scroll.offset >= 0f && scroll.offset < 1000f)
            assertEquals(0f, scroll.offset % 20f, .01f)
        }
    }

    @Test fun higherSensitivityMovesFasterAndCoastsLonger() {
        show()
        fun measure(sensitivity: Float): Pair<Float, Long> {
            var start = 0f
            compose.runOnIdle {
                start = scroll.offset
                scroll.settle(10000, 300f * sensitivity, sensitivity)
            }
            val startTime = compose.mainClock.currentTime
            compose.mainClock.advanceTimeBy(80)
            var initialTravel = 0f
            compose.runOnIdle { initialTravel = scroll.offset - start }
            var active = true
            while (active && compose.mainClock.currentTime - startTime < 15000) {
                compose.mainClock.advanceTimeBy(64)
                compose.runOnIdle { active = scroll.isSettling }
            }
            assertFalse("Fling must eventually settle", active)
            return initialTravel to (compose.mainClock.currentTime - startTime)
        }
        val low = measure(.5f)
        val normal = measure(1f)
        val high = measure(3f)
        assertTrue(normal.first > low.first)
        assertTrue(high.first > normal.first)
        assertTrue("Normal sensitivity must coast longer than low", normal.second > low.second)
        assertTrue("High sensitivity must coast longer than normal", high.second > normal.second)
        compose.runOnIdle { scroll.settle(10000, 900f, 3f) }
        compose.mainClock.advanceTimeBy(300)
        compose.onNodeWithTag("drawer").performTouchInput { down(center) }
        var stopped = 0f
        compose.runOnIdle { stopped = scroll.offset; assertFalse(scroll.isSettling) }
        compose.mainClock.advanceTimeBy(500)
        compose.runOnIdle { assertEquals(stopped, scroll.offset, .001f) }
        compose.onNodeWithTag("drawer").performTouchInput { up() }
    }

    @Test fun nearMaximumSensitivityUsesChosenSpeedAndDuration() {
        show()
        compose.runOnIdle { scroll.settle(10000, 10000f, 2.99f) }
        val started = compose.mainClock.currentTime
        compose.mainClock.advanceTimeBy(160)
        compose.runOnIdle {
            assertTrue("Fling should retain the chosen fast initial motion", scroll.offset > 480f * .160f * .7f)
        }
        compose.mainClock.advanceTimeBy(4100)
        compose.runOnIdle { assertTrue("Glide must retain its roughly 4.4 second duration", scroll.isSettling) }
        compose.mainClock.advanceTimeBy(200)
        compose.runOnIdle {
            assertFalse(scroll.isSettling)
            assertEquals(1040f, scroll.offset, .01f)
            assertTrue(compose.mainClock.currentTime - started < 4500)
        }
    }

    @Test fun extremeInputNeverExceedsConfiguredSpeedOrQueuesCatchUp() {
        show()
        fun checkFrame(before: Float, period: Float = 200000f) {
            val elapsed = compose.mainClock.currentTime
            compose.mainClock.advanceTimeByFrame()
            compose.runOnIdle {
                val raw = kotlin.math.abs(scroll.offset - before)
                val distance = minOf(raw, kotlin.math.abs(period - raw))
                assertTrue("Frame moved $distance logical units", distance <= U_SHAPE_MAX_SPEED * (compose.mainClock.currentTime - elapsed) / 1000f + .02f)
            }
        }
        for (setting in listOf(.1f, 1f, 3f)) {
            for (direction in listOf(-1f, 1f)) {
                repeat(20) {
                    var before = 0f
                    compose.runOnIdle { before = scroll.offset; scroll.dragBy(10000f * direction, setting) }
                    checkFrame(before)
                }
                // Drain the last input frame, then confirm no excess remains queued.
                compose.mainClock.advanceTimeBy(32)
                var held = 0f
                compose.runOnIdle { held = scroll.offset }
                compose.mainClock.advanceTimeBy(300)
                compose.runOnIdle {
                    assertEquals(held, scroll.offset, .001f)
                    scroll.settle(10000, 1_000_000f * direction, setting)
                }
                var active = true
                var frames = 0
                while (active && frames++ < 400) {
                    var before = 0f
                    compose.runOnIdle { before = scroll.offset }
                    checkFrame(before)
                    compose.runOnIdle { active = scroll.isSettling }
                }
                assertFalse("Even extreme input must settle", active)
                compose.runOnIdle { assertEquals(0f, scroll.offset % U_SHAPE_ICON_STEP, .01f) }
            }
        }
    }
}
