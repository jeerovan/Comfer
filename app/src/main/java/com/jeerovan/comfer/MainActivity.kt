package com.jeerovan.comfer

import android.content.Intent
import android.content.pm.ResolveInfo
import android.content.res.Resources
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationEndReason
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.absoluteValue
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
    val scrollAnimatable = remember { Animatable(0f) }
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

    val scope = rememberCoroutineScope()
    val velocityTracker = remember { VelocityTracker() }
    var lastYPosition by remember { mutableFloatStateOf(0f) }

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
                .pointerInput(Unit) {
                    detectTapGestures (
                        onPress = {
                            scope.launch {
                                scrollAnimatable.stop()
                            }
                        }
                    )
                }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = {
                            velocityTracker.resetTracking()
                            scope.launch {
                                scrollAnimatable.stop()
                            }
                        },
                        onDrag = { change, dragAmount ->
                            velocityTracker.addPosition(change.uptimeMillis, change.position)
                            lastYPosition = change.position.y
                            change.consume()
                            if (change.position.y > size.height / 2) {
                                val yNorm =
                                    (change.position.y - size.height / 2) / (size.height / 2)
                                val multiplier = 1.6f - 1.5f * yNorm.coerceIn(0f, 1f)
                                val increment = dragAmount.x * multiplier

                                scope.launch {
                                    val currentValue = scrollAnimatable.value
                                    var newValue =
                                        currentValue + increment.coerceIn(-10.0f, 10.0f)
                                    if (apps.isNotEmpty()) {
                                        val totalScrollWidth = apps.size * 20f
                                        if (totalScrollWidth > 0) {
                                            newValue = newValue.rem(totalScrollWidth)
                                            if (newValue < 0) {
                                                newValue += totalScrollWidth
                                            }
                                        }
                                    }
                                    scrollAnimatable.snapTo(newValue)
                                }
                            }
                        },
                        onDragEnd = {
                            val velocity = velocityTracker.calculateVelocity()
                            val yNorm = (lastYPosition - size.height / 2) / (size.height / 2)
                            val speedMultiplier =
                                (1.6f - 1.5f * yNorm.coerceIn(0f, 1f)).coerceAtLeast(0.1f)

                            scope.launch {
                                val initialVelocity = velocity.x * speedMultiplier
                                Log.d("velocity",initialVelocity.toString())
                                val result = scrollAnimatable.animateDecay(
                                    initialVelocity.coerceIn(-800.0f,800.0f),
                                    exponentialDecay()
                                )

                                if (result.endReason == AnimationEndReason.Finished && apps.isNotEmpty()) {
                                    val totalScrollWidth = apps.size * 20f
                                    if (totalScrollWidth > 0) {
                                        var wrappedValue =
                                            scrollAnimatable.value.rem(totalScrollWidth)
                                        if (wrappedValue < 0) {
                                            wrappedValue += totalScrollWidth
                                        }
                                        scrollAnimatable.snapTo(wrappedValue)
                                    }
                                }
                            }
                        },
                        onDragCancel = {
                            velocityTracker.resetTracking()
                        }
                    )
                }
        ) {
            if (apps.isNotEmpty()) {
                UshapedAppList(apps = apps, scrollOffset = -scrollAnimatable.value)
            }
        }
    }
}

@Composable
fun UshapedAppList(apps: List<AppInfo>, scrollOffset: Float) {
    val numVisibleIcons = 43
    val numTopIcons = 11
    val numSideIcons = (numVisibleIcons - numTopIcons) / 2

    val smallIconSize = 38.dp
    val largeIconSize = 60.dp

    val totalIcons = apps.size
    val scrollIndex = (scrollOffset / 20f).roundToInt()
    val startIndex = ((scrollIndex - numVisibleIcons / 2) % totalIcons + totalIcons) % totalIcons

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {

        val density = LocalDensity.current
        val width =  with(density){maxWidth.value.dp.toPx()}
        val height = with(density){maxHeight.value.dp.toPx()}
        val sidePadding = with(density) { 32.dp.toPx()}
        val topPadding = with(density) { 64.dp.toPx() }

        val verticalSpacing = (height - topPadding * 2) / (numSideIcons - 1)
        val arcRadius = (width - sidePadding * 2) / 2
        val angularSpacing = PI / (numTopIcons - 1)

        for (i in 0 until numVisibleIcons) {
            val appIndex = (startIndex + i) % totalIcons
            val isCenter = i == numSideIcons + numTopIcons / 2

            val (x, y) = if (i < numSideIcons) {
                // Left side
                val xPos = sidePadding
                val yPos = arcRadius + verticalSpacing + height - topPadding - i * verticalSpacing
                Pair(xPos, yPos)
            } else if (i < numSideIcons + numTopIcons) {
                // Top arc
                val arcIndex = i - numSideIcons
                val angle = PI - arcIndex * angularSpacing
                val xPos = width / 2 + arcRadius * cos(angle).toFloat()
                val yPos = topPadding + arcRadius * (1 - sin(angle)).toFloat()
                Pair(xPos, yPos)
            } else {
                // Right side
                val sideIndex = i - numSideIcons - numTopIcons
                val xPos = width - sidePadding
                val yPos = arcRadius + verticalSpacing + topPadding + sideIndex * verticalSpacing
                Pair(xPos, yPos)
            }

            key(apps[appIndex].resolveInfo.activityInfo.packageName) {
                AppIcon(
                    app = apps[appIndex],
                    x = x,
                    y = y,
                    isCenter = isCenter,
                    smallIconSize = smallIconSize,
                    largeIconSize = largeIconSize
                )
            }
        }
    }
}

@Composable
fun AppIcon(app: AppInfo, x: Float, y: Float, isCenter: Boolean, smallIconSize: Dp, largeIconSize: Dp) {
    val context = LocalContext.current
    val packageManager = context.packageManager

    val animatedSize by animateDpAsState(
        targetValue = if (isCenter) largeIconSize else smallIconSize,
        //animationSpec = tween(150)
    )
    val animatedX by animateDpAsState(targetValue = x.toDp(),
        //animationSpec = tween(150)
    )
    val animatedY by animateDpAsState(targetValue = y.toDp(),
        //animationSpec = tween(150)
    )

    Box(
        modifier = Modifier
            .offset(x = animatedX - animatedSize / 2, y = animatedY - animatedSize / 2)
            .size(animatedSize)
            .clip(CircleShape)
            .background(Color.White)
            .clickable {
                val launchIntent =
                    packageManager.getLaunchIntentForPackage(app.resolveInfo.activityInfo.packageName)
                context.startActivity(launchIntent)
            },
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = rememberDrawablePainter(drawable = app.icon),
            contentDescription = app.label.toString(),
            modifier = Modifier.padding(4.dp)
        )
    }
}

private fun Float.toDp(): Dp {
    return (this / Resources.getSystem().displayMetrics.density).dp
}

@Preview
@Composable
private fun PreviewScreen() {
    LauncherScreen()
}
