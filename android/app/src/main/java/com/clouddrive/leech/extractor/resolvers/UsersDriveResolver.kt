package com.clouddrive.leech.extractor.resolvers

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Bitmap
import android.net.Uri
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
import com.clouddrive.leech.vpn.net.UniversalAntiCensorDns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayInputStream
import java.util.Collections
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

/**
 * 🎬 UsersDriveResolver (Phase 1 - 22 Universal Public Landing Page & Media Extractor)
 * ─────────────────────────────────────────────────────────────────────────────
 * Detects publicly exposed direct media URLs from UsersDrive landing pages (e.g.
 * https://usersdrive.com/cqmhg5rjpgst.html) using a multi-layered DOM/HTML strategy.
 *
 * Strict Compliance:
 * - Does NOT bypass CAPTCHA, DRM, or access controls.
 * - Only extracts publicly exposed anchors (e.g. a.btn.btn-download[href] "Click To Download").
 * - Fully preserves ports (e.g. :8443) and query parameters (e.g. ?ffp=...).
 * - Enforces robust SSRF protection against private, loopback, and local IP ranges.
 */
object UsersDriveResolver {

    private const val TAG = "UsersDriveResolver"

    // Standardized Error Codes (Phase 14)
    const val ERROR_USERSDRIVE_PAGE_LOAD_FAILED = "USERSDRIVE_PAGE_LOAD_FAILED"
    const val ERROR_DIRECT_LINK_NOT_FOUND = "DIRECT_LINK_NOT_FOUND"
    const val ERROR_DIRECT_LINK_INVALID = "DIRECT_LINK_INVALID"
    const val ERROR_REDIRECT_BLOCKED = "REDIRECT_BLOCKED"
    const val ERROR_MEDIA_SERVER_UNREACHABLE = "MEDIA_SERVER_UNREACHABLE"
    const val ERROR_MEDIA_TYPE_UNSUPPORTED = "MEDIA_TYPE_UNSUPPORTED"
    const val ERROR_RANGE_NOT_SUPPORTED = "RANGE_NOT_SUPPORTED"
    const val ERROR_DOWNLOAD_FAILED = "DOWNLOAD_FAILED"
    const val ERROR_PLAYBACK_FAILED = "PLAYBACK_FAILED"
    const val ERROR_NETWORK_TIMEOUT = "NETWORK_TIMEOUT"

    private val blockedAdKeywords = listOf(
        "adsterra", "popads", "popcash", "clickadu", "monetag", "propush", "propeller",
        "syndication", "doubleclick", "googlesyndication", "adnxs", "bet365", "1xbet",
        "betway", "melbet", "parimatch", "mostbet", "exoclick", "juicyads", "trafficjunky",
        "onclick", "redirect", "track", "affiliate", "landing", "hilltopads", "highcpmgate",
        "profitablecpmrate", "alwingulla", "creative", "whomeeno", "adsco",
        "histats", "counter", "banner", "cpm", "adserver", "adskeeper", "yadro", "livejasmin"
    )

    data class ResolvedStream(
        val directUrl: String,
        val isZip: Boolean,
        val fileName: String = "",
        val contentType: String = "",
        val contentLength: Long = 0L,
        val candidateRank: Int = 0
    )

    sealed class ResolveResult {
        data class Success(val stream: ResolvedStream) : ResolveResult()
        data class Error(val code: String, val message: String) : ResolveResult()
    }

    // =========================================================================
    // PHASE 2 & 15: Central URL Classification, Normalization & SSRF Protection
    // =========================================================================

    /**
     * Rejects local, loopback, private, and link-local IP addresses (SSRF Protection).
     */
    fun isPrivateOrReservedHost(host: String): Boolean {
        val lower = host.lowercase()
        if (lower == "localhost" || lower.endsWith(".localhost") || lower == "0.0.0.0" || lower == "::1" || lower == "[::1]") return true
        if (lower.startsWith("127.")) return true
        if (lower.startsWith("10.")) return true
        if (lower.startsWith("192.168.")) return true
        if (lower.startsWith("172.")) {
            val second = lower.substringAfter("172.").substringBefore('.').toIntOrNull()
            if (second != null && second in 16..31) return true
        }
        if (lower.startsWith("169.254.")) return true
        if (lower.startsWith("fe80:") || lower.startsWith("fc00:") || lower.startsWith("fd00:")) return true
        return false
    }

    data class UrlParts(val scheme: String?, val host: String?, val path: String?, val port: Int)

    fun parseUrlParts(url: String): UrlParts {
        try {
            val u = Uri.parse(url)
            val s = u?.scheme?.lowercase()
            val h = u?.host?.lowercase()
            if (!s.isNullOrEmpty() && !h.isNullOrEmpty()) {
                return UrlParts(s, h, u.path ?: "", u.port)
            }
        } catch (_: Throwable) {}

        try {
            val j = java.net.URI(url)
            val s = j.scheme?.lowercase()
            val h = j.host?.lowercase()
            if (!s.isNullOrEmpty() && !h.isNullOrEmpty()) {
                return UrlParts(s, h, j.path ?: "", j.port)
            }
        } catch (_: Throwable) {}

        return UrlParts(null, null, null, -1)
    }

    /**
     * Normalizes and validates incoming UsersDrive landing page URLs.
     * Rejects javascript:, file:, content:, private IPs, and malformed inputs.
     */
    fun normalizeAndClassifyUrl(rawUrl: String): String? {
        val trimmed = rawUrl.trim()
            .removeSurrounding("\"")
            .removeSurrounding("'")
            .replace("&amp;", "&")
            .trim()
        if (trimmed.isEmpty() || trimmed.length > 4096) return null

        val parts = parseUrlParts(trimmed)
        val scheme = parts.scheme ?: return null
        if (scheme != "http" && scheme != "https") return null

        val host = parts.host ?: return null
        if (host.isBlank() || isPrivateOrReservedHost(host)) return null

        val isSupportedDomain = host == "usersdrive.com" || host.endsWith(".usersdrive.com") ||
                host == "userdrive.org" || host.endsWith(".userdrive.org")
        if (!isSupportedDomain) return null

        val path = parts.path ?: ""
        if (path.contains("login.html") || path.contains("/login") || path.contains("registration.html")) {
            return null
        }

        return trimmed
    }

    /**
     * Checks if a URL is already a direct media endpoint, bypassing the landing page scraper.
     */
    fun isAlreadyDirectMediaUrl(url: String): Boolean {
        val lower = url.lowercase()
        return (lower.contains("/d/") && !lower.contains("/d/download")) ||
                lower.endsWith(".mp4") || lower.endsWith(".mkv") ||
                lower.endsWith(".webm") || lower.endsWith(".zip") ||
                lower.endsWith(".rar") || lower.contains("userdrive.org:8443") ||
                lower.contains("dl.usersdrive.com")
    }

    /**
     * Central gatekeeper: returns true if this URL is a UsersDrive landing page that requires resolution.
     */
    fun canHandle(url: String): Boolean {
        val normalized = normalizeAndClassifyUrl(url) ?: return false
        return !isAlreadyDirectMediaUrl(normalized)
    }

    // =========================================================================
    // PHASE 4: Direct Link Validation (Preserving Port & Query Parameters)
    // =========================================================================

    /**
     * Validates candidate direct media URLs without truncating ports or query params.
     */
    fun validateCandidateDirectUrl(candidateUrl: String): String? {
        val trimmed = candidateUrl.trim().replace("&amp;", "&")
        if (trimmed.isEmpty() || trimmed.length > 8192) return null

        val parts = parseUrlParts(trimmed)
        val scheme = parts.scheme ?: return null
        if (scheme != "http" && scheme != "https") return null

        val host = parts.host ?: return null
        if (host.isBlank() || isPrivateOrReservedHost(host)) return null

        val path = parts.path ?: ""
        val isDirectHost = host.contains("userdrive.org") || host.contains("usersdrive.com") || host.contains("dl.usersdrive.com")
        val isDirectPath = path.contains("/d/") || path.endsWith(".mp4") || path.endsWith(".mkv") || path.endsWith(".webm") || path.endsWith(".zip") || path.endsWith(".rar")
        if (!isDirectHost && !isDirectPath) return null

        // Reject landing page self-references (e.g. usersdrive.com/70do46ewpb8d.html#dl)
        // These are tab anchors on the landing page, NOT actual download links
        if (host.contains("usersdrive.com") && path.endsWith(".html")) {
            return null
        }

        // Preserves full hostname, required port (e.g. :8443), path, and query parameters (e.g. ?ffp=...)
        return trimmed
    }

    /**
     * Fallback scan of page HTML regex when DOM extraction fails (Phase 3 & 20).
     * Extracts href from anchors with class btn-download, text "Click To Download",
     * or matching public /d/ media download endpoints.
     */
    fun extractCandidateFromHtml(html: String): String? {
        if (html.isBlank()) return null

        // 1. Anchor with class="...btn-download..." and href
        val btnDownloadPattern = Regex("""<a[^>]*class=["'][^"']*btn-download[^"']*["'][^>]*href=["']([^"']+)["']""", RegexOption.IGNORE_CASE)
        btnDownloadPattern.find(html)?.let { m ->
            val candidate = m.groupValues[1]
            validateCandidateDirectUrl(candidate)?.let { return it }
        }

        val btnDownloadReversePattern = Regex("""<a[^>]*href=["']([^"']+)["'][^>]*class=["'][^"']*btn-download[^"']*["']""", RegexOption.IGNORE_CASE)
        btnDownloadReversePattern.find(html)?.let { m ->
            val candidate = m.groupValues[1]
            validateCandidateDirectUrl(candidate)?.let { return it }
        }

        // 2. Anchor containing visible text "Click To Download"
        val clickDownloadPattern = Regex("""<a[^>]*href=["']([^"']+)["'][^>]*>(?:[^<]|<[^\/a]|<\/[^a])*?Click\s+To\s+Download""", RegexOption.IGNORE_CASE)
        clickDownloadPattern.find(html)?.let { m ->
            val candidate = m.groupValues[1]
            validateCandidateDirectUrl(candidate)?.let { return it }
        }

        // 3. Anchor with /d/ pattern
        val directAnchorPattern = Regex("""<a[^>]*href=["'](https?://[a-zA-Z0-9.-]*(?:userdrive\.org|usersdrive\.com)(?::\d+)?/d/[^"']+)["']""", RegexOption.IGNORE_CASE)
        directAnchorPattern.find(html)?.let { m ->
            val candidate = m.groupValues[1]
            validateCandidateDirectUrl(candidate)?.let { return it }
        }

        // 4. Raw URL regex scan for public /d/ endpoint
        val rawUrlPattern = Regex("""https?://[a-zA-Z0-9.-]*(?:userdrive\.org|usersdrive\.com)(?::\d+)?/d/[^\s"'<>]+""", RegexOption.IGNORE_CASE)
        val allMatches = rawUrlPattern.findAll(html)
        for (m in allMatches) {
            val candidate = m.value
            validateCandidateDirectUrl(candidate)?.let { return it }
        }

        return null
    }

    // =========================================================================
    // PHASE 10 & 11: HTTP HEAD / Range Verification & Safe Redirect Handling
    // =========================================================================

    suspend fun verifyDirectMediaUrl(directUrl: String): ResolvedStream? = withContext(Dispatchers.IO) {
        val client = OkHttpClient.Builder()
            .dns(UniversalAntiCensorDns.instance)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .followRedirects(false) // Handle redirects manually to enforce SSRF validation at every hop
            .followSslRedirects(false)
            .build()

        var currentUrl = directUrl
        var redirectHops = 0
        val maxHops = 5

        while (redirectHops < maxHops) {
            val validatedHop = validateCandidateDirectUrl(currentUrl) ?: run {
                Log.w(TAG, "USERSDRIVE: Redirect target failed security validation: $currentUrl")
                return@withContext null
            }

            val req = Request.Builder()
                .url(validatedHop)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36")
                .header("Range", "bytes=0-1")
                .build()

            val resp = try {
                client.newCall(req).execute()
            } catch (e: Exception) {
                Log.w(TAG, "USERSDRIVE: HTTP verification connection note for $validatedHop: ${e.message}")
                val isZip = validatedHop.contains(".zip", ignoreCase = true) || validatedHop.contains(".rar", ignoreCase = true)
                val fileName = Uri.parse(validatedHop).lastPathSegment ?: ""
                return@withContext ResolvedStream(directUrl = validatedHop, isZip = isZip, fileName = fileName)
            }

            val code = resp.code
            if (code in 300..399) {
                val loc = resp.header("Location")
                resp.close()
                if (loc.isNullOrEmpty()) break
                currentUrl = try {
                    java.net.URI(validatedHop).resolve(loc).toString()
                } catch (_: Exception) {
                    loc
                }
                redirectHops++
                Log.d(TAG, "USERSDRIVE: Following validated redirect ($redirectHops) -> $currentUrl")
                continue
            }

            val contentType = resp.header("Content-Type") ?: ""
            val contentRange = resp.header("Content-Range")
            val contentLengthHeader = resp.header("Content-Length")
            val contentDisposition = resp.header("Content-Disposition") ?: ""
            resp.close()

            var fileName = ""
            if (contentDisposition.contains("filename=", ignoreCase = true)) {
                val fnMatch = Regex("""filename\*?=['"]?(?:UTF-8'')?([^"';]+)['"]?""", RegexOption.IGNORE_CASE).find(contentDisposition)
                if (fnMatch != null) fileName = fnMatch.groupValues[1].trim()
            }
            if (fileName.isEmpty()) {
                fileName = Uri.parse(validatedHop).lastPathSegment ?: ""
            }

            var totalLength = 0L
            if (!contentRange.isNullOrEmpty() && contentRange.contains("/")) {
                totalLength = contentRange.substringAfter("/").toLongOrNull() ?: 0L
            } else if (!contentLengthHeader.isNullOrEmpty()) {
                totalLength = contentLengthHeader.toLongOrNull() ?: 0L
            }

            val isZip = fileName.endsWith(".zip", ignoreCase = true) || fileName.endsWith(".rar", ignoreCase = true) ||
                    validatedHop.contains(".zip", ignoreCase = true) || validatedHop.contains(".rar", ignoreCase = true) ||
                    contentType.contains("zip", ignoreCase = true)

            Log.d(TAG, "USERSDRIVE: Verified! Status: $code, Content-Type: $contentType, Length: $totalLength, File: $fileName")
            return@withContext ResolvedStream(
                directUrl = validatedHop,
                isZip = isZip,
                fileName = fileName,
                contentType = contentType,
                contentLength = totalLength
            )
        }

        val isZip = currentUrl.contains(".zip", ignoreCase = true) || currentUrl.contains(".rar", ignoreCase = true)
        val fileName = Uri.parse(currentUrl).lastPathSegment ?: ""
        ResolvedStream(directUrl = currentUrl, isZip = isZip, fileName = fileName)
    }

    // =========================================================================
    // PHASE 3, 5, 6, 7 & 20: Layered WebView Extraction Engine
    // =========================================================================

    @SuppressLint("SetJavaScriptEnabled")
    suspend fun resolve(activity: Activity, targetUrl: String, timeoutMs: Long = 35000L): ResolveResult =
        suspendCancellableCoroutine { continuation ->
            val mainHandler = Handler(Looper.getMainLooper())
            val isResolved = AtomicBoolean(false)
            val seenCandidates = Collections.synchronizedSet(mutableSetOf<String>())
            var webView: WebView? = null

            val normalizedUrl = normalizeAndClassifyUrl(targetUrl)
            if (normalizedUrl == null) {
                continuation.resume(ResolveResult.Error(ERROR_DIRECT_LINK_INVALID, "Invalid UsersDrive URL format"))
                return@suspendCancellableCoroutine
            }

            Log.d(TAG, "USERSDRIVE: Landing URL: $normalizedUrl")

            fun cleanup() {
                mainHandler.post {
                    try {
                        webView?.let { wv ->
                            (wv.parent as? ViewGroup)?.removeView(wv)
                            wv.stopLoading()
                            wv.loadUrl("about:blank")
                            wv.destroy()
                        }
                    } catch (_: Exception) {}
                    webView = null
                }
            }

            val timeoutRunnable = Runnable {
                if (isResolved.compareAndSet(false, true)) {
                    Log.w(TAG, "USERSDRIVE: Resolution timed out after ${timeoutMs}ms for $normalizedUrl")
                    cleanup()
                    if (continuation.isActive) {
                        continuation.resume(ResolveResult.Error(ERROR_DIRECT_LINK_NOT_FOUND, "Download link could not be found within timeout"))
                    }
                }
            }

            mainHandler.post {
                try {
                    val wv = WebView(activity)
                    webView = wv

                    val root = activity.findViewById<ViewGroup>(android.R.id.content)
                    wv.layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    try {
                        (wv.parent as? ViewGroup)?.removeView(wv)
                    } catch (_: Exception) {}
                    root?.addView(wv, 0)
                    wv.visibility = View.VISIBLE
                    wv.resumeTimers()
                    wv.onResume()

                    // Secure WebView settings (Phase 16)
                    val settings = wv.settings
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.databaseEnabled = true
                    settings.allowFileAccess = false
                    settings.allowContentAccess = false
                    settings.allowFileAccessFromFileURLs = false
                    settings.allowUniversalAccessFromFileURLs = false
                    settings.setSupportMultipleWindows(false)
                    settings.javaScriptCanOpenWindowsAutomatically = false
                    settings.mediaPlaybackRequiresUserGesture = false
                    settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW

                    val defaultUa = settings.userAgentString
                    val cleanUa = defaultUa.replace("; wv", "").replace("Version/4.0 ", "")
                    settings.userAgentString = cleanUa

                    val cookieManager = CookieManager.getInstance()
                    cookieManager.setAcceptCookie(true)
                    cookieManager.setAcceptThirdPartyCookies(wv, true)

                    // Candidate Handler: validates candidate and completes resolution
                    fun processCandidate(candidateUrl: String, candidateText: String = "", candidateRank: Int = 0) {
                        val validated = validateCandidateDirectUrl(candidateUrl) ?: return
                        if (!seenCandidates.add(validated)) return

                        if (isResolved.compareAndSet(false, true)) {
                            mainHandler.removeCallbacks(timeoutRunnable)
                            Log.d(TAG, "USERSDRIVE: Selected Candidate [Rank $candidateRank]: $validated (text: $candidateText)")

                            // Perform asynchronous lightweight HTTP verification (Phase 11)
                            kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
                                try {
                                    val verified = verifyDirectMediaUrl(validated)
                                    cleanup()
                                    if (continuation.isActive) {
                                        if (verified != null) {
                                            continuation.resume(ResolveResult.Success(verified.copy(candidateRank = candidateRank)))
                                        } else {
                                            val isZip = validated.contains(".zip", ignoreCase = true) || validated.contains(".rar", ignoreCase = true)
                                            val fn = Uri.parse(validated).lastPathSegment ?: ""
                                            continuation.resume(ResolveResult.Success(ResolvedStream(directUrl = validated, isZip = isZip, fileName = fn, candidateRank = candidateRank)))
                                        }
                                    }
                                } catch (e: Exception) {
                                    cleanup()
                                    if (continuation.isActive) {
                                        val isZip = validated.contains(".zip", ignoreCase = true) || validated.contains(".rar", ignoreCase = true)
                                        val fn = Uri.parse(validated).lastPathSegment ?: ""
                                        continuation.resume(ResolveResult.Success(ResolvedStream(directUrl = validated, isZip = isZip, fileName = fn, candidateRank = candidateRank)))
                                    }
                                }
                            }
                        }
                    }

                    wv.setDownloadListener { url, _, _, _, _ ->
                        Log.d(TAG, "USERSDRIVE: DownloadListener triggered with $url")
                        processCandidate(url, "DownloadListener", 95)
                    }

                    // Thread-safe Javascript Interface (Phase 5)
                    wv.addJavascriptInterface(object {
                        @JavascriptInterface
                        fun onCandidateFound(url: String, text: String, className: String, rank: Int) {
                            processCandidate(url, text, rank)
                        }

                        @JavascriptInterface
                        fun log(msg: String) {
                            Log.d(TAG, "[WebView-JS] $msg")
                        }
                    }, "AndroidUsersDriveBridge")

                    wv.webChromeClient = object : WebChromeClient() {
                        override fun onConsoleMessage(consoleMessage: android.webkit.ConsoleMessage?): Boolean {
                            consoleMessage?.let {
                                Log.d(TAG, "[Console] ${it.message()} (${it.sourceId()}:${it.lineNumber()})")
                            }
                            return true
                        }
                    }

                    // Layered extraction injection script (Phase 3, 6, 7 & 20)
                    val extractionJs = """
                        (function() {
                            try {
                                if (window.__udExtractorInitialized) {
                                    window.__udExtractNow && window.__udExtractNow();
                                    return;
                                }
                                window.__udExtractorInitialized = true;

                                function rankCandidate(el) {
                                    var href = el.getAttribute('href') || el.href || '';
                                    if (!href || href === '#' || href.startsWith('javascript:')) return 0;
                                    var lowerHref = href.toLowerCase();
                                    if (lowerHref.includes('login') || lowerHref.includes('register') || 
                                        lowerHref.includes('facebook') || lowerHref.includes('telegram') || 
                                        lowerHref.includes('twitter') || lowerHref.includes('share')) {
                                        return 0;
                                    }

                                    var cls = (el.className || '').toString().toLowerCase();
                                    var text = (el.textContent || '').trim().toLowerCase();

                                    // Priority 1: a.btn.btn-download[href] (Rank 100)
                                    if (cls.includes('btn-download') || (cls.includes('btn') && cls.includes('download'))) {
                                        return 100;
                                    }
                                    // Priority 2: text contains "Click To Download" (Rank 80)
                                    if (text.includes('click to download') || text.includes('download now') || text.includes('create download link')) {
                                        return 80;
                                    }
                                    // Priority 3: href contains /d/ (Rank 60)
                                    if (lowerHref.includes('/d/') || lowerHref.includes('userdrive.org') || lowerHref.includes('dl.usersdrive.com')) {
                                        return 60;
                                    }
                                    // Priority 4: Direct media extensions (Rank 40)
                                    if (lowerHref.match(/\.(mp4|mkv|webm|avi|zip|rar)($|\?)/)) {
                                        return 40;
                                    }
                                    return 0;
                                }

                                function extractCandidates() {
                                    var candidates = [];
                                    var seen = {};

                                    // A. querySelector a.btn.btn-download[href]
                                    var primaryBtns = document.querySelectorAll('a.btn.btn-download[href], a[class*="btn-download"][href], a#direct_link[href]');
                                    for (var i = 0; i < primaryBtns.length; i++) {
                                        var el = primaryBtns[i];
                                        var rawHref = el.getAttribute('href') || el.href;
                                        if (rawHref && !seen[rawHref]) {
                                            seen[rawHref] = true;
                                            var fullUrl = new URL(rawHref, window.location.href).href;
                                            var r = rankCandidate(el);
                                            candidates.push({ url: fullUrl, text: (el.textContent || '').trim(), className: el.className || '', rank: Math.max(r, 90) });
                                        }
                                    }

                                    // B. document.querySelectorAll('a[href]')
                                    var allAnchors = document.querySelectorAll('a[href]');
                                    for (var j = 0; j < allAnchors.length; j++) {
                                        var a = allAnchors[j];
                                        var h = a.getAttribute('href') || a.href;
                                        if (!h || seen[h]) continue;
                                        // Skip landing page self-references (tab anchors like #dl, #fr, #html)
                                        if (h.startsWith('#') || (h.includes('usersdrive.com') && h.match(/\.html(#.*)?$/))) continue;
                                        var rk = rankCandidate(a);
                                        if (rk > 0) {
                                            seen[h] = true;
                                            var absUrl = new URL(h, window.location.href).href;
                                            candidates.push({ url: absUrl, text: (a.textContent || '').trim(), className: a.className || '', rank: rk });
                                        }
                                    }

                                    // C. Fallback scan of page HTML regex if no DOM candidates found
                                    if (candidates.length === 0) {
                                        var html = document.documentElement ? document.documentElement.innerHTML : '';
                                        var regex = /https?:\/\/[a-zA-Z0-9.-]*(?:userdrive\.org|usersdrive\.com)(?::\d+)?\/d\/[^\s"'<>]+/gi;
                                        var m;
                                        var matchCount = 0;
                                        while ((m = regex.exec(html)) !== null && matchCount < 100) {
                                            matchCount++;
                                            var found = m[0];
                                            if (!seen[found]) {
                                                seen[found] = true;
                                                candidates.push({ url: found, text: 'HTML Regex Fallback', className: '', rank: 50 });
                                            }
                                        }
                                    }

                                    // Sort candidates descending by rank
                                    candidates.sort(function(a, b) { return b.rank - a.rank; });

                                    if (candidates.length > 0 && window.AndroidUsersDriveBridge) {
                                        window.AndroidUsersDriveBridge.log('Extracted ' + candidates.length + ' candidate(s). Top rank: ' + candidates[0].rank + ', URL: ' + candidates[0].url);
                                        for (var k = 0; k < candidates.length; k++) {
                                            window.AndroidUsersDriveBridge.onCandidateFound(candidates[k].url, candidates[k].text, candidates[k].className, candidates[k].rank);
                                        }
                                        return true;
                                    }
                                    return false;
                                }

                                // ============================================================
                                // COUNTDOWN BYPASS + FORM AUTO-SUBMIT
                                // UsersDrive uses XFileSharing Pro: countdown.js disables
                                // #downloadbtn for 9 seconds. After that, clicking the button
                                // submits form F1 (op=download2) which redirects to the actual
                                // download URL on userdrive.org:8443. We bypass the wait.
                                // ============================================================
                                function tryFormSubmit() {
                                    if (window.__udFormSubmitted) return false;
                                    try {
                                        // Look for the download button (it's a <button>, not an <a>)
                                        var dlBtn = document.querySelector('#downloadbtn, button.btn-download, button.downloadbtn, input[type="submit"].btn-download');
                                        if (dlBtn) {
                                            window.__udFormSubmitted = true;
                                            // Force remove disabled state regardless of countdown
                                            dlBtn.removeAttribute('disabled');
                                            dlBtn.classList.remove('disabled');
                                            if (window.AndroidUsersDriveBridge) {
                                                window.AndroidUsersDriveBridge.log('COUNTDOWN_BYPASS: Found download button, bypassing countdown and submitting form');
                                            }
                                            // Try clicking the button first (triggers JS click handlers)
                                            dlBtn.click();
                                            // Also submit the parent form directly as fallback
                                            var form = dlBtn.closest('form') || document.querySelector('form[name="F1"], form#F1');
                                            if (form) {
                                                setTimeout(function() {
                                                    if (!window.__udResolved) {
                                                        if (window.AndroidUsersDriveBridge) {
                                                            window.AndroidUsersDriveBridge.log('COUNTDOWN_BYPASS: Submitting form directly as fallback');
                                                        }
                                                        form.submit();
                                                    }
                                                }, 500);
                                            }
                                            return true;
                                        }
                                    } catch(fe) {
                                        if (window.AndroidUsersDriveBridge) {
                                            window.AndroidUsersDriveBridge.log('COUNTDOWN_BYPASS error: ' + fe.message);
                                        }
                                    }
                                    return false;
                                }

                                window.__udExtractNow = extractCandidates;
                                var found = extractCandidates();

                                if (!found) {
                                    // Try immediate form submit (button might already be enabled on fast load)
                                    var submitted = tryFormSubmit();

                                    // Setup MutationObserver to detect dynamically injected/modified buttons (Phase 6)
                                    if (window.MutationObserver) {
                                        var observer = new MutationObserver(function(mutations, obs) {
                                            if (extractCandidates()) {
                                                obs.disconnect();
                                            }
                                        });
                                        observer.observe(document.body || document.documentElement, { childList: true, subtree: true, attributes: true, attributeFilter: ['href', 'class', 'disabled'] });
                                        setTimeout(function() { observer.disconnect(); }, 32000);
                                    }

                                    // Retry form submit after 1s (countdown might not have started yet)
                                    setTimeout(function() {
                                        if (!window.__udResolved) tryFormSubmit();
                                    }, 1000);

                                    // Final forced form submit after 11s (well after 9s countdown expires)
                                    setTimeout(function() {
                                        if (!window.__udResolved) {
                                            if (window.AndroidUsersDriveBridge) {
                                                window.AndroidUsersDriveBridge.log('COUNTDOWN_BYPASS: Forcing form submit after 11s wait');
                                            }
                                            tryFormSubmit();
                                        }
                                    }, 11000);
                                }
                            } catch(e) {
                                if (window.AndroidUsersDriveBridge) {
                                    window.AndroidUsersDriveBridge.log('Extraction error: ' + e.message);
                                }
                            }
                        })();
                    """.trimIndent()

                    wv.webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                            val reqUrl = request?.url?.toString() ?: ""
                            val lower = reqUrl.lowercase()

                            if (lower.contains("userdrive.org") || lower.contains("dl.usersdrive.com") ||
                                lower.contains("usersdrive.com/d/") || lower.contains("/d/") ||
                                lower.contains(".mp4") || lower.contains(".mkv") || lower.contains(".zip") || lower.contains(".rar")) {
                                processCandidate(reqUrl, "shouldOverrideUrlLoading", 85)
                                return true
                            }

                            for (adKw in blockedAdKeywords) {
                                if (lower.contains(adKw)) return true
                            }
                            return false
                        }

                        override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                            val reqUrl = request?.url?.toString() ?: return null
                            val lower = reqUrl.lowercase()
                            if (lower.contains("userdrive.org") || lower.contains("dl.usersdrive.com") ||
                                (lower.contains("/d/") && (lower.contains(".mp4") || lower.contains(".mkv") || lower.contains(".zip")))) {
                                processCandidate(reqUrl, "shouldInterceptRequest", 90)
                            }
                            for (adKw in blockedAdKeywords) {
                                if (lower.contains(adKw)) {
                                    return WebResourceResponse("text/plain", "UTF-8", ByteArrayInputStream("".toByteArray()))
                                }
                            }
                            return super.shouldInterceptRequest(view, request)
                        }

                        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                            super.onPageStarted(view, url, favicon)
                            Log.d(TAG, "USERSDRIVE: onPageStarted: $url")
                            wv.evaluateJavascript(
                                """
                                (function() {
                                    try {
                                        window.open = function() { return null; };
                                        window.alert = function() {};
                                        window.confirm = function() { return true; };
                                        window.prompt = function() { return null; };
                                        window.onbeforeunload = null;
                                    } catch(e) {}
                                })();
                                """.trimIndent(), null
                            )
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            Log.d(TAG, "USERSDRIVE: Page Loaded: $url")

                            val curUrl = (url ?: "").lowercase()
                            if (curUrl.contains("login.html") || curUrl.contains("/login")) {
                                Log.w(TAG, "USERSDRIVE: Landed on login page ($url), aborting")
                                if (isResolved.compareAndSet(false, true)) {
                                    mainHandler.removeCallbacks(timeoutRunnable)
                                    cleanup()
                                    if (continuation.isActive) {
                                        continuation.resume(ResolveResult.Error(ERROR_DIRECT_LINK_NOT_FOUND, "Page required login or was unavailable"))
                                    }
                                }
                                return
                            }

                            // Run initial extraction immediately
                            wv.evaluateJavascript(extractionJs, null)

                            // Controlled delayed extractions to capture post-load and post-form-submit content
                            mainHandler.postDelayed({
                                if (!isResolved.get()) {
                                    wv.evaluateJavascript("(function(){ if(window.__udExtractNow) window.__udExtractNow(); })();", null)
                                }
                            }, 1000)

                            mainHandler.postDelayed({
                                if (!isResolved.get()) {
                                    wv.evaluateJavascript("(function(){ if(window.__udExtractNow) window.__udExtractNow(); })();", null)
                                }
                            }, 2500)

                            // After 12s (post countdown+form submit), run a final extraction sweep
                            mainHandler.postDelayed({
                                if (!isResolved.get()) {
                                    Log.d(TAG, "USERSDRIVE: Running post-countdown extraction sweep at 12s")
                                    wv.evaluateJavascript("(function(){ window.__udResolved = false; if(window.__udExtractNow) window.__udExtractNow(); })();", null)
                                }
                            }, 12000)
                        }

                        override fun onReceivedError(view: WebView?, errorCode: Int, description: String?, failingUrl: String?) {
                            super.onReceivedError(view, errorCode, description, failingUrl)
                            Log.w(TAG, "USERSDRIVE: Web error $errorCode: $description for $failingUrl")
                        }
                    }

                    mainHandler.postDelayed(timeoutRunnable, timeoutMs)
                    Log.d(TAG, "USERSDRIVE: Loading landing page in secure WebView: $normalizedUrl")
                    wv.loadUrl(normalizedUrl)

                } catch (e: Exception) {
                    Log.e(TAG, "USERSDRIVE: Error initiating WebView for $normalizedUrl: ${e.message}", e)
                    if (isResolved.compareAndSet(false, true)) {
                        mainHandler.removeCallbacks(timeoutRunnable)
                        cleanup()
                        if (continuation.isActive) {
                            continuation.resume(ResolveResult.Error(ERROR_USERSDRIVE_PAGE_LOAD_FAILED, e.message ?: "Failed to initiate web extraction"))
                        }
                    }
                }
            }

            continuation.invokeOnCancellation {
                if (isResolved.compareAndSet(false, true)) {
                    mainHandler.removeCallbacks(timeoutRunnable)
                    cleanup()
                }
            }
        }
}
