package com.jeerovan.comfer

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.v2.createComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class DrawerTitleContrastUiTest {
    @get:Rule val compose = createComposeRule()
    @Test fun wallpaperSamplesReloadWhenSameFileGetsANewVersion() {
        val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        val file = java.io.File.createTempFile("drawer-contrast", ".png", context.cacheDir)
        fun write(color: Int) {
            val bitmap = android.graphics.Bitmap.createBitmap(32, 16, android.graphics.Bitmap.Config.ARGB_8888)
            try { bitmap.eraseColor(color); file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) } }
            finally { bitmap.recycle() }
        }
        var version by mutableIntStateOf(0)
        var loaded: DrawerContrast? = null
        try {
            write(android.graphics.Color.WHITE)
            compose.setContent { loaded = rememberDrawerContrast(file.absolutePath, version, false) }
            compose.waitUntil(10000) { loaded?.samples?.pixels?.firstOrNull() == android.graphics.Color.WHITE }
            write(android.graphics.Color.BLACK)
            compose.runOnIdle { version++ }
            compose.waitUntil(10000) { loaded?.samples?.pixels?.firstOrNull() == android.graphics.Color.BLACK }
            assertTrue(requireNotNull(loaded?.samples).pixels.size <= 512 * 512)
        } finally { file.delete() }
    }
    @Test fun automaticColorFreezesDuringScrollAndMissingWallpaperUsesWhiteFallback() {
        compose.mainClock.autoAdvance = false
        var contrast by mutableStateOf<DrawerContrast?>(DrawerContrast(WallpaperSamples(1,1,intArrayOf(-1)), null, false))
        var scrolling by mutableStateOf(false)
        var actual = Color.Unspecified
        compose.setContent { MaterialTheme { CompositionLocalProvider(LocalDrawerContrast provides contrast, LocalDrawerScrolling provides scrolling) {
            val (color, position) = adaptiveDrawerTitle()
            SideEffect { actual = color }
            Text("App name", color = color, modifier = position.fillMaxWidth())
        } } }
        compose.mainClock.advanceTimeBy(300)
        compose.runOnIdle { assertEquals(Color.Black, actual); scrolling = true; contrast = DrawerContrast(WallpaperSamples(1,1,intArrayOf(0xff000000.toInt())),null,false) }
        compose.mainClock.advanceTimeBy(500)
        compose.runOnIdle { assertEquals(Color.Black, actual); scrolling = false }
        compose.mainClock.advanceTimeBy(300)
        compose.runOnIdle { assertEquals(Color.White, actual); contrast = null }
        compose.mainClock.advanceTimeBy(32)
        compose.runOnIdle { assertEquals(Color.White, actual) }
    }
}
