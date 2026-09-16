package com.jeerovan.comfer.ui

import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp

/** Starts at full height. Only a pull left over at the scroll boundary reveals reach space. */
internal class ThumbReachState(private var limit: Float) : NestedScrollConnection {
    var offset by mutableFloatStateOf(0f)
        private set
    // Toolbar/keyboard resizing must not discard a user's pulled-down position.
    // The new limit constrains further pulling; upward gestures still consume the offset.
    fun updateLimit(value: Float) {
        limit = value.coerceAtLeast(0f)
        if (limit == 0f) offset = 0f
    }
    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        if(available.y >= 0f || offset <= 0f) return Offset.Zero
        val consumed = available.y.coerceAtLeast(-offset)
        offset += consumed
        return Offset(0f, consumed)
    }
    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
        if(source != NestedScrollSource.UserInput || available.y <= 0f) return Offset.Zero
        val used = available.y.coerceAtMost((limit - offset).coerceAtLeast(0f))
        offset += used
        return Offset(0f, used)
    }
}

@Composable
internal fun rememberThumbReach(maxPadding: Dp, screenKey: Any? = null): ThumbReachState {
    val limit = with(LocalDensity.current) { maxPadding.toPx().coerceAtLeast(0f) }
    val state = remember(screenKey) { ThumbReachState(limit) }
    SideEffect { state.updateLimit(limit) }
    return state
}
