package com.clouddrive.leech.plugins

import com.clouddrive.leech.App
import com.clouddrive.leech.database.entities.DownloadEntity
import com.clouddrive.leech.proxy.LocalMediaProxy
import com.clouddrive.leech.torrent.TorrentEngineManager
import com.getcapacitor.JSArray
import com.getcapacitor.JSObject
import com.getcapacitor.Plugin
import com.getcapacitor.PluginCall
import com.getcapacitor.PluginMethod
import com.getcapacitor.annotation.CapacitorPlugin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

@CapacitorPlugin(name = "NativeTorrent")
class NativeTorrentPlugin : Plugin() {

    private val ioScope = CoroutineScope(Dispatchers.IO)

    @PluginMethod
    fun startDownload(call: PluginCall) {
        val url = call.getString("url") ?: call.getString("magnet") ?: ""
        val title = call.getString("title") ?: call.getString("filename")
        val category = call.getString("category") ?: "torrents"
        val sequential = call.getBoolean("sequential", false) ?: false

        if (url.isEmpty()) {
            call.reject("URL or Magnet is required")
            return
        }

        try {
            val hash = TorrentEngineManager.addTorrent(
                uriOrMagnet = url,
                customTitle = title,
                category = category,
                sequential = sequential
            )

            if (!hash.isNullOrEmpty()) {
                val cleanTitle = title ?: "Torrent $hash"
                val entity = DownloadEntity(
                    id = hash,
                    title = cleanTitle,
                    url = url,
                    filename = if (cleanTitle.endsWith(".torrent")) cleanTitle else "$cleanTitle.torrent",
                    localFilePath = File(TorrentEngineManager.defaultSaveDir, cleanTitle).absolutePath,
                    downloadManagerId = -2L,
                    totalBytes = 0L,
                    downloadedBytes = 0L,
                    status = "downloading",
                    category = category,
                    speed = "Connecting to peers...",
                    progress = 0
                )
                ioScope.launch {
                    try {
                        App.instance.database.downloadDao().insertOrUpdate(entity)
                    } catch (_: Exception) {}
                }

                val ret = JSObject().apply {
                    put("success", true)
                    put("id", hash)
                    put("hash", hash)
                    put("title", cleanTitle)
                    put("category", category)
                }
                call.resolve(ret)
            } else {
                call.reject("Failed to add torrent to native engine")
            }
        } catch (e: Exception) {
            call.reject("Error adding torrent: ${e.message}")
        }
    }

    @PluginMethod
    fun getStreamUrl(call: PluginCall) {
        val url = call.getString("url") ?: call.getString("magnet") ?: call.getString("hash") ?: ""
        val title = call.getString("title") ?: "Torrent Stream"
        val fileIndex = call.getInt("fileIndex", -1) ?: -1

        if (url.isEmpty()) {
            call.reject("URL, Magnet or Hash is required")
            return
        }

        try {
            val hash = if (url.startsWith("magnet:") || url.contains(".torrent") || url.startsWith("http")) {
                TorrentEngineManager.addTorrent(
                    uriOrMagnet = url,
                    customTitle = title,
                    category = "movies",
                    sequential = true,
                    isStreaming = true
                )
            } else {
                url.lowercase()
            }

            if (hash.isNullOrEmpty()) {
                call.reject("Could not resolve torrent info hash")
                return
            }

            // Ensure streaming torrent is not registered in download DB
            ioScope.launch {
                try {
                    com.clouddrive.leech.App.instance.database.downloadDao().delete(hash)
                } catch (_: Exception) {}
            }

            TorrentEngineManager.getTorrentHandle(hash)?.let { th ->
                TorrentEngineManager.setSequentialDownload(th, true)
                TorrentEngineManager.prioritizeHeadAndTailPieces(th)
            }

            val proxyPort = LocalMediaProxy.start()
            val streamUrl = "http://127.0.0.1:$proxyPort/torrent-stream?hash=$hash" + if (fileIndex >= 0) "&fileIndex=$fileIndex" else ""

            val ret = JSObject().apply {
                put("success", true)
                put("hash", hash)
                put("streamUrl", streamUrl)
                put("url", streamUrl)
                put("title", title)
            }
            call.resolve(ret)
        } catch (e: Exception) {
            call.reject("Failed to get stream url: ${e.message}")
        }
    }

    @PluginMethod
    fun getTorrentStatus(call: PluginCall) {
        val hash = call.getString("hash") ?: call.getString("id") ?: ""
        if (hash.isEmpty()) {
            call.reject("hash is required")
            return
        }

        val status = TorrentEngineManager.getTorrentStatus(hash)
        if (status == null) {
            call.reject("Torrent not found")
            return
        }

        val ret = JSObject().apply {
            put("infoHash", status.infoHash)
            put("name", status.name)
            put("state", status.state)
            put("progress", status.progress)
            put("percent", (status.progress * 100).toInt())
            put("downloadRate", status.downloadRate)
            put("uploadRate", status.uploadRate)
            put("speedMBps", String.format(Locale.US, "%.2f", status.downloadRate / (1024.0 * 1024.0)))
            put("totalDone", status.totalDone)
            put("totalWanted", status.totalWanted)
            put("totalMB", String.format(Locale.US, "%.1f", status.totalWanted / (1024.0 * 1024.0)))
            put("downloadedMB", String.format(Locale.US, "%.1f", status.totalDone / (1024.0 * 1024.0)))
            put("numPeers", status.numPeers)
            put("numSeeds", status.numSeeds)
            put("isSequential", status.isSequential)
            put("savePath", status.savePath)

            val filesArr = JSArray()
            for (f in status.files) {
                val fObj = JSObject().apply {
                    put("index", f.index)
                    put("name", f.name)
                    put("path", f.path)
                    put("size", f.size)
                    put("isVideo", f.isVideo)
                }
                filesArr.put(fObj)
            }
            put("files", filesArr)
        }
        call.resolve(ret)
    }

    @PluginMethod
    fun getAllTorrents(call: PluginCall) {
        val list = TorrentEngineManager.getAllTorrents()
        val arr = JSArray()
        for (status in list) {
            val item = JSObject().apply {
                put("infoHash", status.infoHash)
                put("name", status.name)
                put("state", status.state)
                put("progress", status.progress)
                put("percent", (status.progress * 100).toInt())
                put("downloadRate", status.downloadRate)
                put("uploadRate", status.uploadRate)
                put("speedMBps", String.format(Locale.US, "%.2f", status.downloadRate / (1024.0 * 1024.0)))
                put("totalDone", status.totalDone)
                put("totalWanted", status.totalWanted)
                put("numPeers", status.numPeers)
                put("numSeeds", status.numSeeds)
            }
            arr.put(item)
        }
        val ret = JSObject().apply {
            put("torrents", arr)
        }
        call.resolve(ret)
    }

    @PluginMethod
    fun pause(call: PluginCall) {
        val hash = call.getString("hash") ?: call.getString("id") ?: ""
        if (hash.isNotEmpty()) TorrentEngineManager.pauseTorrent(hash)
        call.resolve(JSObject().apply { put("success", true) })
    }

    @PluginMethod
    fun resume(call: PluginCall) {
        val hash = call.getString("hash") ?: call.getString("id") ?: ""
        if (hash.isNotEmpty()) TorrentEngineManager.resumeTorrent(hash)
        call.resolve(JSObject().apply { put("success", true) })
    }

    @PluginMethod
    fun delete(call: PluginCall) {
        val hash = call.getString("hash") ?: call.getString("id") ?: ""
        val deleteFiles = call.getBoolean("deleteFiles", false) ?: false
        if (hash.isNotEmpty()) TorrentEngineManager.removeTorrent(hash, deleteFiles)
        call.resolve(JSObject().apply { put("success", true) })
    }
}
