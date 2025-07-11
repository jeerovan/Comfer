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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

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
                AppListColumn(
                    title = titles[listName]!!,
                    apps = apps,
                    listState = listStates[listName]!!,
                    modifier = Modifier
                        .weight(1f),
                    listName = listName,
                    viewModel = viewModel
                )
            }
        }
    }
}

@Composable
fun AppListColumn(
    title: String,
    apps: List<AppInfo>,
    listState: LazyListState,
    modifier: Modifier = Modifier,
    listName: String,
    viewModel: ManageLayersViewModel
) {
    val hapticFeedback = LocalHapticFeedback.current
    val reorderableLazyListState = rememberReorderableLazyListState(listState) { from, to ->
        viewModel.moveAppInList(listName, from.index, to.index)
        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
    }


    Column(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(12.dp))
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
                .fillMaxSize(),
            contentPadding = PaddingValues(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(apps.size, key = { index -> apps[index].packageName }) { index ->
                ReorderableItem(reorderableLazyListState, key = apps[index].packageName) { isDragging ->
                    val elevation by animateDpAsState(if (isDragging) 4.dp else 0.dp)

                    Surface(
                        modifier = Modifier.longPressDraggableHandle(),
                        shadowElevation = elevation
                    ) {
                        AppCard(app=apps[index],modifier = modifier)
                    }
                }
            }
        }
    }
}

@Composable
fun AppCard(app: AppInfo, modifier: Modifier) {
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
