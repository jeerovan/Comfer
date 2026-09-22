package com.jeerovan.comfer.tasks

import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicBoolean

/** The deadline must not wait for a blocked Binder/file call to acknowledge cancellation. */
internal fun launchReminderBroadcast(
    scope: CoroutineScope,
    finish: () -> Unit,
    onFailure: (Throwable) -> Unit,
    timeoutMillis: Long = 6_000,
    work: suspend () -> Unit,
): Job {
    val finished = AtomicBoolean()
    fun finishOnce() { if (finished.compareAndSet(false, true)) finish() }
    lateinit var deadline: Job
    val job = scope.launch(Dispatchers.IO, start = CoroutineStart.LAZY) {
        try { work() }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { onFailure(e) }
        finally { deadline.cancel(); finishOnce() }
    }
    deadline = scope.launch(Dispatchers.Default) {
        delay(timeoutMillis)
        // Finish independently: cancelAndJoin/withTimeout would wait for blocking IO.
        job.cancel()
        finishOnce()
    }
    job.start()
    return job
}
