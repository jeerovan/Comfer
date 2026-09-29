package com.jeerovan.comfer.spatial

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.ViewTreeObserver
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.exp

/** Stops accepting events immediately; service setup/cleanup never blocks composition. */
@Composable
internal fun rememberSpatialTilt(enabled: Boolean, scene: Any?): SpatialMotion {
    val context = LocalContext.current.applicationContext
    val view = LocalView.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val offset = remember(scene) { mutableStateOf(Offset.Zero) }
    DisposableEffect(context, enabled, scene, view, lifecycle) {
        val requested = MutableStateFlow(false)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val mainHandler = Handler(Looper.getMainLooper())
        scope.launch {
            // collectLatest waits for the previous session's finally block before
            // starting another. Every session owns its listener, even during disposal.
            requested.collectLatest { active ->
                if (!active) return@collectLatest
                val session = currentCoroutineContext()
                val calibration = SpatialPose()
                val matrix = FloatArray(9)
                var previousTime = 0L
                val listener = object : SensorEventListener {
                    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
                    override fun onSensorChanged(event: SensorEvent) {
                        // These callbacks explicitly run on Main. A pending service
                        // registration cannot revive motion after pause/disposal.
                        if (!requested.value || !session.isActive ||
                            !view.hasWindowFocus() ||
                            !lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) ||
                            event.values.any { !it.isFinite() }) return
                        val rotation = view.display?.rotation ?: return
                        SensorManager.getRotationMatrixFromVector(matrix, event.values)
                        val (x, y) = calibration.update(matrix, rotation)
                        val dt = if (previousTime == 0L) 0.02f else
                            ((event.timestamp-previousTime)/1_000_000_000f).coerceIn(0f, 0.1f)
                        previousTime = event.timestamp
                        val blend = 1f - exp(-dt / 0.085f)
                        val old = offset.value
                        offset.value = Offset(old.x+(x-old.x)*blend, old.y+(y-old.y)*blend)
                    }
                }
                var sensors: SensorManager? = null
                var registrationAttempted = false
                try {
                    sensors = context.getSystemService(SensorManager::class.java)
                    session.ensureActive()
                    val sensor = sensors?.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
                        ?: sensors?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
                    session.ensureActive()
                    if (sensor != null) {
                        registrationAttempted = true
                        if (sensors?.registerListener(listener, sensor, 20_000, mainHandler) == true) {
                            awaitCancellation()
                        }
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: RuntimeException) {
                    Log.w("SpatialTilt", "Tilt sensor unavailable", error)
                } finally {
                    // This synchronous cleanup still runs on IO when cancellation
                    // arrived while an uninterruptible service call was in flight.
                    if (registrationAttempted) {
                        try { sensors?.unregisterListener(listener) }
                        catch (error: RuntimeException) {
                            Log.w("SpatialTilt", "Could not release tilt sensor", error)
                        }
                    }
                }
            }
        }
        fun updateRegistration() {
            requested.value = enabled && view.hasWindowFocus() &&
                lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        }
        // Direct callbacks matter: Compose may already be paused when focus/lifecycle changes.
        val observer = LifecycleEventObserver { _, _ -> updateRegistration() }
        val focus = ViewTreeObserver.OnWindowFocusChangeListener { updateRegistration() }
        val tree = view.viewTreeObserver
        lifecycle.addObserver(observer)
        tree.addOnWindowFocusChangeListener(focus)
        updateRegistration()
        onDispose {
            requested.value = false
            scope.cancel()
            lifecycle.removeObserver(observer)
            if (tree.isAlive) tree.removeOnWindowFocusChangeListener(focus)
        }
    }
    return remember(offset) { SpatialMotion(offset) }
}

internal data class SpatialMotion(val offset: State<Offset>)
