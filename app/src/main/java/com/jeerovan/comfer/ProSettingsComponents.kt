package com.jeerovan.comfer

import android.content.res.Configuration
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jeerovan.comfer.ui.rememberThumbReach
import java.text.NumberFormat

/** An opaque editing surface keeps wallpaper detail behind the live preview. */
@Composable
internal fun ProSettingsPanel(
    title: String,
    subtitle: String,
    onBack: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    // Portrait lives below the preview; landscape reaches the window's top edge.
    val headerInsets = if (landscape) WindowInsets.safeDrawing.only(
        WindowInsetsSides.Top + WindowInsetsSides.Horizontal
    ) else WindowInsets(0, 0, 0, 0)
    Surface(
        modifier = Modifier.fillMaxSize().testTag("pro-settings-panel"),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        color = colors.surface,
        contentColor = colors.onSurface,
        border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = .45f)),
    ) {
        Column {
            Row(
                Modifier.fillMaxWidth().windowInsetsPadding(headerInsets)
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                FilledTonalIconButton(
                    onClick = onBack,
                    modifier = Modifier.size(48.dp),
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = colors.surfaceColorAtElevation(3.dp),
                        contentColor = colors.onSurface,
                    ),
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.icon_go_back))
                }
                Column(Modifier.weight(1f)) {
                    Text(title, Modifier.semantics { heading() }.testTag("pro-panel-title"),
                        style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text(subtitle, style = MaterialTheme.typography.labelMedium,
                        color = colors.onSurfaceVariant)
                }
            }
            HorizontalDivider(color = colors.outlineVariant.copy(alpha = .45f))
            BoxWithConstraints(Modifier.weight(1f).navigationBarsPadding()) {
                val reach = rememberThumbReach(
                    if (landscape) 0.dp else (maxHeight - 360.dp).coerceAtLeast(0.dp),
                    screenKey = maxHeight to landscape,
                )
                val reachHeight = with(LocalDensity.current) { reach.offset.toDp() }
                Column(
                    Modifier.fillMaxSize().nestedScroll(reach)
                        .verticalScroll(rememberScrollState())
                        .testTag("pro-settings-scroll")
                        .padding(horizontal = 16.dp),
                ) {
                    Spacer(Modifier.height(reachHeight + 16.dp))
                    content()
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
}

@Composable
internal fun ProAdvancedDestination(onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp).testTag("pro-advanced-destination"),
        shape = RoundedCornerShape(12.dp),
        color = colors.primary.copy(alpha = .08f),
        contentColor = colors.primary,
    ) {
        Row(Modifier.heightIn(min = 52.dp).padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.title_advanced), Modifier.weight(1f),
                style = MaterialTheme.typography.labelLarge)
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null,
                modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
internal fun ProSliderHeading(label: String, value: Int) {
    val locale = LocalConfiguration.current.locales[0]
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surfaceColorAtElevation(5.dp)) {
            Text(NumberFormat.getIntegerInstance(locale).format(value),
                Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface)
        }
    }
}
