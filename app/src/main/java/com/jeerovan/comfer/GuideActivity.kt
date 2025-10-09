package com.jeerovan.comfer

import androidx.activity.ComponentActivity
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.jeerovan.comfer.ui.theme.ComferTheme

class GuideActivity: ComponentActivity(){
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
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
        windowInsetsController.hide(WindowInsetsCompat.Type.navigationBars())

        setContent {
            ComferTheme {
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(WindowInsets.navigationBars),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)
                ) {
                    UserGuideScreen()
                }
            }
        }
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserGuideScreen() {
    Scaffold(containerColor = Color.Transparent) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding), // Apply padding from the Scaffold
            contentPadding = WindowInsets.navigationBars.asPaddingValues()
        ) {
            // Home Screen Section
            item {
                GuideSectionHeader(title = "Home Screen")
            }
            item {
                // Using more descriptive text for a better user experience
                GuideStepItem(text = "Swipe up to see the App List.")
            }
            item {
                // Using more descriptive text for a better user experience
                GuideStepItem(text = "Swipe down to see notifications.")
            }
            item {
                GuideStepItem(text = "Double tap screen to open recents.")
            }
            item {
                GuideStepItem(text = "Long-press lower half screen to open settings.")
            }
            item {
                GuideStepItem(text = "Long-press upper half screen to edit widgets.")
            }
            item {
                GuideStepItem(text = "Tap on Date/Time to see alarms.")
            }
            item {
                GuideStepItem(text = "Long-press on Date/Time to see calendar.")
            }
            item {
                GuideStepItem(text = "Swipe left/right to open desired app or widgets screen.")
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
                GuideStepItem(text = "Swipe right on the keyboard to search contacts.")
            }
            item {
                GuideStepItem(text = "Swipe left on the keyboard to search apps.")
            }
            item {
                GuideStepItem(text = "Tap icon to open app.")
            }
            item {
                GuideStepItem(text = "Long press icon to view details.")
            }
            item {
                GuideStepItem(text = "Scroll app list to view all results.")
            }
            item {
                GuideStepItem(text = "Scroll either sides to scroll contact list.")
            }
            item {
                GuideStepItem(text = "Double tap on either sides to open dialer with number.")
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
        //elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.Transparent
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
