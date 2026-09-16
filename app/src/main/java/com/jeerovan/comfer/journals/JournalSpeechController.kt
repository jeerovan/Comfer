package com.jeerovan.comfer.journals

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

internal interface JournalRecognizer {
    fun setRecognitionListener(listener: RecognitionListener)
    fun startListening(intent: Intent)
    fun stopListening()
    fun cancel()
    fun destroy()
}

private class AndroidJournalRecognizer(private val recognizer: SpeechRecognizer) : JournalRecognizer {
    override fun setRecognitionListener(listener: RecognitionListener) = recognizer.setRecognitionListener(listener)
    override fun startListening(intent: Intent) = recognizer.startListening(intent)
    override fun stopListening() = recognizer.stopListening()
    override fun cancel() = recognizer.cancel()
    override fun destroy() = recognizer.destroy()
}

/** Main-thread adapter; every recognizer instance closes over its immutable segment token. */
internal class JournalSpeechController(
    private val context: Context,
    private val update: (JournalSpeech, Float?) -> Unit,
    private val deviceAvailable: () -> Boolean = { Build.VERSION.SDK_INT >= 31 && SpeechRecognizer.isOnDeviceRecognitionAvailable(context) },
    private val providerAvailable: () -> Boolean = { SpeechRecognizer.isRecognitionAvailable(context) },
    private val recognizerFactory: (Boolean) -> JournalRecognizer = { network ->
        AndroidJournalRecognizer(if (!network && Build.VERSION.SDK_INT >= 31) SpeechRecognizer.createOnDeviceSpeechRecognizer(context) else SpeechRecognizer.createSpeechRecognizer(context))
    },
) {
    val protocol = JournalSpeech()
    private val handler = Handler(Looper.getMainLooper())
    private var recognizer: JournalRecognizer? = null
    private var network = false
    private var language = "en"
    private var deadline: Runnable? = null
    private var generation = 0
    val active get() = protocol.state !in setOf(JournalSpeech.State.IDLE, JournalSpeech.State.FINISHED)
    fun onDeviceAvailable() = deviceAvailable()
    fun systemProviderAvailable() = providerAvailable()
    fun recognitionAvailable() = onDeviceAvailable() || providerAvailable()
    fun start(id: String, prefix: String, language: String, networkConsent: Boolean) {
        check(!active)
        require(recognitionAvailable()) { "No speech recognition service is available. Install or enable a speech provider in Android settings." }
        require(onDeviceAvailable() || networkConsent) { "Network dictation requires your permission" }
        require(!networkConsent || systemProviderAvailable()) { "The selected device speech provider is unavailable" }
        // Explicit consent selects the default provider even if the dedicated
        // on-device service advertises availability but fails at runtime.
        this.network = networkConsent
        this.language = language
        protocol.start(id, prefix)
        listen()
    }
    fun resume() { protocol.resume(); listen() }
    private fun listen() {
        release()
        val token = ++generation
        val id = protocol.session
        val part = protocol.segment
        try {
            recognizer = recognizerFactory(network)
            recognizer!!.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) { if (generation != token) return; protocol.ready(id, part); update(protocol, null) }
                override fun onBeginningOfSpeech() = Unit
                override fun onRmsChanged(rmsdB: Float) { if (generation == token && protocol.session == id && protocol.segment == part && protocol.state == JournalSpeech.State.LISTENING) update(protocol, rmsdB) }
                override fun onBufferReceived(buffer: ByteArray?) = Unit
                override fun onEndOfSpeech() {
                    if (generation != token || protocol.session != id || protocol.segment != part) return
                    if (protocol.state !in setOf(JournalSpeech.State.STARTING, JournalSpeech.State.LISTENING)) return
                    // The provider has already stopped its microphone. Wait for its
                    // final result; another stopListening call can cause ERROR_CLIENT.
                    protocol.stop(true)
                    awaitFinalResult(10000)
                    update(protocol, null)
                }
                override fun onError(error: Int) {
                    if (generation != token || protocol.session != id || protocol.segment != part || !active) return
                    protocol.interrupted(when (error) {
                        SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED -> "This speech language is not supported"
                        SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> "This speech language is not available on the device"
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is unavailable"
                        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Speech recognition lost its network connection"
                        SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "The provider did not recognize speech"
                        SpeechRecognizer.ERROR_CLIENT, SpeechRecognizer.ERROR_SERVER_DISCONNECTED -> if (!network) {
                            "On-device dictation stopped (error $error). Reopen dictation and try Use device speech provider."
                        } else "The device speech provider could not complete dictation (error $error). Check its settings and try again."
                        else -> "Speech recognition stopped (provider error $error)"
                    }); release(); update(protocol, null)
                }
                override fun onResults(results: Bundle?) {
                    if (generation != token) return
                    protocol.final(id, part, results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty())
                    if (protocol.state == JournalSpeech.State.PAUSED || protocol.state == JournalSpeech.State.FINISHED) release()
                    update(protocol, null)
                }
                override fun onPartialResults(partialResults: Bundle?) {
                    if (generation != token) return
                    protocol.partial(id, part, partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty())
                    update(protocol, null)
                }
                override fun onEvent(eventType: Int, params: Bundle?) = Unit
            })
            recognizer!!.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
                .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1))
            // Starting cannot wait indefinitely for a missing/broken provider.
            if (protocol.state == JournalSpeech.State.STARTING) {
                deadline = Runnable {
                    if (protocol.state == JournalSpeech.State.STARTING) {
                        protocol.interrupted("The speech provider did not start. Check your device's speech provider and microphone settings, then try again.")
                        release(); update(protocol, null)
                    }
                }.also { handler.postDelayed(it, 10000) }
            }
            update(protocol, null)
        } catch (_: Exception) { protocol.interrupted(); release(); update(protocol, null) }
    }
    fun stop(pause: Boolean = false) {
        if (!active) return
        if (protocol.state == JournalSpeech.State.STARTING) {
            // There is no ready microphone session to drain yet. Cancel it and
            // ignore late provider callbacks rather than asking it to stop listening.
            protocol.stop(pause)
            protocol.timeout()
            release(); update(protocol, null)
            return
        }
        val alreadyStopped = protocol.state == JournalSpeech.State.FINALIZING || protocol.state == JournalSpeech.State.PAUSED
        protocol.stop(pause)
        if (protocol.state == JournalSpeech.State.FINISHED) { release(); update(protocol, null); return }
        if (!alreadyStopped) {
            try { recognizer?.stopListening() } catch (_: Exception) { }
            if (protocol.state == JournalSpeech.State.FINALIZING) awaitFinalResult(2000)
        }
        update(protocol, null)
    }
    private fun awaitFinalResult(timeoutMillis: Long) {
        deadline?.let(handler::removeCallbacks)
        deadline = Runnable { protocol.timeout(); release(); update(protocol, null) }.also { handler.postDelayed(it, timeoutMillis) }
    }
    fun interrupt() { if (active) { protocol.interrupted(); release(); update(protocol, null) } else release() }
    private fun release() {
        generation++
        deadline?.let(handler::removeCallbacks); deadline = null
        val old = recognizer; recognizer = null
        try { old?.cancel() } catch (_: Exception) { }
        try { old?.destroy() } catch (_: Exception) { }
    }
}
