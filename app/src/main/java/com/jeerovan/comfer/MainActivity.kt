package com.jeerovan.comfer

import android.annotation.SuppressLint
import android.app.ActivityOptions
import android.app.WallpaperManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Resources
import android.graphics.BitmapFactory
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.provider.AlarmClock
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationEndReason
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import coil.Coil
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import coil.request.ImageResult
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import java.io.File
import java.io.FileOutputStream
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch


data class BatteryState(val level: Int, val isCharging: Boolean)

class MainActivity : ComponentActivity() {
    private val viewModel: AppInfoViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Do nothing because this is a launcher screen
            }
        })

        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
        windowInsetsController.hide(WindowInsetsCompat.Type.navigationBars())

        setContent {
            LauncherScreen(viewModel)
        }

        com.jeerovan.comfer.utils.GuideUtil.showGuide(
            activity = this,
            keyword = "main_screen",
            title = "Welcome to Comfer Launcher!",
            steps = listOf(
                "Swipe up to see your full app list.",
                "Swipe down from the app list to return here.",
                "Swipe left or right on the bottom half of the screen for custom shortcuts.",
                "Long press the screen to open settings."
            )
        )
    }

    override fun onResume() {
        super.onResume()
        viewModel.loadAppLists()
    }
}

@Composable
fun rememberBatteryState(): State<BatteryState> {
    val context = LocalContext.current
    val batteryState = remember { mutableStateOf(BatteryState(-1, false)) }

    DisposableEffect(context) {
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
                val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
                val batteryLevel = if (level != -1 && scale != -1) {
                    (level * 100 / scale.toFloat()).toInt()
                } else {
                    -1
                }

                val status: Int = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
                val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                        status == BatteryManager.BATTERY_STATUS_FULL

                batteryState.value = BatteryState(batteryLevel, isCharging)
            }
        }
        context.registerReceiver(receiver, filter)
        onDispose {
            context.unregisterReceiver(receiver)
        }
    }
    return batteryState
}


@Composable
fun BatteryStatus() {
    val batteryState by rememberBatteryState()
    val batteryLevel = batteryState.level
    val isCharging = batteryState.isCharging

    val isLow = batteryLevel < 10
    val color = if (isLow) Color.Red else Color.White

    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(24.dp, 12.dp)
                .padding(end = 4.dp)
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val strokeWidth = 2.dp.toPx()
                // Battery body
                drawRoundRect(
                    color = Color.White,
                    size = Size(size.width - strokeWidth, size.height),
                    style = Stroke(width = strokeWidth),
                    cornerRadius = CornerRadius(2.dp.toPx())
                )
                // Battery terminal
                drawRoundRect(
                    color = Color.White,
                    topLeft = Offset(size.width - strokeWidth, size.height / 4),
                    size = Size(strokeWidth, size.height / 2),
                    style = Fill
                )

                if (batteryLevel > 0) {
                    // Battery level
                    val levelWidth = (size.width - strokeWidth * 3) * (batteryLevel / 100f)
                    drawRoundRect(
                        color = color,
                        topLeft = Offset(strokeWidth * 1.5f, strokeWidth * 1.5f),
                        size = Size(levelWidth, size.height - strokeWidth * 3),
                        cornerRadius = CornerRadius(1.dp.toPx())
                    )
                }

                if (isCharging) {
                    val path = androidx.compose.ui.graphics.Path().apply {
                        val w = size.width
                        val h = size.height
                        moveTo(w * 0.7f, h * 0.15f)
                        lineTo(w * 0.4f, h * 0.6f)
                        lineTo(w * 0.55f, h * 0.6f)
                        lineTo(w * 0.3f, h * 0.85f)
                        lineTo(w * 0.6f, h * 0.4f)
                        lineTo(w * 0.45f, h * 0.4f)
                        close()
                    }
                    drawPath(path, color = Color.Red)
                }
            }
        }
        if (batteryLevel > 0) {
            Text(
                text = "$batteryLevel%",
                color = color,
                fontSize = 16.sp,
            )
        }
    }
}

@Composable
fun QuickListOverlay(apps: List<AppInfo>, onSwipeUp: () -> Unit) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    var iconSize by remember { mutableStateOf(48.dp) }
    var isDefault by remember { mutableStateOf(false) }

    fun isDefaultLauncher(): Boolean {
        val intent = Intent(Intent.ACTION_MAIN)
        intent.addCategory(Intent.CATEGORY_HOME)
        val resolveInfo = context.packageManager.resolveActivity(intent, 0)
        return resolveInfo?.activityInfo?.packageName == context.packageName
    }

    fun openDefaultLauncherSettings() {
        val intent = Intent(Settings.ACTION_HOME_SETTINGS)
        context.startActivity(intent)
    }

    LaunchedEffect(Unit) {
        isDefault = isDefaultLauncher()
    }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                iconSize = PreferenceManager.getIconSize(context).dp
                isDefault = isDefaultLauncher()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                //.border(1.dp,color = Color.Red)
                .padding(top=40.dp,start=20.dp,end=20.dp, bottom = 40.dp)
                .clickable {
                    val intent = Intent(AlarmClock.ACTION_SHOW_ALARMS)
                    if (intent.resolveActivity(context.packageManager) != null) {
                        context.startActivity(intent)
                    }
                },
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = date,
                    color = Color.White,
                    fontSize = 20.sp,
                    modifier = Modifier.padding(end = 8.dp)
                )
                BatteryStatus()
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                //.border(1.dp,color=Color.Green)
                .fillMaxWidth()
                .height(LocalConfiguration.current.screenHeightDp.dp / 2 - 40.dp)
                .pointerInput(Unit) {
                    val packageManager = context.packageManager
                    var totalDragOffset = Offset.Zero
                    detectDragGestures(
                        onDragStart = {
                            totalDragOffset = Offset.Zero
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            totalDragOffset += dragAmount
                        },
                        onDragEnd = {
                            val swipeThreshold = 50f
                            val (x, y) = totalDragOffset
                            if (x.absoluteValue > y.absoluteValue) {
                                if (x.absoluteValue > swipeThreshold) {
                                    if (x > 0) {
                                        val swipeRightPackage =
                                            PreferenceManager.getSwipeApp(context, "right")
                                        if (swipeRightPackage != null) {
                                            val launchIntent =
                                                packageManager.getLaunchIntentForPackage(
                                                    swipeRightPackage
                                                )
                                            context.startActivity(launchIntent)
                                        }
                                    } else {
                                        val swipeLeftPackage =
                                            PreferenceManager.getSwipeApp(context, "left")
                                        if (swipeLeftPackage != null) {
                                            val launchIntent =
                                                packageManager.getLaunchIntentForPackage(
                                                    swipeLeftPackage
                                                )
                                            context.startActivity(launchIntent)
                                        }
                                    }
                                }
                            } else {
                                if (y.absoluteValue > swipeThreshold) {
                                    if (y > 0) {
                                        try {
                                            @SuppressLint("WrongConstant")
                                            val statusBarService =
                                                context.getSystemService("statusbar")
                                            val statusBarManager =
                                                Class.forName("android.app.StatusBarManager")
                                            val method =
                                                statusBarManager.getMethod("expandNotificationsPanel")
                                            method.invoke(statusBarService)
                                        } catch (e: Exception) {
                                            e.printStackTrace()
                                        }
                                    } else {
                                        onSwipeUp()
                                    }
                                }
                            }
                        }
                    )
                },
            contentAlignment = Alignment.BottomCenter
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.padding(bottom = 64.dp)
            ) {
                if (!isDefault) {
                    OutlinedButton (onClick = { openDefaultLauncherSettings()},
                        border = null,
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = Color.Black.copy(alpha = 0.5f) // Text color
                        )
                    ) {
                        Text("Set as default launcher",
                            fontSize = 18.sp,
                            color = Color.White)
                    }
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    apps.forEach { app ->
                        val packageManager = context.packageManager
                        Box(
                            modifier = Modifier
                                .size(iconSize)
                                .clip(CircleShape)
                                .background(Color.White)
                                .pointerInput(Unit) {
                                    detectTapGestures(
                                        onTap = {
                                            val launchIntent =
                                                packageManager.getLaunchIntentForPackage(app.packageName)
                                            context.startActivity(launchIntent)
                                        },
                                        onLongPress = {
                                            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                            val intent =
                                                android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                                            intent.data = "package:${app.packageName}".toUri()
                                            context.startActivity(intent)
                                        }
                                    )
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
}

@Composable
fun AppListOverlay(apps: List<AppInfo>, onSwipeDown: () -> Unit) {
    val context = LocalContext.current
    val view = LocalView.current
    val packageManager = context.packageManager
    val scope = rememberCoroutineScope()
    var iconSize by remember { mutableStateOf(48.dp) }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                iconSize = PreferenceManager.getIconSize(context).dp
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

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
                    }/*,
                    onDragCancel = {
                        velocityTracker.resetTracking()
                    }*/
                )
            }
    ) {
        if (apps.isNotEmpty()) {
            UshapedAppList(
                apps = apps,
                updateCenterIndex = { centerAppIndex = it },
                scrollOffset = -scrollAnimatable.value,
                iconSize = iconSize,
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
fun LauncherScreen(viewModel: AppInfoViewModel) {
    val context = LocalContext.current
    var isAppListVisible by remember { mutableStateOf(false) }
    var backgroundImageUri by remember { mutableStateOf<String?>(null) }
    var wallpaperMotionEnabled by remember { mutableStateOf(true) }

    val appInfoUiState by viewModel.uiState.collectAsState()
    val quickApps = appInfoUiState.quickApps
    val primaryApps = appInfoUiState.primaryApps

    val haptic = LocalHapticFeedback.current

    LaunchedEffect(Unit) {
        wallpaperMotionEnabled = PreferenceManager.getWallpaperMotion(context)
        val cachedImagePath = PreferenceManager.getBackgroundImagePath(context)
        if (cachedImagePath != null && File(cachedImagePath).exists()) {
            backgroundImageUri = cachedImagePath
        } else {
            launch {
                val imageUrl = "https://images.unsplash.com/photo-1637532766937-0504f310bd6e?w=2000&q=99"
                val request = ImageRequest.Builder(context)
                    .data(imageUrl)
                    .build()
                val result = (Coil.imageLoader(context) as coil.ImageLoader).execute(request)
                if (result is ImageResult) {
                    val drawable = result.drawable
                    if (drawable != null) {
                        val file = File(context.cacheDir, "background.jpg")
                        val stream = FileOutputStream(file)
                        drawable.toBitmap().compress(android.graphics.Bitmap.CompressFormat.JPEG, 100, stream)
                        stream.close()
                        PreferenceManager.setBackgroundImagePath(context, file.absolutePath)
                        backgroundImageUri = file.absolutePath

                        // Set system wallpaper
                        val wallpaperManager = WallpaperManager.getInstance(context)
                        val bitmap = BitmapFactory.decodeFile(file.absolutePath)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                            wallpaperManager.setBitmap(bitmap, null, true, WallpaperManager.FLAG_SYSTEM)
                        } else {
                            wallpaperManager.setBitmap(bitmap)
                        }
                    }
                }
            }
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(
                    onLongPress = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        context.startActivity(Intent(context, SettingsActivity::class.java))
                    })
            }) {
        val maxWidthPx = with(LocalDensity.current) { maxWidth.toPx() }
        val maxHeightPx = with(LocalDensity.current) { maxHeight.toPx() }

        val angle = remember { Animatable(0f) }

        val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
        var isScreenVisible by remember { mutableStateOf(true) }

        DisposableEffect(lifecycleOwner) {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    isScreenVisible = true
                    // Reload the setting when the app resumes
                    wallpaperMotionEnabled = PreferenceManager.getWallpaperMotion(context)
                } else if (event == Lifecycle.Event.ON_PAUSE) {
                    isScreenVisible = false
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
            }
        }

        LaunchedEffect(isScreenVisible, wallpaperMotionEnabled) {
            if (isScreenVisible && wallpaperMotionEnabled) {
                var lastFrameTime = withFrameNanos { it }
                while (isActive) {
                    val frameTime = withFrameNanos { it }
                    val deltaTimeNanos = frameTime - lastFrameTime
                    val deltaTimeSeconds = deltaTimeNanos / 1_000_000_000f
                    val angleDelta = deltaTimeSeconds * (2f * PI.toFloat() / 100f)
                    val newAngle = (angle.value + angleDelta) % (2f * PI.toFloat())
                    angle.snapTo(newAngle)
                    lastFrameTime = frameTime
                }
            } /*else {
                angle.snapTo(0f)
            }*/
        }

        val xOffset = cos(angle.value) * maxWidthPx * 0.08f
        val yOffset = sin(angle.value) * maxHeightPx * 0.08f

        if (backgroundImageUri != null) {
            Image(
                painter = rememberAsyncImagePainter(
                    ImageRequest
                        .Builder(LocalContext.current)
                        .data(backgroundImageUri)
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
        }

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
    iconSize: Dp,
    updateCenterIconGeom: (x: Float, y: Float, size: Float) -> Unit
) {
    val sidePadding = 18.dp
    val topPadding = 70.dp
    val smallIconSize = iconSize
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
            if (size > largeIconSize - 10.dp) {
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
fun AppIcon(app: AppInfo, x: Dp, y: Dp, size: Dp) {
    val context = LocalContext.current
    val packageManager = context.packageManager
    val haptic = LocalHapticFeedback.current
    Box(
        modifier = Modifier
            .offset(x = x, y = y)
            .size(size)
            .clip(CircleShape)
            .background(Color.White)
            .pointerInput(Unit){
                detectTapGestures (
                    onTap = {
                        val launchIntent =
                            packageManager.getLaunchIntentForPackage(app.packageName)
                        context.startActivity(launchIntent)
                    },
                    onLongPress = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                        intent.data = "package:${app.packageName}".toUri()
                        context.startActivity(intent)
                    }
                )
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

