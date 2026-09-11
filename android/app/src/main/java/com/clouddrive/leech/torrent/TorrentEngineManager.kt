package com.clouddrive.leech.torrent

import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Environment
import android.util.Log
import com.clouddrive.leech.App
import com.clouddrive.leech.database.entities.DownloadEntity
import com.clouddrive.leech.plugins.DownloadNotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import org.libtorrent4j.AlertListener
import org.libtorrent4j.AnnounceEntry
import org.libtorrent4j.Priority
import org.libtorrent4j.SessionManager
import org.libtorrent4j.SessionParams
import org.libtorrent4j.Sha1Hash
import org.libtorrent4j.TorrentFlags
import org.libtorrent4j.TorrentHandle
import org.libtorrent4j.TorrentInfo
import java.util.Locale
import org.libtorrent4j.alerts.Alert
import org.libtorrent4j.alerts.AlertType
import org.libtorrent4j.alerts.MetadataReceivedAlert
import org.libtorrent4j.alerts.TorrentFinishedAlert
import org.libtorrent4j.swig.add_torrent_params
import org.libtorrent4j.swig.error_code
import org.libtorrent4j.swig.libtorrent
import org.libtorrent4j.swig.settings_pack
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

data class TorrentStatusInfo(
    val infoHash: String,
    val name: String,
    val state: String,
    val progress: Float, // 0.0 to 1.0
    val downloadRate: Long, // bytes/sec
    val uploadRate: Long, // bytes/sec
    val totalDone: Long,
    val totalWanted: Long,
    val numPeers: Int,
    val numSeeds: Int,
    val isSequential: Boolean,
    val savePath: String,
    val files: List<TorrentFileInfo> = emptyList()
)

data class TorrentFileInfo(
    val index: Int,
    val name: String,
    val path: String,
    val size: Long,
    val isVideo: Boolean
)

object TorrentEngineManager {
    private const val TAG = "TorrentEngineManager"

    private val sessionManager by lazy { SessionManager() }
    private val scope = CoroutineScope(Dispatchers.IO)
    private var syncJob: Job? = null
    private var isInitialized = false

    // Maps infoHash lowercase -> metadata map
    private val torrentMeta = ConcurrentHashMap<String, MutableMap<String, String>>()

    val defaultSaveDir: File by lazy {
        val app = App.instance
        // App-specific created storage: 100% POSIX Writable on Android 10-15 without permission prompts
        val appExtDir = File(app.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "Torrents")
        if (!appExtDir.exists()) appExtDir.mkdirs()
        Log.i(TAG, "📂 Using App-Specific Storage (Scoped): ${appExtDir.absolutePath}")
        appExtDir
    }

    val PUBLIC_TRACKERS = listOf(
        "udp://tracker.opentrackr.org:1337/announce",
        "udp://open.tracker.cl:1337/announce",
        "udp://tracker.torrent.eu.org:451/announce",
        "udp://open.stealth.si:80/announce",
        "udp://explodie.org:6969/announce",
        "udp://tracker.openbittorrent.com:6969/announce",
        "udp://exodus.desync.com:6969/announce",
        "udp://9.rarbg.to:2920/announce",
        "udp://tracker.coppersurfer.tk:6969/announce",
        "http://tracker.openbittorrent.com:80/announce"
    )

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    @Synchronized
    fun init(context: Context) {
        if (isInitialized) return
        isInitialized = true
        startPeriodicSync()
        // ❄️ Thermal Saver: Do not start heavy C++ DHT swarm unless active torrent downloads exist
        scope.launch {
            try {
                val activeCount = App.instance.database.downloadDao().getAllDownloads()
                    .count { it.downloadManagerId == -2L && (it.status == "downloading" || it.status == "pending") }
                if (activeCount > 0) {
                    startSession()
                    Log.i(TAG, "⚡ Resuming active torrent session ($activeCount active tasks)")
                } else {
                    Log.i(TAG, "❄️ Torrent engine kept sleeping until a torrent download starts")
                }
            } catch (_: Exception) {}
        }
        Log.i(TAG, "⚡ Native TorrentEngineManager initialized successfully (Thermal-Aware)")
    }

    fun startSession() {
        if (sessionManager.isRunning) {
            try { sessionManager.swig().resume() } catch (_: Exception) {}
            return
        }

        val params = SessionParams()
        val sp = params.settings

        try {
            // Enable DHT, LSD, UPnP, NAT-PMP
            sp.setBoolean(settings_pack.bool_types.enable_dht.swigValue(), true)
            sp.setBoolean(settings_pack.bool_types.enable_lsd.swigValue(), true)
            sp.setBoolean(settings_pack.bool_types.enable_upnp.swigValue(), true)
            sp.setBoolean(settings_pack.bool_types.enable_natpmp.swigValue(), true)

            // Enable TCP and uTP incoming/outgoing
            try { sp.setBoolean(settings_pack.bool_types.enable_incoming_tcp.swigValue(), true) } catch (_: Exception) {}
            try { sp.setBoolean(settings_pack.bool_types.enable_outgoing_tcp.swigValue(), true) } catch (_: Exception) {}
            try { sp.setBoolean(settings_pack.bool_types.enable_incoming_utp.swigValue(), true) } catch (_: Exception) {}
            try { sp.setBoolean(settings_pack.bool_types.enable_outgoing_utp.swigValue(), true) } catch (_: Exception) {}

            // ❄️ Mobile Thermal Limits: 120 peer connections saturates 4G/5G max bandwidth without CPU throttling
            sp.setInteger(settings_pack.int_types.connections_limit.swigValue(), 120)
            sp.setInteger(settings_pack.int_types.active_downloads.swigValue(), 4)
            sp.setInteger(settings_pack.int_types.active_seeds.swigValue(), 2)
            sp.setInteger(settings_pack.int_types.active_limit.swigValue(), 6)
            sp.setInteger(settings_pack.int_types.download_rate_limit.swigValue(), 0) // unlimited
            sp.setInteger(settings_pack.int_types.upload_rate_limit.swigValue(), 0)   // unlimited

            // DHT Bootstrap Routers & Dynamic Listening Interfaces (port 0 = auto-bind available open port)
            try {
                sp.setString(settings_pack.string_types.dht_bootstrap_nodes.swigValue(),
                    "router.bittorrent.com:6881,dht.transmissionbt.com:6881,router.utorrent.com:6881,dht.libtorrent.org:25401,dht.aelitis.com:6881")
                sp.setString(settings_pack.string_types.listen_interfaces.swigValue(), "0.0.0.0:0,[::]:0,0.0.0.0:6881,[::]:6881")
            } catch (_: Exception) {}
        } catch (_: Exception) {}

        sessionManager.addListener(object : AlertListener {
            override fun types(): IntArray? = null

            override fun alert(alert: Alert<*>) {
                try {
                    when (alert.type()) {
                        AlertType.METADATA_RECEIVED -> {
                            val a = alert as? MetadataReceivedAlert
                            val th = a?.handle()
                            if (th != null && th.swig().is_valid) {
                                onMetadataReceived(th)
                            }
                        }
                        AlertType.TORRENT_FINISHED -> {
                            val a = alert as? TorrentFinishedAlert
                            val th = a?.handle()
                            if (th != null && th.swig().is_valid) {
                                onTorrentCompleted(th)
                            }
                        }
                        AlertType.TORRENT_ERROR -> {
                            Log.w(TAG, "Torrent error alert: ${alert.message()}")
                        }
                        AlertType.FILE_ERROR -> {
                            Log.e(TAG, "Torrent file error alert: ${alert.message()}")
                        }
                        else -> {}
                    }
                } catch (err: Throwable) {
                    Log.w(TAG, "Alert error: ${err.message}")
                }
            }
        })

        sessionManager.start(params)
        try { sessionManager.swig().resume() } catch (_: Exception) {}
    }

    private fun onMetadataReceived(th: TorrentHandle) {
        try {
            val hash = th.infoHash().toHex().lowercase()
            val meta = torrentMeta[hash]
            val isSeq = meta?.get("sequential") == "true"
            val savePathStr = meta?.get("savePath") ?: defaultSaveDir.absolutePath
            val saveDir = File(savePathStr)
            if (!saveDir.exists()) saveDir.mkdirs()

            th.swig().unset_flags(TorrentFlags.PAUSED)
            th.swig().set_flags(TorrentFlags.AUTO_MANAGED)
            th.resume()
            try { th.forceReannounce() } catch (_: Throwable) {}
            try { th.swig().force_dht_announce() } catch (_: Throwable) {}

            val tf = th.torrentFile()
            if (tf != null) {
                val numFiles = tf.files().numFiles()
                for (i in 0 until numFiles) {
                    th.filePriority(i, Priority.DEFAULT)
                }
            }

            if (isSeq) {
                setSequentialDownload(th, true)
                prioritizeHeadAndTailPieces(th)
            }
            Log.i(TAG, "📦 Metadata received for $hash (sequential=$isSeq). Torrent unpaused and downloading.")
        } catch (e: Exception) {
            Log.e(TAG, "Error in onMetadataReceived: ${e.message}", e)
        }
    }

    private fun onTorrentCompleted(th: TorrentHandle) {
        val s = th.status()
        val totalWanted = s.totalWanted()
        val totalDone = s.totalDone()
        if (totalWanted > 0L && totalDone < (totalWanted - 64 * 1024L)) {
            Log.w(TAG, "TorrentFinishedAlert received prematurely ($totalDone / $totalWanted). Waking torrent to complete all pieces.")
            val tf = th.torrentFile()
            if (tf != null) {
                for (i in 0 until tf.files().numFiles()) {
                    th.filePriority(i, Priority.DEFAULT)
                }
                for (p in 0 until tf.swig().num_pieces()) {
                    th.piecePriority(p, Priority.DEFAULT)
                }
            }
            try { th.swig().unset_flags(TorrentFlags.PAUSED) } catch (_: Throwable) {}
            try { th.swig().set_flags(TorrentFlags.AUTO_MANAGED) } catch (_: Throwable) {}
            try { th.resume() } catch (_: Throwable) {}
            return
        }

        val hash = th.infoHash().toHex().lowercase()
        val appCtx = App.instance
        DownloadNotificationHelper.cancel(appCtx, hash.hashCode())

        val tf = th.torrentFile()
        if (tf != null) {
            val files = tf.files()
            val filePaths = mutableListOf<String>()
            val savePath = torrentMeta[hash]?.get("savePath") ?: defaultSaveDir.absolutePath
            for (i in 0 until files.numFiles()) {
                val p = File(savePath, files.filePath(i)).absolutePath
                filePaths.add(p)
            }
            if (filePaths.isNotEmpty()) {
                MediaScannerConnection.scanFile(appCtx, filePaths.toTypedArray(), null, null)
            }
        }
    }

    fun setSequentialDownload(th: TorrentHandle, sequential: Boolean) {
        try {
            if (sequential) {
                th.swig().set_flags(TorrentFlags.SEQUENTIAL_DOWNLOAD)
                th.setSequentialRange(0)
            } else {
                th.swig().unset_flags(TorrentFlags.SEQUENTIAL_DOWNLOAD)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error setting sequential mode: ${e.message}")
        }
    }

    fun prioritizeHeadAndTailPieces(th: TorrentHandle) {
        try {
            val tf = th.torrentFile() ?: return
            val numPieces = tf.swig().num_pieces()
            if (numPieces <= 0) return

            // 1. Ensure ALL pieces have at least DEFAULT priority so the entire torrent completes
            for (i in 0 until numPieces) {
                th.piecePriority(i, Priority.DEFAULT)
            }

            // 2. High priority for first 12 pieces for instant playback/fast start
            val headCount = minOf(12, numPieces)
            for (i in 0 until headCount) {
                th.piecePriority(i, Priority.TOP_PRIORITY)
                th.setPieceDeadline(i, 0)
            }

            // 3. High priority for last 10 pieces for video index/cues
            val tailCount = minOf(10, numPieces)
            for (i in (numPieces - tailCount) until numPieces) {
                if (i >= 0) {
                    th.piecePriority(i, Priority.TOP_PRIORITY)
                    th.setPieceDeadline(i, 150)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error prioritizing head/tail: ${e.message}")
        }
    }

    fun addTorrent(
        uriOrMagnet: String,
        customTitle: String? = null,
        category: String = "torrents",
        sequential: Boolean = false,
        saveDir: File = defaultSaveDir,
        isStreaming: Boolean = false
    ): String? {
        startSession()

        val rawInput = uriOrMagnet.trim()

        // Direct .torrent URL handling
        if ((rawInput.startsWith("http://") || rawInput.startsWith("https://")) &&
            (rawInput.contains(".torrent") || rawInput.contains("/torrent/download/"))
        ) {
            val hashFromHttp = downloadTorrentFileAndAdd(rawInput, customTitle, category, sequential, saveDir, isStreaming)
            if (!hashFromHttp.isNullOrEmpty()) return hashFromHttp

            // 🛡️ Fallback: If HTTP .torrent download failed (e.g. ISP blocked yts.mx), extract 40-char hash and turn into magnet!
            val hexMatch = Regex("""[0-9a-fA-F]{40}""").find(rawInput)?.value
            if (!hexMatch.isNullOrEmpty()) {
                val fallbackMagnet = "magnet:?xt=urn:btih:${hexMatch.lowercase()}&dn=${java.net.URLEncoder.encode(customTitle ?: "Movie", "UTF-8")}"
                Log.i(TAG, "🧲 HTTP .torrent download failed; falling back to enriched magnet for hash $hexMatch")
                return addTorrent(fallbackMagnet, customTitle, category, sequential, saveDir, isStreaming)
            }
            return null
        }

        // Magnet Link Handling
        if (rawInput.startsWith("magnet:")) {
            val enrichedMagnet = enrichMagnetTrackers(rawInput)
            val hash = extractHashFromMagnet(enrichedMagnet) ?: ""

            if (hash.isNotEmpty()) {
                val existing = getTorrentHandle(hash)
                if (existing != null && existing.swig().is_valid) {
                    if (sequential) {
                        setSequentialDownload(existing, true)
                        prioritizeHeadAndTailPieces(existing)
                    } else {
                        val tf = existing.torrentFile()
                        if (tf != null) {
                            for (i in 0 until tf.files().numFiles()) {
                                existing.filePriority(i, Priority.DEFAULT)
                            }
                        }
                    }
                    existing.swig().unset_flags(TorrentFlags.PAUSED)
                    existing.swig().set_flags(TorrentFlags.AUTO_MANAGED)
                    existing.resume()
                    try { existing.forceReannounce() } catch (_: Throwable) {}
                    return hash
                }

                if (!saveDir.exists()) saveDir.mkdirs()

                torrentMeta[hash] = mutableMapOf(
                    "title" to (customTitle ?: "Torrent $hash"),
                    "category" to category,
                    "sequential" to sequential.toString(),
                    "magnet" to enrichedMagnet,
                    "savePath" to saveDir.absolutePath,
                    "isStreaming" to isStreaming.toString()
                )

                try {
                    val ec = error_code()
                    val p = libtorrent.parse_magnet_uri(enrichedMagnet, ec)
                    p.save_path = saveDir.absolutePath

                    if (sequential) {
                        p.flags = p.flags.or_(TorrentFlags.SEQUENTIAL_DOWNLOAD)
                    }

                    val rawHandle = sessionManager.swig().add_torrent(p, ec)
                    if (rawHandle != null && rawHandle.is_valid) {
                        val th = TorrentHandle(rawHandle)
                        th.swig().unset_flags(TorrentFlags.PAUSED)
                        th.swig().set_flags(TorrentFlags.AUTO_MANAGED)
                        th.resume()
                        for (tr in PUBLIC_TRACKERS) {
                            try { th.addTracker(AnnounceEntry(tr)) } catch (_: Throwable) {}
                        }
                        try { th.forceReannounce() } catch (_: Throwable) {}
                        try { th.swig().force_dht_announce() } catch (_: Throwable) {}
                        if (sequential) {
                            setSequentialDownload(th, true)
                        }
                        Log.i(TAG, "🧲 Enqueued magnet download: $hash")
                        return hash
                    } else {
                        Log.e(TAG, "add_torrent returned invalid handle. error: ${ec.message()}")
                    }
                } catch (e: Throwable) {
                    Log.e(TAG, "Error adding magnet: ${e.message}", e)
                }
            }
        }

        return null
    }

    private fun downloadTorrentFileAndAdd(
        torrentUrl: String,
        customTitle: String?,
        category: String,
        sequential: Boolean,
        saveDir: File,
        isStreaming: Boolean = false
    ): String? {
        try {
            val reqBuilder = Request.Builder()
                .url(torrentUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36")
                .header("Accept", "*/*")
                .header("Accept-Language", "en-US,en;q=0.9")

            if (torrentUrl.contains("yts.mx") || torrentUrl.contains("yts.")) {
                reqBuilder.header("Referer", "https://yts.mx/")
                reqBuilder.header("Origin", "https://yts.mx")
            }

            val req = reqBuilder.build()

            val bytes = httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                resp.body?.bytes() ?: return null
            }

            val tempTorrentFile = File(App.instance.cacheDir, "temp_${System.currentTimeMillis()}.torrent")
            FileOutputStream(tempTorrentFile).use { it.write(bytes) }

            val ti = TorrentInfo(tempTorrentFile)
            val hash = ti.swig().info_hash().to_hex().lowercase()

            val existing = getTorrentHandle(hash)
            if (existing != null && existing.swig().is_valid) {
                if (sequential) {
                    setSequentialDownload(existing, true)
                    prioritizeHeadAndTailPieces(existing)
                } else {
                    val tf = existing.torrentFile()
                    if (tf != null) {
                        for (i in 0 until tf.files().numFiles()) {
                            existing.filePriority(i, Priority.DEFAULT)
                        }
                    }
                }
                existing.swig().unset_flags(TorrentFlags.PAUSED)
                existing.swig().set_flags(TorrentFlags.AUTO_MANAGED)
                existing.resume()
                tempTorrentFile.delete()
                return hash
            }

            if (!saveDir.exists()) saveDir.mkdirs()

            val title = customTitle ?: ti.swig().name()
            torrentMeta[hash] = mutableMapOf(
                "title" to title,
                "category" to category,
                "sequential" to sequential.toString(),
                "magnet" to "magnet:?xt=urn:btih:$hash&dn=${Uri.encode(title)}",
                "savePath" to saveDir.absolutePath,
                "isStreaming" to isStreaming.toString()
            )

            val p = add_torrent_params()
            p.set_ti(ti.swig())
            p.save_path = saveDir.absolutePath
            if (sequential) {
                p.flags = p.flags.or_(TorrentFlags.SEQUENTIAL_DOWNLOAD)
            }
            val ec = error_code()
            val rawHandle = sessionManager.swig().add_torrent(p, ec)

            if (rawHandle != null && rawHandle.is_valid) {
                val th = TorrentHandle(rawHandle)
                th.swig().unset_flags(TorrentFlags.PAUSED)
                th.swig().set_flags(TorrentFlags.AUTO_MANAGED)
                th.resume()
                for (tr in PUBLIC_TRACKERS) {
                    try { th.addTracker(AnnounceEntry(tr)) } catch (_: Throwable) {}
                }
                try { th.forceReannounce() } catch (_: Throwable) {}
                try { th.swig().force_dht_announce() } catch (_: Throwable) {}

                val numFiles = ti.swig().num_files()
                for (i in 0 until numFiles) {
                    th.filePriority(i, Priority.DEFAULT)
                }

                if (sequential) {
                    setSequentialDownload(th, true)
                    prioritizeHeadAndTailPieces(th)
                }
            }

            tempTorrentFile.delete()
            return hash
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to download and add torrent file: ${e.message}", e)
            return null
        }
    }

    fun getTorrentHandle(infoHashHex: String): TorrentHandle? {
        if (!sessionManager.isRunning) return null
        return try {
            val sha = Sha1Hash.parseHex(infoHashHex)
            sessionManager.find(sha)
        } catch (_: Exception) {
            null
        }
    }

    fun pauseTorrent(infoHashHex: String) {
        val th = getTorrentHandle(infoHashHex)
        if (th != null && th.swig().is_valid) {
            try { th.swig().unset_flags(TorrentFlags.AUTO_MANAGED) } catch (_: Throwable) {}
            try { th.swig().set_flags(TorrentFlags.PAUSED) } catch (_: Throwable) {}
            th.pause()
        }
    }

    fun resumeTorrent(infoHashHex: String) {
        val th = getTorrentHandle(infoHashHex)
        if (th != null && th.swig().is_valid) {
            try { th.swig().unset_flags(TorrentFlags.PAUSED) } catch (_: Throwable) {}
            try { th.swig().set_flags(TorrentFlags.AUTO_MANAGED) } catch (_: Throwable) {}
            th.resume()
            try { th.forceReannounce() } catch (_: Throwable) {}
        }
    }

    fun removeTorrent(infoHashHex: String, deleteFiles: Boolean = false) {
        val th = getTorrentHandle(infoHashHex)
        if (th != null && th.swig().is_valid) {
            try {
                sessionManager.remove(th)
            } catch (_: Exception) {}
        }
        val meta = torrentMeta.remove(infoHashHex.lowercase())
        if (deleteFiles && meta != null) {
            val savePath = meta["savePath"] ?: defaultSaveDir.absolutePath
            val title = meta["title"] ?: ""
            if (title.isNotEmpty()) {
                try {
                    File(savePath, title).deleteRecursively()
                } catch (_: Exception) {}
            }
        }
        DownloadNotificationHelper.cancel(App.instance, infoHashHex.hashCode())
    }

    fun isStreaming(infoHashHex: String): Boolean {
        return torrentMeta[infoHashHex.lowercase()]?.get("isStreaming") == "true"
    }

    fun getTorrentMeta(infoHashHex: String): Map<String, String>? {
        return torrentMeta[infoHashHex.lowercase()]
    }

    fun getTorrentStatus(infoHashHex: String): TorrentStatusInfo? {
        val th = getTorrentHandle(infoHashHex) ?: return null
        if (!th.swig().is_valid) return null

        val s = th.status()
        val hash = th.infoHash().toHex().lowercase()
        val meta = torrentMeta[hash]
        val name = meta?.get("title") ?: s.swig().name.ifEmpty { "Torrent $hash" }

        val totalDone = s.totalDone()
        val totalWanted = s.totalWanted()
        val isReallyCompleted = totalWanted > 0L && totalDone >= (totalWanted - 64 * 1024L)

        val isPaused = try { th.swig().flags().and_(TorrentFlags.PAUSED).non_zero() } catch (_: Throwable) { false }
        val stateStr = when {
            isPaused -> "paused"
            s.swig().state.swigValue() == 0 -> "checking"
            s.swig().state.swigValue() == 1 -> "downloading_metadata"
            s.swig().state.swigValue() == 2 -> "downloading"
            s.swig().state.swigValue() == 3 -> if (isReallyCompleted) "completed" else "downloading"
            s.swig().state.swigValue() == 4 -> if (isReallyCompleted) "completed" else "downloading"
            s.swig().state.swigValue() == 5 -> "allocating"
            s.swig().state.swigValue() == 6 -> "checking_resume"
            else -> "downloading"
        }

        // Calculate accurate progress: never report 1.0 unless 100% of wanted bytes are finished
        val realProgress = when {
            isReallyCompleted -> 1.0f
            totalWanted > 0L -> (totalDone.toDouble() / totalWanted.toDouble()).toFloat().coerceIn(0.0f, 0.99f)
            else -> 0.0f
        }

        val fileList = mutableListOf<TorrentFileInfo>()
        val tf = th.torrentFile()
        if (tf != null) {
            val files = tf.files()
            for (i in 0 until files.numFiles()) {
                val fName = files.fileName(i)
                val fPath = files.filePath(i)
                val fSize = files.fileSize(i)
                val isVid = isVideoFile(fName)
                fileList.add(TorrentFileInfo(i, fName, fPath, fSize, isVid))
            }
        }

        val isSeq = th.swig().flags().and_(TorrentFlags.SEQUENTIAL_DOWNLOAD).non_zero()
        val savePath = meta?.get("savePath") ?: defaultSaveDir.absolutePath

        return TorrentStatusInfo(
            infoHash = hash,
            name = name,
            state = stateStr,
            progress = realProgress,
            downloadRate = s.downloadRate().toLong(),
            uploadRate = s.uploadRate().toLong(),
            totalDone = totalDone,
            totalWanted = totalWanted,
            numPeers = s.numPeers(),
            numSeeds = s.numSeeds(),
            isSequential = isSeq,
            savePath = savePath,
            files = fileList
        )
    }

    fun getAllTorrents(): List<TorrentStatusInfo> {
        if (!sessionManager.isRunning) return emptyList()
        val result = mutableListOf<TorrentStatusInfo>()
        for (hash in torrentMeta.keys) {
            val status = getTorrentStatus(hash)
            if (status != null) {
                result.add(status)
            }
        }
        return result
    }

    fun isVideoFile(name: String): Boolean {
        val lower = name.lowercase()
        return lower.endsWith(".mp4") || lower.endsWith(".mkv") || lower.endsWith(".avi") ||
               lower.endsWith(".webm") || lower.endsWith(".mov") || lower.endsWith(".flv") ||
               lower.endsWith(".ts") || lower.endsWith(".m4v")
    }

    fun extractHashFromMagnet(magnet: String): String? {
        val match = Regex("""xt=urn:btih:([a-zA-Z0-9]+)""", RegexOption.IGNORE_CASE).find(magnet)
        val raw = match?.groupValues?.get(1) ?: return null
        return if (raw.length == 32) {
            try {
                Sha1Hash.parseHex(raw).toHex().lowercase()
            } catch (_: Exception) {
                raw.lowercase()
            }
        } else {
            raw.lowercase()
        }
    }

    fun enrichMagnetTrackers(magnet: String): String {
        var result = magnet
        for (tr in PUBLIC_TRACKERS) {
            val enc = Uri.encode(tr)
            if (!result.contains(enc) && !result.contains(tr)) {
                result += "&tr=$enc"
            }
        }
        return result
    }

    private fun startPeriodicSync() {
        syncJob?.cancel()
        syncJob = scope.launch {
            while (isActive) {
                delay(2000) // ❄️ 2s delay cuts background CPU wakeups by 50%
                try {
                    if (sessionManager.isRunning) {
                        val list = getAllTorrents()
                        val hasActive = list.any { it.state == "downloading" || it.state == "downloading_metadata" }
                        if (!hasActive && list.isNotEmpty()) {
                            // If no active downloads, pause C++ DHT/network threads to keep device cool
                            try { sessionManager.swig().pause() } catch (_: Exception) {}
                        } else if (hasActive) {
                            try { sessionManager.swig().resume() } catch (_: Exception) {}
                        }
                    }
                    syncWithDatabaseAndNotifications()
                } catch (_: Throwable) {}
            }
        }
    }

    private suspend fun syncWithDatabaseAndNotifications() {
        val list = getAllTorrents()
        if (list.isEmpty()) return

        val appCtx = App.instance
        val dao = appCtx.database.downloadDao()

        for (item in list) {
            val isStreaming = torrentMeta[item.infoHash]?.get("isStreaming") == "true"
            val existing = dao.getById(item.infoHash)

            // 🛑 CRITICAL FIX: If this torrent is being streamed in the cinema player (isStreaming == true),
            // OR if this torrent was never registered by the user as a real download (existing == null):
            // DO NOT auto-force download of all pieces, DO NOT post download notifications,
            // and DO NOT insert a DownloadEntity into the downloads database!
            if (isStreaming || existing == null) {
                continue
            }

            val th = getTorrentHandle(item.infoHash)
            val isReallyFinished = item.totalWanted > 0L && item.totalDone >= (item.totalWanted - 64 * 1024L)

            if (th != null && th.swig().is_valid) {
                // Auto-recovery watchdog
                val s = th.status()
                val isPaused = try { th.swig().flags().and_(TorrentFlags.PAUSED).non_zero() } catch (_: Throwable) { false }
                val hasError = s.swig().errc.value() != 0
                if (hasError) {
                    Log.w(TAG, "Torrent ${item.infoHash} error: ${s.swig().errc.message()} - auto-clearing")
                    try { th.swig().clear_error() } catch (_: Throwable) {}
                    try { th.resume() } catch (_: Throwable) {}
                }
                val isSupposedToDownload = !isReallyFinished && item.state != "paused"
                if (isPaused && isSupposedToDownload) {
                    try { th.swig().unset_flags(TorrentFlags.PAUSED) } catch (_: Throwable) {}
                    try { th.resume() } catch (_: Throwable) {}
                }
                if (!isReallyFinished && (s.swig().state.swigValue() == 3 || s.swig().state.swigValue() == 4)) {
                    Log.w(TAG, "Torrent ${item.infoHash} stopped prematurely (${item.totalDone}/${item.totalWanted} bytes). Auto-resuming all pieces!")
                    try {
                        val numPieces = th.torrentFile()?.numPieces() ?: 0
                        for (i in 0 until numPieces) {
                            th.piecePriority(i, Priority.DEFAULT)
                        }
                        th.swig().unset_flags(TorrentFlags.PAUSED)
                        th.swig().set_flags(TorrentFlags.AUTO_MANAGED)
                        th.resume()
                    } catch (e: Throwable) {
                        Log.e(TAG, "Failed to force unpause truncated torrent", e)
                    }
                }
            }

            val notifId = item.infoHash.hashCode()
            val progressInt = if (isReallyFinished) 100 else (item.progress * 100).toInt().coerceIn(0, 99)
            val speedMBps = item.downloadRate / (1024.0 * 1024.0)

            if (item.state == "downloading" || item.state == "downloading_metadata" || (!isReallyFinished && (item.state == "completed" || item.state == "seeding"))) {
                DownloadNotificationHelper.updateProgress(
                    appCtx,
                    notifId,
                    item.name,
                    progressInt,
                    speedMBps,
                    item.totalDone,
                    item.totalWanted
                )
            } else if (isReallyFinished) {
                DownloadNotificationHelper.cancel(appCtx, notifId)
            }

            // Sync with Room DB DownloadEntity
            val statusDb = when (item.state) {
                "completed", "seeding" -> if (isReallyFinished) "completed" else "downloading"
                "downloading", "downloading_metadata", "checking", "allocating" -> "downloading"
                "checking_resume" -> "pending"
                "paused" -> "paused"
                else -> item.state
            }

            val mainVideoPath = item.files.firstOrNull { it.isVideo }?.let {
                File(item.savePath, it.path).absolutePath
            } ?: File(item.savePath, item.name).absolutePath

            val entity = DownloadEntity(
                id = item.infoHash,
                title = item.name,
                url = torrentMeta[item.infoHash]?.get("magnet") ?: "magnet:?xt=urn:btih:${item.infoHash}",
                filename = item.name,
                downloadManagerId = -2L, // -2L denotes Native LibTorrent Engine
                status = statusDb,
                category = torrentMeta[item.infoHash]?.get("category") ?: "torrents",
                downloadedBytes = item.totalDone,
                totalBytes = if (item.totalWanted > 0) item.totalWanted else item.totalDone,
                progress = progressInt,
                speed = if (speedMBps > 0.05) "${String.format(Locale.US, "%.1f", speedMBps)} MB/s" else "${item.numPeers} peers",
                localFilePath = mainVideoPath,
                createdTimestamp = existing?.createdTimestamp ?: System.currentTimeMillis()
            )
            dao.insertOrUpdate(entity)
        }
    }
}
