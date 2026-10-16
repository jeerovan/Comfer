package com.jeerovan.comfer

import android.app.Application
import android.app.WallpaperManager
import android.graphics.Bitmap
import android.graphics.Color
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.WorkManager
import com.jeerovan.comfer.spatial.LocalSpatialRepository
import com.jeerovan.comfer.spatial.LocalSpatialStatus
import com.jeerovan.comfer.spatial.LOCAL_SPATIAL
import com.jeerovan.comfer.spatial.spatialPreferences
import com.jeerovan.comfer.utils.CommonUtil
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test

class CloudWallpaperPreloadTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val image = ImageData(980057, "https://test.invalid/preloaded-photo",
        spatialSceneUrl = "https://test.invalid/preloaded-scene")

    /** Keep real background work outside each fixture and restore the isolated app's preferences. */
    private fun fixture(block: suspend (CloudWallpaperPreload) -> Unit) = runBlocking {
        check(context.packageName.endsWith(".notificationtest"))
        withTimeout(25_000) { StartupCoordinator.awaitReady() }
        WallpaperWorkCoordinator.runExclusive {
            val previous = PreferenceManager.snapshotForBackup()
            val directory = File(context.noBackupFilesDir, "wallpaper-preload")
            val backup = File.createTempFile("wallpaper-preload-test-", ".backup", context.noBackupFilesDir)
            backup.delete()
            if (directory.exists()) check(directory.renameTo(backup))
            val preload = CloudWallpaperPreload(context)
            try {
                PreferenceManager.setAutoWallpapers(context, false)
                PreferenceManager.setMonochrome(context, false)
                PreferenceManager.setBatterySaver(context, false)
                PreferenceManager.setWallpaperDirectory(context, null)
                for (key in listOf(PreferenceManager.PREF_BACKGROUND_IMAGE, PreferenceManager.KEY_IMAGE_DATA,
                    PreferenceManager.KEY_TEMP_IMAGE_DATA, PreferenceManager.APPLIED_WALLPAPER_IMAGE,
                    PreferenceManager.WALLPAPER_LAST_SUCCESS, PreferenceManager.WALLPAPER_LAST_PERIOD,
                    PreferenceManager.WALLPAPER_PENDING_PERIOD)) PreferenceManager.clear(context, key)
                PreferenceManager.setBoolean(context, PreferenceManager.IMAGE_AVAILABLE, false)
                PreferenceManager.setWallpaperApplied(context, true)
                block(preload)
            } finally {
                WorkManager.getInstance(context).cancelUniqueWork("WallpaperSolarBoundary").result.get()
                LocalSpatialRepository.prepare(context, null, false)
                PreferenceManager.replaceSnapshot(context, previous)
                preload.clear()
                if (backup.exists()) check(backup.renameTo(directory))
                File(context.filesDir, "comfer_${image.id}.jpg").delete()
                WallpaperScheduler.schedule(context)
            }
        }
        Unit
    }

    private fun writeImage(destination: File, color: Int = Color.BLUE) {
        val bitmap = Bitmap.createBitmap(64, 128, Bitmap.Config.ARGB_8888)
        try {
            bitmap.eraseColor(color)
            destination.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        } finally { bitmap.recycle() }
    }

    private fun writeScene(output: File) {
        writeImage(File(output, "background.png"), Color.RED)
        File(output, "background.depth").writeText("1 1 0 0 0 0")
        File(output, "ready").writeText("1")
    }

    private suspend fun seed(preload: CloudWallpaperPreload, schedule: WallpaperSchedule =
        wallpaperSchedule(context, System.currentTimeMillis())): Boolean = preload.prepare(schedule,
        fetch = { assertEquals(schedule.cloudRequestHour, it); image },
        download = { _, file -> writeImage(file); true },
        details = { error("Published spatial metadata must not require another request") },
        spatial = { _, _, output -> writeScene(output) })

    @Test fun disabledPreparationKeepsWallpaperAndReusesThePreviewAcrossRestarts() = fixture { preload ->
        val wallpaper = WallpaperManager.getInstance(context)
        val systemId = wallpaper.getWallpaperId(WallpaperManager.FLAG_SYSTEM)
        val lockId = wallpaper.getWallpaperId(WallpaperManager.FLAG_LOCK)
        val palette = PreferenceManager.getThemedColors(context)
        val spatial = spatialPreferences(context).getBoolean(LOCAL_SPATIAL, false)
        val schedule = wallpaperSchedule(context, System.currentTimeMillis())
        assertTrue(seed(preload, schedule))
        assertEquals(image, CloudWallpaperPreload(context).imageFor(schedule))
        assertEquals(image, preload.imageFor(schedule.copy(periodKey = "another-day-same-phase")))
        assertFalse(PreferenceManager.getAutoWallpapers(context))
        assertNull(PreferenceManager.getBackgroundImagePath(context))
        assertNull(PreferenceManager.getImageData(context))
        assertNull(PreferenceManager.getAppliedWallpaperImage(context))
        assertEquals(palette, PreferenceManager.getThemedColors(context))
        assertEquals(spatial, spatialPreferences(context).getBoolean(LOCAL_SPATIAL, false))
        assertEquals(systemId, wallpaper.getWallpaperId(WallpaperManager.FLAG_SYSTEM))
        assertEquals(lockId, wallpaper.getWallpaperId(WallpaperManager.FLAG_LOCK))
        assertTrue(CloudWallpaperPreload(context).prepare(schedule.copy(periodKey = "later"),
            fetch = { error("Do not rotate the hidden preview") },
            download = { _, _ -> error("Do not redownload the preview") },
            spatial = { _, _, _ -> error("Do not regenerate the prepared scene") }))
    }

    @Test fun aDifferentDayNightPeriodCannotSelectTheHiddenPreview() = fixture { preload ->
        val schedule = wallpaperSchedule(context, System.currentTimeMillis())
        assertTrue(seed(preload, schedule))
        assertNull(preload.imageFor(schedule.copy(isDay = !schedule.isDay)))
        assertNotNull(preload.sourceFor(image))
    }

    @Test fun invalidPhotoIsNotPublishedAndTheNextAttemptRecovers() = fixture { preload ->
        val schedule = wallpaperSchedule(context, System.currentTimeMillis())
        assertFalse(preload.prepare(schedule, fetch = { image },
            download = { _, file -> file.writeText("not an image"); true }))
        assertNull(preload.imageFor(schedule))
        assertNull(preload.sourceFor(image))
        assertTrue(seed(preload, schedule))
    }

    @Test fun failedDownloadDoesNotPublishOrApplyAPhoto() = fixture { preload ->
        val schedule = wallpaperSchedule(context, System.currentTimeMillis())
        assertFalse(preload.prepare(schedule, fetch = { image },
            download = { _, file -> file.writeText("partial download"); false }))
        assertNull(preload.imageFor(schedule))
        assertNull(PreferenceManager.getBackgroundImagePath(context))
        assertTrue(seed(preload, schedule))
    }

    @Test fun failedSpatialDownloadKeepsThePhotoAndRetriesOnlyTheScene() = fixture { preload ->
        val schedule = wallpaperSchedule(context, System.currentTimeMillis())
        try {
            preload.prepare(schedule, fetch = { image }, download = { _, file -> writeImage(file); true },
                spatial = { _, _, output -> File(output, "partial").writeText("incomplete"); throw IOException("offline") })
            fail("Spatial failure must be recoverable by background work")
        } catch (_: IOException) { }
        assertEquals(image, preload.imageFor(schedule))
        assertFalse(File(context.noBackupFilesDir, "wallpaper-preload/scene.partial").exists())
        assertTrue(CloudWallpaperPreload(context).prepare(schedule,
            fetch = { error("The completed photo must survive a retry") },
            download = { _, _ -> error("The completed photo must not be redownloaded") },
            spatial = { _, _, output -> writeScene(output) }))
    }

    @Test fun cancellationKeepsThePhotoButNeverPublishesAPartialScene() = fixture { preload ->
        val schedule = wallpaperSchedule(context, System.currentTimeMillis())
        try {
            preload.prepare(schedule, fetch = { image }, download = { _, file -> writeImage(file); true },
                spatial = { _, _, _ -> throw CancellationException("worker stopped") })
            fail("Cancellation must propagate")
        } catch (_: CancellationException) { }
        assertEquals(image, preload.imageFor(schedule))
        assertFalse(File(context.noBackupFilesDir, "wallpaper-preload/scene.partial").exists())
        assertFalse(File(context.noBackupFilesDir, "wallpaper-preload/scene/ready").exists())
    }

    @Test fun optInAppliesCachedPhotoAndThreeDUsesPreparedAssetsOffline() = fixture { preload ->
        assertTrue(seed(preload))
        PreferenceManager.setAutoWallpapers(context, true)
        assertEquals(ImageWorkOutcome.SUCCESS, CommonUtil.refreshWallpaper(context))
        val path = requireNotNull(PreferenceManager.getBackgroundImagePath(context))
        assertEquals("comfer_${image.id}.jpg", File(path).name)
        assertTrue(File(path).isFile)
        assertEquals(image, PreferenceManager.getImageData(context))
        assertTrue(PreferenceManager.getWallpaperApplied(context))
        assertTrue(PreferenceManager.getLong(context, PreferenceManager.WALLPAPER_LAST_SUCCESS, 0) > 0)
        assertNull(preload.sourceFor(image))
        // Reapplying inside the rotation interval must also use the current photo offline.
        PreferenceManager.setWallpaperApplied(context, false)
        assertEquals(ImageWorkOutcome.SUCCESS, CommonUtil.refreshWallpaper(context))
        assertTrue(PreferenceManager.getWallpaperApplied(context))
        assertEquals(path, PreferenceManager.getBackgroundImagePath(context))
        LocalSpatialRepository.prepare(context, path, false, cloudSceneUrl = image.spatialSceneUrl)
        LocalSpatialRepository.maintainCache(context)
        // The URL cannot be downloaded. Ready therefore requires the prepared disk assets.
        LocalSpatialRepository.prepare(context, path, true, cloudSceneUrl = image.spatialSceneUrl)
        val ready = withTimeout(5_000) {
            LocalSpatialRepository.status.first { it is LocalSpatialStatus.Ready && it.source == path }
        } as LocalSpatialStatus.Ready
        val scene = LocalSpatialRepository.loadScene(ready.directory)
        try { assertEquals(Color.RED, scene.layers.single().bitmap.getPixel(0, 0)) }
        finally { scene.layers.forEach { it.bitmap.recycle() } }
    }

    @Test fun enablingAutoWallpapersRequestsImmediateWork() = fixture { preload ->
        assertTrue(seed(preload))
        val work = WorkManager.getInstance(context)
        work.cancelUniqueWork("WallpaperSolarBoundary").result.get()
        val old = work.getWorkInfosForUniqueWork("WallpaperSolarBoundary").get().map { it.id }.toSet()
        val store = ViewModelStore()
        try {
            val factory = ViewModelProvider.AndroidViewModelFactory(context.applicationContext as Application)
            val model = ViewModelProvider(store, factory)[SettingsViewModel::class.java]
            model.setAutoWallpapers(true)
            withTimeout(10_000) {
                while (!PreferenceManager.getAutoWallpapers(context) ||
                    work.getWorkInfosForUniqueWork("WallpaperSolarBoundary").get().none { it.id !in old && !it.state.isFinished }) delay(25)
            }
            assertFalse("Opt-in must request reapplication even within the current interval",
                PreferenceManager.getWallpaperApplied(context))
        } finally { instrumentation.runOnMainSync { store.clear() } }
    }

    @Test fun localWallpapersAndBatterySaverDoNotStartCloudPreparation() = fixture { preload ->
        PreferenceManager.setWallpaperDirectory(context, "content://test/local-wallpapers")
        assertEquals(ImageWorkOutcome.SUCCESS, CommonUtil.refreshWallpaper(context))
        assertNull(preload.imageFor(wallpaperSchedule(context, System.currentTimeMillis())))
        PreferenceManager.setWallpaperDirectory(context, null)
        PreferenceManager.setBatterySaver(context, true)
        assertEquals(ImageWorkOutcome.SUCCESS, CommonUtil.refreshWallpaper(context))
        assertNull(preload.sourceFor(image))
        // Battery Saver exposes a generated black background, not a selected cloud photo.
        assertNull(PreferenceManager.getString(context, PreferenceManager.PREF_BACKGROUND_IMAGE, null))
    }

    @Test fun publishedWallpaperPreloadsBeforeOptIn() = fixture { preload ->
        org.junit.Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("liveWallpaperPreload") == "true")
        val schedule = wallpaperSchedule(context, System.currentTimeMillis())
        assertTrue(preload.prepare(schedule))
        val prepared = requireNotNull(preload.imageFor(schedule))
        assertNotNull(preload.sourceFor(prepared))
        val hasSpatial = !prepared.spatialSceneUrl.isNullOrBlank() || !prepared.depthUrl.isNullOrBlank()
        val ready = LocalSpatialRepository.validScene(File(context.noBackupFilesDir, "wallpaper-preload/scene"))
        if (hasSpatial) assertTrue("Published spatial assets must be ready before opt-in", ready)
        assertFalse(PreferenceManager.getAutoWallpapers(context))
        assertNull(PreferenceManager.getBackgroundImagePath(context))
        File(context.getExternalFilesDir(null), "wallpaper-preload-live.json").writeText(
            """{"imageId":${prepared.id},"day":${schedule.isDay},"spatialAvailable":$hasSpatial,"spatialReady":$ready,"autoEnabled":false,"wallpaperApplied":false}""")
    }
}
