package com.jeerovan.comfer

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material.icons.rounded.Add
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
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

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.ui.text.style.TextAlign


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

    val iconSize = PreferenceManager.getIconSize(context)
    val shape = PreferenceManager.getIconShape(context)
    val alphabeticalOrder = PreferenceManager.getAlphabeticalOrder(context)
    val iconShape = getShapeFromShape(shape, iconSize.dp)

    var showFolderDialog by rememberSaveable { mutableStateOf(false) }
    var isEditingFolder by rememberSaveable { mutableStateOf(false) }
    var folderTitle by rememberSaveable { mutableStateOf("") }
    var folderSelected by rememberSaveable { mutableStateOf<String?>(null) }

    val listStates = remember {
        mapOf(
            AppInfoManager.QUICK_APPS_LIST_NAME to quickListState,
            AppInfoManager.PRIMARY_APPS_LIST_NAME to primaryListState,
            AppInfoManager.REST_APPS_LIST_NAME to restListState
        )
    }
    // Stable sorted lists
    val primaryApps = remember(uiState.primaryApps, alphabeticalOrder) {
        if (alphabeticalOrder) uiState.primaryApps.sortedBy { it.label } else uiState.primaryApps
    }

    val foldersCount = remember(uiState.folderApps) { uiState.folderApps.size }

    // Changed to track Package Names instead of Indices for stability
    var selectedList by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedPackageNames by rememberSaveable { mutableStateOf(emptySet<String>()) }

    val onItemSelect = { listName: String, packageName: String ->
        if (!listName.startsWith("folder") && selectedList != listName) {
            selectedList = listName
            selectedPackageNames = setOf(packageName)
            folderSelected = null
        } else {
            selectedPackageNames = if (selectedPackageNames.contains(packageName)) {
                selectedPackageNames - packageName
            } else {
                selectedPackageNames + packageName
            }
        }
        if(packageName.startsWith("folder")){
            folderSelected = if (folderSelected == packageName){
                null
            } else {
                packageName
            }
            if(folderSelected != null){
                val folder:FolderData? = uiState.folders[folderSelected]
                if(folder != null){
                    folderTitle = folder.title
                }
            } else {
                folderTitle = ""
            }
        }
    }

    val clearSelection = {
        selectedList = null
        selectedPackageNames = emptySet()
        folderSelected = null
    }

    val clearFolderSelection = {
        selectedPackageNames = setOf(folderSelected!!)
    }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()


    if (showFolderDialog) {
        AlertDialog(
            onDismissRequest = {
                showFolderDialog = false
                isEditingFolder = false
            },
            text = {
                OutlinedTextField(
                    value = folderTitle,
                    onValueChange = { if (it.length <= 20) folderTitle = it },
                    label = { Text("Folder Name (Max 20 chars)") },
                    singleLine = true
                )
            },
            confirmButton = {
                OutlinedButton(
                    onClick = {
                        if (folderTitle.isNotBlank()) {
                            if (isEditingFolder && folderSelected != null) {
                                viewModel.renameFolder(selectedList,folderSelected!!, folderTitle)
                            } else {
                                viewModel.createNewFolder(folderTitle)
                            }
                            showFolderDialog = false
                            isEditingFolder = false
                        }
                    }
                ) { Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = stringResource(R.string.continue_text),
                ) }
            },
            dismissButton = {
                OutlinedButton(onClick = {
                    showFolderDialog = false
                    isEditingFolder = false
                }) { Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(R.string.close),
                ) }
            }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 24.dp)
            .windowInsetsPadding(WindowInsets.navigationBars)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .weight(1f) // Gives this row priority for space
                    .fillMaxWidth()
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
                    iconSize = iconSize.dp,
                    onAddFolderClick = {},
                    folders = foldersCount
                )
                Column(
                    modifier = Modifier.fillMaxHeight(),
                    verticalArrangement = Arrangement.Center
                ) {
                    Row {
                        OutlinedButton(
                            shape = CircleShape,
                            modifier = Modifier.size(40.dp),
                            contentPadding = PaddingValues(0.dp),
                            onClick = {
                                val packagesToMove = selectedPackageNames
                                scope.launch {
                                    when (viewModel.performMoveAppsToList(
                                        AppInfoManager.PRIMARY_APPS_LIST_NAME,
                                        AppInfoManager.QUICK_APPS_LIST_NAME,
                                        packagesToMove,
                                    )) {
                                        AppMoveResult.QUICK_FULL -> snackbarHostState.showSnackbar(
                                            "Maximum $MAX_QUICK_APPS apps only",
                                        )
                                        AppMoveResult.MOVED -> {
                                            if (selectedList == AppInfoManager.PRIMARY_APPS_LIST_NAME &&
                                                selectedPackageNames == packagesToMove) clearSelection()
                                        }
                                        AppMoveResult.REJECTED -> Unit
                                    }
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
                    iconSize = iconSize.dp,
                    onAddFolderClick = {
                        folderTitle = ""
                        isEditingFolder = false
                        showFolderDialog = true
                    },
                    folders = foldersCount
                )
                Column(
                    modifier = Modifier.fillMaxHeight(),
                    verticalArrangement = Arrangement.Center
                ) {
                    Row {
                        OutlinedButton(
                            shape = CircleShape,
                            modifier = Modifier.size(40.dp),
                            contentPadding = PaddingValues(0.dp),
                            onClick = {
                                viewModel.moveAppsToList(
                                    AppInfoManager.REST_APPS_LIST_NAME,
                                    AppInfoManager.PRIMARY_APPS_LIST_NAME,
                                    selectedPackageNames
                                )
                                clearSelection()
                            },
                            enabled = selectedList == AppInfoManager.REST_APPS_LIST_NAME && selectedPackageNames.isNotEmpty()
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
                                    AppInfoManager.REST_APPS_LIST_NAME,
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
                    listState = listStates[AppInfoManager.REST_APPS_LIST_NAME]!!,
                    modifier = Modifier.weight(1f),
                    listName = AppInfoManager.REST_APPS_LIST_NAME,
                    viewModel = viewModel,
                    selectedList = selectedList,
                    selectedPackageNames = selectedPackageNames,
                    onItemSelect = onItemSelect,
                    iconShape = iconShape,
                    iconSize = iconSize.dp,
                    onAddFolderClick = {},
                    folders = foldersCount
                )
            }
            if (folderSelected != null) {
                // Folder Contents Horizontal View
                val folderAppInfos = uiState.folderApps[folderSelected] ?: emptyList()
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Action Buttons (Arrow Down, Arrow Up)
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        OutlinedButton(
                            shape = CircleShape,
                            modifier = Modifier.size(40.dp),
                            contentPadding = PaddingValues(0.dp),
                            enabled = selectedPackageNames.isNotEmpty(),
                            onClick = {
                                viewModel.moveAppsToFolder(selectedList, folderSelected!!, selectedPackageNames)
                                clearFolderSelection()
                            }
                        ) {
                            Icon(imageVector = Icons.Rounded.ArrowDownward, contentDescription = "Move to Folder")
                        }

                        OutlinedButton(
                            shape = CircleShape,
                            modifier = Modifier.size(40.dp),
                            contentPadding = PaddingValues(0.dp),
                            enabled = selectedPackageNames.isNotEmpty(),
                            onClick = {
                                viewModel.moveAppsFromFolder(selectedList, folderSelected!!, selectedPackageNames)
                                clearFolderSelection()
                            }
                        ) {
                            Icon(imageVector = Icons.Rounded.ArrowUpward, contentDescription = "Move to Primary")
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Spacer(modifier = Modifier.size(40.dp))

                        Text(
                            text = folderTitle,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    isEditingFolder = true
                                    showFolderDialog = true
                                }
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            textAlign = TextAlign.Center
                        )

                        if (folderAppInfos.isEmpty()) {
                            OutlinedButton(
                                shape = CircleShape,
                                modifier = Modifier.size(40.dp),
                                contentPadding = PaddingValues(0.dp),
                                onClick = {
                                    viewModel.deleteFolder(selectedList, folderSelected!!)
                                    clearSelection()
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Delete,
                                    contentDescription = "Delete Folder"
                                )
                            }
                        } else {
                            Spacer(modifier = Modifier.size(40.dp))
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    FolderAppListRow(
                        folderAppInfos,
                        folderSelected!!,
                        viewModel,
                        "Folder",
                        selectedPackageNames,
                        iconSize.dp,
                        iconShape,
                        onItemSelect
                    )
                }
            }
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }
    }
}

@Composable
fun FolderAppListRow(
    apps: List<AppInfo>,
    folderName: String,
    viewModel: AppInfoViewModel,
    selectedList: String?,
    selectedPackageNames: Set<String>, // Changed from Set<Int>
    iconSize: Dp,
    iconShape: Shape,
    onItemSelect: (String, String) -> Unit,
) {
    val hapticFeedback = LocalHapticFeedback.current
    val listState = rememberLazyListState()
    RevealAddedApps(folderName, apps, listState)
    val reorderableLazyListState = rememberReorderableLazyListState(listState) { from, to ->
        viewModel.moveAppsInFolder(folderName, from.key as String, to.key as String)
        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
    }
    LazyRow(
        state = listState,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .border(
                1.dp,
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f),
                RoundedCornerShape(12.dp)
            ),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(
            items = apps,
            key = { it.packageName }
        ) { app ->
            ReorderableItem(
                reorderableLazyListState,
                key = app.packageName
            ) { isDragging ->
                val elevation by animateDpAsState(
                    if (isDragging) 4.dp else 0.dp,
                    label = ""
                )
                val scale by animateFloatAsState(if (isDragging) 1.2f else 1f, label = "scale")
                val isSelected = selectedPackageNames.contains(app.packageName)

                Surface(
                    shape = iconShape,
                    color = Color.Transparent,
                    modifier = Modifier
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                        }
                        .longPressDraggableHandle()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onItemSelect(folderName, app.packageName) },
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
    onItemSelect: (String, String) -> Unit, // Changed to (ListName, PackageName)
    onAddFolderClick: () -> Unit,
    folders: Int
) {
    RevealAddedApps(listName, apps, listState)
    val hapticFeedback = LocalHapticFeedback.current
    val reorderableLazyListState = rememberReorderableLazyListState(listState) { from, to ->
        viewModel.moveAppInList(listName, from.key as String, to.key as String)
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
            modifier = Modifier.padding(15.dp)
        )
        if(listName == AppInfoManager.PRIMARY_APPS_LIST_NAME && folders < 10)AddFolderIcon(viewModel = viewModel,onAddFolderClick = onAddFolderClick)
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(15.dp)
        ) {
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
                            color = Color.Transparent,
                            modifier = Modifier
                                .testTag("manage-app:$listName:${app.packageName}")
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
                        color = Color.Transparent,
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
private fun RevealAddedApps(listName: String, apps: List<AppInfo>, listState: LazyListState) {
    val packageNames = apps.map { it.packageName }
    var previousPackages by remember(listName) { mutableStateOf(packageNames.toSet()) }
    LaunchedEffect(listName, packageNames) {
        val firstAddedIndex = packageNames.indexOfFirst { it !in previousPackages }
        previousPackages = packageNames.toSet()
        if (firstAddedIndex >= 0) {
            // Stable item keys otherwise keep the old visible item anchored,
            // hiding additions before it. Use the displayed order for sorted lists too.
            listState.animateScrollToItem(firstAddedIndex)
        }
    }
}

@Composable
fun AppCard(
    app: AppInfo,
    isSelected: Boolean,
    iconSize: Dp,
    iconShape: Shape
) {
    val borderWidth = 3.dp
    val gapWidth = 2.dp

    val borderColor: Color = if(app.packageName.startsWith("folder_"))  {
        Color.Gray
    } else { MaterialTheme.colorScheme.primary}

    val themedModifier = if(isSelected) {
        Modifier
            // Draw the outer border
            .border(width = borderWidth, color = borderColor, shape = iconShape)
            // Add padding to create the gap, and also account for the border thickness itself
            .padding(borderWidth + gapWidth)
            .clip(iconShape)
    } else {
        Modifier
            // Apply the exact same structural size changes so the layout does not jump
            .padding(borderWidth + gapWidth)
            .clip(iconShape)
    }

    Box(
        modifier = themedModifier,
        contentAlignment = Alignment.Center
    ) {
        AppIcon(app, emptyList(), iconShape, iconSize = iconSize, clickable = false)
    }
}

@Composable
fun AddFolderIcon(viewModel: AppInfoViewModel, onAddFolderClick: () -> Unit){
    val infoViewState by viewModel.uiState.collectAsState()
    val settings = infoViewState.settings
    val iconSize = settings["iconSize"] as? Int ?: 48
    val shape = settings["shape"] as? Shape ?: CircleShape
    val iconShape = getShapeFromShape(shape, iconSize.dp)
    val showThemedIcons = settings["showThemedIcons"] as? Boolean ?: false
    val themedColors = settings["themedColors"] as? WallpaperThemeColors ?: WallpaperThemeColors(
        Color.White.copy(alpha = 0.7f).toArgb(),
        Color.Black.toArgb(),
        Color.Black.copy(alpha = 0.7f).toArgb(),
        Color.White.toArgb(),
        Color.White.toArgb(),
        Color.Black.toArgb()
    )
    val isLightHour = settings["isLightHour"] as? Boolean ?: true

    val foregroundColorInt = if (showThemedIcons) {
        getThemedIconColor(themedColors, isLightHour)
    } else {
        MaterialTheme.colorScheme.onPrimaryContainer.toArgb()
    }

    val backgroundColorInt = if (showThemedIcons) {
        getThemedBackgroundColor(themedColors, isLightHour)
    } else {
        MaterialTheme.colorScheme.primaryContainer.toArgb()
    }

    Box(
        modifier = Modifier
            .padding(bottom = 18.dp, top = 6.dp)
            .size(iconSize.dp)
            .clip(iconShape)
            .background(Color(backgroundColorInt))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {onAddFolderClick()},
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Rounded.Add,
            contentDescription = "Add Folder",
            modifier = Modifier
                .fillMaxSize()
                .padding(6.dp), // Padding prevents the icon from touching the bounds of the shape
            tint = Color(foregroundColorInt)
        )
    }
}
