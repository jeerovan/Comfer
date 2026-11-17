package com.jeerovan.comfer

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2

internal enum class HomeGesture { NONE, UP, DOWN, LEFT, RIGHT, CIRCLE, TOP_RIGHT, TOP_LEFT, BOTTOM_LEFT, BOTTOM_RIGHT, INBOX }
private enum class LPatternType { DOWN_RIGHT, DOWN_LEFT, UP_RIGHT, UP_LEFT, RIGHT_DOWN, RIGHT_UP, LEFT_DOWN, LEFT_UP }

/** One result per released path; an Inbox return never falls through to a swipe. */
internal fun classifyHomeGesture(path: List<Offset>, density: Float): HomeGesture {
    if (path.size < 2 || density <= 0f || !density.isFinite() || path.any { !it.x.isFinite() || !it.y.isFinite() }) return HomeGesture.NONE
    val threshold = 50f * density
    // Finger speed and event coalescing must not change the shape's weighting.
    val shape = resampleGesturePath(path)
    if (detectCircularPattern(shape, 44f * density) != null) return HomeGesture.CIRCLE
    detectLPatternWithCorner(shape, 36f * density)?.let {
        return when (it) {
            LPatternType.LEFT_UP, LPatternType.DOWN_RIGHT -> HomeGesture.TOP_RIGHT
            LPatternType.UP_LEFT, LPatternType.RIGHT_DOWN -> HomeGesture.BOTTOM_LEFT
            LPatternType.RIGHT_UP, LPatternType.DOWN_LEFT -> HomeGesture.TOP_LEFT
            LPatternType.UP_RIGHT, LPatternType.LEFT_DOWN -> HomeGesture.BOTTOM_RIGHT
        }
    }
    val start = path.first()
    // These bounds are below the existing circle/L horizontal extent requirements.
    if (path.maxOf { it.x } - path.minOf { it.x } <= 32f * density) {
        var peak = start.y
        var trough = start.y
        var returning = false
        var valid = true
        for (point in path) {
            if (!returning) {
                peak = maxOf(peak, point.y)
                if (peak - start.y >= 80f * density && peak - point.y >= 24f * density) {
                    returning = true
                    trough = point.y
                } else if (point.y < start.y - 8f * density) valid = false
            } else {
                trough = minOf(trough, point.y)
                if (point.y - trough > 12f * density) valid = false
            }
        }
        if (returning) {
            // Recognize the reversal, not a return to an exact starting height.
            // Thumb strokes naturally have unequal lengths; each leg only needs
            // enough travel to be deliberate. A partial return still cannot
            // fall through to the notification shade or another Home swipe.
            return if (valid && peak - path.last().y >= 40f * density)
                HomeGesture.INBOX else HomeGesture.NONE
        }
    }
    val delta = path.last() - start
    // A rejected loop/corner is not a swipe merely because its endpoints differ.
    // Retain small natural drift, but require a predominantly straight stroke.
    val distance = delta.getDistance()
    val length = path.zipWithNext().sumOf { (a, b) -> (b - a).getDistance().toDouble() }.toFloat()
    if (distance + 16f * density < length * .88f ||
        path.any { distanceFromGestureSegment(it, start, path.last()) > 24f * density }) return HomeGesture.NONE
    return if (abs(delta.x) <= threshold && abs(delta.y) <= threshold) HomeGesture.NONE
    else if (abs(delta.x) > abs(delta.y)) {
        if (delta.x > 0) HomeGesture.RIGHT else HomeGesture.LEFT
    } else if (delta.y > 0) HomeGesture.DOWN else HomeGesture.UP
}

fun Modifier.detectGestures(
    onSwipeUp: () -> Unit = {},
    onSwipeDown: () -> Unit = {},
    onSwipeLeft: () -> Unit = {},
    onSwipeRight: () -> Unit = {},
    onCircular: () -> Unit = {},
    onLPatternDetected: (String) -> Unit = {},
    onInbox: () -> Unit = {},
): Modifier = pointerInput(onSwipeUp, onSwipeDown, onSwipeLeft, onSwipeRight, onCircular, onLPatternDetected, onInbox) {
    awaitEachGesture {
        // Icons/Search consume down for taps. Observe it too, then let consumed
        // movement (e.g. a scrolling child or long press) retain its owner below.
        val down = awaitFirstDown(requireUnconsumed = false)
        val path = mutableListOf(down.position)
        var dragging = false
        var cancelled = false
        while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull { it.id == down.id }
            // Never hand a one-finger gesture to another pointer, including after a lift.
            if (change == null || event.changes.any { it.id != down.id && (it.pressed || it.previousPressed) } || change.isConsumed) {
                cancelled = true
                break
            }
            path.add(change.position)
            if (!dragging && (change.position - down.position).getDistance() > viewConfiguration.touchSlop) dragging = true
            if (dragging) change.consume()
            if (!change.pressed) break
            // A child/other handler can claim this event later in the dispatch pass.
            val finalEvent = awaitPointerEvent(PointerEventPass.Final)
            if (!dragging && finalEvent.changes.any { it.id == down.id && it.isConsumed }) {
                cancelled = true
                break
            }
        }
        if (!cancelled && dragging) {
            when (classifyHomeGesture(path, 1.dp.toPx())) {
                HomeGesture.UP -> onSwipeUp()
                HomeGesture.DOWN -> onSwipeDown()
                HomeGesture.LEFT -> onSwipeLeft()
                HomeGesture.RIGHT -> onSwipeRight()
                HomeGesture.CIRCLE -> onCircular()
                HomeGesture.TOP_RIGHT -> onLPatternDetected("TopRight")
                HomeGesture.TOP_LEFT -> onLPatternDetected("TopLeft")
                HomeGesture.BOTTOM_LEFT -> onLPatternDetected("BottomLeft")
                HomeGesture.BOTTOM_RIGHT -> onLPatternDetected("BottomRight")
                HomeGesture.INBOX -> onInbox()
                HomeGesture.NONE -> Unit
            }
        }
    }
}

private fun distanceFromGestureSegment(point: Offset, start: Offset, end: Offset): Float {
    val delta = end - start
    val squared = delta.x * delta.x + delta.y * delta.y
    if (squared == 0f) return (point - start).getDistance()
    val fromStart = point - start
    val fraction = ((fromStart.x * delta.x + fromStart.y * delta.y) / squared).coerceIn(0f, 1f)
    return (point - (start + delta * fraction)).getDistance()
}

/** Fixed spatial sampling bounds recognition cost and removes pauses/duplicate events. */
private fun resampleGesturePath(path: List<Offset>): List<Offset> {
    val lengths = path.zipWithNext().map { (a, b) -> (b - a).getDistance() }
    val total = lengths.sum()
    if (total <= 0f || !total.isFinite()) return listOf(path.first())
    var segment = 0
    var traversed = 0f
    return (0..63).map { index ->
        val target = total * index / 63f
        while (segment < lengths.lastIndex && traversed + lengths[segment] < target) {
            traversed += lengths[segment++]
        }
        val length = lengths[segment]
        val fraction = if (length == 0f) 0f else ((target - traversed) / length).coerceIn(0f, 1f)
        path[segment] + (path[segment + 1] - path[segment]) * fraction
    }
}

private fun detectLPatternWithCorner(points: List<Offset>, swipeThreshold: Float): LPatternType? {
    if (points.size < 3) return null
    var best: LPatternType? = null
    var bestError = Float.POSITIVE_INFINITY
    // Fit whole legs instead of looking for a sharp turn in six touch events.
    for (corner in 1 until points.lastIndex) {
        val first = points[corner] - points.first()
        val second = points.last() - points[corner]
        val firstLength = first.getDistance()
        val secondLength = second.getDistance()
        if (firstLength < swipeThreshold || secondLength < swipeThreshold) continue
        val pattern = if (abs(first.y) > abs(first.x) * 1.4f &&
            abs(second.x) > abs(second.y) * 1.4f) {
            if (first.y > 0) {
                if (second.x > 0) LPatternType.DOWN_RIGHT else LPatternType.DOWN_LEFT
            } else {
                if (second.x > 0) LPatternType.UP_RIGHT else LPatternType.UP_LEFT
            }
        } else if (abs(first.x) > abs(first.y) * 1.4f &&
            abs(second.y) > abs(second.x) * 1.4f) {
            if (first.x > 0) {
                if (second.y > 0) LPatternType.RIGHT_DOWN else LPatternType.RIGHT_UP
            } else {
                if (second.y > 0) LPatternType.LEFT_DOWN else LPatternType.LEFT_UP
            }
        } else continue
        var error = 0f
        var valid = true
        for (i in points.indices) {
            val length = if (i <= corner) firstLength else secondLength
            val deviation = if (i <= corner)
                distanceFromGestureSegment(points[i], points.first(), points[corner])
            else distanceFromGestureSegment(points[i], points[corner], points.last())
            val relative = deviation / length
            if (relative > .24f) { valid = false; break }
            error += relative * relative
        }
        if (valid && error < bestError) {
            bestError = error
            best = pattern
        }
    }
    return best
}

private fun detectCircularPattern(path: List<Offset>,swipeThreshold:Float): String? {
    if (path.size < 10) return null

    // Normalize path to bounding box
    val minX = path.minOf { it.x }
    val maxX = path.maxOf { it.x }
    val minY = path.minOf { it.y }
    val maxY = path.maxOf { it.y }

    val width = maxX - minX
    val height = maxY - minY

    // Need minimum gesture size
    if (width < swipeThreshold || height < swipeThreshold) return null

    // Normalize points to 0-1 range
    val normalized = path.map {
        Offset(
            (it.x - minX) / width,
            (it.y - minY) / height
        )
    }

    // Detect patterns
    return when {
        isCircularPattern(normalized, width, height) -> "O"
        else -> null
    }
}

private fun isCircularPattern(points: List<Offset>, width: Float, height: Float): Boolean {
    // Thumb loops are often elliptical. Normalize axes, but reject line-like loops.
    if (width / height !in .4f..2.5f) return false
    val center = Offset(.5f, .5f)
    val distances = points.map { (it - center).getDistance() }
    val average = distances.average().toFloat()
    if (average < .3f) return false
    val variance = distances.sumOf { ((it - average) * (it - average)).toDouble() } / distances.size
    if (kotlin.math.sqrt(variance) / average > .28f) return false
    if ((points.first() - points.last()).getDistance() > .65f) return false

    var totalAngle = 0f
    var absoluteAngle = 0f
    var twiceArea = 0f
    for (i in points.indices) {
        val a = points[i] - center
        val b = points[(i + 1) % points.size] - center
        twiceArea += a.x * b.y - b.x * a.y
        if (i == points.lastIndex) continue // Closing the gap must not invent travel.
        var angle = atan2(b.y, b.x) - atan2(a.y, a.x)
        if (angle > PI) angle -= 2 * PI.toFloat()
        if (angle < -PI) angle += 2 * PI.toFloat()
        totalAngle += angle
        absoluteAngle += abs(angle)
    }
    // A mostly completed, consistently wound loop; not a triangle or scribble.
    return abs(totalAngle) >= 1.55f * PI.toFloat() &&
        abs(totalAngle) >= absoluteAngle * .88f && abs(twiceArea) >= 1.1f
}
