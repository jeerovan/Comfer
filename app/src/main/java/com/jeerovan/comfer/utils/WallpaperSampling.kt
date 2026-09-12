package com.jeerovan.comfer.utils

/** Power-of-two sampling that bounds both axes and worst-case decoded pixel count. */
internal fun wallpaperSampleSize(
    width: Int,
    height: Int,
    targetWidth: Int,
    targetHeight: Int,
    maxPixels: Long,
): Int? {
    if (width <= 0 || height <= 0 || targetWidth <= 0 || targetHeight <= 0 || maxPixels <= 0) return null
    var sample = 1
    while (true) {
        val sampledWidth = (width.toLong() + sample - 1) / sample
        val sampledHeight = (height.toLong() + sample - 1) / sample
        if (sampledWidth <= targetWidth && sampledHeight <= targetHeight &&
            sampledWidth * sampledHeight <= maxPixels) return sample
        if (sample >= 1 shl 30) return null
        sample *= 2
    }
}
