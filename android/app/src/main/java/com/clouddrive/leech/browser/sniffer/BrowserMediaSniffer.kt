package com.clouddrive.leech.browser.sniffer

import android.net.Uri
import android.os.Handler
import android.os.Looper
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

data class SniffedMedia(
    val id: String = UUID.randomUUID().toString(),
    val url: String,
    var title: String,
    val format: String, // "MP4", "HLS (m3u8)", "WebM", "DASH", "MKV"
    val mimeType: String? = null,
    val posterUrl: String? = null,
    val duration: String? = null,
    var resolution: String? = null,
    var sizeBytes: Long = -1L,
    val pageUrl: String,
    val referer: String,
    val cookie: String? = null,
    val userAgent: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

object BrowserMediaSniffer {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val tabMediaMap = ConcurrentHashMap<String, MutableList<SniffedMedia>>()
    private val listeners = mutableListOf<SnifferListener>()

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    interface SnifferListener {
        fun onMediaSniffed(tabId: String, totalCount: Int, mediaList: List<SniffedMedia>)
    }

    fun addListener(listener: SnifferListener) {
        synchronized(listeners) {
            if (!listeners.contains(listener)) listeners.add(listener)
        }
    }

    fun removeListener(listener: SnifferListener) {
        synchronized(listeners) {
            listeners.remove(listener)
        }
    }

    fun clearForTab(tabId: String) {
        tabMediaMap.remove(tabId)
        notifyListeners(tabId, 0, emptyList())
    }

    fun getMediaForTab(tabId: String): List<SniffedMedia> {
        val list = tabMediaMap[tabId] ?: return emptyList()
        return synchronized(list) { list.toList() }
    }

    fun getMediaCountForTab(tabId: String): Int {
        val list = tabMediaMap[tabId] ?: return 0
        return synchronized(list) { list.size }
    }

    fun isMediaUrl(url: String): Boolean {
        if (url.startsWith("data:") || url.startsWith("blob:") || url.length < 8) return false
        val lower = url.lowercase(Locale.ROOT)

        // Ignore common web assets
        if (lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg") ||
            lower.endsWith(".gif") || lower.endsWith(".webp") || lower.endsWith(".svg") ||
            lower.endsWith(".ico") || lower.endsWith(".css") || lower.endsWith(".js") ||
            lower.endsWith(".woff") || lower.endsWith(".woff2") || lower.endsWith(".ttf") ||
            lower.endsWith(".json") || lower.endsWith(".map")
        ) {
            return false
        }

        // Ignore known ad / tracking networks
        if (lower.contains("googleads") || lower.contains("doubleclick") ||
            lower.contains("googlesyndication") || lower.contains("adsystem") ||
            lower.contains("adnxs") || lower.contains("exoclick") ||
            lower.contains("popcash") || lower.contains("propeller") ||
            lower.contains("monetag") || lower.contains("analytics") ||
            lower.contains("stats") || lower.contains("beacon") ||
            lower.contains("pixel") || lower.contains("clarity.ms") ||
            lower.contains("scorecardresearch") || lower.contains("taboola") ||
            lower.contains("outbrain") || lower.contains("/ads/") ||
            lower.contains("delivery") || lower.contains("banner")
        ) {
            return false
        }

        // Media Stream & Video Extensions / Endpoints
        return lower.contains(".m3u8") ||
                lower.contains(".mpd") ||
                lower.contains(".mp4") ||
                lower.contains(".webm") ||
                lower.contains(".mkv") ||
                lower.contains(".flv") ||
                lower.contains(".f4v") ||
                lower.contains("/videoplayback") ||
                lower.contains("googlevideo.com") ||
                lower.contains("master.m3u8") ||
                lower.contains("playlist.m3u8") ||
                lower.contains("index.m3u8") ||
                lower.contains("userdrive.org") ||
                lower.contains("mime=video") ||
                lower.contains("video%2F") ||
                (lower.contains(".ts") && (lower.contains("/hls/") || lower.contains("/seg-") || lower.contains("/segment")))
    }

    fun detectFormat(url: String): String {
        val lower = url.lowercase(Locale.ROOT)
        return when {
            lower.contains(".m3u8") -> "HLS (m3u8)"
            lower.contains(".mpd") -> "DASH (mpd)"
            lower.contains(".webm") -> "WebM"
            lower.contains(".mkv") -> "MKV"
            lower.contains(".zip") || lower.contains(".rar") || lower.contains(".7z") -> "ZIP"
            else -> "MP4"
        }
    }

    /**
     * Called when a network URL is intercepted in WebViewClient.shouldInterceptRequest
     */
    fun checkAndSniffRequest(
        tabId: String,
        pageUrl: String,
        pageTitle: String,
        reqUrl: String
    ) {
        if (!isMediaUrl(reqUrl)) return

        // Exclude single .ts segments if an m3u8 playlist already exists for this stream
        val list = tabMediaMap.getOrPut(tabId) { mutableListOf() }
        val isTs = reqUrl.contains(".ts", ignoreCase = true)
        if (isTs) {
            val hasM3u8 = synchronized(list) { list.any { it.url.contains(".m3u8", ignoreCase = true) } }
            if (hasM3u8) return // Master playlist is already captured!
        }

        val normalized = normalizeMediaUrl(reqUrl)
        val alreadyExists = synchronized(list) {
            list.any { normalizeMediaUrl(it.url) == normalized }
        }
        if (alreadyExists) return

        val format = detectFormat(reqUrl)
        val cleanTitle = buildCleanTitle(pageTitle, reqUrl, format)

        val item = SniffedMedia(
            url = reqUrl,
            title = cleanTitle,
            format = format,
            pageUrl = pageUrl,
            referer = pageUrl
        )

        synchronized(list) {
            list.add(item)
        }

        notifyUpdated(tabId)

        // Asynchronously probe resolution & size in background
        probeMediaDetails(tabId, item)
    }

    /**
     * Called by JavascriptInterface when the DOM or window.fetch discovers a video
     */
    fun addFromJs(tabId: String, sniffed: SniffedMedia) {
        val list = tabMediaMap.getOrPut(tabId) { mutableListOf() }
        val normalized = normalizeMediaUrl(sniffed.url)

        synchronized(list) {
            val existing = list.find { normalizeMediaUrl(it.url) == normalized }
            if (existing != null) {
                // Enrich existing item with richer metadata from DOM
                if (!sniffed.title.isNullOrBlank() && (existing.title.startsWith("Video_") || existing.title == "Web Video Stream")) {
                    existing.title = sniffed.title
                }
                if (!sniffed.resolution.isNullOrBlank()) {
                    existing.resolution = sniffed.resolution
                }
                return
            } else {
                list.add(sniffed)
            }
        }

        notifyUpdated(tabId)
        probeMediaDetails(tabId, sniffed)
    }

    private fun normalizeMediaUrl(url: String): String {
        return try {
            val uri = Uri.parse(url)
            "${uri.scheme}://${uri.host}${uri.path}"
        } catch (_: Exception) {
            url.substringBefore('?')
        }
    }

    private fun buildCleanTitle(pageTitle: String, url: String, format: String): String {
        val raw = if (pageTitle.isNotBlank() && pageTitle != "Start Page" && pageTitle != "Cloud Browser" && !pageTitle.startsWith("http")) {
            pageTitle.trim()
        } else {
            try {
                val uri = Uri.parse(url)
                val last = uri.lastPathSegment ?: ""
                if (last.length > 3) last.substringBeforeLast('.') else "Video_${UUID.randomUUID().toString().take(6)}"
            } catch (_: Exception) {
                "Video_${UUID.randomUUID().toString().take(6)}"
            }
        }
        return raw.replace(Regex("""[/\\?%*:|"<>]+"""), " ").trim()
    }

    private fun probeMediaDetails(tabId: String, media: SniffedMedia) {
        Thread {
            try {
                val reqBuilder = Request.Builder()
                    .url(media.url)
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36")
                    .header("Referer", media.referer)

                if (!media.cookie.isNullOrEmpty()) {
                    reqBuilder.header("Cookie", media.cookie)
                }

                if (media.format.contains("HLS", ignoreCase = true)) {
                    // Fetch top of master playlist to extract RESOLUTION=...
                    val resp = httpClient.newCall(reqBuilder.build()).execute()
                    if (resp.isSuccessful) {
                        val body = resp.body?.charStream()?.readLines()?.take(60)?.joinToString("\n") ?: ""
                        val matcher = Pattern.compile("RESOLUTION=(\\d+x\\d+)").matcher(body)
                        val resList = mutableListOf<String>()
                        while (matcher.find()) {
                            val g = matcher.group(1)
                            if (g != null) resList.add(g)
                        }
                        if (resList.isNotEmpty()) {
                            val best = resList.last()
                            val parts = best.split('x')
                            val h = parts.getOrNull(1)?.toIntOrNull() ?: 0
                            val label = when {
                                h >= 2160 -> "4K UHD"
                                h >= 1440 -> "2K QHD"
                                h >= 1080 -> "1080p FHD"
                                h >= 720 -> "720p HD"
                                h >= 480 -> "480p SD"
                                h > 0 -> "${h}p"
                                else -> best
                            }
                            media.resolution = label
                            notifyUpdated(tabId)
                        }
                    }
                    resp.close()
                } else {
                    // Send HEAD request for direct files to get Content-Length
                    val headReq = reqBuilder.head().build()
                    val resp = httpClient.newCall(headReq).execute()
                    if (resp.isSuccessful) {
                        val len = resp.header("Content-Length")?.toLongOrNull() ?: -1L
                        if (len > 0) {
                            media.sizeBytes = len
                            notifyUpdated(tabId)
                        }
                    }
                    resp.close()
                }
            } catch (_: Exception) {}
        }.start()
    }

    private fun notifyUpdated(tabId: String) {
        val list = getMediaForTab(tabId)
        notifyListeners(tabId, list.size, list)
    }

    private fun notifyListeners(tabId: String, count: Int, mediaList: List<SniffedMedia>) {
        mainHandler.post {
            synchronized(listeners) {
                for (listener in listeners) {
                    try {
                        listener.onMediaSniffed(tabId, count, mediaList)
                    } catch (_: Exception) {}
                }
            }
        }
    }

    /**
     * Injected DOM and JavaScript Network Sniffer Hook
     */
    const val MEDIA_SNIFFER_HOOK_JS = """
        (function() {
            if (window.__cdl_media_sniffer_hooked) return;
            window.__cdl_media_sniffer_hooked = true;

            const reported = new Set();

            function sendMedia(data) {
                if (!data || !data.url) return;
                const url = data.url.trim();
                if (url.startsWith('blob:') || url.startsWith('data:') || url.length < 8) return;
                if (reported.has(url)) return;
                reported.add(url);

                const lower = url.toLowerCase();
                if (lower.includes('googleads') || lower.includes('doubleclick') || lower.includes('/ads/') || lower.includes('analytics')) return;

                const payload = {
                    url: url,
                    title: data.title || document.title || 'Web Video Stream',
                    poster: data.poster || '',
                    format: data.format || (lower.includes('.m3u8') ? 'HLS (m3u8)' : (lower.includes('.webm') ? 'WebM' : (lower.includes('.mpd') ? 'DASH' : 'MP4'))),
                    duration: data.duration || '',
                    resolution: data.resolution || '',
                    cookie: document.cookie || ''
                };

                if (window.CloudDriveSnifferBridge && typeof window.CloudDriveSnifferBridge.onMediaDetected === 'function') {
                    window.CloudDriveSnifferBridge.onMediaDetected(JSON.stringify(payload));
                }
            }

            // 1. Intercept window.fetch
            const _origFetch = window.fetch;
            if (_origFetch) {
                window.fetch = function(input, init) {
                    try {
                        const url = typeof input === 'string' ? input : (input && input.url ? input.url : '');
                        if (url) {
                            const l = url.toLowerCase();
                            if (l.includes('.m3u8') || l.includes('.mp4') || l.includes('.webm') || l.includes('.mpd') || l.includes('videoplayback')) {
                                sendMedia({ url: url });
                            }
                        }
                    } catch(e) {}
                    return _origFetch.apply(this, arguments);
                };
            }

            // 2. Intercept XMLHttpRequest
            const _origOpen = XMLHttpRequest.prototype.open;
            if (_origOpen) {
                XMLHttpRequest.prototype.open = function(method, url) {
                    try {
                        if (url && typeof url === 'string') {
                            const l = url.toLowerCase();
                            if (l.includes('.m3u8') || l.includes('.mp4') || l.includes('.webm') || l.includes('.mpd') || l.includes('videoplayback')) {
                                sendMedia({ url: url });
                            }
                        }
                    } catch(e) {}
                    return _origOpen.apply(this, arguments);
                };
            }

            // 3. Intercept HTMLMediaElement.prototype.play
            const _origPlay = HTMLMediaElement.prototype.play;
            if (_origPlay) {
                HTMLMediaElement.prototype.play = function() {
                    try {
                        const src = this.currentSrc || this.src;
                        const poster = this.getAttribute('poster') || '';
                        const dur = !isNaN(this.duration) && this.duration > 0 ? Math.round(this.duration) + 's' : '';
                        const res = this.videoWidth && this.videoHeight ? this.videoWidth + 'x' + this.videoHeight : '';
                        if (src && !src.startsWith('blob:')) {
                            sendMedia({ url: src, poster: poster, duration: dur, resolution: res });
                        } else {
                            // Active video playing via blob: / MSE - report page video stream so user can download!
                            const pageUrl = window.location.href;
                            if (pageUrl && pageUrl.startsWith('http') && !pageUrl.includes('about:')) {
                                sendMedia({
                                    url: pageUrl,
                                    title: document.title || 'Page Video Stream',
                                    poster: poster,
                                    duration: dur,
                                    resolution: res || 'HD Stream',
                                    format: 'Web Stream'
                                });
                            }
                        }
                    } catch(e) {}
                    return _origPlay.apply(this, arguments);
                };
            }

            // 4. Capture 'play' and 'playing' events on window (covers custom web video players)
            window.addEventListener('play', function(e) {
                try {
                    const v = e.target;
                    if (v && v.tagName === 'VIDEO') {
                        const src = v.currentSrc || v.src;
                        const poster = v.getAttribute('poster') || '';
                        const dur = !isNaN(v.duration) && v.duration > 0 ? Math.round(v.duration) + 's' : '';
                        const res = v.videoWidth && v.videoHeight ? v.videoWidth + 'x' + v.videoHeight : '';
                        if (src && !src.startsWith('blob:')) {
                            sendMedia({ url: src, poster: poster, duration: dur, resolution: res });
                        } else {
                            const pageUrl = window.location.href;
                            if (pageUrl && pageUrl.startsWith('http') && !pageUrl.includes('about:')) {
                                sendMedia({
                                    url: pageUrl,
                                    title: document.title || 'Page Video Stream',
                                    poster: poster,
                                    duration: dur,
                                    resolution: res || 'HD Stream',
                                    format: 'Web Stream'
                                });
                            }
                        }
                    }
                } catch(ex) {}
            }, true);

            // 5. Intercept HTMLVideoElement src attribute mutations
            const desc = Object.getOwnPropertyDescriptor(HTMLMediaElement.prototype, 'src');
            if (desc && desc.set) {
                const _origSet = desc.set;
                desc.set = function(val) {
                    try {
                        if (val && typeof val === 'string' && !val.startsWith('blob:')) {
                            sendMedia({ url: val });
                        }
                    } catch(e) {}
                    return _origSet.call(this, val);
                };
                try {
                    Object.defineProperty(HTMLMediaElement.prototype, 'src', desc);
                } catch(e) {}
            }

            // 6. DOM Scanner for existing elements and embedded players
            function scanDom() {
                try {
                    const videos = document.querySelectorAll('video');
                    videos.forEach(function(v) {
                        const src = v.currentSrc || v.src;
                        const poster = v.getAttribute('poster') || '';
                        const dur = !isNaN(v.duration) && v.duration > 0 ? Math.round(v.duration) + 's' : '';
                        const res = v.videoWidth && v.videoHeight ? v.videoWidth + 'x' + v.videoHeight : '';
                        if (src && !src.startsWith('blob:')) {
                            sendMedia({ url: src, poster: poster, duration: dur, resolution: res });
                        } else if (src && src.startsWith('blob:')) {
                            const pageUrl = window.location.href;
                            if (pageUrl && pageUrl.startsWith('http') && !pageUrl.includes('about:')) {
                                sendMedia({
                                    url: pageUrl,
                                    title: document.title || 'Page Video Stream',
                                    poster: poster,
                                    duration: dur,
                                    resolution: res || 'HD Stream',
                                    format: 'Web Stream'
                                });
                            }
                        }
                        v.querySelectorAll('source').forEach(function(s) {
                            if (s.src && !s.src.startsWith('blob:')) {
                                sendMedia({ url: s.src, poster: v.getAttribute('poster') || '' });
                            }
                        });
                    });

                    // Detect Embedded Iframes (Doodstream, Streamwish, Filemoon, Streamtape, JWPlayer)
                    const iframes = document.querySelectorAll('iframe');
                    iframes.forEach(function(f) {
                        const src = f.src;
                        if (!src) return;
                        const l = src.toLowerCase();
                        if (l.includes('dood') || l.includes('streamwish') || l.includes('filemoon') || l.includes('streamtape') || l.includes('vidcloud') || l.includes('player') || l.includes('embed')) {
                            sendMedia({
                                url: src,
                                title: 'Embedded Player: ' + (document.title || 'Video Stream'),
                                format: 'Embed'
                            });
                        }
                    });
                } catch(e) {}
            }

            // 7. MutationObserver for dynamic SPAs (YouTube, TikTok, Instagram, Twitter, video portals)
            try {
                const observer = new MutationObserver(function() {
                    scanDom();
                });
                if (document.body) {
                    observer.observe(document.body, { childList: true, subtree: true });
                } else {
                    document.addEventListener('DOMContentLoaded', function() {
                        if (document.body) observer.observe(document.body, { childList: true, subtree: true });
                    });
                }
            } catch(e) {}

            scanDom();
            if (document.readyState !== 'complete') {
                window.addEventListener('DOMContentLoaded', scanDom);
                window.addEventListener('load', scanDom);
            }
            setInterval(scanDom, 2500);
        })();
    """
}
