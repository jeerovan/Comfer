package com.jeerovan.comfer

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield

// LanguageUpdateActivity.kt
class LanguageUpdateActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Optional: Keep it invisible/transparent in your theme (Theme.Translucent.NoTitleBar)

        val localeTag = intent.getStringExtra("LOCALE_TAG")

        if (localeTag.isNullOrEmpty()) {
            finish()
            return
        }

        // Check if change is actually needed to avoid loops
        val currentTags = AppCompatDelegate.getApplicationLocales().toLanguageTags()
        if (currentTags == localeTag) {
            finish()
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
                finish()

            } catch (e: Exception) {
                // Log error
                finish()
            }
        }
    }
}

