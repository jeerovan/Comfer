package com.jeerovan.comfer.tasks

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.jeerovan.comfer.R
import kotlinx.coroutines.delay

internal enum class TaskGuide { SWIPE, REORDER, LIST }

internal fun nextTaskGuide(tasks: List<TaskItem>, preferences: TaskPreferences): TaskGuide? {
    val incomplete = tasks.filter { it.completedAt == null }
    if(incomplete.isEmpty()) return null
    if(!preferences.swipeGuideShown) return TaskGuide.SWIPE
    if(!preferences.reorderGuideShown && preferences.sort == "manual" && incomplete.groupBy { it.listId to it.parentId }.any { it.value.size >= 2 }) return TaskGuide.REORDER
    return null
}

/** Input-transparent demonstration: never deletes, reorders or consumes a touch. */
@Composable
internal fun TaskGestureGuide(kind: TaskGuide, targetDistance: Float, modifier: Modifier = Modifier, onShown: () -> Unit) {
    val shown by rememberUpdatedState(onShown)
    val lifecycle = LocalLifecycleOwner.current
    LaunchedEffect(kind, lifecycle) {
        lifecycle.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            delay(6000)
            shown()
        }
    }
    val transition = rememberInfiniteTransition(label = "taskGestureGuide")
    val progress by transition.animateFloat(0f, 1f, infiniteRepeatable(keyframes {
        durationMillis = 2000
        0f at 0
        0f at (if(kind == TaskGuide.REORDER) 700 else 150)
        1f at (if(kind == TaskGuide.REORDER) 1400 else 850) using FastOutSlowInEasing
        1f at 1650
        0f at 2000
    }), label = "handTravel")
    val press by transition.animateFloat(0.85f, 1f, infiniteRepeatable(keyframes {
        durationMillis = 2000
        0.85f at 0
        1f at 300
        1f at (if(kind == TaskGuide.LIST) 400 else 1650)
        0.85f at (if(kind == TaskGuide.LIST) 650 else 2000)
    }), label = "holdPulse")
    val swipeDistance = with(LocalDensity.current) { 76.dp.toPx() } * if(LocalLayoutDirection.current == LayoutDirection.Rtl) 1 else -1
    Box(modifier.size(48.dp).offset {
        androidx.compose.ui.unit.IntOffset(
            if(kind == TaskGuide.SWIPE) (swipeDistance * progress).toInt() else 0,
            if(kind == TaskGuide.REORDER) (targetDistance * progress).toInt() else 0,
        )
    }.graphicsLayer {
        scaleX = press; scaleY = press
    }.background(Color.Black.copy(alpha = .7f), CircleShape).testTag("tasks-guide-${kind.name.lowercase()}"), contentAlignment = Alignment.Center) {
        Icon(Icons.Filled.TouchApp, stringResource(when(kind) { TaskGuide.SWIPE -> R.string.tasks_swipe_hint; TaskGuide.REORDER -> R.string.tasks_reorder_hint; TaskGuide.LIST -> R.string.tasks_list_hint }), tint = Color.White)
    }
}
