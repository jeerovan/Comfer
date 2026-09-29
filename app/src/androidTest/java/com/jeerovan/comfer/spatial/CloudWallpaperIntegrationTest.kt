package com.jeerovan.comfer.spatial

import android.content.Intent
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Color
import android.os.SystemClock
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.jeerovan.comfer.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Assume.assumeNotNull
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.security.MessageDigest

class CloudWallpaperIntegrationTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()

    @Test
    @androidx.test.filters.SdkSuppress(minSdkVersion = 26)
    fun cloudMotionAndMetadataUpdatesFollowBothLanguageSources() {
        val context = instrumentation.targetContext
        runBlocking { StartupCoordinator.awaitReady() }
        val previousData = PreferenceManager.getImageDataString(context, null)
        val previousPath = PreferenceManager.getBackgroundImagePath(context)
        val prefs = spatialPreferences(context)
        val previousSpatial = prefs.getBoolean(LOCAL_SPATIAL, false)
        val source = File(context.cacheDir, "comfer_990001.jpg")
        val image = Bitmap.createBitmap(64, 128, Bitmap.Config.ARGB_8888)
        image.eraseColor(Color.BLUE)
        source.outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val url = "https://test.invalid/cloud-motion-scene"
        val suffix = MessageDigest.getInstance("SHA-256").digest("scene:$url".toByteArray())
            .joinToString("") { "%02x".format(it) }
        val directory = File(context.noBackupFilesDir, "cloud-spatial/${sha256(source)}-cloud-v2-$suffix")
        fun seedScene() {
            directory.mkdirs()
            for (y in 0 until 128) for (x in 0 until 64) image.setPixel(x, y, if (x < 32) Color.RED else Color.GREEN)
            File(directory, "background.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
            File(directory, "background.depth").writeText("1 1 0 0 0 0")
            File(directory, "ready").writeText("1")
            SpatialSceneCache(File(context.noBackupFilesDir, "local-spatial"),
                File(context.noBackupFilesDir, "cloud-spatial"), { File(it, "ready").isFile })
                .activate(directory.name, true, source.path)
        }
        var previousLocale = ""
        ActivityScenario.launch<GuideActivity>(Intent(context, GuideActivity::class.java)).use { it.onActivity {
            previousLocale = AppCompatDelegate.getApplicationLocales().toLanguageTags()
        } }
        val systemTag = Resources.getSystem().configuration.locales[0].toLanguageTag()
        val systemLanguage = java.util.Locale.forLanguageTag(systemTag).language
        InstrumentationRegistry.getArguments().getString("expectedSystemLanguage")?.let { assertEquals(it, systemLanguage) }
        compose.mainClock.autoAdvance = false
        try {
            prefs.edit().putBoolean(LOCAL_SPATIAL, false).commit()
            PreferenceManager.setBackgroundImagePath(context, source.path)
            for (tag in listOf("", systemTag, if (systemLanguage == "ar") "en" else "ar", "")) {
                changeLanguage(tag)
                seedScene()
                val expected = java.util.Locale.forLanguageTag(tag.ifEmpty { systemTag }).language
                val legacy = ImageData(990001, "https://test.invalid/original")
                PreferenceManager.setImageDataString(context, Json.encodeToString(legacy))
                var motion by mutableStateOf(false)
                var observed: ImageData? = null
                var direction: LayoutDirection? = null
                ActivityScenario.launch<GuideActivity>(Intent(context, GuideActivity::class.java)).use { scenario ->
                    compose.waitUntil(10_000) {
                        var applied = false
                        scenario.onActivity { applied = it.resources.configuration.locales[0].language == expected }
                        applied
                    }
                    scenario.onActivity { activity ->
                        assertEquals(expected, activity.resources.configuration.locales[0].language)
                        activity.setContent {
                            val cloud = rememberCloudWallpaper(source.path, motion)
                            val actualDirection = LocalLayoutDirection.current
                            SideEffect { observed = cloud; direction = actualDirection }
                            Box(Modifier.fillMaxSize().testTag("cloud-wallpaper")) {
                                renderLocalSpatialWallpaper(motion, 400f, 800f, source.path,
                                    ownWallpapers = false, cloudSceneUrl = cloud?.spatialSceneUrl)
                            }
                        }
                    }
                    fun waitFor(check: () -> Boolean) = compose.waitUntil(15_000) {
                        compose.mainClock.advanceTimeBy(32)
                        check()
                    }
                    fun pixels(): List<Int> {
                        val bitmap = compose.onNodeWithTag("cloud-wallpaper").captureToImage().asAndroidBitmap()
                        return listOf(.02f, .25f, .75f, .98f).flatMap { x ->
                            listOf(.02f, .5f, .98f).map { y -> bitmap.getPixel((bitmap.width*x).toInt(), (bitmap.height*y).toInt()) }
                        }
                    }
                    waitFor { observed == legacy }
                    assertEquals(if (expected == "ar") LayoutDirection.Rtl else LayoutDirection.Ltr, direction)
                    waitFor { pixels().all { it == Color.BLUE } }
                    // Same file, new metadata: this used to stay stuck in remember(path).
                    val published = legacy.copy(spatialSceneUrl = url)
                    PreferenceManager.setImageDataString(context, Json.encodeToString(published))
                    waitFor { observed == published }
                    assertEquals(LocalSpatialStatus.Idle, LocalSpatialRepository.status.value)
                    repeat(2) {
                        compose.runOnIdle { motion = true }
                        waitFor { pixels() == List(6) { Color.RED } + List(6) { Color.GREEN } }
                        assertTrue(LocalSpatialRepository.status.value is LocalSpatialStatus.Ready)
                        compose.runOnIdle { motion = false }
                        waitFor { pixels().all { it == Color.BLUE } }
                        assertEquals(LocalSpatialStatus.Idle, LocalSpatialRepository.status.value)
                    }
                }
            }
        } finally {
            LocalSpatialRepository.prepare(context, null, false)
            changeLanguage(previousLocale)
            PreferenceManager.setString(context, PreferenceManager.KEY_IMAGE_DATA, previousData)
            PreferenceManager.setString(context, PreferenceManager.PREF_BACKGROUND_IMAGE, previousPath)
            prefs.edit().putBoolean(LOCAL_SPATIAL, previousSpatial).commit()
            image.recycle(); source.delete(); directory.deleteRecursively()
        }
    }

    @Test fun publishedCloudSceneLoadsThroughAndroidPipeline() = runBlocking {
        val url = InstrumentationRegistry.getArguments().getString("cloudSceneUrl")
        assumeNotNull(url)
        val context = instrumentation.targetContext
        val directory = File(context.cacheDir, "live-cloud-validation").apply { deleteRecursively(); mkdirs() }
        try {
            LocalSpatialRepository.generateCloudScene(context, url!!, directory)
            val scene = LocalSpatialRepository.loadScene(directory)
            try {
                assertTrue(scene.layers.size in 1..4)
                assertTrue(scene.layers.all { it.bitmap.width in 1..2048 && it.bitmap.height in 1..2048 })
            } finally { scene.layers.forEach { it.bitmap.recycle() } }
        } finally { directory.deleteRecursively() }
    }

    private fun changeLanguage(tag: String) {
        instrumentation.targetContext.startActivity(Intent(instrumentation.targetContext, LanguageUpdateActivity::class.java)
            .putExtra(LanguageUpdateActivity.EXTRA_LOCALE_TAG, tag).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        val deadline = SystemClock.uptimeMillis() + 10_000
        do {
            var current = ""
            instrumentation.runOnMainSync { current = AppCompatDelegate.getApplicationLocales().toLanguageTags() }
            if (current == tag) return
            SystemClock.sleep(50)
        } while (SystemClock.uptimeMillis() < deadline)
        fail("Language change timed out: $tag")
    }
}
