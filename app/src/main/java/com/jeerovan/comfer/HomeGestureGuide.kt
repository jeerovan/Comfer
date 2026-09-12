package com.jeerovan.comfer

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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

internal enum class HomeGuideStep(val preferenceKey: String) {
    SWIPE_UP("quick_apps_swipe"),
    SETTINGS("settings_long_press_key"),
    WIDGETS("widgets_long_press_key"),
    RECENTS("double_tap_recent_apps_gesture_key"),
    INBOX("notification_inbox_return_gesture_key"),
    CLOCK_TAP("widget_clock_tap_key"),
    CLOCK_LONG_PRESS("widget_clock_long_press_key"),
}

/** One active home guide; optional clock steps never block a clock-free home. */
internal fun nextHomeGuideStep(
    completed: Set<HomeGuideStep>,
    hasClock: Boolean,
): HomeGuideStep? = HomeGuideStep.entries.firstOrNull { step ->
    step !in completed && when (step) {
        HomeGuideStep.CLOCK_TAP, HomeGuideStep.CLOCK_LONG_PRESS -> hasClock
        else -> true
    }
}

/** Decorative only: the real home gesture surface continues to receive input. */
@Composable
internal fun InboxGestureHint(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "inboxGuide")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(keyframes {
            durationMillis = 1700
            0f at 0
            0f at 150
            1f at 650 using LinearEasing
            0f at 1150 using LinearEasing
            0f at 1700
        }),
        label = "downAndReturn",
    )
    BoxWithConstraints(modifier.requiredSize(48.dp, 168.dp).testTag("home-inbox-guide")) {
        val travel = constraints.maxHeight - constraints.maxWidth
        Box(
            Modifier.offset { IntOffset(0, (travel * progress).roundToInt()) }
                .size(48.dp).background(Color.Black.copy(alpha = .7f), CircleShape)
                .testTag("home-inbox-guide-hand"),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.TouchApp, contentDescription = null, tint = Color.White)
        }
    }
}
