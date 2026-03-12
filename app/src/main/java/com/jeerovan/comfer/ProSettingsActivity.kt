package com.jeerovan.comfer

import android.os.Bundle
import android.view.SoundEffectConstants
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Brightness1
import androidx.compose.material3.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.jeerovan.comfer.ui.theme.fontProvider
import com.jeerovan.comfer.utils.CommonUtil.getFontWeightFromString
import com.jeerovan.comfer.utils.CommonUtil.getKeyTextObject
import com.jeerovan.comfer.utils.CommonUtil.isColorDark
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyGridState

class ProSettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)
        setContent {

        }
    }
}
enum class SettingsScreen {
    Basic,
    TimeAdvanced,
    DateAdvanced
}


@Composable
fun ProSettingsScreen(settingsViewModel: SettingsViewModel,
                      exitWidgetSettings: () -> Unit) {
    val view = LocalView.current
    // State variable tracks the current screen
    var currentScreen by remember { mutableStateOf(SettingsScreen.Basic) }
    AnimatedContent(
        targetState = currentScreen,
        transitionSpec = {
            // Logic: If the destination is Basic, we are going "Back".
            // Otherwise, we are going "Forward" (deeper) into settings.
            val direction = if (targetState == SettingsScreen.Basic) {
                AnimatedContentTransitionScope.SlideDirection.Right
            } else {
                AnimatedContentTransitionScope.SlideDirection.Left
            }

            // Apply the slide direction to both entering and exiting content
            (slideIntoContainer(direction) + fadeIn()) togetherWith
                    (slideOutOfContainer(direction) + fadeOut())
        },
        label = "SettingsNavigation"
    ) { screen ->
        // The 'screen' parameter ensures the correct content is rendered
        when (screen) {
            SettingsScreen.Basic -> {
                BasicSettings(
                    settingsViewModel,
                    exitWidgetSettings,
                    // Pass distinct callbacks for each sub-screen
                    onShowTimeAdvanced = { currentScreen = SettingsScreen.TimeAdvanced },
                    onShowDateAdvanced = { currentScreen = SettingsScreen.DateAdvanced }
                )
            }
            SettingsScreen.TimeAdvanced -> {
                TimeAdvancedSettings(
                    settingsViewModel,
                    onBack = {
                        view.playSoundEffect(SoundEffectConstants.CLICK)
                        currentScreen = SettingsScreen.Basic
                    }
                )
            }
            SettingsScreen.DateAdvanced -> {
                DateAdvancedSettings(
                    settingsViewModel,
                    onBack = {
                        view.playSoundEffect(SoundEffectConstants.CLICK)
                        currentScreen = SettingsScreen.Basic
                    }
                )
            }
        }
    }
}


@Composable
fun BasicSettings(
    settingsViewModel: SettingsViewModel,
    exitWidgetSettings: () -> Unit,
    onShowTimeAdvanced: () -> Unit,
    onShowDateAdvanced: () -> Unit
){
    val context = LocalContext.current
    val settingsState by settingsViewModel.uiState.collectAsState()
    var showTimeFontDialog by remember { mutableStateOf(false) }
    var showTimeFontColor by remember { mutableStateOf(false) }
    var showDateFontDialog by remember { mutableStateOf(false) }
    var showDateFontColor by remember { mutableStateOf(false) }
    var showClockBgPicker by remember { mutableStateOf(false) }
    var showClockHourPicker by remember { mutableStateOf(false) }
    var showClockMinutePicker by remember { mutableStateOf(false) }
    var showBatteryFontDialog by remember { mutableStateOf(false) }
    var showBatteryColor by remember { mutableStateOf(false) }
    var showNotificationColor by remember { mutableStateOf(false) }
    var selectedNotificationLayoutId by remember { mutableIntStateOf(settingsState.notificationLayoutId) }
    val customWallpaper = !settingsState.autoWallpapers && !settingsState.monochrome
    fun onSelectNotificationLayout(id: Int) {
        settingsViewModel.setNotificationLayoutId(id)
        selectedNotificationLayoutId = id
    }
    Surface(
        modifier = Modifier.fillMaxSize(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
    ) {
        Column {
            Box(Modifier.fillMaxWidth()) { // Use a Box to control alignment
                SmallFloatingActionButton(
                    onClick = exitWidgetSettings,
                    shape = CircleShape,
                    modifier = Modifier
                        .align(Alignment.TopCenter) // Align to the bottom-right corner
                        .padding(8.dp) // Add standard margin from the edges
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.icon_go_back),
                    )
                }
            }
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
                    .windowInsetsPadding(WindowInsets.navigationBars)
            ) {
                SettingSection(stringResource(R.string.title_widgets)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                            .clickable {
                                settingsViewModel.clearAllWidgetPositions()
                            }
                    ) {
                        Text(
                            stringResource(R.string.rest_widget_positions),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 16.dp),
                    thickness = DividerDefaults.Thickness,
                    color = DividerDefaults.color
                )
                if(settingsState.autoWallpapers)SettingSection(stringResource(R.string.title_color)) {
                    SettingSwitch(
                        label = stringResource(R.string.title_wallpaper_colors),
                        checked = settingsState.showThemedText,
                        onCheckedChange = {
                            settingsViewModel.setThemedText(it)
                        }
                    )
                }
                if(settingsState.autoWallpapers)HorizontalDivider(
                    modifier = Modifier.padding(vertical = 16.dp),
                    thickness = DividerDefaults.Thickness,
                    color = DividerDefaults.color
                )
                // Time Settings
                SettingSection(stringResource(R.string.title_time)) {
                    SettingSwitch(
                        label = stringResource(R.string.title_analog_clock),
                        checked = settingsState.showAnalog,
                        onCheckedChange = {
                            settingsViewModel.showAnalog(it)
                        }
                    )
                    if (settingsState.showAnalog) {
                        // Clock Size
                        SettingSlider(
                            label = stringResource(R.string.title_clock_size),
                            value = settingsState.clockSize,
                            range = 70f..250f,
                            onValueChange = { settingsViewModel.setClockSize(it) }
                        )
                        if (customWallpaper) {
                            // Background Color
                            ColorPickerSettingItem(
                                stringResource(R.string.clock_background_color),
                                settingsState.clockBgColor.copy(alpha=settingsState.clockBgAlpha/100f)
                            ) { showClockBgPicker = true }
                            Spacer(modifier = Modifier.height(8.dp))
                            // Hour Color
                            ColorPickerSettingItem(
                                stringResource(R.string.hour_hand_color),
                                settingsState.clockHourColor.copy(alpha = settingsState.clockHourAlpha/100f)
                            ) { showClockHourPicker = true }
                            Spacer(modifier = Modifier.height(8.dp))
                            // Minute Color
                            ColorPickerSettingItem(
                                stringResource(R.string.minute_hand_color),
                                settingsState.clockMinuteColor.copy(alpha=settingsState.clockMinuteAlpha/100f)
                            ) { showClockMinutePicker = true }
                        }
                        if (showClockBgPicker) {
                            EnhancedColorPicker(
                                predefinedColors = settingsViewModel.predefinedColors,
                                initialColor = settingsState.clockBgColor,
                                initialAlpha = settingsState.clockBgAlpha,
                                onColorSelected = { color,alpha ->
                                    settingsViewModel.setClockBgColor(color)
                                    settingsViewModel.setClockBgAlpha(alpha)
                                },
                                onDismissRequest = { showClockBgPicker = false }
                            )
                        }
                        if (showClockHourPicker) {
                            EnhancedColorPicker(
                                predefinedColors = settingsViewModel.predefinedColors,
                                initialColor = settingsState.clockHourColor,
                                initialAlpha = settingsState.clockHourAlpha,
                                onColorSelected = { color,alpha ->
                                    settingsViewModel.setClockHourColor(color)
                                    settingsViewModel.setClockHourAlpha(alpha)
                                },
                                onDismissRequest = { showClockHourPicker = false }
                            )
                        }
                        if (showClockMinutePicker) {
                            EnhancedColorPicker(
                                predefinedColors = settingsViewModel.predefinedColors,
                                initialColor = settingsState.clockMinuteColor,
                                initialAlpha = settingsState.clockMinuteAlpha,
                                onColorSelected = { color,alpha ->
                                    settingsViewModel.setClockMinuteColor(color)
                                    settingsViewModel.setClockMinuteAlpha(alpha)
                                },
                                onDismissRequest = { showClockMinutePicker = false }
                            )
                        }
                    } else {
                        // Time Format
                        SettingDropdown(
                            label = stringResource(R.string.title_time_format),
                            selectedValue = settingsState.timeFormat,
                            options = arrayOf(
                                getKeyTextObject("H12", context),
                                getKeyTextObject("H24", context)
                            ),
                            onValueChange = {
                                settingsViewModel.setTimeFormat(it)
                            }
                        )
                        // Time Font Size
                        SettingSlider(
                            label = stringResource(R.string.title_font_size),
                            value = settingsState.timeFontSize,
                            range = 30f..150f,
                            onValueChange = { settingsViewModel.setTimeFontSize(it) }
                        )

                        // Time Text Style
                        SettingDropdown(
                            label = stringResource(R.string.title_font_weight),
                            selectedValue = settingsState.timeFontWeight,
                            options = arrayOf(
                                getKeyTextObject("Light", context),
                                getKeyTextObject("Normal", context),
                                getKeyTextObject("Bold", context)
                            ),
                            onValueChange = {
                                settingsViewModel.setTimeFontWeight(it)
                            }
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showTimeFontDialog = true
                                } // Make the whole row clickable
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            val fontName = settingsState.timeFontName
                            val fontFamily = remember(fontName) {
                                FontFamily(
                                    Font(
                                        googleFont = GoogleFont(fontName),
                                        fontProvider = fontProvider
                                    )
                                )
                            }
                            Row {
                                Text(
                                    stringResource(R.string.title_font_style),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Text(
                                text = "12:34",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.primary,
                                fontFamily = fontFamily,
                                fontSize = 32.sp,
                                fontWeight = getFontWeightFromString(settingsState.timeFontWeight)
                            )
                        }
                        if (customWallpaper) {
                            ColorPickerSettingItem(
                                stringResource(R.string.title_time_font_color),
                                settingsState.timeFontColor.copy(alpha=settingsState.timeFontAlpha/100f)
                            ) { showTimeFontColor = true }
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onShowTimeAdvanced()
                                } // Make the whole row clickable
                                .padding(vertical = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row {
                                Text(
                                    stringResource(R.string.title_advanced),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Icon(
                                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = stringResource(R.string.icon_arrow_right),
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        if (showTimeFontDialog) {
                            FontSelectionDialog(
                                sampleText = "12:34",
                                onDismissRequest = { showTimeFontDialog = false },
                                onFontSelected = { fontName ->
                                    settingsViewModel.setTimeFontName(fontName)
                                    showTimeFontDialog =
                                        false // Also dismiss dialog after selection
                                }
                            )
                        }
                        if (showTimeFontColor) {
                            EnhancedColorPicker(
                                predefinedColors = settingsViewModel.predefinedColors,
                                initialColor = settingsState.timeFontColor,
                                initialAlpha = settingsState.timeFontAlpha,
                                onColorSelected = { color,alpha ->
                                    settingsViewModel.setTimeFontColor(color)
                                    settingsViewModel.setTimeFontAlpha(alpha)
                                },
                                onDismissRequest = { showTimeFontColor = false }
                            )
                        }
                    }
                }

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 16.dp),
                    thickness = DividerDefaults.Thickness,
                    color = DividerDefaults.color
                )

                // Date Settings
                SettingSection(stringResource(R.string.title_date)) {
                    // Date Font Size
                    SettingSlider(
                        label = stringResource(R.string.title_font_size),
                        value = settingsState.dateFontSize,
                        range = 15f..60f,
                        onValueChange = { settingsViewModel.setDateFontSize(it) }
                    )

                    // Date Text Style
                    SettingDropdown(
                        label = stringResource(R.string.title_font_weight),
                        selectedValue = settingsState.dateFontWeight,
                        options = arrayOf(
                            getKeyTextObject("Light", context),
                            getKeyTextObject("Normal", context),
                            getKeyTextObject("Bold", context)
                        ),
                        onValueChange = {
                            settingsViewModel.setDateFontWeight(it)
                        }
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showDateFontDialog = true }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        val fontName = settingsState.dateFontName
                        val fontFamily = remember(fontName) {
                            FontFamily(
                                Font(
                                    googleFont = GoogleFont(fontName),
                                    fontProvider = fontProvider
                                )
                            )
                        }
                        Row {
                            Text(
                                stringResource(R.string.title_font_style),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Text(
                            text = stringResource(R.string.date_example),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontFamily = fontFamily,
                            fontWeight = getFontWeightFromString(settingsState.dateFontWeight),
                            fontSize = 24.sp
                        )
                    }
                    if (customWallpaper) {
                        ColorPickerSettingItem(
                            stringResource(R.string.date_font_color),
                            settingsState.dateFontColor.copy(alpha=settingsState.dateFontAlpha/100f)
                        ) { showDateFontColor = true }
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onShowDateAdvanced()
                            } // Make the whole row clickable
                            .padding(vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row {
                            Text(
                                stringResource(R.string.title_advanced),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = stringResource(R.string.icon_arrow_right),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    if (showDateFontDialog) {
                        FontSelectionDialog(
                            sampleText = stringResource(R.string.date_example),
                            onDismissRequest = { showDateFontDialog = false },
                            onFontSelected = { fontName ->
                                settingsViewModel.setDateFontName(fontName)
                                showDateFontDialog = false // Also dismiss dialog after selection
                            }
                        )
                    }
                    if (showDateFontColor) {
                        EnhancedColorPicker(
                            predefinedColors = settingsViewModel.predefinedColors,
                            initialColor = settingsState.dateFontColor,
                            initialAlpha = settingsState.dateFontAlpha,
                            onColorSelected = { color,alpha ->
                                settingsViewModel.setDateFontColor(color)
                                settingsViewModel.setDateFontAlpha(alpha)
                            },
                            onDismissRequest = { showDateFontColor = false }
                        )
                    }
                }

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 16.dp),
                    thickness = DividerDefaults.Thickness,
                    color = DividerDefaults.color
                )

                // Battery Settings
                SettingSection(stringResource(R.string.title_battery)) {
                    SettingSwitch(
                        label = stringResource(R.string.show_battery_icon),
                        checked = settingsState.showBatteryIcon,
                        onCheckedChange = { settingsViewModel.setShowBatteryIcon(it) }
                    )
                    SettingSwitch(
                        label = stringResource(R.string.show_battery_percentage),
                        checked = settingsState.showBatteryPercentage,
                        onCheckedChange = { settingsViewModel.setShowBatteryPercentage(it) }
                    )
                    SettingSlider(
                        label = stringResource(R.string.title_font_size),
                        value = settingsState.batteryFontSize,
                        range = 10f..40f,
                        onValueChange = { settingsViewModel.setBatterySize(it) }
                    )
                    SettingDropdown(
                        label = stringResource(R.string.title_font_weight),
                        selectedValue = settingsState.batteryFontWeight,
                        options = arrayOf(
                            getKeyTextObject("Light", context),
                            getKeyTextObject("Normal", context),
                            getKeyTextObject("Bold", context)
                        ),
                        onValueChange = {
                            settingsViewModel.setBatteryFontWeight(it)
                        }
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showBatteryFontDialog = true }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        val fontName = settingsState.batteryFontName
                        val fontFamily = remember(fontName) {
                            FontFamily(
                                Font(
                                    googleFont = GoogleFont(fontName),
                                    fontProvider = fontProvider
                                )
                            )
                        }
                        Row {
                            Text(
                                stringResource(R.string.title_font_style),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Text(
                            text = "100%",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontFamily = fontFamily,
                            fontWeight = getFontWeightFromString(settingsState.batteryFontWeight),
                            fontSize = 24.sp
                        )
                    }
                    if (customWallpaper) {
                        ColorPickerSettingItem(
                            stringResource(R.string.battery_indicator_color),
                            settingsState.batteryColor.copy(alpha = settingsState.batteryAlpha/100f)
                        ) { showBatteryColor = true }
                    }
                    if (showBatteryFontDialog) {
                        FontSelectionDialog(
                            sampleText = "100%",
                            onDismissRequest = { showBatteryFontDialog = false },
                            onFontSelected = { fontName ->
                                settingsViewModel.setBatteryFontName(fontName)
                                showBatteryFontDialog = false
                            }
                        )
                    }
                    if (showBatteryColor) {
                        EnhancedColorPicker(
                            predefinedColors = settingsViewModel.predefinedColors,
                            initialColor = settingsState.batteryColor,
                            initialAlpha = settingsState.batteryAlpha,
                            onColorSelected = { color,alpha ->
                                settingsViewModel.setBatteryColor(color)
                                settingsViewModel.setBatteryAlpha(alpha)
                            },
                            onDismissRequest = { showBatteryColor = false }
                        )
                    }
                }

                if (settingsState.hasNotificationAccess) {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 16.dp),
                        thickness = DividerDefaults.Thickness,
                        color = DividerDefaults.color
                    )
                    // Notification Settings
                    SettingSection(stringResource(R.string.title_notifications)) {
                        SettingSwitch(
                            label = stringResource(R.string.show_notification_icons),
                            checked = settingsState.showNotificationRow,
                            onCheckedChange = { settingsViewModel.setShowNotificationRow(it) }
                        )
                        SettingSlider(
                            label = stringResource(R.string.title_size),
                            value = settingsState.notificationSize,
                            range = 12f..40f,
                            onValueChange = { settingsViewModel.setNotificationSize(it) }
                        )
                        if (customWallpaper) {
                            ColorPickerSettingItem(
                                stringResource(R.string.title_color),
                                settingsState.notificationColor.copy(alpha = settingsState.notificationAlpha/100f)
                            ) { showNotificationColor = true }
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Box 1
                            SelectableSquareBox(
                                id = 1,
                                selectedId = selectedNotificationLayoutId,
                                onSelect = { onSelectNotificationLayout(it) }
                            ) {
                                Row {
                                    Icon(Icons.Filled.Brightness1,
                                        contentDescription = "",
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer)
                                    Icon(Icons.Filled.Brightness1,
                                        contentDescription = "",
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer)
                                    Icon(Icons.Filled.Brightness1,
                                        contentDescription = "",
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer)
                                    Icon(Icons.Filled.Brightness1,
                                        contentDescription = "",
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer)
                                }
                            }

                            // Box 2
                            SelectableSquareBox(
                                id = 2,
                                selectedId = selectedNotificationLayoutId,
                                onSelect = { onSelectNotificationLayout(it) }
                            ) {
                                Column {
                                    Icon(Icons.Filled.Brightness1,
                                        contentDescription = "",
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer)
                                    Icon(Icons.Filled.Brightness1,
                                        contentDescription = "",
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer)
                                    Icon(Icons.Filled.Brightness1,
                                        contentDescription = "",
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer)
                                    Icon(Icons.Filled.Brightness1,
                                        contentDescription = "",
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer)
                                }
                            }
                        }
                        if (showNotificationColor) {
                            EnhancedColorPicker(
                                predefinedColors = settingsViewModel.predefinedColors,
                                initialColor = settingsState.notificationColor,
                                initialAlpha = settingsState.notificationAlpha,
                                onColorSelected = { color,alpha ->
                                    settingsViewModel.setNotificationColor(color)
                                    settingsViewModel.setNotificationAlpha(alpha)
                                },
                                onDismissRequest = { showNotificationColor = false }
                            )
                        }
                    }
                }
            }
        }
    }
}


@Composable
fun TimeAdvancedSettings(
    settingsViewModel: SettingsViewModel,
    onBack: () -> Unit){
    val settingsState by settingsViewModel.uiState.collectAsState()
    Surface(
        modifier = Modifier.fillMaxSize(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
    ) {
        val customWallpaper = !settingsState.autoWallpapers && !settingsState.monochrome
        Column {
            Box(Modifier.fillMaxWidth()) {
                SmallFloatingActionButton(
                    onClick = onBack,
                    shape = CircleShape,
                    modifier = Modifier
                        .align(Alignment.TopCenter) // Align to the bottom-right corner
                        .padding(8.dp) // Add standard margin from the edges
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.icon_go_back),
                    )
                }
            }
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
                    .windowInsetsPadding(WindowInsets.navigationBars)
            ) {
                var selectedId by remember { mutableIntStateOf(settingsState.timeLayoutId) }
                var angle by remember { mutableIntStateOf(settingsState.timeAngle) }
                var radius by remember { mutableIntStateOf(settingsState.timeRadius) }
                var showShadowColorPicker by remember { mutableStateOf(false) }
                fun onSelectLayoutId(id: Int) {
                    settingsViewModel.setTimeLayoutId(id)
                    selectedId = id
                }
                fun onSetAngle(value:Int){
                    settingsViewModel.setTimeAngle(value)
                    angle = value
                }
                fun onSetRadius(value:Int){
                    settingsViewModel.setTimeRadius(value)
                    radius = value
                }
                val fontSize = 30
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Box 1
                    SelectableSquareBox(
                        id = 1,
                        selectedId = selectedId,
                        onSelect = { onSelectLayoutId(it) }
                    ) {
                        Text("12:34",
                            fontSize = fontSize.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }

                    // Box 2
                    SelectableSquareBox(
                        id = 2,
                        selectedId = selectedId,
                        onSelect = { onSelectLayoutId(it) }
                    ) {
                        Text("1234",
                            fontSize = fontSize.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }

                    // Box 3 with Column
                    SelectableSquareBox(
                        id = 3,
                        selectedId = selectedId,
                        onSelect = { onSelectLayoutId(it) }
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text("12",
                                fontSize = fontSize.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer)
                            Text("34",
                                fontSize = (fontSize - 10).sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    }
                }
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 16.dp),
                    thickness = DividerDefaults.Thickness,
                    color = DividerDefaults.color
                )
                SettingSlider(
                    label = stringResource(R.string.title_text_angle),
                    value = angle,
                    range = 0f..360f,
                    onValueChange = { onSetAngle(it) }
                )
                CurveRadiusSlider (
                    label = stringResource(R.string.title_text_radius),
                    value = radius,
                    onValueChange = { onSetRadius(it) }
                )
                if(customWallpaper)SettingSwitch(
                    label = stringResource(R.string.title_shadow),
                    checked = settingsState.timeHasShadow,
                    onCheckedChange = {
                        settingsViewModel.setTimeHasShadow(it)
                    }
                )
                if (settingsState.timeHasShadow && customWallpaper) {
                    ColorPickerSettingItem(
                        stringResource(R.string.title_color),
                        settingsState.timeShadowColor,
                    ) { showShadowColorPicker = true }
                }
                if (showShadowColorPicker) {
                    EnhancedColorPicker(
                        predefinedColors = settingsViewModel.predefinedColors,
                        initialColor = settingsState.timeShadowColor,
                        onColorSelected = { color,alpha ->
                            settingsViewModel.setTimeShadow(color)
                        },
                        onDismissRequest = { showShadowColorPicker = false },
                        setAlpha = false
                    )
                }
            }
        }
    }
}

@Composable
fun DateAdvancedSettings(
    settingsViewModel: SettingsViewModel,
    onBack: () -> Unit) {
    val settingsState by settingsViewModel.uiState.collectAsState()
    Surface(
        modifier = Modifier.fillMaxSize(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
    ) {
        Column {
            Box(Modifier.fillMaxWidth()) {
                SmallFloatingActionButton(
                    onClick = onBack,
                    shape = CircleShape,
                    modifier = Modifier
                        .align(Alignment.TopCenter) // Align to the bottom-right corner
                        .padding(8.dp) // Add standard margin from the edges
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.icon_go_back),
                    )
                }
            }
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
                    .windowInsetsPadding(WindowInsets.navigationBars)
            ) {
                var selectedId by remember { mutableIntStateOf(settingsState.dateLayoutId) }
                var angle by remember { mutableIntStateOf(settingsState.dateAngle) }
                var radius by remember { mutableIntStateOf(settingsState.dateRadius) }
                var showShadowColorPicker by remember { mutableStateOf(false) }
                val customWallpaper = !settingsState.autoWallpapers && !settingsState.monochrome
                fun onSelectLayoutId(id: Int) {
                    settingsViewModel.setDateLayoutId(id)
                    selectedId = id
                }
                fun onSetAngle(value:Int){
                    settingsViewModel.setDateAngle(value)
                    angle = value
                }
                fun onSetRadius(value:Int){
                    settingsViewModel.setDateRadius(value)
                    radius = value
                }
                val fontSize = 20
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Box 1
                    SelectableSquareBox(
                        id = 1,
                        selectedId = selectedId,
                        onSelect = { onSelectLayoutId(it) }
                    ) {
                        Text(stringResource(R.string.date_example),
                            fontSize = fontSize.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }

                    // Box 2
                    SelectableSquareBox(
                        id = 2,
                        selectedId = selectedId,
                        onSelect = { onSelectLayoutId(it) }
                    ) {
                        Text(stringResource(R.string.date_example_two),
                            fontSize = fontSize.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 16.dp),
                    thickness = DividerDefaults.Thickness,
                    color = DividerDefaults.color
                )
                SettingSlider(
                    label = stringResource(R.string.title_text_angle),
                    value = angle,
                    range = 0f..360f,
                    onValueChange = { onSetAngle(it)}
                )
                CurveRadiusSlider(
                    label = stringResource(R.string.title_text_radius),
                    value = radius,
                    onValueChange = { onSetRadius(it) }
                )
                if(customWallpaper)SettingSwitch(
                    label = stringResource(R.string.title_shadow),
                    checked = settingsState.dateHasShadow,
                    onCheckedChange = {
                        settingsViewModel.setDateHasShadow(it)
                    }
                )
                if (settingsState.dateHasShadow && customWallpaper) {
                    ColorPickerSettingItem(
                        stringResource(R.string.title_color),
                        settingsState.dateShadowColor
                    ) { showShadowColorPicker = true }
                }
                if (showShadowColorPicker) {
                    EnhancedColorPicker(
                        predefinedColors = settingsViewModel.predefinedColors,
                        initialColor = settingsState.dateShadowColor,
                        onColorSelected = { color,alpha ->
                            settingsViewModel.setDateShadow(color)
                        },
                        onDismissRequest = { showShadowColorPicker = false },
                        setAlpha = false
                    )
                }
            }
        }
    }
}
// Helper composable for a cleaner settings screen
@Composable
fun SettingSection(title: String,
                   content: @Composable ColumnScope.() -> Unit) {
    Column {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .fillMaxWidth()
                .padding( vertical = 8.dp)
        )
        Column(modifier = Modifier, content = content)
    }
}

@Composable
fun SettingSwitch(label: String,
                  checked: Boolean,
                  onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clickable{ onCheckedChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row {
            Text(label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
fun SettingSlider(label: String,
                  value: Int,
                  range: ClosedFloatingPointRange<Float>,
                  onValueChange: (Int) -> Unit) {
    Column(modifier = Modifier.padding( vertical = 8.dp)) {
        Text(label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        Slider(
            value = value.toFloat(),
            onValueChange = {
                onValueChange(it.toInt())
                            },
            valueRange = range,
            steps = ((range.endInclusive - range.start) / 2).toInt() -1
        )
    }
}

@Composable
fun CurveRadiusSlider(
    label: String,
    value: Int,
    onValueChange: (Int) -> Unit
) {
    val range = -250f..250f

    // Total integer values = 501 (-250 to 250).
    // Steps are the ticks *between* the ends.
    // Steps = (Total Values) - 2 = 501 - 2 = 499.
    val steps = 499

    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )

        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.toInt()) },
            valueRange = range,
            steps = steps,
        )
    }
}

@Composable
fun SettingDropdown(label: String,
                    selectedValue: String,
                    options: Array<KeyTextObject>,
                    onValueChange: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val selectedOption = getKeyTextObject(selectedValue,LocalContext.current)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp)
            .clickable { expanded = true },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        Box {
            Text(
                text = selectedOption.text,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option.text,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface) },
                        onClick = {
                            onValueChange(option.key)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}
@OptIn(ExperimentalTextApi::class)
@Composable
fun FontSelectionDialog(
    sampleText: String,
    onDismissRequest: () -> Unit,
    onFontSelected: (String) -> Unit
) {
    // The same curated list of stylish Google Fonts
    val stylishFonts = listOf(
        "Iter",
        //"Picifico",
        "Shadows Into Light",
        //"Libertinus Keyboard",
        "Indie Flower",
        "Yellowtail",
        "Ole",
        "Creepster",
        "Sacramento",
        "Rock Salt",
        "Monoton",
        "Special Elite",
        "Mr Dafoe",
        "Pinyon Script",
        "Eater",
        "Silkscreen",
        "Rubik Bubbles",
        "Cabin Sketch",
        "Fredericka the Great",
        "Yuji Mai",
        "Herr Von Muellerhoff",
        "Rye",
        "Wallpoet",
        "Rampart One",
        "Monsieur La Doulaise",
        "Faster One",
        //"Exile",
        "Fascinate",
        "Shojumaru",
        "Saira Stencil One",
        "Vast Shadow",
        "Bungee Shade",
        "Sancreek",
        "Fontdiner Swanky",
        "Nosifer",
        "Kranky",
        "Lacquer",
        "Piedra",
        "Codystar",
        "Sevillana",
        "Train One",
        "Rubik Glitch",
        "Rubik Moonrocks",
        "Rubik Dirt",
        "Raleway Dots",
        "Frijole",
        "Caesar Dressing",
        "Londrina Outline",
        "Miltonian",
        "Rubik Doodle Shadow",
        "Fascinate Inline",
        "Unlock",
        "Mystery Quest",
        "Underdog",
        "Zen Tokyo Zoo",
        "Jacques Francois Shadow",
        "Ribeye Marrow",
        "Ewert",
        "Arbutus",
        //"Badeen Display",
        "Smokum",
        "Rubik Iso",
        "Water Brush",
        "Bungee Spice",
        "Foldit",
        "Nabla",
        //"Sixtyfour Convergence",
        //"Bitcount Single Ink",
        //"Coral Pixels",
        "Blaka Ink",
        "Playfair Display",
        "Lobster",
        "Merriweather",
        "Dancing Script",
    )

    Dialog(onDismissRequest = onDismissRequest) {
        // A Surface to provide a background and shape for the dialog
        Surface(
            shape = MaterialTheme.shapes.medium,
            tonalElevation = 8.dp
        ) {
            LazyColumn(modifier = Modifier.padding(vertical = 16.dp)) {
                items(stylishFonts) { fontName ->
                    // Dynamically create a FontFamily for each font in the list
                    val fontFamily = remember(fontName) {
                        FontFamily(
                            Font(
                                googleFont = GoogleFont(fontName),
                                fontProvider = fontProvider
                            )
                        )
                    }

                    // A list item that is clickable to select the font
                    ListItem(
                        headlineContent = {
                            Text(
                                text = sampleText,
                                fontFamily = fontFamily,
                                fontSize = 40.sp
                            )
                        },
                        modifier = Modifier.clickable {
                            onFontSelected(fontName)
                            onDismissRequest() // Close the dialog on selection
                        }
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        thickness = DividerDefaults.Thickness,
                        color = DividerDefaults.color
                    )
                }
            }
        }
    }
}
@Composable
fun EnhancedColorPicker(
    predefinedColors: List<Color>,
    initialColor: Color,
    onColorSelected: (Color,Int) -> Unit,
    onDismissRequest: () -> Unit,
    initialAlpha: Int = 100,
    setAlpha: Boolean = true
) {
    var selectedColor by remember { mutableStateOf(initialColor) }
    var colorAlpha by remember { mutableIntStateOf(initialAlpha) }

    @Composable
    fun ColorSlider(
        modifier: Modifier = Modifier,
        onColorChange: (Color) -> Unit
    ) {
        val gradientColors = remember {
            (0..360 step 2).map {
                Color.hsv(it.toFloat(), 1f, 1f)
            }
        }

        Box(
            modifier = modifier
                .height(40.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(
                    brush = Brush.horizontalGradient(gradientColors)
                )
                .pointerInput(Unit) {
                    detectHorizontalDragGestures { change, _ ->
                        val x = change.position.x.coerceIn(0f, size.width.toFloat())
                        val hue = (x / size.width) * 360f
                        val color = Color.hsv(hue, 1f, 1f)
                        onColorChange(color)
                        change.consume()
                    }
                }
        )
    }

    Dialog(onDismissRequest = onDismissRequest) {
        Column(
            modifier = Modifier
                .background(Color.White, RoundedCornerShape(16.dp))
                .padding(16.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Color preview rectangle
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .background(selectedColor.copy(alpha = colorAlpha/100f), RoundedCornerShape(8.dp))
            ) {
                Text(
                    text = stringResource(R.string.title_selected_color),
                    modifier = Modifier.align(Alignment.Center),
                    color = if (isColorDark(selectedColor)) Color.White else Color.Black
                )
            }

            // Predefined color palette
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 48.dp),
                modifier = Modifier.heightIn(max = 120.dp)
            ) {
                items(predefinedColors) { color ->
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .padding(4.dp)
                            .clip(CircleShape)
                            .background(color)
                            .border(
                                width = if (color == selectedColor) 3.dp else 1.dp,
                                color = if (color == selectedColor) Color.DarkGray else Color.LightGray,
                                shape = CircleShape
                            )
                            .clickable {
                                selectedColor = color
                            }
                    )
                }
            }

            // Continuous color slider
            ColorSlider(
                onColorChange = { color ->
                    selectedColor = color
                }
            )
            if(setAlpha)SettingSlider(
                label = stringResource(R.string.title_transparency),
                value = colorAlpha,
                range = 0.0f..100.0f,
                onValueChange = { value ->
                    colorAlpha = value
                }
            )
            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TextButton(onClick = onDismissRequest) {
                    Text(stringResource(R.string.cancel_text))
                }
                TextButton(onClick = {
                    onColorSelected(selectedColor,colorAlpha)
                    onDismissRequest()
                }) {
                    Text(stringResource(R.string.button_text_save))
                }
            }
        }
    }
}
@Composable
fun ColorPickerSettingItem(
    title: String,
    color: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
            .background(color, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
    ) {
        Text(
            text = title,
            modifier = Modifier.align(Alignment.Center),
            color = if (isColorDark(color)) Color.White else Color.Black
        )
    }
}

// Horizontal App Drawer
@Composable
fun AppDrawer(
    modifier: Modifier = Modifier,
    isEditMode: Boolean,
    apps: List<AppInfo>,
    canReOrder: Boolean,
    notificationPackages: List<String>,
    onAppsReordered: (Int,Int) -> Unit,
    initialHeight: Dp,
    initialOffsetY: Dp,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    horizontalSpacing: Dp,
    verticalSpacing: Dp,
    onHeightChanged: (Dp) -> Unit = {},
    onPositionChanged: (Dp) -> Unit = {},
    enterEditMode: () -> Unit,
    exitEditMode: () -> Unit,
    iconSize: Dp,
    iconShape: Shape
) {
    val hapticFeedback = LocalHapticFeedback.current
    val density = LocalDensity.current

    val maxScreenHeightDp = with(LocalDensity.current) { LocalConfiguration.current.screenHeightDp.dp }

    var drawerHeight by remember { mutableStateOf(initialHeight) }
    var drawerOffsetY by remember { mutableStateOf(initialOffsetY) }

    var appsList by remember { mutableStateOf(apps) }
    val lazyGridState = rememberLazyGridState()

    val cellSize = iconSize + 16.dp
    val totalIconHeight = cellSize + verticalSpacing
    val verticalPadding = contentPadding.calculateTopPadding() + contentPadding.calculateBottomPadding()
    val availableHeight = drawerHeight - verticalPadding
    val numberOfRows = ((availableHeight / totalIconHeight).toInt()).coerceAtLeast(1)

    // Reorderable state for the grid
    val reorderableLazyGridState = rememberReorderableLazyGridState(lazyGridState) { from, to ->
        appsList = appsList.toMutableList().apply {
            add(to.index, removeAt(from.index))
        }
        onAppsReordered(from.index,to.index)
        hapticFeedback.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
    }

    // Update apps list when external list changes
    LaunchedEffect(apps) {
        appsList = apps
    }

    Box(
        modifier = modifier
            .fillMaxSize()
    ) {
        // Main Drawer Container
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(0, drawerOffsetY.roundToPx()) }
                .then(
                    if (isEditMode) {
                        Modifier.border(1.dp, MaterialTheme.colorScheme.primary)
                    } else {
                        Modifier
                    }
                )
                .then(
                    if (isEditMode) {
                        Modifier.pointerInput(Unit) {
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                val newOffset = drawerOffsetY + with(density) { dragAmount.y.toDp() }
                                if(newOffset > 50.dp && (newOffset + drawerHeight < maxScreenHeightDp))  {
                                    drawerOffsetY = newOffset
                                    onPositionChanged(newOffset)
                                }
                            }
                        }
                    } else {
                        Modifier
                    }
                )
                .pointerInput(Unit) {
                    detectTapGestures(
                        onLongPress = {
                            enterEditMode()
                        },
                        onTap = {
                            exitEditMode()
                        }
                    )
                }
        ) {
            // App Grid
            LazyHorizontalGrid(
                rows = GridCells.Fixed(numberOfRows),
                state = lazyGridState,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(drawerHeight),
                contentPadding = contentPadding,
                horizontalArrangement = Arrangement.spacedBy(horizontalSpacing),
                verticalArrangement = Arrangement.spacedBy(verticalSpacing)
            ) {
                items(
                    count = appsList.size,
                    key = { index -> appsList[index].packageName }
                ) { index ->
                    val app = appsList[index]
                    if(canReOrder) {
                        ReorderableItem(
                            reorderableLazyGridState,
                            key = app.packageName
                        ) { isDragging ->
                            AppIconWrapper(
                                app = app,
                                notificationPackages,
                                isEditMode = isEditMode,
                                isDragging = isDragging,
                                iconSize = iconSize,
                                iconShape,
                                dragHandle = this
                            )
                        }
                    } else {
                        AppIconWrapperWoDragging(app,notificationPackages,isEditMode,iconSize,iconShape)
                    }
                }
            }
        }
        // Resize Handle - Circular dot on top border
        AnimatedVisibility(visible = isEditMode) {
            ResizeHandle(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            x = 0,
                            y = (drawerOffsetY - 7.5.dp).roundToPx()
                        )
                    }
                    .fillMaxWidth(),
                onDrag = { dragAmount ->
                    val newOffset = drawerOffsetY + dragAmount
                    if(newOffset > 50.dp)  {
                        drawerOffsetY = newOffset
                        drawerHeight = drawerHeight - dragAmount
                        onHeightChanged(drawerHeight)
                        onPositionChanged(newOffset)
                    }
                }
            )
        }
        // Resize Handle - Circular dot on bottom border
        AnimatedVisibility(visible = isEditMode) {
            ResizeHandle(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            x = 0,
                            y = (drawerOffsetY + drawerHeight - 7.5.dp).roundToPx()
                        )
                    }
                    .fillMaxWidth(),
                onDrag = { dragAmount ->
                    val newHeight = drawerHeight + dragAmount
                    if(drawerOffsetY + newHeight < maxScreenHeightDp && newHeight > 120.dp) {
                        drawerHeight = newHeight
                        onHeightChanged(newHeight)
                    }
                }
            )
        }
    }
}
@Composable
private fun ResizeHandle(
    modifier: Modifier = Modifier,
    onDrag: (Dp) -> Unit
) {
    val density = LocalDensity.current
    val hapticFeedback = LocalHapticFeedback.current

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(15.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                        },
                        onDragEnd = {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.GestureEnd)
                        }
                    ) { change, dragAmount ->
                        change.consume()
                        onDrag(with(density) { dragAmount.y.toDp() })
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
                    }
                }
        )
    }
}
@Composable
private fun AppIconWrapper(
    app: AppInfo,
    notificationPackages: List<String>,
    isEditMode: Boolean,
    isDragging: Boolean,
    iconSize: Dp,
    iconShape: Shape,
    dragHandle: sh.calvin.reorderable.ReorderableCollectionItemScope
) {
    val scale by animateFloatAsState(if (isDragging) 1.2f else 1f, label = "scale")
    Column(
        modifier = with(dragHandle) {
            Modifier
                .size(80.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .then(
                    if(isEditMode) {
                        Modifier
                            .longPressDraggableHandle()
                    } else { Modifier }
                )
        },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AppIcon(app,
            notificationPackages,
            iconShape,
            iconSize = iconSize,
            clickable = !isEditMode)

        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = app.label,
            style = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            color = Color.White,
            modifier = Modifier.widthIn(max = 80.dp),
            lineHeight = 14.sp
        )
    }
}
@Composable
private fun AppIconWrapperWoDragging(
    app: AppInfo,
    notificationPackages: List<String>,
    isEditMode: Boolean,
    iconSize: Dp,
    iconShape: Shape,
) {
    Column(
        modifier = Modifier
            .size(80.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AppIcon(app,
            notificationPackages,
            iconShape,
            iconSize = iconSize,
            clickable = !isEditMode)

        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = app.label,
            style = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            color = Color.White,
            modifier = Modifier.widthIn(max = 80.dp),
            lineHeight = 14.sp
        )
    }
}
// Usage example
@Composable
fun AppDrawerScreen(
    notificationPackages: List<String>,
    settingsViewModel: SettingsViewModel,
    appInfoViewModel: AppInfoViewModel,
    onSwipeDown: () -> Unit
) {
    val hapticFeedback = LocalHapticFeedback.current
    val settings by settingsViewModel.uiState.collectAsState()
    val appsInfo by appInfoViewModel.uiState.collectAsState()
    val sortedPrimaryApps = if(settings.arrangeInAlphabeticalOrder) appsInfo.primaryApps.sortedBy { it.label } else appsInfo.primaryApps

    var currentApps by remember(sortedPrimaryApps) { mutableStateOf(sortedPrimaryApps) }
    val maxScreenHeightDp = with(LocalDensity.current) { LocalConfiguration.current.screenHeightDp.dp }
    val initialHeight = if(settings.drawerHeight > 0) settings.drawerHeight.dp else maxScreenHeightDp - 100.dp
    val initialOffset = if(settings.drawerOffset > 0) settings.drawerOffset.dp else 50.dp
    var drawerHeight by remember { mutableStateOf(initialHeight) }
    var drawerOffset by remember { mutableStateOf(initialOffset) }

    var isEditMode by remember { mutableStateOf(false) }

    Box(modifier = Modifier
        .fillMaxSize()
        .detectGestures(
            onSwipeDown = onSwipeDown
        )
        .pointerInput(Unit) {
            detectTapGestures(
                onLongPress = {
                    isEditMode = !isEditMode
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                },
                onTap = {
                    if(isEditMode)isEditMode = false
                }
            )
        }
    ) {
        AppDrawer(
            isEditMode = isEditMode,
            apps = currentApps,
            canReOrder = !settings.arrangeInAlphabeticalOrder,
            notificationPackages = notificationPackages,
            onAppsReordered = { from,to ->
                appInfoViewModel.moveAppInList(AppInfoManager.PRIMARY_APPS_LIST_NAME,from,to)
            },
            initialHeight = drawerHeight,
            initialOffsetY = drawerOffset,
            contentPadding = PaddingValues(16.dp),
            horizontalSpacing = 16.dp,
            verticalSpacing = 16.dp,
            onHeightChanged = { newHeight ->
                drawerHeight = newHeight
                settingsViewModel.setDrawerHeight(newHeight.value.toInt())
            },
            onPositionChanged = { newOffset ->
                drawerOffset = newOffset
                settingsViewModel.setDrawerOffset(newOffset.value.toInt())
            },
            enterEditMode = {
                if(!isEditMode)isEditMode = true
            },
            exitEditMode = {
                if(isEditMode)isEditMode = false
            },
            iconSize = settings.iconSize.dp,
            iconShape = settings.iconShape
        )
    }
}

@Composable
fun RowScope.SelectableSquareBox(
    id: Int,
    selectedId: Int,
    onSelect: (Int) -> Unit,
    content: @Composable BoxScope.() -> Unit
) {
    val isSelected = selectedId == id
    val borderColor = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
    val backgroundColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f) else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
    Box(
        modifier = Modifier
            .weight(1f)             // Share width equally
            .aspectRatio(1f)        // Force Square shape
            .clip(RoundedCornerShape(12.dp)) // Corner Radius
            .background(backgroundColor)
            .border(
                width = 2.dp,
                color = borderColor,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable {
                onSelect(id)
                       },
        contentAlignment = Alignment.Center,
        content = content
    )
}