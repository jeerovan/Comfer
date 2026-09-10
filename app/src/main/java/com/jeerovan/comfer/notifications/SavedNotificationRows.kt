package com.jeerovan.comfer.notifications

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.triStateToggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp

internal data class SavedNotificationRow(val key: String, val appId: String, val record: SavedNotification? = null)

internal fun savedNotificationRows(records: List<SavedNotification>, chronological: Boolean, collapsed: Set<String>, pinned: Set<String>): List<SavedNotificationRow> {
    val sorted = records.sortedByDescending { it.postedAt }
    if (chronological) return sorted.map { SavedNotificationRow("saved:${it.id}", it.appId, it) }
    return sorted.groupBy { it.appId }.entries
        .sortedWith(compareByDescending<Map.Entry<String, List<SavedNotification>>> { it.key in pinned }.thenBy { it.key })
        .flatMap { (app, copies) ->
            listOf(SavedNotificationRow("saved-group:$app", app)) +
                if (app in collapsed) emptyList() else copies.map { SavedNotificationRow("saved:${it.id}", app, it) }
        }
}

internal fun LazyListScope.savedNotificationItems(
    copies: List<SavedNotification>, chronological: Boolean, collapsed: Set<String>, pinned: Set<String>,
    selected: Set<String>, enabled: Boolean,
    onCollapse: (String) -> Unit, onSelect: (String) -> Unit, onGroupSelect: (String) -> Unit,
    onOpen: (SavedNotification) -> Unit, onDelete: suspend (SavedNotification) -> Boolean,
) {
    items(savedNotificationRows(copies, chronological, collapsed, pinned), key = { it.key }) { row ->
        val context = LocalContext.current
        Box(Modifier.animateItem(fadeInSpec = tween(200), placementSpec = tween(250), fadeOutSpec = tween(200))) {
            val copy = row.record
            if (copy != null) {
                SavedNotificationCard(copy, selectionMode = selected.isNotEmpty(), selected = copy.id in selected,
                    enabled = enabled, onSelect = { onSelect(copy.id) }, onOpen = { onOpen(copy) }, onDismiss = { onDelete(copy) })
            } else {
                val expanded = row.appId !in collapsed
                val rotation by animateFloatAsState(if (expanded) 180f else 0f, tween(250), label = "saved-group-caret")
                val group = copies.filter { it.appId == row.appId }
                val count = group.count { it.id in selected }
                val state = when (count) { 0 -> ToggleableState.Off; group.size -> ToggleableState.On; else -> ToggleableState.Indeterminate }
                Row(Modifier.fillMaxWidth().testTag(row.key), verticalAlignment = Alignment.CenterVertically) {
                    Row(Modifier.weight(1f).heightIn(min = 48.dp).testTag("saved-group-select:${row.appId}")
                        .then(if (selected.isNotEmpty()) Modifier.triStateToggleable(state, enabled = enabled, role = Role.Checkbox, onClick = { onGroupSelect(row.appId) }) else Modifier)
                        .padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(appLabel(context, row.appId.substringAfter(':')), style = MaterialTheme.typography.titleMedium,
                            color = if (state == ToggleableState.On) MaterialTheme.colorScheme.primary else LocalContentColor.current)
                    }
                    IconButton(onClick = { onCollapse(row.appId) }, modifier = Modifier.testTag("saved-group-expand:${row.appId}")) {
                        Icon(Icons.Outlined.KeyboardArrowDown, if (expanded) "Collapse" else "Expand", Modifier.rotate(rotation))
                    }
                }
            }
        }
    }
}
