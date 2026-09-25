package com.jeerovan.comfer.spatial

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Paint
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.WindowManager
import android.view.ViewTreeObserver
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import com.jeerovan.comfer.R
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.exp

private const val SELECTION = "scene"
private fun preferences(context: Context) =
    context.getSharedPreferences("spatial_wallpapers", Context.MODE_PRIVATE)

@Composable
fun rememberSpatialWallpaper(): State<Int> {
    val context = LocalContext.current.applicationContext
    val prefs = remember(context) { preferences(context) }
    val state = remember(prefs) { mutableIntStateOf(prefs.getInt(SELECTION, 0).coerceIn(0, LOCAL_SCENE)) }
    DisposableEffect(prefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == SELECTION) state.intValue = prefs.getInt(SELECTION, 0).coerceIn(0, LOCAL_SCENE)
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        state.intValue = prefs.getInt(SELECTION, 0).coerceIn(0, LOCAL_SCENE)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    return state
}

@Composable
fun SpatialWallpaperSetting() {
    val context = LocalContext.current
    val selected by rememberSpatialWallpaper()
    var showPicker by remember { mutableStateOf(false) }
    val names = listOf(stringResource(R.string.spatial_off), stringResource(R.string.spatial_valley),
        stringResource(R.string.spatial_panda), stringResource(R.string.local_wallpaper_name))
    ListItem(
        headlineContent = { Text(stringResource(R.string.spatial_title)) },
        supportingContent = { Text(names[selected]) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable { showPicker = true },
    )
    if (showPicker) AlertDialog(
        onDismissRequest = { showPicker = false },
        title = { Text(stringResource(R.string.spatial_title)) },
        text = {
            Column {
                Text(stringResource(R.string.spatial_description))
                names.take(if (spatialPreferences(context).contains(LOCAL_PATH)) 4 else 3).forEachIndexed { index, name ->
                    ListItem(
                        headlineContent = { Text(name) },
                        leadingContent = { RadioButton(selected == index, onClick = null) },
                        modifier = Modifier.fillMaxWidth().selectable(selected == index, role = Role.RadioButton) {
                            preferences(context).edit().putInt(SELECTION, index).apply()
                            showPicker = false
                        },
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = { showPicker = false }) {
            Text(stringResource(android.R.string.cancel))
        } },
    )
}

private data class Scene(val bitmap: Bitmap, val mesh: DepthMesh?)

/** Bundled textures are decoded on IO; only the small vertex buffer changes per frame. */
@Composable
fun SpatialWallpaper(scene: Int, motionEnabled: Boolean) {
    val context = LocalContext.current.applicationContext
    val loaded by produceState<Scene?>(null, scene, context) {
        value = null
        value = withContext(Dispatchers.IO) {
            val bitmap = try {
                context.assets.open("spatial/wallpaper-$scene.jpg").use {
                    BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply {
                        inPreferredConfig = Bitmap.Config.ARGB_8888
                    })
                }
            } catch (_: java.io.IOException) { null }
            bitmap?.let {
                val mesh = try {
                    context.assets.open("spatial/wallpaper-$scene.depth").bufferedReader().use {
                        DepthMesh.parse(it.readText())
                    }
                } catch (_: Exception) { null }
                Scene(bitmap, mesh)
            }
        }
    }
    val active = rememberWallpaperActive()
    val pose = rememberSpatialTilt(motionEnabled && active && loaded?.mesh != null, scene)
    val orbitSeconds = rememberWallpaperOrbit(motionEnabled, active)
    val paint = remember { Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG) }
    Canvas(Modifier.fillMaxSize()) {
        val image = loaded ?: return@Canvas
        if (size.width <= 0 || size.height <= 0) return@Canvas
        val mesh = image.mesh
        if (mesh == null) {
            val scale = maxOf(size.width/image.bitmap.width, size.height/image.bitmap.height)
            val w = image.bitmap.width*scale
            val h = image.bitmap.height*scale
            drawContext.canvas.nativeCanvas.drawBitmap(image.bitmap, null,
                android.graphics.RectF((size.width-w)/2, (size.height-h)/2,
                    (size.width+w)/2, (size.height+h)/2), paint)
        } else {
            val tilt = pose.offset.value // Invalidate drawing only, not the launcher composition.
            val orbit = if (motionEnabled) wallpaperOrbit(orbitSeconds.value) else 0f to 0f
            drawContext.canvas.nativeCanvas.drawBitmapMesh(image.bitmap, mesh.columns, mesh.rows,
                mesh.project(size.width, size.height, image.bitmap.width, image.bitmap.height,
                    tilt.x, tilt.y, orbit.first, orbit.second), 0, null, 0, paint)
        }
    }
}

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
