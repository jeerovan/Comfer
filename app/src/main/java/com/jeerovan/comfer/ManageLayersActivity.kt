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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private const val REST_LIST_NAME = "Rest"

class ManageLayersActivity : ComponentActivity() {
    private val viewModel: ManageLayersViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    ManageLayersScreen(viewModel)
                }
            }
        }
    }
}

@Composable
fun ManageLayersScreen(viewModel: ManageLayersViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val dndState = uiState.dragAndDropState

    val coroutineScope = rememberCoroutineScope()
    val quickListState = rememberLazyListState()
    val primaryListState = rememberLazyListState()
    val restListState = rememberLazyListState()

    var dropTargetIndex by remember { mutableIntStateOf(-1) }
    var autoScrollJob by remember { mutableStateOf<Job?>(null) }

    val listStates = remember {
        mapOf(
            AppInfoManager.QUICK_APPS_LIST_NAME to quickListState,
            AppInfoManager.PRIMARY_APPS_LIST_NAME to primaryListState,
            REST_LIST_NAME to restListState
        )
    }

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
            val density = LocalDensity.current
            for ((listName, apps) in columns) {
                AppListColumn(
                    title = titles[listName]!!,
                    apps = apps,
                    listState = listStates[listName]!!,
                    dndState = dndState,
                    dropIndex = if (dndState.dropTarget == listName) dropTargetIndex else -1,
                    modifier = Modifier
                        .weight(1f),
                    onColumnBoundsChanged = { bounds ->
                        viewModel.updateColumnBounds(listName, bounds)
                    },
                    onDragStart = { app, position ->
                        viewModel.onDragStart(app, listName, position)
                    },
                    onDrag = { change, amount ->
                        change.consume()
                        viewModel.onDrag(amount)

                        val currentPosition = dndState.dragPosition
                        val targetList = dndState.dropTarget
                        val targetListState = listStates[targetList]

                        if (targetList != null && targetListState != null) {
                            val visibleItems = targetListState.layoutInfo.visibleItemsInfo
                            val listBounds = when (targetList) {
                                AppInfoManager.QUICK_APPS_LIST_NAME -> dndState.quickColumnBounds
                                AppInfoManager.PRIMARY_APPS_LIST_NAME -> dndState.primaryColumnBounds
                                REST_LIST_NAME -> dndState.restColumnBounds
                                else -> null
                            }
                            val relativeY = currentPosition.y - (listBounds?.top ?: 0f)

                            val targetItem = visibleItems.find { item: LazyListItemInfo -> // Explicitly type item
                                // For LazyColumn, item.offset is the y-offset, and item.size is the height
                                relativeY < item.offset + item.size / 2
                            }
                            dropTargetIndex = targetItem?.index ?: visibleItems.lastOrNull()?.let { lastItem ->
                                if (relativeY > lastItem.offset + lastItem.size / 2) lastItem.index + 1 else -1
                            } ?: 0
                        } else {
                            dropTargetIndex = -1
                        }


                        if (autoScrollJob?.isActive != true) {
                            autoScrollJob = coroutineScope.launch {
                                val scrollState = listStates[dndState.dropTarget]
                                val bounds = when (dndState.dropTarget) {
                                    AppInfoManager.QUICK_APPS_LIST_NAME -> dndState.quickColumnBounds
                                    AppInfoManager.PRIMARY_APPS_LIST_NAME -> dndState.primaryColumnBounds
                                    REST_LIST_NAME -> dndState.restColumnBounds
                                    else -> null
                                }

                                if (scrollState != null && bounds != null) {
                                    val scrollThreshold = with(density) { 60.dp.toPx() }
                                    if (dndState.dragPosition.y < bounds.top + scrollThreshold && scrollState.canScrollBackward) {
                                        scrollState.scrollBy(-30f)
                                    } else if (dndState.dragPosition.y > bounds.bottom - scrollThreshold && scrollState.canScrollForward) {
                                        scrollState.scrollBy(30f)
                                    }
                                }
                            }
                        }
                    },
                    onDragEnd = {
                        autoScrollJob?.cancel()
                        viewModel.onDragEnd(dropTargetIndex)
                        dropTargetIndex = -1
                    }
                )
            }
        }

        if (dndState.draggedApp != null) {
            AppCard(
                app = dndState.draggedApp,
                modifier = Modifier
                    .zIndex(1f)
                    .graphicsLayer {
                        translationX = dndState.dragPosition.x
                        translationY = dndState.dragPosition.y
                    }
                    .shadow(8.dp, RoundedCornerShape(12.dp))
            )
        }
    }
}

@Composable
fun AppListColumn(
    title: String,
    apps: List<AppInfo>,
    listState: LazyListState,
    dndState: DragAndDropState,
    dropIndex: Int,
    modifier: Modifier = Modifier,
    onColumnBoundsChanged: (Rect) -> Unit,
    onDragStart: (AppInfo, Offset) -> Unit,
    onDrag: (androidx.compose.ui.input.pointer.PointerInputChange, Offset) -> Unit,
    onDragEnd: () -> Unit
) {
    val isDropTarget = dropIndex != -1
    val backgroundColor = if (isDropTarget) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    var lazyColumnPosition by remember { mutableStateOf(Offset.Zero) }

    Column(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(12.dp))
            .background(backgroundColor)
            .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f), RoundedCornerShape(12.dp)),
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
                .fillMaxSize()
                .onGloballyPositioned {
                    onColumnBoundsChanged(it.boundsInRoot())
                    lazyColumnPosition = it.positionInRoot()
                },
            contentPadding = PaddingValues(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(apps.size, key = { apps[it].packageName }) { index ->
                val app = apps[index]
                val isBeingDragged = dndState.draggedApp?.packageName == app.packageName

                val animatedDp by animateDpAsState(targetValue = if (dropIndex == index && isDropTarget) 8.dp else 0.dp, label = "padding")
                if (dropIndex == index && isDropTarget) {
                    DropPlaceholder()
                }

                if (isBeingDragged) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp)
                            .background(
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
                                RoundedCornerShape(12.dp)
                            )
                    )
                } else {
                    val itemInfo = remember(listState.layoutInfo.visibleItemsInfo) {
                        listState.layoutInfo.visibleItemsInfo.find { it.index == index }
                    }
                    DraggableAppCard(
                        app = app,
                        modifier = Modifier.padding(top = animatedDp),
                        onDragStart = { pressOffset ->
                            itemInfo?.let { item: LazyListItemInfo -> // Explicitly type item
                                // For LazyColumn:
                                // item.offset is the y-offset (Int)
                                // If it's a vertical list, x-offset is 0 relative to the LazyColumn's content area
                                val itemPositionInRoot = lazyColumnPosition + Offset(0f, item.offset.toFloat()) + pressOffset
                                onDragStart(app, itemPositionInRoot)
                            }
                        },
                        onDrag = onDrag,
                        onDragEnd = onDragEnd
                    )
                }
            }
            if (dropIndex == apps.size && isDropTarget) {
                item {
                    DropPlaceholder()
                }
            }
        }
    }
}

@Composable
fun DraggableAppCard(
    app: AppInfo,
    modifier: Modifier = Modifier,
    onDragStart: (Offset) -> Unit,
    onDrag: (androidx.compose.ui.input.pointer.PointerInputChange, Offset) -> Unit,
    onDragEnd: () -> Unit
) {
    AppCard(
        app = app,
        modifier = modifier.pointerInput(app) {
            detectDragGesturesAfterLongPress(
                onDragStart = onDragStart,
                onDrag = onDrag,
                onDragEnd = onDragEnd,
                onDragCancel = onDragEnd
            )
        }
    )
}

@Composable
fun DropPlaceholder() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(8.dp)
            .padding(vertical = 4.dp)
            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp))
    )
}

@Composable
fun AppCard(app: AppInfo, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painter = rememberDrawablePainter(drawable = app.icon),
            contentDescription = null,
            modifier = Modifier.size(40.dp)
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = app.label.toString(),
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
