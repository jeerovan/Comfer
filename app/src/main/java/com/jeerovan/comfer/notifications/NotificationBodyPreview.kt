package com.jeerovan.comfer.notifications

import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.text.style.TextOverflow

@Stable
internal class NotificationBodyPreviewState {
    var expanded by mutableStateOf(false)
    var overflows by mutableStateOf(false)
    fun tap(open: () -> Unit) {
        if (!expanded && overflows) expanded = true else open()
    }
}

@Composable
internal fun NotificationBodyPreview(text: String, state: NotificationBodyPreviewState) {
    Text(text, minLines = 1, maxLines = if (state.expanded) Int.MAX_VALUE else 1,
        overflow = TextOverflow.Ellipsis,
        onTextLayout = { if (!state.expanded) state.overflows = it.hasVisualOverflow })
}
