package com.jeerovan.comfer

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private data class HomeFolderPage(val folderId: String?, val workspace: Boolean)

/** Keep outgoing icons composed until they reach their destination, including on reversal. */
@Composable
internal fun HomeFolderLayout(
    apps: List<AppInfo>,
    folders: Map<String, List<AppInfo>>,
    activeFolderId: String?,
    circular: Boolean,
    notificationPackages: List<String>,
    iconSize: Dp,
    iconShape: Shape,
    onCenterAction: () -> Unit,
    showThemedIcon: Boolean,
    themedColors: WallpaperThemeColors?,
    isLightMode: Boolean,
    onTappingFolder: (String) -> Unit,
    showGestureGuide: Boolean = false,
    showInboxGestureGuide: Boolean = false,
    workspaceOpen: Boolean = false,
    onModuleSelected: (WorkspaceModule) -> Unit = {},
    onClosed: () -> Unit = {},
) {
    var folderOrigin by remember { mutableStateOf(Offset.Zero) }
    val page = HomeFolderPage(activeFolderId, workspaceOpen)
    val layoutTransition = updateTransition(page, label = "home-folder")
    val closed by rememberUpdatedState(onClosed)
    LaunchedEffect(layoutTransition.currentState, layoutTransition.targetState, layoutTransition.isRunning) {
        if (!layoutTransition.isRunning && layoutTransition.currentState == HomeFolderPage(null, false) && layoutTransition.targetState == HomeFolderPage(null, false)) closed()
    }
    layoutTransition.AnimatedContent(
        modifier = Modifier.height(if (circular) iconSize * 4.8f else iconSize * 2 + 20.dp),
        contentAlignment = Alignment.Center,
        transitionSpec = { (EnterTransition.None togetherWith ExitTransition.None).using(null) },
    ) { shownPage ->
        val folderId = shownPage.folderId
        val progress = transition.animateFloat(
            transitionSpec = { tween(320, easing = FastOutSlowInEasing) },
            label = "folder-icon-position",
        ) { if (it == EnterExitState.Visible) 1f else 0f }
        val current = shownPage == page
        // Returning home icons must not receive a second tap intended for a closing module.
        val interactive = current && !layoutTransition.isRunning && layoutTransition.currentState == page
        val motion = FolderIconMotion(
            progress, if (shownPage.workspace) Offset.Zero else folderOrigin, interactive, scaleInPlace = folderId == null && !shownPage.workspace,
        )
        val displayed = if (shownPage.workspace) emptyList() else if (folderId == null) apps else folders[folderId].orEmpty()
        val modules = remember(circular) {
            if (circular) WorkspaceModule.entries else listOf(
                // Five-column slots fill inner-left, inner-right, outer-left, outer-right.
                WorkspaceModule.TASKS, WorkspaceModule.SEARCH, WorkspaceModule.NOTES, WorkspaceModule.JOURNAL,
            )
        }
        val moduleContent: (@Composable (Int, Offset) -> Unit)? = if (shownPage.workspace) { { index, delta ->
            val module = modules[index]
            WorkspaceModuleIcon(module, iconSize, iconShape, showThemedIcon, themedColors, isLightMode, motion, delta) {
                if (current) onModuleSelected(module)
            }
        } } else null
        if (circular) CircularLayout(
            displayed, notificationPackages, iconSize, iconShape, onCenterAction,
            showThemedIcon, themedColors, isLightMode,
            isFolderActive = activeFolderId != null || workspaceOpen, onTappingFolder = onTappingFolder,
            showGestureGuide = current && showGestureGuide,
            showInboxGestureGuide = current && showInboxGestureGuide,
            expansionKey = folderId, iconMotion = motion,
            onFolderPosition = { folderOrigin = it }, showCenter = current,
            workspaceCenter = true,
            itemCount = if (shownPage.workspace) WorkspaceModule.entries.size else displayed.size,
            itemContent = moduleContent,
        ) else FiveColumnLayout(
            displayed, notificationPackages, iconSize, iconShape, onCenterAction,
            showThemedIcon, themedColors, isLightMode,
            isFolderActive = activeFolderId != null || workspaceOpen, onTappingFolder = onTappingFolder,
            showGestureGuide = current && showGestureGuide,
            showInboxGestureGuide = current && showInboxGestureGuide,
            expansionKey = folderId, iconMotion = motion,
            onFolderPosition = { folderOrigin = it }, showCenter = current,
            workspaceCenter = true,
            itemCount = if (shownPage.workspace) WorkspaceModule.entries.size else displayed.size,
            itemContent = moduleContent,
        )
    }
}
