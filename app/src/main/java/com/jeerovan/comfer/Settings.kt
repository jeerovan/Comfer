package com.jeerovan.comfer

import FlowerShape
import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.ui.graphics.Shape
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Support
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import com.jeerovan.comfer.ui.theme.ComferTheme
import com.jeerovan.comfer.utils.CommonUtil.isDefaultLauncher
import androidx.core.net.toUri
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.jeerovan.comfer.utils.CommonUtil.canSetLockScreenWallpaper
import com.jeerovan.comfer.utils.CommonUtil.getShapeFromShape
import com.jeerovan.comfer.utils.CommonUtil.getShapeFromString
import com.jeerovan.comfer.utils.CommonUtil.getUriPath
import com.jeerovan.comfer.utils.CommonUtil.openUrl
import kotlinx.coroutines.launch

class SettingsActivity : ComponentActivity() {
    private val settingsViewModel: SettingsViewModel by viewModels()

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
                    modifier = Modifier
                        .fillMaxSize(),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)
                ) {
                    SettingsScreen(settingsViewModel)
                }
            }
        }
    }
    override fun onResume() {
        super.onResume()
        val logger = LoggerManager(applicationContext)
        logger.setLog("SettingsActivity","Resumed")
        lifecycleScope.launch {
            settingsViewModel.loadSettings()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(settingsViewModel: SettingsViewModel) {
    val settingsState by settingsViewModel.uiState.collectAsState()
    val context = LocalContext.current

    val appSelectionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.let {
                val direction = it.getStringExtra("swipe_direction")
                val packageName = it.getStringExtra("package_name")
                if (direction != null && packageName != null) {
                    settingsViewModel.setSwipeApp(direction, packageName)
                }
            }
        }
    }

    val leftSwipeApp = settingsState.leftSwipeApp
    val rightSwipeApp = settingsState.rightSwipeApp

    val isLeftSwipeWidgets = settingsState.isLeftSwipeWidgets
    val isRightSwipeWidgets = settingsState.isRightSwipeWidgets

    val iconShape = settingsState.iconShape
    val iconSize = settingsState.iconSize - 10
    val iconShapeString = settingsState.iconShapeString

    val quickAppsLayout = settingsState.quickAppsLayout
    var showDisclosure by remember { mutableStateOf(false) }

    fun checkShowDiscloseOrPermissionIntent(){
        if(settingsState.hasNotificationAccess){
            settingsViewModel.requestNotificationPermission(context)
        } else {
            showDisclosure = true
        }
    }
    Box( modifier = Modifier
        .fillMaxSize()
    ) {
        LazyColumn(
            modifier = Modifier,
            contentPadding = WindowInsets.navigationBars.asPaddingValues()
        ) {
            item {
                Spacer(Modifier.height(24.dp))
            }
            item { SectionHeader("Premium")}
            item {
                ListItem(
                    headlineContent = { Text("Pro Access") },
                    supportingContent = { Text("Subscription") },
                    leadingContent = {
                        Icon(painter = painterResource(R.drawable.outline_star_shine_24),
                            contentDescription = "Subscription") },
                    trailingContent = {
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = "View"
                        )
                    },
                    modifier = Modifier.clickable {
                        context.startActivity(Intent(context, SubscriptionActivity::class.java))
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
            }
            item { SectionHeader("Support")}
            item {
                ListItem(
                    headlineContent = { Text("How to...") },
                    supportingContent = { Text("Navigation guide") },
                    leadingContent = { Icon(painter = painterResource(R.drawable.outline_gesture_24),
                        contentDescription = "Manage App Lists") },
                    trailingContent = {
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = "Go"
                        )
                    },
                    modifier = Modifier.clickable {
                        context.startActivity(Intent(context, GuideActivity::class.java))
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
            }
            if(settingsState.hasPro)item {
                val context = LocalContext.current
                ListItem(
                    headlineContent = { Text("Report an issue") },
                    leadingContent = {
                        Icon(
                            Icons.Filled.Support,
                            contentDescription = "Report Link")
                    },
                    trailingContent = {
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = "Go"
                        )
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.clickable {
                        openUrl("https://t.me/comfer_launcher",context)
                    }
                )
            }
            item { SectionHeader("Background") }
            item {
                ListItem(
                    headlineContent = { Text("Wallpaper Motion") },
                    supportingContent = { Text("Make home screen alive") },
                    leadingContent = {
                        Icon(
                            painter = painterResource(R.drawable.outline_motion_mode_24),
                            contentDescription = "Wallpaper Motion"
                        )
                    },
                    trailingContent = {
                        Switch(
                            checked = settingsState.wallpaperMotionEnabled,
                            onCheckedChange = { settingsViewModel.setWallpaperMotion(it) }
                        )
                    },
                    modifier = Modifier.clickable { settingsViewModel.setWallpaperMotion(!settingsState.wallpaperMotionEnabled) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
            }
            item{
                SelectSetOwnWallpapersDirectory(
                    enabled = settingsState.hasPro,
                    onSelectDirectory = { directoryUri -> settingsViewModel.setWallpaperDirectory(directoryUri)},
                    selectedDirectory = settingsState.wallpaperDirectory)
            }
            if(settingsState.wallpaperDirectory != null){
                item {
                    SelectOptionsWithListItemSettingItem(
                        "Change wallpaper",
                        null,
                        {Icon(Icons.Filled.Refresh, contentDescription = "Change frequency")},
                        settingsState.wallpaperFrequency,
                        {option -> settingsViewModel.setWallpaperFrequency(option)},
                        arrayOf("Hourly","Daily")
                    )
                }
            }
            if(isDefaultLauncher(context) && canSetLockScreenWallpaper()){
                item {
                    ListItem(
                        headlineContent = { Text("Lock Screen") },
                        supportingContent = { Text("Set wallpaper on lock screen also") },
                        leadingContent = {
                            Icon(
                                painter = painterResource(R.drawable.outline_mobile_lock_portrait_24),
                                contentDescription = "Wallpaper on lock screen"
                            )
                        },
                        trailingContent = {
                            Switch(
                                checked = settingsState.wallpaperOnLockScreen,
                                onCheckedChange = { settingsViewModel.setWallpaperOnLockScreen(it) }
                            )
                        },
                        modifier = Modifier.clickable { settingsViewModel.setWallpaperOnLockScreen(!settingsState.wallpaperOnLockScreen) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                }
            }
            item { SectionHeader("Icons") }
            item {
                ListItem(
                    headlineContent = { Text("Icon Size") },
                    supportingContent = { Text("${settingsState.iconSize} dp") },
                    leadingContent = {
                        Icon(
                            painter = painterResource(R.drawable.outline_photo_size_select_small_24),
                            contentDescription = "Icon Size"
                        )
                    },
                    trailingContent = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { settingsViewModel.changeIconSize(increase = false) }) {
                                Icon(
                                    painter = painterResource(R.drawable.outline_remove_24),
                                    contentDescription = "Decrease icon size"
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(settingsState.iconSize.dp) // Adjust preview size
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.onSurface)
                                )
                            }
                            IconButton(onClick = { settingsViewModel.changeIconSize(increase = true) }) {
                                Icon(Icons.Default.Add, contentDescription = "Increase icon size")
                            }
                        }
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
            }
            item {
                IconShapeSettingItem(currentShape = iconShapeString,
                    onShapeChange = {
                        newShape ->
                        settingsViewModel.setIconShape(newShape)
                    }
                )
            }
            if ( Build.VERSION.SDK_INT >= Build.VERSION_CODES.S){
                item{
                    ListItem(
                        headlineContent = { Text("Themed Icons") },
                        supportingContent = { Text("Match icons with wallpaper") },
                        leadingContent = {
                            Icon(
                                Icons.Filled.ColorLens,
                                contentDescription = "Themed Icons"
                            )
                        },
                        trailingContent = {
                            Switch(
                                checked = settingsState.showThemedIcons,
                                onCheckedChange = { settingsViewModel.setThemedIcons(it) }
                            )
                        },
                        modifier = Modifier.clickable { settingsViewModel.setThemedIcons(!settingsState.showThemedIcons) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                }
            }
            item {
                ListItem(
                    headlineContent = { Text("Notification + Badges") },
                    supportingContent = { Text("Requires notification permission") },
                    leadingContent = {
                        Icon(
                            painter = painterResource(R.drawable.outline_notifications_unread_24),
                            contentDescription = "Notification and badges"
                        )
                    },
                    trailingContent = {
                        Switch(
                            checked = settingsState.hasNotificationAccess,
                            onCheckedChange = { checkShowDiscloseOrPermissionIntent() }
                        )
                    },
                    modifier = Modifier.clickable { checkShowDiscloseOrPermissionIntent() },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
            }
            item { SectionHeader("Home Screen") }
            item {
                ListItem(
                    headlineContent = {
                        Row {
                            Text("Custom Widgets")
                            if(!settingsState.hasPro)Icon(Icons.Filled.Lock,
                                contentDescription = "Paid Feature",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .size(15.dp)
                                    .offset(x=10.dp,y=5.dp)
                            )
                        }
                    },
                    supportingContent = { Text("My favorite widgets") },
                    leadingContent = {
                        Icon(
                            painter = painterResource(R.drawable.outline_custom_widgets_24),
                            contentDescription = "Custom Widgets"
                        )
                    },
                    trailingContent = {
                        Switch(
                            enabled = settingsState.hasPro,
                            checked = settingsState.hasCustomWidgets,
                            onCheckedChange = { settingsViewModel.setCustomWidgets(it) }
                        )
                    },
                    modifier = Modifier.clickable {
                        if(settingsState.hasPro){
                            settingsViewModel.setCustomWidgets(!settingsState.hasCustomWidgets)
                        }
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
            }
            item {
                QuickAppsLayoutSettingItem(
                    selectedLayout = quickAppsLayout,
                    onLayoutSelected = { layout -> settingsViewModel.setQuickAppsLayout(layout)}
                )
            }
            item {
                val intent = Intent(context, AppSelectionActivity::class.java).apply {
                    putExtra("swipe_direction", "left")
                }
                SwipeActionSettingItem(
                    headline = "Left Swipe Action",
                    icon = {
                        Icon(
                            Icons.AutoMirrored.Default.ArrowBack,
                            contentDescription = "Left Swipe"
                        )
                    },
                    selectedApp = leftSwipeApp, // Your state variable
                    isWidgetsSelected = isLeftSwipeWidgets, // Your state variable
                    onAppSelectionClick = {
                        // This now launches the app selection activity
                        appSelectionLauncher.launch(intent)
                    },
                    onWidgetsSelectionClick = {
                        settingsViewModel.setWidgetsOnSwipe("left")
                    },
                    iconShape = getShapeFromShape(iconShape, iconSize.dp),
                    iconSize = iconSize.dp
                )
            }
            item {
                val intent = Intent(context, AppSelectionActivity::class.java).apply {
                    putExtra("swipe_direction", "right")
                }
                SwipeActionSettingItem(
                    headline = "Right Swipe Action",
                    icon = {
                        Icon(
                            Icons.AutoMirrored.Default.ArrowForward,
                            contentDescription = "Right Swipe"
                        )
                    },
                    selectedApp = rightSwipeApp, // Your state variable
                    isWidgetsSelected = isRightSwipeWidgets, // Your state variable
                    onAppSelectionClick = {
                        // This now launches the app selection activity
                        appSelectionLauncher.launch(intent)
                    },
                    onWidgetsSelectionClick = {
                        settingsViewModel.setWidgetsOnSwipe("right")
                    },
                    iconShape = getShapeFromShape(iconShape, iconSize.dp),
                    iconSize = iconSize.dp
                )
            }
            item {
                ListItem(
                    headlineContent = {
                        Row {
                            Text("Gestures")
                            if(!settingsState.hasPro)Icon(Icons.Filled.Lock,
                                contentDescription = "Paid Feature",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .size(15.dp)
                                    .offset(x=10.dp,y=5.dp)
                            )
                        }
                                      },
                    supportingContent = { Text("Magic app shortcuts") },
                    leadingContent = { Icon(painter = painterResource(R.drawable.outline_gesture_24),
                        contentDescription = "Manage App Lists") },
                    trailingContent = {
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = "Go"
                        )
                    },
                    modifier = Modifier.clickable {
                        context.startActivity(Intent(context, GestureShortcutActivity::class.java))
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
            }
            item { SectionHeader("App Lists") }
            item {
                ListItem(
                    headlineContent = { Text("Manage") },
                    supportingContent = { Text("Organize/Reorder apps in lists") },
                    leadingContent = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = "Manage App Lists") },
                    trailingContent = {
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = "Go"
                        )
                    },
                    modifier = Modifier.clickable {
                        context.startActivity(Intent(context, ManageAppListActivity::class.java))
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
            }
            item {
                ListItem(
                    headlineContent = { Text("Alphabetical order") },
                    supportingContent = { Text("Arrange apps in A-Z") },
                    leadingContent = {
                        Icon(
                            Icons.AutoMirrored.Filled.Sort,
                            contentDescription = "Sorted app list"
                        )
                    },
                    trailingContent = {
                        Switch(
                            checked = settingsState.arrangeInAlphabeticalOrder,
                            onCheckedChange = { settingsViewModel.setAlphabeticalOrder(it) }
                        )
                    },
                    modifier = Modifier.clickable { settingsViewModel.setCustomWidgets(!settingsState.arrangeInAlphabeticalOrder) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
            }
            item {
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 16.dp, horizontal = 24.dp),
                    thickness = DividerDefaults.Thickness,
                    color = DividerDefaults.color
                )
            }
            item {
                val context = LocalContext.current
                val packageName = context.packageName
                ListItem(
                    headlineContent = { Text("Feedback") },
                    leadingContent = { Icon(painter = painterResource(R.drawable.outline_star_rate_24), contentDescription = "Rate Icon") },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.clickable {
                        try {
                            // Try to open the Play Store app directly
                            val playStoreIntent = Intent(Intent.ACTION_VIEW,
                                "market://details?id=$packageName".toUri())
                            context.startActivity(playStoreIntent)
                        } catch (_: Exception) {
                            // If Play Store is not installed, open in a web browser
                            val webIntent = Intent(Intent.ACTION_VIEW,
                                "https://play.google.com/store/apps/details?id=$packageName".toUri())
                            context.startActivity(webIntent)
                        }
                    }
                )
            }
            item {
                val context = LocalContext.current
                val packageName = context.packageName
                ListItem(
                    headlineContent = { Text("Share App") },
                    leadingContent = { Icon(Icons.Default.Share, contentDescription = "Share Icon") },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.clickable {
                        // Create a share intent
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(
                                Intent.EXTRA_TEXT,
                                "Try the new interesting launcher: https://play.google.com/store/apps/details?id=$packageName"
                            )
                        }
                        // Use a chooser to show the Android share sheet
                        context.startActivity(Intent.createChooser(shareIntent, "Share via"))
                    }
                )
            }
            item {
                ListItem(
                    headlineContent = { Text("Version") },
                    supportingContent = { Text(getAppVersion(context) ?: "N/A") },
                    leadingContent = { Icon(Icons.Default.Info, contentDescription = "Version") },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
            }
            if(saveLogs)item {
                ListItem(
                    headlineContent = { Text("Logs") },
                    supportingContent = { Text("App logs") },
                    leadingContent = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = "App logs") },
                    trailingContent = {
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = "View"
                        )
                    },
                    modifier = Modifier.clickable {
                        context.startActivity(Intent(context, LogsActivity::class.java))
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
            }
            if(saveCrashes)item {
                ListItem(
                    headlineContent = { Text("Crash logs") },
                    supportingContent = { Text("View uncaught exceptions") },
                    leadingContent = {
                        Icon(
                            Icons.Default.Menu,
                            contentDescription = "View crash logs")
                    },
                    trailingContent = {
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = "Go"
                        )
                    },
                    modifier = Modifier.clickable {
                        context.startActivity(Intent(context, CrashViewActivity::class.java))
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
            }
        }
    }
    if (showDisclosure) {
        NotificationServicePermissionDisclosureScreen(
            onContinue = {
                // The user consented. Now we can send them to the settings.
                showDisclosure = false
                settingsViewModel.requestNotificationPermission(context)
            },
            onCancel = {
                // The user declined. Just hide the dialog.
                showDisclosure = false
            }
        )
    }
}
@Composable
fun IconShapeSettingItem(
    currentShape: String? = "circle",
    onShapeChange: (String) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }
    val currentShape:Shape = getShapeFromString(currentShape)

    ListItem(
        modifier = Modifier.clickable { showDialog = true },
        headlineContent = { Text("Icon Shape") },
        supportingContent = { Text("Set unique style") },
        leadingContent = {
            Icon(
                painter = painterResource(R.drawable.outline_blur_circular_24),
                contentDescription = "Icon Appearance"
            )
        },
        trailingContent = {
            IconShapePreview(shape = currentShape)
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )

    if (showDialog) {
        ShapeSelectionDialog(
            onDismissRequest = { showDialog = false },
            onShapeSelected = { shapeName ->
                onShapeChange(shapeName)
                showDialog = false
            }
        )
    }
}
@Composable
fun SectionHeader(title: String) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

fun getAppVersion(context: Context): String? {
    return try {
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        packageInfo.versionName
    } catch (_: PackageManager.NameNotFoundException) {
        "N/A"
    }
}
@Composable
fun ShapeSelectionDialog(
    onDismissRequest: () -> Unit,
    onShapeSelected: (String) -> Unit
) {
    val shapesRowOne = mapOf(
        "circle" to CircleShape,
        "cloud" to FlowerShape(angle = 45.0f),
        "squircle" to RoundedCornerShape(0.0f)
    )
    val shapesRowTwo = mapOf(
        "flower" to FlowerShape(petalCount = 7),
        "cutcorner" to CutCornerShape(0.dp)
    )

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text("Select Icon Shape") },
        text = {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    shapesRowOne.forEach { (name, shape) ->
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.clickable { onShapeSelected(name) }
                        ) {
                            IconShapePreview(
                                shape = shape,
                                size = 56.dp,
                                borderColor = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(name.replaceFirstChar { it.uppercase() })
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    shapesRowTwo.forEach { (name, shape) ->
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.clickable { onShapeSelected(name) }
                        ) {
                            IconShapePreview(
                                shape = shape,
                                size = 56.dp,
                                borderColor = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(name.replaceFirstChar { it.uppercase() })
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismissRequest) {
                Text("Cancel")
            }
        },
        properties = DialogProperties()
    )
}
@Composable
fun IconShapePreview(
    shape: Shape,
    size: Dp = 44.dp,
    borderColor: Color = MaterialTheme.colorScheme.outline
) {
    var iconShape = shape
    when (shape) {
        CircleShape -> {
            iconShape = shape
        }
        is RoundedCornerShape -> {
            val cornerRadius = size * 0.425f
            iconShape = RoundedCornerShape(cornerRadius)
        }

        is CutCornerShape -> {
            val cornerCut = size * 0.225f
            iconShape = CutCornerShape(cornerCut)
        }
    }
    Box(
        modifier = Modifier
            .size(size)
            .clip(iconShape)
            .border(width = 2.dp, color = borderColor, shape = iconShape)
    )
}
@Composable
fun SelectSetOwnWallpapersDirectory(
    enabled: Boolean,
    onSelectDirectory: (directory: String?) -> Unit,
    selectedDirectory: String?
) {
    val context = LocalContext.current

    val isChecked = selectedDirectory != null

    val directoryPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
        onResult = { uri ->
            // ROBUST CHECK: Ensure the URI is not null AND has a valid path.
            // This handles edge cases where a non-null but empty URI is returned on cancel.
            if (uri?.path?.isNotEmpty() == true) {
                // This block only runs for a valid directory selection.
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
                onSelectDirectory(uri.toString())
            }
        }
    )

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            if (isGranted) {
                directoryPickerLauncher.launch(null)
            }
        }
    )

    ListItem(
        headlineContent = {
            Row {
                Text("Set own wallpapers")
                if(!enabled)Icon(Icons.Filled.Lock,
                    contentDescription = "Paid Feature",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .size(15.dp)
                        .offset(x=10.dp,y=5.dp)
                )
            }
                          },
        // Use the .path property for a cleaner display string.
        supportingContent = { Text(getUriPath(selectedDirectory) ?: "Tap to select directory") },
        leadingContent = {
            Icon(
                painter = painterResource(R.drawable.outline_wallpaper_directory),
                contentDescription = "Wallpaper Directory"
            )
        },
        trailingContent = {
            Switch(
                enabled = enabled,
                checked = isChecked, // The UI is driven by the single source of truth.
                onCheckedChange = { checked ->
                    if (checked) {
                        // When user tries to turn the switch ON, launch the picker.
                        if (ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.READ_EXTERNAL_STORAGE
                            ) == PackageManager.PERMISSION_GRANTED
                        ) {
                            directoryPickerLauncher.launch(null)
                        } else {
                            permissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
                        }
                    } else {
                        // When user turns the switch OFF, clear the directory.
                        onSelectDirectory(null)
                    }
                }
            )
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable {
            if (enabled && isChecked) {
                directoryPickerLauncher.launch(null)
            }
        }
    )
}


@Composable
fun NotificationServicePermissionDisclosureScreen(
    onContinue: () -> Unit,
    onCancel: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Permission Required",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "To show notification icons on Home screen and badges on app icons, this launcher requires notification service access.",
                textAlign = TextAlign.Center,
                fontSize = 16.sp
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "What this service does:",
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Text(
                text = "• Access notification apps from service and show icons and badges as required.",
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp)
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "This service does NOT:",
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Text(
                text = "• Collect any personal data.",
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp)
            )
            Spacer(modifier = Modifier.height(32.dp))
            Row {
                Button(onClick = onContinue) {
                    Text("Continue")
                }
                Spacer(modifier = Modifier.width(16.dp))
                OutlinedButton(onClick = onCancel) {
                    Text("Cancel")
                }
            }
        }
    }
}
@Composable
fun QuickAppsLayoutSettingItem(
    selectedLayout: String?,
    onLayoutSelected: (String) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }

    ListItem(
        headlineContent = { Text("Quick apps layout") },
        supportingContent = { Text("Arrange apps with style") },
        leadingContent = { Icon(
            painter = painterResource(R.drawable.outline_apps_24),
            contentDescription = "Home apps layout")
        },
        trailingContent = {
            when (selectedLayout) {
                "linear" -> {
                    Icon(
                        painter = painterResource(R.drawable.layout_linear),
                        contentDescription = "Widgets",
                        modifier = Modifier.size(48.dp)
                    )
                }
                "circular" -> {
                    Icon(
                        painter = painterResource(R.drawable.layout_circular),
                        contentDescription = "Widgets",
                        modifier = Modifier.size(48.dp)
                    )
                }
            }
        },
        modifier = Modifier.clickable { showDialog = true },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("Choose Layout") },
            text = {
                Column (modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        painter = painterResource(R.drawable.layout_linear),
                        contentDescription = "Widgets",
                        modifier = Modifier
                            .size(76.dp)
                            .clickable {
                                showDialog = false
                                onLayoutSelected("linear")
                            }
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Icon(
                        painter = painterResource(R.drawable.layout_circular),
                        contentDescription = "Widgets",
                        modifier = Modifier
                            .size(56.dp)
                            .clickable {
                                showDialog = false
                                onLayoutSelected("circular")
                            }
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
@Composable
fun SwipeActionSettingItem(
    headline: String,
    icon: @Composable () -> Unit,
    selectedApp: AppInfo?,
    isWidgetsSelected: Boolean,
    onAppSelectionClick: () -> Unit,
    onWidgetsSelectionClick: () -> Unit,
    iconShape: Shape,
    iconSize: Dp
) {
    var showDialog by remember { mutableStateOf(false) }

    ListItem(
        headlineContent = { Text(headline) },
        supportingContent = { Text("An app or Widgets screen") },
        leadingContent = { icon() },
        trailingContent = {
            when {
                // If widgets are selected, show a widgets icon
                isWidgetsSelected -> {
                    Icon(
                        painter = painterResource(R.drawable.outline_widgets_24),
                        contentDescription = "Widgets",
                        modifier = Modifier.size(40.dp)
                    )
                }
                // If an app is selected, show its icon
                selectedApp != null -> {
                    AppIcon(selectedApp,emptyList(),shape=iconShape,iconSize=40.dp, clickable = false)
                }
            }
        },
        modifier = Modifier.clickable { showDialog = true },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("Choose Option") },
            text = {
                Column {
                    TextButton(
                        onClick = {
                            showDialog = false
                            onAppSelectionClick()
                        }
                    ) {
                        Text("Select App")
                    }
                    TextButton(
                        onClick = {
                            showDialog = false
                            onWidgetsSelectionClick()
                        }
                    ) {
                        Text("Widgets Screen")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun SelectOptionsWithListItemSettingItem(
    headline: String,
    supportingLine: String?,
    icon: @Composable (() -> Unit)?,
    selectedOption: String,
    onSelectionClick: (String) -> Unit,
    options: Array<String>
) {
    var showDialog by remember { mutableStateOf(false) }

    ListItem(
        headlineContent = { Text(headline) },
        supportingContent = { if(supportingLine != null)Text(supportingLine) },
        leadingContent = { if(icon != null)icon() },
        trailingContent = {
            Text(selectedOption,style = MaterialTheme.typography.bodyLarge )
        },
        modifier = Modifier.fillMaxWidth().clickable { showDialog = true },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("Choose Action") },
            text = {
                LazyColumn {
                    items(options) {
                        option ->
                            TextButton(
                            onClick = {
                                showDialog = false
                                onSelectionClick(option)
                            }
                        ) {
                            Text(option)
                        }
                    }

                }
            },
            confirmButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}