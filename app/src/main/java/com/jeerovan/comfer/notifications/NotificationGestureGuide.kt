package com.jeerovan.comfer.notifications

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.jeerovan.comfer.R
import kotlinx.coroutines.delay

internal enum class NotificationGuide(val label: Int) {
    RULE_SWIPE(R.string.notification_rule_swipe_guide),
    CARD_HOLD(R.string.notification_card_hold_guide),
    CARD_SWIPE(R.string.notification_card_swipe_guide),
}

internal fun finishNotificationGuide(context: Context, kind: NotificationGuide) {
    context.getSharedPreferences("notification_gesture_guides", Context.MODE_PRIVATE).edit().putBoolean(kind.name, true).apply()
}

/** Device-local learning state, separate from backed-up notification configuration. */
@Composable
internal fun NotificationGuideTarget(
    guides: List<NotificationGuide>,
    enabled: Boolean,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val prefs = remember(context) { context.getSharedPreferences("notification_gesture_guides", Context.MODE_PRIVATE) }
    var revision by remember { mutableIntStateOf(0) }
    DisposableEffect(prefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> revision++ }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    val kind = remember(guides, enabled, revision) { if (enabled) guides.firstOrNull { !prefs.getBoolean(it.name, false) } else null }
    var visible by remember { mutableStateOf(false) }
    val lifecycle = LocalLifecycleOwner.current
    LaunchedEffect(kind, visible, lifecycle) {
        if (kind != null && visible) lifecycle.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            delay(6000)
            prefs.edit().putBoolean(kind.name, true).apply()
        }
    }
    Box(Modifier.fillMaxWidth().onGloballyPositioned { visible = it.boundsInWindow().height >= 48 * context.resources.displayMetrics.density }) {
        content()
        if (kind != null && visible) key(kind) {
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
            Column(Modifier.align(Alignment.Center).offset(x = if (kind == NotificationGuide.CARD_HOLD) 0.dp else (direction * 64 * travel).dp)
                .graphicsLayer { scaleX = press; scaleY = press }
                .background(Color.Black.copy(alpha = .8f), RoundedCornerShape(16.dp)).padding(8.dp)
                .testTag("notification-guide-${kind.name}"), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Filled.TouchApp, contentDescription = null, tint = Color.White, modifier = Modifier.size(32.dp))
                Text(stringResource(kind.label), color = Color.White)
            }
        }
    }
}
