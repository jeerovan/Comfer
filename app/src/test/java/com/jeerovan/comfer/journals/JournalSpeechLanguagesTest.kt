package com.jeerovan.comfer.journals

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.util.Locale

class JournalSpeechLanguagesTest {
    @Test fun labelsAreLoadedOncePerLanguageOffTheCallingThread() = runBlocking {
        val caller = Thread.currentThread()
        val calls = mutableListOf<String>()
        val result = loadSpeechLanguages(Locale.US, {
            assertNotSame(caller, Thread.currentThread())
            arrayOf(Locale.FRENCH, Locale.FRENCH, Locale.GERMAN, Locale.CANADA, Locale.ROOT)
        }) { locale, display ->
            assertNotSame(caller, Thread.currentThread())
            assertEquals(Locale.US, display)
            calls.add(locale.toLanguageTag())
            mapOf("en-US" to "Zulu", "fr" to "Alpha", "de" to "Beta").getValue(locale.toLanguageTag())
        }
        assertEquals(listOf("fr", "de", "en-US"), result.map { it.tag })
        assertEquals(listOf("Alpha", "Beta", "Zulu"), result.map { it.label })
        assertEquals(listOf("en-US", "fr", "de"), calls)
    }

    @Test fun emptyInventoryKeepsTheCurrentLocale() = runBlocking {
        assertEquals(listOf(SpeechLanguageOption("en-US", "English")),
            loadSpeechLanguages(Locale.US, { emptyArray() }) { _, _ -> "English" })
    }
}
