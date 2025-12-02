package com.jeerovan.comfer

import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
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

        setContent {
            ComferTheme {
                Surface(
                    modifier = Modifier
                        .fillMaxSize(),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize(),
            contentPadding = WindowInsets.navigationBars.asPaddingValues()
        ) {
            item {
                Spacer(Modifier.height(24.dp))
            }
            item {
                GuideSectionHeader(title = stringResource(R.string.title_home_screen))
            }
            item {
                // Using more descriptive text for a better user experience
                GuideStepItem(text = stringResource(R.string.guide_home_swipe_up))
            }
            item {
                // Using more descriptive text for a better user experience
                GuideStepItem(text = stringResource(R.string.guide_home_swipe_down))
            }
            item {
                GuideStepItem(text = stringResource(R.string.guide_home_double_tap))
            }
            item {
                GuideStepItem(text = stringResource(R.string.guide_home_long_press_lower))
            }
            item {
                GuideStepItem(text = stringResource(R.string.guide_home_long_press_upper))
            }
            item {
                GuideStepItem(text = stringResource(R.string.guide_home_tap_upper_half))
            }
            item {
                GuideStepItem(text = stringResource(R.string.guide_home_tap_date_time))
            }
            item {
                GuideStepItem(text = stringResource(R.string.guide_home_long_press_date_time))
            }
            item {
                GuideStepItem(text = stringResource(R.string.guide_home_swipe_left_right))
            }
            item {
                GuideStepItem(text = stringResource(R.string.guide_home_gestures))
            }

            // Circular app drawer
            item {
                GuideSectionHeader(title = stringResource(R.string.guide_circular_app_drawer))
            }
            item {
                GuideStepItem(text = stringResource(R.string.guide_circular_app_drawer_swipe_down))
            }
            item {
                GuideStepItem(text = stringResource(R.string.guide_circular_app_drawer_double_tap))
            }
            item {
                GuideStepItem(text = stringResource(R.string.guide_circular_app_drawer_swipe_left_right))
            }
            item {
                GuideStepItem(text = stringResource(R.string.guide_circular_app_drawer_tap_icon))
            }
            item {
                GuideStepItem(text = stringResource(R.string.guide_circular_app_drawer_long_press_icon))
            }
            // Horizontal app drawer
            item {
                GuideSectionHeader(title = stringResource(R.string.guide_horizontal_app_drawer))
            }
            item {
                GuideStepItem(text = stringResource(R.string.guide_horizontal_app_drawer_swipe_down))
            }
            item {
                GuideStepItem(text = stringResource(R.string.guide_horizontal_app_drawer_swipe_left_right))
            }
            item {
                GuideStepItem(text = stringResource(R.string.guide_horizontal_app_drawer_long_press))
            }
            item {
                GuideStepItem(text = stringResource(R.string.guide_horizontal_app_drawer_drag_drop))
            }
            item {
                GuideStepItem(text = stringResource(R.string.guide_horizontal_app_drawer_drag))
            }
            item {
                GuideStepItem(text = stringResource(R.string.guide_horizontal_app_drawer_single_tap))
            }

            // Search app list
            item {
                GuideSectionHeader(title = stringResource(R.string.guide_search))
            }
            item {
                GuideStepItem(text = stringResource(R.string.guide_search_swipe_down))
            }
            item {
                GuideStepItem(text = stringResource(R.string.guide_search_swipe_right))
            }
            item {
                GuideStepItem(text = stringResource(R.string.guide_search_swipe_left))
            }
            item {
                GuideStepItem(text = stringResource(R.string.guide_search_swipe_up))
            }
            item {
                GuideStepItem(text = stringResource(R.string.guide_search_long_press))
            }
            item {
                GuideStepItem(text = stringResource(R.string.guide_search_scroll_list))
            }
            item {
                GuideStepItem(text = stringResource(R.string.guide_search_scroll_sides))
            }
            item {
                GuideStepItem(text = stringResource(R.string.guide_search_double_tap_sides))
            }

            // Manage app list
            item {
                GuideSectionHeader(title = stringResource(R.string.guide_manage_apps))
            }
            item {
                GuideStepItem(text = stringResource(R.string.guide_manage_apps_tap))
            }
            item {
                GuideStepItem(text = stringResource(R.string.guide_manage_apps_long_press))
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

