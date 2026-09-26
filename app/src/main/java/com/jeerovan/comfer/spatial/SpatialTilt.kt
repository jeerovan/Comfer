package com.jeerovan.comfer.spatial

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.WindowManager
import android.view.ViewTreeObserver
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlin.math.exp

/** The caller supplies the same active gate as orbit; disposal immediately unregisters sensors. */
@Composable
internal fun rememberSpatialTilt(enabled: Boolean, scene: Any?): SpatialMotion {
    val context = LocalContext.current
    val view = LocalView.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val offset = remember(scene) { mutableStateOf(Offset.Zero) }
    DisposableEffect(context, enabled, scene, view, lifecycle) {
        val sensors = context.getSystemService(SensorManager::class.java)
        val window = context.getSystemService(WindowManager::class.java)
        val sensor = sensors?.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
            ?: sensors?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        val calibration = SpatialPose()
        val matrix = FloatArray(9)
        var registered = false
        var previousTime = 0L
        val listener = object : SensorEventListener {
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
            override fun onSensorChanged(event: SensorEvent) {
                if (!registered || event.values.any { !it.isFinite() }) return
                SensorManager.getRotationMatrixFromVector(matrix, event.values)
                @Suppress("DEPRECATION")
                val rotation = window.defaultDisplay.rotation
                val (x, y) = calibration.update(matrix, rotation)
                val dt = if (previousTime == 0L) 0.02f else
                    ((event.timestamp-previousTime)/1_000_000_000f).coerceIn(0f, 0.1f)
                previousTime = event.timestamp
                val blend = 1f - exp(-dt / 0.085f)
                val old = offset.value
                offset.value = Offset(old.x+(x-old.x)*blend, old.y+(y-old.y)*blend)
            }
        }
        fun updateRegistration() {
            val shouldRegister = enabled && view.hasWindowFocus() &&
                lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
            if (shouldRegister && !registered && sensor != null) {
                calibration.reset()
                previousTime = 0L
                registered = sensors?.registerListener(listener, sensor, 20_000) == true
            } else if (!shouldRegister && registered) {
                registered = false
                sensors?.unregisterListener(listener)
            }
        }
        // Direct callbacks matter: Compose may already be paused when focus/lifecycle changes.
        val observer = LifecycleEventObserver { _, _ -> updateRegistration() }
        val focus = ViewTreeObserver.OnWindowFocusChangeListener { updateRegistration() }
        val tree = view.viewTreeObserver
        lifecycle.addObserver(observer)
        tree.addOnWindowFocusChangeListener(focus)
        updateRegistration()
        onDispose {
            registered = false
            sensors?.unregisterListener(listener)
            lifecycle.removeObserver(observer)
            if (tree.isAlive) tree.removeOnWindowFocusChangeListener(focus)
        }
    }
    return remember(offset) { SpatialMotion(offset) }
}

internal data class SpatialMotion(val offset: State<Offset>)
