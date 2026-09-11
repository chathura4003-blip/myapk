package com.clouddrive.leech.downloader

import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request as OkHttpRequest
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * High-Performance OkHttp Downloader implementing NewPipe Extractor's Downloader interface.
 * Handles custom User-Agents, cookies, timeouts, and redirect handling for on-device extraction.
 */
class NewPipeDownloader private constructor(builder: OkHttpClient.Builder) : Downloader() {

    private val client: OkHttpClient = builder
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    companion object {
        private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36"

        @Volatile
        private var instance: NewPipeDownloader? = null

        fun init(builder: OkHttpClient.Builder = OkHttpClient.Builder()): NewPipeDownloader {
            return instance ?: synchronized(this) {
                instance ?: NewPipeDownloader(builder).also { instance = it }
            }
        }

        fun getInstance(): NewPipeDownloader {
            return instance ?: init()
        }
    }

    @Throws(IOException::class, ReCaptchaException::class)
    override fun execute(request: Request): Response {
        val httpMethod = request.httpMethod()
        val url = request.url()
        val headers = request.headers()
        val dataToSend = request.dataToSend()

        val requestBuilder = OkHttpRequest.Builder().url(url)

        val okHeadersBuilder = Headers.Builder()
        var hasUserAgent = false
        for ((key, values) in headers) {
            for (value in values) {
                if (key.equals("User-Agent", ignoreCase = true)) {
                    hasUserAgent = true
                }
                okHeadersBuilder.add(key, value)
            }
        }
        if (!hasUserAgent) {
            okHeadersBuilder.add("User-Agent", USER_AGENT)
        }
        requestBuilder.headers(okHeadersBuilder.build())

        if (httpMethod.equals("POST", ignoreCase = true)) {
            val body = (dataToSend ?: ByteArray(0)).toRequestBody(null)
            requestBuilder.post(body)
        } else if (httpMethod.equals("HEAD", ignoreCase = true)) {
            requestBuilder.head()
        } else {
            requestBuilder.get()
        }

        val okResponse = client.newCall(requestBuilder.build()).execute()

        if (okResponse.code == 429) {
            okResponse.close()
            throw ReCaptchaException("reCaptcha Challenge Requested (429)", url)
        }

        val responseBody = okResponse.body?.string() ?: ""
        val responseHeaders = mutableMapOf<String, List<String>>()
        for (name in okResponse.headers.names()) {
            responseHeaders[name] = okResponse.headers(name)
        }

        val latestUrl = okResponse.request.url.toString()

        return Response(
            okResponse.code,
            okResponse.message,
            responseHeaders,
            responseBody,
            latestUrl
        )
    }
}
