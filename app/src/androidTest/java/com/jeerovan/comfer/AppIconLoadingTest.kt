package com.jeerovan.comfer

import android.content.pm.LauncherApps
import android.os.Process
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class AppIconLoadingTest {
    @Test fun repeatedColdLoadsOfInstalledAppIconsRetainLabelsAndDrawables() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val launcherApps = context.getSystemService(LauncherApps::class.java)
        val activities = launcherApps.getActivityList(null, Process.myUserHandle())
        assertTrue("Installed launcher activities are required", activities.isNotEmpty())
        val colors = WallpaperThemeColors(-1, -16777216, -16777216, -1, -1, -16777216)
        repeat(3) {
            AppIconCache.clearCache()
            activities.chunked(4).forEach { batch ->
                val loaded = batch.map { activity ->
                    async {
                        activity to getAppInfo(context, activity, false, colors, true, null)
                    }
                }.awaitAll()
                loaded.forEach { (activity, app) ->
                    assertNotNull("Icon load failed for ${activity.componentName}", app)
                    assertNotNull(app!!.icon)
                    assertTrue("Missing app label", app.label.isNotBlank())
                    assertEquals(activity.componentName, app.componentName)
                    assertEquals(activity.user, app.user)
                }
            }
        }
    }
}
