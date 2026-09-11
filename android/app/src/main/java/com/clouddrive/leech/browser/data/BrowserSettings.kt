package com.clouddrive.leech.browser.data

import android.content.Context
import android.net.Uri

object BrowserSettings {

    enum class SearchEngine(val title: String, val searchUrl: String) {
        GOOGLE("Google", "https://www.google.com/search?q="),
        DUCKDUCKGO("DuckDuckGo", "https://duckduckgo.com/?q="),
        BING("Bing", "https://www.bing.com/search?q="),
        BRAVE("Brave", "https://search.brave.com/search?q=")
    }

    var activeSearchEngine: SearchEngine = SearchEngine.GOOGLE
    var isDesktopSiteDefault: Boolean = false

    fun getSearchEngine(context: Context): String {
        return activeSearchEngine.title
    }

    fun setSearchEngine(context: Context, engineName: String) {
        val found = SearchEngine.values().find { it.title.equals(engineName, ignoreCase = true) }
        if (found != null) {
            activeSearchEngine = found
        }
    }

    fun normalizeUrl(context: Context, input: String): String {
        return normalizeUrlOrSearch(input)
    }

    fun normalizeUrlOrSearch(input: String): String {
        val trimmed = input.trim()
        if (trimmed.isEmpty() || trimmed == "about:home") {
            return "about:home"
        }

        // Direct URL scheme check
        if (trimmed.matches(Regex("(?i)^[a-z][a-z0-9+.-]*://.*"))) {
            return trimmed
        }

        // Check if looks like a domain name (e.g. google.com, example.org/path)
        val hasSpace = trimmed.contains(" ")
        val hasDot = trimmed.contains(".")
        val isDomainLike = !hasSpace && hasDot && !trimmed.endsWith(".")

        return if (isDomainLike) {
            "https://$trimmed"
        } else {
            activeSearchEngine.searchUrl + Uri.encode(trimmed)
        }
    }
}
