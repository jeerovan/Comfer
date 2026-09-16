package com.jeerovan.comfer.journals

import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.SpeechRecognizer
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import org.junit.Assert.*

class JournalSpeechControllerTest {
    private class Fake(private val readyOnStart: Boolean = true) : JournalRecognizer {
        lateinit var listener: RecognitionListener
        var stops = 0; var cancels = 0; var destroys = 0; var starts = 0
        override fun setRecognitionListener(listener: RecognitionListener) { this.listener = listener }
        override fun startListening(intent: Intent) { starts++; if (readyOnStart) listener.onReadyForSpeech(Bundle()) }
        override fun stopListening() { stops++ }
        override fun cancel() { cancels++; listener.onError(SpeechRecognizer.ERROR_CLIENT) }
        override fun destroy() { destroys++ }
        fun partial(text: String) { listener.onPartialResults(Bundle().apply { putStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION, arrayListOf(text)) }) }
        fun final(text: String) { listener.onResults(Bundle().apply { putStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION, arrayListOf(text)) }) }
    }
    @Test fun pauseActuallyStopsBackendAndLateCancelCannotFinishResumedSession() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val engines = mutableListOf<Fake>()
            val controller = JournalSpeechController(instrumentation.targetContext, { _, _ -> }, { true }, { true }, { Fake().also(engines::add) })
            controller.start("session", "Prefix", "en", false)
            val first = engines.single()
            first.partial("First")
            controller.stop(true)
            assertEquals(1, first.stops)
            first.final("First.")
            assertEquals(1, first.cancels)
            assertEquals(1, first.destroys)
            assertEquals(JournalSpeech.State.PAUSED, controller.protocol.state)
            controller.resume()
            first.final("Stale result")
            engines.last().final("Second.")
            controller.stop()
            assertEquals("Prefix First. Second.", controller.protocol.text)
            assertFalse(controller.active)
            assertTrue(engines.all { it.destroys == 1 })
        }
    }

    @Test fun providerEndWaitsForResultWithoutSendingAnotherStop() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val fake = Fake()
            val controller = JournalSpeechController(instrumentation.targetContext, { _, _ -> }, { true }, { true }, { fake })
            controller.start("natural-end", "", "en", false)
            fake.partial("Keep these words")
            fake.listener.onEndOfSpeech()
            assertEquals("Provider already stopped listening", 0, fake.stops)
            assertEquals(JournalSpeech.State.FINALIZING, controller.protocol.state)
            // Stop while a natural final result is pending must finish, not pause,
            // without sending a redundant provider command.
            controller.stop()
            assertEquals(0, fake.stops)
            fake.final("Keep these words.")
            assertEquals("Keep these words.", controller.protocol.text)
            assertEquals(JournalSpeech.State.FINISHED, controller.protocol.state)
            assertFalse(controller.protocol.needsReview)
        }
    }

    @Test fun repeatedStopOnlySendsOneProviderCommand() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val fake = Fake()
            val controller = JournalSpeechController(instrumentation.targetContext, { _, _ -> }, { true }, { true }, { fake })
            controller.start("repeat-stop", "", "en", false)
            controller.stop(true)
            controller.stop()
            assertEquals(1, fake.stops)
            fake.final("Saved once")
            assertEquals(JournalSpeech.State.FINISHED, controller.protocol.state)
        }
    }

    @Test fun stoppingBeforeProviderIsReadyCancelsWithoutClientError() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val fake = Fake(readyOnStart = false)
            val controller = JournalSpeechController(instrumentation.targetContext, { _, _ -> }, { true }, { true }, { fake })
            controller.start("starting-stop", "Existing draft", "en", false)
            controller.stop()
            assertEquals(0, fake.stops)
            assertEquals(1, fake.destroys)
            assertEquals(JournalSpeech.State.FINISHED, controller.protocol.state)
            assertEquals("Existing draft", controller.protocol.text)
            assertFalse(controller.protocol.needsReview)
            fake.listener.onReadyForSpeech(Bundle())
            fake.listener.onError(SpeechRecognizer.ERROR_CLIENT)
            assertEquals(JournalSpeech.State.FINISHED, controller.protocol.state)
            assertFalse(controller.protocol.needsReview)
        }
    }

    @Test fun noProviderRejectsStartWithoutCreatingRecognizer() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val fake = Fake()
            val controller = JournalSpeechController(instrumentation.targetContext, { _, _ -> }, { false }, { false }, { fake })
            assertFalse(controller.recognitionAvailable())
            try { controller.start("missing", "", "en", true); fail() } catch (e: IllegalArgumentException) {
                assertTrue(e.message.orEmpty().contains("No speech recognition service"))
            }
            assertEquals(0, fake.starts)
            assertFalse(controller.active)
        }
    }
    @Test fun explicitProviderChoiceBypassesAvailableOnDeviceRecognizerAndSurvivesResume() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val choices = mutableListOf<Boolean>()
            val engines = mutableListOf<Fake>()
            val controller = JournalSpeechController(instrumentation.targetContext, { _, _ -> }, { true }, { true }, { network ->
                choices += network
                Fake().also(engines::add)
            })
            controller.start("default-private", "", "hi-IN", false)
            assertEquals(listOf(false), choices)
            controller.interrupt()
            controller.start("explicit-provider", "", "hi-IN", true)
            assertEquals(listOf(false, true), choices)
            engines.last().final("First segment")
            controller.resume()
            assertEquals(listOf(false, true, true), choices)
            controller.interrupt()
        }
    }

    @Test fun unavailableSelectedProviderDoesNotSilentlySwitchToOnDevice() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val fake = Fake()
            val controller = JournalSpeechController(instrumentation.targetContext, { _, _ -> }, { true }, { false }, { fake })
            try { controller.start("missing-selected", "", "hi-IN", true); fail() } catch (_: IllegalArgumentException) { }
            assertEquals(0, fake.starts)
        }
    }
    @Test fun interruptionClosesBackendAndNetworkNeverStartsWithoutConsent() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val fake = Fake()
            val controller = JournalSpeechController(instrumentation.targetContext, { _, _ -> }, { false }, { true }, { fake })
            try { controller.start("no-consent", "", "en", false); fail() } catch (_: IllegalArgumentException) { }
            assertEquals(0, fake.starts)
            controller.start("consent", "", "en", true)
            fake.partial("Recover")
            controller.interrupt()
            assertEquals(1, fake.cancels)
            assertEquals(1, fake.destroys)
            assertEquals("Recover", controller.protocol.text)
            assertFalse(controller.active)
            assertTrue(controller.protocol.needsReview)
        }
    }

    @Test fun stopTimeoutClosesBackendAndRejectsLateFinal() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val fake = Fake()
        lateinit var controller: JournalSpeechController
        instrumentation.runOnMainSync {
            controller = JournalSpeechController(instrumentation.targetContext, { _, _ -> }, { true }, { true }, { fake })
            controller.start("timeout", "", "en", false)
            fake.partial("Keep recovery")
            controller.stop()
            assertEquals(1, fake.stops)
        }
        Thread.sleep(2200)
        instrumentation.runOnMainSync {
            assertEquals(1, fake.destroys)
            assertFalse(controller.active)
            assertTrue(controller.protocol.needsReview)
            fake.final("Too late")
            assertEquals("Keep recovery", controller.protocol.text)
        }
    }
}
