package com.clouddrive.leech

import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import java.io.ByteArrayInputStream
import android.widget.Toast
import com.clouddrive.leech.browser.ui.NativeBrowserFragment
import com.clouddrive.leech.plugins.GalleryPlayerPlugin
import com.clouddrive.leech.plugins.NativeBrowserPlugin
import com.clouddrive.leech.plugins.NativeDownloadPlugin
import com.clouddrive.leech.plugins.NativeExtractorPlugin
import com.clouddrive.leech.plugins.NativeGdrivePlugin
import com.clouddrive.leech.plugins.NativePlayerPlugin
import com.clouddrive.leech.plugins.NativeStoragePlugin
import com.clouddrive.leech.plugins.NativeTorrentPlugin
import com.clouddrive.leech.plugins.NativeVpnPlugin
import com.clouddrive.leech.plugins.NativeLicensePlugin
import com.clouddrive.leech.proxy.LocalMediaProxy
import com.clouddrive.leech.torrent.TorrentEngineManager
import com.getcapacitor.BridgeActivity
import com.getcapacitor.BridgeWebViewClient
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.Settings
import okhttp3.Request
import com.clouddrive.leech.extractor.providers.movies.BaseMovieScraper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : BridgeActivity() {

    companion object {
        private const val TAG = "MainActivity"
        const val NOTIFICATION_PERMISSION_REQ_CODE = 101
        const val STORAGE_PERMISSION_REQ_CODE = 102

        /**
         * Feature-specific on-demand storage permission request.
         * Only invoked when the user explicitly triggers a local file/media feature.
         */
        @JvmStatic
        fun requestStoragePermissions(activity: android.app.Activity) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    val perms = mutableListOf<String>()
                    if (activity.checkSelfPermission(android.Manifest.permission.READ_MEDIA_VIDEO) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                        perms.add(android.Manifest.permission.READ_MEDIA_VIDEO)
                    }
                    if (activity.checkSelfPermission(android.Manifest.permission.READ_MEDIA_IMAGES) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                        perms.add(android.Manifest.permission.READ_MEDIA_IMAGES)
                    }
                    if (activity.checkSelfPermission(android.Manifest.permission.READ_MEDIA_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                        perms.add(android.Manifest.permission.READ_MEDIA_AUDIO)
                    }
                    if (perms.isNotEmpty()) {
                        activity.requestPermissions(perms.toTypedArray(), STORAGE_PERMISSION_REQ_CODE)
                    }
                } else {
                    val perms = mutableListOf<String>()
                    if (activity.checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                        perms.add(android.Manifest.permission.READ_EXTERNAL_STORAGE)
                    }
                    if (activity.checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                        perms.add(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    }
                    if (perms.isNotEmpty()) {
                        activity.requestPermissions(perms.toTypedArray(), STORAGE_PERMISSION_REQ_CODE)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error requesting feature-specific storage permissions: ${e.message}")
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Register On-Device Standalone Native Plugins
        registerPlugin(NativeExtractorPlugin::class.java)
        registerPlugin(NativePlayerPlugin::class.java)
        registerPlugin(GalleryPlayerPlugin::class.java)
        registerPlugin(NativeStoragePlugin::class.java)
        registerPlugin(NativeDownloadPlugin::class.java)
        registerPlugin(NativeTorrentPlugin::class.java)
        registerPlugin(NativeVpnPlugin::class.java)
        registerPlugin(NativeGdrivePlugin::class.java)
        registerPlugin(NativeBrowserPlugin::class.java)
        registerPlugin(NativeLicensePlugin::class.java)

        // Request Notification Permission on Android 13+ (API 33+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), NOTIFICATION_PERMISSION_REQ_CODE)
            }
        }

        super.onCreate(savedInstanceState)

        // Authoritative Back Button Dispatcher for responsive navigation
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                handleBackButtonInternal()
            }
        })

        // Seamless zero-flicker dark background on native WebView immediately
        try {
            bridge?.webView?.setBackgroundColor(Color.parseColor("#0a0c10"))
        } catch (_: Exception) {}

        // Launch heavy disk / network socket background services asynchronously off the main UI thread
        CoroutineScope(Dispatchers.IO).launch {
            try {
                LocalMediaProxy.start()
            } catch (e: Exception) {
                Log.w(TAG, "Failed to start LocalMediaProxy: ${e.message}")
            }

            try {
                TorrentEngineManager.init(this@MainActivity)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to initialize TorrentEngineManager: ${e.message}")
            }
        }
    }

    override fun onStart() {
        super.onStart()

        try {
            val webView = bridge?.webView
            if (webView != null) {
                webView.setBackgroundColor(Color.parseColor("#0a0c10"))
                WebView.setWebContentsDebuggingEnabled(false)
                val settings = webView.settings
                settings.javaScriptEnabled = true
                settings.mediaPlaybackRequiresUserGesture = false
                settings.domStorageEnabled = true
                settings.databaseEnabled = true
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                settings.setSupportMultipleWindows(false)
                settings.javaScriptCanOpenWindowsAutomatically = false
                settings.cacheMode = WebSettings.LOAD_DEFAULT
                settings.offscreenPreRaster = false // ❄️ Offscreen rasterization disabled to save battery and reduce GPU heat
                settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                settings.userAgentString = "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36"

                val cookieManager = CookieManager.getInstance()
                cookieManager.setAcceptCookie(true)
                cookieManager.setAcceptThirdPartyCookies(webView, false)

                // 🛡️ Defuse Rogue Ad / PWA Service Workers (Stops cinejoy.to & 5gvci.com service worker hijacks)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    try {
                        val swController = android.webkit.ServiceWorkerController.getInstance()
                        swController.setServiceWorkerClient(object : android.webkit.ServiceWorkerClient() {
                            override fun shouldInterceptRequest(request: WebResourceRequest): WebResourceResponse? {
                                val swUrl = request.url.toString()
                                val swHost = request.url.host?.lowercase() ?: ""
                                if (swHost.contains("cinejoy.to") || swHost.contains("5gvci.com") || isKnownAdUrl(swUrl, swHost)) {
                                    Log.w(TAG, "[SW Shield] Blocked service worker request: $swUrl")
                                    return WebResourceResponse(
                                        "text/plain",
                                        "UTF-8",
                                        204,
                                        "No Content",
                                        emptyMap(),
                                        ByteArrayInputStream(ByteArray(0))
                                    )
                                }
                                return null
                            }
                        })
                    } catch (e: Exception) {
                        Log.w(TAG, "ServiceWorkerController init notice:", e)
                    }
                }

                // 🛡️ Comprehensive 3-Tier Ad, Redirect & Intent Firewall for Embedded Players
                val b = bridge
                if (b != null) {
                    webView.webViewClient = object : BridgeWebViewClient(b) {
                        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                            val url = request?.url?.toString() ?: ""
                            val uri = request?.url
                            val scheme = uri?.scheme?.lowercase() ?: ""
                            val host = uri?.host?.lowercase() ?: ""

                            // 1. Block non-web intent / app launch schemes (market://, intent://, apps)
                            if (scheme.isNotEmpty() && scheme != "http" && scheme != "https" && scheme != "capacitor" && scheme != "file") {
                                Log.w(TAG, "[AdBlock] Intercepted & Blocked non-web intent scheme: $url")
                                return true
                            }

                            // 2. Prevent the Main App Window from navigating away to external ad / affiliate pages
                            if (request?.isForMainFrame == true) {
                                val isInternal = url.startsWith("http://localhost") ||
                                                  url.startsWith("https://localhost") ||
                                                  url.startsWith("capacitor://") ||
                                                  url.startsWith("file://")
                                if (!isInternal) {
                                    Log.w(TAG, "[AdBlock] Intercepted & Completely Blocked Main Frame Ad/Redirect: $url")
                                    return true // Never route rogue ad redirects into In-App Browser! Block outright!
                                }
                            }

                            // 3. Block known ad / betting / malicious redirect domains
                            if (isKnownAdUrl(url, host)) {
                                Log.w(TAG, "[AdBlock] Intercepted & Blocked Ad Redirect: $url")
                                return true
                            }

                            return super.shouldOverrideUrlLoading(view, request)
                        }

                        @Deprecated("Deprecated in Java")
                        override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                            // Defer to modern shouldOverrideUrlLoading(view, request) to prevent hijacking iframe video players
                            return false
                        }

                        override fun onReceivedSslError(view: WebView?, handler: android.webkit.SslErrorHandler?, error: android.net.http.SslError?) {
                            val primaryError = error?.primaryError ?: -1
                            Log.e(TAG, "[SSL Security] Blocked invalid SSL certificate (error $primaryError) for: ${error?.url}")
                            handler?.cancel()
                        }

                        override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                            val url = request?.url?.toString() ?: ""
                            val host = request?.url?.host?.lowercase() ?: ""
                            val lowerUrl = url.lowercase()

                            // 1. Defuse anti-devtool / anti-frame self-destruct scripts (VidSrc2 & CloudOrchestraNova)
                            if (lowerUrl.contains("disable-devtool")) {
                                val dummyJs = """
                                    (function(global) {
                                        function DisableDevtool(opts) { return false; }
                                        DisableDevtool.isDevToolOpened = function() { return false; };
                                        DisableDevtool.isRunning = false;
                                        DisableDevtool.isSuspend = true;
                                        DisableDevtool.clearLog = function() {};
                                        DisableDevtool.disableMenu = function() {};
                                        DisableDevtool.ondevtoolopen = function() {};
                                        global.DisableDevtool = DisableDevtool;
                                        if (typeof module !== 'undefined' && module.exports) { module.exports = DisableDevtool; }
                                    })(typeof globalThis !== 'undefined' ? globalThis : typeof window !== 'undefined' ? window : this);
                                """.trimIndent()
                                return WebResourceResponse(
                                    "application/javascript",
                                    "UTF-8",
                                    200,
                                    "OK",
                                    mapOf(
                                        "Access-Control-Allow-Origin" to "*",
                                        "Cache-Control" to "no-cache"
                                    ),
                                    ByteArrayInputStream(dummyJs.toByteArray())
                                )
                            }

                            if (isKnownAdUrl(url, host)) {
                                // Silently drop ad/tracker scripts at the network layer with 204 No Content
                                return WebResourceResponse(
                                    "text/plain",
                                    "UTF-8",
                                    204,
                                    "No Content",
                                    mapOf("Access-Control-Allow-Origin" to "*"),
                                    ByteArrayInputStream(ByteArray(0))
                                )
                            }

                            // 🍿 CloudOrchestraNova (VidSrc2 Core Stream Host) Proxy with Referer & CORS Injection
                            if (host.contains("cloudorchestranova.com")) {
                                try {
                                    val reqBuilder = Request.Builder().url(url)
                                    var hasReferer = false
                                    request?.requestHeaders?.forEach { (k, v) ->
                                        if (k.equals("Referer", ignoreCase = true)) {
                                            hasReferer = true
                                            reqBuilder.header(k, if (v.contains("localhost") || v.isEmpty()) "https://vidsrc2.ru/" else v)
                                        } else if (!k.equals("Host", ignoreCase = true) && !k.equals("User-Agent", ignoreCase = true)) {
                                            reqBuilder.header(k, v)
                                        }
                                    }
                                    if (!hasReferer) {
                                        reqBuilder.header("Referer", "https://vidsrc2.ru/")
                                    }
                                    reqBuilder.header("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36")
                                    val response = BaseMovieScraper.defaultClient.newCall(reqBuilder.build()).execute()
                                    val responseHeaders = mutableMapOf<String, String>()
                                    for (i in 0 until response.headers.size) {
                                        val name = response.headers.name(i)
                                        val value = response.headers.value(i)
                                        if (!name.equals("x-frame-options", ignoreCase = true) &&
                                            !name.equals("content-security-policy", ignoreCase = true) &&
                                            !name.equals("content-security-policy-report-only", ignoreCase = true)) {
                                            responseHeaders[name] = value
                                        }
                                    }
                                    responseHeaders["Access-Control-Allow-Origin"] = "*"
                                    val rawContentType = response.header("Content-Type", "text/html") ?: "text/html"
                                    val mimeType = rawContentType.substringBefore(";").trim()
                                    val encoding = if (rawContentType.contains("charset=", ignoreCase = true)) {
                                        rawContentType.substringAfter("charset=", "UTF-8").substringBefore(";").trim()
                                    } else {
                                        "UTF-8"
                                    }
                                    return WebResourceResponse(
                                        mimeType,
                                        encoding,
                                        response.code,
                                        response.message.ifEmpty { "OK" },
                                        responseHeaders,
                                        response.body?.byteStream()
                                    )
                                } catch (e: Exception) {
                                    Log.w(TAG, "Failed to proxy cloudorchestranova request: $url", e)
                                }
                            }

                            // 🛑 Kill rogue ad / PWA service worker scripts before they can install
                            if (lowerUrl.contains("service-worker.js") || lowerUrl.contains("/sw.js") || host.contains("5gvci.com")) {
                                Log.w(TAG, "[SW Shield] Intercepted & Neutered Service Worker script: $url")
                                val unregisterScript = "self.addEventListener('install', function(e) { self.skipWaiting(); }); self.addEventListener('activate', function(e) { self.registration.unregister(); });"
                                return WebResourceResponse(
                                    "application/javascript",
                                    "UTF-8",
                                    200,
                                    "OK",
                                    mapOf("Content-Type" to "application/javascript"),
                                    ByteArrayInputStream(unregisterScript.toByteArray())
                                )
                            }

                            // 🍿 Cinejoy.to In-App Video Player Iframe Unblocker (Strips X-Frame-Options & CSP restrictions for all Cinejoy HTML documents)
                            val isCinejoyHtmlDoc = host.contains("cinejoy.to") &&
                                !url.contains("/_app/") &&
                                !url.contains("/api/") &&
                                !url.endsWith(".js") &&
                                !url.endsWith(".css") &&
                                !url.endsWith(".png") &&
                                !url.endsWith(".jpg") &&
                                !url.endsWith(".svg") &&
                                !url.endsWith(".webp") &&
                                !url.endsWith(".ico") &&
                                !url.endsWith(".woff") &&
                                !url.endsWith(".woff2") &&
                                !url.endsWith(".json") &&
                                !url.endsWith(".m3u8") &&
                                !url.endsWith(".mp4")

                            if (isCinejoyHtmlDoc) {
                                try {
                                    val cookie = CookieManager.getInstance().getCookie(url)
                                    val okRequest = Request.Builder()
                                        .url(url)
                                        .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36")
                                        .header("Referer", "https://cinejoy.to/")
                                        .apply {
                                            if (!cookie.isNullOrEmpty()) {
                                                header("Cookie", cookie)
                                            }
                                            request?.requestHeaders?.forEach { (k, v) ->
                                                if (!k.equals("Host", ignoreCase = true) &&
                                                    !k.equals("Referer", ignoreCase = true) &&
                                                    !k.equals("User-Agent", ignoreCase = true) &&
                                                    !k.equals("Cookie", ignoreCase = true) &&
                                                    !k.equals("Accept-Encoding", ignoreCase = true)) {
                                                    header(k, v)
                                                }
                                            }
                                        }
                                        .build()
                                    val response = BaseMovieScraper.defaultClient.newCall(okRequest).execute()
                                    val responseHeaders = mutableMapOf<String, String>()
                                    for (i in 0 until response.headers.size) {
                                        val name = response.headers.name(i)
                                        val value = response.headers.value(i)
                                        if (name.equals("x-frame-options", ignoreCase = true) ||
                                            name.equals("content-security-policy", ignoreCase = true) ||
                                            name.equals("content-security-policy-report-only", ignoreCase = true) ||
                                            name.equals("content-encoding", ignoreCase = true) ||
                                            name.equals("content-length", ignoreCase = true)) {
                                            continue
                                        }
                                        responseHeaders[name] = value
                                    }
                                    responseHeaders["Content-Security-Policy"] = "frame-ancestors *"
                                    responseHeaders["Access-Control-Allow-Origin"] = "*"
                                    var rawHtml = response.body?.string() ?: ""
                                    // 🛑 Neutralize navigator.serviceWorker in Cinejoy HTML so it NEVER installs
                                    rawHtml = rawHtml
                                        .replace("'serviceWorker' in navigator", "false")
                                        .replace("\"serviceWorker\" in navigator", "false")
                                        .replace("serviceWorker", "disabledServiceWorker")
                                        .replace("/service-worker.js", "/disabled-sw.js")
                                        .replace("/sw.js", "/disabled-sw.js")

                                    val responseBytes = rawHtml.toByteArray(Charsets.UTF_8)
                                    return WebResourceResponse(
                                        "text/html",
                                        "UTF-8",
                                        response.code,
                                        response.message.ifEmpty { "OK" },
                                        responseHeaders,
                                        ByteArrayInputStream(responseBytes)
                                    )
                                } catch (e: Exception) {
                                    Log.w(TAG, "Failed to proxy cinejoy request: $url", e)
                                }
                            }

                            return super.shouldInterceptRequest(view, request)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error configuring WebView settings: ${e.message}", e)
        }
    }

    override fun onPause() {
        super.onPause()
        try {
            // Pause WebView visual rendering without freezing global JavaScript async promises
            bridge?.webView?.onPause()
        } catch (_: Exception) {}
    }

    override fun onResume() {
        super.onResume()
        try {
            bridge?.webView?.onResume()
        } catch (_: Exception) {}

        // Authoritative Live License & Tab Status Sync with Admin Server on every APK resume
        try {
            bridge?.webView?.evaluateJavascript("""
                (function() {
                    if (typeof window.syncLicenseWithServer === 'function') {
                        window.syncLicenseWithServer();
                    }
                })();
            """.trimIndent(), null)
        } catch (_: Exception) {}

        // Guarantee WebView scrolling is completely unlocked when returning from Pro Player / External Activities, and kill any background audio
        try {
            bridge?.webView?.evaluateJavascript("""
                (function() {
                    if (typeof window.forceUnlockPageScroll === 'function') {
                        window.forceUnlockPageScroll();
                    } else {
                        document.body.classList.remove('modal-open');
                        document.body.style.overflow = '';
                        document.documentElement.style.overflow = '';
                    }
                    var pModal = document.getElementById('playerModal');
                    if (pModal && pModal.classList.contains('hidden')) {
                        pModal.querySelectorAll('video, audio').forEach(function(v) {
                            try { v.pause(); v.currentTime = 0; v.src = ''; } catch(e) {}
                        });
                        pModal.querySelectorAll('iframe').forEach(function(ifr) {
                            try { ifr.src = 'about:blank'; } catch(e) {}
                        });
                    }
                    if (window.navigator && window.navigator.serviceWorker && window.navigator.serviceWorker.getRegistrations) {
                        window.navigator.serviceWorker.getRegistrations().then(function(regs) {
                            for (var r of regs) { r.unregister(); }
                        }).catch(function() {});
                    }
                })();
            """.trimIndent(), null)
        } catch (_: Exception) {}
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        try {
            if (level >= TRIM_MEMORY_RUNNING_LOW) {
                bridge?.webView?.clearCache(false)
                System.gc()
            }
        } catch (_: Exception) {}
    }

    private var lastBackPressTime = 0L

    private fun handleBackButtonInternal() {
        val root = findViewById<ViewGroup>(android.R.id.content)
        val container = root?.findViewById<View>(R.id.native_browser_container)
        if (container != null && container.visibility == View.VISIBLE) {
            val fragment = supportFragmentManager.findFragmentByTag(NativeBrowserFragment.TAG) as? NativeBrowserFragment
            if (fragment != null && fragment.handleBackPress()) {
                return
            } else {
                container.visibility = View.GONE
                bridge?.webView?.evaluateJavascript("""
                    (function() {
                        if (typeof window.closeInAppBrowser === 'function') {
                            window.closeInAppBrowser();
                        } else if (typeof window.switchTab === 'function') {
                            window.switchTab('movies');
                        }
                    })();
                """.trimIndent(), null)
                return
            }
        }

        val webView = bridge?.webView
        if (webView != null) {
            webView.evaluateJavascript("""
                (function() {
                    if (typeof window.handleAndroidBackButton === 'function') {
                        return window.handleAndroidBackButton();
                    }
                    return false;
                })();
            """.trimIndent()) { result ->
                val handled = result != null && (result.trim() == "true" || result.trim() == "\"true\"")
                if (!handled) {
                    val now = System.currentTimeMillis()
                    if (now - lastBackPressTime < 2000) {
                        finish()
                    } else {
                        lastBackPressTime = now
                        Toast.makeText(this@MainActivity, "Press back again to exit", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            return
        }
    }

    @Suppress("DEPRECATION")
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        handleBackButtonInternal()
    }

    /**
     * Comprehensive Ad, Popunder, Betting & Malicious Network Filter
     * Prevents embedded video players from hijacking user screen or opening rogue tabs.
     */
    private fun isKnownAdUrl(url: String, host: String): Boolean {
        val lowerUrl = url.lowercase()
        val lowerHost = host.lowercase()

        // 0. Explicit Whitelist of legitimate streaming CDNs, cloud storage & movie APIs
        if (lowerHost.contains("vidsrc2.ru") ||
            lowerHost.contains("vidlink.pro") ||
            lowerHost.contains("multiembed.mov") ||
            lowerHost.contains("vidsrc") ||
            lowerHost.contains("cloudorchestranova") ||
            lowerHost.contains("vidsrcme") ||
            lowerHost.contains("vidapi") ||
            lowerHost.contains("cinejoy") ||
            lowerHost.contains("2embed") ||
            lowerHost.contains("autoembed") ||
            lowerHost.contains("cineru") ||
            lowerHost.contains("pixeldrain") ||
            lowerHost.contains("google") ||
            lowerHost.contains("mega.nz") ||
            lowerHost.contains("mega.io") ||
            lowerHost.contains("sinhalasub") ||
            lowerHost.contains("sub.lk") ||
            lowerHost.contains("baiscope") ||
            lowerHost.contains("piratelk") ||
            lowerHost.contains("themoviedb") ||
            lowerHost.contains("tmdb") ||
            lowerHost.contains("cloudflare") ||
            lowerHost.contains("jsdelivr") ||
            lowerHost.contains("fontawesome") ||
            lowerHost.contains("strem.io") ||
            lowerHost.contains("cinemeta") ||
            lowerHost.contains("localhost")) {
            // If whitelisted, only intercept if explicitly an ad path
            if (!lowerUrl.contains("/ads/") && !lowerUrl.contains("/popunder") && !lowerUrl.contains("/onclick")) {
                return false
            }
        }

        // 1. Intercept Known Betting Networks frequently popping up on free stream embeds
        val bettingKeywords = arrayOf(
            "1xbet", "betwinner", "betway", "parimatch", "melbet", "mostbet",
            "linebet", "dafabet", "stake.com", "bet365", "1x-bet", "mega-pari",
            "bcgame", "betmaster", "betclic", "bwin", "betfair", "pin-up", "1win"
        )
        for (b in bettingKeywords) {
            if (lowerHost.contains(b) || lowerUrl.contains(b)) return true
        }

        // 2. Intercept Known Ad Networks, Popunders, Trackers, Clickjackers
        val adKeywords = arrayOf(
            "onclick", "adcash", "popads", "adsterra", "propellerads", "propeller",
            "histats", "exoclick", "juicyads", "doubleclick", "googlesyndication",
            "adnxs", "adsystem", "adskeeper", "mgid", "outbrain", "taboola",
            "trafficjunky", "yieldmo", "bidvertiser", "revcontent", "infolinks",
            "ero-advertising", "trafficstars", "yllix", "clickadu", "hilltopads",
            "richpush", "evadav", "monetag", "coinhive", "crypto-loot", "vlitag",
            "pubfuture", "highrevenuenetwork", "profitablegatecpm", "profitablecpmrate",
            "highcpmgate", "deloplen", "whomeeno", "bidgear", "dexpredict",
            "creativecdn", "popunder", "adsupply", "tsyndicate", "adsco.re",
            "adform", "smartadserver", "realsrv.com", "tsyndicate.com", "wpadmngr.com",
            "onclickalgo.com", "propush", "alwingulla", "creative", "counter",
            "banner", "cpm", "adserver", "yadro", "livejasmin", "directrev"
        )
        for (ad in adKeywords) {
            if (lowerHost.contains(ad) || lowerUrl.contains(ad)) return true
        }

        // 3. YouTube App Hijack
        if (lowerUrl.startsWith("vnd.youtube") || (lowerUrl.contains("youtube.com") && !lowerUrl.contains("youtube-nocookie.com/embed") && !lowerUrl.contains("/embed/"))) {
            return true
        }

        return false
    }

    private fun checkAndRequestStoragePermissions() {
        requestStoragePermissions(this)
    }

    override fun onDestroy() {
        if (!isChangingConfigurations) {
            try {
                LocalMediaProxy.stop(force = false)
            } catch (_: Exception) {}
        }
        super.onDestroy()
    }
}
