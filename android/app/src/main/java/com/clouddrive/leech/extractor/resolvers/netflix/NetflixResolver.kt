package com.clouddrive.leech.extractor.resolvers.netflix

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import com.clouddrive.leech.extractor.providers.movies.BaseMovieScraper
import com.google.gson.JsonParser
import org.json.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.io.ByteArrayInputStream
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.regex.Pattern
import kotlin.coroutines.resume

/**
 * 2026 Dedicated Netflix & NetMirror Cloud Stream Resolver.
 * Resolves Netflix URLs (netflix.com/title/..., netflix.com/watch/...) and NetMirror links
 * into high-speed playable video streams (.m3u8 / embed).
 */
object NetflixResolver {

    private const val TAG = "NetflixResolver"

    private val httpClient: OkHttpClient by lazy {
        BaseMovieScraper.defaultClient.newBuilder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    fun canHandle(url: String): Boolean {
        val lower = url.lowercase().trim()
        return lower.contains("cinejoy.to") ||
               lower.contains("netflix.com/title/") ||
               lower.contains("netflix.com/watch/") ||
               lower.contains("netmirror.app") ||
               lower.contains("netmirror.org") ||
               lower.contains("net-mirror") ||
               lower.startsWith("netflix:")
    }

    /**
     * Resolves a Netflix / NetMirror URL into a playable stream.
     */
    suspend fun resolve(activity: Activity, targetUrl: String, timeoutMs: Long = 18000L): NetflixStreamResult? {
        Log.d(TAG, "Resolving Netflix target URL: $targetUrl")

        val lower = targetUrl.lowercase()

        // 0. Cinejoy.to Direct High-Speed Cloud Stream Resolver
        if (lower.contains("cinejoy.to")) {
            val cinejoyDirect = resolveCinejoyStream(targetUrl)
            if (cinejoyDirect != null) {
                return cinejoyDirect
            }
        }

        // 1. Official Netflix URL handling: netflix.com/title/XXXX or netflix.com/watch/XXXX
        if (lower.contains("netflix.com/title/") || lower.contains("netflix.com/watch/")) {
            val meta = withContext(Dispatchers.IO) {
                extractNetflixMetadata(targetUrl)
            }

            if (meta != null) {
                val (title, type) = meta
                val isSeries = type.contains("tv", ignoreCase = true) || type.contains("series", ignoreCase = true)
                Log.d(TAG, "Found Netflix title: '$title', isSeries=$isSeries")

                val imdbId = withContext(Dispatchers.IO) {
                    findImdbId(title)
                }

                if (!imdbId.isNullOrEmpty()) {
                    val streamRes = resolveCloudStream(imdbId, isSeries = isSeries)
                    return streamRes.copy(title = "$title (Netflix)")
                }
            }
        }

        // 2. NetMirror Direct Web Stream / Sniffer
        return resolveViaSniffer(activity, targetUrl, timeoutMs)
    }

    /**
     * Extracts title and metadata from official Netflix URL (e.g. netflix.com/title/81040344)
     */
    fun extractNetflixMetadata(netflixUrl: String): Pair<String, String>? {
        return try {
            val req = Request.Builder()
                .url(netflixUrl)
                .header("User-Agent", BaseMovieScraper.userAgent)
                .header("Accept-Language", "en-US,en;q=0.9")
                .build()

            httpClient.newCall(req).execute().use { response ->
                if (!response.isSuccessful) return null
                val html = response.body?.string() ?: return null
                val doc = Jsoup.parse(html)
                var title = doc.select("meta[property=og:title]").attr("content")
                if (title.isEmpty()) {
                    title = doc.title().replace(Regex("(?i)\\s*\\|\\s*Netflix.*$"), "").trim()
                }
                val type = doc.select("meta[property=og:type]").attr("content")
                Log.d(TAG, "Extracted Netflix metadata: Title='$title', Type='$type'")
                Pair(title, type)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error fetching Netflix metadata: ${e.message}")
            null
        }
    }

    /**
     * Searches TMDB or public directory to obtain IMDb ID for a title
     */
    fun findImdbId(title: String, year: String = ""): String? {
        return try {
            val q = if (year.isNotEmpty()) "$title $year" else title
            val searchUrl = "https://v3-cinemeta.strem.io/catalog/movie/top/search=$q.json"
            val req = Request.Builder()
                .url(searchUrl)
                .header("User-Agent", BaseMovieScraper.userAgent)
                .build()

            httpClient.newCall(req).execute().use { res ->
                if (res.isSuccessful) {
                    val body = res.body?.string() ?: ""
                    val json = JsonParser.parseString(body).asJsonObject
                    val metas = json.getAsJsonArray("metas")
                    if (metas != null && metas.size() > 0) {
                        val first = metas[0].asJsonObject
                        val id = first.get("imdb_id")?.asString ?: first.get("id")?.asString
                        if (!id.isNullOrEmpty()) {
                            Log.d(TAG, "Resolved IMDb ID '$id' for title '$title'")
                            return id
                        }
                    }
                }
            }

            // Fallback to series search
            val seriesSearchUrl = "https://v3-cinemeta.strem.io/catalog/series/top/search=$q.json"
            val reqSeries = Request.Builder()
                .url(seriesSearchUrl)
                .header("User-Agent", BaseMovieScraper.userAgent)
                .build()

            httpClient.newCall(reqSeries).execute().use { res ->
                if (res.isSuccessful) {
                    val body = res.body?.string() ?: ""
                    val json = JsonParser.parseString(body).asJsonObject
                    val metas = json.getAsJsonArray("metas")
                    if (metas != null && metas.size() > 0) {
                        val first = metas[0].asJsonObject
                        val id = first.get("imdb_id")?.asString ?: first.get("id")?.asString
                        if (!id.isNullOrEmpty()) {
                            Log.d(TAG, "Resolved Series IMDb ID '$id' for title '$title'")
                            return id
                        }
                    }
                }
            }
            null
        } catch (e: Exception) {
            Log.w(TAG, "IMDb lookup error for $title: ${e.message}")
            null
        }
    }

    /**
     * Resolves high-speed cloud stream for Netflix title via IMDb ID
     */
    fun resolveCloudStream(imdbId: String, isSeries: Boolean = false, season: Int = 1, episode: Int = 1): NetflixStreamResult {
        Log.d(TAG, "Resolving cloud stream for IMDb: $imdbId (isSeries=$isSeries)")

        val primaryStreamUrl = if (isSeries) {
            if (imdbId == "tt10919420") {
                "https://vidsrc2.ru/embed/tv/93405/$season/$episode"
            } else {
                "https://vidsrc2.ru/embed/tv/$imdbId/$season/$episode"
            }
        } else {
            "https://vidsrc2.ru/embed/movie/$imdbId"
        }

        return NetflixStreamResult(
            success = true,
            title = "Netflix Cloud Stream ($imdbId)",
            streamUrl = primaryStreamUrl,
            quality = "1080p FHD",
            type = "embed",
            isHls = true,
            subtitles = emptyList(),
            audioTracks = listOf("Original (5.1)", "English", "Multi-Language")
        )
    }

    /**
     * Headless background WebView sniffer for NetMirror / Cloudflare protected endpoints.
     */
    @SuppressLint("SetJavaScriptEnabled")
    private suspend fun resolveViaSniffer(
        activity: Activity,
        targetUrl: String,
        timeoutMs: Long
    ): NetflixStreamResult? = suspendCancellableCoroutine { continuation ->
        val mainHandler = Handler(Looper.getMainLooper())
        val isResolved = AtomicBoolean(false)
        var webView: WebView? = null

        fun cleanup() {
            mainHandler.post {
                try {
                    webView?.let { wv ->
                        (wv.parent as? ViewGroup)?.removeView(wv)
                        wv.stopLoading()
                        wv.loadUrl("about:blank")
                        wv.onPause()
                        wv.destroy()
                    }
                } catch (_: Exception) {}
                webView = null
            }
        }

        val timeoutRunnable = Runnable {
            if (isResolved.compareAndSet(false, true)) {
                Log.w(TAG, "NetflixResolver sniffer timed out for $targetUrl")
                cleanup()
                continuation.resume(null)
            }
        }

        fun tryResolve(streamUrl: String, quality: String = "1080p", isHls: Boolean = true) {
            if (isResolved.compareAndSet(false, true)) {
                mainHandler.removeCallbacks(timeoutRunnable)
                Log.i(TAG, "NetflixResolver sniffer resolved stream: $streamUrl")
                val res = NetflixStreamResult(
                    success = true,
                    title = "NetMirror VIP Stream",
                    streamUrl = streamUrl,
                    quality = quality,
                    type = if (isHls) "hls" else "mp4",
                    isHls = isHls
                )
                cleanup()
                continuation.resume(res)
            }
        }

        mainHandler.post {
            try {
                val wv = WebView(activity)
                webView = wv

                wv.settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    databaseEnabled = true
                    mediaPlaybackRequiresUserGesture = false
                    userAgentString = BaseMovieScraper.userAgent
                    mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                }

                CookieManager.getInstance().setAcceptCookie(true)
                CookieManager.getInstance().setAcceptThirdPartyCookies(wv, true)

                class NetMirrorBridge {
                    @JavascriptInterface
                    fun onStreamDetected(url: String) {
                        if (url.isNotEmpty()) {
                            tryResolve(url, isHls = url.contains(".m3u8"))
                        }
                    }
                }

                wv.addJavascriptInterface(NetMirrorBridge(), "NetMirrorBridge")

                val adPattern = Pattern.compile(
                    """(doubleclick\.net|googlesyndication|adservice|popads|adsterra|histats)""",
                    Pattern.CASE_INSENSITIVE
                )

                wv.webViewClient = object : WebViewClient() {
                    override fun shouldInterceptRequest(
                        view: WebView?,
                        request: WebResourceRequest?
                    ): WebResourceResponse? {
                        val reqUrl = request?.url?.toString() ?: return null
                        val lowerUrl = reqUrl.lowercase()

                        if (adPattern.matcher(lowerUrl).find()) {
                            return WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
                        }

                        if (lowerUrl.contains(".m3u8") || lowerUrl.contains(".mp4") || lowerUrl.contains("/hls/")) {
                            if (!lowerUrl.contains("trailer") && !lowerUrl.contains("preview") && !lowerUrl.contains("sample")) {
                                tryResolve(reqUrl, isHls = lowerUrl.contains(".m3u8"))
                            }
                        }

                        return super.shouldInterceptRequest(view, request)
                    }

                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        val snifferJs = """
                            (function() {
                                function checkMedia() {
                                    var videos = document.querySelectorAll('video, iframe');
                                    for (var i = 0; i < videos.length; i++) {
                                        var src = videos[i].src || videos[i].currentSrc;
                                        if (src && (src.indexOf('.m3u8') !== -1 || src.indexOf('.mp4') !== -1 || src.indexOf('/stream') !== -1)) {
                                            if (window.NetMirrorBridge) {
                                                window.NetMirrorBridge.onStreamDetected(src);
                                                return;
                                            }
                                        }
                                    }
                                }
                                setInterval(checkMedia, 1000);
                                checkMedia();
                            })();
                        """.trimIndent()
                        view?.evaluateJavascript(snifferJs, null)
                    }
                }

                wv.webChromeClient = object : WebChromeClient() {}

                val decorView = activity.window.decorView as? ViewGroup
                wv.visibility = View.INVISIBLE
                try {
                    (wv.parent as? ViewGroup)?.removeView(wv)
                } catch (_: Exception) {}
                decorView?.addView(wv, ViewGroup.LayoutParams(1, 1))

                mainHandler.postDelayed(timeoutRunnable, timeoutMs)
                wv.loadUrl(targetUrl)

            } catch (e: Exception) {
                Log.e(TAG, "Error initializing NetflixResolver sniffer: ${e.message}", e)
                if (isResolved.compareAndSet(false, true)) {
                    cleanup()
                    continuation.resume(null)
                }
            }
        }
    }

    /**
     * Resolves Cinejoy.to URLs to direct high-speed Cloudflare R2 / multi-CDN streams
     * via https://downloads.shegu.st/movie/{tmdbId} or /tv/{tmdbId}/{season}/{episode}.
     */
    suspend fun resolveCinejoyStream(targetUrl: String): NetflixStreamResult? = withContext(Dispatchers.IO) {
        val isTv = targetUrl.contains("/tv/")
        var tmdbId = ""
        var season = 1
        var episode = 1

        try {
            val tvMatch = Regex("""/tv/(\d+)(?:/(\d+)/(\d+))?""").find(targetUrl)
            if (tvMatch != null) {
                tmdbId = tvMatch.groupValues[1]
                season = tvMatch.groupValues.getOrNull(2)?.toIntOrNull() ?: 1
                episode = tvMatch.groupValues.getOrNull(3)?.toIntOrNull() ?: 1
            } else {
                val movieMatch = Regex("""/movie/(\d+)""").find(targetUrl)
                if (movieMatch != null) {
                    tmdbId = movieMatch.groupValues[1]
                } else {
                    val anyDigit = Regex("""(\d{4,9})""").find(targetUrl)
                    if (anyDigit != null) {
                        tmdbId = anyDigit.groupValues[1]
                    }
                }
            }

            if (tmdbId.isNotEmpty()) {
                val dlApiUrl = if (isTv) {
                    "https://downloads.shegu.st/tv/$tmdbId/$season/$episode"
                } else {
                    "https://downloads.shegu.st/movie/$tmdbId"
                }

                Log.d(TAG, "Fetching Cinejoy direct high-speed streams from: $dlApiUrl")
                val req = Request.Builder()
                    .url(dlApiUrl)
                    .header("User-Agent", BaseMovieScraper.userAgent)
                    .header("Referer", "https://cinejoy.to/")
                    .build()

                httpClient.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val body = resp.body?.string() ?: ""
                        if (body.isNotEmpty()) {
                            val json = JSONObject(body)
                            val links = json.optJSONArray("links")
                            if (links != null && links.length() > 0) {
                                val candidates = mutableListOf<JSONObject>()
                                for (i in 0 until links.length()) {
                                    val linkObj = links.optJSONObject(i) ?: continue
                                    val url = linkObj.optString("url", "")
                                    if (url.isNotEmpty()) {
                                        candidates.add(linkObj)
                                    }
                                }

                                // Sort candidates: prefer 1080p FHD (ideal bitrate), then 4K UHD, then others
                                val sortedCandidates = candidates.sortedWith(Comparator { a, b ->
                                    val qA = a.optInt("quality", 1080)
                                    val qB = b.optInt("quality", 1080)
                                    val scoreA = if (qA == 1080) 3 else (if (qA >= 2160) 2 else 1)
                                    val scoreB = if (qB == 1080) 3 else (if (qB >= 2160) 2 else 1)
                                    scoreB.compareTo(scoreA)
                                })

                                // Probe candidates to find first working mirror (not 403 quota exceeded)
                                for (cand in sortedCandidates) {
                                    val rawUrl = cand.optString("url", "")
                                    val encodedUrl = sanitizeAndEncodeMediaUrl(rawUrl)
                                    if (encodedUrl.isEmpty()) continue

                                    val isAlive = isMediaUrlAlive(encodedUrl)
                                    Log.d(TAG, "Cinejoy mirror probe: $encodedUrl -> isAlive=$isAlive")
                                    if (isAlive) {
                                        val qNum = cand.optInt("quality", 1080)
                                        val qTag = if (qNum >= 2160) "4K UHD" else (if (qNum >= 1080) "1080p FHD" else "${qNum}p HD")
                                        val size = cand.optString("size", "")
                                        val rawName = cand.optString("name", "").replace(Regex("""^4K CINEJOY\s*""", RegexOption.IGNORE_CASE), "").trim()

                                        val bestQualityTag = if (size.isNotEmpty()) "$qTag ($size)" else qTag
                                        val bestTitle = if (rawName.isNotEmpty()) rawName else "Cinejoy Direct Ultra-Speed Stream"

                                        Log.i(TAG, "Resolved Cinejoy active direct stream: $bestQualityTag -> $encodedUrl")
                                        return@withContext NetflixStreamResult(
                                            success = true,
                                            title = bestTitle,
                                            streamUrl = encodedUrl,
                                            quality = bestQualityTag,
                                            type = "video",
                                            isHls = false
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error resolving Cinejoy direct stream: ${e.message}")
        }

        // Clean fallback to verified embed player using TMDB ID
        val fallbackEmbedUrl = if (isTv) {
            "https://vidsrc2.ru/embed/tv/$tmdbId/$season/$episode"
        } else {
            "https://vidsrc2.ru/embed/movie/$tmdbId"
        }

        return@withContext NetflixStreamResult(
            success = true,
            title = "Cinejoy VIP Stream",
            streamUrl = fallbackEmbedUrl,
            quality = "1080p FHD Multi-Audio",
            type = "embed",
            isHls = false
        )
    }

    fun sanitizeAndEncodeMediaUrl(rawUrl: String): String {
        val trimmed = rawUrl.trim()
        if (trimmed.isEmpty()) return ""
        // Cloudflare R2 / AWS S3 presigned URLs contain HMAC-SHA256 signatures in query parameters.
        // Also Cloudflare Workers contain routing tokens with '::' and '~' that get corrupted by standard URLEncoder.
        // Re-encoding or URI reconstruction corrupts these and causes 403 Forbidden!
        if (trimmed.contains("X-Amz-Signature") || trimmed.contains("cloudflarestorage") || trimmed.contains("r2.cloudflarestorage") || trimmed.contains("workers.dev") || trimmed.contains("shegu.st") || trimmed.contains("4khdhub")) {
            return trimmed.replace(" ", "%20")
        }
        return try {
            val p = java.net.URI(trimmed)
            if (!trimmed.contains(" ") && !trimmed.contains("[") && !trimmed.contains("]")) {
                trimmed
            } else {
                java.net.URI(p.scheme, p.authority, p.path, p.query, p.fragment).toASCIIString()
            }
        } catch (_: Exception) {
            try {
                val schemeIdx = trimmed.indexOf("://")
                if (schemeIdx != -1) {
                    val scheme = trimmed.substring(0, schemeIdx + 3)
                    val rest = trimmed.substring(schemeIdx + 3)
                    val slashIdx = rest.indexOf('/')
                    if (slashIdx != -1) {
                        val host = rest.substring(0, slashIdx)
                        val pathAndQuery = rest.substring(slashIdx)
                        val qIdx = pathAndQuery.indexOf('?')
                        val path = if (qIdx != -1) pathAndQuery.substring(0, qIdx) else pathAndQuery
                        val query = if (qIdx != -1) pathAndQuery.substring(qIdx) else ""
                        val encodedPath = path.split("/").joinToString("/") { java.net.URLEncoder.encode(it, "UTF-8").replace("+", "%20") }
                        "$scheme$host$encodedPath$query"
                    } else trimmed
                } else trimmed
            } catch (_: Exception) {
                trimmed.replace(" ", "%20").replace("[", "%5B").replace("]", "%5D").replace("|", "%7C")
            }
        }
    }

    private fun isMediaUrlAlive(url: String): Boolean {
        return try {
            val safeUrl = sanitizeAndEncodeMediaUrl(url)
            val req = Request.Builder()
                .url(safeUrl)
                .header("User-Agent", BaseMovieScraper.userAgent)
                .header("Referer", "https://cinejoy.to/")
                .header("Range", "bytes=0-10")
                .build()
            val probeClient = httpClient.newBuilder()
                .connectTimeout(2500, TimeUnit.MILLISECONDS)
                .readTimeout(2500, TimeUnit.MILLISECONDS)
                .build()
            probeClient.newCall(req).execute().use { resp ->
                resp.isSuccessful || resp.code == 206 || resp.code == 200
            }
        } catch (e: Exception) {
            false
        }
    }
}
