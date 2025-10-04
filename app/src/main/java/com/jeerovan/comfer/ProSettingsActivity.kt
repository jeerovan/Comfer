package com.jeerovan.comfer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.jeerovan.comfer.ui.theme.ComferTheme
import com.jeerovan.comfer.ui.theme.fontProvider
import com.jeerovan.comfer.utils.CommonUtil.getFontWeightFromString
import kotlin.getValue

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.jeerovan.comfer.utils.CommonUtil.isColorDark

class ProSettingsActivity : ComponentActivity() {
    private val settingsViewModel: SettingsViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            // A basic theme wrapper
            ComferTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ProSettingsScreen(settingsViewModel)
                }
            }
        }
    }
}

@Composable
fun ProSettingsScreen(settingsViewModel: SettingsViewModel) {
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
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp,)
            .windowInsetsPadding(WindowInsets.navigationBars)
    ) {

        // Time Settings
        SettingSection("Time") {
            SettingSwitch(
                label = "Analog Clock",
                checked = settingsState.showAnalog,
                onCheckedChange = { settingsViewModel.showAnalog(it) }
            )
            if (settingsState.showAnalog) {
                // Clock Size
                SettingSlider(
                    label = "Clock Size",
                    value = settingsState.clockSize,
                    range = 70f..200f,
                    onValueChange = { settingsViewModel.setClockSize(it) }
                )
                if(settingsState.wallpaperDirectory != null) {
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
                if(showClockBgPicker){
                    EnhancedColorPicker(
                        predefinedColors = settingsViewModel.predefinedColors,
                        initialColor = settingsState.clockBgColor,
                        onColorSelected = { color ->
                            settingsViewModel.setClockBgColor(color)
                        },
                        onDismissRequest = { showClockBgPicker = false }
                    )
                }
                if(showClockHourPicker){
                    EnhancedColorPicker(
                        predefinedColors = settingsViewModel.predefinedColors,
                        initialColor = settingsState.clockHourColor,
                        onColorSelected = { color ->
                            settingsViewModel.setClockHourColor(color)
                        },
                        onDismissRequest = { showClockHourPicker = false }
                    )
                }
                if(showClockMinutePicker){
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
                    Text("Font Style", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        text = "12:34 PM",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontFamily = fontFamily,
                        fontSize = 32.sp,
                        fontWeight = getFontWeightFromString(settingsState.timeFontWeight)
                    )
                }
                if(settingsState.wallpaperDirectory != null){
                    ColorPickerSettingItem("Time font color",
                        settingsState.timeFontColor) { showTimeFontColor = true }
                }
                if (showTimeFontDialog) {
                    FontSelectionDialog(
                        sampleText = "12:34 PM",
                        onDismissRequest = { showTimeFontDialog = false },
                        onFontSelected = { fontName ->
                            settingsViewModel.setTimeFontName(fontName)
                            showTimeFontDialog = false // Also dismiss dialog after selection
                        }
                    )
                }
                if(showTimeFontColor){
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
                options = arrayOf("Light","Normal","Bold").map { it},
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
                Text("Font Style", style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = "Sat,Sept 16",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontFamily = fontFamily,
                    fontWeight = getFontWeightFromString(settingsState.dateFontWeight),
                    fontSize = 24.sp
                )
            }
            if(settingsState.wallpaperDirectory != null){
                ColorPickerSettingItem("Date font color",
                    settingsState.dateFontColor) { showDateFontColor = true }
            }
            if (showDateFontDialog) {
                FontSelectionDialog(
                    sampleText = "Sat, Sept 16",
                    onDismissRequest = { showDateFontDialog = false },
                    onFontSelected = { fontName ->
                        settingsViewModel.setDateFontName(fontName)
                        showDateFontDialog = false // Also dismiss dialog after selection
                    }
                )
            }
            if(showDateFontColor){
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
                checked = settingsState.showBatteryIcon,
                onCheckedChange = { settingsViewModel.setShowBatteryIcon(it) }
            )
            SettingSwitch(
                label = "Show Battery Percentage",
                checked = settingsState.showBatteryPercentage,
                onCheckedChange = { settingsViewModel.setShowBatteryPercentage(it) }
            )
            SettingSlider(
                label = "Size",
                value = settingsState.batterySize,
                range = 12f..40f,
                onValueChange = { settingsViewModel.setBatterySize(it) }
            )
            if(settingsState.wallpaperDirectory != null){
                ColorPickerSettingItem("Indicator color",
                    settingsState.batteryColor) { showBatteryColor = true }
            }
            if(showBatteryColor){
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


        HorizontalDivider(
            modifier = Modifier.padding(vertical = 16.dp),
            thickness = DividerDefaults.Thickness,
            color = DividerDefaults.color
        )

        // Notification Settings
        SettingSection("Notifications") {
            SettingSwitch(
                label = "Show Notification Icons",
                checked = settingsState.showNotificationRow,
                onCheckedChange = { settingsViewModel.setShowNotificationRow(it) }
            )
            SettingSlider(
                label = "Size",
                value = settingsState.notificationSize,
                range = 12f..40f,
                onValueChange = { settingsViewModel.setNotificationSize(it) }
            )
            if(settingsState.wallpaperDirectory != null){
                ColorPickerSettingItem("Notification icons color",
                    settingsState.notificationColor) { showNotificationColor = true }
            }
            if(showNotificationColor){
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
        HorizontalDivider(
            modifier = Modifier.padding(vertical = 16.dp),
            thickness = DividerDefaults.Thickness,
            color = DividerDefaults.color
        )
    }
}

// Helper composable for a cleaner settings screen

@Composable
fun SettingSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .fillMaxWidth()
                .padding( vertical = 8.dp)
        )
        Column(modifier = Modifier, content = content)
    }
}

@Composable
fun SettingSwitch(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clickable{ onCheckedChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
fun SettingSlider(label: String, value: Int, range: ClosedFloatingPointRange<Float>, onValueChange: (Int) -> Unit) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Text("$label: $value", style = MaterialTheme.typography.bodyLarge)
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
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Box {
            Text(
                text = selectedValue,
                style = MaterialTheme.typography.bodyLarge
            )
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option) },
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