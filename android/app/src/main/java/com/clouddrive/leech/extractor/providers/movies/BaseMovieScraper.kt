package com.clouddrive.leech.extractor.providers.movies

import com.clouddrive.leech.plugins.NativeVpnPlugin
import com.clouddrive.leech.vpn.net.UniversalAntiCensorDns
import com.google.gson.JsonParser
import okhttp3.ConnectionPool
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.InetAddress
import java.net.Proxy
import java.net.ProxySelector
import java.net.SocketAddress
import java.net.URI
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/**
 * Mobile Carrier DNS-Bypass & Fallback Resolver.
 * Resolves blocked movie portals & CDNs on Mobile Data (Dialog, Mobitel, Airtel, Hutch, SLT)
 * via direct Cloudflare (1.1.1.1) and Google (8.8.8.8) DNS over HTTPS.
 */
class CarrierBypassDns : Dns {
    companion object {
        fun clearDnsCache() {
            UniversalAntiCensorDns.instance.clearCache()
        }
    }

    override fun lookup(hostname: String): List<InetAddress> {
        return UniversalAntiCensorDns.instance.lookup(hostname)
    }
}

/**
 * Base scraper utility providing HTTP transport, shared headers, and connection pooling.
 */
abstract class BaseMovieScraper(
    protected val okHttpClient: OkHttpClient = defaultClient
) {
    companion object {
        val userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36"

        private val trustAllCerts = arrayOf<TrustManager>(
            object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
            }
        )

        private val sslContext = SSLContext.getInstance("SSL").apply {
            init(null, trustAllCerts, SecureRandom())
        }

        val defaultClient: OkHttpClient = OkHttpClient.Builder()
            .dns(UniversalAntiCensorDns.instance)
            .proxySelector(object : ProxySelector() {
                override fun select(uri: URI?): List<Proxy> {
                    val p = NativeVpnPlugin.activeProxy
                    return if (p != null) listOf(p) else listOf(Proxy.NO_PROXY)
                }
                override fun connectFailed(uri: URI?, sa: SocketAddress?, ioe: IOException?) {}
            })
            .addNetworkInterceptor { chain ->
                val request = chain.request()
                val reqLen = request.body?.contentLength() ?: 0L
                if (reqLen > 0) NativeVpnPlugin.recordUploadedBytes(reqLen)
                val response = chain.proceed(request)
                val respLen = response.body?.contentLength() ?: 0L
                if (respLen > 0) NativeVpnPlugin.recordDownloadedBytes(respLen)
                response
            }
            .sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as X509TrustManager)
            .hostnameVerifier { _, _ -> true }
            .connectionPool(ConnectionPool(64, 5, TimeUnit.MINUTES))
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .retryOnConnectionFailure(true)
            .build()
    }

    protected fun fetchHtml(url: String): String {
        return try {
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", userAgent)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
                .header("Accept-Language", "en-US,en;q=0.9")
                .header("Upgrade-Insecure-Requests", "1")
                .header("Referer", if (url.contains("sinhalasub")) "https://sinhalasub.lk/" else if (url.contains("baiscope")) "https://baiscopes.lk/" else if (url.contains("cinesubz")) "https://cinesubz.lk/" else if (url.contains("sub.lk")) "https://sub.lk/" else if (url.contains("piratelk")) "https://piratelk.com/" else "https://google.com/")
                .build()

            okHttpClient.newCall(req).execute().use { response ->
                if (!response.isSuccessful && response.code != 301 && response.code != 302) return ""
                response.body?.string() ?: ""
            }
        } catch (e: Exception) {
            ""
        }
    }

    protected fun extractRating(text: String, title: String): String {
        val combined = "$text $title"
        val match = Regex("""(?:IMDb|IMDB|Rating|Score|Rate|⭐|★)\s*:?\s*([1-9]\.[0-9])""", RegexOption.IGNORE_CASE).find(combined)
            ?: Regex("""\b([1-9]\.[0-9])\s*/\s*10\b""", RegexOption.IGNORE_CASE).find(combined)
            ?: Regex("""\b([1-9]\.[0-9])\b""").find(combined)
        if (match != null) {
            val score = match.groupValues[1].toDoubleOrNull()
            if (score != null && score in 3.0..9.9) {
                return match.groupValues[1]
            }
        }
        val cleanT = title.replace(Regex("(?i)sinhala\\s*sub.*"), "").trim()
        val hash = cleanT.ifEmpty { "Movie" }.hashCode().let { if (it < 0) -it else it }
        val fallback = 6.8 + (hash % 22) * 0.1
        return String.format("%.1f", fallback)
    }
}
