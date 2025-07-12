package com.jeerovan.comfer

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

private const val REST_LIST_NAME = "Rest"

class ManageLayersActivity : ComponentActivity() {
    private val viewModel: AppInfoViewModel by viewModels()

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
fun ManageLayersScreen(viewModel: AppInfoViewModel) {
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
    viewModel: AppInfoViewModel
) {
    val hapticFeedback = LocalHapticFeedback.current
    val reorderableLazyListState = rememberReorderableLazyListState(listState) { from, to ->
        Log.d("Move", "From:" + from.index.toString() + "To:" + to.index.toString())
        viewModel.moveAppInList(listName, from.index, to.index)
        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
    }


    Column(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(12.dp))
            .border(
                1.dp,
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f),
                RoundedCornerShape(12.dp)
            ),
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
                .width(76.dp).fillMaxHeight(),
            contentPadding = PaddingValues(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(apps.size, key = { index -> apps[index].packageName }) { index ->
                ReorderableItem(
                    reorderableLazyListState,
                    key = apps[index].packageName
                ) { isDragging ->
                    val elevation by animateDpAsState(if (isDragging) 4.dp else 0.dp)

                    Surface(
                        shape = CircleShape,
                        modifier = Modifier.longPressDraggableHandle(),
                        shadowElevation = elevation,

                    ) {
                        AppCard(app = apps[index], modifier = modifier)
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
