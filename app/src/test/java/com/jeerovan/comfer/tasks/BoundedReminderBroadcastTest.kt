package com.jeerovan.comfer.tasks

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class BoundedReminderBroadcastTest {
    @Test fun stalledStartupFinishesOnceAndCancelsWaiter() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val finished = CompletableDeferred<Unit>()
        val count = AtomicInteger()
        try {
            val job = launchReminderBroadcast(scope, { count.incrementAndGet(); finished.complete(Unit) },
                { throw AssertionError(it) }, timeoutMillis = 100) { awaitCancellation() }
            withTimeout(2_000) { finished.await(); job.join() }
            assertTrue(job.isCancelled)
            assertEquals(1, count.get())
        } finally { scope.cancel() }
    }

    @Test fun blockingIoCannotHoldBroadcastPastDeadline() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val finished = CompletableDeferred<Unit>()
        val count = AtomicInteger()
        try {
            val job = launchReminderBroadcast(scope, { count.incrementAndGet(); finished.complete(Unit) },
                { throw AssertionError(it) }, timeoutMillis = 150) {
                entered.countDown()
                check(release.await(5, TimeUnit.SECONDS))
            }
            assertTrue(entered.await(2, TimeUnit.SECONDS))
            withTimeout(2_000) { finished.await() }
            assertFalse("Deadline must finish without joining blocked work", job.isCompleted)
            release.countDown()
            withTimeout(2_000) { job.join() }
            assertEquals(1, count.get())
        } finally { release.countDown(); scope.cancel() }
    }

    @Test fun successfulWorkFinishesWithoutWaitingForDeadline() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val count = AtomicInteger()
        try {
            val job = launchReminderBroadcast(scope, { count.incrementAndGet() }, { throw AssertionError(it) }) {}
            withTimeout(2_000) { job.join() }
            assertEquals(1, count.get())
        } finally { scope.cancel() }
    }

    @Test fun failedWorkFinishesAndReportsOriginalFailure() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val count = AtomicInteger()
        val failure = IllegalStateException("storage unavailable")
        val reported = CompletableDeferred<Throwable>()
        try {
            val job = launchReminderBroadcast(scope, { count.incrementAndGet() }, { reported.complete(it) }) { throw failure }
            withTimeout(2_000) { job.join() }
            assertSame(failure, reported.await())
            assertEquals(1, count.get())
        } finally { scope.cancel() }
    }
}
