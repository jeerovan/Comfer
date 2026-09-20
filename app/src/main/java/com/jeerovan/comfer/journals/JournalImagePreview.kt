package com.jeerovan.comfer.journals

import com.jeerovan.comfer.ui.ModuleIconButton as IconButton

import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import com.jeerovan.comfer.R

@Composable
internal fun JournalImagePreview(media: JournalMedia, id: String, onClose: () -> Unit, onChange: () -> Unit, onDelete: () -> Unit) {
    var loading by remember(id) { mutableStateOf(true) }
    val bitmap by produceState<android.graphics.Bitmap?>(null, id) {
        try { value = media.thumbnail(id, maxEdge = 2048) }
        catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (_: Exception) { value = null }
        finally { loading = false }
    }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false,
        decorFitsSystemWindows = false, securePolicy = SecureFlagPolicy.SecureOn)) {
        Surface(Modifier.fillMaxSize().testTag("journal-image-preview")) {
            Column(Modifier.fillMaxSize().safeDrawingPadding()) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    if (loading) CircularProgressIndicator()
                    else bitmap?.let { ZoomableJournalImage(it, id) } ?: Text(stringResource(R.string.journal_image_unavailable))
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp).testTag("journal-image-actions"), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onClose, modifier = Modifier.weight(1f)) { Icon(Icons.Outlined.Close, stringResource(R.string.journal_cancel)) }
                    TextButton(onClick = onChange, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.journal_change_image)) }
                    IconButton(onClick = onDelete, modifier = Modifier.weight(1f)) { Icon(Icons.Outlined.DeleteOutline, stringResource(R.string.journal_remove_image)) }
                }
            }
        }
    }
}

@Composable
private fun ZoomableJournalImage(bitmap: android.graphics.Bitmap, id: String) {
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    var zoom by remember(id) { mutableFloatStateOf(1f) }
    var pan by remember(id) { mutableStateOf(Offset.Zero) }
    fun bounded(offset: Offset, scale: Float): Offset {
        if (viewport.width == 0 || viewport.height == 0) return Offset.Zero
        val fit = minOf(viewport.width.toFloat() / bitmap.width, viewport.height.toFloat() / bitmap.height)
        val x = ((bitmap.width * fit * scale - viewport.width) / 2).coerceAtLeast(0f)
        val y = ((bitmap.height * fit * scale - viewport.height) / 2).coerceAtLeast(0f)
        return Offset(offset.x.coerceIn(-x, x), offset.y.coerceIn(-y, y))
    }
    fun setZoom(value: Float) { zoom = value.coerceIn(1f, 5f); pan = bounded(pan, zoom) }
    Box(Modifier.fillMaxSize().clipToBounds().testTag("journal-zoom-image")
        .onSizeChanged { viewport = it; pan = bounded(pan, zoom) }
        .semantics {
            stateDescription = "${(zoom * 100).toInt()}%"
            customActions = listOf(
                CustomAccessibilityAction("Zoom in") { setZoom(zoom + 1); true },
                CustomAccessibilityAction("Zoom out") { setZoom(zoom - 1); true },
                CustomAccessibilityAction("Reset zoom") { zoom = 1f; pan = Offset.Zero; true })
        }
        .pointerInput(id) {
            detectTransformGestures { centroid, movement, scale, _ ->
                val next = (zoom * scale).coerceIn(1f, 5f)
                val center = Offset(viewport.width / 2f, viewport.height / 2f)
                pan = bounded((pan + center - centroid) * (next / zoom) + centroid - center + movement, next)
                zoom = next
            }
        }) {
        Image(bitmap.asImageBitmap(), stringResource(R.string.journal_image),
            Modifier.fillMaxSize().graphicsLayer { scaleX = zoom; scaleY = zoom; translationX = pan.x; translationY = pan.y },
            contentScale = ContentScale.Fit)
    }
}
