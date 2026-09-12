package com.jeerovan.comfer.utils

import org.junit.Assert.*
import org.junit.Test

class WallpaperSamplingTest {
    @Test fun boundsPanoramasInBothOrientations() {
        assertEquals(32, wallpaperSampleSize(32768, 2048, 1440, 1280, 4_000_000))
        assertEquals(32, wallpaperSampleSize(2048, 32768, 1440, 1280, 4_000_000))
    }
    @Test fun leavesSmallAndExactSizeImagesUnchanged() {
        assertEquals(1, wallpaperSampleSize(320, 200, 1440, 1280, 4_000_000))
        assertEquals(1, wallpaperSampleSize(1440, 1280, 1440, 1280, 4_000_000))
    }
    @Test fun respectsPixelBudgetEvenWhenImageFitsTarget() {
        assertEquals(4, wallpaperSampleSize(2048, 2048, 2048, 2048, 300_000))
    }
    @Test fun roundsUpOddDimensionsBeforeCheckingLimits() {
        assertEquals(4, wallpaperSampleSize(2881, 100, 1440, 1280, 4_000_000))
    }
    @Test fun rejectsInvalidDimensionsAndBudgets() {
        assertNull(wallpaperSampleSize(-1, 200, 1440, 1280, 4_000_000))
        assertNull(wallpaperSampleSize(200, 200, 0, 1280, 4_000_000))
        assertNull(wallpaperSampleSize(200, 200, 1440, 1280, 0))
    }
    @Test fun hugeBoundsDoNotOverflowOrLoopForever() {
        assertEquals(1 shl 21, wallpaperSampleSize(Int.MAX_VALUE, Int.MAX_VALUE, 1440, 1280, 4_000_000))
        assertNull(wallpaperSampleSize(Int.MAX_VALUE, Int.MAX_VALUE, 1, 1, 1))
    }
}
