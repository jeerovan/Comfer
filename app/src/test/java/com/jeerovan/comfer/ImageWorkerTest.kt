package com.jeerovan.comfer

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ImageWorkerTest {
    @Test
    fun fetchFailureRetriesWithoutDownload(): Unit = runBlocking {
        var downloaded = false
        val outcome = runImageWorkPipeline(
            fetch = { false },
            download = { downloaded = true; true },
        )

        assertEquals(ImageWorkOutcome.RETRY, outcome)
        assertFalse(downloaded)
    }

    @Test
    fun downloadFailureRetries(): Unit = runBlocking {
        assertEquals(
            ImageWorkOutcome.RETRY,
            runImageWorkPipeline(fetch = { true }, download = { false }),
        )
    }

    @Test
    fun successfulFixtureSucceeds(): Unit = runBlocking {
        assertEquals(
            ImageWorkOutcome.SUCCESS,
            runImageWorkPipeline(fetch = { true }, download = { true }),
        )
    }

    @Test(expected = CancellationException::class)
    fun cancellationIsNotConvertedToRetry(): Unit = runBlocking {
        runImageWorkPipeline(
            fetch = { throw CancellationException("cancel fixture") },
            download = { true },
        )
    }
}
