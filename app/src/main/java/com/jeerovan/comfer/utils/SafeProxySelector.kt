package com.jeerovan.comfer.utils

import java.io.IOException
import java.net.Proxy
import java.net.ProxySelector
import java.net.SocketAddress
import java.net.URI

/**
 * Some OEM builds expose an invalid system proxy port. The platform's default
 * selector then throws on OkHttp's executor instead of returning a failed
 * route. Fall back to a direct connection for that malformed configuration.
 */
internal class SafeProxySelector(
    private val delegate: ProxySelector?,
) : ProxySelector() {
    override fun select(uri: URI?): List<Proxy> {
        if (uri == null) return DIRECT
        return try {
            delegate?.select(uri)?.takeIf { it.isNotEmpty() } ?: DIRECT
        } catch (_: RuntimeException) {
            DIRECT
        }
    }

    override fun connectFailed(uri: URI?, sa: SocketAddress?, ioe: IOException?) {
        try {
            delegate?.connectFailed(uri, sa, ioe)
        } catch (_: RuntimeException) {}
    }

    companion object {
        private val DIRECT = listOf(Proxy.NO_PROXY)
    }
}
