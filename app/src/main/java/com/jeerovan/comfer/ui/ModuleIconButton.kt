package com.jeerovan.comfer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Shared Notes, Tasks and Journal control surface; keep the 48 dp button touch target. */
@Composable internal fun ModuleIconButton(
    onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    IconButton(onClick = onClick, modifier = modifier, enabled = enabled) { ModuleIconSurface(content) }
}

@Composable internal fun ModuleIconToggleButton(
    checked: Boolean, onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier, enabled: Boolean = true, content: @Composable () -> Unit,
) {
    IconToggleButton(checked = checked, onCheckedChange = onCheckedChange, modifier = modifier, enabled = enabled) {
        ModuleIconSurface(content)
    }
}

@Composable internal fun ModuleIconSurface(content: @Composable () -> Unit) {
    Box(Modifier.size(40.dp).background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = .3f), CircleShape),
        contentAlignment = Alignment.Center) { content() }
}
