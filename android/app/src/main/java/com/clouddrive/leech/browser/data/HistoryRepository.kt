package com.clouddrive.leech.browser.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

data class HistoryItem(
    val title: String,
    val url: String,
    val timestamp: Long = System.currentTimeMillis()
)

object HistoryRepository {
    private const val PREFS_NAME = "cdl_browser_history"
    private const val KEY_HISTORY = "history_items"
    private val gson = Gson()
    private val items = mutableListOf<HistoryItem>()
    private var isLoaded = false

    private fun load(context: Context) {
        if (isLoaded) return
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_HISTORY, null)
        if (!json.isNullOrEmpty()) {
            try {
                val type = object : TypeToken<List<HistoryItem>>() {}.type
                val list: List<HistoryItem> = gson.fromJson(json, type)
                items.clear()
                items.addAll(list)
            } catch (_: Exception) {}
        }
        isLoaded = true
    }

    private fun save(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = gson.toJson(items)
        prefs.edit().putString(KEY_HISTORY, json).apply()
    }

    fun addHistory(context: Context, title: String, url: String, isPrivate: Boolean) {
        if (isPrivate || url == "about:home" || url.isEmpty()) return
        load(context)
        items.removeAll { it.url == url }
        items.add(0, HistoryItem(title = title.ifEmpty { url }, url = url))
        if (items.size > 200) items.removeAt(items.size - 1)
        save(context)
    }

    fun getHistory(context: Context): List<HistoryItem> {
        load(context)
        return items.toList()
    }

    fun clearHistory(context: Context) {
        items.clear()
        save(context)
    }

    fun deleteItem(context: Context, url: String) {
        load(context)
        items.removeAll { it.url == url }
        save(context)
    }
}
