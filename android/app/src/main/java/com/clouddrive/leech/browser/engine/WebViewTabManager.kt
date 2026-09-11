package com.clouddrive.leech.browser.engine

import android.annotation.SuppressLint
import android.content.Context
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import java.util.UUID

object WebViewTabManager {

    const val CHROME_MOBILE_UA = "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36"

    private val normalTabs = mutableListOf<WebViewTab>()
    private val privateTabs = mutableListOf<WebViewTab>()

    var activeTabId: String? = null
        private set

    var isPrivateMode: Boolean = false

    fun getTabs(privateOnly: Boolean = isPrivateMode): List<WebViewTab> {
        return if (privateOnly) privateTabs.toList() else normalTabs.toList()
    }

    fun getActiveTab(): WebViewTab? {
        val list = if (isPrivateMode) privateTabs else normalTabs
        return list.find { it.id == activeTabId } ?: list.firstOrNull()?.also { activeTabId = it.id }
    }

    fun getTabCount(privateOnly: Boolean = isPrivateMode): Int {
        return if (privateOnly) privateTabs.size else normalTabs.size
    }

    @SuppressLint("SetJavaScriptEnabled")
    fun createTab(context: Context, url: String = "about:home", isPrivate: Boolean = isPrivateMode): WebViewTab {
        val tabId = UUID.randomUUID().toString()
        val webView = WebView(context.applicationContext).apply {
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                allowContentAccess = true
                allowFileAccess = false
                loadsImagesAutomatically = true
                mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                useWideViewPort = true
                loadWithOverviewMode = true
                builtInZoomControls = true
                displayZoomControls = false
                cacheMode = if (isPrivate) WebSettings.LOAD_NO_CACHE else WebSettings.LOAD_DEFAULT
                userAgentString = CHROME_MOBILE_UA
                javaScriptCanOpenWindowsAutomatically = true
                setSupportMultipleWindows(true)
            }
            setLayerType(View.LAYER_TYPE_HARDWARE, null)
            isVerticalScrollBarEnabled = true
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
        }

        val cookieManager = CookieManager.getInstance()
        if (isPrivate) {
            cookieManager.setAcceptCookie(false)
            cookieManager.setAcceptThirdPartyCookies(webView, false)
        } else {
            cookieManager.setAcceptCookie(true)
            cookieManager.setAcceptThirdPartyCookies(webView, true)
        }

        val tab = WebViewTab(
            id = tabId,
            url = url,
            isPrivate = isPrivate,
            webView = webView
        )

        val jsInterface = com.clouddrive.leech.browser.sniffer.MediaSnifferJsInterface(
            getTabId = { tab.id },
            getPageUrl = { tab.webView.url ?: tab.url },
            getPageTitle = { tab.webView.title ?: tab.title }
        )
        webView.addJavascriptInterface(jsInterface, "CloudDriveSnifferBridge")

        if (isPrivate) {
            privateTabs.add(tab)
        } else {
            normalTabs.add(tab)
        }

        activeTabId = tabId
        isPrivateMode = isPrivate

        if (url != "about:home" && url.isNotEmpty()) {
            webView.loadUrl(url)
        }

        return tab
    }

    fun switchTab(tabId: String) {
        val normal = normalTabs.find { it.id == tabId }
        if (normal != null) {
            isPrivateMode = false
            activeTabId = tabId
            return
        }
        val privateTab = privateTabs.find { it.id == tabId }
        if (privateTab != null) {
            isPrivateMode = true
            activeTabId = tabId
        }
    }

    fun closeTab(tabId: String) {
        val list = if (isPrivateMode) privateTabs else normalTabs
        val index = list.indexOfFirst { it.id == tabId }
        if (index != -1) {
            val removed = list.removeAt(index)
            com.clouddrive.leech.browser.sniffer.BrowserMediaSniffer.clearForTab(removed.id)
            destroyWebView(removed.webView)

            if (activeTabId == tabId) {
                activeTabId = if (list.isNotEmpty()) {
                    val nextIndex = (index - 1).coerceAtLeast(0)
                    list[nextIndex].id
                } else {
                    null
                }
            }
        }
    }

    fun closeAllTabs(privateOnly: Boolean = isPrivateMode) {
        val list = if (privateOnly) privateTabs else normalTabs
        for (tab in list) {
            com.clouddrive.leech.browser.sniffer.BrowserMediaSniffer.clearForTab(tab.id)
            destroyWebView(tab.webView)
        }
        list.clear()
        activeTabId = null
    }

    private fun destroyWebView(wv: WebView) {
        try {
            wv.stopLoading()
            wv.loadUrl("about:blank")
            wv.clearHistory()
            wv.removeAllViews()
            wv.destroy()
        } catch (_: Exception) {}
    }
}
