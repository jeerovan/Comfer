package com.jeerovan.comfer.tasks

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.jeerovan.comfer.R
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import androidx.lifecycle.repeatOnLifecycle
import java.time.LocalDate

@Composable
internal fun TaskHomePanel() {
    val context = LocalContext.current
    val state by TaskStore.state.collectAsState()
    val scope = rememberCoroutineScope()
    if(!state.preferences.panelEnabled) return
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current
    LaunchedEffect(lifecycle) { lifecycle.lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) { while(true) { now = System.currentTimeMillis(); delay(60000) } } }
    val today = java.time.Instant.ofEpochMilli(now).atZone(java.time.ZoneId.systemDefault()).toLocalDate().toEpochDay()
    val active = state.tasks.filter { it.completedAt == null }
    val tasks = active.filter { when(state.preferences.panelView) { "starred" -> it.starred; "selected" -> it.listId == state.preferences.selectedList; else -> it.day == today } }.sortedBy { it.position }
    Surface(Modifier.padding(horizontal = 16.dp).widthIn(max = 440.dp), shape = MaterialTheme.shapes.large, tonalElevation = 3.dp) {
        Column(Modifier.padding(horizontal = 12.dp)) {
            Row {
                TextButton(onClick = { TasksActivity.open(context) }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.tasks_title)) }
                TextButton(onClick = {
                    val intent = android.content.Intent(context, TasksActivity::class.java).putExtra("add", true).putExtra("star", state.preferences.panelView == "starred")
                    if(state.preferences.panelView == "today") intent.putExtra("day", today)
                    context.startActivity(intent)
                }) { Text(stringResource(R.string.tasks_add)) }
            }
            Text(stringResource(R.string.tasks_counts, active.count { it.isOverdue(now) }, active.count { (it.day ?: Long.MIN_VALUE) > today }), style = MaterialTheme.typography.bodySmall)
            tasks.take(2).forEach { item -> Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Checkbox(false, { if(state.tasks.any { it.parentId == item.id && it.completedAt == null }) TasksActivity.open(context, item.id)
                    else scope.launch { runCatching { TaskStore.change(context) { it.completeTask(item.id, true) } }.onFailure { android.widget.Toast.makeText(context, it.localizedMessage, android.widget.Toast.LENGTH_LONG).show() } } })
                TextButton(onClick = { TasksActivity.open(context, item.id) }, modifier = Modifier.weight(1f)) { Text(item.title, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) }
            } }
        }
    }
}
