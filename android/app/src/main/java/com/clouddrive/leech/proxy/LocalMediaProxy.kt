package com.clouddrive.leech.proxy

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.webkit.MimeTypeMap
import com.clouddrive.leech.extractor.providers.movies.BaseMovieScraper
import com.clouddrive.leech.torrent.TorrentEngineManager
import com.clouddrive.leech.vpn.VpnEngineManager
import com.clouddrive.leech.vpn.net.UniversalAntiCensorDns
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import org.libtorrent4j.Priority
import org.libtorrent4j.TorrentHandle
import org.libtorrent4j.TorrentFlags
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.io.RandomAccessFile
import java.net.InetAddress
import java.net.ServerSocket
import java.net.InetSocketAddress
import java.net.SocketException
import java.util.concurrent.ThreadFactory
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

object LocalMediaProxy {
    private const val TAG = "LocalMediaProxy"
    private const val MAX_PROXY_WORKERS = 8
    private const val MAX_QUEUED_CONNECTIONS = 32
    private const val DEFAULT_PORT = 8998
    private const val SOCKET_BACKLOG = 32
    private const val MAX_HEADER_LINE_LENGTH = 16 * 1024
    private const val GOOGLE_CLIENT_ID = "202264815644.apps.googleusercontent.com"

    @Volatile
    private var serverSocket: ServerSocket? = null
    @Volatile
    var port: Int = 0
        private set
    @Volatile
    private var isRunning = false
    private var acceptThread: Thread? = null

    private val activeConnections = java.util.concurrent.atomic.AtomicInteger(0)
    private val activeSessions = java.util.concurrent.atomic.AtomicInteger(0)

    fun registerSession() {
        activeSessions.incrementAndGet()
    }

    fun unregisterSession() {
        activeSessions.decrementAndGet().coerceAtLeast(0)
    }

    fun getActiveConnectionsCount(): Int = activeConnections.get()
    fun getActiveSessionsCount(): Int = activeSessions.get()

    private val workerThreadFactory = ThreadFactory { runnable ->
        Thread(runnable, "LocalMediaProxy-Worker").apply { isDaemon = true }
    }

    private fun createExecutor(): ThreadPoolExecutor = ThreadPoolExecutor(
        4,
        MAX_PROXY_WORKERS,
        30L,
        TimeUnit.SECONDS,
        ArrayBlockingQueue(MAX_QUEUED_CONNECTIONS),
        workerThreadFactory,
        ThreadPoolExecutor.AbortPolicy()
    )

    @Volatile
    private var executor: ThreadPoolExecutor = createExecutor()

    @Volatile
    var lastBrowsedOrigin: String = "https://www.google.com"

    private val googleClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .protocols(listOf(okhttp3.Protocol.HTTP_1_1))
            .dns(UniversalAntiCensorDns.instance)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .retryOnConnectionFailure(true)
            .build()
    }

    private val cookieStore = ConcurrentHashMap<String, MutableList<Cookie>>()
    private val browserCookieJar = object : CookieJar {
        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            val host = url.host
            val list = cookieStore.getOrPut(host) { mutableListOf() }
            synchronized(list) {
                for (c in cookies) {
                    list.removeAll { it.name == c.name }
                    list.add(c)
                }
            }
        }
        override fun loadForRequest(url: HttpUrl): List<Cookie> {
            val host = url.host
            val result = mutableListOf<Cookie>()
            for ((storedHost, cookies) in cookieStore) {
                if (host.endsWith(storedHost) || storedHost.endsWith(host)) {
                    synchronized(cookies) {
                        result.addAll(cookies)
                    }
                }
            }
            return result
        }
    }

    fun clearCache() {
        cookieStore.clear()
    }

    val browserClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .dns(UniversalAntiCensorDns.instance)
            .cookieJar(browserCookieJar)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .proxySelector(object : java.net.ProxySelector() {
                override fun select(uri: java.net.URI?): MutableList<java.net.Proxy> {
                    val p = VpnEngineManager.globalProxy
                    return if (p != null) mutableListOf(p) else mutableListOf(java.net.Proxy.NO_PROXY)
                }
                override fun connectFailed(uri: java.net.URI?, sa: java.net.SocketAddress?, ioe: java.io.IOException?) {}
            })
            .build()
    }

    val adBlockHosts = setOf(
        // Google & DoubleClick Ads & Trackers
        "doubleclick.net",
        "googleadservices.com",
        "googlesyndication.com",
        "adservice.google.com",
        "adsystem.com",
        "pagead2.googlesyndication.com",
        "google-analytics.com",
        "googletagmanager.com",
        "analytics.google.com",
        "stats.g.doubleclick.net",
        // Major Ad Networks & Programmatic Exchanges (EasyList)
        "adnxs.com",
        "criteo.com",
        "criteo.net",
        "scorecardresearch.com",
        "taboola.com",
        "outbrain.com",
        "revcontent.com",
        "mgid.com",
        "pubmatic.com",
        "rubiconproject.com",
        "openx.net",
        "smartadserver.com",
        "casalemedia.com",
        "indexexchange.com",
        "sovrn.com",
        "advertising.com",
        "media.net",
        "infolinks.com",
        "bidswitch.net",
        "yieldmo.com",
        "triplelift.com",
        "sharethrough.com",
        "quantserve.com",
        // Popups, Popunders & Redirect Networks (Peter Lowe's & uBlock filters)
        "popads.net",
        "popcash.net",
        "propellerads.com",
        "adsterra.com",
        "exoclick.com",
        "trafficjunky.com",
        "monetag.com",
        "adsupply.com",
        "onclickpredictiv.com",
        "adkeeper.co.uk",
        "adkeeper.com",
        "hilltopads.net",
        "clickadu.com",
        "evadav.com",
        "admaven.com",
        "rollerads.com",
        "richpush.co",
        "richads.com",
        "pushame.com",
        "traffors.com",
        // Streaming Video Pre-Roll Ad Injectors & Betting Ads
        "mcloud.to",
        "creative.mcloud.to",
        "bet365.com",
        "1xbet.com",
        "melbet.com",
        "mostbet.com",
        "streamtape.com/ad",
        "vidcloud.co/ad",
        // Telemetry & Trackers (EasyPrivacy)
        "hotjar.com",
        "crazyegg.com",
        "mouseflow.com",
        "clarity.ms",
        "mc.yandex.ru",
        "connect.facebook.net/en_US/fbevents.js",
        "pixel.facebook.com",
        // Google One Tap & Incompatible GSI Identity Endpoints (Prevents localhost origin errors)
        "accounts.google.com/gsi",
        "smartlock.google.com",
        "apis.google.com/js/platform.js"
    )

    fun getActiveGoogleAccount(): JSONObject? {
        return try {
            val appCtx = com.clouddrive.leech.App.instance
            val prefs = com.clouddrive.leech.security.SecureStorageManager.getEncryptedPrefs(appCtx)
            val isConnected = prefs.getBoolean("is_connected", false)
            val email = prefs.getString("active_email", null)
            if (isConnected && !email.isNullOrEmpty()) {
                val obj = JSONObject()
                obj.put("email", email)
                obj.put("name", prefs.getString("active_name", email.substringBefore("@")))
                obj.put("photo", prefs.getString("active_photo", ""))
                val usage = prefs.getLong("quota_usage", 0L)
                val limit = prefs.getLong("quota_limit", 15L * 1024 * 1024 * 1024)
                obj.put("quotaUsage", usage)
                obj.put("quotaLimit", limit)
                obj
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    fun isAdOrTracker(url: String): Boolean {
        val lower = url.lowercase()
        return adBlockHosts.any { lower.contains(it) } ||
               lower.contains("/pagead/") ||
               lower.contains("/ads?") ||
               lower.contains("/ad/") ||
               lower.contains("&ad_type=") ||
               lower.contains("google_ads") ||
               lower.contains("doubleclick") ||
               lower.contains("googlesyndication")
    }

    fun getAutoIp(): String = "127.0.0.1"

    @Synchronized
    fun start(): Int {
        val current = serverSocket
        if (isRunning && current != null && !current.isClosed) return port

        return try {
            if (executor.isShutdown || executor.isTerminated) {
                executor = createExecutor()
            }

            val socket = try {
                ServerSocket().apply {
                    reuseAddress = true
                    bind(InetSocketAddress(InetAddress.getByName("127.0.0.1"), DEFAULT_PORT), SOCKET_BACKLOG)
                }
            } catch (_: Exception) {
                ServerSocket().apply {
                    reuseAddress = true
                    bind(InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), SOCKET_BACKLOG)
                }
            }

            serverSocket = socket
            port = socket.localPort
            isRunning = true

            val thread = Thread({
                while (isRunning) {
                    try {
                        val clientSocket = socket.accept()
                        if (!isRunning) {
                            runCatching { clientSocket.close() }
                            break
                        }
                        clientSocket.tcpNoDelay = true
                        try {
                            executor.execute {
                                activeConnections.incrementAndGet()
                                try {
                                    handleClientSocket(clientSocket)
                                } finally {
                                    activeConnections.decrementAndGet().coerceAtLeast(0)
                                }
                            }
                        } catch (_: RejectedExecutionException) {
                            runCatching { clientSocket.close() }
                        }
                    } catch (_: SocketException) {
                        if (isRunning) {
                            Log.w(TAG, "Proxy accept socket closed unexpectedly")
                        }
                        break
                    } catch (e: Exception) {
                        if (isRunning) Log.w(TAG, "Proxy accept error: ${e.message}")
                    }
                }
            }, "LocalMediaProxy-Accept").apply {
                isDaemon = true
            }

            acceptThread = thread
            thread.start()
            Log.i(TAG, "Local Media Proxy started on http://127.0.0.1:$port")
            port
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start LocalMediaProxy: ${e.message}", e)
            isRunning = false
            port = 0
            serverSocket = null
            0
        }
    }

    @JvmOverloads
    @Synchronized
    fun stop(force: Boolean = false) {
        if (!force && (activeConnections.get() > 0 || activeSessions.get() > 0)) {
            Log.d(TAG, "Skipping LocalMediaProxy stop: activeConnections=${activeConnections.get()}, activeSessions=${activeSessions.get()}")
            return
        }
        isRunning = false
        runCatching { serverSocket?.close() }
        serverSocket = null
        port = 0
        acceptThread = null
    }

    @Synchronized
    fun shutdown() {
        stop(force = true)
        executor.shutdownNow()
        executor = createExecutor()
    }

    fun getProxiedUrl(targetUrl: String): String {
        val p = start()
        if (p == 0) return targetUrl
        val encoded = try {
            Uri.encode(targetUrl) ?: java.net.URLEncoder.encode(targetUrl, "UTF-8")
        } catch (_: Throwable) {
            java.net.URLEncoder.encode(targetUrl, "UTF-8")
        }
        return "http://127.0.0.1:$p/stream?url=$encoded"
    }

    fun getProxiedEmbedUrl(targetUrl: String): String {
        val p = start()
        if (p == 0) return targetUrl
        val encoded = try {
            Uri.encode(targetUrl) ?: java.net.URLEncoder.encode(targetUrl, "UTF-8")
        } catch (_: Throwable) {
            java.net.URLEncoder.encode(targetUrl, "UTF-8")
        }
        return "http://127.0.0.1:$p/embed?url=$encoded"
    }

    private fun validateUpstreamUrl(rawUrl: String): String? {
        val trimmed = rawUrl.trim()
        if (trimmed.length > 8192) return null

        val uri = try { Uri.parse(trimmed) } catch (_: Exception) { return null }
        val scheme = uri.scheme?.lowercase() ?: return null
        val host = uri.host?.lowercase() ?: return null
        if (scheme != "http" && scheme != "https") return null
        if (host.isBlank() || uri.userInfo != null) return null

        // This proxy is intended for public web/media URLs, not arbitrary local services (SSRF Protection).
        if (host == "localhost" || host.endsWith(".localhost") || host == "[::1]" || host == "::1") return null
        if (host.startsWith("127.")) return null
        if (host.startsWith("10.")) return null
        if (host.startsWith("192.168.")) return null
        if (host.startsWith("172.")) {
            val second = host.substringAfter("172.").substringBefore('.').toIntOrNull()
            if (second != null && second in 16..31) return null
        }
        if (host == "0.0.0.0" || host.startsWith("169.254.") || host == "169.254.169.254") return null
        return trimmed
    }

    private fun handleClientSocket(socket: Socket) {
        try {
            socket.tcpNoDelay = true
            socket.soTimeout = 30000
            socket.sendBufferSize = 2 * 1024 * 1024
            socket.receiveBufferSize = 1024 * 1024
            val input = BufferedReader(InputStreamReader(socket.getInputStream()))
            val output = BufferedOutputStream(socket.getOutputStream(), 64 * 1024)

            val requestLine = input.readLine() ?: run {
                socket.close()
                return
            }

            val parts = requestLine.split(" ")
            if (parts.size < 2) {
                socket.close()
                return
            }

            val method = parts[0]
            val path = parts[1]

            // Parse headers
            var rangeHeader: String? = null
            var headerBytes = 0
            var line = input.readLine()
            while (!line.isNullOrEmpty()) {
                headerBytes += line.length
                if (headerBytes > MAX_HEADER_LINE_LENGTH) {
                    sendError(output, 431, "Request headers too large")
                    runCatching { socket.close() }
                    return
                }
                if (line.startsWith("Range:", ignoreCase = true)) {
                    rangeHeader = line.substring(6).trim()
                }
                line = input.readLine()
            }

            if (method.equals("OPTIONS", ignoreCase = true)) {
                val preflight = "HTTP/1.1 200 OK\r\nAccess-Control-Allow-Origin: *\r\nAccess-Control-Allow-Private-Network: true\r\nAccess-Control-Allow-Methods: GET, POST, OPTIONS, HEAD\r\nAccess-Control-Allow-Headers: *\r\nContent-Length: 0\r\nConnection: close\r\n\r\n"
                output.write(preflight.toByteArray(Charsets.UTF_8))
                output.flush()
                socket.close()
                return
            }

            if (path.startsWith("/ping")) {
                val pingJson = "{\"status\":\"ok\",\"port\":$port,\"server\":\"LocalMediaProxy\"}"
                val pingBytes = pingJson.toByteArray(Charsets.UTF_8)
                val resp = "HTTP/1.1 200 OK\r\nAccess-Control-Allow-Origin: *\r\nAccess-Control-Allow-Private-Network: true\r\nContent-Type: application/json\r\nContent-Length: ${pingBytes.size}\r\nConnection: close\r\n\r\n"
                output.write(resp.toByteArray(Charsets.UTF_8))
                output.write(pingBytes)
                output.flush()
                socket.close()
                return
            }

            if (path.startsWith("/oauth2callback")) {
                handleGoogleOAuthCallback(path, output, socket)
                return
            }

            if (path.startsWith("/embed")) {
                val uri = Uri.parse("http://127.0.0.1$path")
                var targetUrl = uri.getQueryParameter("url")
                if (targetUrl.isNullOrEmpty()) {
                    sendError(output, 400, "Missing target url")
                    runCatching { socket.close() }
                    return
                }
                targetUrl = validateUpstreamUrl(targetUrl!!)
                if (targetUrl.isNullOrEmpty()) {
                    sendError(output, 400, "Invalid target url")
                    runCatching { socket.close() }
                    return
                }

                val reqBuilder = Request.Builder()
                    .url(targetUrl)
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36")
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8")

                if (targetUrl.contains("usersdrive") || targetUrl.contains("userdrive")) {
                    reqBuilder.header("Referer", "https://usersdrive.com/")
                } else if (targetUrl.contains("filespayout")) {
                    reqBuilder.header("Referer", "https://filespayouts.com/")
                } else if (targetUrl.contains("pixeldrain")) {
                    reqBuilder.header("Referer", "https://pixeldrain.com/")
                } else if (targetUrl.contains("mega.nz") || targetUrl.contains("mega.co.nz")) {
                    reqBuilder.header("Referer", "https://mega.nz/")
                }

                val okHttpResp: Response
                try {
                    okHttpResp = BaseMovieScraper.defaultClient.newCall(reqBuilder.build()).execute()
                } catch (e: Exception) {
                    sendError(output, 502, "Bad Gateway")
                    socket.close()
                    return
                }

                val code = okHttpResp.code
                var html = sanitizeBrowserHtml(okHttpResp.body?.string() ?: "")
                val originUri = Uri.parse(targetUrl)
                val baseDomain = "${originUri.scheme}://${originUri.host}"

                // 🛡️ Interactive External Website Permission Interceptor for Embeds
                val adShieldScript = """
                    <script>
                    (function() {
                        try {
                            window.open = function(targetUrl) {
                                if (!targetUrl) return null;
                                if (confirm("External website requested to open:\n\n" + targetUrl + "\n\nDo you want to open this page?")) {
                                    location.href = targetUrl;
                                }
                                return null;
                            };
                            window.alert = function() {};
                            window.prompt = function() { return null; };

                            // Intercept top-level redirect link clicks
                            document.addEventListener('click', function(e) {
                                var target = e.target;
                                while (target && target !== document.body) {
                                    if (target.tagName === 'A') {
                                        var href = target.getAttribute('href') || '';
                                        if (href.startsWith('http') && !href.includes(location.hostname) && !href.includes('.m3u8') && !href.includes('.mp4')) {
                                            e.preventDefault();
                                            e.stopPropagation();
                                            if (confirm("External link detected:\n\n" + href + "\n\nDo you want to allow this external website to open?")) {
                                                window.location.href = href;
                                            }
                                            return false;
                                        }
                                    }
                                    target = target.parentElement;
                                }
                            }, true);
                        } catch(e) {}
                    })();
                    </script>
                """.trimIndent()

                // Inject <base href="..."> and adShieldScript into HTML head
                if (html.contains("<head>", ignoreCase = true)) {
                    val baseTag = if (!html.contains("<base ", ignoreCase = true)) "<base href=\"$baseDomain/\">" else ""
                    html = html.replaceFirst("<head>", "<head>$baseTag$adShieldScript", ignoreCase = true)
                } else {
                    html = "$adShieldScript$html"
                }

                val htmlBytes = html.toByteArray(Charsets.UTF_8)
                val headerBuilder = StringBuilder()
                headerBuilder.append("HTTP/1.1 $code OK\r\n")
                headerBuilder.append("Access-Control-Allow-Origin: *\r\n")
                headerBuilder.append("Content-Type: text/html; charset=UTF-8\r\n")
                headerBuilder.append("Content-Length: ${htmlBytes.size}\r\n")
                headerBuilder.append("Connection: close\r\n\r\n")

                output.write(headerBuilder.toString().toByteArray(Charsets.UTF_8))
                output.write(htmlBytes)
                output.flush()

                okHttpResp.close()
                socket.close()
                return
            }

            if (path.startsWith("/browse")) {
                var targetUrl: String? = null
                val qIdx = path.indexOf("url=")
                if (qIdx != -1) {
                    val rawParam = path.substring(qIdx + 4)
                    val ampIdx = rawParam.lastIndexOf("&desktop=")
                    val urlPart = if (ampIdx != -1) rawParam.substring(0, ampIdx) else rawParam
                    targetUrl = try {
                        java.net.URLDecoder.decode(urlPart, "UTF-8")
                    } catch (_: Exception) {
                        urlPart
                    }
                }
                if (targetUrl.isNullOrEmpty()) {
                    val uri = Uri.parse("http://127.0.0.1$path")
                    targetUrl = uri.getQueryParameter("url")
                }
                val isDesktop = path.contains("desktop=1")
                if (targetUrl.isNullOrEmpty()) {
                    sendError(output, 400, "Missing target url")
                    socket.close()
                    return
                }
                targetUrl = targetUrl.trim().trimEnd(',', ';')
                targetUrl = validateUpstreamUrl(targetUrl!!)
                if (targetUrl.isNullOrEmpty()) {
                    sendError(output, 400, "Invalid target url")
                    runCatching { socket.close() }
                    return
                }

                // uBlock Origin Network Filter: Block known ad domains and trackers
                if (isAdOrTracker(targetUrl)) {
                    val blockedResp = "HTTP/1.1 204 No Content\r\nAccess-Control-Allow-Origin: *\r\nAccess-Control-Allow-Methods: GET, POST, OPTIONS, HEAD\r\nAccess-Control-Allow-Headers: *\r\nContent-Length: 0\r\nConnection: close\r\n\r\n"
                    output.write(blockedResp.toByteArray(Charsets.UTF_8))
                    output.flush()
                    socket.close()
                    return
                }

                val originUri = Uri.parse(targetUrl)
                if (!originUri.scheme.isNullOrEmpty() && !originUri.host.isNullOrEmpty()) {
                    lastBrowsedOrigin = "${originUri.scheme}://${originUri.host}"
                }

                val ua = if (isDesktop) {
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36"
                } else {
                    "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36"
                }

                // Pre-seed Google consent cookies to eliminate consent interstitial & bot flags
                if (targetUrl.contains("google.com")) {
                    val gUrl = "https://www.google.com/".toHttpUrlOrNull()
                    if (gUrl != null && cookieStore["google.com"].isNullOrEmpty()) {
                        val socsCookie = okhttp3.Cookie.Builder()
                            .domain("google.com")
                            .path("/")
                            .name("SOCS")
                            .value("CAISHAgBEhJnd3NfMjAyNjAyMjUtMF9SQzEaAmVuIAEaBgiA_L-6Bg")
                            .build()
                        val consentCookie = okhttp3.Cookie.Builder()
                            .domain("google.com")
                            .path("/")
                            .name("CONSENT")
                            .value("PENDING+999")
                            .build()
                        browserCookieJar.saveFromResponse(gUrl, listOf(socsCookie, consentCookie))
                    }
                }

                val reqBuilder = Request.Builder()
                    .url(targetUrl)
                    .header("User-Agent", ua)
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7")
                    .header("Accept-Language", "en-US,en;q=0.9")
                    .header("Sec-Ch-Ua", if (isDesktop) "\"Google Chrome\";v=\"130\", \"Chromium\";v=\"130\", \"Not?A_Brand\";v=\"99\"" else "\"Chromium\";v=\"130\", \"Android WebView\";v=\"130\", \"Not?A_Brand\";v=\"99\"")
                    .header("Sec-Ch-Ua-Mobile", if (isDesktop) "?0" else "?1")
                    .header("Sec-Ch-Ua-Platform", if (isDesktop) "\"Windows\"" else "\"Android\"")
                    .header("Sec-Fetch-Dest", "document")
                    .header("Sec-Fetch-Mode", "navigate")
                    .header("Sec-Fetch-Site", "none")
                    .header("Sec-Fetch-User", "?1")
                    .header("Upgrade-Insecure-Requests", "1")
                    .header("Priority", "u=0, i")

                var okHttpResp: Response
                try {
                    okHttpResp = browserClient.newCall(reqBuilder.build()).execute()
                } catch (e: Exception) {
                    sendError(output, 502, "Bad Gateway: ${e.message}")
                    socket.close()
                    return
                }

                // Auto-healing fallback for Google bot check / sorry / reCAPTCHA challenge
                if (okHttpResp.code == 429 || okHttpResp.request.url.encodedPath.contains("/sorry/")) {
                    val q = originUri.getQueryParameter("q")
                    if (!q.isNullOrEmpty()) {
                        val fallbackUrl = "https://www.google.com/search?q=${Uri.encode(q)}&gbv=1"
                        try {
                            okHttpResp.close()
                            val fbReq = reqBuilder.url(fallbackUrl).build()
                            okHttpResp = browserClient.newCall(fbReq).execute()
                        } catch (_: Exception) {}
                    }
                }

                val code = okHttpResp.code
                val contentType = okHttpResp.header("Content-Type") ?: "text/html"
                
                // If it's direct media returned, notify client or stream it
                if (contentType.contains("video/") || contentType.contains("mpegurl") || targetUrl.contains(".m3u8") || targetUrl.contains(".mp4")) {
                    okHttpResp.close()
                    // Redirect to stream
                    val streamRedirect = "HTTP/1.1 302 Found\r\nLocation: /stream?url=${Uri.encode(targetUrl)}\r\nConnection: close\r\n\r\n"
                    output.write(streamRedirect.toByteArray())
                    output.flush()
                    socket.close()
                    return
                }

                var renderedTargetUrl = targetUrl
                var html = sanitizeBrowserHtml(okHttpResp.body?.string() ?: "")
                val googleChallenge = targetUrl.contains("google.com/search", ignoreCase = true) &&
                    (html.contains("unusual traffic", ignoreCase = true) ||
                        html.contains("not a robot", ignoreCase = true) ||
                        html.contains("/sorry/", ignoreCase = true) ||
                        html.contains("recaptcha", ignoreCase = true))
                if (googleChallenge) {
                    val query = Uri.parse(targetUrl).getQueryParameter("q")
                    if (!query.isNullOrBlank()) {
                        renderedTargetUrl = "https://www.bing.com/search?q=${Uri.encode(query)}"
                        try {
                            val fallbackRequest = reqBuilder.url(renderedTargetUrl).build()
                            okHttpResp.close()
                            val fallbackResponse = browserClient.newCall(fallbackRequest).execute()
                            html = sanitizeBrowserHtml(fallbackResponse.body?.string() ?: "")
                            fallbackResponse.close()
                        } catch (e: Exception) {
                            Log.w(TAG, "Search fallback failed: ${e.message}")
                        }
                    }
                }
                // Strip meta refresh tags that attempt to break out to challenge or external redirect
                html = html.replace(Regex("<meta[^>]*http-equiv=[\"']?refresh[\"']?[^>]*>", RegexOption.IGNORE_CASE), "")
                
                // Fix Mixed Content: Upgrade insecure font requests to HTTPS
                html = html.replace("http://fonts.gstatic.com", "https://fonts.gstatic.com")
                    .replace("http://fonts.googleapis.com", "https://fonts.googleapis.com")

                val currentTargetJson = JSONObject.quote(renderedTargetUrl)
                val googleAccountJson = getActiveGoogleAccount()?.toString() ?: "null"

                // ⚡ uBlock Origin Cosmetic Ad Filter CSS (EasyList + gorhill/uBlock)
                val ublockCosmeticStyle = """
                    <style id="ublock-cosmetic-filter">
                    .adsbygoogle, [id^="google_ads"], [class*="google_ads"], iframe[src*="doubleclick"],
                    iframe[src*="googlesyndication"], .ad-container, .ad-banner, .banner-ad, [class*="adbox"],
                    div[id*="ScriptRoot"], [data-ad], .ad_unit, #ad-bottom, #ad-top, .advertisement,
                    .pop-under, .ad-overlay, .ad-placement, #tads, #tadsb, #bottomads, .commercial-unit,
                    .sponsored-post, [data-ad-client], [data-ad-slot], .native-ad, .ad-wrapper,
                    .OUTBRAIN, .taboola, .mgid-wrapper, div[data-ad-placeholder], .ad-shield-hide {
                        display: none !important;
                        visibility: hidden !important;
                        height: 0 !important;
                        min-height: 0 !important;
                        pointer-events: none !important;
                    }
                    </style>
                """.trimIndent()

                // ⚡ In-Page Media Sniffer, uBlock Stats & Robust Navigation Interception Telemetry Script
                val mediaSnifferScript = """
                    $ublockCosmeticStyle
                    <script id="cdl-browser-injected">
                    (function() {
                        try {
                            var proxyOrigin = window.location.origin;
                            var currentTargetUrl = $currentTargetJson;
                            var syncedGoogleAccount = $googleAccountJson;

                            // Frame un-busting defense
                            try {
                                Object.defineProperty(window, 'top', { get: function() { return window.self; } });
                                Object.defineProperty(window, 'parent', { get: function() { return window.self; } });
                            } catch(_) {}

                            // Intercept and sanitize any reCAPTCHA iframe creation so it uses target origin instead of mobile IP
                            try {
                                var origCreate = document.createElement;
                                document.createElement = function(tagName) {
                                    var el = origCreate.apply(this, arguments);
                                    if (tagName && typeof tagName === 'string' && tagName.toLowerCase() === 'iframe') {
                                        var origSet = Object.getOwnPropertyDescriptor(HTMLIFrameElement.prototype, 'src');
                                        if (origSet && origSet.set) {
                                            var origSetter = origSet.set;
                                            Object.defineProperty(el, 'src', {
                                                set: function(val) {
                                                    if (typeof val === 'string' && val.indexOf('recaptcha') !== -1 && val.indexOf('co=') !== -1) {
                                                        val = val.replace(/co=[a-zA-Z0-9_.-]+/g, 'co=aHR0cHM6Ly93d3cuZ29vZ2xlLmNvbTo0NDM.');
                                                    }
                                                    return origSetter.call(this, val);
                                                },
                                                get: origSet.get,
                                                configurable: true
                                            });
                                        }
                                    }
                                    return el;
                                };
                            } catch(_) {}

                            // Cleanse any Google One Tap or localhost origin error elements
                            function cleanseOriginErrors() {
                                try {
                                    var nodes = document.querySelectorAll('div, dialog, p, span, iframe');
                                    for (var i = 0; i < nodes.length; i++) {
                                        var txt = nodes[i].innerText || nodes[i].textContent || '';
                                        if (txt.indexOf('is not in the list of') !== -1 || txt.indexOf('redirect_uri_mismatch') !== -1 || txt.indexOf('GSI_LOGGER') !== -1) {
                                            nodes[i].remove();
                                        }
                                    }
                                } catch(_) {}
                            }
                            var cdlCleanTimer = null; function startCleanse(){ if(cdlCleanTimer === null) cdlCleanTimer=setInterval(cleanseOriginErrors, 1500); } function stopCleanse(){ if(cdlCleanTimer !== null){ clearInterval(cdlCleanTimer); cdlCleanTimer=null; } } startCleanse(); document.addEventListener('visibilitychange', function(){ if(document.hidden) stopCleanse(); else startCleanse(); });

                            // Auto-heal reCAPTCHA error if triggered by cellular IP rate limiter
                            function healRecaptchaError() {
                                var bodyText = (document.body && (document.body.innerText || document.body.textContent)) || '';
                                if (bodyText.indexOf('is not in the list of') !== -1 || bodyText.indexOf('unusual traffic') !== -1) {
                                    var href = window.location.href;
                                    var qMatch = href.match(/[?&]q=([^&]+)/);
                                    if (qMatch) {
                                        var rawQ = decodeURIComponent(qMatch[1]);
                                        if (rawQ.indexOf('http') === -1) {
                                            window.location.replace(proxyOrigin + '/browse?url=' + encodeURIComponent('https://www.bing.com/search?q=' + encodeURIComponent(rawQ)));
                                        }
                                    }
                                }
                            }
                            setTimeout(healRecaptchaError, 250);
                            setTimeout(healRecaptchaError, 1000);

                            // Google Account Seamless Header Integration on Google Search
                            if (syncedGoogleAccount && (currentTargetUrl.indexOf('google.com') !== -1 || window.location.href.indexOf('google.com') !== -1)) {
                                function renderGoogleProfileAvatar() {
                                    try {
                                        var targets = document.querySelectorAll('a[href*="ServiceLogin"], a[aria-label*="Sign in"], a.gb_1, a.gb_2, a.gb_8d, a.gb_9d, a.gb_A');
                                        targets.forEach(function(btn) {
                                            if (btn.dataset.cdlSynced) return;
                                            btn.dataset.cdlSynced = 'true';

                                            var container = document.createElement('div');
                                            container.className = 'cdl-google-account-pill';
                                            container.style.cssText = 'display:inline-flex;align-items:center;cursor:pointer;position:relative;margin:0 8px;z-index:9999;user-select:none;';

                                            var avatarHtml = '';
                                            if (syncedGoogleAccount.photo && syncedGoogleAccount.photo.length > 5) {
                                                avatarHtml = '<img src="' + syncedGoogleAccount.photo + '" style="width:34px;height:34px;border-radius:50%;border:2px solid #4285f4;object-fit:cover;box-shadow:0 2px 8px rgba(66,133,244,0.4);">';
                                            } else {
                                                var initial = (syncedGoogleAccount.name || syncedGoogleAccount.email || 'G').charAt(0).toUpperCase();
                                                avatarHtml = '<div style="width:34px;height:34px;border-radius:50%;background:linear-gradient(135deg,#4285f4,#1a73e8);color:#fff;display:flex;align-items:center;justify-content:center;font-weight:800;font-size:15px;box-shadow:0 2px 8px rgba(66,133,244,0.4);border:1.5px solid #ffffff;">' + initial + '</div>';
                                            }

                                            container.innerHTML = avatarHtml + `
                                                <div class="cdl-account-modal-card" style="display:none;position:absolute;top:44px;right:0;width:290px;background:#202124;color:#e8eaed;border-radius:18px;box-shadow:0 12px 35px rgba(0,0,0,0.85);border:1px solid rgba(255,255,255,0.12);padding:18px 16px;font-family:system-ui,-apple-system,BlinkMacSystemFont,Roboto,sans-serif;text-align:center;">
                                                    <div style="font-size:10px;font-weight:800;letter-spacing:0.8px;color:#8ab4f8;text-transform:uppercase;margin-bottom:8px;">Drive ↔ Browser Synced</div>
                                                    <div style="font-size:15px;font-weight:700;color:#ffffff;margin-bottom:2px;">` + (syncedGoogleAccount.name || 'Google Account') + `</div>
                                                    <div style="font-size:12px;color:#9aa0a6;margin-bottom:12px;word-break:break-all;">` + (syncedGoogleAccount.email || '') + `</div>
                                                    <div style="background:#303134;padding:8px 12px;border-radius:10px;font-size:11px;color:#34a853;margin-bottom:12px;display:flex;align-items:center;justify-content:center;gap:6px;font-weight:600;">
                                                        <span style="width:7px;height:7px;border-radius:50%;background:#34a853;box-shadow:0 0 8px #34a853;display:inline-block;"></span> Google Search Connected
                                                    </div>
                                                    <div style="font-size:11px;color:#80868b;border-top:1px solid rgba(255,255,255,0.08);padding-top:10px;">CloudDriveLeech Unified Account</div>
                                                </div>
                                            `;

                                            container.addEventListener('click', function(ev) {
                                                ev.preventDefault();
                                                ev.stopPropagation();
                                                var card = container.querySelector('.cdl-account-modal-card');
                                                if (card) {
                                                    card.style.display = card.style.display === 'none' ? 'block' : 'none';
                                                }
                                            });

                                            btn.parentNode.replaceChild(container, btn);
                                        });
                                    } catch(_) {}
                                }
                                var cdlAvatarTimer = setInterval(renderGoogleProfileAvatar, 1500); document.addEventListener('visibilitychange', function(){ if(document.hidden){clearInterval(cdlAvatarTimer); cdlAvatarTimer=null;} else if(cdlAvatarTimer===null){cdlAvatarTimer=setInterval(renderGoogleProfileAvatar,1500);} });
                                document.addEventListener('DOMContentLoaded', renderGoogleProfileAvatar);
                            }

                            function syncParentUrl() {
                                try {
                                    window.parent.postMessage({
                                        type: 'CDL_PAGE_NAVIGATED',
                                        url: currentTargetUrl || window.location.href,
                                        title: document.title || ''
                                    }, '*');
                                } catch(_) {}
                            }
                            syncParentUrl();
                            document.addEventListener('DOMContentLoaded', syncParentUrl);

                            // Intercept all link clicks so they never escape the proxy or leak localhost
                            document.addEventListener('click', function(e) {
                                if (zapperActive) return;
                                var target = e.target.closest('a');
                                if (target && target.href) {
                                    var href = target.href;
                                    if (href.startsWith('javascript:') || href === '#' || href.startsWith('data:')) return;

                                    // Intercept Google Sign In redirects to prevent localhost redirect error
                                    if (href.indexOf('accounts.google.com/ServiceLogin') !== -1 || href.indexOf('accounts.google.com/signin') !== -1) {
                                        e.preventDefault();
                                        e.stopPropagation();
                                        try {
                                            window.parent.postMessage({ type: 'CDL_GOOGLE_ACCOUNT_PROMPT' }, '*');
                                        } catch(_) {}
                                        return;
                                    }

                                    e.preventDefault();
                                    e.stopPropagation();

                                    // Neutralize any localhost/127.0.0.1 leakage in relative URLs
                                    if (href.indexOf('127.0.0.1') !== -1 || href.indexOf('localhost') !== -1) {
                                        try {
                                            var parsed = new URL(href);
                                            var rel = parsed.pathname + parsed.search + parsed.hash;
                                            href = new URL(rel, currentTargetUrl).href;
                                        } catch(_) {}
                                    }

                                    window.location.href = proxyOrigin + '/browse?url=' + encodeURIComponent(href);
                                }
                            }, true);

                            // Intercept all form submissions (e.g. Google Search form, login, queries)
                            document.addEventListener('submit', function(e) {
                                if (zapperActive) return;
                                var form = e.target;
                                if (form) {
                                    e.preventDefault();
                                    e.stopPropagation();
                                    var rawAction = form.getAttribute('action') || '';
                                    var action = form.action || currentTargetUrl;

                                    // Resolve relative / localhost actions to real target domain
                                    if (action.indexOf('127.0.0.1') !== -1 || action.indexOf('localhost') !== -1 || rawAction.startsWith('/')) {
                                        try {
                                            action = new URL(rawAction || '/search', currentTargetUrl).href;
                                        } catch(_) {
                                            action = currentTargetUrl;
                                        }
                                    }

                                    var method = (form.method || 'GET').toUpperCase();
                                    var formData = new FormData(form);
                                    var params = new URLSearchParams();
                                    for (var pair of formData.entries()) {
                                        params.append(pair[0], pair[1]);
                                    }
                                    if (method === 'GET') {
                                        var fullUrl = action + (action.indexOf('?') === -1 ? '?' : '&') + params.toString();
                                        window.location.href = proxyOrigin + '/browse?url=' + encodeURIComponent(fullUrl);
                                    } else {
                                        window.location.href = proxyOrigin + '/browse?url=' + encodeURIComponent(action);
                                    }
                                }
                            }, true);

                            // uBlock Origin Popup Blocker: Intercept window.open & suppress popups
                            window.open = function(destUrl) {
                                if (destUrl) {
                                    var lower = String(destUrl).toLowerCase();
                                    if (lower.indexOf('ad') !== -1 || lower.indexOf('banner') !== -1 || lower.indexOf('pop') !== -1 || lower.indexOf('doubleclick') !== -1) {
                                        return null;
                                    }
                                    window.location.href = proxyOrigin + '/browse?url=' + encodeURIComponent(destUrl);
                                }
                                return null;
                            };

                            // ⚡ uBlock Origin Interactive Element Zapper Engine
                            var zapperActive = false;
                            var lastHoveredEl = null;

                            function onZapperMouseOver(e) {
                                if (!zapperActive) return;
                                if (lastHoveredEl && lastHoveredEl !== e.target) {
                                    lastHoveredEl.style.outline = '';
                                    lastHoveredEl.style.backgroundColor = '';
                                }
                                lastHoveredEl = e.target;
                                lastHoveredEl.style.outline = '3px dashed #ef4444';
                                lastHoveredEl.style.backgroundColor = 'rgba(239, 68, 68, 0.2)';
                            }

                            function onZapperClick(e) {
                                if (!zapperActive) return;
                                e.preventDefault();
                                e.stopPropagation();
                                var el = e.target;
                                if (el) {
                                    el.remove();
                                    window.parent.postMessage({ type: 'CDL_UBLOCK_ZAPPED' }, '*');
                                }
                                disableZapper();
                            }

                            function enableZapper() {
                                zapperActive = true;
                                document.addEventListener('mouseover', onZapperMouseOver, true);
                                document.addEventListener('click', onZapperClick, true);
                            }

                            function disableZapper() {
                                zapperActive = false;
                                if (lastHoveredEl) {
                                    lastHoveredEl.style.outline = '';
                                    lastHoveredEl.style.backgroundColor = '';
                                    lastHoveredEl = null;
                                }
                                document.removeEventListener('mouseover', onZapperMouseOver, true);
                                document.removeEventListener('click', onZapperClick, true);
                            }

                            window.addEventListener('message', function(ev) {
                                if (ev.data && ev.data.type === 'CDL_UBLOCK_START_ZAPPER') {
                                    enableZapper();
                                } else if (ev.data && ev.data.type === 'CDL_UBLOCK_STOP_ZAPPER') {
                                    disableZapper();
                                }
                            });

                            // uBlock Origin Stats Scanner
                            function reportUblockStats() {
                                try {
                                    var count = document.querySelectorAll('.adsbygoogle, [id^="google_ads"], iframe[src*="doubleclick"], .ad-banner, .ad-container, #tads, .commercial-unit').length;
                                    window.parent.postMessage({ type: 'CDL_UBLOCK_STATS', blockedCount: count }, '*');
                                } catch(_) {}
                            }
                            setTimeout(reportUblockStats, 1000);
                            var cdlStatsTimer = setInterval(reportUblockStats, 5000); document.addEventListener('visibilitychange', function(){ if(document.hidden){clearInterval(cdlStatsTimer); cdlStatsTimer=null;} else if(cdlStatsTimer===null){cdlStatsTimer=setInterval(reportUblockStats,5000);} });

                            function reportMedia(url, width, height, duration) {
                                if (!url || url.startsWith('blob:') || url.startsWith('data:') || url.length < 5) return;
                                try {
                                    window.parent.postMessage({
                                        type: 'CDL_MEDIA_SNIFFED',
                                        url: url,
                                        pageTitle: document.title || '',
                                        width: width || 0,
                                        height: height || 0,
                                        duration: duration || 0
                                    }, '*');
                                } catch(_) {}
                            }

                            function scanMedia() {
                                var videos = document.querySelectorAll('video, audio');
                                for (var i = 0; i < videos.length; i++) {
                                    var v = videos[i];
                                    var src = v.currentSrc || v.src;
                                    if (src) reportMedia(src, v.videoWidth, v.videoHeight, v.duration);
                                    var sources = v.querySelectorAll('source');
                                    for (var j = 0; j < sources.length; j++) {
                                        var sSrc = sources[j].src;
                                        if (sSrc) reportMedia(sSrc, v.videoWidth, v.videoHeight, v.duration);
                                    }
                                }
                            }

                            var cdlMediaTimer = setInterval(scanMedia, 2000); document.addEventListener('visibilitychange', function(){ if(document.hidden){clearInterval(cdlMediaTimer); cdlMediaTimer=null;} else if(cdlMediaTimer===null){cdlMediaTimer=setInterval(scanMedia,2000);} });
                            document.addEventListener('DOMContentLoaded', scanMedia);
                            document.addEventListener('play', function(e) {
                                if (e.target && e.target.tagName === 'VIDEO') {
                                    var v = e.target;
                                    reportMedia(v.currentSrc || v.src, v.videoWidth, v.videoHeight, v.duration);
                                }
                            }, true);

                            // Intercept XHR / Fetch for .m3u8 and .mp4
                            var origOpen = XMLHttpRequest.prototype.open;
                            XMLHttpRequest.prototype.open = function(method, url) {
                                if (url && (url.indexOf('.m3u8') !== -1 || url.indexOf('.mp4') !== -1)) {
                                    reportMedia(url, 0, 0, 0);
                                }
                                return origOpen.apply(this, arguments);
                            };
                        } catch(e) {}
                    })();
                    </script>
                """.trimIndent()

                val renderedUri = Uri.parse(renderedTargetUrl)
                val baseDomain = "${renderedUri.scheme}://${renderedUri.host}"
                val baseTag = if (!html.contains("<base ", ignoreCase = true)) "<base href=\"$baseDomain/\">" else ""

                if (html.contains("<head>", ignoreCase = true)) {
                    html = html.replaceFirst("<head>", "<head>$baseTag$mediaSnifferScript", ignoreCase = true)
                } else {
                    html = "$baseTag$mediaSnifferScript$html"
                }

                val htmlBytes = html.toByteArray(Charsets.UTF_8)
                val headerBuilder = StringBuilder()
                headerBuilder.append("HTTP/1.1 $code OK\r\n")
                headerBuilder.append("Access-Control-Allow-Origin: *\r\n")
                headerBuilder.append("Access-Control-Allow-Private-Network: true\r\n")
                headerBuilder.append("Access-Control-Allow-Methods: GET, POST, OPTIONS, HEAD\r\n")
                headerBuilder.append("Access-Control-Allow-Headers: *\r\n")
                headerBuilder.append("Content-Type: text/html; charset=UTF-8\r\n")
                headerBuilder.append("Content-Length: ${htmlBytes.size}\r\n")
                headerBuilder.append("Connection: close\r\n\r\n")

                output.write(headerBuilder.toString().toByteArray(Charsets.UTF_8))
                output.write(htmlBytes)
                output.flush()

                okHttpResp.close()
                socket.close()
                return
            }

            if (path.startsWith("/torrent-stream")) {
                handleTorrentStream(path, rangeHeader, output, socket)
                return
            }

            if (!path.startsWith("/stream") && !path.startsWith("/media")) {
                // Sub-resource proxying (Google autocomplete, scripts, styles, images)
                if (lastBrowsedOrigin.isNotEmpty() && (path.startsWith("/") || path.startsWith("http"))) {
                    val fullUrl = if (path.startsWith("http://") || path.startsWith("https://")) path else "$lastBrowsedOrigin$path"
                    handleSubResourceProxy(fullUrl, output, socket)
                    return
                }
                sendError(output, 404, "Not Found")
                socket.close()
                return
            }

            var targetUrl: String? = null
            val qIdx = path.indexOf("url=")
            if (qIdx != -1) {
                val rawParam = path.substring(qIdx + 4)
                targetUrl = try {
                    java.net.URLDecoder.decode(rawParam, "UTF-8")
                } catch (_: Exception) {
                    rawParam
                }
            }
            if (targetUrl.isNullOrEmpty()) {
                val uri = Uri.parse("http://127.0.0.1$path")
                targetUrl = uri.getQueryParameter("url")
            }
            if (targetUrl.isNullOrEmpty()) {
                sendError(output, 400, "Missing target url")
                runCatching { socket.close() }
                return
            }

            targetUrl = validateUpstreamUrl(targetUrl!!)
            if (targetUrl.isNullOrEmpty()) {
                sendError(output, 400, "Invalid target url")
                runCatching { socket.close() }
                return
            }

            // Clean targetUrl for OkHttp (encode raw spaces and special characters so OkHttp never throws)
            val cleanTargetUrl = targetUrl.replace(" ", "%20").replace("[", "%5B").replace("]", "%5D").replace("|", "%7C")

            // Build OkHttp request to upstream server with mandatory bypass headers
            val reqBuilder = Request.Builder()
                .url(cleanTargetUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36")
                .header("Accept", "*/*")
                .header("Accept-Encoding", "identity;q=1, *;q=0")
                .header("Connection", "keep-alive")

            if (targetUrl.contains("cinejoy") || targetUrl.contains("shegu.st") || targetUrl.contains("4khdhub") || targetUrl.contains("cloudflarestorage.com") || targetUrl.contains("workers.dev")) {
                reqBuilder.header("Referer", "https://cinejoy.to/")
                reqBuilder.header("Origin", "https://cinejoy.to")
            } else if (targetUrl.contains("usersdrive") || targetUrl.contains("userdrive")) {
                reqBuilder.header("Referer", "https://usersdrive.com/")
                reqBuilder.header("Origin", "https://usersdrive.com")
            } else if (targetUrl.contains("sinhalasub.net") || targetUrl.contains("ddl.sinhalasub.net") || targetUrl.contains("cdn.sinhalasub.net") || targetUrl.contains("sinhalasub.lk") || targetUrl.contains("sinhalasub")) {
                reqBuilder.header("Referer", "https://sinhalasub.lk/")
                reqBuilder.header("Origin", "https://sinhalasub.lk")
            } else if (targetUrl.contains("pixeldrain.com") || targetUrl.contains("pixeldrain")) {
                reqBuilder.header("Referer", "https://pixeldrain.com/")
                reqBuilder.header("Origin", "https://pixeldrain.com")
            } else if (targetUrl.contains("mega.nz") || targetUrl.contains("mega.co.nz")) {
                reqBuilder.header("Referer", "https://mega.nz/")
                reqBuilder.header("Origin", "https://mega.nz")
            } else if (targetUrl.contains("sub.lk") || targetUrl.contains("dl.sub.lk")) {
                reqBuilder.header("Referer", "https://sub.lk/")
            } else if (targetUrl.contains("cineru") || targetUrl.contains("cinerustreams")) {
                reqBuilder.header("Referer", "https://cineru.lk/")
                reqBuilder.header("Origin", "https://cineru.lk")
            } else if (targetUrl.contains("cinesubz") || targetUrl.contains("sonic-cloud")) {
                reqBuilder.header("Referer", "https://cinesubz.co/")
            } else if (targetUrl.contains("baiscope")) {
                reqBuilder.header("Referer", "https://www.baiscopelk.com/")
            } else if (targetUrl.contains("pornhub") || targetUrl.contains("phncdn")) {
                reqBuilder.header("Referer", "https://www.pornhub.com/")
                reqBuilder.header("Origin", "https://www.pornhub.com")
                reqBuilder.header("Cookie", "age_verified=1; accessAgeDisclaimerPH=1; has_access=1; il=v111")
            } else if (targetUrl.contains("redtube") || targetUrl.contains("rdtcdn")) {
                reqBuilder.header("Referer", "https://www.redtube.com/")
                reqBuilder.header("Origin", "https://www.redtube.com")
                reqBuilder.header("Cookie", "age_verified=1; accessAgeDisclaimerPH=1; has_access=1; il=v111")
            } else if (targetUrl.contains("xhamster") || targetUrl.contains("xhcdn")) {
                reqBuilder.header("Referer", "https://xhamster.com/")
                reqBuilder.header("Origin", "https://xhamster.com")
            } else if (targetUrl.contains("eporner")) {
                reqBuilder.header("Referer", "https://www.eporner.com/")
            } else if (targetUrl.contains("xnxx")) {
                reqBuilder.header("Referer", "https://www.xnxx.com/")
                reqBuilder.header("Origin", "https://www.xnxx.com")
            } else if (targetUrl.contains("xvideos") || targetUrl.contains("cdn77")) {
                reqBuilder.header("Referer", "https://www.xvideos.com/")
                reqBuilder.header("Origin", "https://www.xvideos.com")
            } else if (targetUrl.contains("googleapis.com/drive") || targetUrl.contains("drive.google.com")) {
                val prefs = com.clouddrive.leech.security.SecureStorageManager.getEncryptedPrefs(com.clouddrive.leech.App.instance)
                val token = prefs.getString("auth_token", null)
                if (!token.isNullOrEmpty()) {
                    reqBuilder.header("Authorization", "Bearer $token")
                }
            }

            if (!rangeHeader.isNullOrEmpty()) {
                reqBuilder.header("Range", rangeHeader)
            }

            val okHttpResp: Response
            try {
                okHttpResp = BaseMovieScraper.defaultClient.newCall(reqBuilder.build()).execute()
            } catch (e: Exception) {
                Log.w(TAG, "Upstream connection error for $targetUrl: ${e.message}")
                sendError(output, 502, "Bad Gateway")
                socket.close()
                return
            }

            var code = okHttpResp.code
            val contentLength = okHttpResp.header("Content-Length")
            var contentRange = okHttpResp.header("Content-Range")
            if (code == 200 && !rangeHeader.isNullOrEmpty()) {
                code = 206
                if (contentRange.isNullOrEmpty() && !contentLength.isNullOrEmpty()) {
                    val totalLen = contentLength.toLongOrNull() ?: 0L
                    if (totalLen > 0L) {
                        contentRange = "bytes 0-${totalLen - 1}/$totalLen"
                    }
                }
            }
            val message = if (code == 206) "Partial Content" else if (code == 200) "OK" else okHttpResp.message
            var contentType = okHttpResp.header("Content-Type") ?: ""
            val isGenericBinary = contentType.isEmpty() ||
                contentType == "text/plain" ||
                contentType.contains("octet-stream", ignoreCase = true) ||
                contentType.contains("download", ignoreCase = true) ||
                contentType.contains("binary", ignoreCase = true) ||
                contentType.contains("matroska", ignoreCase = true) ||
                (contentType.contains("application/", ignoreCase = true) && !contentType.contains("mpegurl"))

            if (isGenericBinary) {
                contentType = when {
                    targetUrl.contains(".m3u8") -> "application/vnd.apple.mpegurl"
                    targetUrl.contains(".ts") -> "video/MP2T"
                    targetUrl.contains(".webm") -> "video/webm"
                    else -> "video/mp4"
                }
            }

            // 🎬 Transparent M3U8 Playlist URL Rewriting for Cross-Origin Sub-Streams
            val isM3u8 = contentType.contains("mpegurl", ignoreCase = true) || targetUrl.contains(".m3u8", ignoreCase = true)
            if (isM3u8) {
                val m3u8Raw = okHttpResp.body?.string() ?: ""
                val targetUri = Uri.parse(targetUrl)
                val baseScheme = targetUri.scheme ?: "https"
                val baseHost = targetUri.host ?: ""
                val rawPath = targetUri.path ?: ""
                val basePath = if (rawPath.contains("/")) rawPath.substringBeforeLast("/") else ""

                val rewritten = m3u8Raw.lines().joinToString("\n") { line ->
                    val trimmed = line.trim()
                    if (trimmed.isEmpty()) {
                        trimmed
                    } else if (trimmed.startsWith("#")) {
                        if (trimmed.contains("URI=\"")) {
                            val uriRegex = Regex("""URI="([^"]+)"""")
                            trimmed.replace(uriRegex) { match ->
                                val rel = match.groupValues[1]
                                val full = resolveAbsoluteUrl(rel, baseScheme, baseHost, basePath)
                                "URI=\"http://127.0.0.1:$port/stream?url=" + java.net.URLEncoder.encode(full, "UTF-8") + "\""
                            }
                        } else {
                            trimmed
                        }
                    } else {
                        val fullUrl = resolveAbsoluteUrl(trimmed, baseScheme, baseHost, basePath)
                        "http://127.0.0.1:$port/stream?url=" + java.net.URLEncoder.encode(fullUrl, "UTF-8")
                    }
                }

                val m3u8Bytes = rewritten.toByteArray(Charsets.UTF_8)
                val headerBuilder = StringBuilder()
                headerBuilder.append("HTTP/1.1 200 OK\r\n")
                headerBuilder.append("Access-Control-Allow-Origin: *\r\n")
                headerBuilder.append("Access-Control-Allow-Headers: *\r\n")
                headerBuilder.append("Access-Control-Allow-Methods: GET, HEAD, OPTIONS\r\n")
                headerBuilder.append("Content-Type: application/vnd.apple.mpegurl\r\n")
                headerBuilder.append("Content-Length: ${m3u8Bytes.size}\r\n")
                headerBuilder.append("Connection: close\r\n\r\n")

                output.write(headerBuilder.toString().toByteArray(Charsets.UTF_8))
                output.write(m3u8Bytes)
                output.flush()

                okHttpResp.close()
                socket.close()
                return
            }

            val acceptRanges = okHttpResp.header("Accept-Ranges") ?: "bytes"

            // Write HTTP response line and headers to WebView client
            val headerBuilder = StringBuilder()
            headerBuilder.append("HTTP/1.1 $code $message\r\n")
            headerBuilder.append("Access-Control-Allow-Origin: *\r\n")
            headerBuilder.append("Access-Control-Allow-Headers: *\r\n")
            headerBuilder.append("Access-Control-Allow-Methods: GET, HEAD, OPTIONS\r\n")
            headerBuilder.append("Content-Type: $contentType\r\n")
            headerBuilder.append("Accept-Ranges: $acceptRanges\r\n")
            headerBuilder.append("Content-Disposition: inline\r\n")
            headerBuilder.append("Connection: close\r\n")
            headerBuilder.append("Cache-Control: no-store\r\n")

            if (code == 206 && !contentRange.isNullOrEmpty()) {
                headerBuilder.append("Content-Range: $contentRange\r\n")
            }
            if (!contentLength.isNullOrEmpty()) {
                headerBuilder.append("Content-Length: $contentLength\r\n")
            }
            headerBuilder.append("\r\n")

            output.write(headerBuilder.toString().toByteArray(Charsets.UTF_8))
            output.flush()

            if (method.equals("HEAD", ignoreCase = true)) {
                okHttpResp.close()
                socket.close()
                return
            }

            val bodyStream = okHttpResp.body?.byteStream()
            if (bodyStream != null) {
                val buffer = ByteArray(128 * 1024)
                var bytesRead: Int
                val bis = BufferedInputStream(bodyStream, 256 * 1024)
                var flushCounter = 0
                while (bis.read(buffer).also { bytesRead = it } != -1) {
                    try {
                        output.write(buffer, 0, bytesRead)
                        if (++flushCounter % 4 == 0) {
                            output.flush()
                        }
                    } catch (e: Exception) {
                        break // Client disconnected (seek / pause / close)
                    }
                }
                try { output.flush() } catch (_: Exception) {}
            }

            okHttpResp.close()
            socket.close()
        } catch (e: Exception) {
            try { socket.close() } catch (_: Exception) {}
        }
    }

    private fun resolveAbsoluteUrl(rel: String, baseScheme: String, baseHost: String, basePath: String): String {
        return try {
            val base = java.net.URI("$baseScheme://$baseHost$basePath/")
            base.resolve(rel).toString()
        } catch (_: Exception) {
            when {
                rel.startsWith("http://") || rel.startsWith("https://") -> rel
                rel.startsWith("//") -> "$baseScheme:$rel"
                rel.startsWith("/") -> "$baseScheme://$baseHost$rel"
                else -> "$baseScheme://$baseHost/${rel.removePrefix("/")}"
            }
        }
    }

    private fun handleGoogleOAuthCallback(path: String, output: BufferedOutputStream, socket: Socket) {
        try {
            val uri = Uri.parse("http://127.0.0.1$path")
            val code = uri.getQueryParameter("code")
            val error = uri.getQueryParameter("error")

            if (code.isNullOrEmpty()) {
                val prefs = com.clouddrive.leech.security.SecureStorageManager.getEncryptedPrefs(com.clouddrive.leech.App.instance)
                prefs.edit().putString("last_oauth_status", "cancelled").apply()
                val errorMsg = error ?: "Google Sign-In was cancelled by user"
                val html = "<!DOCTYPE html><html><head><meta name=\"viewport\" content=\"width=device-width, initial-scale=1\"><style>body{background:#0a0f1d;color:#fff;font-family:sans-serif;display:flex;flex-direction:column;align-items:center;justify-content:center;height:100vh;margin:0;padding:20px;text-align:center;}</style></head><body><h2 style=\"color:#ff4757\">Google Connection Cancelled</h2><p style=\"color:#94a3b8\">$errorMsg</p><script>setTimeout(function(){window.location=\"intent:#Intent;action=android.intent.action.MAIN;category=android.intent.category.LAUNCHER;package=com.clouddrive.leech;component=com.clouddrive.leech/.MainActivity;end\";},1000);</script></body></html>"
                sendHtml(output, 400, html)
                socket.close()
                return
            }

            // 1. Exchange auth code for real tokens using PKCE (RFC 7636 - Zero Client Secret)
            val prefs = com.clouddrive.leech.security.SecureStorageManager.getEncryptedPrefs(com.clouddrive.leech.App.instance)
            val codeVerifier = prefs.getString("pkce_code_verifier", null)

            val tokenReqBodyBuilder = FormBody.Builder()
                .add("client_id", GOOGLE_CLIENT_ID)
                .add("grant_type", "authorization_code")
                .add("code", code)
                .add("redirect_uri", "http://127.0.0.1:$port/oauth2callback")

            if (!codeVerifier.isNullOrEmpty()) {
                tokenReqBodyBuilder.add("code_verifier", codeVerifier)
            }

            val tokenReqBody = tokenReqBodyBuilder.build()

            val tokenReq = Request.Builder()
                .url("https://oauth2.googleapis.com/token")
                .post(tokenReqBody)
                .build()

            val tokenResp = try {
                googleClient.newCall(tokenReq).execute()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to exchange auth token with Google: ${e.message}", e)
                prefs.edit().putString("last_oauth_status", "error").putString("last_oauth_error", "Connection error: ${e.message}").apply()
                val html = "<!DOCTYPE html><html><head><meta name=\"viewport\" content=\"width=device-width, initial-scale=1\"><style>body{background:#0a0f1d;color:#fff;font-family:sans-serif;display:flex;flex-direction:column;align-items:center;justify-content:center;height:100vh;margin:0;padding:20px;text-align:center;}</style></head><body><h2 style=\"color:#ff4757\">Connection Error</h2><p style=\"color:#94a3b8\">Could not connect to Google servers: ${e.message}</p><script>setTimeout(function(){window.location=\"intent:#Intent;action=android.intent.action.MAIN;category=android.intent.category.LAUNCHER;package=com.clouddrive.leech;component=com.clouddrive.leech/.MainActivity;end\";},2000);</script></body></html>"
                sendHtml(output, 502, html)
                socket.close()
                return
            }

            val tokenJsonStr = tokenResp.body?.string() ?: "{}"
            val tokenJson = JSONObject(tokenJsonStr)
            val accessToken = tokenJson.optString("access_token", "")
            val refreshToken = tokenJson.optString("refresh_token", "")
            val expiresIn = tokenJson.optLong("expires_in", 3600L)
            val grantedScope = tokenJson.optString("scope", "")
            val hasDriveScope = grantedScope.isEmpty() || grantedScope.contains("drive", ignoreCase = true)

            if (accessToken.isEmpty()) {
                val errDesc = tokenJson.optString("error_description", "Failed to retrieve access token")
                prefs.edit().putString("last_oauth_status", "error").putString("last_oauth_error", errDesc).apply()
                val html = "<!DOCTYPE html><html><head><meta name=\"viewport\" content=\"width=device-width, initial-scale=1\"><style>body{background:#0a0f1d;color:#fff;font-family:sans-serif;display:flex;flex-direction:column;align-items:center;justify-content:center;height:100vh;margin:0;padding:20px;text-align:center;}</style></head><body><h2 style=\"color:#ff4757\">Token Exchange Failed</h2><p style=\"color:#94a3b8\">$errDesc</p><script>setTimeout(function(){window.location=\"intent:#Intent;action=android.intent.action.MAIN;category=android.intent.category.LAUNCHER;package=com.clouddrive.leech;component=com.clouddrive.leech/.MainActivity;end\";},1200);</script></body></html>"
                sendHtml(output, 400, html)
                socket.close()
                return
            }

            // 2. Query user info & storage quota
            var userDisplayName = "Google Account"
            var userEmail = ""
            var userPhoto = ""
            var quotaUsage = 0L
            var quotaLimit = 15L * 1024 * 1024 * 1024

            try {
                // Fetch profile info via oauth2 userinfo endpoint
                val userinfoReq = Request.Builder()
                    .url("https://www.googleapis.com/oauth2/v2/userinfo")
                    .addHeader("Authorization", "Bearer $accessToken")
                    .build()
                val userinfoResp = googleClient.newCall(userinfoReq).execute()
                if (userinfoResp.isSuccessful) {
                    val uJson = JSONObject(userinfoResp.body?.string() ?: "{}")
                    userEmail = uJson.optString("email", userEmail)
                    userDisplayName = uJson.optString("name", userDisplayName)
                    userPhoto = uJson.optString("picture", userPhoto)
                }

                val aboutReq = Request.Builder()
                    .url("https://www.googleapis.com/drive/v3/about?fields=user,storageQuota")
                    .addHeader("Authorization", "Bearer $accessToken")
                    .build()
                val aboutResp = googleClient.newCall(aboutReq).execute()
                if (aboutResp.isSuccessful) {
                    val aboutJson = JSONObject(aboutResp.body?.string() ?: "{}")
                    val u = aboutJson.optJSONObject("user")
                    if (u != null) {
                        if (userDisplayName.isEmpty() || userDisplayName == "Google Account") {
                            userDisplayName = u.optString("displayName", userDisplayName)
                        }
                        if (userEmail.isEmpty()) {
                            userEmail = u.optString("emailAddress", "")
                        }
                        if (userPhoto.isEmpty()) {
                            userPhoto = u.optString("photoLink", "")
                        }
                    }
                    val s = aboutJson.optJSONObject("storageQuota")
                    if (s != null) {
                        quotaUsage = s.optLong("usage", 0L)
                        quotaLimit = s.optLong("limit", quotaLimit)
                    }
                }
            } catch (_: Exception) {}

            // 3. Save to EncryptedSharedPreferences (Hardware Encrypted AES-256-GCM)
            val previousEmail = prefs.getString("active_email", null)
            val isSwitched = (!previousEmail.isNullOrEmpty() && !previousEmail.equals(userEmail, ignoreCase = true))

            val editor = prefs.edit()
                .putString("active_email", userEmail)
                .putString("active_name", userDisplayName)
                .putString("active_photo", userPhoto)
                .putString("auth_token", accessToken)
                .putLong("token_expires_at", System.currentTimeMillis() + (expiresIn * 1000L))
                .putLong("quota_usage", quotaUsage)
                .putLong("quota_limit", quotaLimit)
                .putBoolean("is_connected", true)
                .putBoolean("user_logged_out", false)
                .putBoolean("drive_scope_granted", hasDriveScope)
                .remove("app_folder_id") // Clear stale folder ID from prior account or session
                .remove("pkce_code_verifier") // Clear single-use PKCE verifier
                .putString("last_oauth_status", if (isSwitched) "switched" else "success")
                .putLong("last_oauth_timestamp", System.currentTimeMillis())
            if (refreshToken.isNotEmpty()) {
                editor.putString("refresh_token", refreshToken)
            }
            editor.apply()

            // 4. Return success page and reopen app
            val html = if (hasDriveScope) {
                """
                <!DOCTYPE html>
                <html><head><meta name="viewport" content="width=device-width, initial-scale=1">
                <style>body{background:#0a0f1d;color:#fff;font-family:system-ui,-apple-system,sans-serif;display:flex;flex-direction:column;align-items:center;justify-content:center;height:100vh;margin:0;padding:24px;box-sizing:border-box;text-align:center;}
                .box{background:rgba(255,255,255,0.05);border:1px solid #4285f4;border-radius:20px;padding:32px 24px;max-width:380px;box-shadow:0 20px 50px rgba(0,0,0,0.8);}
                h2{color:#34a853;margin:0 0 10px;font-size:1.4rem;}p{color:#94a3b8;font-size:0.95rem;margin:0 0 24px;}
                .btn{display:inline-block;background:linear-gradient(135deg,#4285f4,#00f2fe);color:#000;text-decoration:none;padding:12px 28px;border-radius:12px;font-weight:700;box-shadow:0 4px 15px rgba(0,242,254,0.3);}
                </style></head><body>
                <div class="box">
                  <h2>✅ Google Drive Connected!</h2>
                  <p>Account <b>$userEmail</b> ($userDisplayName) has been linked successfully.</p>
                  <a class="btn" href="intent:#Intent;action=android.intent.action.MAIN;category=android.intent.category.LAUNCHER;package=com.clouddrive.leech;component=com.clouddrive.leech/.MainActivity;end">Return to App</a>
                </div>
                <script>
                  setTimeout(function(){
                    window.location = "intent:#Intent;action=android.intent.action.MAIN;category=android.intent.category.LAUNCHER;package=com.clouddrive.leech;component=com.clouddrive.leech/.MainActivity;end";
                  }, 800);
                </script>
                </body></html>
                """.trimIndent()
            } else {
                """
                <!DOCTYPE html>
                <html><head><meta name="viewport" content="width=device-width, initial-scale=1">
                <style>body{background:#0a0f1d;color:#fff;font-family:system-ui,-apple-system,sans-serif;display:flex;flex-direction:column;align-items:center;justify-content:center;height:100vh;margin:0;padding:24px;box-sizing:border-box;text-align:center;}
                .box{background:rgba(255,255,255,0.05);border:1px solid #f59e0b;border-radius:20px;padding:32px 24px;max-width:380px;box-shadow:0 20px 50px rgba(0,0,0,0.8);}
                h2{color:#f59e0b;margin:0 0 10px;font-size:1.3rem;}p{color:#94a3b8;font-size:0.9rem;margin:0 0 18px;line-height:1.5;}
                .warn-box{background:rgba(245,158,11,0.12);border:1px solid rgba(245,158,11,0.3);border-radius:10px;padding:10px;margin-bottom:20px;font-size:0.82rem;color:#fde68a;}
                .btn{display:inline-block;background:linear-gradient(135deg,#f59e0b,#eab308);color:#000;text-decoration:none;padding:12px 28px;border-radius:12px;font-weight:700;}
                </style></head><body>
                <div class="box">
                  <h2>⚠️ Drive Permissions Required</h2>
                  <p>Account <b>$userEmail</b> connected, but the <b>Google Drive file access</b> checkbox was not ticked during sign-in.</p>
                  <div class="warn-box">
                    Please make sure to check the box: <b>"See, edit, create, and delete all your Google Drive files"</b>.
                  </div>
                  <a class="btn" href="intent:#Intent;action=android.intent.action.MAIN;category=android.intent.category.LAUNCHER;package=com.clouddrive.leech;component=com.clouddrive.leech/.MainActivity;end">Return to App</a>
                </div>
                <script>
                  setTimeout(function(){
                    window.location = "intent:#Intent;action=android.intent.action.MAIN;category=android.intent.category.LAUNCHER;package=com.clouddrive.leech;component=com.clouddrive.leech/.MainActivity;end";
                  }, 2500);
                </script>
                </body></html>
                """.trimIndent()
            }
            sendHtml(output, 200, html)
            socket.close()

            // Bring MainActivity back to foreground
            try {
                val intent = Intent(com.clouddrive.leech.App.instance, com.clouddrive.leech.MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                }
                com.clouddrive.leech.App.instance.startActivity(intent)
            } catch (_: Exception) {}

        } catch (e: Exception) {
            sendError(output, 500, "OAuth processing error: ${e.message}")
            try { socket.close() } catch (_: Exception) {}
        }
    }

    private fun handleSubResourceProxy(fullUrl: String, output: BufferedOutputStream, socket: Socket) {
        try {
            if (isAdOrTracker(fullUrl)) {
                val blockedResp = "HTTP/1.1 204 No Content\r\nAccess-Control-Allow-Origin: *\r\nAccess-Control-Allow-Methods: GET, POST, OPTIONS, HEAD\r\nAccess-Control-Allow-Headers: *\r\nContent-Length: 0\r\nConnection: close\r\n\r\n"
                output.write(blockedResp.toByteArray(Charsets.UTF_8))
                output.flush()
                socket.close()
                return
            }

            val reqBuilder = Request.Builder()
                .url(fullUrl)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36")
                .header("Referer", "$lastBrowsedOrigin/")

            val upstreamResp = browserClient.newCall(reqBuilder.build()).execute()
            val code = upstreamResp.code
            val contentType = upstreamResp.header("Content-Type") ?: "application/octet-stream"
            val bodyBytes = upstreamResp.body?.bytes() ?: ByteArray(0)

            val headerBuilder = StringBuilder()
            headerBuilder.append("HTTP/1.1 $code OK\r\n")
            headerBuilder.append("Access-Control-Allow-Origin: *\r\n")
            headerBuilder.append("Access-Control-Allow-Methods: GET, POST, OPTIONS, HEAD\r\n")
            headerBuilder.append("Access-Control-Allow-Headers: *\r\n")
            headerBuilder.append("Content-Type: $contentType\r\n")
            headerBuilder.append("Content-Length: ${bodyBytes.size}\r\n")
            headerBuilder.append("Connection: close\r\n\r\n")

            output.write(headerBuilder.toString().toByteArray(Charsets.UTF_8))
            output.write(bodyBytes)
            output.flush()
            upstreamResp.close()
            socket.close()
        } catch (e: Exception) {
            sendError(output, 502, "Bad Gateway: ${e.message}")
            try { socket.close() } catch (_: Exception) {}
        }
    }

    private fun sanitizeBrowserHtml(html: String): String {
        return html
            .replace(Regex("<meta[^>]+http-equiv\\s*=\\s*[\\\"']?content-security-policy[\\\"']?[^>]*>", RegexOption.IGNORE_CASE), "")
            .replace(Regex("<meta[^>]+http-equiv\\s*=\\s*[\\\"']?x-frame-options[\\\"']?[^>]*>", RegexOption.IGNORE_CASE), "")
    }

    private fun sendHtml(out: BufferedOutputStream, code: Int, html: String) {
        try {
            val bytes = html.toByteArray(Charsets.UTF_8)
            val resp = "HTTP/1.1 $code OK\r\nContent-Type: text/html; charset=UTF-8\r\nContent-Length: ${bytes.size}\r\nAccess-Control-Allow-Origin: *\r\nConnection: close\r\n\r\n"
            out.write(resp.toByteArray(Charsets.UTF_8))
            out.write(bytes)
            out.flush()
        } catch (_: Exception) {}
    }

    private fun sendError(out: BufferedOutputStream, code: Int, msg: String) {
        try {
            val response = "HTTP/1.1 $code $msg\r\nContent-Type: text/plain\r\nContent-Length: ${msg.length}\r\n\r\n$msg"
            out.write(response.toByteArray(Charsets.UTF_8))
            out.flush()
        } catch (_: Exception) {}
    }

    private fun sleepInterruptibly(millis: Long) {
        try {
            Thread.sleep(millis)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            throw IllegalStateException("Proxy worker interrupted")
        }
    }

    private fun parseSingleRange(rangeHeader: String, totalSize: Long): LongRange? {
        if (totalSize <= 0L) return null
        if (!rangeHeader.startsWith("bytes=", ignoreCase = true)) return null
        val spec = rangeHeader.substringAfter('=', "").trim()
        if (spec.contains(',')) return null // A single sequential stream is supported.

        val dash = spec.indexOf('-')
        if (dash < 0) return null
        val startText = spec.substring(0, dash).trim()
        val endText = spec.substring(dash + 1).trim()

        return try {
            when {
                startText.isNotEmpty() && endText.isNotEmpty() -> {
                    val start = startText.toLong()
                    val end = endText.toLong()
                    if (start < 0L || end < start || start >= totalSize) null
                    else start..end.coerceAtMost(totalSize - 1L)
                }
                startText.isNotEmpty() -> {
                    val start = startText.toLong()
                    if (start < 0L || start >= totalSize) null else start..(totalSize - 1L)
                }
                endText.isNotEmpty() -> {
                    val suffix = endText.toLong()
                    if (suffix <= 0L) null
                    else {
                        val len = suffix.coerceAtMost(totalSize)
                        (totalSize - len)..(totalSize - 1L)
                    }
                }
                else -> null
            }
        } catch (_: NumberFormatException) {
            null
        }
    }

    private fun handleTorrentStream(path: String, rangeHeader: String?, output: BufferedOutputStream, socket: Socket) {
        try {
            socket.soTimeout = 90000

            val uri = Uri.parse("http://127.0.0.1$path")
            val hash = uri.getQueryParameter("hash")?.lowercase()
            val fileIndexParam = uri.getQueryParameter("fileIndex")?.toIntOrNull() ?: -1

            if (hash.isNullOrEmpty()) {
                sendError(output, 400, "Missing torrent hash")
                try { socket.close() } catch (_: Exception) {}
                return
            }

            var th = TorrentEngineManager.getTorrentHandle(hash)
            var waitHandle = 0
            val isSwigValid = { handle: TorrentHandle? -> handle != null && runCatching { handle.swig().is_valid }.getOrDefault(false) }

            while (!isSwigValid(th) && waitHandle < 5000 && !socket.isClosed) {
                sleepInterruptibly(100)
                waitHandle += 100
                th = TorrentEngineManager.getTorrentHandle(hash)
            }

            if (!isSwigValid(th) || th == null) {
                sendError(output, 404, "Torrent not found in session")
                try { socket.close() } catch (_: Exception) {}
                return
            }

            // Immediately activate session download, unpause flags, and announce to DHT & trackers
            runCatching { th.swig().unset_flags(org.libtorrent4j.TorrentFlags.PAUSED) }
            runCatching { th.swig().set_flags(org.libtorrent4j.TorrentFlags.AUTO_MANAGED) }
            runCatching { th.resume() }
            runCatching { th.forceReannounce() }
            runCatching { th.swig().force_dht_announce() }
            TorrentEngineManager.setSequentialDownload(th, true)

            // Wait up to 35s for metadata if not yet loaded
            var ti = th.torrentFile()
            var waitedMeta = 0
            while (ti == null && waitedMeta < 35000 && !socket.isClosed) {
                sleepInterruptibly(100)
                waitedMeta += 100
                if (waitedMeta % 5000 == 0) {
                    try { th.forceReannounce() } catch (_: Throwable) {}
                }
                ti = th.torrentFile()
            }

            if (ti == null) {
                sendError(output, 503, "Torrent metadata downloading...")
                try { socket.close() } catch (_: Exception) {}
                return
            }

            val files = ti.files()
            val numFiles = files.numFiles()
            if (numFiles <= 0) {
                sendError(output, 404, "No files in torrent")
                try { socket.close() } catch (_: Exception) {}
                return
            }

            // Select target file (specified index or largest video file)
            val targetIndex = if (fileIndexParam in 0 until numFiles) {
                fileIndexParam
            } else {
                var bestIdx = 0
                var bestSize = -1L
                for (i in 0 until numFiles) {
                    val fName = files.fileName(i)
                    val sz = files.fileSize(i)
                    if (TorrentEngineManager.isVideoFile(fName) && sz > bestSize) {
                        bestSize = sz
                        bestIdx = i
                    }
                }
                if (bestSize == -1L) {
                    for (i in 0 until numFiles) {
                        val sz = files.fileSize(i)
                        if (sz > bestSize) {
                            bestSize = sz
                            bestIdx = i
                        }
                    }
                }
                bestIdx
            }

            val targetFileName = files.fileName(targetIndex)
            val targetFileSize = files.fileSize(targetIndex)
            val targetFilePath = File(TorrentEngineManager.defaultSaveDir, files.filePath(targetIndex))
            targetFilePath.parentFile?.mkdirs()

            val fileOffsetInTorrent = files.fileOffset(targetIndex)
            val pieceLength = ti.pieceLength()
            val totalTorrentPieces = ti.numPieces()

            val fileStartPiece = (fileOffsetInTorrent / pieceLength).toInt().coerceIn(0, totalTorrentPieces - 1)
            val fileEndPiece = ((fileOffsetInTorrent + targetFileSize - 1L) / pieceLength).toInt().coerceIn(fileStartPiece, totalTorrentPieces - 1)

            // 🎯 Concentrate 100% bandwidth on target movie file
            for (i in 0 until numFiles) {
                try {
                    if (i == targetIndex) {
                        th.filePriority(i, Priority.TOP_PRIORITY)
                    } else {
                        th.filePriority(i, Priority.IGNORE)
                    }
                } catch (_: Exception) {}
            }

            // Ensure entire file range has at least DEFAULT priority so it doesn't stop after 3MB
            for (p in fileStartPiece..fileEndPiece) {
                try {
                    th.piecePriority(p, Priority.DEFAULT)
                } catch (_: Exception) {}
            }

            // 🚀 Prioritize file head (first 12 pieces) with immediate deadline 0
            val headCount = minOf(12, fileEndPiece - fileStartPiece + 1)
            for (p in fileStartPiece until (fileStartPiece + headCount)) {
                th.piecePriority(p, Priority.TOP_PRIORITY)
                th.setPieceDeadline(p, 0)
            }

            // 🎬 Prioritize file tail (last 8 pieces) for MP4 moov / MKV cues
            val tailCount = minOf(8, fileEndPiece - fileStartPiece + 1)
            for (p in (fileEndPiece - tailCount + 1)..fileEndPiece) {
                if (p >= fileStartPiece) {
                    th.piecePriority(p, Priority.TOP_PRIORITY)
                    th.setPieceDeadline(p, 150)
                }
            }

            val ext = MimeTypeMap.getFileExtensionFromUrl(targetFileName).lowercase()
            val mimeType = when (ext) {
                "mp4", "m4v" -> "video/mp4"
                "mkv" -> "video/x-matroska"
                "webm" -> "video/webm"
                "avi" -> "video/x-msvideo"
                "mov" -> "video/quicktime"
                "ts" -> "video/mp2t"
                else -> "video/mp4"
            }

            var rangeStart = 0L
            var rangeEnd = targetFileSize - 1L

            if (!rangeHeader.isNullOrEmpty()) {
                val parsedRange = parseSingleRange(rangeHeader, targetFileSize)
                if (parsedRange == null) {
                    val total = targetFileSize.coerceAtLeast(0L)
                    val errHeaders = "HTTP/1.1 416 Range Not Satisfiable\r\nContent-Range: bytes */$total\r\nConnection: close\r\n\r\n"
                    output.write(errHeaders.toByteArray(Charsets.UTF_8))
                    output.flush()
                    runCatching { socket.close() }
                    return
                }
                rangeStart = parsedRange.first
                rangeEnd = parsedRange.last
            }

            if (targetFileSize <= 0L) {
                sendError(output, 416, "Empty media file")
                runCatching { socket.close() }
                return
            }

            val contentLength = rangeEnd - rangeStart + 1L

            // Prioritize piece required by rangeStart
            val requestStartPiece = ((fileOffsetInTorrent + rangeStart) / pieceLength).toInt().coerceIn(fileStartPiece, fileEndPiece)
            th.setPieceDeadline(requestStartPiece, 0)
            th.piecePriority(requestStartPiece, Priority.TOP_PRIORITY)
            for (p in (requestStartPiece + 1)..(requestStartPiece + 10)) {
                if (p <= fileEndPiece) {
                    th.piecePriority(p, Priority.TOP_PRIORITY)
                }
            }

            // Wait until request piece is available or file exists with data
            var waitPiece = 0
            while (!th.havePiece(requestStartPiece) && (!targetFilePath.exists() || targetFilePath.length() <= rangeStart) && waitPiece < 40000 && !socket.isClosed) {
                sleepInterruptibly(100)
                waitPiece += 100
            }

            var waitFile = 0
            while (!targetFilePath.exists() && waitFile < 10000 && !socket.isClosed) {
                sleepInterruptibly(100)
                waitFile += 100
            }

            if (!targetFilePath.exists()) {
                sendError(output, 504, "Torrent piece buffering timeout")
                try { socket.close() } catch (_: Exception) {}
                return
            }

            val isPartial = !rangeHeader.isNullOrEmpty()
            val statusCode = if (isPartial) "206 Partial Content" else "200 OK"

            val headers = StringBuilder()
            headers.append("HTTP/1.1 $statusCode\r\n")
            headers.append("Access-Control-Allow-Origin: *\r\n")
            headers.append("Access-Control-Allow-Private-Network: true\r\n")
            headers.append("Content-Type: $mimeType\r\n")
            headers.append("Accept-Ranges: bytes\r\n")
            if (isPartial) {
                headers.append("Content-Range: bytes $rangeStart-$rangeEnd/$targetFileSize\r\n")
            }
            headers.append("Content-Length: $contentLength\r\n")
            headers.append("Connection: close\r\n\r\n")

            output.write(headers.toString().toByteArray(Charsets.UTF_8))
            output.flush()

            var currentByte = rangeStart
            val buffer = ByteArray(64 * 1024)
            var raf = RandomAccessFile(targetFilePath, "r")

            try {
                var consecutiveEmptyReads = 0
                while (currentByte <= rangeEnd && !socket.isClosed) {
                    val torrentBytePos = fileOffsetInTorrent + currentByte
                    val pieceIndex = (torrentBytePos / pieceLength).toInt().coerceIn(fileStartPiece, fileEndPiece)

                    // Ensure this piece is downloaded
                    val hasPiece = { idx: Int -> runCatching { th.havePiece(idx) }.getOrDefault(false) }
                    if (!hasPiece(pieceIndex)) {
                        runCatching { th.setPieceDeadline(pieceIndex, 0) }
                        runCatching { th.piecePriority(pieceIndex, Priority.TOP_PRIORITY) }

                        // Buffer next 8 pieces ahead
                        for (p in (pieceIndex + 1)..(pieceIndex + 8)) {
                            if (p <= fileEndPiece) {
                                runCatching { th.piecePriority(p, Priority.TOP_PRIORITY) }
                            }
                        }

                        var waitP = 0
                        while (!hasPiece(pieceIndex) && waitP < 30000 && !socket.isClosed) {
                            sleepInterruptibly(50)
                            waitP += 50
                        }
                    }

                    if (socket.isClosed) break

                    val nextPieceBytePos = (pieceIndex + 1L) * pieceLength
                    val remainingInPiece = (nextPieceBytePos - torrentBytePos).coerceAtLeast(1L)
                    val remainingInRequest = rangeEnd - currentByte + 1L
                    val toRead = minOf(buffer.size.toLong(), remainingInPiece, remainingInRequest).toInt()

                    raf.seek(currentByte)
                    val readBytes = raf.read(buffer, 0, toRead)
                    if (readBytes <= 0) {
                        consecutiveEmptyReads++
                        if (consecutiveEmptyReads > 40) {
                            // Refresh file descriptor in case file size extended by libtorrent
                            try {
                                raf.close()
                                raf = RandomAccessFile(targetFilePath, "r")
                            } catch (_: Exception) {}
                            consecutiveEmptyReads = 0
                        }
                        sleepInterruptibly(50)
                        continue
                    }
                    consecutiveEmptyReads = 0

                    output.write(buffer, 0, readBytes)
                    currentByte += readBytes

                    if (currentByte % (256 * 1024) == 0L) {
                        output.flush()
                    }
                }
                try { output.flush() } catch (_: Exception) {}
            } finally {
                try { raf.close() } catch (_: Exception) {}
                try { socket.close() } catch (_: Exception) {}
            }
        } catch (e: Exception) {
            Log.w("LocalMediaProxy", "Torrent stream error: ${e.message}")
            try { socket.close() } catch (_: Exception) {}
        }
    }
}
