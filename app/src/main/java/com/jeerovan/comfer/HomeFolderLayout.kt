package com.jeerovan.comfer

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

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
) {
    var folderOrigin by remember { mutableStateOf(Offset.Zero) }
    AnimatedContent(
        targetState = activeFolderId,
        modifier = Modifier.height(if (circular) iconSize * 4.8f else iconSize * 2 + 20.dp),
        contentAlignment = Alignment.Center,
        transitionSpec = { (EnterTransition.None togetherWith ExitTransition.None).using(null) },
        label = "home-folder",
    ) { folderId ->
        val progress = transition.animateFloat(
            transitionSpec = { tween(320, easing = FastOutSlowInEasing) },
            label = "folder-icon-position",
        ) { if (it == EnterExitState.Visible) 1f else 0f }
        val current = folderId == activeFolderId
        val motion = FolderIconMotion(
            progress, folderOrigin, current, scaleInPlace = folderId == null,
        )
        val displayed = if (folderId == null) apps else folders[folderId].orEmpty()
        if (circular) CircularLayout(
            displayed, notificationPackages, iconSize, iconShape, onCenterAction,
            showThemedIcon, themedColors, isLightMode,
            isFolderActive = activeFolderId != null, onTappingFolder = onTappingFolder,
            showGestureGuide = current && showGestureGuide,
            showInboxGestureGuide = current && showInboxGestureGuide,
            expansionKey = folderId, iconMotion = motion,
            onFolderPosition = { folderOrigin = it }, showCenter = current,
        ) else FiveColumnLayout(
            displayed, notificationPackages, iconSize, iconShape, onCenterAction,
            showThemedIcon, themedColors, isLightMode,
            isFolderActive = activeFolderId != null, onTappingFolder = onTappingFolder,
            showGestureGuide = current && showGestureGuide,
            showInboxGestureGuide = current && showInboxGestureGuide,
            expansionKey = folderId, iconMotion = motion,
            onFolderPosition = { folderOrigin = it }, showCenter = current,
        )
    }
}
