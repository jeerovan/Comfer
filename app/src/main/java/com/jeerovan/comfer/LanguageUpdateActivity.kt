package com.jeerovan.comfer

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield

// LanguageUpdateActivity.kt
class LanguageUpdateActivity : AppCompatActivity() {
    companion object {
        const val EXTRA_LOCALE_TAG = "LOCALE_TAG"
        const val EXTRA_LAUNCH_MAIN = "LAUNCH_MAIN_AFTER_LOCALE_UPDATE"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Optional: Keep it invisible/transparent in your theme (Theme.Translucent.NoTitleBar)

        val localeTag = intent.getStringExtra(EXTRA_LOCALE_TAG)
        val launchMain = intent.getBooleanExtra(EXTRA_LAUNCH_MAIN, false)

        if (localeTag == null) {
            finish()
            return
        }

        // Check if change is actually needed to avoid loops
        val currentTags = AppCompatDelegate.getApplicationLocales().toLanguageTags()
        if (currentTags == localeTag) {
            finishFlow(launchMain)
            return
        }

        lifecycleScope.launch {
            // Wait for onCreate to complete to avoid the WindowManager crash
            yield()

            try {
                val appLocale = LocaleListCompat.forLanguageTags(localeTag)
                AppCompatDelegate.setApplicationLocales(appLocale)

                // IMPORTANT: Wait briefly or let the system recreate handling close this.
                // However, setApplicationLocales usually triggers a recreation of the *calling* activity
                // or the whole app stack.

                // If you must finish this trampoline activity, do it AFTER the call succeeds.
                // Note: The system might kill/recreate this activity immediately after
                // setApplicationLocales, so finish() might be redundant or run on a detached instance.
                finishFlow(launchMain)

            } catch (e: Exception) {
                // Log error
                finish()
            }
        }
    }

    private fun finishFlow(launchMain: Boolean) {
        if (launchMain) {
            startActivity(
                Intent(this, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                },
            )
        }
        finish()
    }
}
