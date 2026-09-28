package com.jeerovan.comfer

import android.graphics.Bitmap
import androidx.test.platform.app.InstrumentationRegistry
import com.jeerovan.comfer.utils.CommonUtil
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class WidgetGlassPaletteTest {
    @Test fun existingWallpaperPipelinePublishesTintIncludingBlackAndWhite() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName.endsWith(".notificationtest"))
        StartupCoordinator.awaitReady()
        val keys = listOf(PreferenceManager.WALLPAPER_LIGHT_BG, PreferenceManager.WALLPAPER_LIGHT_FG,
            PreferenceManager.WALLPAPER_DARK_BG, PreferenceManager.WALLPAPER_DARK_FG,
            PreferenceManager.WALLPAPER_TEXT_FG, PreferenceManager.WALLPAPER_TEXT_BG,
            PreferenceManager.WALLPAPER_GLASS_TINT, PreferenceManager.MONOCHROME)
        val previous = keys.associateWith { PreferenceManager.getString(context, it, null) }
        val file = File.createTempFile("glass-palette", ".png", context.cacheDir)
        try {
            PreferenceManager.setBoolean(context, PreferenceManager.MONOCHROME, false)
            val results = mutableListOf<Int>()
            for (color in listOf(0xffaa2020.toInt(), 0xff2040aa.toInt(), 0xff000000.toInt(), 0xffffffff.toInt())) {
                val bitmap = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(color)
                file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                bitmap.recycle()
                CommonUtil.setWallpaperThemedColors(context, file)
                val tint = requireNotNull(PreferenceManager.getThemedColors(context).glassTint)
                results += tint
                for (shift in listOf(0, 8, 16)) assertEquals("Palette quantization", ((color shr shift) and 255).toDouble(),
                    ((tint shr shift) and 255).toDouble(), 8.0)
            }
            assertEquals("Different wallpapers refresh the cached tint", 4, results.distinct().size)
        } finally {
            file.delete()
            previous.forEach { (key, value) -> PreferenceManager.setString(context, key, value) }
        }
    }
}
