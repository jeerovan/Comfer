package com.jeerovan.comfer.notifications

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.jeerovan.comfer.R
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

/** Local copy interactions never act on a live Android notification. */
@Composable
internal fun SavedNotificationCard(
    record: SavedNotification,
    selectionMode: Boolean = false,
    selected: Boolean = false,
    enabled: Boolean = true,
    onSelect: () -> Unit = {},
    onOpen: () -> Unit = {},
    onDismiss: suspend () -> Boolean,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val dismiss by rememberUpdatedState(onDismiss)
    val selectLabel = stringResource(R.string.notification_select)
    val dismissLabel = stringResource(R.string.notification_saved_dismiss)
    val preview = remember(record.id, record.text) { NotificationBodyPreviewState() }
    NotificationSwipeContainer(enabled = enabled, onDismiss = onDismiss) {
        Card(Modifier.fillMaxWidth().animateContentSize(tween(250)).testTag("saved-copy:${record.id}")
            .combinedClickable(enabled = enabled, onClick = { if (selectionMode) onSelect() else preview.tap(onOpen) }, onLongClick = { if (!selected) onSelect() }).semantics {
            customActions = listOf(CustomAccessibilityAction(dismissLabel) {
                if (enabled) scope.launch { dismiss() }; enabled
            })
        }, colors = CardDefaults.cardColors(containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer)) {
            Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(48.dp).then(if (selectionMode) Modifier.testTag("saved-select:${record.id}").semantics { contentDescription = selectLabel }
                    .toggleable(selected, enabled = enabled, role = Role.Checkbox, onValueChange = { onSelect() }) else Modifier), contentAlignment = Alignment.Center) {
                    if (selectionMode) NotificationRingDot(selected)
                }
                Column(Modifier.weight(1f)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(record.title.ifBlank { stringResource(R.string.notification_preview_unavailable) }, modifier = Modifier.weight(1f).alignByBaseline(), style = MaterialTheme.typography.titleSmall)
                        Text(DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(record.postedAt)), modifier = Modifier.alignByBaseline(), style = MaterialTheme.typography.labelMedium, maxLines = 1)
                    }
                    Text("${appLabel(context, record.app)} · ${DateFormat.getDateInstance().format(Date(record.postedAt))}", style = MaterialTheme.typography.labelMedium)
                    NotificationBodyPreview(record.text, preview)
                }
            }
        }
    }
}
