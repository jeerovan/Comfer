package com.jeerovan.comfer.journals

import org.junit.Test
import org.junit.Assert.*

class JournalSpeechTest {
    @Test fun revisedPartialsAndDuplicateFinalsDoNotRepeatWords() {
        val speech = JournalSpeech()
        speech.start("one", "Prefix")
        speech.partial("one", 0, "a")
        speech.partial("one", 0, "a sentence")
        assertEquals("Prefix a sentence", speech.text)
        speech.final("one", 0, "A sentence.")
        speech.final("one", 0, "A sentence.")
        assertEquals("Prefix A sentence.", speech.text)
        assertEquals(JournalSpeech.State.PAUSED, speech.state)
    }
    @Test fun pauseGatesResumeAndRejectsOldSegments() {
        val speech = JournalSpeech()
        speech.start("one", "")
        speech.partial("one", 0, "First")
        speech.stop(true)
        try { speech.resume(); fail() } catch (_: IllegalStateException) { }
        speech.final("one", 0, "First.")
        assertEquals(1, speech.resume())
        speech.final("one", 0, "Duplicate")
        speech.final("one", 1, "Second.")
        speech.stop(false)
        assertEquals("First. Second.", speech.text)
        assertEquals(JournalSpeech.State.FINISHED, speech.state)
    }
    @Test fun timeoutRecoversLatestHypothesisAndRejectsLateCallbacks() {
        val speech = JournalSpeech()
        speech.start("one", "")
        speech.partial("one", 0, "Keep this")
        speech.stop(false)
        speech.timeout()
        speech.final("one", 0, "Too late")
        assertEquals("Keep this", speech.text)
        assertTrue(speech.needsReview)
        speech.start("two", "New")
        speech.final("one", 0, "Previous session")
        assertEquals("New", speech.text)
    }
    @Test fun silenceAndRepeatedInterruptionsCreateNoText() {
        val speech = JournalSpeech()
        speech.start("one", "")
        speech.interrupted(); speech.interrupted()
        assertEquals("", speech.text)
        assertEquals(JournalSpeech.State.FINISHED, speech.state)
    }

    @Test fun cancellingBeforeRecognitionDoesNotTurnTypedPrefixIntoSpeech() {
        val speech = JournalSpeech()
        speech.start("cancel", "  Keep typed prefix  ")
        speech.stop(false); speech.timeout()
        assertFalse(speech.hasSpeechResults)
        assertEquals("  Keep typed prefix  ", speech.text)
    }
    @Test fun missingPartialBundleKeepsLatestWordsAndEmptyFinalNeedsReview() {
        val speech = JournalSpeech()
        speech.start("empty", "")
        speech.partial("empty", 0, "Keep these words")
        speech.partial("empty", 0, "")
        speech.final("empty", 0, "")
        assertEquals("Keep these words", speech.text)
        assertTrue(speech.needsReview)
        assertFalse(speech.segmentConfirmed)
        assertEquals("Keep these words", speech.segmentRecovery)
    }
}
