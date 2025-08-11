package com.jeerovan.comfer

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Divider
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import com.jeerovan.comfer.ui.theme.ComferTheme
import com.jeerovan.comfer.utils.CommonUtil.isDefaultLauncher
import androidx.core.net.toUri

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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    titleContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            item { SectionHeader("Appearance") }
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
            if(isDefaultLauncher(context)){
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
                ListItem(
                    headlineContent = { Text("Enhanced Icons") },
                    supportingContent = { Text("Better appearance on older devices") },
                    leadingContent = {
                        Icon(
                            painter = painterResource(R.drawable.outline_blur_circular_24),
                            contentDescription = "Icon Appearance"
                        )
                    },
                    trailingContent = {
                        Switch(
                            checked = settingsState.enhancedIcons,
                            onCheckedChange = { settingsViewModel.setEnhancedIcons(it) }
                        )
                    },
                    modifier = Modifier.clickable { settingsViewModel.setEnhancedIcons(!settingsState.enhancedIcons) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
            }

            item { SectionHeader("Gestures") }
            item {
                val intent = Intent(context, AppSelectionActivity::class.java).apply {
                    putExtra("swipe_direction", "left")
                }
                ListItem(
                    headlineContent = { Text("Left Swipe Action") },
                    supportingContent = { Text(leftSwipeApp?.label?.toString() ?: "Not set") },
                    leadingContent = {
                        Icon(
                            Icons.AutoMirrored.Default.ArrowBack,
                            contentDescription = "Left Swipe"
                        )
                    },
                    trailingContent = {
                        if (leftSwipeApp != null) {
                            Image(
                                painter = rememberDrawablePainter(drawable = leftSwipeApp.icon),
                                contentDescription = leftSwipeApp.label.toString(),
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                            )
                        } else {
                            Icon(
                                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = "Select App"
                            )
                        }
                    },
                    modifier = Modifier.clickable { launcher.launch(intent) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
            }

            item {
                val intent = Intent(context, AppSelectionActivity::class.java).apply {
                    putExtra("swipe_direction", "right")
                }
                ListItem(
                    headlineContent = { Text("Right Swipe Action") },
                    supportingContent = { Text(rightSwipeApp?.label?.toString() ?: "Not set") },
                    leadingContent = {
                        Icon(
                            Icons.AutoMirrored.Default.ArrowForward,
                            contentDescription = "Right Swipe"
                        )
                    },
                    trailingContent = {
                        if (rightSwipeApp != null) {
                            Image(
                                painter = rememberDrawablePainter(drawable = rightSwipeApp.icon),
                                contentDescription = rightSwipeApp.label.toString(),
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                            )
                        } else {
                            Icon(
                                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = "Select App"
                            )
                        }
                    },
                    modifier = Modifier.clickable { launcher.launch(intent) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
            }
            item {
                ListItem(
                    headlineContent = { Text("Navigation/Gestures") },
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
            item { SectionHeader("Customization") }
            item {
                ListItem(
                    headlineContent = { Text("Manage App Lists") },
                    supportingContent = { Text("Organize your apps into custom lists") },
                    leadingContent = { Icon(Icons.Default.List, contentDescription = "Manage App Lists") },
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

            item { SectionHeader("App") }
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
                        } catch (e: Exception) {
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
            if(saveLogs)item {
                ListItem(
                    headlineContent = { Text("Logs") },
                    supportingContent = { Text("App logs") },
                    leadingContent = { Icon(Icons.Default.List, contentDescription = "App logs") },
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
            item {
                ListItem(
                    headlineContent = { Text("Version") },
                    supportingContent = { Text(getAppVersion(context) ?: "N/A") },
                    leadingContent = { Icon(Icons.Default.Info, contentDescription = "Version") },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
            }
        }
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
    } catch (e: PackageManager.NameNotFoundException) {
        "N/A"
    }
}