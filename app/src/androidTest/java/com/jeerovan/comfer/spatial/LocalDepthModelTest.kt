package com.jeerovan.comfer.spatial

import android.graphics.BitmapFactory
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** Run explicitly after seeding the checksum-verified model into the isolated test app. */
class LocalDepthModelTest {
    @Test fun realDepthModelProducesSceneDepthAndCachedAssets() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val model = File(context.noBackupFilesDir, "spatial-models/$DEPTH_MODEL_SHA256.tflite")
        org.junit.Assume.assumeTrue("Seed model to run the optional real-ML integration test", model.isFile)
        assertEquals(DEPTH_MODEL_SHA256, sha256(model))
        val source = File(context.cacheDir, "spatial-model-panda.jpg")
        context.assets.open("spatial/wallpaper-2.jpg").use { input -> source.outputStream().use { input.copyTo(it) } }
        try {
            val image = BitmapFactory.decodeFile(source.path)
            val started = android.os.SystemClock.elapsedRealtime()
            val depth = withContext(Dispatchers.Default) { estimateDepth(model, image) }
            android.util.Log.i("SpatialValidation", "depthInferenceMs=${android.os.SystemClock.elapsedRealtime()-started}")
            image.recycle()
            assertTrue(depth.at(0.5f, 0.65f) > depth.at(0.95f, 0.2f))
            assertTrue(depth.values.max() - depth.values.min() > 0.8f)
            LocalSpatialRepository.prepare(context, source.path, true, retry = true)
            val result = withTimeout(240_000) {
                LocalSpatialRepository.status.first { it is LocalSpatialStatus.Ready || it is LocalSpatialStatus.Failed }
            }
            assertTrue("Preparation result: $result", result is LocalSpatialStatus.Ready)
            val directory = (result as LocalSpatialStatus.Ready).directory
            val scene = LocalSpatialRepository.loadScene(directory)
            assertTrue(scene.layers.size in 1..4)
            android.util.Log.i("SpatialValidation", "preparedLayers=${scene.layers.size}")
            for ((label, tilt) in listOf("neutral" to 0f, "tilted" to 1f)) {
                val output = android.graphics.Bitmap.createBitmap(380, 822, android.graphics.Bitmap.Config.ARGB_8888)
                val canvas = android.graphics.Canvas(output)
                val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG or android.graphics.Paint.FILTER_BITMAP_FLAG)
                for (layer in scene.layers) canvas.drawBitmapMesh(layer.bitmap, layer.mesh.columns, layer.mesh.rows,
                    layer.mesh.project(380f, 822f, layer.bitmap.width, layer.bitmap.height, tilt, -tilt), 0, null, 0, paint)
                File(context.getExternalFilesDir(null), "local-spatial-$label.png").outputStream().use {
                    output.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
                }
                output.recycle()
            }
            scene.layers.forEach { assertFalse(it.bitmap.isRecycled); it.bitmap.recycle() }
            val modified = File(directory, "background.png").lastModified()
            LocalSpatialRepository.prepare(context, null, false)
            LocalSpatialRepository.prepare(context, source.path, true)
            withTimeout(10_000) { LocalSpatialRepository.status.first { it is LocalSpatialStatus.Ready } }
            assertEquals(modified, File(directory, "background.png").lastModified())
        } finally {
            LocalSpatialRepository.prepare(context, null, false)
            source.delete()
        }
    }
}
