package com.jeerovan.comfer.utils
import android.content.Context
import java.io.InputStream
import java.security.KeyStore
import java.security.cert.CertificateFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

object SSLHelper {

    fun createSslSocketFactory(context: Context, certResId: Int): Pair<SSLSocketFactory, X509TrustManager> {

        val certificateFactory = CertificateFactory.getInstance("X.509")
        val inputStream: InputStream = context.resources.openRawResource(certResId)
        val certificates = certificateFactory.generateCertificates(inputStream)
        inputStream.close()

        // Create a KeyStore containing our trusted CAs
        val keyStoreType = KeyStore.getDefaultType()
        val keyStore = KeyStore.getInstance(keyStoreType)
        keyStore.load(null, null)
        certificates.forEachIndexed { index, certificate ->
            val alias = "ca_$index"
            keyStore.setCertificateEntry(alias, certificate)
        }

        // Create a TrustManager that trusts the CAs in our KeyStore
        val tmfAlgorithm = TrustManagerFactory.getDefaultAlgorithm()
        val tmf = TrustManagerFactory.getInstance(tmfAlgorithm)
        tmf.init(keyStore)

        val customTrustManager = tmf.trustManagers[0] as X509TrustManager

        // Create an SSLContext that uses our combined TrustManager
        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(null,tmf.trustManagers,null)

        return Pair(sslContext.socketFactory, customTrustManager)
    }
}
