package com.clouddrive.leech.extractor.providers.adult

import com.clouddrive.leech.vpn.net.UniversalAntiCensorDns
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

open class BaseAdultScraper(
    protected val client: OkHttpClient = defaultClient
) {
    companion object {
        val defaultClient: OkHttpClient = OkHttpClient.Builder()
            .dns(UniversalAntiCensorDns.instance)
            .connectionPool(ConnectionPool(32, 5, TimeUnit.MINUTES))
            .connectTimeout(4, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()

        const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36"
    }

    protected fun fetchText(url: String, headers: Map<String, String> = emptyMap()): String {
        val builder = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,application/json,*/*;q=0.8")
            .header("Accept-Language", "en-US,en;q=0.9")

        for ((k, v) in headers) {
            builder.header(k, v)
        }

        client.newCall(builder.build()).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code} for $url")
            return response.body?.string() ?: ""
        }
    }
}
