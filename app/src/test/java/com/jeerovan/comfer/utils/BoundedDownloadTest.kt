package com.jeerovan.comfer.utils

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.net.ServerSocket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

class BoundedDownloadTest {
    private fun withResponse(response: String, test: suspend (HttpClient, String, File) -> Unit) {
        val server = ServerSocket(0)
        val release = CountDownLatch(1)
        val worker = thread(isDaemon = true) {
            server.accept().use { socket ->
                val reader = socket.getInputStream().bufferedReader()
                while (!reader.readLine().isNullOrEmpty()) { }
                socket.getOutputStream().apply {
                    write(response.toByteArray())
                    flush()
                }
                release.await(10, TimeUnit.SECONDS)
            }
        }
        val file = File.createTempFile("bounded-download", ".tmp")
        val client = HttpClient(OkHttp)
        try {
            runBlocking { withTimeout(5_000) { test(client, "http://127.0.0.1:${server.localPort}/", file) } }
        } finally {
            release.countDown()
            client.close()
            server.close()
            worker.join(2_000)
            file.delete()
        }
    }

    @Test fun rejectsDeclaredOversizeWithoutWaitingForBody() = withResponse(
        "HTTP/1.1 200 OK\r\nContent-Length: 1000000000\r\n\r\n",
    ) { client, url, file ->
        assertFalse(downloadWithinLimit(client, url, file, 8))
        assertFalse(file.exists())
    }

    @Test fun stopsChunkedResponseBeforeServerFinishesAndDeletesPartialFile() = withResponse(
        "HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\n\r\n9\r\n123456789\r\n",
    ) { client, url, file ->
        assertFalse(downloadWithinLimit(client, url, file, 8))
        assertFalse(file.exists())
    }

    @Test fun exactLimitRetainsCompleteBytes() = withResponse(
        "HTTP/1.1 200 OK\r\nContent-Length: 8\r\n\r\n12345678",
    ) { client, url, file ->
        assertTrue(downloadWithinLimit(client, url, file, 8))
        assertEquals("12345678", file.readText())
    }

    @Test fun failedHttpResponseDeletesStagingFile() = withResponse(
        "HTTP/1.1 503 Unavailable\r\nContent-Length: 0\r\n\r\n",
    ) { client, url, file ->
        assertFalse(downloadWithinLimit(client, url, file, 8))
        assertFalse(file.exists())
    }

    @Test fun cancellationPropagatesAndDeletesPartialFile() = withResponse(
        "HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\n\r\n1\r\nx\r\n",
    ) { client, url, file ->
        try {
            withTimeout(500) { downloadWithinLimit(client, url, file, 8) }
            fail("Expected cancellation")
        } catch (_: TimeoutCancellationException) {
            assertFalse(file.exists())
        }
    }
}
