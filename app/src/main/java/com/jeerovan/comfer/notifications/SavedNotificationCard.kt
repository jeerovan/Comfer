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
                Text(appLabel(context, record.app), style = MaterialTheme.typography.labelMedium)
                Text(DateFormat.getDateTimeInstance().format(Date(record.postedAt)), style = MaterialTheme.typography.bodySmall)
                if (record.title.isNotBlank()) Text(record.title, style = MaterialTheme.typography.titleSmall)
                Text(record.text)
            }
        }
    }
}
