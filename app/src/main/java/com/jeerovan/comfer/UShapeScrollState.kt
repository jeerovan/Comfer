package com.jeerovan.comfer

import android.view.ViewConfiguration
import androidx.compose.foundation.gestures.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.round
import kotlin.math.sign

/** An icon takes 20 logical units to move to the next position on the U-shaped path. */
internal const val U_SHAPE_ICON_STEP = 20f
private const val U_SHAPE_SPEED_MULTIPLIER = 8f
private const val U_SHAPE_DRAG_DP_PER_ICON = 64f
private const val U_SHAPE_FLING_DISTANCE_DP = 24f
internal const val U_SHAPE_MAX_SPEED = 3f * U_SHAPE_ICON_STEP * U_SHAPE_SPEED_MULTIPLIER

private fun sensitivityValue(value: Float) = value.takeIf { it.isFinite() }?.coerceIn(.1f, 3f) ?: 1f
private fun speedLimit(sensitivity: Float) = (1.5f + .5f * sensitivityValue(sensitivity)) * U_SHAPE_ICON_STEP * U_SHAPE_SPEED_MULTIPLIER

/** One frame-driven owner limits drag, coast and centering to the same visual speed. */
@Stable
internal class UShapeScrollState(private val scope: CoroutineScope) {
    var offset by mutableFloatStateOf(0f)
        private set
    private var motion: Job? = null
    private var dragging = false
    private var pendingDrag = 0f
    private val dragLimit = speedLimit(1f)
    val isSettling: Boolean get() = !dragging && motion?.isActive == true

    fun stop() {
        motion?.cancel()
        motion = null
        dragging = false
        pendingDrag = 0f
    }

    fun dragBy(delta: Float) {
        if (!delta.isFinite()) return
        if (!dragging) {
            stop()
            dragging = true
            motion = scope.launch {
                var previous = 0L
                while (true) {
                    val frame = withFrameNanos { it }
                    if (previous == 0L) { previous = frame; continue }
                    val seconds = ((frame - previous) / 1_000_000_000f).coerceIn(0f, 1f / 30f)
                    previous = frame
                    val limit = dragLimit * seconds
                    offset += pendingDrag.coerceIn(-limit, limit)
                    // Discard excess input each frame: never queue a fast swipe and
                    // continue chasing it after the finger has stopped or reversed.
                    pendingDrag = 0f
                }
            }
        }
        pendingDrag += delta
    }

    fun settle(itemCount: Int, velocity: Float = 0f, sensitivity: Float = 1f) {
        stop()
        if (itemCount <= 0) return
        val setting = sensitivityValue(sensitivity)
        val limit = speedLimit(setting)
        val inputVelocity = velocity.takeIf { it.isFinite() } ?: 0f
        val releasedVelocity = (inputVelocity * U_SHAPE_SPEED_MULTIPLIER).coerceIn(-limit, limit)
        val start = offset
        // The gesture handler classifies flings in screen pixels before applying gain.
        val coasting = inputVelocity != 0f
        // Speed tuning must not change the sensitivity-based glide duration.
        var duration = if (coasting) 1.4f + 1.0f * setting else .22f
        var target = round((start + if (coasting) releasedVelocity * duration / 2f else 0f) / U_SHAPE_ICON_STEP) * U_SHAPE_ICON_STEP
        if (coasting && abs(target - start) > limit * duration / 2f) {
            target -= sign(target - start) * U_SHAPE_ICON_STEP
        }
        if (coasting && (target - start) * inputVelocity <= 0f) {
            // Rounding a short glide must never select a center behind the fling.
            target = if (inputVelocity > 0f) (floor(start / U_SHAPE_ICON_STEP) + 1f) * U_SHAPE_ICON_STEP
                else (ceil(start / U_SHAPE_ICON_STEP) - 1f) * U_SHAPE_ICON_STEP
        }
        val distance = target - start
        if (abs(distance) < .001f) return
        val timingLimit = if (coasting) limit else limit / U_SHAPE_SPEED_MULTIPLIER
        duration = maxOf(duration, 2f * abs(distance) / timingLimit)
        // Monotone Hermite easing: no bounce or overshoot, zero speed at the center.
        // A starting slope in [0, 2] has a maximum derivative of 2, so this duration
        // bounds every phase, including the final centering movement, to the limit.
        val slope = (releasedVelocity * duration / distance).coerceIn(0f, 2f)
        motion = scope.launch {
            var previous = withFrameNanos { it }
            var elapsed = 0f
            while (elapsed < duration) {
                val frame = withFrameNanos { it }
                elapsed = (elapsed + ((frame - previous) / 1_000_000_000f).coerceIn(0f, 1f / 30f)).coerceAtMost(duration)
                previous = frame
                val t = elapsed / duration
                val eased = (-2f * t * t * t + 3f * t * t) + slope * (t * t * t - 2f * t * t + t)
                offset = start + distance * eased
            }
            val period = itemCount * U_SHAPE_ICON_STEP
            offset = ((target % period) + period) % period
        }
    }
}

/** Small spatial hysteresis absorbs touch tremor without animating or delaying a drag. */
private class UShapeDragTracker(startTime: Long, private val jitterSlop: Float, private val flingDistance: Float) {
    private data class Sample(val time: Long, val position: Float)
    private val samples = ArrayDeque<Sample>().apply { add(Sample(startTime, 0f)) }
    private var rawPosition = 0f
    private var position = 0f
    private var direction = 0f

    fun dragBy(delta: Float, time: Long): Float {
        rawPosition += delta
        val previous = position
        val difference = rawPosition - position
        if (abs(difference) > jitterSlop) position += difference - sign(difference) * jitterSlop
        val moved = position - previous
        if (moved != 0f) {
            if (moved * direction < 0f) {
                // A deliberate reversal starts a new stroke; old momentum cannot win.
                val previousTime = samples.last().time
                samples.clear()
                samples.add(Sample(previousTime, previous))
            }
            direction = sign(moved)
        }
        sample(time)
        return moved
    }

    fun releaseVelocity(time: Long): Float {
        // Use the last accepted position. A shifted UP coordinate is not a drag.
        sample(time)
        val first = samples.first()
        val distance = position - first.position
        val elapsed = time - first.time
        return if (elapsed > 0 && abs(distance) >= flingDistance) distance * 1000f / elapsed else 0f
    }

    private fun sample(time: Long) {
        samples.add(Sample(time, position))
        // A recent stroke needs meaningful travel. Tiny fast corrections and a
        // stationary hold cannot revive momentum from earlier in the gesture.
        while (samples.size > 1 && samples.first().time < time - 100L) samples.removeFirst()
    }
}

@Composable
internal fun rememberUShapeScrollState(): UShapeScrollState {
    val scope = rememberCoroutineScope()
    val state = remember(scope) { UShapeScrollState(scope) }
    DisposableEffect(state) { onDispose { state.stop() } }
    return state
}

/** Touch braking observes Initial pass without consuming taps or child icon presses. */
@Composable
internal fun Modifier.uShapeScrollGestures(
    state: UShapeScrollState,
    enabled: Boolean,
    itemCount: Int,
    speed: Float,
    onHorizontalDrag: () -> Unit,
    onSwipeDown: () -> Unit,
): Modifier {
    val context = LocalContext.current
    val density = LocalDensity.current.density
    val dragGain = U_SHAPE_ICON_STEP / (U_SHAPE_DRAG_DP_PER_ICON * density)
    val minimumFlingVelocity = remember(context) {
        ViewConfiguration.get(context).scaledMinimumFlingVelocity.toFloat()
    }
    val currentSpeed by rememberUpdatedState(speed)
    val horizontalDrag by rememberUpdatedState(onHorizontalDrag)
    val swipeDown by rememberUpdatedState(onSwipeDown)
    DisposableEffect(state, enabled, itemCount) {
        state.stop()
        onDispose { state.stop() }
    }
    return this.pointerInput(state, enabled) {
        if (enabled) awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            state.stop()
            do {
                val event = awaitPointerEvent(PointerEventPass.Initial)
            } while (event.changes.any { it.pressed })
        }
    }.pointerInput(state, enabled, itemCount, minimumFlingVelocity, density) {
        if (enabled) awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val tracker = UShapeDragTracker(down.uptimeMillis, density, U_SHAPE_FLING_DISTANCE_DP * density)
            var horizontal = false
            var verticalDistance = 0f
            var closed = false
            fun update(change: PointerInputChange, delta: Offset) {
                if (horizontal && itemCount > 0) {
                    // Keep direct manipulation predictable at every Scroll Speed.
                    // The setting applies only after the finger is released.
                    state.dragBy(tracker.dragBy(delta.x, change.uptimeMillis) * dragGain)
                    horizontalDrag()
                } else if (!horizontal && !closed) {
                    verticalDistance += delta.y
                    if (verticalDistance > 80f) { closed = true; swipeDown() }
                }
                change.consume()
            }
            val start = awaitTouchSlopOrCancellation(down.id) { change, overSlop ->
                val movement = change.position - down.position
                horizontal = abs(movement.x) > abs(movement.y)
                update(change, overSlop)
            }
            if (start != null) {
                val released = drag(start.id) { change -> update(change, change.positionChange()) }
                if (horizontal) {
                    val release = currentEvent.changes.firstOrNull { it.id == start.id }
                    val fingerVelocity = if (released && release != null) tracker.releaseVelocity(release.uptimeMillis) else 0f
                    // Start momentum at the drag's physical speed. The animation
                    // multiplier and user setting apply only to the released fling.
                    val velocity = if (abs(fingerVelocity) >= minimumFlingVelocity) {
                        fingerVelocity * dragGain / U_SHAPE_SPEED_MULTIPLIER * sensitivityValue(currentSpeed)
                    } else 0f
                    state.settle(itemCount, velocity, currentSpeed)
                }
            }
        }
    }
}
