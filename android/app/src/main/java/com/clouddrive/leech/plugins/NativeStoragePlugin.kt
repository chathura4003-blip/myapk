package com.clouddrive.leech.plugins

import android.content.Context
import android.content.SharedPreferences
import com.clouddrive.leech.App
import com.clouddrive.leech.database.entities.FavoriteEntity
import com.clouddrive.leech.database.entities.HistoryEntity
import com.getcapacitor.JSArray
import com.getcapacitor.JSObject
import com.getcapacitor.Plugin
import com.getcapacitor.PluginCall
import com.getcapacitor.PluginMethod
import com.getcapacitor.annotation.CapacitorPlugin
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

@CapacitorPlugin(name = "NativeStorage")
class NativeStoragePlugin : Plugin() {

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)
    private val gson = Gson()

    private val prefs: SharedPreferences by lazy {
        val ctx = context ?: App.instance
        ctx.getSharedPreferences("cdl_native_storage", Context.MODE_PRIVATE)
    }

    override fun handleOnDestroy() {
        super.handleOnDestroy()
        job.cancel()
    }

    // =========================================================================
    // 1. KEY-VALUE DATASTORE / PREFERENCES API (Used by core.js NativeStorage)
    // =========================================================================

    @PluginMethod
    fun getItem(call: PluginCall) {
        val key = call.getString("key")
        if (key.isNullOrEmpty()) {
            call.reject("Storage key must be a valid string")
            return
        }
        try {
            val value = prefs.getString(key, null)
            val ret = JSObject()
            ret.put("value", value)
            call.resolve(ret)
        } catch (e: Exception) {
            call.reject("Failed to read storage key: ${e.message}")
        }
    }

    @PluginMethod
    fun setItem(call: PluginCall) {
        val key = call.getString("key")
        val value = call.getString("value")
        if (key.isNullOrEmpty()) {
            call.reject("Storage key must be a valid string")
            return
        }
        try {
            prefs.edit().putString(key, value ?: "").apply()
            val ret = JSObject()
            ret.put("key", key)
            ret.put("stored", true)
            call.resolve(ret)
        } catch (e: Exception) {
            call.reject("Failed to write storage key: ${e.message}")
        }
    }

    @PluginMethod
    fun removeItem(call: PluginCall) {
        val key = call.getString("key")
        if (key.isNullOrEmpty()) {
            call.reject("Storage key must be a valid string")
            return
        }
        try {
            prefs.edit().remove(key).apply()
            val ret = JSObject()
            ret.put("key", key)
            ret.put("removed", true)
            call.resolve(ret)
        } catch (e: Exception) {
            call.reject("Failed to remove storage key: ${e.message}")
        }
    }

    @PluginMethod
    fun clear(call: PluginCall) {
        try {
            prefs.edit().clear().apply()
            val ret = JSObject()
            ret.put("cleared", true)
            call.resolve(ret)
        } catch (e: Exception) {
            call.reject("Failed to clear storage: ${e.message}")
        }
    }

    @PluginMethod
    fun keys(call: PluginCall) {
        try {
            val keyList = prefs.all.keys.toList()
            val ret = JSObject()
            ret.put("keys", JSArray(gson.toJson(keyList)))
            call.resolve(ret)
        } catch (e: Exception) {
            call.reject("Failed to fetch storage keys: ${e.message}")
        }
    }

    // =========================================================================
    // 2. WATCH HISTORY & CONTINUE WATCHING API (Room Database)
    // =========================================================================

    @PluginMethod
    fun getHistory(call: PluginCall) {
        scope.launch {
            try {
                val list = App.instance.database.historyDao().getAllHistory()
                val json = gson.toJson(list)
                val ret = JSObject()
                ret.put("history", JSArray(json))
                call.resolve(ret)
            } catch (e: Exception) {
                call.reject("Failed to fetch history: ${e.message}")
            }
        }
    }

    @PluginMethod
    fun addHistory(call: PluginCall) {
        val url = call.getString("url") ?: ""
        val title = call.getString("title") ?: "Video"
        val poster = call.getString("poster") ?: ""
        val source = call.getString("source") ?: ""
        val category = call.getString("category") ?: "movies"
        val watchedMs = call.getLong("watchedDurationMs") ?: call.getLong("position") ?: 0L
        val totalMs = call.getLong("totalDurationMs") ?: call.getLong("duration") ?: 0L

        if (url.isEmpty()) {
            call.reject("URL is required")
            return
        }

        scope.launch {
            try {
                App.instance.database.historyDao().insertOrUpdate(
                    HistoryEntity(
                        url = url,
                        title = title,
                        poster = poster,
                        source = source,
                        category = category,
                        watchedDurationMs = watchedMs,
                        totalDurationMs = totalMs,
                        lastWatchedTimestamp = System.currentTimeMillis()
                    )
                )
                val ret = JSObject()
                ret.put("success", true)
                call.resolve(ret)
            } catch (e: Exception) {
                call.reject("Failed to record history: ${e.message}")
            }
        }
    }

    @PluginMethod
    fun deleteHistory(call: PluginCall) {
        val url = call.getString("url") ?: ""
        if (url.isEmpty()) {
            call.reject("URL is required")
            return
        }
        scope.launch {
            try {
                App.instance.database.historyDao().delete(url)
                val ret = JSObject()
                ret.put("success", true)
                call.resolve(ret)
            } catch (e: Exception) {
                call.reject("Failed to delete history item: ${e.message}")
            }
        }
    }

    @PluginMethod
    fun clearHistory(call: PluginCall) {
        scope.launch {
            try {
                App.instance.database.historyDao().clearAll()
                val ret = JSObject()
                ret.put("success", true)
                call.resolve(ret)
            } catch (e: Exception) {
                call.reject("Failed to clear history: ${e.message}")
            }
        }
    }

    @PluginMethod
    fun getContinueWatching(call: PluginCall) {
        scope.launch {
            try {
                val list = App.instance.database.historyDao().getContinueWatching()
                val json = gson.toJson(list)
                val ret = JSObject()
                ret.put("continueWatching", JSArray(json))
                call.resolve(ret)
            } catch (e: Exception) {
                call.reject("Failed to fetch continue watching: ${e.message}")
            }
        }
    }

    @PluginMethod
    fun saveContinueWatching(call: PluginCall) {
        val url = call.getString("url") ?: ""
        val title = call.getString("title") ?: "Video"
        val poster = call.getString("poster") ?: ""
        val source = call.getString("source") ?: ""
        val category = call.getString("category") ?: "movies"
        val positionMs = call.getLong("position") ?: call.getLong("watchedDurationMs") ?: 0L
        val durationMs = call.getLong("duration") ?: call.getLong("totalDurationMs") ?: 0L

        if (url.isEmpty()) {
            call.reject("URL is required")
            return
        }

        scope.launch {
            try {
                App.instance.database.historyDao().insertOrUpdate(
                    HistoryEntity(
                        url = url,
                        title = title,
                        poster = poster,
                        source = source,
                        category = category,
                        watchedDurationMs = positionMs,
                        totalDurationMs = durationMs,
                        lastWatchedTimestamp = System.currentTimeMillis()
                    )
                )
                val ret = JSObject()
                ret.put("success", true)
                call.resolve(ret)
            } catch (e: Exception) {
                call.reject("Failed to save continue watching: ${e.message}")
            }
        }
    }

    @PluginMethod
    fun removeContinueWatching(call: PluginCall) {
        val url = call.getString("url") ?: ""
        if (url.isEmpty()) {
            call.reject("URL is required")
            return
        }
        scope.launch {
            try {
                val existing = App.instance.database.historyDao().getHistoryByUrl(url)
                if (existing != null) {
                    // Mark watchedDurationMs = 0 to remove from continue watching rail while preserving history entry
                    App.instance.database.historyDao().insertOrUpdate(
                        existing.copy(watchedDurationMs = 0L)
                    )
                }
                val ret = JSObject()
                ret.put("success", true)
                call.resolve(ret)
            } catch (e: Exception) {
                call.reject("Failed to remove continue watching: ${e.message}")
            }
        }
    }

    // =========================================================================
    // 3. FAVORITES / BOOKMARKS API (Room Database)
    // =========================================================================

    @PluginMethod
    fun getFavorites(call: PluginCall) {
        scope.launch {
            try {
                val list = App.instance.database.favoritesDao().getAllFavorites()
                val json = gson.toJson(list)
                val ret = JSObject()
                ret.put("favorites", JSArray(json))
                call.resolve(ret)
            } catch (e: Exception) {
                call.reject("Failed to fetch favorites: ${e.message}")
            }
        }
    }

    @PluginMethod
    fun toggleFavorite(call: PluginCall) {
        val url = call.getString("url") ?: ""
        val title = call.getString("title") ?: "Movie"
        val poster = call.getString("poster") ?: ""
        val source = call.getString("source") ?: ""
        val category = call.getString("category") ?: "movies"

        if (url.isEmpty()) {
            call.reject("URL is required")
            return
        }

        scope.launch {
            try {
                val isFav = App.instance.database.favoritesDao().isFavorite(url)
                if (isFav) {
                    App.instance.database.favoritesDao().delete(url)
                } else {
                    App.instance.database.favoritesDao().insert(
                        FavoriteEntity(
                            url = url,
                            title = title,
                            poster = poster,
                            source = source,
                            category = category,
                            addedTimestamp = System.currentTimeMillis()
                        )
                    )
                }
                val ret = JSObject()
                ret.put("isFavorite", !isFav)
                call.resolve(ret)
            } catch (e: Exception) {
                call.reject("Failed to toggle favorite: ${e.message}")
            }
        }
    }

    @PluginMethod
    fun isFavorite(call: PluginCall) {
        val url = call.getString("url") ?: ""
        if (url.isEmpty()) {
            call.reject("URL is required")
            return
        }
        scope.launch {
            try {
                val isFav = App.instance.database.favoritesDao().isFavorite(url)
                val ret = JSObject()
                ret.put("isFavorite", isFav)
                call.resolve(ret)
            } catch (e: Exception) {
                call.reject("Failed to query favorite: ${e.message}")
            }
        }
    }

    @PluginMethod
    fun deleteFavorite(call: PluginCall) {
        val url = call.getString("url") ?: ""
        if (url.isEmpty()) {
            call.reject("URL is required")
            return
        }
        scope.launch {
            try {
                App.instance.database.favoritesDao().delete(url)
                val ret = JSObject()
                ret.put("success", true)
                call.resolve(ret)
            } catch (e: Exception) {
                call.reject("Failed to delete favorite: ${e.message}")
            }
        }
    }

    @PluginMethod
    fun clearFavorites(call: PluginCall) {
        scope.launch {
            try {
                App.instance.database.favoritesDao().clearAll()
                val ret = JSObject()
                ret.put("success", true)
                call.resolve(ret)
            } catch (e: Exception) {
                call.reject("Failed to clear favorites: ${e.message}")
            }
        }
    }
}
