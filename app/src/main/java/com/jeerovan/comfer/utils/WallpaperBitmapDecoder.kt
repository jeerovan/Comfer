package com.jeerovan.comfer.utils

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File

/** Returns an owned bitmap, or null so wallpaper work can retry without replacing the current image. */
internal fun decodeBoundedWallpaper(
    file: File,
    targetWidth: Int,
    targetHeight: Int,
    decodePixels: (String, BitmapFactory.Options) -> Bitmap? = { path, options ->
        BitmapFactory.decodeFile(path, options)
    },
): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    try {
        BitmapFactory.decodeFile(file.absolutePath, bounds)
    } catch (_: OutOfMemoryError) {
        return null
    }
    // ARGB_8888 is possible even when RGB_565 is requested (e.g. transparent PNGs).
    val pixelBudget = minOf(16L * 1024 * 1024, Runtime.getRuntime().maxMemory() / 8) / 4
    var sample = wallpaperSampleSize(bounds.outWidth, bounds.outHeight, targetWidth, targetHeight, pixelBudget)
        ?: return null
    while (true) {
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        try {
            // Decode directly to a bounded size: no overlapping full-size and scaled bitmaps.
            return decodePixels(file.absolutePath, options)
        } catch (_: OutOfMemoryError) {
            // Other allocations can consume our headroom after the budget was calculated.
            // Retry at quarter the pixel count, stopping after the smallest useful bitmap.
            if (sample >= maxOf(bounds.outWidth, bounds.outHeight) || sample >= 1 shl 30) return null
            sample *= 2
        }
    }
}
