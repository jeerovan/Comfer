package com.jeerovan.comfer.notes

import androidx.compose.animation.core.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private data class HeldNote(val note:Note,val size:IntSize)

// Lazy-grid offsets exclude before-content padding; pointer/overlay coordinates include it.
private fun LazyGridItemInfo.viewportOffset(layout:LazyGridLayoutInfo)=
    Offset(offset.x.toFloat(),(offset.y-layout.viewportStartOffset).toFloat())

/** A stationary parent owns the gesture; the grid reserves a real slot for the floating card. */
@Composable internal fun NotesReorderGrid(
    values:List<Note>,grid:Boolean,canDrag:Boolean,onDrop:(List<Note>)->Unit,
    onOpen:(Note)->Unit,onSelect:(Note)->Unit,modifier:Modifier=Modifier,topPadding:Dp=0.dp,
    onDragStart:()->Unit={},onPosition:(Int,Int)->Unit={_,_->},initialIndex:Int=0,initialOffset:Int=0,
    content:@Composable (Note)->Unit,
) {
    var preview by remember { mutableStateOf(values) }
    var held by remember { mutableStateOf<HeldNote?>(null) }
    var position by remember { mutableStateOf(Offset.Zero) }
    var settling by remember { mutableStateOf(false) }
    val settle=remember { Animatable(Offset.Zero,Offset.VectorConverter) }
    val scope=rememberCoroutineScope()
    val state=rememberLazyGridState(initialIndex,initialOffset)
    val latest by rememberUpdatedState(values)
    val drop by rememberUpdatedState(onDrop)
    val open by rememberUpdatedState(onOpen)
    val select by rememberUpdatedState(onSelect)
    val start by rememberUpdatedState(onDragStart)
    val dragEnabled by rememberUpdatedState(canDrag)
    val haptic=LocalHapticFeedback.current
    val density=LocalDensity.current
    LaunchedEffect(values){if(held==null)preview=values}
    LaunchedEffect(state.firstVisibleItemIndex,state.firstVisibleItemScrollOffset){delay(500);if(!state.isScrollInProgress&&held==null)onPosition(state.firstVisibleItemIndex,state.firstVisibleItemScrollOffset)}
    fun move(id:String,target:String):Boolean {
        val from=preview.indexOfFirst{it.id==id};val to=preview.indexOfFirst{it.id==target}
        if(from<0||to<0||from==to||preview[from].pinned!=preview[to].pinned)return false
        preview=preview.toMutableList().apply{add(to,removeAt(from))};return true
    }
    fun commit(){val current=latest.associateBy{it.id};val ids=preview.map{it.id}.toSet();drop(preview.mapNotNull{current[it.id]}+latest.filter{it.id !in ids})}
    fun updateTarget() {
        val active=held?:return
        val info=state.layoutInfo
        val layout=info.visibleItemsInfo
        val slot=layout.firstOrNull{it.key==active.note.id}?:return
        // Pointer events can outrun layout. Do not move back over the same stale neighbor
        // before the grid has measured the order requested by the previous event.
        if(slot.index!=preview.indexOfFirst{it.id==active.note.id})return
        val center=position+Offset(active.size.width/2f,active.size.height/2f)
        val target=layout.firstOrNull {
            val offset=it.viewportOffset(info)
            it.key!=active.note.id&&center.x>=offset.x&&center.x<=offset.x+it.size.width&&
                center.y>=offset.y&&center.y<=offset.y+it.size.height
        }
        if(target!=null&&target.index==preview.indexOfFirst{it.id==target.key})move(active.note.id,target.key as String)
    }
    fun finish(cancelled:Boolean) {
        val active=held?:return
        if(settling)return
        if(cancelled)preview=latest else commit()
        settling=true
        scope.launch {
            settle.snapTo(position)
            // Wait for the reserved slot to be laid out after the last move (or cancellation).
            withFrameNanos { };withFrameNanos { }
            val info=state.layoutInfo
            info.visibleItemsInfo.firstOrNull{it.key==active.note.id}?.let { slot->
                settle.animateTo(slot.viewportOffset(info),spring(stiffness=Spring.StiffnessMediumLow))
            }
            held=null;settling=false
            preview=latest
        }
    }
    // Scroll the grid underneath a stationary finger, not the floating card.
    LaunchedEffect(held?.note?.id,settling){while(held!=null&&!settling){
        val active=held?:break
        val center=position.y+active.size.height/2f
        val info=state.layoutInfo
        val edge=with(density){64.dp.toPx()};val speed=with(density){8.dp.toPx()}
        val viewportHeight=info.viewportEndOffset-info.viewportStartOffset
        val step=when{center>viewportHeight-edge->speed;center<edge->-speed;else->0f}
        if(step!=0f&&state.scrollBy(step)!=0f)updateTarget()
        delay(16)
    }}
    Box(modifier.clipToBounds().pointerInput(grid){
        awaitEachGesture {
            val down=awaitFirstDown(requireUnconsumed=false)
            val info=state.layoutInfo
            val source=info.visibleItemsInfo.firstOrNull {
                val offset=it.viewportOffset(info)
                down.position.x>=offset.x&&down.position.x<=offset.x+it.size.width&&
                    down.position.y>=offset.y&&down.position.y<=offset.y+it.size.height
            }
            val id=source?.key as? String
            if(id==null||settling)return@awaitEachGesture
            val origin=source.viewportOffset(info)
            val longPress=awaitLongPressOrCancellation(down.id)
            if(longPress==null){
                val up=currentEvent.changes.firstOrNull{it.id==down.id}
                if(up!=null&&!up.pressed&&!up.isConsumed){up.consume();latest.firstOrNull{it.id==id}?.let(open)}
                return@awaitEachGesture
            }
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            var moved=false
            try {
                var ended=false
                while(true) {
                    // Claim held movement before the lazy grid/nested thumb-reach scroll consumes it.
                    val change=awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull{it.id==longPress.id}
                    if(change==null||change.isConsumed)break
                    if(!change.pressed){change.consume();ended=true;break}
                    val distance=change.position-down.position
                    if(!moved&&distance.getDistance()>viewConfiguration.touchSlop){
                        moved=true
                        if(dragEnabled)latest.firstOrNull{it.id==id}?.let { note->
                            held=HeldNote(note,source.size)
                            start()
                        }
                    }
                    if(held!=null){
                        position=origin+distance
                        updateTarget()
                    }
                    change.consume()
                }
                if(held!=null)finish(!ended)
                else if(ended&&!moved)latest.firstOrNull{it.id==id}?.let(select)
            } finally {
                if(held!=null&&!settling)finish(true)
            }
        }
    }) {
        LazyVerticalGrid(if(grid)GridCells.Adaptive(150.dp) else GridCells.Fixed(1),Modifier.fillMaxSize(),state=state,
            contentPadding=PaddingValues(top=topPadding,bottom=12.dp),verticalArrangement=Arrangement.spacedBy(8.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            items(preview,key={it.id}){note->
                val id=note.id
                val active=held?.takeIf{it.note.id==id}
                Box(Modifier.animateItem(fadeInSpec=null,fadeOutSpec=null,placementSpec=spring(stiffness=Spring.StiffnessMediumLow))) {
                    if(active!=null) {
                        Spacer(Modifier.fillMaxWidth().height(with(density){active.size.height.toDp()}).testTag("note-slot-$id"))
                    } else Box(Modifier.testTag("note-$id").semantics(mergeDescendants=true){
                        onClick{open(note);true};onLongClick("Select note"){select(note);true}
                        if(canDrag)customActions=listOf(
                            CustomAccessibilityAction("Move up"){val i=preview.indexOfFirst{it.id==id};if(i>0&&move(id,preview[i-1].id)){commit();true}else false},
                            CustomAccessibilityAction("Move down"){val i=preview.indexOfFirst{it.id==id};if(i>=0&&i<preview.lastIndex&&move(id,preview[i+1].id)){commit();true}else false})
                    }){content(note)}
                }
            }
        }
        held?.let { active->
            Box(Modifier.offset {
                val point=if(settling&&settle.isRunning)settle.value else position
                IntOffset(point.x.roundToInt(),point.y.roundToInt())
            }.width(with(density){active.size.width.toDp()}).testTag("note-${active.note.id}").semantics(mergeDescendants=true){}) {
                content(active.note)
            }
        }
    }
}
