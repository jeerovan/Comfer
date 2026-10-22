package com.jeerovan.comfer.spatial

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
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

/** Stops accepting events immediately; service setup/cleanup never blocks composition. */
@Composable
internal fun rememberSpatialTilt(enabled: Boolean, scene: Any?): SpatialMotion {
    val context = LocalContext.current.applicationContext
    val view = LocalView.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val offset = remember(scene) { mutableStateOf(Offset.Zero) }
    val compositionScope = rememberCoroutineScope()
    DisposableEffect(context, enabled, scene, view, lifecycle) {
        val requested = MutableStateFlow(false)
        // Retain Compose's frame clock while doing sensor service work on IO.
        val scope = CoroutineScope(compositionScope.coroutineContext +
            SupervisorJob(compositionScope.coroutineContext[Job]) + Dispatchers.IO)
        val mainHandler = Handler(Looper.getMainLooper())
        scope.launch {
            // collectLatest waits for the previous session's finally block before
            // starting another. Every session owns its listener, even during disposal.
            requested.collectLatest { active ->
                if (!active) return@collectLatest
                val session = currentCoroutineContext()
                val motion = SpatialTiltAnimation()
                val matrix = FloatArray(9)
                var frames: Job? = null // Accessed only on Main; owned by this sensor session.
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
                        motion.tilt(matrix, rotation, SystemClock.elapsedRealtimeNanos())
                        if (!motion.isAtRest && frames?.isActive != true) {
                            frames = CoroutineScope(session).launch(Dispatchers.Main.immediate) {
                                var previousFrame: Long? = null
                                do {
                                    withFrameNanos { now ->
                                        if (requested.value && session.isActive) {
                                            // Use frame-clock deltas for animation; the sensor's
                                            // elapsed-time clock only detects interrupted input.
                                            val seconds = previousFrame?.let {
                                                (now - it).coerceAtLeast(0L) / 1_000_000_000.0
                                            } ?: 0.0
                                            previousFrame = now
                                            val (x, y) = motion.advanceBy(seconds)
                                            offset.value = Offset(x, y)
                                        }
                                    }
                                } while (requested.value && session.isActive && !motion.isAtRest)
                            }
                        }
                    }
                }
                var sensors: SensorManager? = null
                var registrationAttempted = false
                try {
                    sensors = context.getSystemService(SensorManager::class.java)
                    session.ensureActive()
                    val sensor = sensors?.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
                        ?: sensors?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
                        // Gyroscope-free phones (for example Galaxy A13) still expose
                        // a geomagnetic vector in the same rotation-vector format.
                        ?: sensors?.getDefaultSensor(Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR)
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
            if (!requested.value) offset.value = Offset.Zero
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
            offset.value = Offset.Zero
            scope.cancel()
            lifecycle.removeObserver(observer)
            if (tree.isAlive) tree.removeOnWindowFocusChangeListener(focus)
        }
    }
    return remember(offset) { SpatialMotion(offset) }
}

internal data class SpatialMotion(val offset: State<Offset>)
