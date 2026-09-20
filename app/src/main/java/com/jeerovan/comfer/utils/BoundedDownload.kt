package com.jeerovan.comfer.utils

import io.ktor.client.HttpClient
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.contentLength
import io.ktor.http.isSuccess
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.CancellationException
import java.io.File

/** Stream within the response lifetime; ordinary get() buffers before returning. */
internal suspend fun downloadWithinLimit(
    client: HttpClient,
    url: String,
    destination: File,
    maxBytes: Long,
): Boolean {
    require(maxBytes >= 0)
    var complete = false
    try {
        complete = client.prepareGet(url).execute { response ->
            val channel = response.bodyAsChannel()
            try {
                if (!response.status.isSuccess() || (response.contentLength() ?: 0) > maxBytes) {
                    return@execute false
                }
                destination.outputStream().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var total = 0L
                    while (true) {
                        val count = channel.readAvailable(buffer, 0, buffer.size)
                        if (count < 0) break
                        if (count > maxBytes - total) return@execute false
                        output.write(buffer, 0, count)
                        total += count
                    }
                }
                true
            } finally {
                channel.cancel(null)
            }
        }
        return complete
    } catch (e: CancellationException) {
        throw e
    } finally {
        // This is the caller's staging file, never the currently applied wallpaper.
        if (!complete) destination.delete()
    }
}
