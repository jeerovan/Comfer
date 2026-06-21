package com.jeerovan.comfer

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.core.EaseIn
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.jeerovan.comfer.ui.theme.ComferTheme
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

class GestureShortcutActivity : AppCompatActivity() {

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
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)
                ) {
                    GestureShortcutScreen(settingsViewModel)
                }
            }
        }
    }
    override fun onResume() {
        super.onResume()
        lifecycleScope.launch {
            settingsViewModel.loadSettings()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GestureShortcutScreen(settingsViewModel: SettingsViewModel) {
    val settingsState by settingsViewModel.uiState.collectAsState()
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
                title = {
                    Row {
                        Text(stringResource(R.string.gesture_shortcuts_title))
                    }
                        },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = MaterialTheme.colorScheme.primary
                )
            )
            Text(stringResource(R.string.tap_icon_to_select_app),
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
                    settingsState,
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

private fun calculateCircularPositionAndRotation(
    progress: Float,
    centerX: Float,
    centerY: Float,
    circleRadius: Float
): Pair<Offset, Float> {
    val circularAngle = if (progress <= 1f) {
        val easedFraction = FastOutSlowInEasing.transform(progress)
        easedFraction * 2 * PI.toFloat()
    } else {
        val normalizedProgress = progress - 1f
        val easedFraction = FastOutSlowInEasing.transform(normalizedProgress)
        (2f - easedFraction) * 2 * PI.toFloat()
    }
    val x = centerX + circleRadius * cos(circularAngle - PI.toFloat() / 2)
    val y = centerY + circleRadius * sin(circularAngle - PI.toFloat() / 2)
    val rot = if (progress <= 1f) {
        (circularAngle * 180f / PI.toFloat()) + 90f
    } else {
        ((circularAngle + PI.toFloat()) * 180f / PI.toFloat()) + 90f
    }
    return Offset(x, y) to rot
}

private fun calculateLPositionAndRotation(
    progress: Float,
    quadrant: GestureType,
    centerX: Float,
    centerY: Float,
    circleRadius: Float,
    lineLength: Float
): Pair<Offset, Float> {
    return when (quadrant) {
        GestureType.L_TOP_RIGHT -> {
            val hStartX = centerX + circleRadius + lineLength
            val hEndX = hStartX - lineLength
            val hY = centerY - circleRadius
            val vX = hEndX
            val vStartY = hY
            val vEndY = vStartY - lineLength
            if (progress <= 1f) {
                val dotProgress = progress
                if (dotProgress <= 0.5f) {
                    val easedProgress = EaseOut.transform(dotProgress * 2)
                    Offset(hStartX + (hEndX - hStartX) * easedProgress, hY) to 270f
                } else {
                    val easedProgress = EaseIn.transform((dotProgress - 0.5f) * 2)
                    Offset(vX, vStartY + (vEndY - vStartY) * easedProgress) to 0f
                }
            } else {
                val dotProgress = progress - 1f
                if (dotProgress <= 0.5f) {
                    val easedProgress = EaseOut.transform(dotProgress * 2)
                    Offset(vX, vEndY - (vEndY - vStartY) * easedProgress) to 180f
                } else {
                    val easedProgress = EaseIn.transform((dotProgress - 0.5f) * 2)
                    Offset(hEndX - (hEndX - hStartX) * easedProgress, hY) to 90f
                }
            }
        }
        GestureType.L_TOP_LEFT -> {
            val vX = centerX - circleRadius
            val vStartY = centerY - circleRadius - lineLength
            val vEndY = vStartY + lineLength
            val hStartX = vX
            val hEndX = hStartX - lineLength
            val hY = vEndY
            if (progress <= 1f) {
                val dotProgress = progress
                if (dotProgress <= 0.5f) {
                    val easedProgress = EaseOut.transform(dotProgress * 2)
                    Offset(vX, vStartY + (vEndY - vStartY) * easedProgress) to 180f
                } else {
                    val easedProgress = EaseIn.transform((dotProgress - 0.5f) * 2)
                    Offset(hStartX + (hEndX - hStartX) * easedProgress, hY) to 270f
                }
            } else {
                val dotProgress = progress - 1f
                if (dotProgress <= 0.5f) {
                    val easedProgress = EaseOut.transform(dotProgress * 2)
                    Offset(hEndX - (hEndX - hStartX) * easedProgress, hY) to 90f
                } else {
                    val easedProgress = EaseIn.transform((dotProgress - 0.5f) * 2)
                    Offset(vX, vEndY - (vEndY - vStartY) * easedProgress) to 0f
                }
            }
        }
        GestureType.L_BOTTOM_LEFT -> {
            val hStartX = centerX - circleRadius - lineLength
            val hEndX = hStartX + lineLength
            val hY = centerY + circleRadius
            val vX = hEndX
            val vStartY = hY
            val vEndY = vStartY + lineLength
            if (progress <= 1f) {
                val dotProgress = progress
                if (dotProgress <= 0.5f) {
                    val easedProgress = EaseOut.transform(dotProgress * 2)
                    Offset(hStartX + (hEndX - hStartX) * easedProgress, hY) to 90f
                } else {
                    val easedProgress = EaseIn.transform((dotProgress - 0.5f) * 2)
                    Offset(vX, vStartY + (vEndY - vStartY) * easedProgress) to 180f
                }
            } else {
                val dotProgress = progress - 1f
                if (dotProgress <= 0.5f) {
                    val easedProgress = EaseOut.transform(dotProgress * 2)
                    Offset(vX, vEndY - (vEndY - vStartY) * easedProgress) to 0f
                } else {
                    val easedProgress = EaseIn.transform((dotProgress - 0.5f) * 2)
                    Offset(hEndX - (hEndX - hStartX) * easedProgress, hY) to 270f
                }
            }
        }
        GestureType.L_BOTTOM_RIGHT -> {
            val vX = centerX + circleRadius
            val vStartY = centerY + circleRadius + lineLength
            val vEndY = vStartY - lineLength
            val hStartX = vX
            val hEndX = hStartX + lineLength
            val hY = vEndY
            if (progress <= 1f) {
                val dotProgress = progress
                if (dotProgress <= 0.5f) {
                    val easedProgress = EaseOut.transform(dotProgress * 2)
                    Offset(vX, vStartY + (vEndY - vStartY) * easedProgress) to 0f
                } else {
                    val easedProgress = EaseIn.transform((dotProgress - 0.5f) * 2)
                    Offset(hStartX + (hEndX - hStartX) * easedProgress, hY) to 90f
                }
            } else {
                val dotProgress = progress - 1f
                if (dotProgress <= 0.5f) {
                    val easedProgress = EaseOut.transform(dotProgress * 2)
                    Offset(hEndX - (hEndX - hStartX) * easedProgress, hY) to 270f
                } else {
                    val easedProgress = EaseIn.transform((dotProgress - 0.5f) * 2)
                    Offset(vX, vEndY - (vEndY - vStartY) * easedProgress) to 180f
                }
            }
        }
    }
}


@Composable
fun HandIndicator(
    progress: Float,
    type: GestureType?,
    centerX: Float,
    centerY: Float,
    circleRadius: Float,
    lineLength: Float,
    density: Density,
    accentColor: Color
) {
    val handSize = with(density) { 24.dp.toPx() }
    val (position, rotation) = remember(progress, type, centerX, centerY, circleRadius, lineLength) {
        if (type == null) {
            calculateCircularPositionAndRotation(progress, centerX, centerY, circleRadius)
        } else {
            calculateLPositionAndRotation(progress, type, centerX, centerY, circleRadius, lineLength)
        }
    }

    Box(
        modifier = Modifier
            .offset { IntOffset((position.x - handSize / 2).roundToInt(), (position.y - handSize / 2).roundToInt()) }
    ) {
        Icon(
            imageVector = Icons.Filled.TouchApp,
            contentDescription = null,
            tint = accentColor,
            modifier = Modifier.size(with(density) { handSize.toDp() })
        )
    }
}

@Composable
fun GestureCanvas(
    circleRadius: Dp,
    lineLength: Dp
) {
    val density = LocalDensity.current
    val circleRadiusPx = with(density) { circleRadius.toPx() }
    val lineLengthPx = with(density) { lineLength.toPx() }
    val strokeWidth = with(density) { 2.dp.toPx() }
    val accentColor = MaterialTheme.colorScheme.secondary
    val primaryColor = MaterialTheme.colorScheme.primary

    val infiniteTransition = rememberInfiniteTransition(label = "gestureAnimation")

    val circularProgress by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 2f,
        animationSpec = infiniteRepeatable(tween(4000, easing = LinearEasing), RepeatMode.Restart),
        label = "circularProgress"
    )
    val lProgressTopRight by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 2f,
        animationSpec = infiniteRepeatable(tween(3500, easing = LinearEasing), RepeatMode.Restart),
        label = "lProgressTopRight"
    )
    val lProgressTopLeft by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 2f,
        animationSpec = infiniteRepeatable(tween(3500, easing = LinearEasing), RepeatMode.Restart),
        label = "lProgressTopLeft"
    )
    val lProgressBottomLeft by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 2f,
        animationSpec = infiniteRepeatable(tween(3500, easing = LinearEasing), RepeatMode.Restart),
        label = "lProgressBottomLeft"
    )
    val lProgressBottomRight by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 2f,
        animationSpec = infiniteRepeatable(tween(3500, easing = LinearEasing), RepeatMode.Restart),
        label = "lProgressBottomRight"
    )

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val centerX = constraints.maxWidth / 2f
        val centerY = constraints.maxHeight / 2f

        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                color = primaryColor,
                radius = circleRadiusPx,
                center = Offset(centerX, centerY),
                style = Stroke(width = strokeWidth)
            )
            drawLShapeLines(centerX, centerY, circleRadiusPx, lineLengthPx, strokeWidth, primaryColor, GestureType.L_TOP_RIGHT)
            drawLShapeLines(centerX, centerY, circleRadiusPx, lineLengthPx, strokeWidth, primaryColor, GestureType.L_TOP_LEFT)
            drawLShapeLines(centerX, centerY, circleRadiusPx, lineLengthPx, strokeWidth, primaryColor, GestureType.L_BOTTOM_LEFT)
            drawLShapeLines(centerX, centerY, circleRadiusPx, lineLengthPx, strokeWidth, primaryColor, GestureType.L_BOTTOM_RIGHT)
        }

        HandIndicator(circularProgress, null, centerX, centerY, circleRadiusPx, lineLengthPx, density, accentColor)
        HandIndicator(lProgressTopRight, GestureType.L_TOP_RIGHT, centerX, centerY, circleRadiusPx, lineLengthPx, density, accentColor)
        HandIndicator(lProgressTopLeft, GestureType.L_TOP_LEFT, centerX, centerY, circleRadiusPx, lineLengthPx, density, accentColor)
        HandIndicator(lProgressBottomLeft, GestureType.L_BOTTOM_LEFT, centerX, centerY, circleRadiusPx, lineLengthPx, density, accentColor)
        HandIndicator(lProgressBottomRight, GestureType.L_BOTTOM_RIGHT, centerX, centerY, circleRadiusPx, lineLengthPx, density, accentColor)
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawLShapeLines(
    centerX: Float, centerY: Float, circleRadius: Float, lineLength: Float, strokeWidth: Float, color: Color, quadrant: GestureType
) {
    when (quadrant) {
        GestureType.L_TOP_RIGHT -> {
            val hStartX = centerX + circleRadius + lineLength
            val hEndX = hStartX - lineLength
            val hY = centerY - circleRadius
            drawLine(color, Offset(hStartX, hY), Offset(hEndX, hY), strokeWidth, cap = StrokeCap.Round)
            drawLine(color, Offset(hEndX, hY), Offset(hEndX, hY - lineLength), strokeWidth, cap = StrokeCap.Round)
        }
        GestureType.L_TOP_LEFT -> {
            val vX = centerX - circleRadius
            val vStartY = centerY - circleRadius - lineLength
            val vEndY = vStartY + lineLength
            drawLine(color, Offset(vX, vStartY), Offset(vX, vEndY), strokeWidth, cap = StrokeCap.Round)
            drawLine(color, Offset(vX, vEndY), Offset(vX - lineLength, vEndY), strokeWidth, cap = StrokeCap.Round)
        }
        GestureType.L_BOTTOM_LEFT -> {
            val hStartX = centerX - circleRadius - lineLength
            val hEndX = hStartX + lineLength
            val hY = centerY + circleRadius
            drawLine(color, Offset(hStartX, hY), Offset(hEndX, hY), strokeWidth, cap = StrokeCap.Round)
            drawLine(color, Offset(hEndX, hY), Offset(hEndX, hY + lineLength), strokeWidth, cap = StrokeCap.Round)
        }
        GestureType.L_BOTTOM_RIGHT -> {
            val vX = centerX + circleRadius
            val vStartY = centerY + circleRadius + lineLength
            val vEndY = vStartY - lineLength
            drawLine(color, Offset(vX, vStartY), Offset(vX, vEndY), strokeWidth, cap = StrokeCap.Round)
            drawLine(color, Offset(vX, vEndY), Offset(vX + lineLength, vEndY), strokeWidth, cap = StrokeCap.Round)
        }
    }
}


@Composable
fun AppsLayout(
    settingsViewModel: SettingsViewModel,
    settings: SettingsUiState,
    circleRadius: Dp,
    lineLength: Dp,
    iconSize: Dp,
    iconShape: androidx.compose.ui.graphics.Shape
) {
    val context = LocalContext.current
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
            val centerApp = patternApps["Center"]
            if(centerApp == null) {
                IconShapePreview(
                    shape = iconShape,
                    size = iconSize
                )
            } else {
                AppIcon(app = centerApp,
                    iconSize=iconSize,
                    shape=iconShape,
                    notificationPackages = emptyList(),
                    clickable = false)
            }
        }
        val offsetLength = (circleRadius + lineLength/2)
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .offset(x = offsetLength, y = offsetLength)
                .clickable { selectSetApp("BottomRight")},
        ){
            val bottomRightApp = patternApps["BottomRight"]
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
            val bottomLeftApp = patternApps["BottomLeft"]
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
            val topRightApp = patternApps["TopRight"]
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
            val topLeftApp =  patternApps["TopLeft"]
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
