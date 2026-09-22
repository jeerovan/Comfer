package com.jeerovan.comfer.spatial

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class SpatialWallpaperTest {
    @get:Rule val compose = createComposeRule()

    @Test fun wholePictureMovesOverTimeAndMotionToggleStopsIt() {
        var enabled by mutableStateOf(true)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            Box(Modifier.fillMaxSize().testTag("moving-wallpaper")) {
                SpatialWallpaper(1, motionEnabled = enabled)
            }
        }
        fun capture() = compose.onNodeWithTag("moving-wallpaper").captureToImage().asAndroidBitmap()
        compose.waitUntil(10_000) {
            compose.mainClock.advanceTimeBy(32)
            val bitmap = capture()
            Color.blue(bitmap.getPixel(bitmap.width/2, bitmap.height/2)) > 5
        }
        // Loading the bitmap starts the motion effect in the next composition.
        // Let that effect subscribe to the frame clock before measuring travel.
        compose.mainClock.advanceTimeBy(128)
        compose.waitForIdle()
        val initial = capture()
        compose.mainClock.advanceTimeBy(15_000)
        compose.waitForIdle()
        assertFalse("Wallpaper should orbit even with a stationary phone", initial.sameAs(capture()))
        compose.runOnIdle { enabled = false }
        compose.mainClock.advanceTimeBy(64)
        val stopped = capture()
        compose.mainClock.advanceTimeBy(15_000)
        assertTrue("Motion off must stop both orbit and parallax", stopped.sameAs(capture()))
    }

    @Test fun bothScenesRenderInComposeAndCanBeReplaced() {
        var selected by mutableIntStateOf(1)
        compose.setContent {
            Box(Modifier.fillMaxSize().testTag("wallpaper")) {
                SpatialWallpaper(selected, motionEnabled = false)
            }
        }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        var previous: Bitmap? = null
        for (scene in 1..2) {
            compose.runOnIdle { selected = scene }
            var captured: Bitmap? = null
            compose.waitUntil(10_000) {
                val bitmap = compose.onNodeWithTag("wallpaper").captureToImage().asAndroidBitmap()
                val pixel = bitmap.getPixel(bitmap.width/2, bitmap.height/2)
                val colorful = Color.blue(pixel) > 5 && Color.red(pixel) < 240
                val changed = previous?.let { !it.sameAs(bitmap) } ?: true
                if (colorful && changed) captured = bitmap
                colorful && changed
            }
            val bitmap = requireNotNull(captured)
            File(context.getExternalFilesDir(null), "spatial-scene-$scene.png").outputStream().use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            previous = bitmap
        }
    }

    @Test fun pickerPersistsSelectionAndOffRestoresRegularMode() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = context.getSharedPreferences("spatial_wallpapers", 0)
        val original = prefs.getInt("scene", 0)
        try {
            compose.setContent { MaterialTheme { SpatialWallpaperSetting() } }
            for (name in listOf("Ocean valley", "Meditating panda", "Off · Use regular wallpaper")) {
                compose.onNodeWithText("Spatial wallpapers").performClick()
                compose.onAllNodesWithText(name).onLast().performClick()
                compose.onNodeWithText(name).assertIsDisplayed()
            }
            assertEquals(0, prefs.getInt("scene", -1))
        } finally { prefs.edit().putInt("scene", original).commit() }
    }

    @Test fun extremeTiltRendersWithoutTransparentEdges() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        for (scene in 1..2) {
            val texture = context.assets.open("spatial/wallpaper-$scene.jpg").use { BitmapFactory.decodeStream(it)!! }
            val mesh = context.assets.open("spatial/wallpaper-$scene.depth").bufferedReader().use { DepthMesh.parse(it.readText()) }
            val output = Bitmap.createBitmap(380, 822, Bitmap.Config.ARGB_8888)
            try {
                for (x in listOf(-1f, 1f)) for (y in listOf(-1f, 1f)) {
                    output.eraseColor(Color.TRANSPARENT)
                    Canvas(output).drawBitmapMesh(texture, mesh.columns, mesh.rows,
                        mesh.project(380f, 822f, texture.width, texture.height, x, y, x*0.08f, y*0.08f), 0, null, 0, Paint(Paint.FILTER_BITMAP_FLAG))
                    for (edgeX in listOf(0, 379)) for (edgeY in 0 until 822) {
                        assertEquals(255, Color.alpha(output.getPixel(edgeX, edgeY)))
                    }
                    for (edgeY in listOf(0, 821)) for (edgeX in 0 until 380) {
                        assertEquals(255, Color.alpha(output.getPixel(edgeX, edgeY)))
                    }
                }
            } finally { texture.recycle(); output.recycle() }
        }
    }
}
