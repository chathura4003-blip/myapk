package com.clouddrive.leech.browser.engine

import android.webkit.WebView

data class WebViewTab(
    val id: String,
    var title: String = "Start Page",
    var url: String = "about:home",
    val isPrivate: Boolean = false,
    var isSecure: Boolean = false,
    var progress: Int = 0,
    var isLoading: Boolean = false,
    var canGoBack: Boolean = false,
    var canGoForward: Boolean = false,
    val webView: WebView,
    val createdAt: Long = System.currentTimeMillis()
)
