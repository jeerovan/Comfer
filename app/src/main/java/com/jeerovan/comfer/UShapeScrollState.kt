package com.jeerovan.comfer

import androidx.compose.foundation.gestures.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.input.pointer.util.VelocityTracker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.round

/** An icon takes 20 logical units to move to the next position on the U-shaped path. */
internal const val U_SHAPE_ICON_STEP = 20f
private const val U_SHAPE_SPEED_MULTIPLIER = 8f
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
    private var dragLimit = speedLimit(1f)
    val isSettling: Boolean get() = !dragging && motion?.isActive == true

    fun stop() {
        motion?.cancel()
        motion = null
        dragging = false
        pendingDrag = 0f
    }

    fun dragBy(delta: Float, sensitivity: Float = 1f) {
        if (!delta.isFinite()) return
        dragLimit = speedLimit(sensitivity)
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
        pendingDrag += delta * U_SHAPE_SPEED_MULTIPLIER
    }

    fun settle(itemCount: Int, velocity: Float = 0f, sensitivity: Float = 1f) {
        stop()
        if (itemCount <= 0) return
        val setting = sensitivityValue(sensitivity)
        val limit = speedLimit(setting)
        val inputVelocity = velocity.takeIf { it.isFinite() } ?: 0f
        val releasedVelocity = (inputVelocity * U_SHAPE_SPEED_MULTIPLIER).coerceIn(-limit, limit)
        val start = offset
        val coasting = abs(inputVelocity) > 5f
        // Speed tuning must not change the sensitivity-based glide duration.
        var duration = if (coasting) 1.4f + 1.0f * setting else .22f
        var target = round((start + if (coasting) releasedVelocity * duration / 2f else 0f) / U_SHAPE_ICON_STEP) * U_SHAPE_ICON_STEP
        if (coasting && abs(target - start) > limit * duration / 2f) {
            target -= kotlin.math.sign(target - start) * U_SHAPE_ICON_STEP
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
    }.pointerInput(state, enabled, itemCount) {
        if (enabled) awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val tracker = VelocityTracker()
            tracker.addPosition(down.uptimeMillis, down.position)
            var horizontal = false
            var verticalDistance = 0f
            var closed = false
            fun update(change: PointerInputChange, delta: Offset) {
                tracker.addPosition(change.uptimeMillis, change.position)
                if (horizontal && itemCount > 0) {
                    state.dragBy(delta.x * .05f * sensitivityValue(currentSpeed), currentSpeed)
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
                // Include the release time so holding still before lifting kills the fling.
                currentEvent.changes.firstOrNull { it.id == start.id }?.let {
                    tracker.addPosition(it.uptimeMillis, it.position)
                }
                if (horizontal) {
                    val velocity = if (released) tracker.calculateVelocity().x * .3f * sensitivityValue(currentSpeed) else 0f
                    state.settle(itemCount, velocity, currentSpeed)
                }
            }
        }
    }
}
