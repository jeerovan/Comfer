package com.jeerovan.comfer

import android.app.Activity
import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.jeerovan.comfer.ui.rememberThumbReach

internal enum class SettingsPage(@StringRes val title: Int, val icon: ImageVector) {
    WALLPAPERS(R.string.title_wallpapers, Icons.Default.Wallpaper),
    ICONS(R.string.title_app_icons, Icons.Default.Apps),
    HOME(R.string.title_home_screen, Icons.Default.Home),
    DRAWER(R.string.title_app_drawer, Icons.Default.GridView),
    PERFORMANCE(R.string.title_performance, Icons.Default.BatterySaver),
    PROTECTION(R.string.protection_settings, Icons.Default.Shield),
    BACKUP(R.string.title_backup, Icons.Default.Backup),
    SUPPORT(R.string.title_support, Icons.Default.HelpOutline),
}

/** Owns only navigation. Settings actions/result launchers remain alive above destinations. */
@Composable
internal fun SettingsNavigation(
    onLanguage: () -> Unit,
    content: LazyListScope.(SettingsPage) -> Unit,
) {
    var selected by rememberSaveable { mutableStateOf<SettingsPage?>(null) }
    val savedPages = rememberSaveableStateHolder()
    val context = LocalContext.current
    BackHandler(enabled = selected != null) { selected = null }
    val title = stringResource(selected?.title ?: R.string.journal_settings)
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    Column(Modifier.fillMaxSize().safeDrawingPadding().testTag("settings-viewport")) {
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val page = selected
            val pageKey = page?.name ?: "root"
            // Destination changes replace content immediately. The short fade explains the change
            // without leaving outgoing controls active; Compose honors system animator duration.
            val opacity = remember(pageKey) { Animatable(0f) }
            LaunchedEffect(pageKey) { opacity.animateTo(1f, tween(160)) }
            savedPages.SaveableStateProvider(pageKey) {
                val state = rememberLazyListState()
                val allowance = if (landscape) 0.dp else (maxHeight - 360.dp).coerceAtLeast(0.dp)
                val reach = rememberThumbReach(allowance, Triple(pageKey, maxWidth, maxHeight))
                val reachTop = with(LocalDensity.current) { reach.offset.toDp() }
                LazyColumn(
                    state = state,
                    modifier = Modifier.fillMaxSize()
                        .graphicsLayer { alpha = opacity.value }
                        .nestedScroll(reach)
                        .semantics { paneTitle = title }
                        .testTag("settings-list"),
                    // Index zero stays nearest the thumb, including on small/landscape screens.
                    reverseLayout = page == null,
                    verticalArrangement = Arrangement.Bottom,
                    contentPadding = PaddingValues(top = reachTop + 8.dp, bottom = 8.dp),
                ) {
                    if (page == null) {
                        items(SettingsPage.entries.take(5), key = { it.name }) { destination ->
                            SettingsCategoryRow(destination.title, destination.icon, destination.name) {
                                selected = destination
                            }
                        }
                        item(key = "language") {
                            SettingsCategoryRow(R.string.title_app_language, Icons.Default.Translate, "language", onLanguage)
                        }
                        items(SettingsPage.entries.drop(5), key = { it.name }) { destination ->
                            SettingsCategoryRow(destination.title, destination.icon, destination.name) {
                                selected = destination
                            }
                        }
                    } else {
                        content(page)
                    }
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .5f))
        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 8.dp)
                .testTag("settings-navigation"),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = { if (selected != null) selected = null else (context as? Activity)?.finish() },
                modifier = Modifier.testTag("settings-back"),
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.tasks_back),
                    tint = MaterialTheme.colorScheme.onSurface)
            }
            Text(title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f).padding(vertical = 12.dp).semantics { heading() }
                    .testTag("settings-title"))
        }
    }
}

@Composable
private fun SettingsCategoryRow(@StringRes title: Int, icon: ImageVector, key: String, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(stringResource(title)) },
        leadingContent = {
            Icon(icon, contentDescription = null, modifier = Modifier.testTag("settings-icon-$key"))
        },
        trailingContent = {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null,
                modifier = Modifier.testTag("settings-arrow-$key"))
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
            .clickable(onClick = onClick).testTag("settings-category-$key"),
    )
}
