package com.jeerovan.comfer

import android.graphics.BitmapFactory
import android.os.Debug
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jeerovan.comfer.utils.CommonUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
class Wallpaper8kStressTest {
    @Test
    fun eightKWallpaperAppliesWithinMemoryBudgetAndRestoresOriginal() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<ComferApp>()
        context.initializeApplicationData()
        StartupCoordinator.awaitReady()
        val fixture = File(context.filesDir, FIXTURE_NAME)
        assertTrue("Push $FIXTURE_NAME into app files before test", fixture.isFile)

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(fixture.absolutePath, bounds)
        assertEquals(7_680, bounds.outWidth)
        assertEquals(4_320, bounds.outHeight)

        val originalPath = PreferenceManager.getBackgroundImagePath(context)
        assertTrue("Original wallpaper must exist for non-destructive restore", originalPath != null)
        assertTrue(File(originalPath!!).isFile)

        try {
            PreferenceManager.setBackgroundImagePath(context, fixture.absolutePath)
            val baselinePssKb = currentPssKb()
            val peakPssKb = AtomicInteger(baselinePssKb)
            coroutineScope {
                val monitor = launch(Dispatchers.Default) {
                    while (isActive) {
                        peakPssKb.accumulateAndGet(currentPssKb(), ::maxOf)
                        delay(5)
                    }
                }
                try {
                    WallpaperWorkCoordinator.runExclusive {
                        CommonUtil.setWallpaper(context)
                    }
                } finally {
                    monitor.cancel()
                }
            }
            val deltaKb = peakPssKb.get() - baselinePssKb
            Log.i(TAG, "baselinePssKb=$baselinePssKb peakPssKb=${peakPssKb.get()} deltaKb=$deltaKb")
            assertTrue("Peak PSS delta $deltaKb KiB exceeded 32 MiB", deltaKb <= 32 * 1_024)
            assertEquals(fixture.absolutePath, PreferenceManager.getAppliedWallpaperImage(context))
        } finally {
            PreferenceManager.setBackgroundImagePath(context, originalPath)
            WallpaperWorkCoordinator.runExclusive {
                CommonUtil.setWallpaper(context)
            }
            withContext(Dispatchers.IO) { fixture.delete() }
        }
    }

    private fun currentPssKb(): Int = Debug.MemoryInfo().also(Debug::getMemoryInfo).totalPss

    private companion object {
        const val FIXTURE_NAME = "stress_wallpaper_8k.jpg"
        const val TAG = "Wallpaper8kStress"
    }
}
