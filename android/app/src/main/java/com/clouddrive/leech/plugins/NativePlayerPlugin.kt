package com.clouddrive.leech.plugins

import android.content.Intent
import android.content.pm.ActivityInfo
import android.net.Uri
import com.clouddrive.leech.player.MovieCinemaPlayerActivity
import com.clouddrive.leech.proxy.LocalMediaProxy
import com.clouddrive.leech.torrent.TorrentEngineManager
import com.getcapacitor.JSObject
import com.getcapacitor.Plugin
import com.getcapacitor.PluginCall
import com.getcapacitor.PluginMethod
import com.getcapacitor.annotation.CapacitorPlugin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@CapacitorPlugin(name = "NativePlayer")
class NativePlayerPlugin : Plugin() {

    @PluginMethod
    fun playVideo(call: PluginCall) {
        val streamUrl = call.getString("streamUrl") ?: call.getString("url") ?: call.getString("path") ?: ""
        val title = call.getString("title") ?: "Playing Movie"
        val poster = call.getString("poster") ?: ""
        val category = call.getString("category") ?: "movies"
        val startPositionMs = when {
            call.data.has("startPositionMs") -> call.data.optLong("startPositionMs", 0L)
            call.data.has("position") -> (call.data.optDouble("position", 0.0) * 1000).toLong()
            call.data.has("currentTime") -> (call.data.optDouble("currentTime", 0.0) * 1000).toLong()
            else -> call.getLong("startPositionMs") ?: 0L
        }
        
        val serversJs = call.getArray("servers") ?: call.getArray("qualities")
        val serversJson = serversJs?.toString() ?: call.getString("serversJson") ?: "[]"

        val isTv = call.getBoolean("isTv", false) ?: false
        val seriesTitle = call.getString("seriesTitle") ?: ""
        val season = call.getInt("season", 1) ?: 1
        val episode = call.getInt("episode", 1) ?: 1
        val tmdbId = call.getString("tmdb") ?: call.getString("tmdbId") ?: ""
        val imdbId = call.getString("imdb") ?: call.getString("imdbId") ?: ""
        val seriesJson = call.getObject("seriesData")?.toString()
            ?: call.getString("seriesJson")
            ?: "{}"

        if (streamUrl.isEmpty()) {
            call.reject("streamUrl is required")
            return
        }

        val lowerUrl = streamUrl.trim().lowercase()
        if (lowerUrl.startsWith("javascript:") || lowerUrl.startsWith("data:") || lowerUrl.startsWith("about:")) {
            call.reject("Unsafe URL scheme rejected")
            return
        }

        activity?.let { act ->
            MovieCinemaPlayerActivity.start(
                context = act,
                url = streamUrl,
                title = title,
                poster = poster,
                category = category,
                startPositionMs = startPositionMs,
                serversJson = serversJson,
                isTv = isTv,
                seriesTitle = seriesTitle,
                season = season,
                episode = episode,
                tmdbId = tmdbId,
                imdbId = imdbId,
                seriesJson = seriesJson
            )
            val ret = JSObject().apply { put("success", true) }
            call.resolve(ret)
        } ?: run {
            call.reject("Activity context not available")
        }
    }

    @PluginMethod
    fun playTorrentStream(call: PluginCall) {
        val streamUrl = call.getString("streamUrl") ?: call.getString("url") ?: call.getString("magnet") ?: ""
        val title = call.getString("title") ?: "Torrent Cinema Stream"
        val poster = call.getString("poster") ?: ""
        val fileIndex = call.getInt("fileIndex", -1) ?: -1

        if (streamUrl.isEmpty()) {
            call.reject("streamUrl is required")
            return
        }

        val lowerTorrentUrl = streamUrl.trim().lowercase()
        if (!lowerTorrentUrl.startsWith("magnet:") && !lowerTorrentUrl.startsWith("http://") && !lowerTorrentUrl.startsWith("https://") && !lowerTorrentUrl.endsWith(".torrent")) {
            call.reject("Invalid torrent URL or magnet link")
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val hash = TorrentEngineManager.addTorrent(
                    uriOrMagnet = streamUrl,
                    customTitle = title,
                    category = "movies",
                    sequential = true,
                    isStreaming = true
                )

                if (hash.isNullOrEmpty()) {
                    withContext(Dispatchers.Main) {
                        call.reject("Failed to add torrent to native streaming engine")
                    }
                    return@launch
                }

                // Ensure no stale download entry appears in Downloads tab for this streaming hash
                try {
                    com.clouddrive.leech.App.instance.database.downloadDao().delete(hash)
                } catch (_: Exception) {}

                TorrentEngineManager.getTorrentHandle(hash)?.let { th ->
                    th.resume()
                    try { th.forceReannounce() } catch (_: Throwable) {}
                    TorrentEngineManager.setSequentialDownload(th, true)
                    TorrentEngineManager.prioritizeHeadAndTailPieces(th)
                }

                val port = LocalMediaProxy.start()
                val localStreamUrl = "http://127.0.0.1:$port/torrent-stream?hash=$hash" + if (fileIndex >= 0) "&fileIndex=$fileIndex" else ""

                withContext(Dispatchers.Main) {
                    activity?.let { act ->
                        MovieCinemaPlayerActivity.start(
                            context = act,
                            url = localStreamUrl,
                            title = title,
                            poster = poster
                        )
                        val ret = JSObject().apply {
                            put("success", true)
                            put("streamUrl", localStreamUrl)
                            put("hash", hash)
                        }
                        call.resolve(ret)
                    } ?: call.reject("Activity context not available")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    call.reject("Torrent streaming error: ${e.message}")
                }
            }
        }
    }

    @PluginMethod
    fun getProxiedStreamUrl(call: PluginCall) {
        val rawUrl = call.getString("url") ?: call.getString("streamUrl") ?: ""
        if (rawUrl.isEmpty()) {
            call.reject("url is required")
            return
        }
        val proxied = LocalMediaProxy.getProxiedUrl(rawUrl)
        val ret = JSObject().apply {
            put("success", true)
            put("url", proxied)
            put("proxiedUrl", proxied)
        }
        call.resolve(ret)
    }

    @PluginMethod
    fun getProxiedEmbedUrl(call: PluginCall) {
        val rawUrl = call.getString("url") ?: call.getString("embedUrl") ?: ""
        if (rawUrl.isEmpty()) {
            call.reject("url is required")
            return
        }
        val proxied = LocalMediaProxy.getProxiedEmbedUrl(rawUrl)
        val ret = JSObject().apply {
            put("success", true)
            put("url", proxied)
            put("proxiedUrl", proxied)
        }
        call.resolve(ret)
    }

    @PluginMethod
    fun openYouTube(call: PluginCall) {
        val query = call.getString("query") ?: call.getString("title") ?: ""
        val targetUrl = call.getString("url") ?: ""
        activity?.let { act ->
            try {
                val intent = if (targetUrl.isNotEmpty()) {
                    Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl)).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                } else {
                    Intent(Intent.ACTION_SEARCH).apply {
                        setPackage("com.google.android.youtube")
                        putExtra("query", query)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                }
                if (intent.resolveActivity(act.packageManager) != null) {
                    act.startActivity(intent)
                } else {
                    val webIntent = Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse(
                            if (targetUrl.isNotEmpty()) targetUrl else "https://www.youtube.com/results?search_query=${Uri.encode(query)}"
                        )
                    ).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    act.startActivity(webIntent)
                }
                call.resolve(JSObject().apply { put("success", true) })
            } catch (_: Exception) {
                try {
                    val webIntent = Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("https://www.youtube.com/results?search_query=${Uri.encode(query)}")
                    ).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    act.startActivity(webIntent)
                    call.resolve(JSObject().apply { put("success", true) })
                } catch (err: Exception) {
                    call.reject(err.message)
                }
            }
        } ?: call.reject("Activity context not available")
    }

    @PluginMethod
    fun setOrientation(call: PluginCall) {
        val orientation = call.getString("orientation") ?: "sensor"
        activity?.runOnUiThread {
            when (orientation.lowercase()) {
                "landscape", "sensor_landscape" -> {
                    activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                }
                "portrait", "sensor_portrait" -> {
                    activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                }
                "auto", "sensor", "unspecified" -> {
                    activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                }
            }
            call.resolve(JSObject().apply { put("success", true) })
        } ?: call.reject("Activity context not available")
    }
}
