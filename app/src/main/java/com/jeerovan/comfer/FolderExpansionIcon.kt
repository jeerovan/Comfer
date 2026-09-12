package com.jeerovan.comfer

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.Transition
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection

data class FolderIconMotion(
    val progress: State<Float>,
    val origin: Offset,
    val interactive: Boolean,
    val scaleInPlace: Boolean = false,
)

/** Animate the icon layer without moving its final layout slot or its siblings. */
@Composable
internal fun FolderExpansionIcon(
    app: AppInfo,
    notificationPackages: List<String>,
    shape: Shape,
    iconSize: Dp,
    index: Int,
    expansionKey: String?,
    originDeltaDp: Offset,
    onTappingFolder: ((String) -> Unit)?,
    motion: FolderIconMotion? = null,
    visibilityTransition: Transition<EnterExitState>? = null,
) {
    val horizontalDirection = if (LocalLayoutDirection.current == LayoutDirection.Rtl) -1f else 1f
    key(expansionKey, app.packageName, app.user, index) {
        val progress = remember { Animatable(if (expansionKey == null) 1f else 0f) }
        LaunchedEffect(expansionKey) {
            if (expansionKey != null && motion == null && visibilityTransition == null) {
                progress.animateTo(1f, tween(260, delayMillis = index * 18, easing = FastOutSlowInEasing))
            }
        }
        val visibilityProgress = visibilityTransition?.animateFloat(
            transitionSpec = {
                tween(260, delayMillis = if (targetState == EnterExitState.Visible) index * 18 else (7 - index) * 18,
                    easing = FastOutSlowInEasing)
            }, label = "drawer-folder-icon-$index",
        ) { if (it == EnterExitState.Visible) 1f else 0f }
        Box(Modifier.graphicsLayer {
            val fraction = visibilityProgress?.value ?: motion?.progress?.value ?: progress.value
            translationX = if (motion?.scaleInPlace == true) 0f else originDeltaDp.x * horizontalDirection * density * (1f - fraction)
            translationY = if (motion?.scaleInPlace == true) 0f else originDeltaDp.y * density * (1f - fraction)
            scaleX = if (motion != null) fraction else .35f + .65f * fraction
            scaleY = scaleX
            alpha = fraction
        }) {
            AppIcon(app, notificationPackages, shape, iconSize = iconSize,
                clickable = visibilityTransition?.let { it.targetState == EnterExitState.Visible }
                    ?: motion?.interactive ?: true, onTappingFolder = onTappingFolder)
        }
    }
}
