package com.jeerovan.comfer.tasks

import androidx.compose.animation.core.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private data class HeldTask(val item: TaskItem, val height: Int)

/** Like Notes: a stationary gesture owner, a full-size slot and a floating row. */
@Composable internal fun TasksReorderList(
    values: List<TaskItem>, canDrag: Boolean, state: LazyListState, modifier: Modifier,
    slotTag: String, onDrop: (String, String) -> Unit, onDragging: (Boolean) -> Unit = {},
    footer: @Composable () -> Unit = {}, content: @Composable (TaskItem) -> Unit,
) {
    var preview by remember { mutableStateOf(values) }
    var held by remember { mutableStateOf<HeldTask?>(null) }
    var position by remember { mutableFloatStateOf(0f) }
    var settling by remember { mutableStateOf(false) }
    val settle = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val latest by rememberUpdatedState(values)
    val enabled by rememberUpdatedState(canDrag)
    val drop by rememberUpdatedState(onDrop)
    val dragging by rememberUpdatedState(onDragging)
    LaunchedEffect(values) { if(held == null) preview = values }
    fun siblings(a: TaskItem, b: TaskItem) = a.listId == b.listId && a.parentId == b.parentId && (a.completedAt == null) == (b.completedAt == null)
    fun move(id: String, target: String): Boolean {
        val source = preview.find { it.id == id } ?: return false
        val destination = preview.find { it.id == target } ?: return false
        if(id == target || !siblings(source, destination)) return false
        // Keep expanded children beside their parent while moving the parent between siblings.
        val from = preview.indexOf(source)
        val to = preview.indexOf(destination)
        val block = preview.filter { it.id == id || it.parentId == id }
        val remaining = preview.filterNot { it in block }.toMutableList()
        val targetIndex = if(to > from) remaining.indexOfLast { it.id == target || it.parentId == target } + 1 else remaining.indexOf(destination)
        remaining.addAll(targetIndex, block)
        preview = remaining
        return true
    }
    fun commit(id: String) {
        val item = latest.find { it.id == id } ?: return
        val before = latest.filter { siblings(it, item) }
        val after = preview.filter { siblings(it, item) }
        val index = after.indexOfFirst { it.id == id }
        val target = before.getOrNull(index) ?: return
        if(target.id != id) drop(id, target.id)
    }
    fun updateTarget() {
        val active = held ?: return
        val info = state.layoutInfo
        val slot = info.visibleItemsInfo.find { it.key == active.item.id } ?: return
        if(slot.index != preview.indexOfFirst { it.id == active.item.id }) return
        val center = position + active.height / 2f
        val target = info.visibleItemsInfo.firstOrNull {
            it.key != active.item.id && center >= it.offset && center <= it.offset + it.size &&
                preview.any { row -> row.id == it.key && siblings(row, active.item) }
        }
        if(target != null && target.index == preview.indexOfFirst { it.id == target.key }) move(active.item.id, target.key as String)
    }
    fun finish(cancelled: Boolean) {
        val active = held ?: return
        if(settling) return
        if(cancelled) preview = latest else commit(active.item.id)
        settling = true
        scope.launch {
            settle.snapTo(position)
            withFrameNanos { }; withFrameNanos { }
            state.layoutInfo.visibleItemsInfo.find { it.key == active.item.id }?.let {
                settle.animateTo(it.offset.toFloat(), spring(stiffness = Spring.StiffnessMediumLow))
            }
            held = null; settling = false; preview = latest; dragging(false)
        }
    }
    LaunchedEffect(held?.item?.id, settling) {
        while(held != null && !settling) {
            val active = held ?: break
            val center = position + active.height / 2f
            val edge = with(density) { 64.dp.toPx() }
            val speed = with(density) { 8.dp.toPx() }
            val step = when { center > state.layoutInfo.viewportEndOffset - edge -> speed; center < edge -> -speed; else -> 0f }
            if(step != 0f && state.scrollBy(step) != 0f) updateTarget()
            delay(16)
        }
    }
    Box(Modifier.clipToBounds().pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            if(!enabled || settling) return@awaitEachGesture
            val source = state.layoutInfo.visibleItemsInfo.firstOrNull { down.position.y >= it.offset && down.position.y <= it.offset + it.size } ?: return@awaitEachGesture
            val item = latest.find { it.id == source.key } ?: return@awaitEachGesture
            val press = awaitLongPressOrCancellation(down.id) ?: return@awaitEachGesture
            var ended = false
            try {
                while(true) {
                    val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == press.id }
                    if(change == null || change.isConsumed) break
                    if(!change.pressed) { change.consume(); ended = true; break }
                    val distance = change.position.y - down.position.y
                    if(held == null && kotlin.math.abs(distance) > viewConfiguration.touchSlop) {
                        held = HeldTask(item, source.size)
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        dragging(true)
                    }
                    if(held != null) { position = source.offset + distance; updateTarget() }
                    change.consume()
                }
                if(held != null) finish(!ended)
            } finally { if(held != null && !settling) finish(true) }
        }
    }) {
        LazyColumn(modifier, state = state) {
            items(preview, key = { it.id }) { item ->
                Box(Modifier.animateItem(fadeInSpec = null, fadeOutSpec = null, placementSpec = spring(stiffness = Spring.StiffnessMediumLow))) {
                    val active = held?.takeIf { it.item.id == item.id }
                    if(active != null) Spacer(Modifier.fillMaxWidth().height(with(density) { active.height.toDp() }).testTag(slotTag))
                    else Box(Modifier.testTag("task-${item.id}").semantics {
                        if(canDrag) customActions = listOf(
                            CustomAccessibilityAction("Move up") { val rows = preview.filter { siblings(it, item) }; val index = rows.indexOf(item); if(index > 0 && move(item.id, rows[index - 1].id)) { commit(item.id); true } else false },
                            CustomAccessibilityAction("Move down") { val rows = preview.filter { siblings(it, item) }; val index = rows.indexOf(item); if(index >= 0 && index < rows.lastIndex && move(item.id, rows[index + 1].id)) { commit(item.id); true } else false },
                        )
                    }) { content(item) }
                }
            }
            item { footer() }
        }
        held?.let { active ->
            Box(Modifier.fillMaxWidth().offset { IntOffset(0, (if(settling && settle.isRunning) settle.value else position).roundToInt()) }.testTag("task-${active.item.id}")) { content(active.item) }
        }
    }
}
