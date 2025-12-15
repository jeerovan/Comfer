package com.jeerovan.comfer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

// LanguageUpdateActivity.kt
class LanguageUpdateActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. Get the locale tag passed from intent
        val localeTag = intent.getStringExtra("LOCALE_TAG")

        if (localeTag != null) {
            // 2. Perform the update from this safe, non-HOME context
            val appLocale = LocaleListCompat.forLanguageTags(localeTag)
            AppCompatDelegate.setApplicationLocales(appLocale)
        }

        // 3. Close immediately. The system will recreate the Launcher in the background.
        finish()
    }
}
