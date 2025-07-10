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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationEndReason
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.PI
import kotlin.math.absoluteValue
import kotlin.math.asin
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

data class AppInfo(
    val resolveInfo: ResolveInfo,
    val icon: Drawable,
    val label: CharSequence,
    val packageName: String
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
fun QuickListOverlay(apps: List<AppInfo>, onSwipeUp: () -> Unit) {
    val context = LocalContext.current

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 100.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            var time by remember { mutableStateOf("") }
            var date by remember { mutableStateOf("") }

            val timeFormat = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }
            val dateFormat = remember { SimpleDateFormat("EEE, MMM d", Locale.getDefault()) }

            LaunchedEffect(Unit) {
                while (true) {
                    val now = System.currentTimeMillis()
                    time = timeFormat.format(Date(now))
                    date = dateFormat.format(Date(now))
                    delay(1000)
                }
            }

            Text(
                text = time,
                color = Color.White,
                fontSize = 60.sp,
                fontWeight = FontWeight.Light
            )
            Text(
                text = date,
                color = Color.White,
                fontSize = 20.sp,
            )
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(250.dp)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onLongPress = {
                            context.startActivity(Intent(context, ManageLayersActivity::class.java))
                        }
                    )
                }
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        if (dragAmount.y < -40) { // Swipe up
                            onSwipeUp()
                            change.consume()
                        }
                    }
                },
            contentAlignment = Alignment.BottomCenter
        ) {
            Row(
                modifier = Modifier.padding(bottom = 64.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                apps.forEach { app ->
                    val packageManager = context.packageManager
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(Color.White)
                            .clickable {
                                val launchIntent =
                                    packageManager.getLaunchIntentForPackage(app.packageName)
                                context.startActivity(launchIntent)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = rememberDrawablePainter(drawable = app.icon),
                            contentDescription = app.label.toString(),
                            modifier = Modifier
                                .padding(4.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AppListOverlay(apps: List<AppInfo>, onSwipeDown: () -> Unit) {
    val context = LocalContext.current
    val view = LocalView.current
    val packageManager = context.packageManager
    val scope = rememberCoroutineScope()

    val scrollAnimatable = remember { Animatable(0f) }
    val velocityTracker = remember { VelocityTracker() }
    var lastYPosition by remember { mutableFloatStateOf(0f) }
    var centerAppIndex by remember { mutableIntStateOf(0) }
    var centerIconX by remember { mutableFloatStateOf(0f) }
    var centerIconY by remember { mutableFloatStateOf(0f) }
    var centerIconSize by remember { mutableFloatStateOf(0f) }
    var dragAxis by remember { mutableStateOf<DragAxis?>(null) }
    var verticalDragAmount by remember { mutableFloatStateOf(0f) }
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
                        if (apps.isNotEmpty()) {
                            val app = apps[centerAppIndex.absoluteValue]
                            val launchIntent =
                                packageManager.getLaunchIntentForPackage(app.packageName)
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
                    }
                )
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = {
                        dragAxis = null
                        verticalDragAmount = 0f
                        velocityTracker.resetTracking()
                        scope.launch {
                            scrollAnimatable.stop()
                        }
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        if (dragAxis == null) {
                            if (dragAmount.x.absoluteValue > 4f || dragAmount.y.absoluteValue > 4f) {
                                dragAxis =
                                    if (dragAmount.x.absoluteValue > dragAmount.y.absoluteValue) {
                                        DragAxis.HORIZONTAL
                                    } else {
                                        DragAxis.VERTICAL
                                    }
                            }
                        }

                        when (dragAxis) {
                            DragAxis.HORIZONTAL -> {
                                velocityTracker.addPosition(
                                    change.uptimeMillis,
                                    change.position
                                )
                                lastYPosition = change.position.y
                                if (change.position.y > size.height / 2) {
                                    val increment = dragAmount.x * 0.3f

                                    scope.launch {
                                        val currentValue = scrollAnimatable.value
                                        var newValue =
                                            currentValue + increment
                                        if (apps.isNotEmpty()) {
                                            val totalScrollWidth = apps.size * 20f
                                            if (totalScrollWidth > 0) {
                                                newValue =
                                                    newValue.rem(totalScrollWidth)
                                                if (newValue < 0) {
                                                    newValue += totalScrollWidth
                                                }
                                            }
                                        }
                                        scrollAnimatable.snapTo(newValue)
                                    }
                                }
                            }

                            DragAxis.VERTICAL -> {
                                verticalDragAmount += dragAmount.y
                                if (verticalDragAmount > 80f) {
                                    onSwipeDown()
                                }
                            }

                            null -> { /* Wait for axis detection */
                            }
                        }
                    },
                    onDragEnd = {
                        if (dragAxis == DragAxis.HORIZONTAL) {
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

private enum class DragAxis { HORIZONTAL, VERTICAL }

@Composable
fun LauncherScreen() {
    val context = LocalContext.current
    var isAppListVisible by remember { mutableStateOf(false) }

    val allApps by produceState<List<AppInfo>>(initialValue = emptyList(), context) {
        val packageManager = context.packageManager
        var packageNames =
            AppInfoManager.getAppPackageNames(context, AppInfoManager.ALL_APPS_LIST_NAME)

        if (packageNames == null) { // First time launch or cache cleared
            val intent = Intent(Intent.ACTION_MAIN, null).addCategory(Intent.CATEGORY_LAUNCHER)
            val allResolveInfos = packageManager.queryIntentActivities(intent, 0)
            packageNames = allResolveInfos.map { it.activityInfo.packageName }.toSet()
            AppInfoManager.saveAppPackageNames(
                context,
                AppInfoManager.ALL_APPS_LIST_NAME,
                packageNames
            )
            val quickAppList: Set<String> = filterStandardApps(packageNames)
            AppInfoManager.saveAppPackageNames(
                context,
                AppInfoManager.QUICK_APPS_LIST_NAME,
                quickAppList
            )
            val primaryAppList: Set<String> = packageNames.filter { packageName ->
                !quickAppList.contains(packageName)
            }.toSet()
            AppInfoManager.saveAppPackageNames(
                context,
                AppInfoManager.PRIMARY_APPS_LIST_NAME,
                primaryAppList
            ) // Default primary to all
        }

        value = packageNames.mapNotNull { packageName ->
            packageManager.getLaunchIntentForPackage(packageName)?.let { launchIntent ->
                packageManager.resolveActivity(launchIntent, 0)?.let { resolveInfo ->
                    AppInfo(
                        resolveInfo = resolveInfo,
                        icon = resolveInfo.loadIcon(packageManager),
                        label = resolveInfo.loadLabel(packageManager),
                        packageName = packageName
                    )
                }
            }
        }
    }

    val quickApps by produceState<List<AppInfo>>(initialValue = emptyList(), allApps) {
        val packageNames =
            AppInfoManager.getAppPackageNames(context, AppInfoManager.QUICK_APPS_LIST_NAME)
                ?: emptySet()
        value = packageNames.mapNotNull { packageName ->
            allApps.find { it.packageName == packageName }
        }
    }

    val primaryApps by produceState<List<AppInfo>>(initialValue = emptyList(), allApps) {
        val packageNames =
            AppInfoManager.getAppPackageNames(context, AppInfoManager.PRIMARY_APPS_LIST_NAME)
                ?: emptySet()
        value = packageNames.mapNotNull { packageName ->
            allApps.find { it.packageName == packageName }
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(onLongPress = {
                    context.startActivity(Intent(context, ManageLayersActivity::class.java))
                })
            }) {
        val maxWidthPx = with(LocalDensity.current) { maxWidth.toPx() }
        val maxHeightPx = with(LocalDensity.current) { maxHeight.toPx() }

        val angle = remember { Animatable(0f) }

        LaunchedEffect(Unit) {
            angle.animateTo(
                targetValue = (2 * PI).toFloat(),
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 100000, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                )
            )
        }

        val xOffset = cos(angle.value) * maxWidthPx * 0.08f
        val yOffset = sin(angle.value) * maxHeightPx * 0.08f
        // Background - first layer
        Image(
            painter = rememberAsyncImagePainter(
                ImageRequest
                    .Builder(LocalContext.current)
                    .data("https://images.unsplash.com/photo-1637532766937-0504f310bd6e?w=2000&q=99")
                    .crossfade(true)
                    .build()
            ),
            contentDescription = "Background",
            modifier = Modifier
                .fillMaxSize()
                .scale(1.2f)
                .graphicsLayer {
                    translationX = xOffset
                    translationY = yOffset
                },
            contentScale = ContentScale.Crop
        )

        // Quick-list layer
        AnimatedVisibility(
            visible = !isAppListVisible,
            enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut()
        ) {
            QuickListOverlay(apps = quickApps, onSwipeUp = { isAppListVisible = true })
        }

        // app list - second layer
        AnimatedVisibility(
            visible = isAppListVisible,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
        ) {
            AppListOverlay(apps = primaryApps, onSwipeDown = { isAppListVisible = false })
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
    val sidePadding = 18.dp
    val topPadding = 70.dp
    val smallIconSize = 45.dp
    val largeIconSize = smallIconSize + 30.dp
    val minimumGap = 6.dp

    val totalIcons = apps.size
    if (totalIcons == 0) return

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val width = with(density) { maxWidth.toPx() }
        val height = with(density) { maxHeight.toPx() }

        val sidePaddingPx = with(density) { sidePadding.toPx() }
        val topPaddingPx = with(density) { topPadding.toPx() }
        val smallIconPx = with(density) { smallIconSize.toPx() }
        val largeIconPx = with(density) { largeIconSize.toPx() }
        val minimumGapPx = with(density) { minimumGap.toPx() }

        val arcRadius = width / 2f - sidePaddingPx - smallIconPx / 2f
        val numTopIcons =
            2 * floor(PI / (4 * asin((smallIconPx / 2 + minimumGapPx / 2) / (arcRadius - smallIconPx / 2)))).toInt() + 1

        val arcCenterY = topPaddingPx + arcRadius

        val verticalSpacingPx =
            2 * (2 * (arcRadius - smallIconPx / 2) * sin(PI / (2 * (numTopIcons - 1))).toFloat() - smallIconPx).absoluteValue
        // Calculate the actual angular spacing based on the final number of top icons
        val angularSpacingRad = PI / (numTopIcons - 1)

        val sideColumnY = arcCenterY + smallIconPx / 2
        val availableHeight = height - sideColumnY
        val iconWithSpace = smallIconPx + verticalSpacingPx
        val numSideIcons = ceil(availableHeight / iconWithSpace).toInt()

        if (numSideIcons <= 0 || numTopIcons <= 1) {
            // Not enough space to draw a meaningful shape
            return@BoxWithConstraints
        }

        val numVisibleIcons = numSideIcons * 2 + numTopIcons + 1

        val smoothScrollIndex = scrollOffset / 20f
        val baseScrollIndex = floor(smoothScrollIndex)
        val scrollFraction = smoothScrollIndex - baseScrollIndex
        val intScrollIndex = baseScrollIndex.toInt()
        val startIndex = (intScrollIndex - numVisibleIcons / 2 + totalIcons) % totalIcons

        fun getPositionForSlot(slot: Int, center: Int): Pair<Float, Float> {
            return when {
                slot < numSideIcons -> {
                    // Left side (bottom to top)
                    val xPos = sidePaddingPx
                    val yPos =
                        sideColumnY + (numSideIcons - 1) * iconWithSpace - (slot - 1) * verticalSpacingPx - slot * smallIconPx
                    Pair(xPos, yPos)
                }

                slot < numSideIcons + numTopIcons -> {
                    // Top arc
                    val arcIndex = slot - numSideIcons
                    val angle = PI - arcIndex * angularSpacingRad
                    var xPos = width / 2 - smallIconPx / 2 + arcRadius * cos(angle).toFloat()
                    var yPos = arcCenterY - arcRadius * sin(angle).toFloat() - smallIconPx / 2
                    if (slot == center) {
                        xPos = xPos + smallIconPx / 2 - largeIconPx / 2
                        yPos = yPos + smallIconPx / 2 - largeIconPx / 2
                    }
                    Pair(xPos, yPos)
                }

                else -> {
                    // Right side (bottom to top)
                    val sideIndex = slot - numSideIcons - numTopIcons
                    val xPos = width - sidePaddingPx - smallIconPx
                    val yPos =
                        sideColumnY + verticalSpacingPx + sideIndex * verticalSpacingPx + sideIndex * smallIconPx
                    Pair(xPos, yPos)
                }
            }
        }

        val centerSlot = numSideIcons + numTopIcons / 2

        for (i in 0 until numVisibleIcons) {
            val appIndex = (startIndex + i + totalIcons) % totalIcons

            val posCurrent = getPositionForSlot(i, centerSlot)
            val posPrev = getPositionForSlot(i - 1, centerSlot)

            val x = lerp(posCurrent.first, posPrev.first, scrollFraction)
            val y = lerp(posCurrent.second, posPrev.second, scrollFraction)

            val sizeCurrent = if (i == centerSlot) largeIconSize else smallIconSize
            val sizePrev = if ((i - 1) == centerSlot) largeIconSize else smallIconSize
            val size = lerp(sizeCurrent.value, sizePrev.value, scrollFraction).dp
            if (size > 55.dp) {
                updateCenterIndex(appIndex)
                val sizePx = with(density) { size.toPx() }
                updateCenterIconGeom(x + sizePx / 2, y + sizePx / 2, sizePx)
            }
            key(apps[appIndex].packageName) {
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
fun AppIcon(app: AppInfo, x: Dp, y: Dp, size: Dp, clickable: Boolean = false) {
    val context = LocalContext.current
    val packageManager = context.packageManager
    Box(
        modifier = Modifier
            .offset(x = x, y = y)
            .size(size)
            .clip(CircleShape)
            .background(Color.White)
            .clickable {
                val launchIntent =
                    packageManager.getLaunchIntentForPackage(app.packageName)
                if (clickable) context.startActivity(launchIntent)
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

fun filterStandardApps(allPackageNames: Set<String>): Set<String> {
    val standardAppPackageNames = setOf(
        // Telephony/Dialer
        "com.android.dialer",
        "com.android.phone",
        "com.android.server.telecom",
        "com.android.providers.telephony",
        "com.google.android.dialer",
        "com.google.android.apps.messaging",
        "com.samsung.android.dialer",
        "com.samsung.android.contacts",
        "com.samsung.android.app.telephonyui",
        "com.miui.dialer",
        "com.android.contacts", // Xiaomi
        "com.android.mms",      // Xiaomi

        // Camera
        "com.android.camera",
        "com.android.camera2",
        "com.google.android.camera",
        "com.sec.android.app.camera",
        "com.samsung.android.camera.internal",
        "com.miui.camera",

        // Gallery
        "com.android.gallery3d",
        "com.android.gallery",
        "com.google.android.apps.photos",
        "com.sec.android.gallery3d",
        "com.samsung.android.gallery",
        "com.miui.gallery"
        // Add more as you discover them
    )

    return allPackageNames.filter { packageName ->
        standardAppPackageNames.contains(packageName)
    }.toSet()
}

