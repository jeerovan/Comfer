package com.jeerovan.comfer

import android.graphics.drawable.Drawable
import android.util.Log
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

private val widgetImagePermit = Semaphore(1)

/** Only visible picker rows load images; retain a small owned bitmap, not vendor drawables. */
internal suspend fun loadWidgetImage(maxEdge: Int, load: () -> Drawable?): ImageBitmap? =
    withContext(Dispatchers.IO) {
        widgetImagePermit.withPermit {
            try {
                load()?.toOwnedBitmap(maxEdge)?.asImageBitmap()
            } catch (e: CancellationException) {
                throw e
            } catch (_: OutOfMemoryError) {
                // No retry while under memory pressure. The row remains selectable with a placeholder.
                Log.w("WidgetImages", "Not enough memory for widget image")
                null
            } catch (e: Exception) {
                Log.w("WidgetImages", "Widget image unavailable", e)
                null
            }
        }
    }
