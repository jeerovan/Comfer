package com.jeerovan.comfer.spatial

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class SpatialSceneTest {
    @Test fun localFourLayerSceneAndCloudSceneRenderIdentically() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val root = File(context.cacheDir, "four-layer-test").apply { deleteRecursively(); mkdirs() }
        val local = File(root, "local").apply { mkdirs() }
        val cloud = File(root, "cloud").apply { mkdirs() }
        val broken = File(root, "broken").apply { mkdirs() }
        val width = 120; val height = 160
        val blue = 0xff17365d.toInt()
        val colors = intArrayOf(0xffff4b35.toInt(), 0xff36da72.toInt(), 0xffffd740.toInt())
        val pixels = IntArray(width * height) { blue }
        val mask = FloatArray(pixels.size)
        val rawDepth = FloatArray(pixels.size) { 0.05f }
        for (subject in 0..2) for (y in 35..110) for (x in 10 + subject * 38..29 + subject * 38) {
            val index = y * width + x
            pixels[index] = colors[subject]
            mask[index] = 1f
            rawDepth[index] = 0.2f + subject * 0.35f
        }
        val image = Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
        try {
            assertTrue(hasReliableSubjectBoundary(pixels, mask, width, height))
            LocalSpatialRepository.writeLocalLayers(image, SpatialDepth(width, height, rawDepth), mask, pixels, local)
            assertEquals("4", File(local, "ready").readText())
            val names = spatialLayerNames(4)
            val assets = mutableMapOf<String, ByteArray>()
            val layers = names.map { name ->
                for (extension in listOf("png", "depth")) assets["https://test.invalid/$name.$extension"] = File(local, "$name.$extension").readBytes()
                """{"imageUrl":"https://test.invalid/$name.png","depthUrl":"https://test.invalid/$name.depth"}"""
            }
            val manifestUrl = "https://test.invalid/scene.json"
            assets[manifestUrl] = """{"version":1,"layers":[${layers.joinToString(",") }]}""".toByteArray()
            LocalSpatialRepository.generateCloudScene(context, manifestUrl, cloud) { url, limit ->
                assets.getValue(url).also { assertTrue(it.size <= limit) }
            }
            val localScene = LocalSpatialRepository.loadScene(local)
            val cloudScene = LocalSpatialRepository.loadScene(cloud)
            try {
                assertEquals(4, cloudScene.layers.size)
                localScene.layers.zip(cloudScene.layers).forEachIndexed { index, (a, b) ->
                    assertEquals("Layer $index width", a.bitmap.width, b.bitmap.width)
                    assertEquals("Layer $index height", a.bitmap.height, b.bitmap.height)
                    assertTrue("Layer $index pixels changed during cloud import", a.bitmap.sameAs(b.bitmap))
                }
                assertEquals(colors[0], localScene.layers[1].bitmap.getPixel(15, 50))
                assertEquals(0, localScene.layers[2].bitmap.getPixel(15, 50) ushr 24)
                assertEquals(blue, localScene.layers[0].bitmap.getPixel(15, 50))
                for (tilt in listOf(0f, -1f, 1f)) {
                    fun render(scene: LocalScene): Bitmap {
                        val result = Bitmap.createBitmap(240, 320, Bitmap.Config.ARGB_8888)
                        val canvas = Canvas(result)
                        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
                        for (layer in scene.layers) canvas.drawBitmapMesh(layer.bitmap, layer.mesh.columns, layer.mesh.rows,
                            layer.mesh.project(240f, 320f, layer.bitmap.width, layer.bitmap.height, tilt, -tilt, 0.08f, -0.08f), 0, null, 0, paint)
                        return result
                    }
                    val a = render(localScene); val b = render(cloudScene)
                    try {
                        assertTrue(a.sameAs(b))
                        assertEquals(255, a.getPixel(0, 0) ushr 24)
                        assertEquals(255, a.getPixel(239, 319) ushr 24)
                        if (tilt == 1f) File(context.getExternalFilesDir(null), "four-layer-tilted.png").outputStream().use {
                            a.compress(Bitmap.CompressFormat.PNG, 100, it)
                        }
                    } finally { a.recycle(); b.recycle() }
                }
            } finally { (localScene.layers + cloudScene.layers).forEach { it.bitmap.recycle() } }
            // A broken final asset must never publish a ready marker or load as a partial scene.
            try {
                LocalSpatialRepository.generateCloudScene(context, manifestUrl, broken) { url, _ ->
                    if (url.endsWith("layer3.png")) throw java.io.IOException("Interrupted download")
                    assets.getValue(url)
                }
                fail("Expected an interrupted download")
            } catch (_: java.io.IOException) { }
            assertFalse(File(broken, "ready").exists())
            try { LocalSpatialRepository.loadScene(broken); fail("Partial scene loaded") }
            catch (_: IllegalStateException) { }
        } finally { image.recycle(); root.deleteRecursively() }
    }
}
