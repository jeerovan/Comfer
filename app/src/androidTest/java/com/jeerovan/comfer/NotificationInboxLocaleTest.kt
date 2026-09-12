package com.jeerovan.comfer

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.jeerovan.comfer.notifications.NotificationInboxActivity
import org.junit.Assert.assertEquals
import org.junit.Test

/** Exercises the real activity context, rather than injecting translated resources. */
class NotificationInboxLocaleTest {
    @Test fun inboxFollowsAppLanguageAcrossReopening() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        var previous = ""
        ActivityScenario.launch(GuideActivity::class.java).use { guide ->
            guide.onActivity { previous = AppCompatDelegate.getApplicationLocales().toLanguageTags() }
        }
        try {
            for (tag in listOf("de", "ar")) {
                setLanguage(tag)
                instrumentation.waitForIdleSync()
                repeat(2) {
                    ActivityScenario.launch(NotificationInboxActivity::class.java).use { inbox ->
                        inbox.onActivity { activity ->
                            assertEquals(tag, activity.resources.configuration.locales[0].language)
                            // Compare with the shipped locale resource without assuming translation wording.
                            val config = android.content.res.Configuration(activity.resources.configuration)
                            config.setLocale(java.util.Locale.forLanguageTag(tag))
                            val localized = activity.createConfigurationContext(config)
                            assertEquals(localized.getString(R.string.notification_inbox), activity.getString(R.string.notification_inbox))
                        }
                        instrumentation.waitForIdleSync()
                        instrumentation.uiAutomation.takeScreenshot()?.let { bitmap ->
                            java.io.File(instrumentation.targetContext.cacheDir, "inbox-$tag.png").outputStream().use {
                                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
                            }
                            bitmap.recycle()
                        }
                    }
                }
            }
        } finally {
            setLanguage(previous)
            instrumentation.waitForIdleSync()
        }
    }

    private fun setLanguage(tag: String) {
        ActivityScenario.launch(GuideActivity::class.java).use { guide ->
            guide.onActivity {
                AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
            }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        }
    }
}
