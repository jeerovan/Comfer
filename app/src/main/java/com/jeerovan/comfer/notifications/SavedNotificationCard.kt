package com.jeerovan.comfer.notifications

import androidx.compose.foundation.layout.*
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

/** A text-only local copy: its sole action deletes this copy, never the source notification. */
@Composable
internal fun SavedNotificationCard(record: SavedNotification, onDismiss: suspend () -> Boolean) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val dismiss by rememberUpdatedState(onDismiss)
    val dismissLabel = stringResource(R.string.notification_saved_dismiss)
    NotificationSwipeContainer(enabled = true, onDismiss = onDismiss) {
        OutlinedCard(Modifier.fillMaxWidth().testTag("saved-copy:${record.id}").semantics {
            customActions = listOf(CustomAccessibilityAction(dismissLabel) {
                scope.launch { dismiss() }; true
            })
        }) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(record.title.ifBlank { stringResource(R.string.notification_preview_unavailable) }, modifier = Modifier.weight(1f).alignByBaseline(), style = MaterialTheme.typography.titleSmall)
                    Text(DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(record.postedAt)), modifier = Modifier.alignByBaseline(), style = MaterialTheme.typography.labelMedium, maxLines = 1)
                }
                Text("${appLabel(context, record.app)} · ${DateFormat.getDateInstance().format(Date(record.postedAt))}", style = MaterialTheme.typography.labelMedium)
                Text(record.text)
            }
        }
    }
}
