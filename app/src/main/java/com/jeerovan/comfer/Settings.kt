package com.jeerovan.comfer

import com.jeerovan.comfer.utils.FlowerShape
import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.ui.graphics.Shape
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
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
import androidx.compose.material.icons.filled.InvertColors
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Support
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Wallpaper
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.jeerovan.comfer.ui.theme.ComferTheme
import com.jeerovan.comfer.utils.CommonUtil.isDefaultLauncher
import androidx.core.net.toUri
import androidx.core.os.LocaleListCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.jeerovan.comfer.utils.CommonUtil.canSetLockScreenWallpaper
import com.jeerovan.comfer.utils.CommonUtil.getKeyTextObject
import com.jeerovan.comfer.utils.CommonUtil.getShapeFromShape
import com.jeerovan.comfer.utils.CommonUtil.getShapeFromString
import com.jeerovan.comfer.utils.CommonUtil.getUriPath
import com.jeerovan.comfer.utils.CommonUtil.openUrl
import com.jeerovan.comfer.utils.PebbleShape
import kotlinx.coroutines.launch
import java.util.Locale

class SettingsActivity : AppCompatActivity() {
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
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)
                ) {
                    SettingsScreen(settingsViewModel)
                }
            }
        }
    }
    override fun onResume() {
        super.onResume()
        lifecycleScope.launch {
            // required to load notification change settings (example)
            settingsViewModel.loadSettings()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(settingsViewModel: SettingsViewModel) {
    val settingsState by settingsViewModel.uiState.collectAsState()
    val context = LocalContext.current
    val stringShareWith = stringResource(R.string.title_share_with)
    val stringRequiresSubscription = stringResource(R.string.requires_subscription)
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
    val appDrawerLayout = settingsState.appDrawerLayout
    var showDisclosure by remember { mutableStateOf(false) }
    var showLocaleSelection by remember {mutableStateOf(false)}

    fun checkShowDiscloseOrPermissionIntent(){
        if(settingsState.hasNotificationAccess){
            settingsViewModel.requestNotificationPermission(context)
        } else {
            showDisclosure = true
        }
    }
    fun changeAppLanguage(locale:Locale){
        val appLocale: LocaleListCompat = LocaleListCompat.forLanguageTags(locale.toLanguageTag())
        AppCompatDelegate.setApplicationLocales(appLocale)
        showLocaleSelection = false
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
            item { SectionHeader(stringResource(R.string.title_premium))}
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.title_pro_features)) },
                    supportingContent = { Text(stringResource(R.string.title_subscription)) },
                    leadingContent = {
                        Icon(painter = painterResource(R.drawable.outline_star_shine_24),
                            contentDescription = stringResource(R.string.title_subscription)) },
                    trailingContent = {
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = stringResource(R.string.go_to_page)
                        )
                    },
                    modifier = Modifier.clickable {
                        context.startActivity(Intent(context, SubscriptionActivity::class.java))
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
            }
            if(isTesting)item{
                ListItem(
                    headlineContent = { Text("Pro Access") },
                    supportingContent = { Text("Turn on to enable") },
                    leadingContent = {
                        Icon(
                            painter = painterResource(R.drawable.outline_star_shine_24),
                            contentDescription = "Pro Access"
                        )
                    },
                    trailingContent = {
                        Switch(
                            checked = settingsState.hasPro,
                            onCheckedChange = { settingsViewModel.setPro(it) }
                        )
                    },
                    modifier = Modifier.clickable { settingsViewModel.setPro(!settingsState.hasPro) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
            }
            item { SectionHeader(stringResource(R.string.title_support))}
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.title_how_to)) },
                    supportingContent = { Text(stringResource(R.string.title_app_guide)) },
                    leadingContent = { Icon(painter = painterResource(R.drawable.outline_gesture_24),
                        contentDescription = stringResource(R.string.icon_how_to_guide)) },
                    trailingContent = {
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = stringResource(R.string.icon_arrow_right)
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
                    headlineContent = { Text(stringResource(R.string.title_report_issue)) },
                    leadingContent = {
                        Icon(
                            Icons.Filled.Support,
                            contentDescription = stringResource(R.string.icon_support))
                    },
                    trailingContent = {
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = stringResource(R.string.icon_arrow_right)
                        )
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.clickable {
                        openUrl("https://t.me/comfer_launcher",context)
                    }
                )
            }
            item { SectionHeader(stringResource(R.string.title_wallpapers)) }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.title_auto_wallpapers)) },
                    supportingContent = { Text(stringResource(R.string.auto_wallpaper_text)) },
                    leadingContent = {
                        Icon(
                            Icons.Filled.Wallpaper,
                            contentDescription = stringResource(R.string.icon_wallpaper)
                        )
                    },
                    trailingContent = {
                        Switch(
                            checked = settingsState.autoWallpapers,
                            onCheckedChange = { settingsViewModel.setAutoWallpapers(it) }
                        )
                    },
                    modifier = Modifier.clickable { settingsViewModel.setAutoWallpapers(!settingsState.autoWallpapers) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
            }
            if(!settingsState.autoWallpapers)item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.title_monochrome)) },
                    supportingContent = { Text(stringResource(R.string.title_color_less_world)) },
                    leadingContent = {
                        Icon(
                            Icons.Filled.InvertColors,
                            contentDescription = stringResource(R.string.icon_monochrome)
                        )
                    },
                    trailingContent = {
                        Switch(
                            checked = settingsState.monochrome,
                            onCheckedChange = { settingsViewModel.setMonochrome(it) }
                        )
                    },
                    modifier = Modifier.clickable { settingsViewModel.setMonochrome(!settingsState.monochrome) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
            }
            if(settingsState.autoWallpapers)item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.title_wallpaper_motion)) },
                    supportingContent = { Text(stringResource(R.string.wallpaper_motion_text)) },
                    leadingContent = {
                        Icon(
                            painter = painterResource(R.drawable.outline_motion_mode_24),
                            contentDescription = stringResource(R.string.icon_wallpaper_motion)
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
            if(settingsState.autoWallpapers)item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.title_change_wallpaper)) },
                    supportingContent = { Text(stringResource(R.string.change_wallpaper_text)) },
                    leadingContent = {
                        Icon(
                            Icons.Filled.Wallpaper,
                            contentDescription = stringResource(R.string.icon_wallpaper_change)
                        )
                    },
                    modifier = Modifier.clickable { settingsViewModel.signalToChangeWallpaper() },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
            }
            if(settingsState.autoWallpapers){
                item {
                    SelectOptionsWithListItemSettingItem(
                        stringResource(R.string.title_wallpaper_frequency),
                        stringResource(R.string.wallpaper_frequency_text),
                        {Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.icon_wallpaper_frequency))},
                        settingsState.wallpaperFrequency,
                        {frequency -> settingsViewModel.setWallpaperFrequency(frequency)},
                        arrayOf(getKeyTextObject("Hourly",context),getKeyTextObject("Daily",context))
                    )
                }
            }
            if(settingsState.autoWallpapers)item{
                SelectSetOwnWallpapersDirectory(
                    isDefaultLauncher = isDefaultLauncher(context),
                    hasPro = settingsState.hasPro,
                    onSelectDirectory = { directoryUri -> settingsViewModel.setWallpaperDirectory(directoryUri)},
                    selectedDirectory = settingsState.wallpaperDirectory)
            }
            if(settingsState.autoWallpapers && isDefaultLauncher(context) && canSetLockScreenWallpaper()){
                item {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.title_lock_screen)) },
                        supportingContent = { Text(stringResource(R.string.lock_screen_wallpaper_text)) },
                        leadingContent = {
                            Icon(
                                painter = painterResource(R.drawable.outline_mobile_lock_portrait_24),
                                contentDescription = stringResource(R.string.icon_lock_screen_wallpaper)
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
            item { SectionHeader(stringResource(R.string.title_app_icons)) }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.title_app_icon_size)) },
                    supportingContent = { Text("${settingsState.iconSize} dp") },
                    leadingContent = {
                        Icon(
                            painter = painterResource(R.drawable.outline_photo_size_select_small_24),
                            contentDescription = stringResource(R.string.icon_app_icon_size)
                        )
                    },
                    trailingContent = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { settingsViewModel.changeIconSize(increase = false) }) {
                                Icon(
                                    painter = painterResource(R.drawable.outline_remove_24),
                                    contentDescription = stringResource(R.string.button_decrease_app_icon_size)
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
                                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.button_increase_app_icon_size))
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
            item{
                ListItem(
                    headlineContent = { Text(stringResource(R.string.title_themed_icons)) },
                    supportingContent = { Text(stringResource(R.string.themed_icons_text)) },
                    leadingContent = {
                        Icon(
                            Icons.Filled.ColorLens,
                            contentDescription = stringResource(R.string.icon_themed_icons)
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
            if(isTesting)item{
                ListItem(
                    headlineContent = { Text("Light Hours") },
                    supportingContent = { Text("Turn on light mode") },
                    leadingContent = {
                        Icon(
                            Icons.Filled.InvertColors,
                            contentDescription = "Dark/Light mode"
                        )
                    },
                    trailingContent = {
                        Switch(
                            checked = settingsState.isLightHour,
                            onCheckedChange = { settingsViewModel.setLightHour(it) }
                        )
                    },
                    modifier = Modifier.clickable { settingsViewModel.setLightHour(!settingsState.isLightHour) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
            }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.title_notification_badges)) },
                    supportingContent = { Text(stringResource(R.string.requires_notification_permission)) },
                    leadingContent = {
                        Icon(
                            painter = painterResource(R.drawable.outline_notifications_unread_24),
                            contentDescription = stringResource(R.string.icon_notification_badges)
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
            item { SectionHeader(stringResource(R.string.title_home_screen)) }
            item {
                ListItem(
                    headlineContent = {
                        Row {
                            Text(stringResource(R.string.title_custom_widgets))
                            if(!settingsState.hasPro)Icon(Icons.Filled.Lock,
                                contentDescription = stringResource(R.string.paid_feature_title),
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .size(15.dp)
                                    .offset(x=10.dp,y=5.dp)
                            )
                        }
                    },
                    supportingContent = { Text(stringResource(R.string.custom_widgets_text)) },
                    leadingContent = {
                        Icon(
                            painter = painterResource(R.drawable.outline_custom_widgets_24),
                            contentDescription = stringResource(R.string.icon_custom_widgets)
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
                        } else {
                            Toast.makeText(context, stringRequiresSubscription, Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
            }
            item {
                val layoutOptions:Map<String,Painter> = mapOf(
                    "linear" to painterResource(R.drawable.layout_linear),
                    "circular" to painterResource(R.drawable.layout_circular)
                )
                AppsLayoutSettingItem(
                    title = stringResource(R.string.title_quick_apps_layout),
                    subtitle = stringResource(R.string.quick_apps_layout_text),
                    leadingPainter = painterResource(R.drawable.outline_apps_24),
                    layoutOptions = layoutOptions,
                    selectedLayout = quickAppsLayout,
                    onLayoutSelected = { layout -> settingsViewModel.setQuickAppsLayout(layout)}
                )
            }
            item {
                val intent = Intent(context, AppSelectionActivity::class.java).apply {
                    putExtra("swipe_direction", "left")
                }
                SwipeActionSettingItem(
                    headline = stringResource(R.string.title_left_swipe_action),
                    icon = {
                        Icon(
                            Icons.AutoMirrored.Default.ArrowBack,
                            contentDescription = stringResource(R.string.icon_left_swipe_action)
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
                    iconShape = getShapeFromShape(iconShape, iconSize.dp)
                )
            }
            item {
                val intent = Intent(context, AppSelectionActivity::class.java).apply {
                    putExtra("swipe_direction", "right")
                }
                SwipeActionSettingItem(
                    headline = stringResource(R.string.title_right_swipe_action),
                    icon = {
                        Icon(
                            Icons.AutoMirrored.Default.ArrowForward,
                            contentDescription = stringResource(R.string.icon_right_swipe_action)
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
                    iconShape = getShapeFromShape(iconShape, iconSize.dp)
                )
            }
            item {
                ListItem(
                    headlineContent = {
                        Row {
                            Text(stringResource(R.string.title_swipe_gestures))
                            if(!settingsState.hasPro)Icon(Icons.Filled.Lock,
                                contentDescription = stringResource(R.string.paid_feature_title),
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .size(15.dp)
                                    .offset(x=10.dp,y=5.dp)
                            )
                        }
                                      },
                    supportingContent = { Text(stringResource(R.string.swipe_gestures_text)) },
                    leadingContent = { Icon(painter = painterResource(R.drawable.outline_gesture_24),
                        contentDescription = stringResource(R.string.icon_swipe_gestures)) },
                    trailingContent = {
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = stringResource(R.string.icon_arrow_right)
                        )
                    },
                    modifier = Modifier.clickable {
                        context.startActivity(Intent(context, GestureShortcutActivity::class.java))
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
            }
            item { SectionHeader(stringResource(R.string.title_app_drawer)) }
            item {
                val layoutOptions:Map<String,Painter> = mapOf(
                    "linear" to painterResource(R.drawable.outline_apps_24),
                    "circular" to painterResource(R.drawable.layout_comfer)
                )
                AppsLayoutSettingItem(
                    title = stringResource(R.string.title_app_drawer_layouts),
                    subtitle = stringResource(R.string.app_drawer_layouts_text),
                    leadingPainter = painterResource(R.drawable.outline_apps_24),
                    layoutOptions = layoutOptions,
                    selectedLayout = appDrawerLayout,
                    onLayoutSelected = { layout -> settingsViewModel.setAppDrawerLayout(layout)}
                )
            }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.title_alphabetical_order)) },
                    supportingContent = { Text(stringResource(R.string.alphabetical_order_text)) },
                    leadingContent = {
                        Icon(
                            Icons.AutoMirrored.Filled.Sort,
                            contentDescription = stringResource(R.string.icon_alphabetical_order)
                        )
                    },
                    trailingContent = {
                        Switch(
                            checked = settingsState.arrangeInAlphabeticalOrder,
                            onCheckedChange = { settingsViewModel.setAlphabeticalOrder(it) }
                        )
                    },
                    modifier = Modifier.clickable { settingsViewModel.setAlphabeticalOrder(!settingsState.arrangeInAlphabeticalOrder) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
            }
            item { SectionHeader(stringResource(R.string.title_apps_list)) }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.title_manage_app_list)) },
                    supportingContent = { Text(stringResource(R.string.manage_app_list_text)) },
                    leadingContent = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = stringResource(R.string.icon_manage_app_list)) },
                    trailingContent = {
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = stringResource(R.string.icon_arrow_right)
                        )
                    },
                    modifier = Modifier.clickable {
                        context.startActivity(Intent(context, ManageAppListActivity::class.java))
                    },
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
                ListItem(
                    headlineContent = {Text(stringResource(R.string.title_app_language))},
                    supportingContent = { Text(stringResource(R.string.app_language_text))},
                    leadingContent = { Icon(Icons.Default.Translate, contentDescription = stringResource(R.string.icon_app_language))},
                    modifier = Modifier.clickable {
                        showLocaleSelection = true
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
            }
            item {
                val context = LocalContext.current
                val packageName = context.packageName
                ListItem(
                    headlineContent = { Text(stringResource(R.string.title_feedback)) },
                    leadingContent = { Icon(painter = painterResource(R.drawable.outline_star_rate_24), contentDescription = stringResource(R.string.icon_feedback)) },
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
                val playStoreUrl = "https://play.google.com/store/apps/details?id=$packageName"
                val shareText = stringResource(R.string.share_app_text)
                ListItem(
                    headlineContent = { Text(stringResource(R.string.title_share_app)) },
                    leadingContent = { Icon(Icons.Default.Share, contentDescription = stringResource(R.string.icon_share_app)) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.clickable {
                        // Create a share intent
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(
                                Intent.EXTRA_TEXT,
                                "$shareText $playStoreUrl"
                            )
                        }
                        // Use a chooser to show the Android share sheet
                        context.startActivity(Intent.createChooser(shareIntent, stringShareWith))
                    }
                )
            }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.title_app_version)) },
                    supportingContent = { Text(getAppVersion(context) ?: "") },
                    leadingContent = { Icon(Icons.Default.Info, contentDescription = stringResource(R.string.icon_app_version)) },
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
                        context.startActivity(Intent(context, LogcatViewActivity::class.java))
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
    if(showLocaleSelection) {
        LocaleSelectionDialog(
            onDismissRequest = { showLocaleSelection = false},
            onLocaleSelected = { locale -> changeAppLanguage(locale)}
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
        headlineContent = { Text(stringResource(R.string.title_icon_shapes)) },
        supportingContent = { Text(stringResource(R.string.icon_shapes_text)) },
        leadingContent = {
            Icon(
                painter = painterResource(R.drawable.outline_blur_circular_24),
                contentDescription = stringResource(R.string.icon_icon_shapes)
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
        "cutcorner" to CutCornerShape(0.dp),
        "pebble" to PebbleShape()
    )

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(stringResource(R.string.title_select_icon_shape)) },
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
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismissRequest) {
                Text(stringResource(R.string.cancel_text))
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
    isDefaultLauncher: Boolean,
    hasPro: Boolean,
    onSelectDirectory: (directory: String?) -> Unit,
    selectedDirectory: String?
) {
    val context = LocalContext.current
    val stringRequiresSubscription = stringResource(R.string.requires_subscription)
    val stringSetLauncherFirst = stringResource(R.string.set_launcher_first)
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
                Text(stringResource(R.string.title_own_wallpapers))
                if(!hasPro)Icon(Icons.Filled.Lock,
                    contentDescription = stringResource(R.string.paid_feature_title),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .size(15.dp)
                        .offset(x=10.dp,y=5.dp)
                )
            }
                          },
        // Use the .path property for a cleaner display string.
        supportingContent = { Text(getUriPath(selectedDirectory) ?: stringResource(R.string.wallpaper_directory_select)) },
        leadingContent = {
            Icon(
                painter = painterResource(R.drawable.outline_wallpaper_directory),
                contentDescription = stringResource(R.string.icon_wallpaper_directory)
            )
        },
        trailingContent = {
            Switch(
                enabled = hasPro && isDefaultLauncher,
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
                        // reset image data with white color
                        PreferenceManager.resetImageDataWithWhiteColor(context)
                    }
                }
            )
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable {
            if (hasPro) {
                if(isDefaultLauncher){
                    if(isChecked){
                        onSelectDirectory(null)
                        // reset image data with white color
                        PreferenceManager.resetImageDataWithWhiteColor(context)
                    } else {
                        if (ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.READ_EXTERNAL_STORAGE
                            ) == PackageManager.PERMISSION_GRANTED
                        ) {
                            directoryPickerLauncher.launch(null)
                        } else {
                            permissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
                        }
                    }
                } else {
                    Toast.makeText(context, stringSetLauncherFirst, Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(context, stringRequiresSubscription, Toast.LENGTH_SHORT).show()
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
                text = stringResource(R.string.permission_required),
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.notification_permission_text),
                textAlign = TextAlign.Center,
                fontSize = 16.sp
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = stringResource(R.string.what_notification_permission_do_title),
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Text(
                text = stringResource(R.string.what_notification_permission_do_text),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp)
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = stringResource(R.string.what_notification_permission_do_not_title),
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Text(
                text = stringResource(R.string.what_notification_permission_do_not_text),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp)
            )
            Spacer(modifier = Modifier.height(32.dp))
            Row {
                Button(onClick = onContinue) {
                    Text(stringResource(R.string.continue_text))
                }
                Spacer(modifier = Modifier.width(16.dp))
                OutlinedButton(onClick = onCancel) {
                    Text(stringResource(R.string.cancel_text))
                }
            }
        }
    }
}
@Composable
fun AppsLayoutSettingItem(
    title: String,
    subtitle: String,
    leadingPainter: Painter,
    layoutOptions: Map<String,Painter>,
    selectedLayout: String?,
    onLayoutSelected: (String) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }

    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        leadingContent = { Icon(
            painter = leadingPainter,
            contentDescription = stringResource(R.string.icon_apps_layout))
        },
        trailingContent = {
            layoutOptions[selectedLayout]?.let {
                Icon(
                    painter = it,
                    contentDescription = stringResource(R.string.icon_apps_layout),
                    modifier = Modifier.size(48.dp)
                )
            }
        },
        modifier = Modifier.clickable { showDialog = true },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(stringResource(R.string.title_choose_layout)) },
            text = {
                Column (modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    layoutOptions.forEach { (key, painter) ->
                        Icon(
                            painter = painter,
                            contentDescription = key,
                            modifier = Modifier
                                .size(if (key == "linear") 76.dp else 56.dp)
                                .clickable {
                                    showDialog = false
                                    onLayoutSelected(key)
                                }
                        )
                        if (key != layoutOptions.keys.last()) {
                            Spacer(modifier = Modifier.height(24.dp))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text(stringResource(R.string.cancel_text))
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
    iconShape: Shape
) {
    var showDialog by remember { mutableStateOf(false) }

    ListItem(
        headlineContent = { Text(headline) },
        supportingContent = { Text(stringResource(R.string.swipe_action_selection)) },
        leadingContent = { icon() },
        trailingContent = {
            when {
                // If widgets are selected, show a widgets icon
                isWidgetsSelected -> {
                    Icon(
                        painter = painterResource(R.drawable.outline_widgets_24),
                        contentDescription = stringResource(R.string.icon_custom_widgets),
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
            title = { Text(stringResource(R.string.title_choose_options)) },
            text = {
                Column {
                    TextButton(
                        onClick = {
                            showDialog = false
                            onAppSelectionClick()
                        }
                    ) {
                        Text(stringResource(R.string.title_select_app))
                    }
                    TextButton(
                        onClick = {
                            showDialog = false
                            onWidgetsSelectionClick()
                        }
                    ) {
                        Text(stringResource(R.string.title_widgets_screen))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text(stringResource(R.string.cancel_text))
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
    options: Array<KeyTextObject>
) {
    var showDialog by remember { mutableStateOf(false) }
    val selectedFrequency = getKeyTextObject(selectedOption,LocalContext.current)
    ListItem(
        headlineContent = { Text(headline) },
        supportingContent = { if(supportingLine != null)Text(supportingLine) },
        leadingContent = { if(icon != null)icon() },
        trailingContent = {
            Text(selectedFrequency.text,style = MaterialTheme.typography.bodyLarge )
        },
        modifier = Modifier.fillMaxWidth().clickable { showDialog = true },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(stringResource(R.string.title_choose_action)) },
            text = {
                LazyColumn {
                    items(options) {
                        option ->
                            TextButton(
                            onClick = {
                                showDialog = false
                                onSelectionClick(option.key)
                            }
                        ) {
                            Text(option.text)
                        }
                    }

                }
            },
            confirmButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text(stringResource(R.string.cancel_text))
                }
            }
        )
    }
}