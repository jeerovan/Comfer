package com.jeerovan.comfer.spatial

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive

internal data class WallpaperEnvironment(val interactive: Boolean, val animations: Boolean)

/** Registration, settings reads and cleanup share the same IO lifetime. */
internal fun wallpaperEnvironment(context: Context) = callbackFlow {
    val refresh = Channel<Unit>(Channel.CONFLATED)
    val settings = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) { refresh.trySend(Unit) }
    }
    val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) { refresh.trySend(Unit) }
    }
    var observing = false
    var receiving = false
    try {
        val power = context.getSystemService(PowerManager::class.java)
        ensureActive()
        context.contentResolver.registerContentObserver(Settings.Global.getUriFor(
            Settings.Global.ANIMATOR_DURATION_SCALE), false, settings)
        observing = true
        ensureActive()
        ContextCompat.registerReceiver(context, receiver, IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        }, ContextCompat.RECEIVER_NOT_EXPORTED)
        receiving = true
        refresh.trySend(Unit)
        for (ignored in refresh) {
            send(WallpaperEnvironment(
                interactive = power?.isInteractive != false,
                animations = Settings.Global.getFloat(context.contentResolver,
                    Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f,
            ))
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: RuntimeException) {
        // Motion is optional. If a service fails, remain still until a new subscription.
        trySend(WallpaperEnvironment(interactive = false, animations = false))
        Log.w("WallpaperMotion", "Motion environment unavailable", error)
    } finally {
        refresh.close()
        channel.close()
        // A cancellation during registerReceiver must wait for it to return and
        // release that exact receiver. An independent onDispose unregister races it.
        if (receiving) {
            try { context.unregisterReceiver(receiver) }
            catch (error: RuntimeException) { Log.w("WallpaperMotion", "Receiver cleanup failed", error) }
        }
        if (observing) {
            try { context.contentResolver.unregisterContentObserver(settings) }
            catch (error: RuntimeException) { Log.w("WallpaperMotion", "Observer cleanup failed", error) }
        }
    }
}.flowOn(Dispatchers.IO)

internal fun wallpaperMotionActive(resumed: Boolean, focused: Boolean, interactive: Boolean,
                                   animations: Boolean) = resumed && focused && interactive && animations

/** One gate for orbit frames, tilt sensors and the home-screen progress animation. */
@Composable
internal fun rememberWallpaperActive(): Boolean {
    val context = LocalContext.current.applicationContext
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val focused = LocalWindowInfo.current.isWindowFocused
    var resumed by remember(lifecycle) { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    val environment by produceState(WallpaperEnvironment(false, false), context, resumed) {
        value = WallpaperEnvironment(false, false)
        if (resumed) wallpaperEnvironment(context).collect { value = it }
    }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, _ ->
            resumed = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    return wallpaperMotionActive(resumed, focused, environment.interactive, environment.animations)
}

/** Retains phase while paused; state is read in drawing, never once per composition/frame. */
@Composable
internal fun rememberWallpaperOrbit(enabled: Boolean, active: Boolean): State<Double> {
    val seconds = remember { mutableDoubleStateOf(0.0) }
    LaunchedEffect(enabled, active) {
        if (enabled && active) {
            var previous = withFrameNanos { it }
            while (isActive) withFrameNanos { now ->
                seconds.doubleValue = (seconds.doubleValue + (now - previous) / 1_000_000_000.0) % 60.0
                previous = now
            }
        }
    }
    return seconds
}
