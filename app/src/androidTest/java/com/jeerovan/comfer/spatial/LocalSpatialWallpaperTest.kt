package com.jeerovan.comfer.spatial

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class LocalSpatialWallpaperTest {
    @get:Rule val compose = createComposeRule()

    @Test fun orbitFreezesWithoutFocusAndResumesWithoutElapsedTimeJump() {
        var active by mutableStateOf(true)
        var enabled by mutableStateOf(true)
        lateinit var phase: State<Double>
        compose.mainClock.autoAdvance = false
        compose.setContent { phase = rememberWallpaperOrbit(enabled, active) }
        compose.mainClock.advanceTimeBy(100)
        compose.mainClock.advanceTimeBy(1000)
        var before = 0.0
        compose.runOnIdle { before = phase.value; active = false }
        compose.mainClock.advanceTimeBy(32)
        var frozen = 0.0
        compose.runOnIdle { frozen = phase.value }
        compose.mainClock.advanceTimeBy(15_000)
        compose.runOnIdle { assertEquals(frozen, phase.value, 0.0); active = true }
        compose.mainClock.advanceTimeBy(1000)
        compose.runOnIdle {
            assertTrue(phase.value > before)
            assertTrue(phase.value - frozen < 1.1)
            enabled = false
        }
        compose.mainClock.advanceTimeBy(32)
        compose.runOnIdle { frozen = phase.value }
        compose.mainClock.advanceTimeBy(1000)
        compose.runOnIdle { assertEquals(frozen, phase.value, 0.0) }
    }

    @Test fun smallProgressHintHasAccessibleStatusAndRetry() {
        var failed by mutableStateOf(false)
        var retried = false
        compose.setContent {
            MaterialTheme {
                SpatialProgressHint(active = false, failed = failed, progress = null, onRetry = { retried = true })
            }
        }
        compose.onNodeWithContentDescription("Preparing spatial wallpaper").assertIsDisplayed()
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val image = compose.onRoot().captureToImage().asAndroidBitmap()
            File(context.getExternalFilesDir(null), "local-spatial-progress.png").outputStream().use {
                image.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        }
        compose.runOnIdle { failed = true }
        compose.onNodeWithContentDescription("Could not prepare spatial effect. Tap to retry.").performClick()
        compose.runOnIdle { assertTrue(retried) }
    }

    @Test fun settingsShowLocalized3DEffectSwitch() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = spatialPreferences(context)
        val oldEnabled = prefs.getBoolean(LOCAL_SPATIAL, false)
        prefs.edit().putBoolean(LOCAL_SPATIAL, false).commit()
        try {
            var locale by mutableStateOf("en")
            compose.setContent { SpatialTestLocale(locale) { MaterialTheme { LocalWallpaperSetting() } } }
            for (tag in listOf("en", "ar")) {
                compose.runOnIdle { locale = tag }
                val resources = spatialTestContext(context, tag).resources
                compose.onNodeWithText(resources.getString(com.jeerovan.comfer.R.string.local_spatial_title)).assertIsDisplayed()
                compose.onNode(isToggleable()).assertIsOff()
                if (android.os.Build.VERSION.SDK_INT >= 26) {
                    val image = compose.onRoot().captureToImage().asAndroidBitmap()
                    File(context.getExternalFilesDir(null), "spatial-setting-$tag.png").outputStream().use {
                        image.compress(Bitmap.CompressFormat.PNG, 100, it)
                    }
                }
            }
        } finally { prefs.edit().putBoolean(LOCAL_SPATIAL, oldEnabled).commit() }
    }

    @Test fun optOutNeverStartsMl() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        LocalSpatialRepository.prepare(context, "/missing-photo", false)
        assertEquals(LocalSpatialStatus.Idle, LocalSpatialRepository.status.value)
    }

    @Test fun cloudWithoutDepthDoesNotStartLocalMl() {
        compose.mainClock.autoAdvance = false
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        LocalSpatialRepository.prepare(context, null, false)
        compose.setContent {
            renderLocalSpatialWallpaper(true, 380f, 822f, path = null, ownWallpapers = false)
        }
        compose.runOnIdle { assertEquals(LocalSpatialStatus.Idle, LocalSpatialRepository.status.value) }
    }

    @Test
    @androidx.test.filters.SdkSuppress(minSdkVersion = 26)
    fun archivedEffectAppliesOnHomeAndOptOutRestoresOriginalWithoutPreview() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = spatialPreferences(context)
        val oldEnabled = prefs.getBoolean(LOCAL_SPATIAL, false)
        val source = File(context.cacheDir, "cached-local-spatial-test.png")
        fun writeSolid(file: File, color: Int) {
            val image = Bitmap.createBitmap(32, 64, Bitmap.Config.ARGB_8888)
            image.eraseColor(color)
            file.outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
            image.recycle()
        }
        writeSolid(source, android.graphics.Color.BLUE)
        val directory = File(context.noBackupFilesDir, "local-spatial/${sha256(source)}-$PIPELINE_VERSION").apply { mkdirs() }
        writeSolid(File(directory, "background.png"), android.graphics.Color.GREEN)
        File(directory, "background.depth").writeText("1 1 0 0 0 0")
        File(directory, "ready").writeText("1")
        // Seed only an archive. Its deliberately GREEN layer differs from the BLUE original,
        // so rendering green proves repository archive reuse rather than fresh ML generation.
        val fixture = File(context.cacheDir, "archive-ui-fixture").apply { deleteRecursively(); mkdirs() }
        val fixtureLocal = File(fixture, "local").apply { mkdirs() }
        check(directory.renameTo(File(fixtureLocal, directory.name)))
        val fixtureCache = SpatialSceneCache(fixtureLocal, File(fixture, "cloud"),
            { File(it, "ready").isFile })
        fixtureCache.maintain()
        val archive = File(context.noBackupFilesDir, "local-spatial/archives/${directory.name}.zip")
        archive.parentFile!!.mkdirs()
        fixtureCache.archiveFile(directory.name).copyTo(archive, overwrite = true)
        fixture.deleteRecursively()
        try {
            prefs.edit().putBoolean(LOCAL_SPATIAL, true).commit()
            compose.setContent {
                Box(Modifier.fillMaxSize()) { renderLocalSpatialWallpaper(false, 380f, 822f, path = source.path) }
            }
            fun center(): Int {
                val image = compose.onRoot().captureToImage().asAndroidBitmap()
                return image.getPixel(image.width / 2, image.height / 2)
            }
            compose.waitUntil(10_000) { center() == android.graphics.Color.GREEN }
            compose.onAllNodesWithText("Preview").assertCountEquals(0)
            compose.runOnIdle { prefs.edit().putBoolean(LOCAL_SPATIAL, false).apply() }
            compose.waitUntil(10_000) { center() == android.graphics.Color.BLUE }
        } finally {
            LocalSpatialRepository.prepare(context, null, false)
            prefs.edit().putBoolean(LOCAL_SPATIAL, oldEnabled).commit()
            source.delete()
            directory.deleteRecursively()
            archive.delete()
        }
    }
}
