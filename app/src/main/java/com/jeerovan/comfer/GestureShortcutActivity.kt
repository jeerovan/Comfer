package com.jeerovan.comfer

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.PI

class GestureShortcutActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                GestureShortcutScreen()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GestureShortcutScreen() {
    val context = LocalContext.current
    var selectedGesture by remember { mutableStateOf<GestureType?>(null) }
    var gestureApps by remember {
        mutableStateOf(
            mapOf(
                GestureType.CIRCULAR to "",
                GestureType.L_TOP_RIGHT to "",
                GestureType.L_TOP_LEFT to "",
                GestureType.L_BOTTOM_LEFT to "",
                GestureType.L_BOTTOM_RIGHT to ""
            )
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.7f))
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

            // Main gesture display area
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                GestureCanvas(
                    onGestureClick = { gestureType ->
                        selectedGesture = gestureType
                    },
                    gestureApps = gestureApps
                )
            }
        }

        // App picker dialog
        if (selectedGesture != null) {
            AppPickerDialog(
                onDismiss = { selectedGesture = null },
                onAppSelected = { packageName ->
                    gestureApps = gestureApps.toMutableMap().apply {
                        put(selectedGesture!!, packageName)
                    }
                    selectedGesture = null
                }
            )
        }
    }
}

enum class GestureType {
    CIRCULAR,
    L_TOP_RIGHT,
    L_TOP_LEFT,
    L_BOTTOM_LEFT,
    L_BOTTOM_RIGHT
}

@Composable
fun GestureCanvas(
    onGestureClick: (GestureType) -> Unit,
    gestureApps: Map<GestureType, String>
) {
    val density = LocalDensity.current
    val circleRadius = with(density) { 40.dp.toPx() }
    val lineLength = with(density) { 100.dp.toPx() }
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

        else -> {}
    }
}

@Composable
fun AppPickerDialog(
    onDismiss: () -> Unit,
    onAppSelected: (String) -> Unit
) {
    val context = LocalContext.current
    val packageManager = context.packageManager

    // Get all launchable apps
    val apps = remember {
        val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        packageManager.queryIntentActivities(mainIntent, 0)
            .map { it.activityInfo.packageName to it.loadLabel(packageManager).toString() }
            .distinctBy { it.first }
            .sortedBy { it.second }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select App") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp)
            ) {
                apps.forEach { (packageName, appName) ->
                    TextButton(
                        onClick = { onAppSelected(packageName) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = appName,
                            modifier = Modifier.fillMaxWidth(),
                            fontSize = 16.sp
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
