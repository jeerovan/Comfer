package com.jeerovan.comfer

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.jeerovan.comfer.ui.theme.ComferTheme
import kotlinx.coroutines.launch
import kotlin.getValue
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.PI

class GestureShortcutActivity : ComponentActivity() {

    private val settingsViewModel: SettingsViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        // Only set colors for Android 14 and below to avoid deprecation warnings
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            window.statusBarColor = android.graphics.Color.TRANSPARENT
            window.navigationBarColor = android.graphics.Color.TRANSPARENT
        }
        // Handle display cutout
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        setContent {
            ComferTheme {
                Surface(
                    modifier = Modifier
                        .fillMaxSize(),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)
                ) {
                    GestureShortcutScreen(settingsViewModel)
                }
            }
        }
    }
    override fun onResume() {
        super.onResume()
        val logger = LoggerManager(applicationContext)
        logger.setLog("SettingsActivity","Resumed")
        lifecycleScope.launch {
            settingsViewModel.loadSettings()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GestureShortcutScreen(settingsViewModel: SettingsViewModel) {
    val context = LocalContext.current
    val iconSize = PreferenceManager.getIconSize(context).dp
    val iconShape = PreferenceManager.getIconShape(context)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 24.dp)
            .windowInsetsPadding(WindowInsets.navigationBars)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Top App Bar
            TopAppBar(
                title = { Text("Gesture Shortcuts") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = MaterialTheme.colorScheme.primary
                )
            )
            Text("Tap icon to select app",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.offset(x=20.dp))
            // Main gesture display area
            Box(
                modifier = Modifier
                    .fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                val circleRadius = 40.dp
                val lineLength = 100.dp
                GestureCanvas(
                    circleRadius = circleRadius,
                    lineLength = lineLength
                )
                AppsLayout(
                    settingsViewModel,
                    circleRadius,
                    lineLength,
                    iconSize,
                    iconShape)
            }
        }

    }
}

enum class GestureType {
    L_TOP_RIGHT,
    L_TOP_LEFT,
    L_BOTTOM_LEFT,
    L_BOTTOM_RIGHT
}

@Composable
fun GestureCanvas(
    circleRadius: Dp,
    lineLength: Dp
) {
    val density = LocalDensity.current
    val circleRadius = with(density) { circleRadius.toPx() }
    val lineLength = with(density) { lineLength.toPx() }
    val strokeWidth = with(density) { 2.dp.toPx() }
    val dotRadius = with(density) { 7.dp.toPx() }

    // Infinite transition for animations
    val infiniteTransition = rememberInfiniteTransition(label = "gestureAnimation")

    // Circular gesture animation (0 to 2 for clockwise then anticlockwise)
    val circularProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "circularProgress"
    )

    // L-shape animations for each quadrant
    val lProgressTopRight by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(
            animation = tween(3500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "lProgressTopRight"
    )

    val lProgressTopLeft by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(
            animation = tween(3500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "lProgressTopLeft"
    )

    val lProgressBottomLeft by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(
            animation = tween(3500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "lProgressBottomLeft"
    )

    val lProgressBottomRight by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(
            animation = tween(3500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "lProgressBottomRight"
    )

    val primaryColor = MaterialTheme.colorScheme.primary
    val accentColor = MaterialTheme.colorScheme.secondary

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null,
                onClick = {}
            )
    ) {
        val centerX = size.width / 2
        val centerY = size.height / 2

        // Draw central circle
        drawCircle(
            color = primaryColor,
            radius = circleRadius,
            center = Offset(centerX, centerY),
            style = Stroke(width = strokeWidth)
        )

        // Animated dot on circular border
        val circularAngle = if (circularProgress <= 1f) {
            // Clockwise: Apply ease-in-out for smooth start/end
            val easedFraction = FastOutSlowInEasing.transform(circularProgress)
            easedFraction * 2 * PI.toFloat()
        } else {
            // Anticlockwise: Apply ease-in-out for smooth start/end
            val normalizedProgress = circularProgress - 1f
            val easedFraction = FastOutSlowInEasing.transform(normalizedProgress)
            (2f - easedFraction) * 2 * PI.toFloat()
        }

        val circularDotX = centerX + circleRadius * cos(circularAngle - PI.toFloat() / 2)
        val circularDotY = centerY + circleRadius * sin(circularAngle - PI.toFloat() / 2)
        drawCircle(
            color = accentColor,
            radius = dotRadius,
            center = Offset(circularDotX, circularDotY)
        )

        // Draw L-shapes and animated dots
        // TOP RIGHT L-shape (horizontal right, then vertical down)
        drawLShape(
            centerX = centerX,
            centerY = centerY,
            circleRadius = circleRadius,
            lineLength = lineLength,
            strokeWidth = strokeWidth,
            color = primaryColor,
            quadrant = GestureType.L_TOP_RIGHT,
            progress = lProgressTopRight,
            dotRadius = dotRadius,
            dotColor = accentColor
        )

        // TOP LEFT L-shape (vertical up, then horizontal left)
        drawLShape(
            centerX = centerX,
            centerY = centerY,
            circleRadius = circleRadius,
            lineLength = lineLength,
            strokeWidth = strokeWidth,
            color = primaryColor,
            quadrant = GestureType.L_TOP_LEFT,
            progress = lProgressTopLeft,
            dotRadius = dotRadius,
            dotColor = accentColor
        )

        // BOTTOM LEFT L-shape (horizontal left, then vertical down)
        drawLShape(
            centerX = centerX,
            centerY = centerY,
            circleRadius = circleRadius,
            lineLength = lineLength,
            strokeWidth = strokeWidth,
            color = primaryColor,
            quadrant = GestureType.L_BOTTOM_LEFT,
            progress = lProgressBottomLeft,
            dotRadius = dotRadius,
            dotColor = accentColor
        )

        // BOTTOM RIGHT L-shape (vertical down, then horizontal right)
        drawLShape(
            centerX = centerX,
            centerY = centerY,
            circleRadius = circleRadius,
            lineLength = lineLength,
            strokeWidth = strokeWidth,
            color = primaryColor,
            quadrant = GestureType.L_BOTTOM_RIGHT,
            progress = lProgressBottomRight,
            dotRadius = dotRadius,
            dotColor = accentColor
        )
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawLShape(
    centerX: Float,
    centerY: Float,
    circleRadius: Float,
    lineLength: Float,
    strokeWidth: Float,
    color: Color,
    quadrant: GestureType,
    progress: Float,
    dotRadius: Float,
    dotColor: Color
) {
    when (quadrant) {
        GestureType.L_TOP_RIGHT -> {
            // Horizontal line (right side)
            val hStartX = centerX + circleRadius + lineLength
            val hEndX = hStartX - lineLength
            val hY = centerY - circleRadius
            drawLine(
                color = color,
                start = Offset(hStartX, hY),
                end = Offset(hEndX, hY),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round
            )

            // Vertical line (down from horizontal end)
            val vX = hEndX
            val vStartY = hY
            val vEndY = vStartY - lineLength
            drawLine(
                color = color,
                start = Offset(vX, vStartY),
                end = Offset(vX, vEndY),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round
            )

            // Animated dot: horizontal first, then vertical
            if (progress <= 1f) {
                val dotProgress = progress
                if (dotProgress <= 0.5f) {
                    // Moving horizontally - start fast, end slow
                    val normalizedProgress = dotProgress * 2 // 0.0 to 1.0
                    val easedProgress = EaseOut.transform(normalizedProgress)
                    val dotX = hStartX + (hEndX - hStartX) * easedProgress
                    drawCircle(color = dotColor, radius = dotRadius, center = Offset(dotX, hY))
                } else {
                    // Moving vertically - start slow, end fast
                    val normalizedProgress = (dotProgress - 0.5f) * 2 // 0.0 to 1.0
                    val easedProgress = EaseIn.transform(normalizedProgress)
                    val dotY = vStartY + (vEndY - vStartY) * easedProgress
                    drawCircle(color = dotColor, radius = dotRadius, center = Offset(vX, dotY))
                }
            } else {
                // Reverse: vertical first, then horizontal
                val dotProgress = progress - 1f
                if (dotProgress <= 0.5f) {
                    // Moving vertically upward - start fast, end slow
                    val normalizedProgress = dotProgress * 2 // 0.0 to 1.0
                    val easedProgress = EaseOut.transform(normalizedProgress)
                    val dotY = vEndY - (vEndY - vStartY) * easedProgress
                    drawCircle(color = dotColor, radius = dotRadius, center = Offset(vX, dotY))
                } else {
                    // Moving horizontally leftward - start slow, end fast
                    val normalizedProgress = (dotProgress - 0.5f) * 2 // 0.0 to 1.0
                    val easedProgress = EaseIn.transform(normalizedProgress)
                    val dotX = hEndX - (hEndX - hStartX) * easedProgress
                    drawCircle(color = dotColor, radius = dotRadius, center = Offset(dotX, hY))
                }
            }
        }

        GestureType.L_TOP_LEFT -> {
            // Vertical line (going toward circle from top)
            val vX = centerX - circleRadius
            val vStartY = centerY - circleRadius - lineLength
            val vEndY = vStartY + lineLength  // = centerY - circleRadius (CORNER near circle)
            drawLine(
                color = color,
                start = Offset(vX, vStartY),
                end = Offset(vX, vEndY),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round
            )

            // Horizontal line (going away left from corner)
            val hStartX = vX  // = centerX - circleRadius (CORNER - same X as vertical)
            val hEndX = hStartX - lineLength
            val hY = vEndY  // = centerY - circleRadius (CORNER - same Y as vertical end)
            drawLine(
                color = color,
                start = Offset(hStartX, hY),
                end = Offset(hEndX, hY),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round
            )

            // Animated dot: vertical first, then horizontal
            if (progress <= 1f) {
                val dotProgress = progress
                if (dotProgress <= 0.5f) {
                    // Moving vertically downward (toward circle) - start fast, end slow
                    val normalizedProgress = dotProgress * 2 // 0.0 to 1.0
                    val easedProgress = EaseOut.transform(normalizedProgress)
                    val dotY = vStartY + (vEndY - vStartY) * easedProgress
                    drawCircle(color = dotColor, radius = dotRadius, center = Offset(vX, dotY))
                } else {
                    // Moving horizontally leftward (away from circle) - start slow, end fast
                    val normalizedProgress = (dotProgress - 0.5f) * 2 // 0.0 to 1.0
                    val easedProgress = EaseIn.transform(normalizedProgress)
                    val dotX = hStartX + (hEndX - hStartX) * easedProgress
                    drawCircle(color = dotColor, radius = dotRadius, center = Offset(dotX, hY))
                }
            } else {
                // Reverse: horizontal first, then vertical
                val dotProgress = progress - 1f
                if (dotProgress <= 0.5f) {
                    // Moving horizontally rightward (toward circle) - start fast, end slow
                    val normalizedProgress = dotProgress * 2 // 0.0 to 1.0
                    val easedProgress = EaseOut.transform(normalizedProgress)
                    val dotX = hEndX - (hEndX - hStartX) * easedProgress
                    drawCircle(color = dotColor, radius = dotRadius, center = Offset(dotX, hY))
                } else {
                    // Moving vertically upward (away from circle) - start slow, end fast
                    val normalizedProgress = (dotProgress - 0.5f) * 2 // 0.0 to 1.0
                    val easedProgress = EaseIn.transform(normalizedProgress)
                    val dotY = vEndY - (vEndY - vStartY) * easedProgress
                    drawCircle(color = dotColor, radius = dotRadius, center = Offset(vX, dotY))
                }
            }
        }

        GestureType.L_BOTTOM_LEFT -> {
            // Horizontal line (going toward circle from left)
            val hStartX = centerX - circleRadius - lineLength
            val hEndX = hStartX + lineLength  // = centerX - circleRadius (CORNER near circle)
            val hY = centerY + circleRadius
            drawLine(
                color = color,
                start = Offset(hStartX, hY),
                end = Offset(hEndX, hY),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round
            )

            // Vertical line (going away down from corner)
            val vX = hEndX  // = centerX - circleRadius (CORNER - same X as horizontal end)
            val vStartY = hY  // = centerY + circleRadius (CORNER - same Y as horizontal)
            val vEndY = vStartY + lineLength
            drawLine(
                color = color,
                start = Offset(vX, vStartY),
                end = Offset(vX, vEndY),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round
            )

            // Animated dot: horizontal first, then vertical
            if (progress <= 1f) {
                val dotProgress = progress
                if (dotProgress <= 0.5f) {
                    // Moving horizontally rightward (toward circle) - start fast, end slow
                    val normalizedProgress = dotProgress * 2 // 0.0 to 1.0
                    val easedProgress = EaseOut.transform(normalizedProgress)
                    val dotX = hStartX + (hEndX - hStartX) * easedProgress
                    drawCircle(color = dotColor, radius = dotRadius, center = Offset(dotX, hY))
                } else {
                    // Moving vertically downward (away from circle) - start slow, end fast
                    val normalizedProgress = (dotProgress - 0.5f) * 2 // 0.0 to 1.0
                    val easedProgress = EaseIn.transform(normalizedProgress)
                    val dotY = vStartY + (vEndY - vStartY) * easedProgress
                    drawCircle(color = dotColor, radius = dotRadius, center = Offset(vX, dotY))
                }
            } else {
                // Reverse: vertical first, then horizontal
                val dotProgress = progress - 1f
                if (dotProgress <= 0.5f) {
                    // Moving vertically upward (toward circle) - start fast, end slow
                    val normalizedProgress = dotProgress * 2 // 0.0 to 1.0
                    val easedProgress = EaseOut.transform(normalizedProgress)
                    val dotY = vEndY - (vEndY - vStartY) * easedProgress
                    drawCircle(color = dotColor, radius = dotRadius, center = Offset(vX, dotY))
                } else {
                    // Moving horizontally leftward (away from circle) - start slow, end fast
                    val normalizedProgress = (dotProgress - 0.5f) * 2 // 0.0 to 1.0
                    val easedProgress = EaseIn.transform(normalizedProgress)
                    val dotX = hEndX - (hEndX - hStartX) * easedProgress
                    drawCircle(color = dotColor, radius = dotRadius, center = Offset(dotX, hY))
                }
            }
        }

        GestureType.L_BOTTOM_RIGHT -> {
            // Vertical line (going toward circle from bottom)
            val vX = centerX + circleRadius
            val vStartY = centerY + circleRadius + lineLength
            val vEndY = vStartY - lineLength  // = centerY + circleRadius (CORNER near circle)
            drawLine(
                color = color,
                start = Offset(vX, vStartY),
                end = Offset(vX, vEndY),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round
            )

            // Horizontal line (going away right from corner)
            val hStartX = vX  // = centerX + circleRadius (CORNER - same X as vertical)
            val hEndX = hStartX + lineLength
            val hY = vEndY  // = centerY + circleRadius (CORNER - same Y as vertical end)
            drawLine(
                color = color,
                start = Offset(hStartX, hY),
                end = Offset(hEndX, hY),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round
            )

            // Animated dot: vertical first, then horizontal
            if (progress <= 1f) {
                val dotProgress = progress
                if (dotProgress <= 0.5f) {
                    // Moving vertically upward (toward circle) - start fast, end slow
                    val normalizedProgress = dotProgress * 2 // 0.0 to 1.0
                    val easedProgress = EaseOut.transform(normalizedProgress)
                    val dotY = vStartY + (vEndY - vStartY) * easedProgress
                    drawCircle(color = dotColor, radius = dotRadius, center = Offset(vX, dotY))
                } else {
                    // Moving horizontally rightward (away from circle) - start slow, end fast
                    val normalizedProgress = (dotProgress - 0.5f) * 2 // 0.0 to 1.0
                    val easedProgress = EaseIn.transform(normalizedProgress)
                    val dotX = hStartX + (hEndX - hStartX) * easedProgress
                    drawCircle(color = dotColor, radius = dotRadius, center = Offset(dotX, hY))
                }
            } else {
                // Reverse: horizontal first, then vertical
                val dotProgress = progress - 1f
                if (dotProgress <= 0.5f) {
                    // Moving horizontally leftward (toward circle) - start fast, end slow
                    val normalizedProgress = dotProgress * 2 // 0.0 to 1.0
                    val easedProgress = EaseOut.transform(normalizedProgress)
                    val dotX = hEndX - (hEndX - hStartX) * easedProgress
                    drawCircle(color = dotColor, radius = dotRadius, center = Offset(dotX, hY))
                } else {
                    // Moving vertically downward (away from circle) - start slow, end fast
                    val normalizedProgress = (dotProgress - 0.5f) * 2 // 0.0 to 1.0
                    val easedProgress = EaseIn.transform(normalizedProgress)
                    val dotY = vEndY - (vEndY - vStartY) * easedProgress
                    drawCircle(color = dotColor, radius = dotRadius, center = Offset(vX, dotY))
                }
            }
        }

    }
}

@Composable
fun AppsLayout(
    settingsViewModel: SettingsViewModel,
    circleRadius: Dp,
    lineLength: Dp,
    iconSize: Dp,
    iconShape: androidx.compose.ui.graphics.Shape
) {
    val context = LocalContext.current
    val packageManager = context.packageManager
    val settings by settingsViewModel.uiState.collectAsState()
    val patternApps = settings.patternApps
    val appSelectionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.let {
                val gesturePattern = it.getStringExtra("gesture_pattern")
                val packageName = it.getStringExtra("package_name")
                if (gesturePattern != null && packageName != null) {
                    settingsViewModel.setPatternApp(gesturePattern, packageName)
                }
            }
        }
    }
    fun selectSetApp(pattern:String){
        val intent = Intent(context, AppSelectionActivity::class.java).apply {
            putExtra("gesture_pattern", pattern)
        }
        appSelectionLauncher.launch(intent)
    }
    Box(modifier = Modifier.fillMaxSize()
    ) {
        // Center composable
        Box(modifier = Modifier
            .align(Alignment.Center)
            .clickable { selectSetApp("Center")}
        ){
            val centerApp = mapPackageNameToAppInfo(context,packageManager, patternApps["Center"])
            if(centerApp == null) {
                IconShapePreview(
                    shape = iconShape,
                    size = iconSize
                )
            } else {
                AppIcon(app = centerApp,iconSize=iconSize,shape=iconShape, notificationPackages = emptyList(), clickable = false)
            }
        }
        val offsetLength = (circleRadius + lineLength/2)
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .offset(x = offsetLength, y = offsetLength)
                .clickable { selectSetApp("BottomRight")},
        ){
            val bottomRightApp = mapPackageNameToAppInfo(context,packageManager, patternApps["BottomRight"])
            if(bottomRightApp == null) {
                IconShapePreview(
                    shape = iconShape,
                    size = iconSize
                )
            } else {
                AppIcon(app = bottomRightApp,iconSize=iconSize,shape=iconShape, notificationPackages = emptyList(), clickable = false)
            }
        }
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .offset(x = -offsetLength, y = offsetLength)
                .clickable { selectSetApp("BottomLeft")}
        ){
            val bottomLeftApp = mapPackageNameToAppInfo(context,packageManager, patternApps["BottomLeft"])
            if(bottomLeftApp == null) {
                IconShapePreview(
                    shape = iconShape,
                    size = iconSize
                )
            } else {
                AppIcon(app = bottomLeftApp,iconSize=iconSize,shape=iconShape, notificationPackages = emptyList(), clickable = false)
            }
        }
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .offset(x = offsetLength, y = -offsetLength)
                .clickable { selectSetApp("TopRight")}
        ){
            val topRightApp = mapPackageNameToAppInfo(context,packageManager, patternApps["TopRight"])
            if(topRightApp == null) {
                IconShapePreview(
                    shape = iconShape,
                    size = iconSize
                )
            } else {
                AppIcon(app = topRightApp,iconSize=iconSize,shape=iconShape, notificationPackages = emptyList(), clickable = false)
            }
        }
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .offset(x = -offsetLength, y = -offsetLength)
                .clickable { selectSetApp("TopLeft")}
        ){
            val topLeftApp = mapPackageNameToAppInfo(context,packageManager, patternApps["TopLeft"])
            if(topLeftApp == null) {
                IconShapePreview(
                    shape = iconShape,
                    size = iconSize
                )
            } else {
                AppIcon(app = topLeftApp,iconSize=iconSize,shape=iconShape, notificationPackages = emptyList(), clickable = false)
            }
        }
    }
}
