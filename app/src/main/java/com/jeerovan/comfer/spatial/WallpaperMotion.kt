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
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.isActive

internal fun wallpaperMotionActive(resumed: Boolean, focused: Boolean, interactive: Boolean,
                                   animations: Boolean) = resumed && focused && interactive && animations

/** One gate for orbit frames, tilt sensors and the home-screen progress animation. */
@Composable
internal fun rememberWallpaperActive(): Boolean {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val focused = LocalWindowInfo.current.isWindowFocused
    var resumed by remember(lifecycle) { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    val power = remember(context) { context.getSystemService(PowerManager::class.java) }
    var interactive by remember { mutableStateOf(power?.isInteractive != false) }
    var animations by remember { mutableStateOf(true) }
    DisposableEffect(context, lifecycle) {
        fun update() {
            resumed = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
            interactive = power?.isInteractive != false
            animations = Settings.Global.getFloat(context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
        }
        val observer = LifecycleEventObserver { _, _ -> update() }
        val settings = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) = update()
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) = update()
        }
        lifecycle.addObserver(observer)
        context.contentResolver.registerContentObserver(Settings.Global.getUriFor(
            Settings.Global.ANIMATOR_DURATION_SCALE), false, settings)
        ContextCompat.registerReceiver(context, receiver, IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        }, ContextCompat.RECEIVER_NOT_EXPORTED)
        update()
        onDispose {
            lifecycle.removeObserver(observer)
            context.contentResolver.unregisterContentObserver(settings)
            context.unregisterReceiver(receiver)
        }
    }
    return wallpaperMotionActive(resumed, focused, interactive, animations)
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
