package com.clouddrive.leech.plugins

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.widget.FrameLayout
import com.clouddrive.leech.R
import com.clouddrive.leech.browser.ui.NativeBrowserFragment
import com.clouddrive.leech.proxy.LocalMediaProxy
import com.clouddrive.leech.vpn.VpnEngineManager
import com.clouddrive.leech.vpn.net.UniversalAntiCensorDns
import com.getcapacitor.JSArray
import com.getcapacitor.JSObject
import com.getcapacitor.Plugin
import com.getcapacitor.PluginCall
import com.getcapacitor.PluginMethod
import com.getcapacitor.annotation.CapacitorPlugin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

@CapacitorPlugin(name = "NativeBrowser")
class NativeBrowserPlugin : Plugin() {

    private val scope = CoroutineScope(Dispatchers.IO)
    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .dns(UniversalAntiCensorDns.instance)
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(12, TimeUnit.SECONDS)
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

    @PluginMethod
    fun getProxyStatus(call: PluginCall) {
        scope.launch {
            try {
                val proxyPort = LocalMediaProxy.start()
                if (proxyPort <= 0) {
                    throw IllegalStateException("Local browser proxy did not start")
                }
                val autoIp = LocalMediaProxy.getAutoIp()
                val res = JSObject().apply {
                    put("success", true)
                    put("code", "OK")
                    put("message", "Mobile network proxy active")
                    put("running", true)
                    put("port", proxyPort)
                    // The WebView is on the same device; loopback is stable even when
                    // the active Wi-Fi/mobile interface changes or has no LAN route.
                    put("host", "127.0.0.1")
                    put("networkHost", autoIp)
                    put("browseUrl", "http://127.0.0.1:$proxyPort/browse")
                }
                withContext(Dispatchers.Main) {
                    call.resolve(res)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    val res = JSObject().apply {
                        put("success", false)
                        put("code", "PROXY_START_FAILED")
                        put("message", e.message ?: "Failed to start local media proxy")
                        put("running", false)
                    }
                    call.resolve(res)
                }
            }
        }
    }

    @PluginMethod
    fun probeMedia(call: PluginCall) {
        val url = call.getString("url") ?: ""
        if (url.isEmpty()) {
            call.resolve(JSObject().apply {
                put("success", false)
                put("code", "INVALID_URL")
                put("message", "URL is empty or required")
            })
            return
        }

        scope.launch {
            try {
                val req = Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36")
                    .header("Accept", "*/*")
                    .build()

                val resp = httpClient.newCall(req).execute()
                val httpCode = resp.code
                val contentType = resp.header("Content-Type") ?: ""
                val contentLength = resp.header("Content-Length")?.toLongOrNull() ?: -1L
                val isHls = url.contains(".m3u8", ignoreCase = true) || contentType.contains("mpegurl", ignoreCase = true)

                var resolution: String? = null
                var isMasterPlaylist = false
                var isMediaPlaylist = false
                val variants = JSArray()

                if (isHls && resp.isSuccessful) {
                    val bodySnippet = resp.body?.charStream()?.readLines()?.take(60)?.joinToString("\n") ?: ""
                    isMasterPlaylist = bodySnippet.contains("#EXT-X-STREAM-INF", ignoreCase = true)
                    isMediaPlaylist = bodySnippet.contains("#EXTINF", ignoreCase = true)

                    // Parse all RESOLUTION=WxH
                    val matcher = Pattern.compile("RESOLUTION=(\\d+x\\d+)").matcher(bodySnippet)
                    while (matcher.find()) {
                        val resStr = matcher.group(1)
                        if (resolution == null) resolution = resStr
                        variants.put(resStr)
                    }
                }

                resp.close()

                val res = JSObject().apply {
                    put("success", resp.isSuccessful)
                    put("code", if (resp.isSuccessful) "OK" else "HTTP_$httpCode")
                    put("message", if (resp.isSuccessful) "Media probe succeeded" else "HTTP $httpCode response")
                    val data = JSObject().apply {
                        put("url", url)
                        put("httpCode", httpCode)
                        put("mimeType", contentType)
                        put("contentLength", contentLength)
                        put("isHls", isHls)
                        put("isMasterPlaylist", isMasterPlaylist)
                        put("isMediaPlaylist", isMediaPlaylist)
                        if (resolution != null) put("resolution", resolution)
                        if (variants.length() > 0) put("variants", variants)
                    }
                    put("data", data)
                }

                withContext(Dispatchers.Main) {
                    call.resolve(res)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    val res = JSObject().apply {
                        put("success", false)
                        put("code", "NETWORK_ERROR")
                        put("message", e.message ?: "Failed to probe media")
                        put("data", JSObject().apply { put("url", url) })
                    }
                    call.resolve(res)
                }
            }
        }
    }

    @PluginMethod
    fun shareUrl(call: PluginCall) {
        val url = call.getString("url") ?: ""
        val title = call.getString("title") ?: "Share Link"
        if (url.isEmpty()) {
            call.reject("URL is required")
            return
        }

        try {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, title)
                putExtra(Intent.EXTRA_TEXT, url)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(Intent.createChooser(intent, "Share via:"))
            call.resolve(JSObject().apply { put("success", true) })
        } catch (e: Exception) {
            call.reject(e.message ?: "Failed to share")
        }
    }

    @PluginMethod
    fun copyToClipboard(call: PluginCall) {
        val text = call.getString("text") ?: ""
        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("URL", text)
            clipboard.setPrimaryClip(clip)
            call.resolve(JSObject().apply { put("success", true) })
        } catch (e: Exception) {
            call.reject(e.message ?: "Failed to copy")
        }
    }

    @PluginMethod
    fun clearBrowsingData(call: PluginCall) {
        try {
            CookieManager.getInstance().removeAllCookies(null)
            CookieManager.getInstance().flush()
            WebStorage.getInstance().deleteAllData()
            call.resolve(JSObject().apply { put("success", true) })
        } catch (e: Exception) {
            call.reject(e.message ?: "Failed to clear data")
        }
    }

    @PluginMethod
    fun showBrowser(call: PluginCall) {
        val url = call.getString("url") ?: "about:home"
        activity?.runOnUiThread {
            try {
                val act = activity as? androidx.appcompat.app.AppCompatActivity ?: run {
                    call.reject("Activity is not AppCompatActivity")
                    return@runOnUiThread
                }

                val root = act.findViewById<ViewGroup>(android.R.id.content)
                var container = root.findViewById<FrameLayout>(R.id.native_browser_container)
                if (container == null) {
                    container = FrameLayout(act).apply {
                        id = R.id.native_browser_container
                        val density = act.resources.displayMetrics.density
                        val bottomNavHeight = (65 * density).toInt()
                        layoutParams = FrameLayout.LayoutParams(
                            FrameLayout.LayoutParams.MATCH_PARENT,
                            FrameLayout.LayoutParams.MATCH_PARENT
                        ).apply {
                            bottomMargin = bottomNavHeight
                        }
                        setBackgroundColor(android.graphics.Color.parseColor("#0A0C10"))
                        isClickable = true
                        isFocusable = true
                    }
                    root.addView(container)
                }

                container.visibility = View.VISIBLE

                var fragment = act.supportFragmentManager.findFragmentByTag(NativeBrowserFragment.TAG) as? NativeBrowserFragment
                if (fragment == null) {
                    fragment = NativeBrowserFragment.newInstance(url)
                    act.supportFragmentManager.beginTransaction()
                        .replace(R.id.native_browser_container, fragment, NativeBrowserFragment.TAG)
                        .commitAllowingStateLoss()
                } else if (url.isNotEmpty() && url != "about:home") {
                    fragment.loadUrl(url)
                }

                call.resolve(JSObject().apply { put("success", true) })
            } catch (e: Exception) {
                call.reject("Failed to show browser: ${e.message}")
            }
        }
    }

    @PluginMethod
    fun hideBrowser(call: PluginCall) {
        activity?.runOnUiThread {
            try {
                val root = activity?.findViewById<ViewGroup>(android.R.id.content)
                val container = root?.findViewById<View>(R.id.native_browser_container)
                container?.visibility = View.GONE
                call.resolve(JSObject().apply { put("success", true) })
            } catch (e: Exception) {
                call.reject("Failed to hide browser: ${e.message}")
            }
        }
    }

    @PluginMethod
    fun loadUrl(call: PluginCall) {
        val url = call.getString("url") ?: ""
        if (url.isEmpty()) {
            call.reject("URL is required")
            return
        }
        activity?.runOnUiThread {
            try {
                val act = activity as? androidx.appcompat.app.AppCompatActivity
                val fragment = act?.supportFragmentManager?.findFragmentByTag(NativeBrowserFragment.TAG) as? NativeBrowserFragment
                fragment?.loadUrl(url)
                call.resolve(JSObject().apply { put("success", true) })
            } catch (e: Exception) {
                call.reject("Failed to load URL: ${e.message}")
            }
        }
    }

    @PluginMethod
    fun goBack(call: PluginCall) {
        activity?.runOnUiThread {
            try {
                val act = activity as? androidx.appcompat.app.AppCompatActivity
                val fragment = act?.supportFragmentManager?.findFragmentByTag(NativeBrowserFragment.TAG) as? NativeBrowserFragment
                val handled = fragment?.handleBackPress() ?: false
                call.resolve(JSObject().apply { put("handled", handled) })
            } catch (e: Exception) {
                call.reject("Failed to go back: ${e.message}")
            }
        }
    }
}
