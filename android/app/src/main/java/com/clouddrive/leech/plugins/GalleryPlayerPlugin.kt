package com.clouddrive.leech.plugins

import com.clouddrive.leech.player.GalleryPlayerActivity
import com.getcapacitor.JSObject
import com.getcapacitor.Plugin
import com.getcapacitor.PluginCall
import com.getcapacitor.PluginMethod
import com.getcapacitor.annotation.CapacitorPlugin

@CapacitorPlugin(name = "GalleryPlayer")
class GalleryPlayerPlugin : Plugin() {

    @PluginMethod
    fun playOfflineVideo(call: PluginCall) {
        val path = call.getString("path") ?: call.getString("url") ?: ""
        val title = call.getString("title") ?: "Offline Video"
        val poster = call.getString("poster") ?: ""
        val category = call.getString("category") ?: "offline"
        val index = call.getInt("index") ?: 0

        val startPositionMs = when {
            call.data.has("startPositionMs") -> call.data.optLong("startPositionMs", 0L)
            call.data.has("position") -> (call.data.optDouble("position", 0.0) * 1000).toLong()
            call.data.has("currentTime") -> (call.data.optDouble("currentTime", 0.0) * 1000).toLong()
            else -> call.getLong("startPositionMs") ?: 0L
        }

        val playlistJs = call.getArray("playlist")
        val titlesJs = call.getArray("titles")

        val playlist = ArrayList<String>()
        val titles = ArrayList<String>()

        if (playlistJs != null) {
            for (i in 0 until playlistJs.length()) {
                playlist.add(playlistJs.optString(i))
            }
        }

        if (titlesJs != null) {
            for (i in 0 until titlesJs.length()) {
                titles.add(titlesJs.optString(i))
            }
        }

        if (path.isEmpty() && playlist.isEmpty()) {
            call.reject("path or playlist is required")
            return
        }

        activity?.let { act ->
            GalleryPlayerActivity.start(
                context = act,
                url = path,
                title = title,
                poster = poster,
                category = category,
                startPositionMs = startPositionMs,
                playlist = playlist,
                titles = titles,
                index = index
            )
            val ret = JSObject().apply { put("success", true) }
            call.resolve(ret)
        } ?: run {
            call.reject("Activity context not available")
        }
    }
}
