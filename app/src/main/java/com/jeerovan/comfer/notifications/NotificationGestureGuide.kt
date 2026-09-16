package com.jeerovan.comfer.notifications

import android.content.Context
import android.content.SharedPreferences
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
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.jeerovan.comfer.LongPressHint

internal enum class NotificationGuide { RULE_SWIPE, CARD_HOLD, CARD_SWIPE }

// Versioned because the old flags also counted elapsed time as learning an action.
internal const val NOTIFICATION_GUIDE_PREFERENCES = "notification_gesture_guides_actions"

internal fun finishNotificationGuide(context: Context, kind: NotificationGuide) {
    val prefs = context.getSharedPreferences(NOTIFICATION_GUIDE_PREFERENCES, Context.MODE_PRIVATE)
    if (kind == NotificationGuide.CARD_SWIPE && !prefs.getBoolean(NotificationGuide.CARD_HOLD.name, false)) return
    prefs.edit().putBoolean(kind.name, true).apply()
}

/** Device-local learning state, separate from backed-up notification configuration. */
@Composable
internal fun NotificationGuideTarget(
    guides: List<NotificationGuide>,
    enabled: Boolean,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val prefs = remember(context) { context.getSharedPreferences(NOTIFICATION_GUIDE_PREFERENCES, Context.MODE_PRIVATE) }
    var revision by remember { mutableIntStateOf(0) }
    DisposableEffect(prefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> revision++ }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    val kind = remember(guides, enabled, revision) { if (enabled) guides.firstOrNull {
        !prefs.getBoolean(it.name, false) &&
            (it != NotificationGuide.CARD_SWIPE || prefs.getBoolean(NotificationGuide.CARD_HOLD.name, false))
    } else null }
    var visible by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth().onGloballyPositioned { visible = it.boundsInWindow().height >= 48 * context.resources.displayMetrics.density }) {
        content()
        if (kind != null && visible) key(kind) {
            if (kind == NotificationGuide.CARD_HOLD) {
                LongPressHint(Modifier.align(Alignment.Center).testTag("notification-guide-CARD_HOLD"))
            } else {
                val transition = rememberInfiniteTransition(label = "notificationGuide")
                val travel by transition.animateFloat(0f, 1f, infiniteRepeatable(keyframes {
                    durationMillis = 2000
                    0f at 0; 0f at 250; 1f at 1050 using FastOutSlowInEasing; 1f at 1550; 0f at 2000
                }), label = "swipe")
                val press by transition.animateFloat(.85f, 1f, infiniteRepeatable(keyframes {
                    durationMillis = 2000
                    .85f at 0; 1f at 300; 1f at 1400; .85f at 1800
                }), label = "hold")
                val direction = if (LocalLayoutDirection.current == LayoutDirection.Rtl) 1 else -1
                Box(Modifier.align(Alignment.Center).offset(x = (direction * 64 * travel).dp)
                    .graphicsLayer { scaleX = press; scaleY = press }
                    .size(40.dp).background(Color.Black.copy(alpha = .65f), CircleShape)
                    .testTag("notification-guide-${kind.name}"), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.TouchApp, contentDescription = null, tint = Color.White)
                }
            }
        }
    }
}
