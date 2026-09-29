package com.jeerovan.comfer

import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/** Shared, input-transparent demonstration. Only real gestures produce haptics. */
@Composable
fun LongPressHint(
    modifier: Modifier = Modifier,
    dragDistance: Float = 0f,
    contentDescription: String? = null,
    size: Dp = 58.dp,
) {
    val resolver = LocalContext.current.contentResolver
    var animated by remember(resolver) {
        mutableStateOf(Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f)
    }
    DisposableEffect(resolver) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                animated = Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
            }
        }
        resolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, observer)
        onDispose { resolver.unregisterContentObserver(observer) }
    }
    val holdEnd = 150 + LocalViewConfiguration.current.longPressTimeoutMillis.toInt()
    val cycleEnd = holdEnd + 1400
    var hold = 1f
    var travel = 0f
    var opacity = 1f
    if (animated) {
        val transition = rememberInfiniteTransition(label = "holdGuide")
        val progress by transition.animateFloat(0f, 1f, infiniteRepeatable(keyframes {
            durationMillis = cycleEnd
            0f at 0
            0f at 150
            1f at holdEnd using LinearEasing
            1f at cycleEnd
        }), label = "holdProgress")
        val movement by transition.animateFloat(0f, 1f, infiniteRepeatable(keyframes {
            durationMillis = cycleEnd
            0f at 0
            0f at holdEnd + 200
            1f at holdEnd + 900 using FastOutSlowInEasing
            1f at cycleEnd
        }), label = "dragAfterHold")
        val alpha by transition.animateFloat(1f, 0f, infiniteRepeatable(keyframes {
            durationMillis = cycleEnd
            1f at 0
            1f at cycleEnd - 200
            0f at cycleEnd
        }), label = "release")
        hold = progress
        travel = movement
        opacity = alpha
    }
    Box(
        modifier = Modifier.offset { IntOffset(0, (dragDistance * travel).roundToInt()) }.then(modifier)
            .graphicsLayer { alpha = opacity }
            .background(Color.Black.copy(alpha = .7f), CircleShape).padding(size * (4f / 58f)),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(
            progress = { hold }, modifier = Modifier.size(size * (50f / 58f)), color = Color.White,
            strokeWidth = 2.dp, trackColor = ProgressIndicatorDefaults.circularIndeterminateTrackColor,
            strokeCap = StrokeCap.Round,
        )
        Icon(Icons.Filled.TouchApp, contentDescription, tint = Color.White,
            modifier = Modifier.size(size * (40f / 58f)).graphicsLayer { scaleX = 1f - .2f * hold; scaleY = scaleX })
    }
}
