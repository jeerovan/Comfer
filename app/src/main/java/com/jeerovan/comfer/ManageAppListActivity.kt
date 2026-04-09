package com.jeerovan.comfer

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.jeerovan.comfer.ui.theme.ComferTheme
import com.jeerovan.comfer.utils.CommonUtil.getShapeFromShape
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import kotlin.math.min

private const val REST_LIST_NAME = "Rest"
private const val MAX_QUICK_APPS = 8

class ManageAppListActivity : AppCompatActivity() {
    private val viewModel: AppInfoViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        // Only set colors for Android 14 and below to avoid deprecation warnings
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            window.statusBarColor = android.graphics.Color.TRANSPARENT
            window.navigationBarColor = android.graphics.Color.TRANSPARENT
        }
        // Handle display cutout
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        setContent {
            ComferTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)
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

    val iconSize = min(46, PreferenceManager.getIconSize(context))
    val shape = PreferenceManager.getIconShape(context)
    val alphabeticalOrder = PreferenceManager.getAlphabeticalOrder(context)
    val iconShape = getShapeFromShape(shape, iconSize.dp)
    val listStates = remember {
        mapOf(
            AppInfoManager.QUICK_APPS_LIST_NAME to quickListState,
            AppInfoManager.PRIMARY_APPS_LIST_NAME to primaryListState,
            REST_LIST_NAME to restListState
        )
    }
    // Stable sorted lists
    val primaryApps = remember(uiState.primaryApps, alphabeticalOrder) {
        if (alphabeticalOrder) uiState.primaryApps.sortedBy { it.label.toString() } else uiState.primaryApps
    }

    // Changed to track Package Names instead of Indices for stability
    var selectedList by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedPackageNames by rememberSaveable { mutableStateOf(emptySet<String>()) }

    val onItemSelect = { listName: String, packageName: String ->
        if (selectedList != listName) {
            selectedList = listName
            selectedPackageNames = setOf(packageName)
        } else {
            selectedPackageNames = if (selectedPackageNames.contains(packageName)) {
                selectedPackageNames - packageName
            } else {
                selectedPackageNames + packageName
            }
        }
    }

    val clearSelection = {
        selectedList = null
        selectedPackageNames = emptySet()
    }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 24.dp)
            .windowInsetsPadding(WindowInsets.navigationBars)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 8.dp, top = 8.dp, end = 8.dp, bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AppListColumn(
                title = "⚡",
                apps = uiState.quickApps,
                canReOrder = true,
                listState = listStates[AppInfoManager.QUICK_APPS_LIST_NAME]!!,
                modifier = Modifier.weight(1f),
                listName = AppInfoManager.QUICK_APPS_LIST_NAME,
                viewModel = viewModel,
                selectedList = selectedList,
                selectedPackageNames = selectedPackageNames,
                onItemSelect = onItemSelect,
                iconShape = iconShape,
                iconSize = iconSize.dp
            )
            Column(modifier = Modifier.fillMaxHeight(), verticalArrangement = Arrangement.Center) {
                Row {
                    OutlinedButton(
                        shape = CircleShape,
                        modifier = Modifier.size(40.dp),
                        contentPadding = PaddingValues(0.dp),
                        onClick = {
                            if (uiState.quickApps.size + selectedPackageNames.size > MAX_QUICK_APPS) {
                                scope.launch {
                                    snackbarHostState.showSnackbar("Maximum $MAX_QUICK_APPS apps only")
                                }
                            } else {
                                viewModel.moveAppsToList(
                                    AppInfoManager.PRIMARY_APPS_LIST_NAME,
                                    AppInfoManager.QUICK_APPS_LIST_NAME,
                                    selectedPackageNames
                                )
                                clearSelection()
                            }
                        },
                        enabled = selectedList == AppInfoManager.PRIMARY_APPS_LIST_NAME && selectedPackageNames.isNotEmpty()
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.button_to_move_app_to_quick_app_list),
                        )
                    }
                }
                Spacer(Modifier.height(20.dp))
                Row {
                    OutlinedButton(
                        shape = CircleShape,
                        modifier = Modifier.size(40.dp),
                        contentPadding = PaddingValues(0.dp),
                        onClick = {
                            viewModel.moveAppsToList(
                                AppInfoManager.QUICK_APPS_LIST_NAME,
                                AppInfoManager.PRIMARY_APPS_LIST_NAME,
                                selectedPackageNames
                            )
                            clearSelection()
                        },
                        enabled = selectedList == AppInfoManager.QUICK_APPS_LIST_NAME && selectedPackageNames.isNotEmpty()
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = stringResource(R.string.button_to_move_app_to_primary_app_list)
                        )
                    }
                }
            }
            AppListColumn(
                title = "⭐",
                apps = primaryApps,
                canReOrder = !alphabeticalOrder,
                listState = listStates[AppInfoManager.PRIMARY_APPS_LIST_NAME]!!,
                modifier = Modifier.weight(1f),
                listName = AppInfoManager.PRIMARY_APPS_LIST_NAME,
                viewModel = viewModel,
                selectedList = selectedList,
                selectedPackageNames = selectedPackageNames,
                onItemSelect = onItemSelect,
                iconShape = iconShape,
                iconSize = iconSize.dp
            )
            Column(modifier = Modifier.fillMaxHeight(), verticalArrangement = Arrangement.Center) {
                Row {
                    OutlinedButton(
                        shape = CircleShape,
                        modifier = Modifier.size(40.dp),
                        contentPadding = PaddingValues(0.dp),
                        onClick = {
                            viewModel.moveAppsToList(
                                REST_LIST_NAME,
                                AppInfoManager.PRIMARY_APPS_LIST_NAME,
                                selectedPackageNames
                            )
                            clearSelection()
                        },
                        enabled = selectedList == REST_LIST_NAME && selectedPackageNames.isNotEmpty()
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.button_to_move_app_to_primary_app_list),
                        )
                    }
                }
                Spacer(Modifier.height(20.dp))
                Row {
                    OutlinedButton(
                        shape = CircleShape,
                        modifier = Modifier.size(40.dp),
                        contentPadding = PaddingValues(0.dp),
                        onClick = {
                            viewModel.moveAppsToList(
                                AppInfoManager.PRIMARY_APPS_LIST_NAME,
                                REST_LIST_NAME,
                                selectedPackageNames
                            )
                            clearSelection()
                        },
                        enabled = selectedList == AppInfoManager.PRIMARY_APPS_LIST_NAME && selectedPackageNames.isNotEmpty()
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = stringResource(R.string.button_to_move_app_to_ghost_app_list)
                        )
                    }
                }
            }
            AppListColumn(
                title = "👻",
                apps = uiState.restApps,
                canReOrder = true,
                listState = listStates[REST_LIST_NAME]!!,
                modifier = Modifier.weight(1f),
                listName = REST_LIST_NAME,
                viewModel = viewModel,
                selectedList = selectedList,
                selectedPackageNames = selectedPackageNames,
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
    selectedPackageNames: Set<String>, // Changed from Set<Int>
    iconSize: Dp,
    iconShape: Shape,
    onItemSelect: (String, String) -> Unit // Changed to (ListName, PackageName)
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
            modifier = Modifier.fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(15.dp)
        ) {
            // FIX: Using items with key selector and direct item access
            items(
                items = apps,
                key = { it.packageName }
            ) { app ->
                if (canReOrder) {
                    ReorderableItem(
                        reorderableLazyListState,
                        key = app.packageName
                    ) { isDragging ->
                        val elevation by animateDpAsState(
                            if (isDragging) 4.dp else 0.dp,
                            label = ""
                        )
                        val scale by animateFloatAsState(if (isDragging) 1.2f else 1f, label = "scale")
                        val isSelected = selectedList == listName && selectedPackageNames.contains(app.packageName)

                        Surface(
                            shape = iconShape,
                            modifier = Modifier
                                .graphicsLayer {
                                    scaleX = scale
                                    scaleY = scale
                                }
                                .longPressDraggableHandle()
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) { onItemSelect(listName, app.packageName) },
                            shadowElevation = elevation,
                        ) {
                            AppCard(
                                app = app,
                                isSelected = isSelected,
                                iconSize = iconSize,
                                iconShape = iconShape
                            )
                        }
                    }
                } else {
                    // Normal list with selectable items
                    val isSelected = selectedList == listName && selectedPackageNames.contains(app.packageName)
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
                            ) { onItemSelect(listName, app.packageName) },
                        shadowElevation = elevation,
                    ) {
                        AppCard(
                            app = app,
                            isSelected = isSelected,
                            iconSize = iconSize,
                            iconShape = iconShape
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AppCard(app: AppInfo, isSelected: Boolean,iconSize: Dp,iconShape: Shape) {
    val themedModifier = if(isSelected){
        Modifier
            .clip(iconShape)
            .border(width = 2.dp,Color.Cyan,iconShape)
    } else {
        Modifier.clip(iconShape)
    }
    Box(
        modifier = themedModifier,
        contentAlignment = Alignment.Center
    ) {
        AppIcon(app,emptyList(),iconShape,iconSize=iconSize, clickable = false)
    }
}
