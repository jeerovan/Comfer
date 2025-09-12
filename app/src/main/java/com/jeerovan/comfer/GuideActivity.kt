package com.jeerovan.comfer

import androidx.activity.ComponentActivity
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.jeerovan.comfer.ui.theme.ComferTheme

class GuideActivity: ComponentActivity(){
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ComferTheme {
                UserGuideScreen()
            }
        }
    }
}
/**
 * The main screen for the user guide. It uses a Scaffold for a standard
 * Material Design layout, including a TopAppBar.
 *
 * @param onNavigateUp A callback to be invoked when the user presses the back arrow.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserGuideScreen() {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Navigation/Gestures") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding), // Apply padding from the Scaffold
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            // Home Screen Section
            item {
                GuideSectionHeader(title = "Home Screen")
            }
            item {
                // Using more descriptive text for a better user experience
                GuideStepItem(text = "Swipe up to see the full App List.")
            }
            item {
                // Using more descriptive text for a better user experience
                GuideStepItem(text = "Swipe down to see system notifications.")
            }
            item {
                GuideStepItem(text = "Double tap screen to open recents.")
            }
            item {
                GuideStepItem(text = "Long-press to open settings.")
            }
            item {
                GuideStepItem(text = "Tap on Date/Time to see alarms.")
            }
            item {
                GuideStepItem(text = "Long-press on Date/Time to see calendar.")
            }
            item {
                GuideStepItem(text = "Swipe left/right to open desired app.")
            }

            // App List Section
            item {
                GuideSectionHeader(title = "App List")
            }
            item {
                GuideStepItem(text = "Swipe down to go back.")
            }
            item {
                GuideStepItem(text = "Double tap to open app in the middle.")
            }
            item {
                GuideStepItem(text = "Swipe left/right to scroll.")
            }
            item {
                GuideStepItem(text = "Tap on icon to open app.")
            }
            item {
                GuideStepItem(text = "Long press on icon to open app info.")
            }

            // Search app list
            item {
                GuideSectionHeader(title = "Search apps/contacts")
            }
            item {
                GuideStepItem(text = "Swipe down on the keyboard to go back.")
            }
            item {
                GuideStepItem(text = "Tap icon to open app.")
            }
            item {
                GuideStepItem(text = "Long press icon to view details.")
            }
            item {
                GuideStepItem(text = "Scroll list to view all apps.")
            }
            item {
                GuideStepItem(text = "Swipe right on the keyboard to search contacts.")
            }
            item {
                GuideStepItem(text = "Swipe left on the keyboard to search apps.")
            }
            item {
                GuideStepItem(text = "Scroll sides to scroll contact list.")
            }

            // Manage app list
            item {
                GuideSectionHeader(title = "Manage app list")
            }
            item {
                GuideStepItem(text = "Tap to select apps and move to another list.")
            }
            item {
                GuideStepItem(text = "Long press app icon to drag and re-order within same list.")
            }
        }
    }
}

/**
 * A styled header for a section of the guide.
 */
@Composable
fun GuideSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    )
}

/**
 * A styled item representing a single step in the guide, enclosed in a Card.
 */
@Composable
fun GuideStepItem(text: String) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                painter = painterResource(R.drawable.outline_circle_24),
                contentDescription = null, // Decorative icon
                tint = MaterialTheme.colorScheme.secondary
            )
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}


// --- PREVIEWS for Light and Dark Themes ---

// Add a theme wrapper if your project has one, otherwise this is a simple placeholder.
@Composable
fun YourAppTheme(darkTheme: Boolean = false, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) androidx.compose.material3.darkColorScheme() else androidx.compose.material3.lightColorScheme(),
        content = content
    )
}

@Preview(name = "User Guide Light Theme", showBackground = true)
@Composable
fun UserGuideScreenPreviewLight() {
    YourAppTheme(darkTheme = false) {
        Surface {
            UserGuideScreen()
        }
    }
}

@Preview(name = "User Guide Dark Theme", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun UserGuideScreenPreviewDark() {
    YourAppTheme(darkTheme = true) {
        Surface {
            UserGuideScreen()
        }
    }
}
