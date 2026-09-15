package com.jeerovan.comfer.journals

/** Pure capture protocol. The Android adapter owns mic commands; no callbacks can revive a session. */
class JournalSpeech {
    enum class State { IDLE, STARTING, LISTENING, FINALIZING, PAUSED, FINISHED }
    var state = State.IDLE; private set
    var session = ""; private set
    var segment = 0; private set
    var committed = ""; private set
    var provisional = ""; private set
    var needsReview = false; private set
    var hasSpeechResults = false; private set
    var reviewReason: String? = null; private set
    private var segmentPrefix = ""
    var segmentConfirmed = false; private set
    var segmentRecovery = ""; private set
    val segmentFinal: String get() = if(segmentConfirmed) committed.removePrefix(segmentPrefix).trimStart() else ""
    private var settled = false
    private var pauseRequested = false
    val text get() = join(committed, provisional)
    fun start(id: String, prefix: String) {
        check(state == State.IDLE || state == State.FINISHED)
        session = id; segment = 0; committed = prefix; provisional = ""
        segmentPrefix = prefix; segmentConfirmed = false; segmentRecovery = ""
        needsReview = false; hasSpeechResults = false; reviewReason = null; settled = false; state = State.STARTING
    }
    fun ready(id: String, part: Int) {
        if (matches(id, part) && state == State.STARTING) state = State.LISTENING
    }
    fun partial(id: String, part: Int, words: String) {
        if (matches(id, part) && accepting() && !settled && words.isNotBlank()) { hasSpeechResults = true; provisional = words.trim(); segmentRecovery = provisional }
    }
    fun final(id: String, part: Int, words: String) {
        if (!matches(id, part) || !accepting() || settled) return
        if(words.isNotBlank()) hasSpeechResults = true
        segmentConfirmed = words.isNotBlank()
        if (segmentConfirmed) segmentRecovery = "" else if(provisional.isNotBlank()) {
            needsReview = true; reviewReason = "Recognition ended without a final result"
        }
        committed = join(committed, words.trim().ifEmpty { provisional })
        provisional = ""; settled = true
        if (state == State.FINALIZING) finishDrain()
        // Providers often stop at their final result. Explicit Resume avoids hidden restarts.
        else state = State.PAUSED
    }
    fun stop(pause: Boolean) {
        if (state == State.IDLE || state == State.FINISHED) return
        pauseRequested = pause
        if (state == State.PAUSED && settled) { state = if (pause) State.PAUSED else State.FINISHED; return }
        state = State.FINALIZING
    }
    fun timeout() {
        if (state != State.FINALIZING) return
        if (!settled && provisional.isNotBlank()) {
            committed = join(committed, provisional); needsReview = true; reviewReason = "Recognition ended without a final result"
        }
        provisional = ""; settled = true; finishDrain()
    }
    fun interrupted(reason: String = "Recording was interrupted") { needsReview = true; stop(false); timeout(); reviewReason = reason }
    fun resume(): Int {
        check(state == State.PAUSED)
        segment++; settled = false; provisional = ""; segmentPrefix = committed; segmentConfirmed = false; segmentRecovery = ""; state = State.STARTING
        return segment
    }
    private fun finishDrain() { state = if (pauseRequested) State.PAUSED else State.FINISHED }
    private fun matches(id: String, part: Int) = id == session && part == segment
    private fun accepting() = state == State.STARTING || state == State.LISTENING || state == State.FINALIZING
    private fun join(a: String, b: String) = listOf(a, b).filter { it.isNotBlank() }.joinToString(" ")
}
