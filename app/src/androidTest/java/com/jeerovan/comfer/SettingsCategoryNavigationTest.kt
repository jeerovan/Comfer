package com.jeerovan.comfer

import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.core.graphics.ColorUtils
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class SettingsCategoryNavigationTest {
    @get:Rule val compose = createAndroidComposeRule<SettingsActivity>()

    private fun ready() {
        check(compose.activity.packageName.endsWith(".notificationtest"))
        compose.waitUntil(15_000) {
            compose.onAllNodesWithTag("settings-title").fetchSemanticsNodes().isNotEmpty()
        }
    }
    private fun open(page: SettingsPage) {
        val tag = "settings-category-${page.name}"
        compose.onNodeWithTag("settings-list").performScrollToNode(hasTestTag(tag))
        compose.onNodeWithTag(tag).assertIsDisplayed().performClick()
        compose.onNodeWithTag("settings-title").assertTextEquals(compose.activity.getString(page.title))
    }

    private fun capture(name: String) {
        if (android.os.Build.VERSION.SDK_INT < 26) return
        val config = compose.activity.resources.configuration
        File(compose.activity.getExternalFilesDir(null),
            "settings-$name-${config.locales[0].language}-${config.orientation}.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    @Test fun categoriesRetainEveryGroupAndBackReturnsToMenu() {
        ready()
        val examples = mapOf(
            SettingsPage.WALLPAPERS to R.string.title_auto_wallpapers,
            SettingsPage.ICONS to R.string.title_app_icon_size,
            SettingsPage.HOME to R.string.title_custom_widgets,
            SettingsPage.DRAWER to R.string.title_manage_app_list,
            SettingsPage.PERFORMANCE to R.string.title_battery_saver,
            SettingsPage.PROTECTION to R.string.protection_timeout,
            SettingsPage.BACKUP to R.string.title_backup,
            SettingsPage.SUPPORT to R.string.more_from_jeerovan,
        )
        for ((page, example) in examples) {
            open(page)
            if (page in listOf(SettingsPage.WALLPAPERS, SettingsPage.HOME, SettingsPage.PROTECTION)) capture(page.name)
            compose.onNodeWithTag("settings-category-WALLPAPERS").assertDoesNotExist()
            run {
                compose.onNodeWithTag("settings-list").performScrollToNode(hasText(compose.activity.getString(example), ignoreCase = true))
                compose.onNode(hasText(compose.activity.getString(example), ignoreCase = true) and
                    hasAnyAncestor(hasTestTag("settings-list"))).assertIsDisplayed()
            }
            compose.onNodeWithTag("settings-back").assertIsDisplayed().performClick()
            compose.onNodeWithTag("settings-title").assertTextEquals(compose.activity.getString(R.string.journal_settings))
        }
        // Language remains a first-class root item; opening/dismissing it does not enter Support.
        compose.onNodeWithTag("settings-list").performScrollToNode(hasTestTag("settings-category-language"))
        compose.onNodeWithTag("settings-category-language").performClick()
        compose.onNode(isDialog()).assertExists()
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
        compose.onNodeWithTag("settings-category-language").assertIsDisplayed()
    }

    @Test fun savedSettingAndDestinationSurviveRecreationAndSystemBack() {
        ready()
        val context = compose.activity.applicationContext
        runBlocking { StartupCoordinator.awaitReady() }
        val before = PreferenceManager.isTopBarVisible(context)
        try {
            open(SettingsPage.HOME)
            val label = compose.activity.getString(R.string.title_status_bar)
            compose.onNodeWithTag("settings-list").performScrollToNode(hasText(label))
            compose.onNodeWithText(label).performClick()
            compose.waitUntil(5_000) { PreferenceManager.isTopBarVisible(context) != before }
            compose.activityRule.scenario.recreate()
            compose.onNodeWithTag("settings-title").assertTextEquals(compose.activity.getString(R.string.title_home_screen))
            compose.onNodeWithText(label).assertIsDisplayed()
            assertEquals(!before, PreferenceManager.isTopBarVisible(context))
            InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
            compose.onNodeWithTag("settings-category-HOME").assertIsDisplayed()
            val activity = compose.activity
            InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
            compose.waitUntil(5_000) { activity.isFinishing }
        } finally { PreferenceManager.setTopBarVisible(context, before) }
    }

    @Test fun frequentDestinationsStartAtBottomWithDirectionalAffordances() {
        ready()
        val activity = compose.activity
        capture("root")
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            val title = compose.onNodeWithTag("settings-title")
            val layouts = mutableListOf<TextLayoutResult>()
            title.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            val pixels = title.captureToImage().asAndroidBitmap()
            val background = pixels.getPixel(0, 0)
            for (underlay in listOf(android.graphics.Color.BLACK, android.graphics.Color.WHITE)) {
                // Settings uses an 80% surface over the wallpaper; captureToImage retains alpha.
                val opaque = ColorUtils.compositeColors(background, underlay)
                assertTrue("Bottom title stays readable over light and dark wallpaper",
                    ColorUtils.calculateContrast(layouts.single().layoutInput.style.color.toArgb(), opaque) >= 4.5)
            }
        }
        val args = InstrumentationRegistry.getArguments()
        args.getString("expectedSystemLanguage")?.let {
            assertEquals(it, android.content.res.Resources.getSystem().configuration.locales[0].language)
        }
        args.getString("expectedOrientation")?.let {
            assertEquals(if (it == "landscape") Configuration.ORIENTATION_LANDSCAPE else Configuration.ORIENTATION_PORTRAIT,
                activity.resources.configuration.orientation)
        }
        val viewport = compose.onNodeWithTag("settings-viewport").getUnclippedBoundsInRoot()
        val navigation = compose.onNodeWithTag("settings-navigation").getUnclippedBoundsInRoot()
        val wallpaper = compose.onNodeWithTag("settings-category-WALLPAPERS").assertIsDisplayed().getUnclippedBoundsInRoot()
        val icons = compose.onNodeWithTag("settings-category-ICONS").assertIsDisplayed().getUnclippedBoundsInRoot()
        assertTrue("Most frequent category is nearest bottom", wallpaper.top > icons.top)
        assertTrue("Wallpaper is just above fixed navigation", navigation.top.value - wallpaper.bottom.value in 0f..12f)
        assertTrue("Full safe viewport is available", navigation.bottom <= viewport.bottom)
        val rtl = activity.resources.configuration.layoutDirection == 1
        for (page in SettingsPage.entries) {
            val tag = page.name
            compose.onNodeWithTag("settings-list").performScrollToNode(hasTestTag("settings-category-$tag"))
            val row = compose.onNodeWithTag("settings-category-$tag").assertIsDisplayed().assertHasClickAction().getUnclippedBoundsInRoot()
            val icon = compose.onNodeWithTag("settings-icon-$tag", true).getUnclippedBoundsInRoot()
            val arrow = compose.onNodeWithTag("settings-arrow-$tag", true).getUnclippedBoundsInRoot()
            assertTrue("48dp touch target", (row.bottom - row.top).value >= 48f)
            assertTrue("No horizontal clipping", row.left >= viewport.left && row.right <= viewport.right)
            assertTrue("Leading icon and trailing arrow follow locale", if (rtl) icon.left > arrow.right else icon.right < arrow.left)
            assertEquals("Bottom navigation remains fixed", navigation, compose.onNodeWithTag("settings-navigation").getUnclippedBoundsInRoot())
        }
    }
}
