package com.jeerovan.comfer

import FlowerShape
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.compose.ui.graphics.Shape
import android.net.Uri
import android.os.Bundle
import android.util.Log
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
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
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import com.jeerovan.comfer.ui.theme.ComferTheme
import com.jeerovan.comfer.utils.CommonUtil.isDefaultLauncher
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import com.jeerovan.comfer.utils.CommonUtil.canSetLockScreenWallpaper
import com.jeerovan.comfer.utils.CommonUtil.copyUriToInternalStorage
import com.jeerovan.comfer.utils.CommonUtil.getShapeFromShape
import com.jeerovan.comfer.utils.CommonUtil.getShapeFromString
import kotlinx.coroutines.launch
import kotlinx.io.IOException
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.time.ExperimentalTime

@Composable
fun QuickAppsLayoutSettingItem(
    selectedLayout: String?,
    onLayoutSelected: (String) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }

    ListItem(
        headlineContent = { Text("Quick apps layout") },
        supportingContent = { Text("Select circular or linear") },
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
                        modifier = Modifier.size(44.dp)
                    )
                }
                "circular" -> {
                    Icon(
                        painter = painterResource(R.drawable.layout_circular),
                        contentDescription = "Widgets",
                        modifier = Modifier.size(44.dp)
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

    val supportingText = when {
        isWidgetsSelected -> "Widgets screen"
        selectedApp != null -> selectedApp.label.toString()
        else -> "Select an app or Widgets screen"
    }

    ListItem(
        headlineContent = { Text(headline) },
        supportingContent = { Text("Select an app or Widgets screen") },
        leadingContent = { icon() },
        trailingContent = {
            when {
                // If widgets are selected, show a widgets icon
                isWidgetsSelected -> {
                    Icon(
                        painter = painterResource(R.drawable.outline_widgets_24),
                        contentDescription = "Widgets",
                        modifier = Modifier.size(iconSize)
                    )
                }
                // If an app is selected, show its icon
                selectedApp != null -> {
                    Box(
                        modifier = Modifier
                            .size(iconSize)
                            .clip(iconShape),
                        contentAlignment = Alignment.Center
                    ) {
                        // Your existing logic to display the app icon
                        if (selectedApp.background != null) {
                            Image(
                                painter = rememberDrawablePainter(drawable = selectedApp.background),
                                contentDescription = "${selectedApp.label} background",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.FillBounds
                            )
                        }
                        if (selectedApp.foreground != null) {
                            Image(
                                painter = rememberDrawablePainter(drawable = selectedApp.foreground),
                                contentDescription = selectedApp.label.toString(),
                                modifier = Modifier.fillMaxSize().scale(selectedApp.scale),
                                contentScale = ContentScale.FillBounds
                            )
                        }
                    }
                }
            }
        },
        modifier = Modifier.clickable { showDialog = true },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("Choose Action") },
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

class SettingsActivity : ComponentActivity() {
    private val settingsViewModel: SettingsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ComferTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
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

    val launcher = rememberLauncherForActivityResult(
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

    val packageManager = context.packageManager

    val leftSwipeApp = mapPackageNameToAppInfo(packageManager, settingsState.leftSwipeApp)
    val rightSwipeApp = mapPackageNameToAppInfo(packageManager, settingsState.rightSwipeApp)

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
    Box( modifier = Modifier.fillMaxSize()
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
        ) {
            item { SectionHeader("Background") }
            item {
                ListItem(
                    headlineContent = { Text("Wallpaper Motion") },
                    supportingContent = { Text("Make your screen alive") },
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
            if(isDefaultLauncher(context) && canSetLockScreenWallpaper()){
                item {
                    ListItem(
                        headlineContent = { Text("Lock Screen Wallpaper") },
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
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(settingsState.iconSize.dp / 2) // Adjust preview size
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
                        launcher.launch(intent)
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
                        launcher.launch(intent)
                    },
                    onWidgetsSelectionClick = {
                        settingsViewModel.setWidgetsOnSwipe("right")
                    },
                    iconShape = getShapeFromShape(iconShape, iconSize.dp),
                    iconSize = iconSize.dp
                )
            }
            item { SectionHeader("App") }
            item {
                ListItem(
                    headlineContent = { Text("Manage App Lists") },
                    supportingContent = { Text("Organize your apps into custom lists") },
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
                    headlineContent = { Text("Navigation guide") },
                    supportingContent = { Text("Gestures on multiple screens") },
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
            /*item {
                ListItem(
                    headlineContent = { Text("Date-Time color") },
                    supportingContent = { Text("Set to white") },
                    leadingContent = {
                        Icon(
                            painter = painterResource(R.drawable.outline_motion_mode_24),
                            contentDescription = "Wallpaper Motion"
                        )
                    },
                    trailingContent = {
                        Switch(
                            checked = settingsState.dateTimeColor == "White",
                            onCheckedChange = { settingsViewModel.changeDateTimeColor(it) }
                        )
                    },
                    modifier = Modifier.clickable { settingsViewModel.changeDateTimeColor(settingsState.dateTimeColor != "White") },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
            }
            item{
                ImagePickerSettingItem(title = "Set wallpaper")
            }*/
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
        supportingContent = { Text("Customize the icon shape") },
        leadingContent = {
            Icon(
                painter = painterResource(R.drawable.outline_blur_circular_24),
                contentDescription = "Icon Appearance"
            )
        },
        trailingContent = {
            ShapePreview(shape = currentShape)
        }
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
                            ShapePreview(
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
                            ShapePreview(
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
fun ShapePreview(
    shape: Shape,
    size: Dp = 30.dp,
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
@OptIn(ExperimentalTime::class)
@Composable
fun ImagePickerSettingItem(
    title: String,
    modifier: Modifier = Modifier
) {
    // State to hold the FILE PATH of the selected image in internal storage.
    var savedImagePath by remember { mutableStateOf<String?>(null) }
    val isChecked = savedImagePath != null

    val context = LocalContext.current

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri: Uri? ->
            if (uri != null) {
                // --- THIS IS THE NEW LOGIC ---
                try {
                    // 1. Define the destination file
                    val currentUtcSeconds = TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis())
                    val filename = "comfer_${currentUtcSeconds}.jpg"
                    val destinationFile = File(context.filesDir, filename)

                    // 2. Copy the file
                    copyUriToInternalStorage(context, uri, destinationFile)

                    // 3. Save the path to the new file in our state
                    savedImagePath = destinationFile.absolutePath
                    Log.d("ImagePicker", "Successfully copied image to ${destinationFile.absolutePath}")
                    PreferenceManager.setBackgroundImagePath(
                        context,
                        destinationFile.absolutePath
                    )
                } catch (e: IOException) {
                    Log.e("ImagePicker", "Failed to copy image", e)
                    // If copy fails, ensure the switch remains off
                    savedImagePath = null
                }
            } else {
                // User canceled the selection, do nothing or ensure state is null
                savedImagePath = null
            }
        }
    )

    val toggleAction = {
        if (!isChecked) {
            galleryLauncher.launch("image/*")
        } else {
            // If the switch is on, delete the file and clear the state
            savedImagePath?.let { path ->
                val fileToDelete = File(path)
                if (fileToDelete.exists()) {
                    fileToDelete.delete()
                }
                savedImagePath = null
            }
        }
    }

    // 3. The ListItem composable for the UI.
    ListItem(
        modifier = modifier.clickable { toggleAction() },
        headlineContent = { Text(title) },
        supportingContent = { },
        leadingContent = {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "$title Icon"
            )
        },
        trailingContent = {
            Switch(
                checked = isChecked,
                onCheckedChange = { toggleAction() }
            )
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
