package com.jeerovan.comfer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

private const val REST_LIST_NAME = "Rest"

private class DragDropState(
    val onMove: (String, Int, String, Int) -> Unit,
    val coroutineScope: CoroutineScope
) {
    var isDragging by mutableStateOf(false)
    var dragPosition by mutableStateOf(Offset.Zero)
    var dragOffset by mutableStateOf(Offset.Zero)
    var draggedItem by mutableStateOf<AppInfo?>(null)
    var sourceListName by mutableStateOf<String?>(null)
    var sourceIndex by mutableStateOf(-1)
    val columnBounds = mutableMapOf<String, Rect>()
    var dropTargetListName by mutableStateOf<String?>(null)
    var dropTargetIndex by mutableStateOf(-1)

    fun onDragStart(item: AppInfo, listName: String, index: Int) {
        isDragging = true
        draggedItem = item
        sourceListName = listName
        sourceIndex = index

    }

    fun onDrag(offset: Offset) {
        dragOffset += offset
    }

    fun onDragEnd() {
        val fromList = sourceListName
        val fromIndex = sourceIndex
        val toList = dropTargetListName
        val toIndex = dropTargetIndex

        if (fromList != null && toList != null && fromIndex != -1) {
            onMove(fromList, fromIndex, toList, toIndex)
        }

        isDragging = false
        dragOffset = Offset.Zero
        draggedItem = null
        sourceListName = null
        sourceIndex = -1
        dropTargetListName = null
        dropTargetIndex = -1
    }
}

private val LocalDragDropState = staticCompositionLocalOf<DragDropState?> { null }

class ManageAppListActivity : ComponentActivity() {
    private val viewModel: AppInfoViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    ManageAppListScreen(viewModel)
                }
            }
        }
    }
}

@Composable
fun ManageAppListScreen(viewModel: AppInfoViewModel) {
    val uiState by viewModel.uiState.collectAsState()

    val quickListState = rememberLazyListState()
    val primaryListState = rememberLazyListState()
    val restListState = rememberLazyListState()

    val listStates = remember {
        mapOf(
            AppInfoManager.QUICK_APPS_LIST_NAME to quickListState,
            AppInfoManager.PRIMARY_APPS_LIST_NAME to primaryListState,
            REST_LIST_NAME to restListState
        )
    }

    val coroutineScope = rememberCoroutineScope()
    val dragDropState = remember {
        DragDropState(
            onMove = viewModel::moveApp,
            coroutineScope = coroutineScope
        )
    }

    CompositionLocalProvider(LocalDragDropState provides dragDropState) {
        Box(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val columns = mapOf(
                    AppInfoManager.QUICK_APPS_LIST_NAME to uiState.quickApps,
                    AppInfoManager.PRIMARY_APPS_LIST_NAME to uiState.primaryApps,
                    REST_LIST_NAME to uiState.restApps
                )
                val titles = mapOf(
                    AppInfoManager.QUICK_APPS_LIST_NAME to "Quick",
                    AppInfoManager.PRIMARY_APPS_LIST_NAME to "Primary",
                    REST_LIST_NAME to "Rest"
                )
                for ((listName, apps) in columns) {
                    AppListColumnView(
                        title = titles[listName]!!,
                        apps = apps,
                        listState = listStates[listName]!!,
                        modifier = Modifier.weight(1f),
                        listName = listName
                    )
                }
            }

            if (dragDropState.isDragging) {
                var targetSize by remember { mutableStateOf(IntSize.Zero) }
                val elevation by animateDpAsState(if (dragDropState.isDragging) 8.dp else 0.dp)
                Box(
                    modifier = Modifier
                        .graphicsLayer {
                            val offset = dragDropState.dragPosition + dragDropState.dragOffset
                            translationX = offset.x - targetSize.width / 2
                            translationY = offset.y - targetSize.height / 2
                            alpha = if (targetSize == IntSize.Zero) 0f else .9f
                        }
                        .onGloballyPositioned {
                            targetSize = it.size
                        }
                ) {
                    Surface(shape = CircleShape, shadowElevation = elevation) {
                        dragDropState.draggedItem?.let {
                            AppCardView(app = it, modifier = Modifier)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AppListColumnView(
    title: String,
    apps: List<AppInfo>,
    listState: LazyListState,
    modifier: Modifier = Modifier,
    listName: String
) {
    val dragDropState = LocalDragDropState.current!!

    LaunchedEffect(dragDropState.isDragging, dragDropState.dragOffset) {
        if (dragDropState.isDragging) {
            val dragPosition = dragDropState.dragPosition + dragDropState.dragOffset
            val columnBounds = dragDropState.columnBounds[listName]
            if (columnBounds?.contains(dragPosition) == true) {
                dragDropState.dropTargetListName = listName
                val scrollThreshold = 100f
                if (dragPosition.y < columnBounds.top + scrollThreshold) {
                    listState.scrollBy(-20f)
                } else if (dragPosition.y > columnBounds.bottom - scrollThreshold) {
                    listState.scrollBy(20f)
                }

                val visibleItems = listState.layoutInfo.visibleItemsInfo
                val targetItem = visibleItems.firstOrNull {
                    val itemTop = columnBounds.top + it.offset
                    val itemBottom = itemTop + it.size
                    dragPosition.y in itemTop..itemBottom
                }

                if (targetItem != null) {
                    val itemCenterY = columnBounds.top + targetItem.offset + targetItem.size / 2
                    val isAfter = dragPosition.y > itemCenterY
                    dragDropState.dropTargetIndex = if (isAfter) targetItem.index + 1 else targetItem.index
                } else {
                    val lastItem = visibleItems.lastOrNull()
                    if (lastItem != null && dragPosition.y > columnBounds.top + lastItem.offset + lastItem.size) {
                        dragDropState.dropTargetIndex = apps.size
                    } else if (apps.isEmpty() || (visibleItems.firstOrNull() != null && dragPosition.y < columnBounds.top + visibleItems.first().offset)) {
                        dragDropState.dropTargetIndex = 0
                    } else {
                        dragDropState.dropTargetIndex = -1
                    }
                }

            } else if (dragDropState.dropTargetListName == listName) {
                dragDropState.dropTargetListName = null
                dragDropState.dropTargetIndex = -1
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(12.dp))
            .border(
                1.dp,
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f),
                RoundedCornerShape(12.dp)
            )
            .onGloballyPositioned {
                dragDropState.columnBounds[listName] = it.boundsInWindow()
            },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(16.dp)
        )
        LazyColumn(
            state = listState,
            modifier = Modifier
                .width(76.dp)
                .fillMaxHeight(),
            contentPadding = PaddingValues(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            itemsIndexed(apps, key = { _, app -> app.packageName }) { index, app ->
                val isDropTarget = dragDropState.dropTargetListName == listName &&
                        dragDropState.dropTargetIndex == index &&
                        (dragDropState.sourceListName != listName || dragDropState.sourceIndex != index)

                if (isDropTarget) {
                    DropPlaceholder()
                }

                DraggableItem(
                    item = app,
                    listName = listName,
                    index = index
                ) { isDragging, _ ->
                    val elevation by animateDpAsState(if (isDragging) 4.dp else 0.dp)
                    if(isDragging){
                        DropPlaceholder()
                    } else {
                        Surface(
                            modifier = Modifier,
                            shadowElevation = elevation,
                        ) {
                            AppCardView(app = app, modifier = Modifier)
                        }
                    }
                }
            }
            val isLastItemDropTarget = dragDropState.dropTargetListName == listName &&
                    dragDropState.dropTargetIndex == apps.size &&
                    (dragDropState.sourceListName != listName || dragDropState.sourceIndex != apps.size)
            if (isLastItemDropTarget) {
                item { DropPlaceholder() }
            }
        }
    }
}

@Composable
fun DropPlaceholder() {
    Spacer(
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp)
            .padding(vertical = 4.dp)
            .clip(CircleShape)
            .background(Color.Gray)
    )
}

@Composable
fun DraggableItem(
    item: AppInfo,
    listName: String,
    index: Int,
    content: @Composable (isDragging: Boolean, draggedItem: AppInfo?) -> Unit
) {
    val dragDropState = LocalDragDropState.current!!
    val hapticFeedback = LocalHapticFeedback.current
    val isDragging =
        dragDropState.isDragging && dragDropState.draggedItem?.packageName == item.packageName

    var itemBounds by remember { mutableStateOf<Rect?>(null) }

    Box(
        modifier = Modifier
            .onGloballyPositioned {
                itemBounds = it.boundsInWindow()
            }
            .pointerInput(Unit) {
                detectDragGesturesAfterLongPress(
                    onDragStart = {
                        itemBounds?.let {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                            dragDropState.onDragStart(item, listName, index)
                            dragDropState.dragPosition = it.center
                        }
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        dragDropState.onDrag(dragAmount)
                    },
                    onDragEnd = { dragDropState.onDragEnd() },
                    onDragCancel = { dragDropState.onDragEnd() }
                )
            }
    ) {
        content(isDragging, dragDropState.draggedItem)
    }
}

@Composable
fun AppCardView(app: AppInfo, modifier: Modifier) {
    Row(
        modifier = modifier
            .width(60.dp)
            .clip(CircleShape)
            .background(Color.White)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Image(
            painter = rememberDrawablePainter(drawable = app.icon),
            contentDescription = app.label.toString(),
            modifier = Modifier.size(40.dp),
        )
    }
}


