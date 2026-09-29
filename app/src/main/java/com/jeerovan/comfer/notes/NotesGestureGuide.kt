package com.jeerovan.comfer.notes

import android.content.Context
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.jeerovan.comfer.LongPressHint
import kotlinx.coroutines.delay

internal const val NOTES_GUIDE_PREFERENCES = "notes_gesture_guides"
internal enum class NotesGuide { HOLD, DRAG }

/** Device-local learning state, independent of the notes database and backups. */
@Stable
internal class NotesGuideProgress(context: Context) {
    private val prefs = context.getSharedPreferences(NOTES_GUIDE_PREFERENCES, Context.MODE_PRIVATE)
    private var held by mutableStateOf(prefs.getBoolean("hold", false))
    private var dragged by mutableStateOf(prefs.getBoolean("drag", false))

    fun next(count: Int): NotesGuide? = when {
        count == 0 -> null
        !held -> NotesGuide.HOLD
        count >= 2 && !dragged -> NotesGuide.DRAG
        else -> null
    }

    fun performedHold() { held = true; prefs.edit().putBoolean("hold", true).apply() }
    fun finishedDragGuide() { dragged = true; prefs.edit().putBoolean("drag", true).apply() }
}

/** The five-second timer runs only while the guide is visible and the screen is resumed. */
@Composable
internal fun NotesGestureGuide(kind: NotesGuide, distance: Offset, modifier: Modifier = Modifier, onFinished: () -> Unit) {
    val finish by rememberUpdatedState(onFinished)
    val lifecycle = LocalLifecycleOwner.current
    LaunchedEffect(kind, lifecycle) {
        if (kind == NotesGuide.DRAG) lifecycle.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            delay(5000)
            finish()
        }
    }
    LongPressHint(
        modifier.testTag("notes-guide-${kind.name.lowercase()}"),
        dragDistance = if (kind == NotesGuide.DRAG) distance.y else 0f,
        dragDistanceX = if (kind == NotesGuide.DRAG) distance.x else 0f,
        size = 48.dp,
    )
}
