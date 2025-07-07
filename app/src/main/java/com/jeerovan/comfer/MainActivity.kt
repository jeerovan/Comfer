package com.jeerovan.comfer

import android.content.Intent
import android.content.pm.ResolveInfo
import android.graphics.drawable.Drawable
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

data class AppInfo(
    val resolveInfo: ResolveInfo,
    val icon: Drawable,
    val label: CharSequence
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LauncherScreen()
        }
    }
}

@Composable
fun LauncherScreen() {
    val context = LocalContext.current
    val packageManager = context.packageManager
    var scrollOffset by remember { mutableFloatStateOf(0f) }
    var scrollState = remember { Animatable(0f) }

    val apps by produceState<List<AppInfo>>(initialValue = emptyList()) {
        val intent = Intent(Intent.ACTION_MAIN, null).addCategory(Intent.CATEGORY_LAUNCHER)
        val allApps = packageManager.queryIntentActivities(intent, 0)
        value = allApps.map {
            AppInfo(
                resolveInfo = it,
                icon = it.loadIcon(packageManager),
                label = it.loadLabel(packageManager)
            )
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Image(
            painter = rememberAsyncImagePainter(
                ImageRequest.Builder(LocalContext.current)
                    .data("https://images.unsplash.com/photo-1611656993299-9f9d73232b9c?w=2000&q=99")
                    .crossfade(true)
                    .build()
            ),
            contentDescription = "Background",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 10.dp)
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        if (change.position.y > size.height / 2) {
                            scrollOffset += dragAmount.x
                        }
                    }
                }
        ) {
            if (apps.isNotEmpty()) {
                UshapedAppList(apps = apps, scrollOffset = -scrollOffset)
            }
        }
    }
}

@Composable
fun UshapedAppList(apps: List<AppInfo>, scrollOffset: Float) {
    val context = LocalContext.current
    val packageManager = context.packageManager

    val numVisibleIcons = 29
    val numTopIcons = 11
    val numSideIcons = (numVisibleIcons - numTopIcons) / 2

    Layout(
        content = {
            apps.forEach { app ->
                Image(
                    painter = rememberDrawablePainter(drawable = app.icon),
                    contentDescription = app.label.toString(),
                    modifier = Modifier
                        .clickable {
                            val launchIntent =
                                packageManager.getLaunchIntentForPackage(app.resolveInfo.activityInfo.packageName)
                            context.startActivity(launchIntent)
                        }
                )
            }
        }
    ) { measurables, constraints ->
        val placeables = measurables.map { it.measure(constraints) }

        layout(constraints.maxWidth, constraints.maxHeight) {
            val width = constraints.maxWidth.toFloat()
            val height = constraints.maxHeight.toFloat()

            val sidePadding = 32.dp.toPx()
            val topPadding = 64.dp.toPx()

            val verticalSpacing = (height - topPadding * 2) / (numSideIcons - 1)
            val arcRadius = (width - sidePadding * 2) / 2
            val angularSpacing = PI / (numTopIcons - 1)

            val totalIcons = apps.size
            val scrollIndex = (scrollOffset / 20f).roundToInt()

            val startIndex = (scrollIndex - numVisibleIcons / 2 + totalIcons) % totalIcons

            for (i in 0 until numVisibleIcons) {
                val appIndex = (startIndex + i) % totalIcons
                val placeable = placeables[appIndex]

                val (x, y, scale) = if (i < numSideIcons) {
                    // Left side
                    val xPos = sidePadding
                    val yPos = height - topPadding - i * verticalSpacing
                    Triple(xPos, yPos, 0.5f)
                } else if (i < numSideIcons + numTopIcons) {
                    // Top arc
                    val arcIndex = i - numSideIcons
                    val angle = PI - arcIndex * angularSpacing
                    val xPos = width / 2 + arcRadius * cos(angle).toFloat()
                    val yPos = topPadding + arcRadius * (1 - sin(angle)).toFloat()
                    val isCenter = arcIndex == numTopIcons / 2
                    Triple(xPos, yPos, if (isCenter) 1f else 0.5f)
                } else {
                    // Right side
                    val sideIndex = i - numSideIcons - numTopIcons
                    val xPos = width - sidePadding
                    val yPos = topPadding + sideIndex * verticalSpacing
                    Triple(xPos, yPos, 0.5f)
                }

                placeable.placeRelativeWithLayer(
                    x = (x - placeable.width / 2 * scale).toInt(),
                    y = (y - placeable.height / 2 * scale).toInt(),
                    layerBlock = {
                        this.scaleX = scale
                        this.scaleY = scale
                    }
                )
            }
        }
    }
}
