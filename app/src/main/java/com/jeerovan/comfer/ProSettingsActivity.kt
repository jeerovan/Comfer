package com.jeerovan.comfer

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
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
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.jeerovan.comfer.ui.theme.ComferTheme
import com.jeerovan.comfer.ui.theme.fontProvider
import com.jeerovan.comfer.utils.CommonUtil.getFontWeightFromString
import com.jeerovan.comfer.utils.CommonUtil.isColorDark
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyGridState
import kotlin.getValue

class ProSettingsActivity : ComponentActivity() {
    private val settingsViewModel: SettingsViewModel by viewModels()
    private val appsViewModel: AppInfoViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)
        setContent {
            // A basic theme wrapper
            ComferTheme {
                ProSettingsScreen(settingsViewModel)
            }
        }
    }
}

@Composable
fun ProSettingsScreen(settingsViewModel: SettingsViewModel) {
    val context = LocalContext.current
    val settingsState by settingsViewModel.uiState.collectAsState()
    var showTimeFontDialog by remember { mutableStateOf(false) }
    var showTimeFontColor by remember { mutableStateOf(false) }
    var showDateFontDialog by remember { mutableStateOf(false) }
    var showDateFontColor by remember { mutableStateOf(false) }
    var showClockBgPicker by remember { mutableStateOf(false) }
    var showClockHourPicker by remember { mutableStateOf(false) }
    var showClockMinutePicker by remember { mutableStateOf(false) }
    var showBatteryColor by remember { mutableStateOf(false) }
    var showNotificationColor by remember { mutableStateOf(false) }
    Surface(
        modifier = Modifier.fillMaxSize(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp,)
                .windowInsetsPadding(WindowInsets.navigationBars)
        ) {
            SettingSection("Widgets") {
                Box(modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .clickable {
                        settingsViewModel.clearAllWidgetPositions()
                    }
                ) {
                    Text("Reset positions",
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
            // Time Settings
            SettingSection("Time") {
                SettingSwitch(
                    label = "Analog Clock",
                    enabled = settingsState.hasPro,
                    checked = settingsState.showAnalog,
                    onCheckedChange = {
                        if(settingsState.hasPro){
                            settingsViewModel.showAnalog(it)
                        } else {
                            Toast.makeText(context, "Requires subscription", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
                if (settingsState.showAnalog) {
                    // Clock Size
                    SettingSlider(
                        label = "Clock Size",
                        value = settingsState.clockSize,
                        range = 70f..250f,
                        onValueChange = { settingsViewModel.setClockSize(it) }
                    )
                    if (settingsState.wallpaperDirectory != null) {
                        // Background Color
                        ColorPickerSettingItem(
                            "Clock background color",
                            settingsState.clockBgColor
                        ) { showClockBgPicker = true }
                        // Background Alpha
                        SettingSlider(
                            label = "Background Transparency",
                            value = (settingsState.clockBgAlpha * 100f).toInt(),
                            range = 0f..100f,
                            onValueChange = { settingsViewModel.setClockBgAlpha(it) }
                        )
                        // Hour Color
                        ColorPickerSettingItem(
                            "Hour hand color",
                            settingsState.clockHourColor
                        ) { showClockHourPicker = true }
                        Spacer(modifier = Modifier.height(8.dp))
                        // Minute Color
                        ColorPickerSettingItem(
                            "Minute hand color",
                            settingsState.clockMinuteColor
                        ) { showClockMinutePicker = true }
                    }
                    if (showClockBgPicker) {
                        EnhancedColorPicker(
                            predefinedColors = settingsViewModel.predefinedColors,
                            initialColor = settingsState.clockBgColor,
                            onColorSelected = { color ->
                                settingsViewModel.setClockBgColor(color)
                            },
                            onDismissRequest = { showClockBgPicker = false }
                        )
                    }
                    if (showClockHourPicker) {
                        EnhancedColorPicker(
                            predefinedColors = settingsViewModel.predefinedColors,
                            initialColor = settingsState.clockHourColor,
                            onColorSelected = { color ->
                                settingsViewModel.setClockHourColor(color)
                            },
                            onDismissRequest = { showClockHourPicker = false }
                        )
                    }
                    if (showClockMinutePicker) {
                        EnhancedColorPicker(
                            predefinedColors = settingsViewModel.predefinedColors,
                            initialColor = settingsState.clockMinuteColor,
                            onColorSelected = { color ->
                                settingsViewModel.setClockMinuteColor(color)
                            },
                            onDismissRequest = { showClockMinutePicker = false }
                        )
                    }
                } else {
                    // Time Format
                    SettingDropdown(
                        label = "Time Format",
                        selectedValue = settingsState.timeFormat,
                        options = arrayOf("H12", "H24").map { it },
                        onValueChange = {
                            settingsViewModel.setTimeFormat(it)
                        }
                    )
                    // Show AM/PM
                    if (settingsState.timeFormat == "H12") {
                        SettingSwitch(
                            label = "Show AM/PM",
                            enabled = true,
                            checked = settingsState.showAmPm,
                            onCheckedChange = { settingsViewModel.setShowAmPm(it) }
                        )
                    }
                    // Time Font Size
                    SettingSlider(
                        label = "Font Size",
                        value = settingsState.timeFontSize,
                        range = 20f..100f,
                        onValueChange = { settingsViewModel.setTimeFontSize(it) }
                    )

                    // Time Text Style
                    SettingDropdown(
                        label = "Font Weight",
                        selectedValue = settingsState.timeFontWeight,
                        options = arrayOf("Light", "Normal", "Bold").map { it },
                        onValueChange = {
                            settingsViewModel.setTimeFontWeight(it)
                        }
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showTimeFontDialog = true } // Make the whole row clickable
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
                            Text("Font Style",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface)
                            if(!settingsState.hasPro)Icon(Icons.Filled.Lock,
                                contentDescription = "Paid Feature",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .size(15.dp)
                                    .offset(x=10.dp,y=2.dp)
                            )
                        }
                        Text(
                            text = "12:34 PM",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontFamily = fontFamily,
                            fontSize = 32.sp,
                            fontWeight = getFontWeightFromString(settingsState.timeFontWeight)
                        )
                    }
                    if (settingsState.wallpaperDirectory != null) {
                        ColorPickerSettingItem(
                            "Time font color",
                            settingsState.timeFontColor
                        ) { showTimeFontColor = true }
                    }
                    if (showTimeFontDialog) {
                        FontSelectionDialog(
                            sampleText = "12:34 PM",
                            onDismissRequest = { showTimeFontDialog = false },
                            onFontSelected = { fontName ->
                                if(settingsState.hasPro){
                                    settingsViewModel.setTimeFontName(fontName)
                                } else {
                                    Toast.makeText(context, "Requires subscription", Toast.LENGTH_SHORT).show()
                                }
                                showTimeFontDialog = false // Also dismiss dialog after selection
                            }
                        )
                    }
                    if (showTimeFontColor) {
                        EnhancedColorPicker(
                            predefinedColors = settingsViewModel.predefinedColors,
                            initialColor = settingsState.timeFontColor,
                            onColorSelected = { color ->
                                settingsViewModel.setTimeFontColor(color)
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
            SettingSection("Date") {
                // Date Font Size
                SettingSlider(
                    label = "Font Size",
                    value = settingsState.dateFontSize,
                    range = 12f..40f,
                    onValueChange = { settingsViewModel.setDateFontSize(it) }
                )

                // Date Text Style
                SettingDropdown(
                    label = "Font Weight",
                    selectedValue = settingsState.dateFontWeight,
                    options = arrayOf("Light", "Normal", "Bold").map { it },
                    onValueChange = {
                        settingsViewModel.setDateFontWeight(it)
                    }
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showDateFontDialog = true } // Make the whole row clickable
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
                        Text("Font Style",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface)
                        if(!settingsState.hasPro)Icon(Icons.Filled.Lock,
                            contentDescription = "Paid Feature",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .size(15.dp)
                                .offset(x=10.dp,y=2.dp)
                        )
                    }
                    Text(
                        text = "Sat,Sept 16",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontFamily = fontFamily,
                        fontWeight = getFontWeightFromString(settingsState.dateFontWeight),
                        fontSize = 24.sp
                    )
                }
                if (settingsState.wallpaperDirectory != null) {
                    ColorPickerSettingItem(
                        "Date font color",
                        settingsState.dateFontColor
                    ) { showDateFontColor = true }
                }
                if (showDateFontDialog) {
                    FontSelectionDialog(
                        sampleText = "Sat, Sept 16",
                        onDismissRequest = { showDateFontDialog = false },
                        onFontSelected = { fontName ->
                            if(settingsState.hasPro){
                                settingsViewModel.setDateFontName(fontName)
                            } else {
                                Toast.makeText(context, "Requires subscription", Toast.LENGTH_SHORT).show()
                            }
                            showDateFontDialog = false // Also dismiss dialog after selection
                        }
                    )
                }
                if (showDateFontColor) {
                    EnhancedColorPicker(
                        predefinedColors = settingsViewModel.predefinedColors,
                        initialColor = settingsState.dateFontColor,
                        onColorSelected = { color ->
                            settingsViewModel.setDateFontColor(color)
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
            SettingSection("Battery") {
                SettingSwitch(
                    label = "Show Battery Icon",
                    enabled = true,
                    checked = settingsState.showBatteryIcon,
                    onCheckedChange = { settingsViewModel.setShowBatteryIcon(it) }
                )
                SettingSwitch(
                    label = "Show Battery Percentage",
                    enabled = true,
                    checked = settingsState.showBatteryPercentage,
                    onCheckedChange = { settingsViewModel.setShowBatteryPercentage(it) }
                )
                SettingSlider(
                    label = "Size",
                    value = settingsState.batterySize,
                    range = 12f..40f,
                    onValueChange = { settingsViewModel.setBatterySize(it) }
                )
                if (settingsState.wallpaperDirectory != null) {
                    ColorPickerSettingItem(
                        "Indicator color",
                        settingsState.batteryColor
                    ) { showBatteryColor = true }
                }
                if (showBatteryColor) {
                    EnhancedColorPicker(
                        predefinedColors = settingsViewModel.predefinedColors,
                        initialColor = settingsState.batteryColor,
                        onColorSelected = { color ->
                            settingsViewModel.setBatteryColor(color)
                        },
                        onDismissRequest = { showBatteryColor = false }
                    )
                }
            }

            if(settingsState.hasNotificationAccess) {
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 16.dp),
                    thickness = DividerDefaults.Thickness,
                    color = DividerDefaults.color
                )
                // Notification Settings
                SettingSection("Notifications") {
                    SettingSwitch(
                        label = "Show Notification Icons",
                        enabled = true,
                        checked = settingsState.showNotificationRow,
                        onCheckedChange = { settingsViewModel.setShowNotificationRow(it) }
                    )
                    SettingSlider(
                        label = "Size",
                        value = settingsState.notificationSize,
                        range = 12f..40f,
                        onValueChange = { settingsViewModel.setNotificationSize(it) }
                    )
                    if (settingsState.wallpaperDirectory != null) {
                        ColorPickerSettingItem(
                            "Notification icons color",
                            settingsState.notificationColor
                        ) { showNotificationColor = true }
                    }
                    if (showNotificationColor) {
                        EnhancedColorPicker(
                            predefinedColors = settingsViewModel.predefinedColors,
                            initialColor = settingsState.notificationColor,
                            onColorSelected = { color ->
                                settingsViewModel.setNotificationColor(color)
                            },
                            onDismissRequest = { showNotificationColor = false }
                        )
                    }
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
                  enabled: Boolean,
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
            if(!enabled)Icon(Icons.Filled.Lock,
                contentDescription = "Paid Feature",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .size(15.dp)
                    .offset(x=10.dp,y=2.dp)
            )
        }
        Switch(
            enabled = enabled,
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
fun SettingSlider(label: String, value: Int, range: ClosedFloatingPointRange<Float>, onValueChange: (Int) -> Unit) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Text("$label: $value",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.toInt()) },
            valueRange = range,
            steps = ((range.endInclusive - range.start) / 2).toInt() -1
        )
    }
}

@Composable
fun SettingDropdown(label: String, selectedValue: String, options: List<String>, onValueChange: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
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
                text = selectedValue,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface) },
                        onClick = {
                            onValueChange(option)
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
    onColorSelected: (Color) -> Unit,
    onDismissRequest: () -> Unit
) {
    var selectedColor by remember { mutableStateOf(initialColor) }

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
                    .background(selectedColor, RoundedCornerShape(8.dp))
            ) {
                Text(
                    text = "Selected Color",
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

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TextButton(onClick = onDismissRequest) {
                    Text("Cancel")
                }
                TextButton(onClick = {
                    onColorSelected(selectedColor)
                    onDismissRequest()
                }) {
                    Text("Set")
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

// Test Composables

@Composable
fun AppDrawer(
    modifier: Modifier = Modifier,
    isEditMode: Boolean,
    apps: List<AppInfo>,
    notificationPackages: List<String>,
    onAppsReordered: (List<AppInfo>) -> Unit,
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
    val context = LocalContext.current
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
        onAppsReordered(appsList)
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
            text = app.label.toString(),
            style = MaterialTheme.typography.bodyMedium,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            color = Color.White,
            fontWeight = FontWeight.Bold,
        )
    }
}

// Usage example
@Composable
fun AppDrawerScreen(
    apps: List<AppInfo>,
    notificationPackages: List<String>,
    settingsViewModel: SettingsViewModel,
    onSwipeDown: () -> Unit
) {
    val hapticFeedback = LocalHapticFeedback.current
    val settings by settingsViewModel.uiState.collectAsState()

    var currentApps by remember(apps) { mutableStateOf(apps) }
    val maxScreenHeightDp = with(LocalDensity.current) { LocalConfiguration.current.screenHeightDp.dp }
    val initialHeight = maxScreenHeightDp - 100.dp
    var drawerHeight by remember { mutableStateOf(initialHeight) }
    var drawerOffset by remember { mutableStateOf(50.dp) }

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
            notificationPackages = notificationPackages,
            onAppsReordered = { reorderedApps ->
                currentApps = reorderedApps
                // Persist to preferences/database
            },
            initialHeight = drawerHeight,
            initialOffsetY = drawerOffset,
            contentPadding = PaddingValues(16.dp),
            horizontalSpacing = 16.dp,
            verticalSpacing = 16.dp,
            onHeightChanged = { newHeight ->
                drawerHeight = newHeight
                // Save to preferences
            },
            onPositionChanged = { newOffset ->
                drawerOffset = newOffset
                // Save to preferences
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
