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
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.jeerovan.comfer.ui.theme.ComferTheme
import com.jeerovan.comfer.ui.theme.fontProvider
import com.jeerovan.comfer.utils.CommonUtil.getFontWeightFromString
import kotlin.getValue

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
    var showDateFontDialog by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start=16.dp,top=16.dp,end=16.dp,bottom=48.dp)
    ) {
        Text("Status Bar Settings", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(bottom = 16.dp))

        // Time Settings
        SettingSection("Time") {
            // Time Format
            SettingDropdown(
                label = "Time Format",
                selectedValue = settingsState.timeFormat,
                options = arrayOf("H12","H24").map { it },
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
                options = arrayOf("Light","Normal","Bold").map { it },
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

        HorizontalDivider(
            modifier = Modifier.padding(vertical = 16.dp),
            thickness = DividerDefaults.Thickness,
            color = DividerDefaults.color
        )

        // Date Settings
        SettingSection("Date") {
            // Date Format
            OutlinedTextField(
                value = settingsState.dateFormat,
                onValueChange = { settingsViewModel.setDateFormat(it) },
                label = { Text("Date Format Pattern") },
                modifier = Modifier.fillMaxWidth()
            )

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
        }
    }
}

// Helper composables for a cleaner settings screen

@Composable
fun SettingSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column {
        Text(text = title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 8.dp))
        Column(modifier = Modifier.padding(start = 16.dp), content = content)
    }
}

@Composable
fun SettingSwitch(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
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
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Box {
            Text(
                text = selectedValue,
                modifier = Modifier.clickable { expanded = true },
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
        "Press Start 2P",
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
                                fontSize = 48.sp
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
