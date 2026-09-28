package com.jeerovan.comfer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

private class StretchMeasurement { var height = 0f }

@Composable
fun DraggableQuickWidgets(
    id: String, editMode: Boolean, savedPosition: Offset?, initialPosition: Offset?,
    onPositionChanged: (String, Offset) -> Unit, onSizeMeasured: (IntSize) -> Unit,
    heightScale: Float? = null, borderColor: Color = Color.White,
    containerHeight: Float = Float.POSITIVE_INFINITY,
    onHeightScaleChanged: (String, Float) -> Unit = { _, _ -> },
    content: @Composable (Float?) -> Unit,
) {
    var offset by remember { mutableStateOf(savedPosition ?: initialPosition ?: Offset.Zero) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    var contentHeight by remember { mutableIntStateOf(0) }
    var preview by remember { mutableFloatStateOf(widgetHeightScale(heightScale ?: 1f)) }
    var resizing by remember { mutableStateOf(false) }
    var dragStartScale by remember { mutableFloatStateOf(1f) }
    var dragStartOffset by remember { mutableStateOf(Offset.Zero) }
    var dragTop by remember { mutableFloatStateOf(0f) }
    var dragBaseHeight by remember { mutableFloatStateOf(1f) }
    var dragFixedHeight by remember { mutableFloatStateOf(0f) }
    var dragDistance by remember { mutableFloatStateOf(0f) }
    val measurement = remember { StretchMeasurement() }
    val commitPosition by rememberUpdatedState(onPositionChanged)
    val commitHeight by rememberUpdatedState(onHeightScaleChanged)
    val viewportHeight by rememberUpdatedState(containerHeight)
    val canResize = editMode && heightScale != null
    val minimumHeight = with(LocalDensity.current) { if (canResize) 48.dp.toPx() else 0f }
    val handleHalfSize = with(LocalDensity.current) { 24.dp.toPx() }

    LaunchedEffect(savedPosition, initialPosition) {
        if (!resizing) offset = savedPosition ?: initialPosition ?: offset
    }
    LaunchedEffect(heightScale) { if (!resizing) preview = widgetHeightScale(heightScale ?: 1f) }

    fun beginResize() {
        dragStartScale = preview
        dragStartOffset = offset
        dragTop = offset.y - size.height / 2f
        dragBaseHeight = measurement.height.coerceAtLeast(1f)
        dragFixedHeight = (contentHeight - dragBaseHeight * preview).coerceAtLeast(0f)
        dragDistance = 0f
        resizing = true
    }
    fun resizeTo(value: Float) {
        val maximum = ((viewportHeight - dragTop - dragFixedHeight) / dragBaseHeight)
            .coerceIn(MIN_WIDGET_HEIGHT, MAX_WIDGET_HEIGHT)
        preview = widgetHeightScale(value).coerceAtMost(maximum)
        offset = Offset(dragStartOffset.x, dragTop + maxOf(minimumHeight, dragFixedHeight + dragBaseHeight * preview) / 2f)
    }
    fun finishResize() {
        resizing = false
        commitHeight(id, preview)
        commitPosition(id, offset)
    }
    fun cancelResize() {
        if (resizing) {
            preview = dragStartScale
            offset = dragStartOffset
            resizing = false
        }
    }
    LaunchedEffect(editMode) { if (!editMode) cancelResize() }

    // Centers are physical screen coordinates. Keep a left origin even in an RTL locale.
    // Emit handles beside all widget frames, with a higher drawing/hit-test order.
    Box(Modifier.fillMaxSize().wrapContentSize(AbsoluteAlignment.TopLeft)
        .absoluteOffset { IntOffset((offset.x - size.width / 2f).roundToInt(), (offset.y - size.height / 2f).roundToInt()) }
        .onSizeChanged {
            if (size != it) {
                size = it
                onSizeMeasured(it)
                if (resizing) offset = Offset(dragStartOffset.x, dragTop + it.height / 2f)
            }
        }
        .pointerInput(editMode) {
            if (editMode) detectDragGestures(
                onDragEnd = { commitPosition(id, offset) },
            ) { change, amount -> change.consume(); offset += amount }
        }
        .testTag("quick-widget-$id")
        .then(if (canResize) Modifier.defaultMinSize(48.dp, 48.dp)
            .border(2.dp, borderColor.copy(alpha = 1f), RoundedCornerShape(8.dp)) else Modifier),
        contentAlignment = androidx.compose.ui.Alignment.Center,
    ) {
        CompositionLocalProvider(LocalWidgetStretchHeight provides { measurement.height = it }) {
            Box(Modifier.onSizeChanged { contentHeight = it.height }) {
                content(if (heightScale == null) null else preview)
            }
        }
    }
    if (canResize) {
        val name = stringResource(if (id == "time") R.string.title_time else R.string.title_date)
        val description = stringResource(R.string.widget_resize_height, name)
        val reset = stringResource(R.string.widget_reset_height)
        Canvas(Modifier.zIndex(1f).fillMaxSize().wrapContentSize(AbsoluteAlignment.TopLeft)
            .absoluteOffset {
                IntOffset((offset.x - size.width / 2f).roundToInt() + size.width - handleHalfSize.roundToInt(),
                    (offset.y - size.height / 2f).roundToInt() + size.height - handleHalfSize.roundToInt())
            }
            .size(48.dp)
            .testTag("widget-height-handle-$id")
            .semantics {
                contentDescription = description
                progressBarRangeInfo = ProgressBarRangeInfo(preview, MIN_WIDGET_HEIGHT..MAX_WIDGET_HEIGHT)
                setProgress { value -> beginResize(); resizeTo(value); finishResize(); true }
                customActions = listOf(CustomAccessibilityAction(reset) {
                    beginResize(); resizeTo(1f); finishResize(); true
                })
            }
            .pointerInput(Unit) {
                detectTapGestures(onDoubleTap = { beginResize(); resizeTo(1f); finishResize() })
            }
            .pointerInput(Unit) {
                detectDragGestures(onDragStart = { beginResize() }, onDragEnd = { finishResize() },
                    onDragCancel = { cancelResize() }) { change, amount ->
                    change.consume()
                    dragDistance += amount.y
                    resizeTo(dragStartScale + dragDistance / dragBaseHeight)
                }
            }) {
            val center = this.center
            drawCircle(Color.Black, 12.dp.toPx(), center)
            drawCircle(borderColor.copy(alpha = 1f), 11.dp.toPx(), center, style = Stroke(1.dp.toPx()))
            val dy = 6.dp.toPx(); val dx = 3.dp.toPx(); val stroke = 2.dp.toPx()
            drawLine(Color.White, center - Offset(0f, dy), center + Offset(0f, dy), stroke, StrokeCap.Round)
            for (sign in listOf(-1f, 1f)) for (side in listOf(-1f, 1f)) {
                drawLine(Color.White, center + Offset(0f, sign * dy),
                    center + Offset(side * dx, sign * (dy - dx)), stroke, StrokeCap.Round)
            }
        }
    }
}
