package com.jeerovan.comfer

import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
internal fun DrawerFolderLayout(
    activeFolderId: String?,
    folders: Map<String, List<AppInfo>>,
    notificationPackages: List<String>,
    iconSize: Dp,
    iconShape: Shape,
    onClose: () -> Unit,
    showThemedIcon: Boolean,
    themedColors: WallpaperThemeColors?,
    isLightMode: Boolean,
    modifier: Modifier = Modifier,
) {
    // AnimatedContent retains the outgoing folder until every icon has converged.
    AnimatedContent(
        targetState = activeFolderId,
        modifier = modifier,
        contentAlignment = Alignment.BottomCenter,
        transitionSpec = { (EnterTransition.None togetherWith ExitTransition.None).using(null) },
        label = "drawer-folder",
    ) { folderId ->
        if (folderId != null) {
            Box(Modifier.fillMaxWidth().padding(bottom = 64.dp), contentAlignment = Alignment.BottomCenter) {
                Box(
                    Modifier.animateEnterExit(
                        enter = fadeIn() + scaleIn(initialScale = .8f),
                        // Match the last returning icon (index 0), including its stagger.
                        // Fade the background and Close together instead of leaving them
                        // opaque until AnimatedContent disposes the outgoing folder.
                        exit = fadeOut(tween(260, delayMillis = 7 * 18, easing = FastOutSlowInEasing)),
                    ).background(Color.Black.copy(alpha = .3f), CircleShape).padding(16.dp),
                ) {
                    CircularLayout(
                        folders[folderId].orEmpty(), notificationPackages, iconSize, iconShape,
                        onClose, showThemedIcon, themedColors, isLightMode,
                        isFolderActive = true, showGestureGuide = false, expansionKey = folderId,
                        visibilityTransition = transition,
                    )
                }
            }
        }
    }
}
