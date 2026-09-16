package com.jeerovan.comfer.journals

import android.content.Context
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
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

internal enum class JournalGuide { EDIT, DELETE, DATE }

/** Local onboarding progress: elapsed time, navigation and Undo never complete a step. */
@Stable
internal class JournalGuideProgress(context: Context) {
    private val preferences = context.getSharedPreferences("journal_gesture_guides", Context.MODE_PRIVATE)
    private var step by mutableIntStateOf(preferences.getInt("step", -1).coerceIn(-1, 3))
    val current: JournalGuide? get() = JournalGuide.entries.getOrNull(step)
    fun activate() { if (step == -1) save(0) }
    fun performed(action: JournalGuide) { if (current == action) save(step + 1) }
    private fun save(value: Int) { step = value; preferences.edit().putInt("step", value).apply() }
}

/** Decorative and input-transparent, with no labels, dismissal or completion timer. */
@Composable
internal fun JournalGestureGuide(kind: JournalGuide, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "journal-guide")
    val travel by transition.animateFloat(0f, 0f, infiniteRepeatable(keyframes {
        durationMillis = 2400
        0f at 0
        0f at 250
        -1f at 900 using FastOutSlowInEasing
        0f at 1300 using FastOutSlowInEasing
        (if (kind == JournalGuide.DATE) 1f else 0f) at 1950 using FastOutSlowInEasing
        0f at 2400 using FastOutSlowInEasing
    }), label = "swipe")
    val press by transition.animateFloat(1f, 1f, infiniteRepeatable(keyframes {
        durationMillis = 1200
        1f at 0
        .8f at 250
        1f at 500
        1f at 1200
    }), label = "tap")
    val direction = if (LocalLayoutDirection.current == LayoutDirection.Rtl) -1 else 1
    Box(modifier.offset(x = (if (kind == JournalGuide.EDIT) 0f else travel * 56f * direction).dp)
        .size(40.dp).graphicsLayer {
            scaleX = if (kind == JournalGuide.EDIT) press else 1f
            scaleY = scaleX
        }.background(Color.Black.copy(alpha = .65f), CircleShape)
        .testTag("journal-guide-${kind.name.lowercase()}"), contentAlignment = Alignment.Center) {
        Icon(Icons.Filled.TouchApp, contentDescription = null, tint = Color.White)
    }
}
