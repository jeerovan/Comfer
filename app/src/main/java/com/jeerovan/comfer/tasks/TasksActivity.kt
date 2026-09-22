@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.jeerovan.comfer.tasks

import com.jeerovan.comfer.ui.ModuleIconButton as IconButton
import com.jeerovan.comfer.ui.ModuleIconToggleButton as IconToggleButton

import com.jeerovan.comfer.ui.rememberThumbReach
import androidx.compose.ui.input.nestedscroll.nestedScroll

import android.app.ActivityOptions
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.ui.semantics.onClick
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.jeerovan.comfer.R
import com.jeerovan.comfer.notifications.reachableHeightDp
import com.jeerovan.comfer.ui.theme.ComferTheme
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.serialization.encodeToString
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

class TasksActivity : AppCompatActivity() {
    override fun onResume() { super.onResume(); TaskReminders.request(this) }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val shared = if (intent.action == Intent.ACTION_SEND) intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()?.take(100000) else null
        setContent { ComferTheme { TasksScreen(::finish, shared, intent.getStringExtra("task"), intent.getBooleanExtra("add", false), intent.getBooleanExtra("complete", false), intent.getBooleanExtra("star", false), if(intent.hasExtra("day")) intent.getLongExtra("day", 0) else null) } }
    }
    companion object {
        fun open(context: Context, task: String? = null) {
            val intent = Intent(context, TasksActivity::class.java).putExtra("task", task).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            val transition = ActivityOptions.makeCustomAnimation(context, R.anim.notification_inbox_enter, R.anim.notification_inbox_underlay)
            context.startActivity(intent, transition.toBundle())
        }
    }
}

internal fun dateLabel(day: Long): String = LocalDate.ofEpochDay(day).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
internal fun timeLabel(minute: Int): String = LocalTime.of(minute / 60, minute % 60).format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))

@Composable
internal fun TasksScreen(onFinish: () -> Unit, shared: String? = null, initialTask: String? = null, quickAdd: Boolean = false, confirmComplete: Boolean = false, initialStarred: Boolean = false, quickDay: Long? = null) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(lifecycle) {
        lifecycle.lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
            while(true) { now = System.currentTimeMillis(); TaskReminders.request(context); delay(60000) }
        }
    }
    val state by TaskStore.state.collectAsState()
    var ready by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var route by rememberSaveable { mutableStateOf("browse") }
    var view by rememberSaveable { mutableStateOf(if(initialStarred) "starred" else "selected") }
    var searching by rememberSaveable { mutableStateOf(false) }
    val keyboard=LocalSoftwareKeyboardController.current
    val focusManager=LocalFocusManager.current
    val inputView=LocalView.current
    var includeCompleted by rememberSaveable { mutableStateOf(false) }
    var completedExpanded by rememberSaveable { mutableStateOf(false) }
    var sheet by rememberSaveable { mutableStateOf<String?>(null) }
    var moveTaskId by rememberSaveable { mutableStateOf<String?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var listId by rememberSaveable { mutableStateOf("") }
    var listName by rememberSaveable { mutableStateOf("") }
    var destination by rememberSaveable { mutableStateOf("") }
    var deletingList by rememberSaveable { mutableStateOf(false) }
    var listOrder by rememberSaveable { mutableStateOf(listOf<String>()) }
    var draft by rememberSaveable { mutableStateOf<String?>(null) }
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingFocusId by rememberSaveable { mutableStateOf<String?>(null) }
    var menuId by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmation by rememberSaveable { mutableStateOf<String?>(null) }
    var undo by remember { mutableStateOf<TaskUndo?>(null) }
    var expanded by rememberSaveable { mutableStateOf(listOf<String>()) }
    var completingIds by remember { mutableStateOf(setOf<String>()) }
    var revealedId by rememberSaveable { mutableStateOf<String?>(null) }
    var revealDirection by rememberSaveable { mutableIntStateOf(0) }
    val savedViews = rememberSaveableStateHolder()
    var controlsHeight by remember { mutableIntStateOf(0) }
    LaunchedEffect(undo) { val token = undo; if(token != null) { delay(5000); if(undo === token) undo = null } }
    var undoHeight by remember { mutableIntStateOf(0) }
    val snackbar = remember { SnackbarHostState() }
    val selected = state.lists.find { it.id == state.preferences.selectedList } ?: state.lists.first()
    fun edit(item: TaskItem) { editingId = item.id; pendingFocusId = item.id; draft = taskJson.encodeToString(item); route = "edit" }
    fun add() = edit(TaskItem(listId = selected.id, title = "", starred = view == "starred", position = state.tasks.maxOfOrNull { it.position }?.plus(1) ?: 0))
    fun mutate(nextRoute: String? = null, success: (() -> Unit)? = null, deletion: Boolean = false, transform: (TaskSnapshot) -> TaskSnapshot) {
        if (busy) return
        busy = true
        scope.launch {
            try {
                val result = TaskStore.change(context, transform)
                val newlyCompleted = result.after.tasks.filter { next -> next.completedAt != null && result.before.tasks.any { old -> old.id == next.id && old.completedAt == null } }.map { it.id }.toSet()
                completingIds = completingIds + newlyCompleted
                if(result.before.tasks != result.after.tasks || result.before.lists != result.after.lists || result.before.series != result.after.series) {
                    if(deletion && (result.before.tasks.any { old -> result.after.tasks.none { it.id == old.id } } || result.before.lists.any { old -> result.after.lists.none { it.id == old.id } })) undo = result
                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                }
                if(pendingFocusId != null && result.after.tasks.none { it.id == pendingFocusId }) pendingFocusId = result.after.tasks.firstOrNull()?.id
                if (nextRoute == "choose") { route = "browse"; sheet = "choose" } else if (nextRoute != null) route = nextRoute
                menuId = null
                confirmation = null
                success?.invoke()
            } catch (e: Exception) { error = com.jeerovan.comfer.localizedModuleMessage(context.resources,e.localizedMessage) }
            finally { busy = false }
        }
    }
    fun complete(item: TaskItem) {
        if (item.completedAt == null && state.tasks.any { it.parentId == item.id && it.completedAt == null }) { menuId = item.id; confirmation = "complete" }
        else mutate { it.completeTask(item.id, item.completedAt == null) }
    }
    fun delete(item: TaskItem) {
        if(item.seriesId != null || state.tasks.any { it.parentId == item.id }) { menuId = item.id; confirmation = if(item.seriesId != null) "deleteScope" else "delete" }
        else mutate(deletion = true) { it.deleteTask(item.id) }
    }
    LaunchedEffect(Unit) {
        try {
            TaskStore.initialize(context)
            TaskStore.change(context) { it.materialize() }
            ready = true
            if (draft == null && shared != null) { edit(TaskItem(listId = TaskStore.state.value.preferences.selectedList, title = shared.lineSequence().firstOrNull().orEmpty().take(2000), notes = shared)) }
            else if (draft == null && initialTask != null) TaskStore.state.value.tasks.find { it.id == initialTask }?.let {
                if(confirmComplete && it.completedAt == null) { menuId = it.id; confirmation = "complete" } else edit(it)
            }
            else if (draft == null && quickAdd) edit(TaskItem(listId = TaskStore.state.value.preferences.selectedList, title = "", day = quickDay, starred = initialStarred))
        } catch (e: Exception) { error = com.jeerovan.comfer.localizedModuleMessage(context.resources,e.localizedMessage) }
    }
    LaunchedEffect(error) { error?.let { snackbar.showSnackbar(resources.getString(R.string.tasks_error, it)); error = null } }
    fun closeSearch() {
        keyboard?.hide()
        (context.getSystemService(Context.INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager)
            ?.hideSoftInputFromWindow(inputView.windowToken,0)
        focusManager.clearFocus(force=true)
        searching=false;query=""
    }
    fun back() {
        route = when (route) {
            "browse" -> { if(searching) closeSearch() else onFinish(); "browse" }
            "edit" -> { confirmation = "discard"; "edit" }
            "listActions" -> "browse"
            "reorderLists" -> "manage"
            "listName" -> if(listId.isEmpty()) { sheet = "choose"; "browse" } else "listActions"
            "destination" -> if(deletingList) "deleteList" else "listActions"
            "deleteList" -> "listActions"
            "reviewMove" -> "destination"
            "manage" -> { sheet = "choose"; "browse" }
            else -> "browse"
        }
    }
    BackHandler { if(sheet != null) { sheet = null; moveTaskId = null } else if (confirmation != null) confirmation = null else if (menuId != null && route != "reparent") menuId = null else back() }
    val today = Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay()
    val visible = state.tasks.filter { item ->
        (if(searching) (includeCompleted || item.completedAt == null || item.id in completingIds) else when (view) {
            "starred" -> item.starred
            "today" -> (item.day == today || item.isOverdue(now)) && (item.completedAt == null || item.id in completingIds)
            "upcoming" -> item.day != null && item.day > today && (item.completedAt == null || item.id in completingIds)
            "overdue" -> item.isOverdue(now) || item.id in completingIds
            "completed" -> item.completedAt != null
            "all" -> true
            else -> item.listId == selected.id
        }) && (!searching || query.isBlank() || item.title.contains(query, true) || item.notes.contains(query, true))
    }.let { rows -> when (state.preferences.sort) {
        "date" -> rows.sortedWith(compareBy<TaskItem> { it.completedAt != null }.thenBy { it.day ?: Long.MAX_VALUE }.thenBy { it.minute ?: 0 })
        "starred" -> rows.sortedWith(compareBy<TaskItem> { !it.starred }.thenBy { it.position })
        "alphabetical" -> rows.sortedWith(compareBy<TaskItem> { it.completedAt != null }.thenBy { it.title.lowercase() })
        else -> rows.sortedWith(compareBy<TaskItem> { it.completedAt != null }.thenBy { it.position }.thenBy { it.createdAt })
    } }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface.copy(alpha = .8f), contentColor = MaterialTheme.colorScheme.onSurface, tonalElevation = 0.dp) {
        Column(Modifier.safeDrawingPadding().imePadding().fillMaxSize()) {
            if(route != "browse") Text(stringResource(when(route) {
                "edit" -> R.string.tasks_details; "manage", "listActions" -> R.string.tasks_manage
                "listName" -> if(listId.isEmpty()) R.string.tasks_new_list else R.string.tasks_rename
                "destination" -> R.string.tasks_move_to; "reviewMove" -> R.string.tasks_move; "deleteList" -> R.string.tasks_delete_list
                "reorderLists" -> R.string.tasks_manual; else -> R.string.tasks_title
            }), Modifier.padding(16.dp), style = MaterialTheme.typography.titleLarge)
            if (!ready) { Text(stringResource(R.string.tasks_loading), Modifier.padding(24.dp)); return@Column }
            if (route == "edit" && draft != null) {
                key(editingId) {
                    TaskEditor(taskJson.decodeFromString(draft!!), state, busy, onCancel = { confirmation = "discard" }, onClose = { draft = null; route = "browse" }, onDraft = { draft = taskJson.encodeToString(it) }, onSave = { item, repeat, future -> mutate("browse", success = { draft = null }) { it.saveTask(item, repeat, future) } })
                }
            } else if (route == "browse") {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp).heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) {
                    Box(Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp), contentAlignment = Alignment.CenterStart) {
                    Text(if(searching) stringResource(R.string.tasks_search) else when(view) {
                        "starred" -> stringResource(R.string.tasks_starred); "all" -> stringResource(R.string.tasks_all)
                        "today" -> stringResource(R.string.tasks_today); "upcoming" -> stringResource(R.string.tasks_upcoming)
                        "overdue" -> stringResource(R.string.tasks_overdue); else -> selected.name
                    }, Modifier.widthIn(min = 48.dp).testTag("tasks-heading").then(if(!searching && view == "selected") Modifier.clickable {
                        listId = selected.id; listName = selected.name; sheet = "listActions"
                        if(!state.preferences.listGuideShown) mutate { it.copy(preferences = it.preferences.copy(listGuideShown = true)) }
                    } else Modifier), style = MaterialTheme.typography.headlineSmall)
                    if(!searching && view == "selected" && sheet == null && !state.preferences.listGuideShown) {
                        TaskGestureGuide(TaskGuide.LIST, 0f, Modifier.align(Alignment.Center)) {
                            scope.launch { runCatching { TaskStore.change(context) { it.copy(preferences = it.preferences.copy(listGuideShown = true)) } }.onFailure { snackbar.showSnackbar(com.jeerovan.comfer.localizedModuleMessage(context.resources,it.localizedMessage)) } }
                        }
                    }
                    }
                    }
                    IconButton(onClick = { sheet = "sort" }) { Icon(Icons.Outlined.MoreVert, stringResource(R.string.tasks_options)) }
                }
                BoxWithConstraints(Modifier.weight(1f)) {
                    val cardMaxHeight = maxHeight
                    val landscape = LocalConfiguration.current.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
                    val bottom = with(LocalDensity.current) { (controlsHeight + if(undo != null) undoHeight else 0).toDp().value }
                    val top = if (landscape) 0.dp else (maxHeight.value + bottom - reachableHeightDp(maxHeight.value + bottom)).coerceAtLeast(0f).dp
                    savedViews.SaveableStateProvider("browse:$view:${selected.id}") {
                        val scroll = rememberLazyListState()
                        val reach = rememberThumbReach(top)
                        val reachTop = with(LocalDensity.current) { reach.offset.toDp() }
                        LazyColumn(Modifier.fillMaxSize().nestedScroll(reach).testTag("tasks-list"), state = scroll, contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = reachTop, bottom = 12.dp)) {
                            if (visible.isEmpty()) item { Text(stringResource(if(query.isNotBlank()) R.string.tasks_no_match else if(view == "starred") R.string.tasks_empty_starred else R.string.tasks_empty), Modifier.padding(vertical = 32.dp)) }
                            val roots = visible.filter { it.parentId == null || visible.none { parent -> parent.id == it.parentId } }
                            val orderedRows = roots.flatMap { root -> listOf(root) + if(root.id in expanded || query.isNotBlank()) visible.filter { it.parentId == root.id } else emptyList() }
                            val displayed = orderedRows.filter { it.completedAt == null || it.id in completingIds }
                            if(displayed.isNotEmpty()) item {
                                Card(Modifier.fillMaxWidth().testTag("tasks-incomplete-card"), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.5f), contentColor = MaterialTheme.colorScheme.onSurface)) {
                                    val cardScroll = rememberLazyListState()
                                    var dragging by remember { mutableStateOf(false) }
                                    var cardVisible by remember { mutableStateOf(false) }
                                    Box(Modifier.onGloballyPositioned { cardVisible = it.boundsInWindow().height > 0f }) {
                                    val density = LocalDensity.current
                                    TasksReorderList(displayed, state.preferences.sort == "manual", cardScroll,
                                        Modifier.fillMaxWidth().heightIn(max = cardMaxHeight).testTag("tasks-incomplete-list"),
                                        "tasks-drop-target", onDrop = { id, target -> mutate { reorderTaskTo(it, id, target) } }, onDragging = { dragging = it }) { item ->
                                        if(state.preferences.sort == "date" && item.day == null && displayed.firstOrNull { it.day == null }?.id == item.id) Text(stringResource(R.string.tasks_no_date), Modifier.padding(vertical = 8.dp), style = MaterialTheme.typography.titleSmall)
                                        TaskRow(item, state, modifier = Modifier, rowShape = androidx.compose.foundation.shape.RoundedCornerShape(0.dp), completing = item.id in completingIds, onCompletedAnimation = { completingIds = completingIds - item.id }, reveal = if(revealedId == item.id) revealDirection else 0, onReveal = { revealedId = item.id; revealDirection = it }, expanded = item.id in expanded, onExpand = { expanded = if(item.id in expanded) expanded - item.id else expanded + item.id }, onOpen = { edit(item) }, onComplete = { revealedId = null; complete(item) }, onStar = { mutate { s -> s.copy(tasks = s.tasks.map { if (it.id == item.id) it.copy(starred = !it.starred, version = it.version + 1) else it }) } }, onMenu = { menuId = item.id }, onMove = { moveTaskId = item.id; sheet = "move" }, onDelete = { delete(item) }, restoreFocus = pendingFocusId == item.id, onFocusRestored = { pendingFocusId = null })
                                    }
                                    val guide = if(cardVisible && state.preferences.listGuideShown && sheet == null && !searching && view == "selected" && !dragging) nextTaskGuide(displayed, state.preferences) else null
                                    val rows = cardScroll.layoutInfo.visibleItemsInfo.filter { info -> displayed.any { it.id == info.key && it.completedAt == null } && info.offset >= 0 && info.offset + info.size <= cardScroll.layoutInfo.viewportEndOffset }
                                    val source = rows.firstOrNull()
                                    val sourceTask = displayed.find { it.id == source?.key }
                                    val target = rows.drop(1).firstOrNull { info -> displayed.any { it.id == info.key && it.listId == sourceTask?.listId && it.parentId == sourceTask?.parentId } }
                                    if(guide != null && source != null && (guide == TaskGuide.SWIPE || target != null)) {
                                        key(guide, source.key) {
                                            TaskGestureGuide(guide, target?.let { it.offset + it.size / 2f - (source.offset + source.size / 2f) } ?: 0f,
                                                Modifier.align(Alignment.TopCenter).offset { androidx.compose.ui.unit.IntOffset(0, source.offset + source.size / 2 - with(density) { 24.dp.roundToPx() }) }) {
                                                scope.launch {
                                                    runCatching { TaskStore.change(context) { snapshot -> snapshot.copy(preferences = if(guide == TaskGuide.SWIPE) snapshot.preferences.copy(swipeGuideShown = true) else snapshot.preferences.copy(reorderGuideShown = true)) } }
                                                        .onFailure { snackbar.showSnackbar(com.jeerovan.comfer.localizedModuleMessage(context.resources,it.localizedMessage)) }
                                                }
                                            }
                                        }
                                    }
                                    }
                                }
                            }
                            if(visible.any { it.completedAt != null }) item { Card(Modifier.fillMaxWidth().padding(top = 12.dp).testTag("tasks-completed-card"), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.5f), contentColor = MaterialTheme.colorScheme.onSurface)) {
                                TextButton(onClick = { completedExpanded = !completedExpanded }, modifier = Modifier.fillMaxWidth().testTag("tasks-completed-toggle")) {
                                    Text(stringResource(R.string.tasks_completed) + " · " + visible.count { it.completedAt != null }, Modifier.weight(1f))
                                    Icon(if(completedExpanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null)
                                }
                                if(completedExpanded) {
                                    val done = orderedRows.filter { it.completedAt != null && it.id !in completingIds }
                                    val cardScroll = rememberLazyListState()
                                    TasksReorderList(done, state.preferences.sort == "manual", cardScroll,
                                        Modifier.fillMaxWidth().heightIn(max = 480.dp).testTag("tasks-completed-list"),
                                        "tasks-completed-drop-target", onDrop = { id, target -> mutate { reorderTaskTo(it, id, target) } },
                                        footer = { if(done.isNotEmpty()) TextButton(onClick = { confirmation = "clear" }) { Text(stringResource(R.string.tasks_clear_completed)) } }) { item ->
                                        TaskRow(item, state, Modifier, 0, {}, item.id in expanded, { expanded = if(item.id in expanded) expanded - item.id else expanded + item.id }, { edit(item) }, { complete(item) }, {}, { menuId = item.id }, onMove = { moveTaskId = item.id; sheet = "move" }, onDelete = { delete(item) }, rowShape = androidx.compose.foundation.shape.RoundedCornerShape(0.dp))
                                    }
                                }
                            } }


                        }
                    }
                }
                if (undo != null) Row(Modifier.fillMaxWidth().onSizeChanged { undoHeight = it.height }.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.tasks_deleted), Modifier.weight(1f))
                    TextButton(enabled = !busy, onClick = {
                        val token = undo ?: return@TextButton
                        busy = true
                        scope.launch { try { TaskStore.undo(context, token); pendingFocusId = token.before.tasks.firstOrNull { old -> token.after.tasks.none { it == old } }?.id; undo = null } catch(e: Exception) { error = com.jeerovan.comfer.localizedModuleMessage(context.resources,e.localizedMessage) } finally { busy = false } }
                    }) { Text(stringResource(R.string.tasks_undo)) }
                }
                AnimatedVisibility(
                    visible=searching,
                    enter=expandVertically(tween(220))+fadeIn(tween(160)),
                    exit=shrinkVertically(tween(220))+fadeOut(tween(120)),
                ) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        val includeLabel = stringResource(R.string.tasks_include_completed)
                        Text(includeLabel, Modifier.weight(1f))
                        Box(Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp).toggleable(value = includeCompleted, role = androidx.compose.ui.semantics.Role.Switch, onValueChange = { includeCompleted = it }).semantics { contentDescription = includeLabel }, contentAlignment = Alignment.Center) {
                            Switch(includeCompleted, null, Modifier.graphicsLayer { scaleX = 0.7f; scaleY = 0.7f })
                        }
                    }
                }
                AnimatedContent(
                    targetState=searching,
                    modifier=Modifier.fillMaxWidth(),
                    transitionSpec={
                        (fadeIn(tween(180))+expandHorizontally(tween(220),expandFrom=Alignment.Start)) togetherWith
                            (fadeOut(tween(120))+shrinkHorizontally(tween(220),shrinkTowards=Alignment.Start))
                    },
                    label="tasks-search-mode",
                ) { searchMode ->
                    Row(Modifier.fillMaxWidth().testTag("tasks-bottom-actions").onSizeChanged { controlsHeight = it.height }.padding(if(searchMode) PaddingValues(start=16.dp,end=16.dp,bottom=8.dp) else PaddingValues(8.dp)), horizontalArrangement = if(searchMode) Arrangement.spacedBy(8.dp) else Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                        if(searchMode) {
                            val searchFocus=remember{FocusRequester()}
                            LaunchedEffect(searching){if(searching)searchFocus.requestFocus()}
                            TaskInputField(
                                value=query,onValueChange={query=it},singleLine=true,enabled=searching,
                                placeholder=stringResource(R.string.tasks_search),
                                modifier=Modifier.weight(1f).testTag("tasks-search-input").focusRequester(searchFocus),
                            )
                            IconButton(onClick=::closeSearch,enabled=searching){
                                Box(Modifier.size(40.dp),contentAlignment=Alignment.Center){
                                    Icon(Icons.Outlined.Close,stringResource(R.string.tasks_close_search))
                                }
                            }
                        } else {
                            IconButton(onClick = { searching = true }, enabled = !searching) { Icon(Icons.Outlined.Search, stringResource(R.string.tasks_search)) }
                            IconToggleButton(enabled = !searching, checked = view == "starred", onCheckedChange = { view = if(it) "starred" else "selected"; searching = false }) { Icon(if(view == "starred") Icons.Filled.Star else Icons.Outlined.StarBorder, stringResource(R.string.tasks_star)) }
                            IconButton(onClick = { add() }, enabled = !busy && !searching) { Icon(Icons.Outlined.Add, stringResource(R.string.tasks_add)) }
                            IconButton(onClick = { sheet = "choose" }, enabled = !searching, modifier = Modifier.testTag("tasks-list-picker")) { Icon(Icons.Outlined.Menu, stringResource(R.string.tasks_choose_list)) }
                        }
                    }
                }
            } else {
                Box(Modifier.weight(1f)) {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    when (route) {
                        "choose" -> {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { state.lists.sortedBy { it.position }.forEach { list -> FilterChip(selected = list.id == selected.id, onClick = { view = "selected"; mutate("browse") { it.copy(preferences = it.preferences.copy(selectedList = list.id)) } }, leadingIcon = if(list.id == selected.id) {{ Icon(Icons.Outlined.Check, null) }} else null, label = { Text(list.name) }) } }
                            Action(R.string.tasks_manage) { route = "manage" }
                        }
                        "manage" -> {
                            Action(R.string.tasks_new_list) { listId = ""; listName = ""; route = "listName" }
                            state.lists.sortedBy { it.position }.forEach { list -> TextButton(onClick = { listId = list.id; listName = list.name; route = "listActions" }) { Text(list.name) } }
                            Action(R.string.tasks_manual) { listOrder = state.lists.sortedBy { it.position }.map { it.id }; route = "reorderLists" }
                        }
                        "listName" -> {
                            OutlinedTextField(listName, { listName = it }, label = { Text(stringResource(R.string.tasks_name)) }, modifier = Modifier.fillMaxWidth())
                            Button(enabled = !busy && listName.isNotBlank(), onClick = {
                                val created = if(listId.isEmpty()) TaskList(name = listName.trim(), position = state.lists.size) else null
                                mutate(if(created != null) "choose" else "browse") { s -> require(created != null || s.lists.any { it.id == listId }) { "List no longer exists" }; s.copy(lists = if(created != null) s.lists + created else s.lists.map { if (it.id == listId) it.copy(name = listName.trim()) else it }, preferences = if(created != null) s.preferences.copy(selectedList = created.id) else s.preferences) }
                            }) { Text(stringResource(R.string.tasks_save)) }
                        }
                        "listActions" -> {
                            Text(listName, style = MaterialTheme.typography.headlineSmall)
                            Action(R.string.tasks_rename) { route = "listName" }
                            Action(R.string.tasks_delete_list) { route = "deleteList" }
                        }
                        "deleteList" -> if (state.lists.size == 1) Text(stringResource(R.string.tasks_keep_list)) else {
                            Action(R.string.tasks_move_delete) { deletingList = true; route = "destination" }
                            Action(R.string.tasks_delete_content) { confirmation = "deleteList" }
                        }
                        "destination" -> {
                            if(state.lists.none { it.id != listId }) Text(stringResource(R.string.tasks_need_destination))
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { state.lists.filter { it.id != listId }.forEach { list -> AssistChip(onClick = { destination = list.id; route = "reviewMove" }, label = { Text(list.name) }) } }
                        }
                        "reviewMove" -> {
                            Text(stringResource(R.string.tasks_review_move, state.lists.find { it.id == destination }?.name.orEmpty()))
                            Text(stringResource(R.string.tasks_move_count, listName, state.tasks.count { it.listId == listId }))
                            Button(enabled = !busy, onClick = { mutate("browse") { it.moveList(listId, destination, deletingList) } }) { Text(stringResource(R.string.tasks_move)) }
                        }
                        "reorderLists" -> {
                          listOrder.mapNotNull { id -> state.lists.find { it.id == id } }.forEachIndexed { index, list ->
                            fun move(delta: Int) { val next = listOrder.toMutableList(); val target = (index + delta).coerceIn(0, next.lastIndex); next.removeAt(index); next.add(target, list.id); listOrder = next }
                            var drag by remember(list.id) { mutableFloatStateOf(0f) }
                            Row(Modifier.graphicsLayer { translationY = drag }, verticalAlignment = Alignment.CenterVertically) {
                                Text(list.name, Modifier.weight(1f))
                                IconButton(enabled = index > 0 && !busy, onClick = { move(-1) }) { Icon(Icons.Outlined.KeyboardArrowUp, stringResource(R.string.tasks_up)) }
                                IconButton(enabled = index < listOrder.lastIndex && !busy, onClick = { move(1) }) { Icon(Icons.Outlined.KeyboardArrowDown, stringResource(R.string.tasks_down)) }
                                val density = LocalDensity.current
                                Icon(Icons.Outlined.DragHandle, stringResource(R.string.tasks_reorder), Modifier.size(48.dp).pointerInput(listOrder) { detectDragGesturesAfterLongPress(onDragEnd = { move(with(density) { (drag / 48.dp.toPx()).toInt() }); drag = 0f }, onDragCancel = { drag = 0f }) { change, amount -> change.consume(); drag += amount.y } })
                            }
                          }
                          Button(enabled = !busy, onClick = { mutate("browse", success = { sheet = "sort" }) { s -> require(s.lists.map { it.id }.toSet() == listOrder.toSet()) { "Lists changed; reopen ordering" }; s.copy(lists = listOrder.mapIndexed { i, id -> s.lists.first { it.id == id }.copy(position = i) }) } }) { Text(stringResource(R.string.tasks_save)) }
                        }
                        "sort" -> listOf("manual" to R.string.tasks_manual, "date" to R.string.tasks_date, "alphabetical" to R.string.tasks_alphabetical).forEach { (sort, label) -> Action(label) { mutate("browse") { it.copy(preferences = it.preferences.copy(sort = sort)) } } }

                    }
                }
            }
            }
            SnackbarHost(snackbar)
        }
    }
    if(sheet != null) ModalBottomSheet(containerColor=MaterialTheme.colorScheme.surfaceContainer.copy(alpha=.9f),contentColor=MaterialTheme.colorScheme.onSurface,tonalElevation=0.dp,onDismissRequest = { sheet = null; moveTaskId = null }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = sheet == "sort")) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(if(sheet == "listActions") (if(listId.isEmpty()) R.string.tasks_add_list else R.string.tasks_rename) else if(sheet == "sort") R.string.tasks_sort else if(sheet == "move") R.string.tasks_move_to_list else R.string.tasks_choose_list), style = MaterialTheme.typography.titleLarge)
            if(sheet == "listActions") {
                OutlinedTextField(listName, { listName = it }, label = { Text(stringResource(R.string.tasks_name)) }, modifier = Modifier.fillMaxWidth().testTag("tasks-list-name"))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    if(listId.isNotEmpty()) IconButton(enabled = !busy && state.lists.size > 1, onClick = {
                        if(state.tasks.any { it.listId == listId }) confirmation = "deleteList"
                        else mutate("browse", success = { sheet = null }, deletion = true) { it.moveList(listId, null, true) }
                    }) { Icon(Icons.Outlined.Delete, stringResource(R.string.tasks_delete_list)) }
                    else Spacer(Modifier.weight(1f))
                    IconButton(enabled = !busy && listName.isNotBlank(), onClick = {
                        mutate("browse", success = { sheet = null; if(listId.isEmpty()) { view = "selected"; searching = false } }) { snapshot ->
                            if(listId.isEmpty()) {
                                val created = TaskList(name = listName.trim(), position = snapshot.lists.size)
                                snapshot.copy(lists = snapshot.lists + created, preferences = snapshot.preferences.copy(selectedList = created.id))
                            } else {
                                require(snapshot.lists.any { it.id == listId }) { "List no longer exists" }
                                snapshot.copy(lists = snapshot.lists.map { if(it.id == listId) it.copy(name = listName.trim()) else it })
                            }
                        }
                    }) { Icon(Icons.Outlined.Check, stringResource(R.string.tasks_save)) }
                }
                if(listId.isNotEmpty() && state.lists.size == 1) Text(stringResource(R.string.tasks_keep_list))
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            } else if(sheet == "sort") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf("manual" to R.string.tasks_my_order, "date" to R.string.tasks_due_date, "starred" to R.string.tasks_starred, "alphabetical" to R.string.tasks_task_title).forEach { (value, label) ->
                        FilterChip(selected = state.preferences.sort == value, onClick = { mutate { it.copy(preferences = it.preferences.copy(sort = value)) }; sheet = null }, label = { Text(stringResource(label)) })
                    }
                }
                HorizontalDivider()
                Text(stringResource(R.string.tasks_preferences), Modifier.testTag("tasks-settings-heading"), style = MaterialTheme.typography.titleMedium)
                ReminderPermissions()
                PreferenceToggle(R.string.tasks_date_reminders, state.preferences.dateOnlyReminders, compact = true) { value -> mutate { it.copy(preferences = it.preferences.copy(dateOnlyReminders = value)) } }
                TaskSettingAction(R.string.tasks_default_time, trailing = { Text(timeLabel(state.preferences.defaultMinute)) }) {
                    pickTime(context, state.preferences.defaultMinute) { minute -> mutate { it.copy(preferences = it.preferences.copy(defaultMinute = minute)) } }
                }
                PreferenceToggle(R.string.tasks_private, state.preferences.privateNotifications, compact = true) { value -> mutate { it.copy(preferences = it.preferences.copy(privateNotifications = value)) } }
            } else {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { state.lists.sortedBy { it.position }.forEach { list ->
                    FilterChip(selected = list.id == selected.id, onClick = {
                        if(sheet == "move") {
                            val moving = state.tasks.find { it.id == moveTaskId }
                            if(moving != null) mutate("browse", success = { sheet = null; moveTaskId = null; scope.launch { snackbar.showSnackbar(resources.getString(R.string.tasks_moved, list.name)) } }) { snapshot -> snapshot.saveTask(moving.copy(listId = list.id, parentId = null)) }
                        } else { view = "selected"; searching = false; mutate("browse") { it.copy(preferences = it.preferences.copy(selectedList = list.id)) } }
                        if(sheet != "move") { sheet = null; moveTaskId = null }
                    }, enabled = !busy, leadingIcon = if(list.id == selected.id) {{ Icon(Icons.Outlined.Check, null) }} else null, label = { Text(list.name) })
                }
                    if(sheet == "choose") AssistChip(onClick = { listId = ""; listName = ""; sheet = "listActions" }, leadingIcon = { Icon(Icons.Outlined.Add, null) }, label = { Text(stringResource(R.string.tasks_add_list)) })
                }
                if(sheet == "choose") {
                    Text(stringResource(R.string.tasks_views), style = MaterialTheme.typography.titleMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) { listOf("all" to R.string.tasks_all, "today" to R.string.tasks_today, "upcoming" to R.string.tasks_upcoming, "overdue" to R.string.tasks_overdue).forEach { (value, label) -> FilterChip(selected = view == value, onClick = { view = value; searching = false; sheet = null }, label = { Text(stringResource(label)) }) } }
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }
    val menu = state.tasks.find { it.id == menuId }
    if (menu != null && confirmation == null && route != "reparent") AlertDialog(onDismissRequest = { menuId = null }, title = { Text(menu.title) }, text = {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            Action(R.string.tasks_details) { edit(menu); menuId = null }
            if(menu.parentId == null) Action(R.string.tasks_move_to_list) { moveTaskId = menu.id; menuId = null; sheet = "move" }
            Action(if (menu.completedAt == null) R.string.tasks_complete else R.string.tasks_reopen) { complete(menu) }
            Action(if (menu.starred) R.string.tasks_unstar else R.string.tasks_star) { mutate { s -> s.copy(tasks = s.tasks.map { if (it.id == menu.id) it.copy(starred = !it.starred, version = it.version + 1) else it }) } }
            if(state.preferences.sort == "manual") {
                Action(R.string.tasks_up) { mutate { reorderTask(it, menu.id, -1) } }
                Action(R.string.tasks_down) { mutate { reorderTask(it, menu.id, 1) } }
            } else Action(R.string.tasks_switch_manual) { mutate { it.copy(preferences = it.preferences.copy(sort = "manual")) } }
            if(menu.completedAt == null && menu.day != null) listOf(10, 30, 60, 1440).forEach { minutes ->
                TextButton(onClick = { mutate { applyReminderAction(it, menu.id, menu.version, "SNOOZE", minutes, System.currentTimeMillis()) } }) { Text(stringResource(R.string.tasks_snooze) + " · " + stringResource(R.string.tasks_snooze_minutes, minutes)) }
            }
            if(menu.completedAt == null) Action(R.string.tasks_snooze_custom) {
                pickDate(context, menu.day) { day -> pickTime(context, menu.minute) { minute ->
                    val at = menu.copy(day = day, minute = minute).dueInstant(state.preferences)!!
                    mutate { s -> require(at > System.currentTimeMillis()) { "Choose a future snooze time" }; s.copy(tasks = s.tasks.map { if(it.id == menu.id) it.copy(snoozedUntil = at, reminder = true, notifiedAt = null, version = it.version + 1) else it }) }
                } }
            }
            Action(R.string.tasks_delete) { confirmation = if (menu.seriesId != null) "deleteScope" else "delete" }
        }
    }, confirmButton = { TextButton(onClick = { menuId = null }) { Text(stringResource(R.string.tasks_cancel)) } })
    confirmation?.let { kind ->
        AlertDialog(onDismissRequest = { confirmation = null }, title = { Text(stringResource(when(kind) {
            "discard" -> R.string.tasks_discard; "complete" -> R.string.tasks_complete_children; "clear" -> R.string.tasks_clear_review; "deleteScope" -> R.string.tasks_scope; else -> R.string.tasks_delete
        })) }, text = { if (kind == "delete" || kind == "deleteList") Column {
            Text(stringResource(R.string.tasks_review_delete, if(kind == "deleteList") listName else menu?.title.orEmpty()))
            if(kind == "deleteList") Text(stringResource(R.string.tasks_move_count, listName, state.tasks.count { it.listId == listId }))
        } }, confirmButton = {
            TextButton(enabled = !busy, onClick = { when(kind) {
                "discard" -> { draft = null; route = "browse"; confirmation = null }
                "complete" -> menu?.let { mutate("browse") { s -> s.completeTask(it.id, true, true) } }
                "delete", "deleteScope" -> menu?.let { mutate(deletion = true) { s -> s.deleteTask(it.id) } }
                "deleteList" -> mutate("browse", success = { sheet = null }, deletion = true) { it.moveList(listId, null, true) }
                "clear" -> mutate(deletion = true) { s -> val ids = visible.filter { it.completedAt != null && s.tasks.none { child -> child.parentId == it.id && child.completedAt == null } }.map { it.id }.toSet(); s.copy(tasks = s.tasks.filterNot { it.id in ids || it.parentId in ids }) }
            } }) { Text(stringResource(when(kind) { "deleteScope" -> R.string.tasks_this; "discard" -> R.string.tasks_discard_action; "complete" -> R.string.tasks_complete; "delete", "deleteList", "clear" -> R.string.tasks_delete; else -> R.string.tasks_save })) }
        }, dismissButton = {
            Row { if (kind == "deleteScope") TextButton(onClick = { menu?.let { mutate(deletion = true) { s -> s.deleteTask(it.id, true) } } }) { Text(stringResource(R.string.tasks_future)) }
                TextButton(onClick = { confirmation = null }) { Text(stringResource(R.string.tasks_cancel)) } }
        })
    }
}

private fun reorderTaskTo(state: TaskSnapshot, id: String, target: String): TaskSnapshot {
    val item = state.tasks.find { it.id == id } ?: return state
    val siblings = state.tasks.filter { it.listId == item.listId && it.parentId == item.parentId && (it.completedAt == null) == (item.completedAt == null) }.sortedBy { it.position }
    val from = siblings.indexOfFirst { it.id == id }
    val to = siblings.indexOfFirst { it.id == target }
    return if(to < 0) state else reorderTask(state, id, to - from)
}

internal fun reorderTask(state: TaskSnapshot, id: String, delta: Int): TaskSnapshot {
    val item = state.tasks.find { it.id == id } ?: return state
    val ordered = state.tasks.filter { it.listId == item.listId && it.parentId == item.parentId && (it.completedAt == null) == (item.completedAt == null) }.sortedBy { it.position }.toMutableList()
    val index = ordered.indexOfFirst { it.id == id }; val target = (index + delta).coerceIn(0, ordered.lastIndex)
    if(index == target) return state
    ordered.removeAt(index); ordered.add(target, item)
    val positions = ordered.mapIndexed { i, task -> task.id to i }.toMap()
    return state.copy(tasks = state.tasks.map { if (it.id in positions) it.copy(position = positions.getValue(it.id), version = it.version + 1) else it })
}

@Composable private fun Action(label: Int, onClick: () -> Unit) { TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(stringResource(label)) } }
@Composable private fun TaskSettingAction(label: Int, trailing: @Composable () -> Unit, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp)
            .clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(label), Modifier.weight(1f))
        trailing()
    }
}
@Composable private fun PreferenceToggle(label: Int, checked: Boolean, compact: Boolean = false, change: (Boolean) -> Unit) {
    val title = stringResource(label)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f))
        Box(Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp).toggleable(value = checked, role = androidx.compose.ui.semantics.Role.Switch, onValueChange = change).semantics { contentDescription = title }, contentAlignment = Alignment.Center) {
            Switch(checked, null, Modifier.graphicsLayer { scaleX = if(compact) 0.7f else 1f; scaleY = scaleX })
        }
    }
}

@Composable internal fun TaskRow(item: TaskItem, state: TaskSnapshot, modifier: Modifier, reveal: Int, onReveal: (Int) -> Unit, expanded: Boolean, onExpand: () -> Unit, onOpen: () -> Unit, onComplete: () -> Unit, onStar: () -> Unit, onMenu: () -> Unit, restoreFocus: Boolean = false, onFocusRestored: () -> Unit = {}, onMove: () -> Unit = onMenu, onDelete: () -> Unit = onMenu, completing: Boolean = false, onCompletedAnimation: () -> Unit = {}, rowShape: androidx.compose.ui.graphics.Shape = MaterialTheme.shapes.medium) {
    val squeeze = remember { androidx.compose.animation.core.Animatable(1f) }
    val finishAnimation by rememberUpdatedState(onCompletedAnimation)
    LaunchedEffect(completing) {
        if(completing) { squeeze.animateTo(0f, androidx.compose.animation.core.tween(220)); finishAnimation() }
        else squeeze.animateTo(1f)
    }
    val threshold = with(LocalDensity.current) { 72.dp.toPx() }
    val completeLabel = stringResource(if(item.completedAt == null) R.string.tasks_complete else R.string.tasks_reopen) + ": " + item.title
    val latestDelete by rememberUpdatedState(onDelete)
    val direction = if(LocalLayoutDirection.current == LayoutDirection.Rtl) -1 else 1
    var drag by remember { mutableFloatStateOf(0f) }
    val openTask by rememberUpdatedState(onOpen)
    val openLabel = stringResource(R.string.tasks_details)
    val focus = remember { FocusRequester() }
    LaunchedEffect(restoreFocus) { if(restoreFocus) { withFrameNanos { }; focus.requestFocus(); onFocusRestored() } }
    Column(modifier) {
    Surface(Modifier.fillMaxWidth().padding(start = if(item.parentId != null) 20.dp else 0.dp, top = 0.dp)
        .focusRequester(focus)
        .graphicsLayer { translationX = drag; scaleX = squeeze.value; scaleY = squeeze.value; alpha = squeeze.value }
        .pointerInput(item.id, threshold, direction) { detectHorizontalDragGestures(onDragStart = { drag = 0f }, onDragCancel = { drag = 0f }, onDragEnd = { val distance = drag * direction; if(kotlin.math.abs(distance) > threshold * 2) latestDelete(); drag = 0f }) { change, amount -> change.consume(); drag += amount } }
        .semantics(mergeDescendants = true) {
            onClick(openLabel) { openTask(); true }
        }
        .pointerInput(item.id) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val up = waitForUpOrCancellation()
                if(up != null && up.uptimeMillis - down.uptimeMillis < viewConfiguration.longPressTimeoutMillis) {
                    up.consume()
                    openTask()
                }
            }
        },
        shape = rowShape, color = androidx.compose.ui.graphics.Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface) {
        Row(Modifier.padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onComplete) { Icon(if(item.completedAt != null) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked, completeLabel) }
            Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                Text(item.title, style = MaterialTheme.typography.bodyLarge)
                if(item.completedAt == null && item.notes.isNotBlank()) Text(item.notes, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                if(item.completedAt == null) item.day?.let { Text(dateLabel(it) + (item.minute?.let { time -> " · " + timeLabel(time) } ?: ""), style = MaterialTheme.typography.bodySmall) }
                if(item.completedAt == null && item.seriesId != null) Icon(Icons.Outlined.Repeat, stringResource(R.string.tasks_repeat), Modifier.size(16.dp))
                if(item.isOverdue(System.currentTimeMillis())) Text(stringResource(R.string.tasks_overdue), style = MaterialTheme.typography.labelSmall)
                if(item.completedAt == null && item.reminder && item.day != null && (item.minute != null || state.preferences.dateOnlyReminders)) {
                    val context = LocalContext.current
                    Text(if(item.snoozedUntil != null) stringResource(R.string.tasks_snoozed_until, Instant.ofEpochMilli(item.snoozedUntil).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)))
                        else stringResource(if(!TaskReminders.notificationsAllowed(context)) R.string.tasks_blocked else if(item.notifiedAt != null) R.string.tasks_reminder_sent else if(!TaskReminders.exactAllowed(context)) R.string.tasks_approximate else R.string.tasks_scheduled), style = MaterialTheme.typography.labelSmall)
                }
                val children = state.tasks.filter { it.parentId == item.id }
                if (children.isNotEmpty()) Text(stringResource(R.string.tasks_children, children.count { it.completedAt != null }, children.size), style = MaterialTheme.typography.bodySmall)
                item.completedAt?.let { Text(stringResource(R.string.tasks_done_at, Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT))), style = MaterialTheme.typography.bodySmall) }
            }
            if(state.tasks.any { it.parentId == item.id }) IconButton(onClick = onExpand) { Icon(if(expanded) Icons.Outlined.KeyboardArrowUp else Icons.Outlined.KeyboardArrowDown, stringResource(R.string.tasks_subtasks)) }
            if(item.completedAt == null) IconButton(onClick = onStar) { Icon(if(item.starred) Icons.Outlined.Star else Icons.Outlined.StarBorder, stringResource(if(item.starred) R.string.tasks_unstar else R.string.tasks_star)) }

        }
    }

    }
}

private fun pickDate(context: Context, initial: Long?, onPick: (Long) -> Unit) {
    val date = initial?.let(LocalDate::ofEpochDay) ?: LocalDate.now()
    DatePickerDialog(context, { _, y, m, d -> onPick(LocalDate.of(y, m + 1, d).toEpochDay()) }, date.year, date.monthValue - 1, date.dayOfMonth).show()
}
private fun pickTime(context: Context, initial: Int?, onPick: (Int) -> Unit) {
    val time = initial ?: 540
    TimePickerDialog(context, { _, h, m -> onPick(h * 60 + m) }, time / 60, time % 60, android.text.format.DateFormat.is24HourFormat(context)).show()
}

@Composable
private fun TaskInputField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = false,
    enabled: Boolean = true,
) {
    BasicTextField(
        value=value,
        onValueChange=onValueChange,
        singleLine=singleLine,
        enabled=enabled,
        modifier=modifier.heightIn(min=40.dp)
            .semantics { contentDescription = placeholder }
            .background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha=.3f),RoundedCornerShape(20.dp))
            .padding(horizontal=16.dp,vertical=8.dp),
        textStyle=MaterialTheme.typography.bodyMedium.copy(color=MaterialTheme.colorScheme.onSurface),
        cursorBrush=SolidColor(MaterialTheme.colorScheme.primary),
        decorationBox={innerTextField->
            Box(contentAlignment=Alignment.CenterStart) {
                if(value.isEmpty())Text(placeholder,style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                innerTextField()
            }
        },
    )
}

@Composable
private fun ColumnScope.TaskEditor(initial: TaskItem, state: TaskSnapshot, busy: Boolean, onCancel: () -> Unit, onClose: () -> Unit, onDraft: (TaskItem) -> Unit, onSave: (TaskItem, TaskRepeat?, Boolean) -> Unit) {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val baseline by rememberSaveable { mutableStateOf(taskJson.encodeToString(initial)) }
    var encoded by rememberSaveable { mutableStateOf(taskJson.encodeToString(initial)) }
    val task = taskJson.decodeFromString<TaskItem>(encoded)
    fun update(next: TaskItem) { encoded = taskJson.encodeToString(next); onDraft(next) }
    var choosingList by rememberSaveable { mutableStateOf(false) }
    var page by rememberSaveable { mutableStateOf("capture") }
    val originalRule = remember(initial.id) { state.series.find { it.id == initial.seriesId }?.let { taskJson.decodeFromString<TaskRepeat>(it.rule) } }
    var repeatUnit by rememberSaveable { mutableStateOf(originalRule?.unit?.name.orEmpty()) }
    var interval by rememberSaveable { mutableStateOf((originalRule?.interval ?: 1).toString()) }
    var count by rememberSaveable { mutableStateOf(originalRule?.count?.let { (it - initial.occurrence).coerceAtLeast(1).toString() }.orEmpty()) }
    var end by rememberSaveable { mutableStateOf(originalRule?.endDay) }
    var weekdays by rememberSaveable { mutableStateOf(originalRule?.weekdays?.toList().orEmpty()) }
    val originalCount = originalRule?.count?.let { (it - initial.occurrence).coerceAtLeast(1).toString() }.orEmpty()
    val dirty = encoded != baseline || repeatUnit != originalRule?.unit?.name.orEmpty() || interval != (originalRule?.interval ?: 1).toString() || count != originalCount || end != originalRule?.endDay || weekdays.toSet() != originalRule?.weekdays.orEmpty()
    fun close() { if(dirty) onCancel() else onClose() }
    BackHandler { if(page != "capture") page = if(page == "repeat") "schedule" else "capture" else close() }
    var scopePrompt by rememberSaveable { mutableStateOf(false) }
    var validation by remember { mutableStateOf<String?>(null) }
    val titleFocus = remember { FocusRequester() }
    LaunchedEffect(initial.id, page) { if(initial.title.isEmpty() && page == "capture") titleFocus.requestFocus() }
    fun save(future: Boolean) {
        try {
            val repeat = if (repeatUnit.isEmpty() || task.seriesId != null && !future) null else TaskRepeat(RepeatUnit.valueOf(repeatUnit), interval.toInt(), weekdays.toSet(), end, count.takeIf { it.isNotBlank() }?.toInt())
            state.saveTask(task, repeat, future).validate()
            onSave(task, repeat, future)
            scopePrompt = false
        } catch(e: Exception) { validation = e.localizedMessage }
    }
    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.Bottom)) {
        if(page == "capture") {
        TaskInputField(task.title, { update(task.copy(title = it)) }, placeholder = stringResource(R.string.tasks_task_title), modifier = Modifier.fillMaxWidth().focusRequester(titleFocus).testTag("task-title"))
        TaskInputField(task.notes, { update(task.copy(notes = it)) }, placeholder = stringResource(R.string.tasks_notes), modifier = Modifier.fillMaxWidth().testTag("task-description"))
        android.util.Patterns.WEB_URL.matcher(task.notes).let { matcher ->
            val links = mutableListOf<String>(); while(matcher.find() && links.size < 5) links += matcher.group()
            links.distinct().forEach { link -> TextButton(onClick = {
                val uri = android.net.Uri.parse(if(link.startsWith("http://") || link.startsWith("https://")) link else "https://$link")
                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }.onFailure { validation = it.localizedMessage }
            }) { Text(link) } }
        }
        if(task.day != null) TextButton(onClick = { page = "schedule" }) { Text(dateLabel(task.day) + (task.minute?.let { " · " + timeLabel(it) } ?: "")) }
        if(repeatUnit.isNotEmpty()) TextButton(onClick = { page = "repeat" }) { Text(repeatSummary(repeatUnit, interval, weekdays)) }
        }
        if(page == "schedule") {
        Text(stringResource(R.string.tasks_due_date), style = MaterialTheme.typography.titleLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AssistChip(onClick = { update(task.copy(day = LocalDate.now().toEpochDay())) }, label = { Text(stringResource(R.string.tasks_today)) })
            AssistChip(onClick = { update(task.copy(day = LocalDate.now().plusDays(1).toEpochDay())) }, label = { Text(stringResource(R.string.tasks_tomorrow)) })
            AssistChip(onClick = { pickDate(context, task.day) { update(task.copy(day = it)) } }, label = { Text(task.day?.let(::dateLabel) ?: stringResource(R.string.tasks_date)) })
            AssistChip(onClick = { update(task.copy(day = null, minute = null)); repeatUnit = "" }, label = { Text(stringResource(R.string.tasks_no_date)) })
            run {
                AssistChip(onClick = { pickTime(context, task.minute) { update(task.copy(day = task.day ?: LocalDate.now().toEpochDay(), minute = it)) } }, label = { Text(task.minute?.let(::timeLabel) ?: stringResource(R.string.tasks_time)) })
                AssistChip(onClick = { update(task.copy(minute = null)) }, label = { Text(stringResource(R.string.tasks_no_time)) })
            }
        }
        PreferenceToggle(R.string.tasks_remind, task.reminder) { update(task.copy(reminder = it)) }
        if(task.reminder && task.day != null) ReminderPermissions(showNotificationSettings = false)
        Action(R.string.tasks_repeat) { page = "repeat" }
        }
        if(page == "repeat") {
        if(task.parentId == null && task.day != null && state.tasks.none { it.parentId == task.id }) {
            Text(stringResource(R.string.tasks_repeat))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("" to R.string.tasks_never, "DAILY" to R.string.tasks_daily, "WEEKLY" to R.string.tasks_weekly, "MONTHLY" to R.string.tasks_monthly, "YEARLY" to R.string.tasks_yearly).forEach { (unit, label) -> FilterChip(selected = repeatUnit == unit, onClick = { repeatUnit = unit }, label = { Text(stringResource(label)) }) }
            }
            if (repeatUnit.isNotEmpty()) {
                OutlinedTextField(interval, { interval = it }, label = { Text(stringResource(R.string.tasks_interval)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                if(repeatUnit == "WEEKLY") FlowRow { (1..7).forEach { day -> FilterChip(selected = day in weekdays, onClick = { weekdays = if(day in weekdays) weekdays - day else weekdays + day }, label = { Text(DayOfWeek.of(day).getDisplayName(java.time.format.TextStyle.SHORT, locale)) }) } }
                OutlinedTextField(count, { count = it }, label = { Text(stringResource(R.string.tasks_end_count)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                TextButton(onClick = { pickDate(context, end ?: task.day) { end = it } }) { Text(end?.let(::dateLabel) ?: stringResource(R.string.tasks_end_date)) }
                if(end != null) TextButton(onClick = { end = null }) { Text(stringResource(R.string.tasks_no_end)) }
                val next = runCatching { occurrenceDay(LocalDate.ofEpochDay(task.day!!), TaskRepeat(RepeatUnit.valueOf(repeatUnit), interval.toInt(), weekdays.toSet()), 1) }.getOrNull()
                next?.let { Text(stringResource(R.string.tasks_next, dateLabel(it.toEpochDay()))) }
            }
        }
        else Text(stringResource(R.string.tasks_repeat_restriction))
        }
        validation?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
    Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        if(page == "capture") {
            IconButton(onClick = { page = "schedule" }) { Icon(Icons.Outlined.Alarm, stringResource(R.string.tasks_schedule)) }
            IconToggleButton(checked = task.starred, onCheckedChange = { update(task.copy(starred = it)) }) { Icon(if(task.starred) Icons.Filled.Star else Icons.Outlined.StarBorder, stringResource(R.string.tasks_star)) }
            IconButton(onClick = { choosingList = true }, enabled = task.parentId == null) { Icon(Icons.Outlined.DriveFileMove, stringResource(R.string.tasks_move_to_list)) }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = ::close, enabled = !busy) { Icon(Icons.Outlined.Close, stringResource(R.string.tasks_cancel)) }
            IconButton(onClick = { if(task.seriesId != null) scopePrompt = true else save(false) }, enabled = !busy && task.title.isNotBlank(), modifier = Modifier.testTag("task-save")) { Icon(Icons.Outlined.Check, stringResource(R.string.tasks_save)) }
        } else {
            IconButton(onClick = { page = "capture" }) { Icon(Icons.Outlined.Check, stringResource(R.string.tasks_done)) }
        }
    }
    if(choosingList) ModalBottomSheet(containerColor=MaterialTheme.colorScheme.surfaceContainer.copy(alpha=.9f),contentColor=MaterialTheme.colorScheme.onSurface,tonalElevation=0.dp,onDismissRequest = { choosingList = false }) {
        FlowRow(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            state.lists.sortedBy { it.position }.forEach { list -> FilterChip(selected = task.listId == list.id, onClick = { update(task.copy(listId = list.id)); choosingList = false }, label = { Text(list.name) }) }
        }
    }
    if(scopePrompt) AlertDialog(onDismissRequest = { scopePrompt = false }, title = { Text(stringResource(R.string.tasks_scope)) }, confirmButton = { TextButton(onClick = { save(false) }) { Text(stringResource(R.string.tasks_this)) } }, dismissButton = { TextButton(onClick = { save(true) }) { Text(stringResource(R.string.tasks_future)) } })
}

@Composable private fun ReminderPermissions(showNotificationSettings: Boolean = true) {
    val context = LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycle) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event -> if(event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) refresh++ }
        lifecycle.lifecycle.addObserver(observer)
        onDispose { lifecycle.lifecycle.removeObserver(observer) }
    }
    val notifications = remember(refresh) { TaskReminders.notificationsAllowed(context) }
    val exact = remember(refresh) { TaskReminders.exactAllowed(context) }
    Text(stringResource(if(!notifications) R.string.tasks_permission_blocked else if(!exact) R.string.tasks_permission_approximate else R.string.tasks_permission_ready))
    if(showNotificationSettings) TaskSettingAction(R.string.tasks_enable_notifications, trailing = {
        Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = null)
    }) {
        val intent = if(android.os.Build.VERSION.SDK_INT >= 26) Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
        else Intent("android.settings.APP_NOTIFICATION_SETTINGS").putExtra("app_package", context.packageName).putExtra("app_uid", context.applicationInfo.uid)
        runCatching { context.startActivity(intent) }.onFailure {
            context.startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:${context.packageName}")))
        }
    }
    if(android.os.Build.VERSION.SDK_INT >= 31 && !exact) Action(R.string.tasks_enable_alarms) { context.startActivity(Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, android.net.Uri.parse("package:${context.packageName}"))) }
}

@Composable private fun repeatSummary(unit: String, interval: String, weekdays: List<Int>): String {
    val count = interval.toIntOrNull()?.coerceAtLeast(1) ?: 1
    val frequency = if(count == 1) stringResource(when(unit) {
        "WEEKLY" -> R.string.tasks_weekly
        "MONTHLY" -> R.string.tasks_monthly
        "YEARLY" -> R.string.tasks_yearly
        else -> R.string.tasks_daily
    }) else stringResource(when(unit) {
        "WEEKLY" -> R.string.tasks_every_weeks
        "MONTHLY" -> R.string.tasks_every_months
        "YEARLY" -> R.string.tasks_every_years
        else -> R.string.tasks_every_days
    }, count)
    val days = if(unit == "WEEKLY") weekdays.sorted().joinToString(", ") { DayOfWeek.of(it).getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.getDefault()) } else ""
    return if(days.isEmpty()) frequency else "$frequency · $days"
}
