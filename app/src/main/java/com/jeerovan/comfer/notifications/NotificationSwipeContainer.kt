package com.jeerovan.comfer.notifications

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/** Keep the row in the list until the outward animation has completely finished. */
@Composable
internal fun NotificationSwipeContainer(
    enabled: Boolean,
    onDismiss: suspend () -> Boolean,
    content: @Composable () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val canDismiss by rememberUpdatedState(enabled)
    val dismiss by rememberUpdatedState(onDismiss)
    val density = LocalDensity.current.density
    var translation by remember { mutableFloatStateOf(0f) }
    var width by remember { mutableFloatStateOf(0f) }
    var leftExit by remember { mutableFloatStateOf(0f) }
    var rightExit by remember { mutableFloatStateOf(0f) }
    var exiting by remember { mutableStateOf(false) }
    var motion by remember { mutableStateOf<Job?>(null) }

    fun settle(velocity: Float, cancelled: Boolean) {
        if (exiting) return
        motion?.cancel()
        val fastEnough = abs(velocity) > 500f * density && velocity * translation > 0f
        val shouldDismiss = !cancelled && canDismiss && width > 0f &&
            (abs(translation) > width * .6f || fastEnough)
        exiting = shouldDismiss
        motion = scope.launch {
            if (shouldDismiss) {
                val target = if (translation < 0f) leftExit else rightExit
                val duration = if (fastEnough) ((abs(target - translation) / abs(velocity)) * 1000).roundToInt().coerceIn(100, 400) else 200
                animate(translation, target, animationSpec = tween(durationMillis = duration)) { value, _ -> translation = value }
                // Allow the final off-screen position to render before Android removes the record.
                withFrameNanos { }
                val accepted = canDismiss && dismiss()
                // A listener callback normally removes this composition. Recover if Android
                // rejects the action or never confirms it, rather than leaving a blank row.
                if (accepted) delay(1500)
            }
            animate(translation, 0f, animationSpec = spring()) { value, _ -> translation = value }
            exiting = false
        }
    }

    Box(Modifier.fillMaxWidth().onGloballyPositioned { coordinates ->
        width = coordinates.size.width.toFloat()
        val left = coordinates.positionInRoot().x
        leftExit = -(left + width)
        rightExit = coordinates.findRootCoordinates().size.width - left
    }.pointerInput(density) {
        val tracker = VelocityTracker()
        var distance = 0f
        detectHorizontalDragGestures(
            onDragStart = {
                if (!exiting) {
                    motion?.cancel()
                    distance = translation
                    tracker.resetTracking()
                }
            },
            onDragCancel = { settle(0f, cancelled = true) },
            onDragEnd = { settle(tracker.calculateVelocity().x.coerceIn(-4000f * density, 4000f * density), cancelled = false) },
        ) { change, amount ->
            if (!exiting) {
                distance += amount
                tracker.addPosition(change.uptimeMillis, Offset(distance, 0f))
                translation = if (canDismiss) distance else (distance * .2f).coerceIn(-width * .3f, width * .3f)
            }
            change.consume()
        }
    }) {
        Box(Modifier.offset { IntOffset(translation.roundToInt(), 0) }) { content() }
    }
}
