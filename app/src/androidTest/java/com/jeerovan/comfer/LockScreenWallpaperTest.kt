package com.jeerovan.comfer

import android.Manifest
import android.app.Application
import android.app.WallpaperManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.ParcelFileDescriptor
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.WorkManager
import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test

// Wallpaper readback is restricted on newer Android releases. Preserve the real
// device's lock wallpaper while verifying pixels on the supported Samsung device.
@SdkSuppress(maxSdkVersion = 32)
class LockScreenWallpaperTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val wallpaper get() = WallpaperManager.getInstance(context)

    private fun fixture(block: suspend (SettingsViewModel, File, ViewModelStore) -> Unit) = runBlocking {
        check(context.packageName.endsWith(".notificationtest"))
        withTimeout(25_000) { StartupCoordinator.awaitReady() }
        WorkManager.getInstance(context).cancelUniqueWork("WallpaperSolarBoundary").result.get()
        val previous = WallpaperWorkCoordinator.runExclusive { PreferenceManager.snapshotForBackup() }
        val homeId = wallpaper.getWallpaperId(WallpaperManager.FLAG_SYSTEM)
        val lockId = wallpaper.getWallpaperId(WallpaperManager.FLAG_LOCK)
        val original = File.createTempFile("lock-wallpaper-backup-", ".image", context.cacheDir)
        val image = File.createTempFile("lock-wallpaper-test-", ".png", context.cacheDir)
        val store = ViewModelStore()
        instrumentation.uiAutomation.adoptShellPermissionIdentity(Manifest.permission.READ_EXTERNAL_STORAGE)
        try {
            val descriptor = wallpaper.getWallpaperFile(WallpaperManager.FLAG_LOCK)
            assertTrue("An existing lock wallpaper must be backed up before testing", lockId <= 0 || descriptor != null)
            descriptor?.let {
                ParcelFileDescriptor.AutoCloseInputStream(it).use { input ->
                    original.outputStream().use { output -> input.copyTo(output) }
                }
            }
            WallpaperWorkCoordinator.runExclusive {
                PreferenceManager.setAutoWallpapers(context, false)
                PreferenceManager.setMonochrome(context, false)
                PreferenceManager.setBatterySaver(context, false)
                PreferenceManager.setWallpaperOnLockScreen(context, false)
                PreferenceManager.setWallpaperFrequency(context, "Daily")
                PreferenceManager.setBackgroundImagePath(context, image.absolutePath)
                PreferenceManager.setAppliedWallpaperImage(context, image.absolutePath)
                PreferenceManager.setWallpaperApplied(context, true)
            }
            val factory = ViewModelProvider.AndroidViewModelFactory(context.applicationContext as Application)
            val model = ViewModelProvider(store, factory)[SettingsViewModel::class.java]
            await { model.uiState.value.wallpaperFrequency == "Daily" }
            block(model, image, store)
        } finally {
            instrumentation.runOnMainSync { store.clear() }
            WallpaperWorkCoordinator.runExclusive {
                PreferenceManager.replaceSnapshot(context, previous)
                if (wallpaper.getWallpaperId(WallpaperManager.FLAG_LOCK) != lockId) {
                    if (original.length() > 0) original.inputStream().use {
                        wallpaper.setStream(it, null, true, WallpaperManager.FLAG_LOCK)
                    } else wallpaper.clear(WallpaperManager.FLAG_LOCK)
                }
            }
            instrumentation.uiAutomation.dropShellPermissionIdentity()
            image.delete()
            original.delete()
            WallpaperScheduler.schedule(context)
            assertEquals("Lock-screen changes must preserve Home wallpaper", homeId,
                wallpaper.getWallpaperId(WallpaperManager.FLAG_SYSTEM))
        }
    }

    @Test fun enablingCopiesAlreadyAppliedWallpaperWithoutRotatingIt() = fixture { model, image, _ ->
        val scheduleKeys = listOf(PreferenceManager.WALLPAPER_LAST_SUCCESS,
            PreferenceManager.WALLPAPER_LAST_PERIOD, PreferenceManager.WALLPAPER_PENDING_PERIOD,
            PreferenceManager.KEY_IMAGE_DATA, PreferenceManager.KEY_TEMP_IMAGE_DATA)
        val schedule = scheduleKeys.associateWith { PreferenceManager.getString(context, it, null) }
        val palette = PreferenceManager.getThemedColors(context)
        for ((directory, color) in listOf(null to Color.BLUE, "content://test/local-wallpapers" to Color.RED)) {
            PreferenceManager.setWallpaperDirectory(context, directory)
            writeImage(image, color)
            val oldLockId = wallpaper.getWallpaperId(WallpaperManager.FLAG_LOCK)
            model.setWallpaperOnLockScreen(true).join()
            await { model.uiState.value.wallpaperOnLockScreen &&
                wallpaper.getWallpaperId(WallpaperManager.FLAG_LOCK) != oldLockId }
            // Samsung recompresses the wallpaper; allow color quantization.
            val pixel = lockPixel()
            assertTrue("The current photo must be copied immediately",
                kotlin.math.abs(Color.red(color) - Color.red(pixel)) <= 8 &&
                    kotlin.math.abs(Color.green(color) - Color.green(pixel)) <= 4 &&
                    kotlin.math.abs(Color.blue(color) - Color.blue(pixel)) <= 8)
            assertEquals(image.absolutePath, PreferenceManager.getBackgroundImagePath(context))
            assertEquals(image.absolutePath, PreferenceManager.getAppliedWallpaperImage(context))
            assertTrue(PreferenceManager.getWallpaperApplied(context))
            assertEquals(schedule, scheduleKeys.associateWith { PreferenceManager.getString(context, it, null) })
            assertEquals(palette, PreferenceManager.getThemedColors(context))
            val appliedId = wallpaper.getWallpaperId(WallpaperManager.FLAG_LOCK)
            model.setWallpaperOnLockScreen(false).join()
            await { !model.uiState.value.wallpaperOnLockScreen }
            WallpaperWorkCoordinator.runExclusive { }
            assertEquals("Disabling must keep the existing lock wallpaper", appliedId,
                wallpaper.getWallpaperId(WallpaperManager.FLAG_LOCK))
        }
    }

    @Test fun turningOffWhileWallpaperWorkIsQueuedCancelsTheCopy() = fixture { model, image, _ ->
        writeImage(image, Color.GREEN)
        val before = wallpaper.getWallpaperId(WallpaperManager.FLAG_LOCK)
        coroutineScope {
            val held = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val holder = launch {
                WallpaperWorkCoordinator.runExclusive { held.complete(Unit); release.await() }
            }
            held.await()
            var enabling: kotlinx.coroutines.Job? = null
            try {
                enabling = model.setWallpaperOnLockScreen(true)
                await { model.uiState.value.wallpaperOnLockScreen }
                model.setWallpaperOnLockScreen(false).join()
                await { !model.uiState.value.wallpaperOnLockScreen }
            } finally { release.complete(Unit); holder.join() }
            enabling?.join()
        }
        WallpaperWorkCoordinator.runExclusive { }
        assertEquals(before, wallpaper.getWallpaperId(WallpaperManager.FLAG_LOCK))
    }

    @Test fun closingSettingsWhileCopyIsQueuedStillAppliesCurrentWallpaper() = fixture { model, image, store ->
        writeImage(image, Color.GREEN)
        val before = wallpaper.getWallpaperId(WallpaperManager.FLAG_LOCK)
        coroutineScope {
            val held = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val holder = launch {
                WallpaperWorkCoordinator.runExclusive { held.complete(Unit); release.await() }
            }
            held.await()
            try {
                instrumentation.runOnMainSync {
                    model.setWallpaperOnLockScreen(true)
                    store.clear()
                }
                assertTrue(PreferenceManager.getWallpaperOnLockScreen(context))
            } finally { release.complete(Unit); holder.join() }
        }
        // Android assigns a new ID before the new bitmap is fully readable.
        await {
            wallpaper.getWallpaperId(WallpaperManager.FLAG_LOCK) != before && runCatching {
                val pixel = lockPixel()
                Color.green(pixel) >= 251 && Color.red(pixel) <= 8 && Color.blue(pixel) <= 8
            }.getOrDefault(false)
        }
    }

    @Test fun unavailableCurrentImageKeepsTheExistingLockWallpaper() = fixture { model, image, _ ->
        val before = wallpaper.getWallpaperId(WallpaperManager.FLAG_LOCK)
        for (content in listOf<String?>(null, "not an image")) {
            if (content == null) image.delete() else image.writeText(content)
            model.setWallpaperOnLockScreen(true).join()
            await { model.uiState.value.wallpaperOnLockScreen }
            WallpaperWorkCoordinator.runExclusive { }
            assertEquals(before, wallpaper.getWallpaperId(WallpaperManager.FLAG_LOCK))
            model.setWallpaperOnLockScreen(false).join()
            await { !model.uiState.value.wallpaperOnLockScreen }
        }
        PreferenceManager.clear(context, PreferenceManager.PREF_BACKGROUND_IMAGE)
        model.setWallpaperOnLockScreen(true).join()
        assertEquals(before, wallpaper.getWallpaperId(WallpaperManager.FLAG_LOCK))
    }

    private suspend fun await(condition: () -> Boolean) = withTimeout(8_000) {
        while (!condition()) delay(25)
    }

    private fun writeImage(file: File, color: Int) {
        val bitmap = Bitmap.createBitmap(64, 128, Bitmap.Config.ARGB_8888)
        try {
            bitmap.eraseColor(color)
            file.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        } finally { bitmap.recycle() }
    }

    private fun lockPixel(): Int = requireNotNull(wallpaper.getWallpaperFile(WallpaperManager.FLAG_LOCK)).use {
        val bitmap = requireNotNull(BitmapFactory.decodeFileDescriptor(it.fileDescriptor, null,
            BitmapFactory.Options().apply { inSampleSize = 8 }))
        try { bitmap.getPixel(bitmap.width / 2, bitmap.height / 2) } finally { bitmap.recycle() }
    }
}
