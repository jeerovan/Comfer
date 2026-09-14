package com.jeerovan.comfer

import android.content.ComponentName
import android.content.ContextWrapper
import android.content.Intent
import android.content.res.Resources
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.UserHandle
import android.util.AndroidRuntimeException
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.test.platform.app.InstrumentationRegistry
import com.jeerovan.comfer.ui.theme.resilientColorScheme
import com.jeerovan.comfer.utils.CommonUtil
import org.junit.Assert.*
import org.junit.Test

class Release50RegressionTest {
    @Test fun notificationDrawableWithNoIntrinsicSizeRendersBoundedSnapshot() {
        val drawable = ColorDrawable(Color.RED)
        drawable.setBounds(5, 6, 20, 30)
        val bitmap = requireNotNull(drawable.toOwnedBitmap())
        try {
            assertEquals(1, bitmap.width)
            assertEquals(1, bitmap.height)
            assertEquals(Color.RED, bitmap.getPixel(0, 0))
            assertEquals(5, drawable.bounds.left)
            assertEquals(30, drawable.bounds.bottom)
        } finally { bitmap.recycle() }
    }

    @Test fun missingDynamicResourcesUseMatchingFallbackWhileValidPaletteIsPreserved() {
        for (fallback in listOf(lightColorScheme(), darkColorScheme())) {
            assertSame(fallback, resilientColorScheme(fallback) { throw Resources.NotFoundException("OEM color missing") })
            val dynamic = lightColorScheme()
            assertSame(dynamic, resilientColorScheme(fallback) { dynamic })
        }
    }

    @Test fun failedExternalActivityLaunchDoesNotCrashSettings() {
        val context = object : ContextWrapper(InstrumentationRegistry.getInstrumentation().targetContext) {
            override fun startActivity(intent: Intent) { throw AndroidRuntimeException("Activity could not be started") }
        }
        InstrumentationRegistry.getInstrumentation().runOnMainSync { CommonUtil.openUrl("https://play.google.com", context) }
    }

    @Test fun duplicateSearchEntriesCollapseWithoutMergingActivitiesOrProfiles() {
        fun app(activity: String, profile: Int) = AppInfo(null, null, activity, 1f, "example.app", null,
            ComponentName("example.app", activity), UserHandle.getUserHandleForUid(profile * 100000))
        val personal = app("Main", 0)
        val work = app("Main", 10)
        val other = app("Other", 0)
        assertEquals(listOf(personal, work, other), uniqueSearchApps(listOf(personal, personal.copy(), work, other, work.copy())))
        assertTrue(uniqueSearchApps(emptyList()).isEmpty())
    }
}
