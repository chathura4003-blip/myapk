package com.clouddrive.leech.browser.sniffer

import android.webkit.JavascriptInterface
import org.json.JSONObject

class MediaSnifferJsInterface(
    private val getTabId: () -> String,
    private val getPageUrl: () -> String,
    private val getPageTitle: () -> String
) {
    @JavascriptInterface
    fun onMediaDetected(jsonString: String) {
        try {
            val json = JSONObject(jsonString)
            val url = json.optString("url", "").trim()
            if (url.isEmpty()) return

            val tabId = getTabId()
            val pageUrl = getPageUrl()
            val pageTitle = getPageTitle()

            val rawTitle = json.optString("title", "").ifEmpty { pageTitle }
            val poster = json.optString("poster", "").ifEmpty { null }
            val format = json.optString("format", "").ifEmpty { BrowserMediaSniffer.detectFormat(url) }
            val duration = json.optString("duration", "").ifEmpty { null }
            val res = json.optString("resolution", "").ifEmpty { null }
            val cookie = json.optString("cookie", "").ifEmpty { null }

            val sniffed = SniffedMedia(
                url = url,
                title = rawTitle,
                format = format,
                posterUrl = poster,
                duration = duration,
                resolution = res,
                pageUrl = pageUrl,
                referer = pageUrl,
                cookie = cookie
            )

            BrowserMediaSniffer.addFromJs(tabId, sniffed)
        } catch (e: Exception) {
            android.util.Log.w("MediaSnifferJs", "Failed to parse sniffed JSON: ${e.message}")
        }
    }
}
