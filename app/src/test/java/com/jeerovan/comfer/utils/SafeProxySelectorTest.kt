package com.jeerovan.comfer.utils

import java.io.IOException
import java.net.Proxy
import java.net.ProxySelector
import java.net.SocketAddress
import java.net.URI
import org.junit.Assert.assertEquals
import org.junit.Test

class SafeProxySelectorTest {
    @Test
    fun malformedPlatformProxyFallsBackToDirectConnection() {
        val throwingSelector = object : ProxySelector() {
            override fun select(uri: URI?): List<Proxy> {
                throw IllegalArgumentException("invalid proxy port")
            }

            override fun connectFailed(uri: URI?, sa: SocketAddress?, ioe: IOException?) = Unit
        }

        assertEquals(
            listOf(Proxy.NO_PROXY),
            SafeProxySelector(throwingSelector).select(URI("https://example.com")),
        )
    }

    @Test
    fun validPlatformProxySelectionIsPreserved() {
        val expected = listOf(Proxy.NO_PROXY)
        val selector = object : ProxySelector() {
            override fun select(uri: URI?): List<Proxy> = expected

            override fun connectFailed(uri: URI?, sa: SocketAddress?, ioe: IOException?) = Unit
        }

        assertEquals(
            expected,
            SafeProxySelector(selector).select(URI("https://example.com")),
        )
    }
}
