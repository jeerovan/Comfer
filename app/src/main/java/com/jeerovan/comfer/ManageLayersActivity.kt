package com.jeerovan.comfer

import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

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

    Box(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val density = LocalDensity.current
            val columns = mapOf(
                AppInfoManager.QUICK_APPS_LIST_NAME to uiState.quickApps,
                AppInfoManager.PRIMARY_APPS_LIST_NAME to uiState.primaryApps,
                REST_LIST_NAME to uiState.restApps
            )
            val listStates = mapOf(
                AppInfoManager.QUICK_APPS_LIST_NAME to quickListState,
                AppInfoManager.PRIMARY_APPS_LIST_NAME to primaryListState,
                REST_LIST_NAME to restListState
            )
            val titles = mapOf(
                AppInfoManager.QUICK_APPS_LIST_NAME to "Quick",
                AppInfoManager.PRIMARY_APPS_LIST_NAME to "Primary",
                REST_LIST_NAME to "Rest"
            )

            for ((listName, apps) in columns) {
                AppListColumn(
                    title = titles[listName]!!,
                    apps = apps,
                    listState = listStates[listName]!!,
                    isDropTarget = dndState.dropTarget == listName,
                    modifier = Modifier
                        .weight(1f)
                        .onGloballyPositioned { viewModel.updateColumnBounds(listName, it.boundsInRoot()) },
                    onDragStart = { app, position -> viewModel.onDragStart(app, listName, position) },
                    onDrag = { change, amount ->
                        change.consume()
                        viewModel.onDrag(amount)

                        val scrollState = listStates[dndState.dropTarget]
                        val bounds = when (dndState.dropTarget) {
                            AppInfoManager.QUICK_APPS_LIST_NAME -> dndState.quickColumnBounds
                            AppInfoManager.PRIMARY_APPS_LIST_NAME -> dndState.primaryColumnBounds
                            REST_LIST_NAME -> dndState.restColumnBounds
                            else -> null
                        }

                        if (scrollState != null && bounds != null) {
                            coroutineScope.launch {
                                val scrollThreshold = with(density) { 60.dp.toPx() }
                                if (dndState.dragPosition.y < bounds.top + scrollThreshold && scrollState.canScrollBackward) {
                                    scrollState.scrollBy(-30f)
                                } else if (dndState.dragPosition.y > bounds.bottom - scrollThreshold && scrollState.canScrollForward) {
                                    scrollState.scrollBy(30f)
                                }
                            }
                        }
                    },
                    onDragEnd = { viewModel.onDragEnd() }
                )
            }
        }

        if (dndState.draggedApp != null) {
            AppCard(
                app = dndState.draggedApp,
                modifier = Modifier
                    .zIndex(1f)
                    .offset { IntOffset(dndState.dragPosition.x.roundToInt(), dndState.dragPosition.y.roundToInt()) }
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
    isDropTarget: Boolean,
    modifier: Modifier = Modifier,
    onDragStart: (AppInfo, Offset) -> Unit,
    onDrag: (androidx.compose.ui.input.pointer.PointerInputChange, Offset) -> Unit,
    onDragEnd: () -> Unit
) {
    val backgroundColor = if (isDropTarget) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)

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
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(apps, key = { it.packageName }) { app ->
                var itemPosition by remember { mutableStateOf(Offset.Zero) }
                AppCard(
                    app = app,
                    modifier = Modifier
                        .onGloballyPositioned { itemPosition = it.positionInRoot() }
                        .pointerInput(app) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = { offset -> onDragStart(app, itemPosition + offset) },
                                onDrag = onDrag,
                                onDragEnd = onDragEnd,
                                onDragCancel = onDragEnd
                            )
                        }
                )
            }
        }
    }
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
