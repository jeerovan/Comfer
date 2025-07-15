package com.jeerovan.comfer

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import com.jeerovan.comfer.ui.theme.ComferTheme

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

@Composable
fun SettingsScreen(settingsViewModel: SettingsViewModel) {
    val settingsState by settingsViewModel.uiState.collectAsState()
    val context = LocalContext.current

    val packageManager = context.packageManager

    val leftSwipeApp = mapPackageNameToAppInfo(packageManager,settingsState.leftSwipeApp)
    val rightSwipeApp = mapPackageNameToAppInfo(packageManager,settingsState.rightSwipeApp)

    Scaffold(
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            item {
                SettingRow(
                    title = "Wallpaper motion",
                    onClick = { settingsViewModel.setWallpaperMotion(!settingsState.wallpaperMotionEnabled) }
                ) {
                    Switch(
                        checked = settingsState.wallpaperMotionEnabled,
                        onCheckedChange = { settingsViewModel.setWallpaperMotion(it) }
                    )
                }
            }
            item {
                SettingRow(title = "Icon size") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { settingsViewModel.changeIconSize(increase = false) }) {
                            Icon(painter = painterResource(R.drawable.outline_remove_24), contentDescription = "Decrease icon size")
                        }
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .border(2.dp, Color.Gray, CircleShape)
                                .size(settingsState.iconSize.dp)
                        )
                        IconButton(onClick = { settingsViewModel.changeIconSize(increase = true) }) {
                            Icon(Icons.Filled.Add, contentDescription = "Increase icon size")
                        }
                    }
                }
            }
            item {
                SettingRow(title = "Left swipe", onClick = {
                    val intent = Intent(context, AppSelectionActivity::class.java)
                    intent.putExtra("swipe_direction", "left")
                    context.startActivity(intent)
                }) {
                    if(leftSwipeApp == null){
                        Text("Select")
                    } else {
                        Image(
                            painter = rememberDrawablePainter(drawable = leftSwipeApp.icon),
                            contentDescription = leftSwipeApp.label.toString(),
                            modifier = Modifier.padding(4.dp).size(45.dp)
                        )
                    }
                }
            }
            item {
                SettingRow(title = "Right swipe", onClick = {
                    val intent = Intent(context, AppSelectionActivity::class.java)
                    intent.putExtra("swipe_direction", "right")
                    context.startActivity(intent)
                }) {
                    if(rightSwipeApp == null){
                        Text("Select")
                    } else {
                        Image(
                            painter = rememberDrawablePainter(drawable = rightSwipeApp.icon),
                            contentDescription = rightSwipeApp.label.toString(),
                            modifier = Modifier.padding(4.dp).size(45.dp)
                        )
                    }
                }
            }
            item {
                SettingRow(
                    title = "Manage app lists",
                    onClick = {
                        context.startActivity(Intent(context, ManageAppListActivity::class.java))
                    }
                ) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Manage app list")
                }
            }
            /*item {
                SettingRow(
                    title = "Feedback",
                    onClick = {
                        val intent = Intent(
                            Intent.ACTION_VIEW,
                            "market://details?id=${context.packageName}".toUri()
                        )
                        context.startActivity(intent)
                    }
                )
            }
            item {
                SettingRow(
                    title = "Share",
                    onClick = {
                        val sendIntent: Intent = Intent().apply {
                            action = Intent.ACTION_SEND
                            putExtra(
                                Intent.EXTRA_TEXT,
                                "Check out this awesome launcher: https://play.google.com/store/apps/details?id=${context.packageName}"
                            )
                            type = "text/plain"
                        }
                        val shareIntent = Intent.createChooser(sendIntent, null)
                        context.startActivity(shareIntent)
                    }
                )
            }*/
            item {
                Spacer(modifier = Modifier.height(24.dp))
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "Version: ${getAppVersion(context)}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

@Composable
fun SettingRow(title: String, onClick: (() -> Unit)? = null, content: @Composable () -> Unit = {}) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = onClick != null) { onClick?.invoke() }
            .padding(vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = title, fontSize = 18.sp)
        content()
    }
}

fun getAppVersion(context: Context): String? {
    return try {
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        packageInfo.versionName
    } catch (e: PackageManager.NameNotFoundException) {
        "N/A"
    }
}
