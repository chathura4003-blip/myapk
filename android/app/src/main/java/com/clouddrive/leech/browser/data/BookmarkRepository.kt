package com.clouddrive.leech.browser.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

data class BookmarkItem(
    val title: String,
    val url: String,
    val timestamp: Long = System.currentTimeMillis()
)

object BookmarkRepository {
    private const val PREFS_NAME = "cdl_browser_bookmarks"
    private const val KEY_BOOKMARKS = "bookmark_items"
    private val gson = Gson()
    private val items = mutableListOf<BookmarkItem>()
    private var isLoaded = false

    private fun load(context: Context) {
        if (isLoaded) return
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_BOOKMARKS, null)
        if (!json.isNullOrEmpty()) {
            try {
                val type = object : TypeToken<List<BookmarkItem>>() {}.type
                val list: List<BookmarkItem> = gson.fromJson(json, type)
                items.clear()
                items.addAll(list)
            } catch (_: Exception) {}
        }
        isLoaded = true
    }

    private fun save(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = gson.toJson(items)
        prefs.edit().putString(KEY_BOOKMARKS, json).apply()
    }

    fun isBookmarked(context: Context, url: String): Boolean {
        load(context)
        return items.any { it.url == url }
    }

    fun addBookmark(context: Context, title: String, url: String) {
        if (url == "about:home" || url.isEmpty()) return
        load(context)
        items.removeAll { it.url == url }
        items.add(0, BookmarkItem(title = title.ifEmpty { url }, url = url))
        save(context)
    }

    fun removeBookmark(context: Context, url: String) {
        load(context)
        items.removeAll { it.url == url }
        save(context)
    }

    fun getBookmarks(context: Context): List<BookmarkItem> {
        load(context)
        return items.toList()
    }
}
