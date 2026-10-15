package com.jeerovan.comfer

import android.content.Intent
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.Bitmap
import android.os.Build
import android.os.SystemClock
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.jeerovan.comfer.spatial.LOCAL_SPATIAL
import com.jeerovan.comfer.spatial.spatialPreferences
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.Locale

class WallpaperSettingsTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val keys = listOf(PreferenceManager.AUTO_WALLPAPER, PreferenceManager.MONOCHROME,
        PreferenceManager.BATTERY_SAVER_MODE, PreferenceManager.WALLPAPER_DIRECTORY,
        PreferenceManager.KEY_WALLPAPER_MOTION, PreferenceManager.WALLPAPER_FREQUENCY, "howto_guide_shown_key")

    @Test fun freshDefaultsAreOffAndExplicitChoicesArePreserved() = withPreferences {
        PreferenceManager.clear(context, PreferenceManager.AUTO_WALLPAPER)
        PreferenceManager.clear(context, PreferenceManager.MONOCHROME)
        assertFalse("Fresh installations must not enable automatic wallpapers", PreferenceManager.getAutoWallpapers(context))
        assertFalse("Fresh installations must not enable Monochrome", PreferenceManager.getMonochrome(context))
        for (enabled in listOf(true, false)) {
            PreferenceManager.setAutoWallpapers(context, enabled)
            assertEquals("An explicit automatic-wallpaper choice must survive the new default", enabled,
                PreferenceManager.getAutoWallpapers(context))
        }
    }

    @Test fun wallpaperSettingsFollowTheRequestedOrderAcrossLanguageSources() = withPreferences {
        val systemTag = Resources.getSystem().configuration.locales[0].toLanguageTag()
        val systemLanguage = Locale.forLanguageTag(systemTag).language
        InstrumentationRegistry.getArguments().getString("expectedSystemLanguage")?.let {
            assertEquals("Actual system language", it, systemLanguage)
        }
        var previousLocale = ""
        ActivityScenario.launch(GuideActivity::class.java).use { scenario ->
            scenario.onActivity { previousLocale = AppCompatDelegate.getApplicationLocales().toLanguageTags() }
        }
        try {
            val snapshots = mutableListOf<Map<Int, List<Float>>>()
            for (tag in listOf("", systemTag, if (systemLanguage == "ar") "en" else "ar", "")) {
                compose.mainClock.autoAdvance = true
                changeLanguage(tag)
                PreferenceManager.setAutoWallpapers(context, false)
                PreferenceManager.setMonochrome(context, false)
                PreferenceManager.setWallpaperMotion(context, true)
                PreferenceManager.setWallpaperFrequency(context, "Daily")
                spatialPreferences(context).edit().putBoolean(LOCAL_SPATIAL, false).commit()
                var model: SettingsViewModel? = null
                var resources: Resources? = null
                ActivityScenario.launch(SettingsActivity::class.java).use { scenario ->
                    scenario.onActivity { activity ->
                        resources = activity.resources
                        val expected = Locale.forLanguageTag(tag.ifEmpty { systemTag })
                        assertEquals(expected.language, activity.resources.configuration.locales[0].language)
                        InstrumentationRegistry.getArguments().getString("expectedOrientation")?.let {
                            assertEquals("Actual settings orientation",
                                if (it == "landscape") Configuration.ORIENTATION_LANDSCAPE else Configuration.ORIENTATION_PORTRAIT,
                                activity.resources.configuration.orientation)
                        }
                        model = ViewModelProvider(activity)[SettingsViewModel::class.java]
                    }
                    val res = resources!!
                    val settings = model!!
                    Log.i("WallpaperSettingsTest", "source=$tag activity-created")
                    fun waitFor(auto: Boolean) = compose.waitUntil(10_000) {
                        compose.mainClock.advanceTimeBy(32)
                        settings.uiState.value.autoWallpapers == auto && !settings.uiState.value.isBatterySaver &&
                            settings.uiState.value.wallpaperFrequency == "Daily"
                    }
                    fun tap(id: Int) {
                        compose.onNodeWithTag("settings-list").performScrollToNode(hasText(res.getString(id)))
                        compose.onNodeWithText(res.getString(id)).performTouchInput { click() }
                        compose.mainClock.advanceTimeBy(300)
                    }
                    waitFor(false)
                    compose.onNodeWithTag("settings-list").assertIsDisplayed()
                    compose.mainClock.advanceTimeBy(1000)
                    Log.i("WallpaperSettingsTest", "source=$tag settings-ready")
                    compose.onNodeWithTag("settings-list").performScrollToNode(hasText(res.getString(R.string.title_auto_wallpapers)))
                    compose.onNodeWithText(res.getString(R.string.title_wallpaper_motion)).assertDoesNotExist()
                    compose.onNodeWithText(res.getString(R.string.local_spatial_title)).assertDoesNotExist()
                    compose.onNodeWithTag("settings-list").performScrollToNode(hasText(res.getString(R.string.title_monochrome)))
                    compose.onNodeWithText(res.getString(R.string.title_monochrome)).assertIsDisplayed()
                    tap(R.string.title_auto_wallpapers)
                    waitFor(true)
                    compose.onNodeWithText(res.getString(R.string.title_monochrome)).assertDoesNotExist()
                    snapshots += captureRows(res)
                    Log.i("WallpaperSettingsTest", "source=$tag order-and-geometry-checked")
                    if (Build.VERSION.SDK_INT >= 26) {
                        val image = compose.onRoot().captureToImage().asAndroidBitmap()
                        File(context.getExternalFilesDir(null),
                            "wallpaper-settings-${res.configuration.locales[0].language}-${res.configuration.orientation}.png")
                            .outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    }

                    // Cloud 3D has no local-model prerequisite or download prompt.
                    tap(R.string.local_spatial_title)
                    compose.waitUntil(5_000) { spatialPreferences(context).getBoolean(LOCAL_SPATIAL, false) }
                    compose.onNodeWithText(res.getString(R.string.local_spatial_description)).assertExists()
                    compose.onNodeWithText(res.getString(R.string.local_spatial_download_description)).assertDoesNotExist()
                    tap(R.string.local_spatial_title)
                    compose.waitUntil(5_000) { !spatialPreferences(context).getBoolean(LOCAL_SPATIAL, true) }
                    tap(R.string.title_wallpaper_motion)
                    compose.waitUntil(5_000) { !settings.uiState.value.wallpaperMotionEnabled }
                    tap(R.string.title_auto_wallpapers)
                    waitFor(false)
                    compose.onNodeWithText(res.getString(R.string.title_wallpaper_motion)).assertDoesNotExist()
                    compose.onNodeWithText(res.getString(R.string.local_spatial_title)).assertDoesNotExist()
                    tap(R.string.title_monochrome)
                    compose.waitUntil(5_000) { settings.uiState.value.monochrome }
                    tap(R.string.title_auto_wallpapers)
                    waitFor(true)
                    compose.runOnIdle {
                        assertFalse("Enabling automatic wallpapers exits Monochrome", settings.uiState.value.monochrome)
                        assertFalse(PreferenceManager.getMonochrome(context))
                        assertFalse("Hiding Wallpaper motion must preserve its saved value", settings.uiState.value.wallpaperMotionEnabled)
                    }
                    Log.i("WallpaperSettingsTest", "source=$tag toggles-checked")
                }
            }
            assertEquals("System and picker locales must have the same row geometry", snapshots[0], snapshots[1])
            assertEquals("Returning to system language must restore row geometry", snapshots[0], snapshots[3])
        } finally { changeLanguage(previousLocale) }
    }

    private fun captureRows(resources: Resources): Map<Int, List<Float>> {
        val expected = listOf(R.string.title_auto_wallpapers, R.string.title_wallpaper_motion,
            R.string.local_spatial_title, R.string.title_wallpaper_frequency, R.string.title_own_wallpapers)
        val list = compose.onNodeWithTag("settings-list")
        list.performScrollToNode(hasText(resources.getString(expected.first())))
        val found = linkedSetOf<Int>()
        for (step in 0..12) {
            val visible = expected.mapNotNull { id ->
                val row = compose.onNodeWithText(resources.getString(id))
                // Lazy lists can expose an unplaced prefetched node at a dummy origin.
                if (runCatching { row.assertIsDisplayed() }.isSuccess)
                    row.fetchSemanticsNode().boundsInRoot.let { id to it.top }
                else null
            }.sortedBy { it.second }
            visible.forEach { found += it.first }
            if (expected.last() in found) break
            list.performTouchInput { swipeUp(startY = height * .8f, endY = height * .3f, durationMillis = 300) }
            compose.mainClock.advanceTimeBy(400)
        }
        assertEquals("Wallpaper setting order", expected, found.toList())
        return expected.associateWith { id ->
            list.performScrollToNode(hasText(resources.getString(id)))
            val row = compose.onNodeWithText(resources.getString(id)).assertIsDisplayed().assertHasClickAction()
            val bounds = row.fetchSemanticsNode().boundsInRoot
            val viewport = compose.onNodeWithTag("settings-viewport").fetchSemanticsNode().boundsInRoot
            assertTrue("Settings row must fit horizontally", bounds.left >= viewport.left - 1 && bounds.right <= viewport.right + 1)
            if (id in expected.take(3)) {
                val toggle = row.onChildren().filterToOne(isToggleable()).assertIsDisplayed()
                    .fetchSemanticsNode().boundsInRoot
                assertTrue("Switch must be at the trailing edge for the effective locale",
                    if (resources.configuration.layoutDirection == 1) toggle.center.x < bounds.center.x else toggle.center.x > bounds.center.x)
            }
            val full = row.getUnclippedBoundsInRoot()
            assertTrue("Row keeps a usable touch target", (full.bottom - full.top).value >= 48f)
            listOf(full.left.value, (full.right - full.left).value, (full.bottom - full.top).value)
        }
    }

    private fun withPreferences(block: () -> Unit) {
        check(context.packageName.endsWith(".notificationtest")) { "Use the isolated notificationTest app" }
        runBlocking { StartupCoordinator.awaitReady() }
        val previous = keys.associateWith { PreferenceManager.getString(context, it, null) }
        val prefs = spatialPreferences(context)
        val spatial = prefs.getBoolean(LOCAL_SPATIAL, false)
        PreferenceManager.setBatterySaver(context, false)
        PreferenceManager.setWallpaperDirectory(context, null)
        // Keep an unrelated infinite first-run guide pulse out of wallpaper layout checks.
        PreferenceManager.setBoolean(context, "howto_guide_shown_key", true)
        try { block() } finally {
            previous.forEach { (key, value) -> PreferenceManager.setString(context, key, value) }
            prefs.edit().putBoolean(LOCAL_SPATIAL, spatial).commit()
        }
    }

    private fun changeLanguage(tag: String) {
        context.startActivity(Intent(context, LanguageUpdateActivity::class.java)
            .putExtra(LanguageUpdateActivity.EXTRA_LOCALE_TAG, tag).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        val deadline = SystemClock.uptimeMillis() + 10_000
        do {
            var applied = false
            instrumentation.runOnMainSync { applied = AppCompatDelegate.getApplicationLocales().toLanguageTags() == tag }
            if (applied) return
            SystemClock.sleep(50)
        } while (SystemClock.uptimeMillis() < deadline)
        fail("Could not apply language: $tag")
    }
}
