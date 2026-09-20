package com.jeerovan.comfer.journals

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

internal data class SpeechLanguageOption(val tag: String, val label: String)

/** ICU locale enumeration and display-name lookup must not run in composition. */
internal suspend fun loadSpeechLanguages(
    displayLocale: Locale,
    availableLocales: () -> Array<Locale> = Locale::getAvailableLocales,
    label: (Locale, Locale) -> String = { locale, display -> locale.getDisplayName(display) },
): List<SpeechLanguageOption> = withContext(Dispatchers.Default) {
    (listOf(displayLocale) + availableLocales().filter { it.language.isNotBlank() && it.country.isEmpty() })
        .distinctBy { it.toLanguageTag() }
        .map { SpeechLanguageOption(it.toLanguageTag(), label(it, displayLocale)) }
        .sortedBy { it.label }
}
