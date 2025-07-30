package com.jeerovan.comfer

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
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
            item { HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                thickness = DividerDefaults.Thickness,
                color = DividerDefaults.color
            ) }
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
            item { Divider(modifier = Modifier.padding(horizontal = 16.dp)) }
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

            item { SectionHeader("About") }
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
            .padding(horizontal = 16.dp, vertical = 8.dp,)
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