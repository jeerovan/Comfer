package com.jeerovan.comfer

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.test.platform.app.InstrumentationRegistry
import com.jeerovan.comfer.utils.CommonUtil
import com.jeerovan.comfer.utils.decodeBoundedWallpaper
import java.io.File
import java.lang.reflect.InvocationTargetException
import org.junit.Assert.*
import org.junit.Test

class WallpaperDecodeTest {
    @Test fun extremeAspectRatiosDecodeRepeatedlyWithoutExhaustingHeap() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        android.util.Log.i("WallpaperDecodeTest", "maxHeap=${Runtime.getRuntime().maxMemory()}")
        for (asset in listOf("wallpaper-wide.png", "wallpaper-tall.png")) {
            val file = File.createTempFile("wallpaper-test", ".png", instrumentation.targetContext.cacheDir)
            try {
                instrumentation.context.assets.open(asset).use { source -> file.outputStream().use(source::copyTo) }
                repeat(3) {
                    val bitmap = decode(file, 1440, 1280)
                    assertNotNull(bitmap)
                    try {
                        assertTrue(bitmap!!.width in 1..1440)
                        assertTrue(bitmap.height in 1..1280)
                        assertTrue(bitmap.allocationByteCount <= 1440 * 1280 * 4)
                        val pixel = bitmap.getPixel(0, 0)
                        assertTrue(kotlin.math.abs(Color.red(pixel) - 48) <= 8)
                        assertTrue(kotlin.math.abs(Color.green(pixel) - 128) <= 4)
                        assertTrue(kotlin.math.abs(Color.blue(pixel) - 176) <= 8)
                    } finally { bitmap?.recycle() }
                }
            } finally { file.delete() }
        }
    }

    @Test fun allocationFailureRetriesSmallerAndExhaustionReturnsNull() = withSmallImage { file ->
        val samples = mutableListOf<Int>()
        val recovered = decodeBoundedWallpaper(file, 100, 100) { path, options ->
            samples += options.inSampleSize
            if (samples.size == 1) throw OutOfMemoryError("Injected pixel allocation failure")
            BitmapFactory.decodeFile(path, options)
        }
        try {
            assertEquals(listOf(1, 2), samples)
            assertNotNull(recovered)
            assertEquals(16, recovered!!.width)
            assertEquals(8, recovered.height)
        } finally { recovered?.recycle() }
        var attempts = 0
        assertNull(decodeBoundedWallpaper(file, 100, 100) { _, _ ->
            attempts++
            throw OutOfMemoryError("Injected persistent memory pressure")
        })
        assertEquals(6, attempts) // 32x16 down to 1x1; no unbounded retry.
        val afterFailure = decodeBoundedWallpaper(file, 100, 100)
        try { assertNotNull(afterFailure) } finally { afterFailure?.recycle() }
    }

    @Test fun smallTransparentImagesKeepSizeAndInvalidFilesReturnNull() = withSmallImage { file ->
        val bitmap = decode(file, 1440, 1280)
        try {
            assertNotNull(bitmap)
            assertEquals(32, bitmap!!.width)
            assertEquals(16, bitmap.height)
            assertEquals(0, Color.alpha(bitmap.getPixel(0, 0)))
        } finally { bitmap?.recycle() }
        file.writeText("not an image")
        assertNull(decode(file, 1440, 1280))
        file.delete()
        assertNull(decode(file, 1440, 1280))
    }

    private fun withSmallImage(block: (File) -> Unit) {
        val file = File.createTempFile("wallpaper-small", ".png", InstrumentationRegistry.getInstrumentation().targetContext.cacheDir)
        try {
            val bitmap = Bitmap.createBitmap(32, 16, Bitmap.Config.ARGB_8888)
            try { file.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) } }
            finally { bitmap.recycle() }
            block(file)
        } finally { file.delete() }
    }

    private fun decode(file: File, width: Int, height: Int): Bitmap? {
        val method = CommonUtil::class.java.getDeclaredMethod(
            "decodeWallpaperBitmap", File::class.java, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType,
        ).apply { isAccessible = true }
        return try { method.invoke(CommonUtil, file, width, height) as Bitmap? }
        catch (failure: InvocationTargetException) { throw failure.targetException }
    }
}
