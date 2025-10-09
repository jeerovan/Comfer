package com.jeerovan.comfer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import com.jeerovan.comfer.ui.theme.ComferTheme
import com.jeerovan.comfer.utils.CommonUtil.getShapeFromShape
import com.jeerovan.comfer.utils.GuideUtil.GuideDialog
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import kotlin.math.min

private const val REST_LIST_NAME = "Rest"
private const val MAX_QUICK_APPS = 8

class ManageAppListActivity : ComponentActivity() {
    private val viewModel: AppInfoViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ComferTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ManageLayersScreen(viewModel)
                }
            }
        }
    }
}

@Composable
fun ManageLayersScreen(viewModel: AppInfoViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val quickListState = rememberLazyListState()
    val primaryListState = rememberLazyListState()
    val restListState = rememberLazyListState()

    val iconSize = min(46,PreferenceManager.getIconSize(context))
    val shape = PreferenceManager.getIconShape(context)
    val alphabeticalOrder = PreferenceManager.getAlphabeticalOrder(context)
    val iconShape = getShapeFromShape(shape,iconSize.dp)
    val listStates = remember {
        mapOf(
            AppInfoManager.QUICK_APPS_LIST_NAME to quickListState,
            AppInfoManager.PRIMARY_APPS_LIST_NAME to primaryListState,
            REST_LIST_NAME to restListState
        )
    }
    val primaryApps = if(alphabeticalOrder) uiState.primaryApps.sortedBy { it.label.toString() } else uiState.primaryApps

    var selectedList by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedIndices by rememberSaveable { mutableStateOf(emptySet<Int>()) }

    val onItemSelect = { listName: String, index: Int ->
        if (selectedList != listName) {
            selectedList = listName
            selectedIndices = setOf(index)
        } else {
            selectedIndices = if (selectedIndices.contains(index)) {
                selectedIndices - index
            } else {
                selectedIndices + index
            }
        }
    }

    val clearSelection = {
        selectedList = null
        selectedIndices = emptySet()
    }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var guideShown by remember { mutableStateOf(true) }
    val guideKeyword = "app_list_guide_1"
    var canShowGuide by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        guideShown = PreferenceManager.getBoolean(context,guideKeyword,false)
        delay(500)
        canShowGuide = true
    }
    fun onGuideDismiss(){
        PreferenceManager.setBoolean(context,guideKeyword,true)
        guideShown = true
    }
    if(!guideShown && canShowGuide) GuideDialog(
        onDismiss = {onGuideDismiss()},
        title = "Navigation",
        steps = listOf(
            "Tap to select and move.",
            "Long press and drag to re-order."
        )
    )

    Box(modifier = Modifier
        .fillMaxSize()
        .windowInsetsPadding(WindowInsets.navigationBars)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 8.dp, top = 8.dp, end = 8.dp, bottom = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AppListColumn(
                title = "⚡",
                apps = uiState.quickApps,
                canReOrder = true,
                listState = listStates[AppInfoManager.QUICK_APPS_LIST_NAME]!!,
                modifier = Modifier
                    .weight(1f),
                listName = AppInfoManager.QUICK_APPS_LIST_NAME,
                viewModel = viewModel,
                selectedList = selectedList,
                selectedIndices = selectedIndices,
                onItemSelect = onItemSelect,
                iconShape = iconShape,
                iconSize = iconSize.dp
            )
            Column(modifier = Modifier.fillMaxHeight(), verticalArrangement = Arrangement.Center) {
                Row {
                    // Button to move apps from primary list to quick list
                    OutlinedButton(
                        shape = CircleShape,
                        modifier = Modifier.size(40.dp),
                        contentPadding = PaddingValues(0.dp),
                        onClick = {
                            if (uiState.quickApps.size + selectedIndices.size > MAX_QUICK_APPS) {
                                scope.launch {
                                    snackbarHostState.showSnackbar("Maximum $MAX_QUICK_APPS apps only")
                                }
                            } else {
                                viewModel.moveAppsToList(
                                    AppInfoManager.PRIMARY_APPS_LIST_NAME,
                                    AppInfoManager.QUICK_APPS_LIST_NAME,
                                    selectedIndices.toList().sortedDescending()
                                )
                                clearSelection()
                            }
                        },
                        enabled = selectedList == AppInfoManager.PRIMARY_APPS_LIST_NAME && selectedIndices.isNotEmpty()
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Move to Quick",
                        )
                    }
                }
                Spacer(Modifier.height(20.dp))
                Row {
                    // Button to move apps from quick list to primary list
                    OutlinedButton(
                        shape = CircleShape,
                        modifier = Modifier.size(40.dp),
                        contentPadding = PaddingValues(0.dp),
                        onClick = {
                            viewModel.moveAppsToList(
                                AppInfoManager.QUICK_APPS_LIST_NAME,
                                AppInfoManager.PRIMARY_APPS_LIST_NAME,
                                selectedIndices.toList().sortedDescending()
                            )
                            clearSelection()
                        },
                        enabled = selectedList == AppInfoManager.QUICK_APPS_LIST_NAME && selectedIndices.isNotEmpty()
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "Move to Primary"
                        )
                    }
                }
            }
            AppListColumn(
                title = "⭐",
                apps = primaryApps,
                canReOrder = !alphabeticalOrder,
                listState = listStates[AppInfoManager.PRIMARY_APPS_LIST_NAME]!!,
                modifier = Modifier
                    .weight(1f),
                listName = AppInfoManager.PRIMARY_APPS_LIST_NAME,
                viewModel = viewModel,
                selectedList = selectedList,
                selectedIndices = selectedIndices,
                onItemSelect = onItemSelect,
                iconShape = iconShape,
                iconSize = iconSize.dp
            )
            Column(modifier = Modifier.fillMaxHeight(), verticalArrangement = Arrangement.Center) {
                Row {
                    // Button to move apps from ghost list to primary list
                    OutlinedButton(
                        shape = CircleShape,
                        modifier = Modifier.size(40.dp),
                        contentPadding = PaddingValues(0.dp),
                        onClick = {
                            viewModel.moveAppsToList(
                                REST_LIST_NAME,
                                AppInfoManager.PRIMARY_APPS_LIST_NAME,
                                selectedIndices.toList().sortedDescending()
                            )
                            clearSelection()
                        },
                        enabled = selectedList == REST_LIST_NAME && selectedIndices.isNotEmpty()
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Move to Primary",
                        )
                    }
                }
                Spacer(Modifier.height(20.dp))
                Row {
                    // Button to move apps from primary list to ghost list
                    OutlinedButton(
                        shape = CircleShape,
                        modifier = Modifier.size(40.dp),
                        contentPadding = PaddingValues(0.dp),
                        onClick = {
                            viewModel.moveAppsToList(
                                AppInfoManager.PRIMARY_APPS_LIST_NAME,
                                REST_LIST_NAME,
                                selectedIndices.toList().sortedDescending()
                            )
                            clearSelection()
                        },
                        enabled = selectedList == AppInfoManager.PRIMARY_APPS_LIST_NAME && selectedIndices.isNotEmpty()
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "Move to Ghost"
                        )
                    }
                }
            }
            AppListColumn(
                title = "👻",
                apps = uiState.restApps,
                canReOrder = true,
                listState = listStates[REST_LIST_NAME]!!,
                modifier = Modifier
                    .weight(1f),
                listName = REST_LIST_NAME,
                viewModel = viewModel,
                selectedList = selectedList,
                selectedIndices = selectedIndices,
                onItemSelect = onItemSelect,
                iconShape = iconShape,
                iconSize = iconSize.dp
            )
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

@Composable
fun AppListColumn(
    title: String,
    apps: List<AppInfo>,
    canReOrder: Boolean,
    listState: LazyListState,
    modifier: Modifier = Modifier,
    listName: String,
    viewModel: AppInfoViewModel,
    selectedList: String?,
    selectedIndices: Set<Int>,
    iconSize: Dp,
    iconShape: Shape,
    onItemSelect: (String, Int) -> Unit
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
                .width(76.dp)
                .fillMaxHeight(),
            contentPadding = PaddingValues(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(apps.size, key = { index -> apps[index].packageName }) { index ->
                if(canReOrder) {
                    ReorderableItem(
                        reorderableLazyListState,
                        key = apps[index].packageName
                    ) { isDragging ->
                        val elevation by animateDpAsState(
                            if (isDragging) 4.dp else 0.dp,
                            label = ""
                        )
                        val isSelected = selectedList == listName && selectedIndices.contains(index)

                        Surface(
                            shape = iconShape,
                            modifier = Modifier
                                .longPressDraggableHandle()
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) { onItemSelect(listName, index) },
                            shadowElevation = elevation,

                            ) {
                            AppCard(app = apps[index], isSelected = isSelected, iconSize, iconShape)
                        }
                    }
                } else {
                    // Normal list with selectable items
                    val isSelected = selectedList == listName && selectedIndices.contains(index)
                    val elevation by animateDpAsState(
                        if (isSelected) 2.dp else 0.dp,
                        label = ""
                    )
                    Surface(
                        shape = iconShape,
                        modifier = Modifier
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { onItemSelect(listName, index) },
                        shadowElevation = elevation,
                    ) {
                        AppCard(
                            app = apps[index],
                            isSelected = isSelected,
                            iconSize,
                            iconShape
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AppCard(app: AppInfo, isSelected: Boolean,iconSize: Dp,iconShape: Shape) {
    val borderColor = if (isSystemInDarkTheme()) Color.Red else Color.Gray
    val borderModifier = if (isSelected) {
        Modifier.border(2.dp, borderColor, iconShape)
    } else {
        Modifier
    }
    Row(
        modifier = Modifier
            .clip(iconShape)
            .then(borderModifier)
            .padding(7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(iconSize)
                .clip(iconShape),
            contentAlignment = Alignment.Center
        ) {
            // Background Layer
            if (app.background != null) {
                Image(
                    painter = rememberDrawablePainter(drawable = app.background),
                    contentDescription = "${app.label} background",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.FillBounds
                )
            }

            // Foreground Layer
            if (app.foreground != null) {
                Image(
                    painter = rememberDrawablePainter(drawable = app.foreground),
                    contentDescription = app.label.toString(),
                    modifier = Modifier.fillMaxSize().scale(app.scale), // Let it fill the clipped Box
                    contentScale = ContentScale.FillBounds
                )
            }
        }
    }
}
