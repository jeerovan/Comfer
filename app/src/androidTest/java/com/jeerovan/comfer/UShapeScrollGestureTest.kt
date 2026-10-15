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
    private var density = 1f

    private fun show() {
        compose.setContent {
            density = androidx.compose.ui.platform.LocalDensity.current.density
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

    @Test fun interruptedFlingThenPrecisionDragImmediatelyReleasedOnlySnaps() {
        show()
        val drawer = compose.onNodeWithTag("drawer")
        for (sensitivity in listOf(1f, 3f)) {
            compose.runOnIdle { speed = sensitivity }
            compose.mainClock.advanceTimeByFrame()
            // Start a real touch fling, then brake it with a separate tap.
            drawer.performTouchInput { down(center) }
            repeat(10) {
                drawer.performTouchInput { moveBy(Offset(10f * density, 0f), delayMillis = 16) }
                compose.mainClock.advanceTimeByFrame()
            }
            drawer.performTouchInput { up() }
            compose.mainClock.advanceTimeBy(80)
            compose.runOnIdle { assertTrue("Initial swipe must fling", scroll.isSettling) }
            drawer.performTouchInput { down(center); up() }
            var stopped = 0f
            compose.runOnIdle { stopped = scroll.offset }
            compose.mainClock.advanceTimeBy(100)
            compose.runOnIdle { assertEquals(stopped, scroll.offset, .001f) }
            drawer.performTouchInput {
                down(center)
                moveBy(Offset(-touchSlop - density, 0f), delayMillis = 200)
            }
            repeat(40) {
                drawer.performTouchInput { moveBy(Offset(-.5f * density, 0f), delayMillis = 16) }
                compose.mainClock.advanceTimeByFrame()
            }
            var target = 0f
            compose.runOnIdle {
                val nearest = kotlin.math.round(scroll.offset / U_SHAPE_ICON_STEP) * U_SHAPE_ICON_STEP
                target = ((nearest % 1000f) + 1000f) % 1000f
            }
            // No artificial stationary pause: lift immediately after fine positioning.
            drawer.performTouchInput { up() }
            compose.mainClock.advanceTimeBy(600)
            compose.runOnIdle {
                assertFalse("Precision release must not coast (sensitivity=$sensitivity, offset=${scroll.offset}, target=$target)", scroll.isSettling)
                assertEquals("Precision release must select the nearest icon", target, scroll.offset, .01f)
            }
        }
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
        compose.runOnIdle { dragged = scroll.offset; assertTrue("Slow movement must register", dragged > 1f) }
        drawer.performTouchInput { advanceEventTime(400); up() }
        compose.mainClock.advanceTimeBy(6000)
        compose.runOnIdle {
            assertFalse(scroll.isSettling)
            assertEquals(0f, scroll.offset % U_SHAPE_ICON_STEP, .01f)
            assertTrue("A slow adjustment must stay near the chosen icon", kotlin.math.abs(scroll.offset - dragged) <= U_SHAPE_ICON_STEP)
        }
    }

    @Test fun slowDragFiltersTinyReversalsAndTracksAboutOneIconPerFingerWidth() {
        show()
        val drawer = compose.onNodeWithTag("drawer")
        drawer.performTouchInput {
            down(center)
            moveBy(Offset(touchSlop + 2f * density, 0f), delayMillis = 200)
        }
        compose.mainClock.advanceTimeBy(32)
        var previous = 0f
        compose.runOnIdle { previous = scroll.offset }
        // A slow rightward stroke with sub-dp hand tremor must not shake the icons
        // backward on every small correction from the touch sensor.
        repeat(16) {
            for (delta in listOf(.4f, -.2f)) {
                drawer.performTouchInput { moveBy(Offset(delta * density, 0f), delayMillis = 16) }
                compose.mainClock.advanceTimeByFrame()
                compose.runOnIdle {
                    assertTrue("Tiny touch reversals must not vibrate the list", scroll.offset >= previous - .001f)
                    previous = scroll.offset
                }
            }
        }
        val start = previous
        repeat(8) {
            drawer.performTouchInput { moveBy(Offset(8f * density, 0f), delayMillis = 16) }
            compose.mainClock.advanceTimeByFrame()
        }
        compose.runOnIdle {
            val moved = scroll.offset - start
            assertTrue("A 64 dp drag should move about one icon, not race through the list (moved=$moved)",
                moved in U_SHAPE_ICON_STEP * .8f..U_SHAPE_ICON_STEP * 1.2f)
        }
        drawer.performTouchInput { cancel() }
    }

    @Test fun shortFastAdjustmentsOnlySnapInsteadOfStartingAFling() {
        show()
        val drawer = compose.onNodeWithTag("drawer")
        for (setting in listOf(3f, .1f, 1f)) {
            compose.runOnIdle { speed = setting }
            compose.mainClock.advanceTimeByFrame()
            drawer.performTouchInput {
                down(center)
                moveBy(Offset(touchSlop + 4f * density, 0f), delayMillis = 16)
            }
            compose.mainClock.advanceTimeByFrame()
            drawer.performTouchInput { moveBy(Offset(3f * density, 0f), delayMillis = 16) }
            compose.mainClock.advanceTimeByFrame()
            var target = 0f
            compose.runOnIdle {
                val nearest = kotlin.math.round(scroll.offset / U_SHAPE_ICON_STEP) * U_SHAPE_ICON_STEP
                target = ((nearest % 1000f) + 1000f) % 1000f
            }
            drawer.performTouchInput { up() }
            compose.mainClock.advanceTimeBy(1000)
            compose.runOnIdle {
                assertFalse("A short adjustment must not start a long fling (speed=$setting)", scroll.isSettling)
                assertEquals("A short adjustment must select the nearest icon", target, scroll.offset, .01f)
            }
        }
    }

    @Test fun flingTargetNeverRoundsBackAgainstTheReleasedDirection() {
        show()
        for (direction in listOf(-1f, 1f)) {
            compose.runOnIdle { scroll.dragBy(direction * 3f) }
            compose.mainClock.advanceTimeBy(32)
            var released = 0f
            compose.runOnIdle {
                released = scroll.offset
                scroll.settle(50, direction * .05f, .1f)
            }
            compose.mainClock.advanceTimeBy(160)
            compose.runOnIdle {
                assertTrue("Fling centering must preserve its direction", (scroll.offset - released) * direction > 0f)
            }
            compose.mainClock.advanceTimeBy(3000)
        }
    }

    @Test fun clearFlingsFollowTheFinalStrokeDespiteLiftTremor() {
        show()
        val drawer = compose.onNodeWithTag("drawer")
        for (direction in listOf(-1f, 1f)) {
            for (reverse in listOf(false, true)) {
                drawer.performTouchInput {
                    down(center - Offset(direction * 80f * density, 0f))
                    moveBy(Offset(direction * (touchSlop + 4f * density), 0f), delayMillis = 80)
                }
                compose.mainClock.advanceTimeBy(32)
                repeat(6) {
                    drawer.performTouchInput { moveBy(Offset(direction * 30f * density, 0f), delayMillis = 16) }
                    compose.mainClock.advanceTimeByFrame()
                }
                if (reverse) repeat(6) {
                    drawer.performTouchInput { moveBy(Offset(-direction * 30f * density, 0f), delayMillis = 16) }
                    compose.mainClock.advanceTimeByFrame()
                }
                val finalDirection = if (reverse) -direction else direction
                // A tiny opposite movement while lifting cannot reverse the fling.
                drawer.performTouchInput {
                    moveBy(Offset(-finalDirection * .4f * density, 0f), delayMillis = 4)
                    up()
                }
                var released = 0f
                compose.runOnIdle { released = scroll.offset }
                compose.mainClock.advanceTimeBy(80)
                compose.runOnIdle {
                    assertTrue("The clear final stroke must own fling direction (direction=$direction, reverse=$reverse)",
                        (scroll.offset - released) * finalDirection > 0f)
                }
                drawer.performTouchInput { down(center); up() }
                compose.runOnIdle { assertFalse("Touch must still brake the fling", scroll.isSettling) }
            }
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
            drawer.performTouchInput { moveBy(Offset(-10f * density, 0f), delayMillis = 16) }
            compose.mainClock.advanceTimeByFrame()
        }
        drawer.performTouchInput { up() }
        var released = 0f
        compose.runOnIdle { released = scroll.offset; assertTrue(released < stopped) }
        compose.mainClock.advanceTimeBy(80)
        compose.runOnIdle { assertTrue("An old stop job must not kill the new fling", scroll.offset < released) }
    }

    @Test fun dragDistanceAndReversalAreIndependentOfScrollSpeed() {
        show()
        val drawer = compose.onNodeWithTag("drawer")
        fun drag(setting: Float): List<Float> {
            compose.runOnIdle { speed = setting }
            compose.mainClock.advanceTimeByFrame()
            drawer.performTouchInput {
                down(center)
                moveBy(Offset(touchSlop + 1f, 0f), delayMillis = 100)
            }
            compose.mainClock.advanceTimeBy(32)
            val movements = mutableListOf<Float>()
            // Check fine positioning, a faster drag that reaches the speed cap,
            // and immediate reversal while the finger remains on the screen.
            for (delta in listOf(3f, 24f, -3f, -24f)) repeat(4) {
                var before = 0f
                compose.runOnIdle { before = scroll.offset }
                drawer.performTouchInput { moveBy(Offset(delta * density, 0f), delayMillis = 16) }
                compose.mainClock.advanceTimeByFrame()
                compose.runOnIdle {
                    val moved = scroll.offset - before
                    assertTrue("The list must follow the drag direction", moved * delta > 0f)
                    movements += moved
                }
            }
            var held = 0f
            compose.runOnIdle { held = scroll.offset }
            compose.mainClock.advanceTimeBy(200)
            compose.runOnIdle { assertEquals("A held finger must not queue catch-up motion", held, scroll.offset, .001f) }
            drawer.performTouchInput { cancel() }
            compose.runOnIdle { scroll.stop() }
            return movements
        }
        val normal = drag(1f)
        for (setting in listOf(.1f, 3f)) {
            val actual = drag(setting)
            normal.zip(actual).forEachIndexed { index, (expected, moved) ->
                assertEquals("Drag step $index must be independent of Scroll Speed ($setting)", expected, moved, .01f)
            }
        }
    }

    @Test fun updatedSpeedDuringDragOnlyChangesTheReleasedFling() {
        show()
        val drawer = compose.onNodeWithTag("drawer")
        fun gesture(releaseSpeed: Float): Pair<Float, Long> {
            compose.runOnIdle { speed = 1f }
            compose.mainClock.advanceTimeByFrame()
            var start = 0f
            compose.runOnIdle { start = scroll.offset }
            drawer.performTouchInput {
                down(center - Offset(80f * density, 0f))
                moveBy(Offset(touchSlop + 1f, 0f), delayMillis = 100)
            }
            compose.mainClock.advanceTimeBy(32)
            // Updating the preference must not restart or accelerate an active drag.
            compose.runOnIdle { speed = releaseSpeed }
            compose.mainClock.advanceTimeByFrame()
            repeat(6) {
                drawer.performTouchInput { moveBy(Offset(30f * density, 0f), delayMillis = 16) }
                compose.mainClock.advanceTimeByFrame()
            }
            var dragged = 0f
            compose.runOnIdle { dragged = scroll.offset - start }
            drawer.performTouchInput { up() }
            val releasedAt = compose.mainClock.currentTime
            var active = true
            while (active && compose.mainClock.currentTime - releasedAt < 6000) {
                compose.mainClock.advanceTimeBy(64)
                compose.runOnIdle { active = scroll.isSettling }
            }
            assertFalse("The released fling must eventually settle", active)
            return dragged to (compose.mainClock.currentTime - releasedAt)
        }
        val low = gesture(.1f)
        val high = gesture(3f)
        assertTrue("A manual drag must move the list", low.first > 0f)
        assertEquals("Scroll Speed must not change an active drag", low.first, high.first, .01f)
        assertTrue("The updated setting must still control the released fling duration (low=${low.second} ms, high=${high.second} ms)",
            high.second > low.second)
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
                    compose.runOnIdle { before = scroll.offset; scroll.dragBy(10000f * direction) }
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
