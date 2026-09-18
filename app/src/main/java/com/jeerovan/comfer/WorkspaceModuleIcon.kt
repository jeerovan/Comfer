package com.jeerovan.comfer

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Notes
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import com.jeerovan.comfer.utils.CommonUtil.getShapeFromShape

/** Internal destinations never enter app/folder persistence or package-launch routing. */
internal enum class WorkspaceModule(@param:StringRes val title: Int, val icon: ImageVector) {
    NOTES(R.string.workspace_notes, Icons.Outlined.Notes),
    JOURNAL(R.string.journal_title, Icons.Outlined.MenuBook),
    TASKS(R.string.workspace_tasks, Icons.Outlined.TaskAlt),
    SEARCH(R.string.search, Icons.Outlined.Search),
}

@Composable
internal fun WorkspaceModuleIcon(
    module: WorkspaceModule,
    iconSize: Dp,
    iconShape: Shape,
    themed: Boolean,
    colors: WallpaperThemeColors?,
    light: Boolean,
    motion: FolderIconMotion,
    originDelta: Offset,
    onClick: () -> Unit,
) {
    val foreground = if (themed && colors != null) Color(getThemedIconColor(colors, light))
        else if (light) Color.Black else Color.White.copy(alpha = .7f)
    val background = if (themed && colors != null) Color(getThemedBackgroundColor(colors, light)) else getBackgroundColor(light)
    val title = stringResource(module.title)
    val description = title
    Box(Modifier.folderIconLayer({ motion.progress.value }, originDelta,
        if (LocalLayoutDirection.current == LayoutDirection.Rtl) -1f else 1f, false, true)
        .size(iconSize).testTag("workspace-module-${module.name.lowercase()}")) {
        Box(Modifier.align(Alignment.Center).size(iconSize).clip(getShapeFromShape(iconShape, iconSize))
            .background(background), contentAlignment = Alignment.Center) {
            Icon(module.icon, null, Modifier.size(iconSize * .65f), tint = foreground)
        }
        Box(Modifier.matchParentSize().semantics { contentDescription = description }
            .clickable(enabled = motion.interactive, role = Role.Button, onClick = onClick))
    }
}
