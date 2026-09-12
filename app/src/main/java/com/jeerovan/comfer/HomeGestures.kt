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
    if (detectCircularPattern(path, threshold) != null) return HomeGesture.CIRCLE
    detectLPatternWithCorner(path, threshold)?.let {
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
                if (point.y < start.y - 8f * density) valid = false
                peak = maxOf(peak, point.y)
                if (peak - start.y >= 80f * density && peak - point.y >= 24f * density) {
                    returning = true
                    trough = point.y
                }
            } else {
                trough = minOf(trough, point.y)
                if (point.y - trough > 12f * density) valid = false
            }
        }
        if (returning) {
            return if (valid && abs(path.last().y - start.y) <= 20f * density &&
                peak - path.last().y >= 60f * density) HomeGesture.INBOX else HomeGesture.NONE
        }
    }
    val delta = path.last() - start
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

private fun detectLPatternWithCorner(points: List<Offset>,swipeThreshold: Float): LPatternType? {
    if (points.size < 10) return null

    // Helper function to normalize angle difference
    fun normalizeAngleDiff(angle1: Float, angle2: Float): Float {
        val diff = abs(angle1 - angle2)
        return minOf(diff, (2 * PI).toFloat() - diff)
    }

    // Find the corner point (where direction changes most)
    var maxDirectionChange = 0f
    var cornerIndex = 0

    for (i in 3 until points.size - 3) {
        val beforeStartIdx = (i - 3).coerceAtLeast(0)
        val afterEndIdx = (i + 3).coerceAtMost(points.size - 1)

        val beforeAngle = atan2(
            points[i - 1].y - points[beforeStartIdx].y,
            points[i - 1].x - points[beforeStartIdx].x
        )
        val afterAngle = atan2(
            points[afterEndIdx].y - points[i].y,
            points[afterEndIdx].x - points[i].x
        )

        val directionChange = normalizeAngleDiff(beforeAngle, afterAngle)
        if (directionChange > maxDirectionChange) {
            maxDirectionChange = directionChange
            cornerIndex = i
        }
    }

    // Require significant direction change (close to 90 degrees)
    if (maxDirectionChange < PI / 3) return null

    // Calculate movements without creating sublists
    val firstStartIdx = 0
    val firstEndIdx = cornerIndex
    val secondStartIdx = cornerIndex
    val secondEndIdx = points.size

    val firstVertical = points[firstEndIdx - 1].y - points[firstStartIdx].y
    val firstHorizontal = points[firstEndIdx - 1].x - points[firstStartIdx].x
    val secondVertical = points[secondEndIdx - 1].y - points[secondStartIdx].y
    val secondHorizontal = points[secondEndIdx - 1].x - points[secondStartIdx].x

    val threshold = 1.2f

    // Check if segments are long enough
    val firstSegmentLength = kotlin.math.sqrt(firstVertical * firstVertical + firstHorizontal * firstHorizontal)
    val secondSegmentLength = kotlin.math.sqrt(secondVertical * secondVertical + secondHorizontal * secondHorizontal)

    if (firstSegmentLength < swipeThreshold || secondSegmentLength < swipeThreshold) {
        return null
    }
    // Determine pattern type
    if (abs(firstVertical) > abs(firstHorizontal) * threshold &&
        abs(secondHorizontal) > abs(secondVertical) * threshold) {

        return when {
            firstVertical > 0 && secondHorizontal > 0 -> LPatternType.DOWN_RIGHT
            firstVertical > 0 && secondHorizontal < 0 -> LPatternType.DOWN_LEFT
            firstVertical < 0 && secondHorizontal > 0 -> LPatternType.UP_RIGHT
            firstVertical < 0 && secondHorizontal < 0 -> LPatternType.UP_LEFT
            else -> null
        }
    }

    if (abs(firstHorizontal) > abs(firstVertical) * threshold &&
        abs(secondVertical) > abs(secondHorizontal) * threshold) {

        return when {
            firstHorizontal > 0 && secondVertical > 0 -> LPatternType.RIGHT_DOWN
            firstHorizontal > 0 && secondVertical < 0 -> LPatternType.RIGHT_UP
            firstHorizontal < 0 && secondVertical > 0 -> LPatternType.LEFT_DOWN
            firstHorizontal < 0 && secondVertical < 0 -> LPatternType.LEFT_UP
            else -> null
        }
    }

    return null
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
    // Check if aspect ratio is close to square
    val aspectRatio = width / height
    if (aspectRatio !in 0.6f..1.6f) return false

    // Calculate center
    val centerX = points.map { it.x }.average().toFloat()
    val centerY = points.map { it.y }.average().toFloat()
    val center = Offset(centerX, centerY)

    // Calculate distances from center
    val distances = points.map { point ->
        kotlin.math.sqrt(
            (point.x - center.x) * (point.x - center.x) +
                    (point.y - center.y) * (point.y - center.y)
        )
    }

    val avgDistance = distances.average().toFloat()
    val variance = distances.map { (it - avgDistance) * (it - avgDistance) }.average()
    val stdDev = kotlin.math.sqrt(variance).toFloat()

    // Low standard deviation indicates circular path
    if (stdDev / avgDistance > 0.25f) return false

    // Check if the path is closed (start and end points are relatively close)
    val startPoint = points.first()
    val endPoint = points.last()
    val closureDistance = kotlin.math.sqrt(
        (startPoint.x - endPoint.x) * (startPoint.x - endPoint.x) +
                (startPoint.y - endPoint.y) * (startPoint.y - endPoint.y)
    )
    if (closureDistance > avgDistance * 1.2f) return false

    // Check total angle swept to ensure it's a loop, not just a small arc
    var totalAngle = 0f
    for (i in 0 until points.size - 1) {
        val p1 = Offset(points[i].x - center.x, points[i].y - center.y)
        val p2 = Offset(points[i + 1].x - center.x, points[i + 1].y - center.y)
        var angle = atan2(p2.y, p2.x) - atan2(p1.y, p1.x)
        if (angle > PI) angle -= 2 * PI.toFloat()
        if (angle < -PI) angle += 2 * PI.toFloat()
        totalAngle += angle
    }

    return abs(totalAngle) > 1.5 * PI.toFloat()
}
