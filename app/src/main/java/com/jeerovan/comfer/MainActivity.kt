package com.jeerovan.comfer

import android.app.ActivityOptions
import android.content.Intent
import android.content.pm.ResolveInfo
import android.content.res.Resources
import android.graphics.drawable.Drawable
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationEndReason
import androidx.compose.animation.core.EaseInOutSine
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.absoluteValue
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

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
    val view = LocalView.current
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
    var centerAppIndex by remember { mutableIntStateOf(0) }
    var centerIconX by remember { mutableFloatStateOf(0f) }
    var centerIconY by remember { mutableFloatStateOf(0f) }
    var centerIconSize by remember { mutableFloatStateOf(0f) }
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val maxWidthPx = with(LocalDensity.current) { maxWidth.toPx() }
        val maxHeightPx = with(LocalDensity.current) { maxHeight.toPx() }

        val xOffset = remember { Animatable(0f) }
        val yOffset = remember { Animatable(0f) }

        LaunchedEffect(Unit) {
            while (true) {
                val targetX = (Random.nextFloat() * 2 - 1) * maxWidthPx * 0.08f
                val targetY = (Random.nextFloat() * 2 - 1) * maxHeightPx * 0.08f

                launch {
                    xOffset.animateTo(
                        targetValue = targetX,
                        animationSpec = tween(durationMillis = 15000, easing = EaseInOutSine)
                    )
                }
                launch {
                    yOffset.animateTo(
                        targetValue = targetY,
                        animationSpec = tween(durationMillis = 15000, easing = EaseInOutSine)
                    )
                }
                delay(15000)
            }
        }

        Image(
            painter = rememberAsyncImagePainter(
                ImageRequest.Builder(LocalContext.current)
                    .data("https://images.unsplash.com/photo-1637532766937-0504f310bd6e?w=2000&q=99")
                    .crossfade(true)
                    .build()
            ),
            contentDescription = "Background",
            modifier = Modifier
                .fillMaxSize()
                .scale(1.2f)
                .offset { IntOffset(xOffset.value.roundToInt(), yOffset.value.roundToInt()) },
            contentScale = ContentScale.Crop
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            scope.launch {
                                scrollAnimatable.stop()
                            }
                        },
                        onDoubleTap = {
                            val app = apps[centerAppIndex.absoluteValue]
                            val launchIntent =
                                packageManager.getLaunchIntentForPackage(app.resolveInfo.activityInfo.packageName)
                            if (launchIntent != null) {
                                val opts = ActivityOptions.makeScaleUpAnimation(
                                    view,
                                    centerIconX.toInt(),
                                    centerIconY.toInt(),
                                    centerIconSize.toInt(),
                                    centerIconSize.toInt()
                                )
                                context.startActivity(launchIntent, opts.toBundle())
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
                                val increment = dragAmount.x * 0.3f

                                scope.launch {
                                    val currentValue = scrollAnimatable.value
                                    var newValue =
                                        currentValue + increment
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

                            scope.launch {
                                val initialVelocity = velocity.x * 0.3f
                                val result = scrollAnimatable.animateDecay(
                                    initialVelocity,
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
                UshapedAppList(
                    apps = apps,
                    updateCenterIndex = { centerAppIndex = it },
                    scrollOffset = -scrollAnimatable.value,
                    updateCenterIconGeom = { x, y, size ->
                        centerIconX = x
                        centerIconY = y
                        centerIconSize = size
                    })
            }
        }
    }
}

private fun lerp(start: Float, stop: Float, fraction: Float): Float {
    return start + (stop - start) * fraction
}

@Composable
fun UshapedAppList(
    apps: List<AppInfo>,
    updateCenterIndex: (Int) -> Unit,
    scrollOffset: Float,
    updateCenterIconGeom: (x: Float, y: Float, size: Float) -> Unit
) {
    val numVisibleIcons = 42
    val numTopIcons = 11
    val numSideIcons = (numVisibleIcons - numTopIcons) / 2

    val smallIconSize = 42.dp
    val largeIconSize = 68.dp

    val totalIcons = apps.size
    val smoothScrollIndex = scrollOffset / 20f
    val baseScrollIndex = floor(smoothScrollIndex)
    val scrollFraction = smoothScrollIndex - baseScrollIndex

    val intScrollIndex = baseScrollIndex.toInt()
    val startIndex = (intScrollIndex - numVisibleIcons / 2 + totalIcons) % totalIcons

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {

        val density = LocalDensity.current
        val width = with(density) { maxWidth.value.dp.toPx() }
        val height = with(density) { maxHeight.value.dp.toPx() }
        val sidePadding = with(density) { 32.dp.toPx() }
        val topPadding = with(density) { 68.dp.toPx() }
        val smallIcon = with(density){smallIconSize.toPx()}
        val largeIcon = with(density){largeIconSize.toPx()}
        val verticalSpacing = (height - topPadding * 2) / (numSideIcons - 1)
        val arcRadius = (width - sidePadding * 2) / 2
        val angularSpacing = PI / (numTopIcons - 1)

        fun getPositionForSlot(slot: Int,center:Int): Pair<Float, Float> {
            return if (slot < numSideIcons) {
                // Left side
                val xPos = sidePadding - smallIcon/2
                val yPos = arcRadius + verticalSpacing + height - topPadding - slot * verticalSpacing
                Pair(xPos, yPos)
            } else if (slot < numSideIcons + numTopIcons) {
                // Top arc
                val arcIndex = slot - numSideIcons
                val angle = PI - arcIndex * angularSpacing
                var xPos = width / 2  - smallIcon/2 + arcRadius * cos(angle).toFloat()
                var yPos =  topPadding + arcRadius * (1 - sin(angle)).toFloat()
                if(slot == center){
                    xPos = xPos + smallIcon/2 - largeIcon/2
                    yPos -= largeIcon/2
                }
                Pair(xPos, yPos)
            } else {
                // Right side
                val sideIndex = slot - numSideIcons - numTopIcons
                val xPos = width - smallIcon/2 - sidePadding
                val yPos = arcRadius + verticalSpacing + topPadding + sideIndex * verticalSpacing
                Pair(xPos, yPos)
            }
        }

        val centerSlot = numSideIcons + numTopIcons / 2

        for (i in 0 until numVisibleIcons) {
            val appIndex = (startIndex + i + totalIcons) % totalIcons

            val posCurrent = getPositionForSlot(i,centerSlot)
            val posPrev = getPositionForSlot(i - 1,centerSlot)

            val x = lerp(posCurrent.first, posPrev.first, scrollFraction)
            val y = lerp(posCurrent.second, posPrev.second, scrollFraction)

            val sizeCurrent = if (i == centerSlot) largeIconSize else smallIconSize
            val sizePrev = if ((i - 1) == centerSlot) largeIconSize else smallIconSize
            val size = lerp(sizeCurrent.value, sizePrev.value, scrollFraction).dp
            if(size > 55.dp){
                updateCenterIndex(appIndex)
                val sizePx = with(density) { size.toPx() }
                updateCenterIconGeom(x, y, sizePx)
            }
            key(apps[appIndex].resolveInfo.activityInfo.packageName) {
                AppIcon(
                    app = apps[appIndex],
                    x = x.toDp(),
                    y = y.toDp(),
                    size = size
                )
            }
        }
    }
}

@Composable
fun AppIcon(app: AppInfo, x: Dp, y: Dp, size: Dp) {

    Box(
        modifier = Modifier
            .offset(x = x, y = y)
            .size(size)
            .clip(CircleShape)
            .background(Color.White),
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

