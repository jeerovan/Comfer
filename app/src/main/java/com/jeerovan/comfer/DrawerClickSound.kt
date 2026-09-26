package com.jeerovan.comfer

import android.content.Context
import android.media.AudioManager
import android.os.SystemClock
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

/** Optional feedback must never hold up drawer selection or build a sound backlog. */
internal class DrawerClickSound(
    scope: CoroutineScope,
    private val clock: () -> Long = SystemClock::uptimeMillis,
    private val play: () -> Unit,
) {
    private val requests = Channel<Long>(Channel.CONFLATED)
    private val job = scope.launch(Dispatchers.IO) {
        for (requestedAt in requests) {
            if (clock() - requestedAt > 100) continue
            try {
                play()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: RuntimeException) {
                Log.w("DrawerClickSound", "System sound service unavailable", error)
            }
        }
    }

    fun request() { requests.trySend(clock()) }
    fun close() { requests.close(); job.cancel() }
}

@Composable
internal fun rememberDrawerClickSound(): DrawerClickSound {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val sound = remember(context, scope) {
        // Both service acquisition and playback run in the IO consumer. The
        // default-volume API respects the system's sound-effects setting.
        val audio by lazy { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
        DrawerClickSound(scope) { audio.playSoundEffect(AudioManager.FX_KEY_CLICK) }
    }
    DisposableEffect(sound) { onDispose { sound.close() } }
    return sound
}
