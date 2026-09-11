package com.clouddrive.leech.plugins

import android.app.DownloadManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.media.MediaScannerConnection
import android.media.ThumbnailUtils
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import com.clouddrive.leech.App
import com.clouddrive.leech.database.entities.DownloadEntity
import com.clouddrive.leech.player.PlayerActivity
import com.clouddrive.leech.service.DownloadForegroundService
import com.clouddrive.leech.vpn.net.UniversalAntiCensorDns
import com.getcapacitor.JSArray
import com.getcapacitor.JSObject
import com.getcapacitor.Plugin
import com.getcapacitor.PluginCall
import com.getcapacitor.PluginMethod
import com.getcapacitor.annotation.CapacitorPlugin
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

object DownloadNotificationHelper {
    private const val OLD_CHANNEL_ID = "cloud_drive_downloads"

    fun init(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                nm?.deleteNotificationChannel(OLD_CHANNEL_ID)
            } catch (_: Exception) {}
        }
    }

    fun updateProgress(
        context: Context,
        notifId: Int,
        title: String,
        progress: Int,
        speedMBps: Double,
        downloadedBytes: Long,
        totalBytes: Long
    ) {
        try {
            // Cancel any old separate notification id so it never shows twice
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            nm?.cancel(notifId)

            val speedStr = if (speedMBps > 0.05) "${String.format(java.util.Locale.US, "%.1f", speedMBps)} MB/s" else "Downloading..."
            val downloadedMB = downloadedBytes / (1024.0 * 1024.0)
            val totalMB = totalBytes / (1024.0 * 1024.0)
            val sizeStr = if (totalBytes > 0) "${String.format(java.util.Locale.US, "%.1f", downloadedMB)} / ${String.format(java.util.Locale.US, "%.1f", totalMB)} MB" else "${String.format(java.util.Locale.US, "%.1f", downloadedMB)} MB"

            // Unify into single rich Foreground Service notification
            DownloadForegroundService.startOrUpdate(
                context = context,
                title = title,
                statusText = "$progress% • $speedStr • $sizeStr",
                progress = progress,
                indeterminate = totalBytes <= 0,
                activeCount = 1
            )
        } catch (_: Exception) {}
    }

    fun cancel(context: Context, notifId: Int) {
        try {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            nm?.cancel(notifId)
        } catch (_: Exception) {}
    }

    fun showCompleted(context: Context, notifId: Int, title: String, filePath: String) {
        try {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            val completedChannelId = "cloud_downloads_completed"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = android.app.NotificationChannel(
                    completedChannelId,
                    "Completed Downloads",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Notifications for completed downloads"
                }
                nm.createNotificationChannel(channel)
            }

            val launchIntent = Intent(context, com.clouddrive.leech.MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = android.app.PendingIntent.getActivity(
                context,
                notifId,
                launchIntent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            )

            val notif = androidx.core.app.NotificationCompat.Builder(context, completedChannelId)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle("Download Complete")
                .setContentText(title)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setPriority(androidx.core.app.NotificationCompat.PRIORITY_DEFAULT)
                .build()

            nm.notify(notifId, notif)
        } catch (_: Exception) {}
    }
}

@CapacitorPlugin(name = "NativeDownload")
class NativeDownloadPlugin : Plugin() {

    private val scope get() = App.instance.downloadScope
    private val activeDownloadCount = java.util.concurrent.atomic.AtomicInteger(0)
    private val gson = Gson()
    private val speedTracker = ConcurrentHashMap<String, SpeedSample>()
    private val activeHlsJobs = ConcurrentHashMap<String, Job>()
    private val activeCalls = ConcurrentHashMap<String, Call>()
    private val pausedDownloadIds = ConcurrentHashMap.newKeySet<String>()
    private val cancelledDownloadIds = ConcurrentHashMap.newKeySet<String>()

    private fun checkAndUpdateForegroundService(ctx: Context) {
        scope.launch(Dispatchers.IO) {
            try {
                val active = App.instance.database.downloadDao().getActiveDownloads()
                if (active.isEmpty()) {
                    DownloadForegroundService.stop(ctx)
                }
            } catch (_: Exception) {}
        }
    }

    fun emitDownloadProgress(
        id: String,
        filename: String,
        status: String,
        progress: Int,
        speed: String,
        downloaded: Long,
        total: Long,
        localPath: String = ""
    ) {
        try {
            val ev = JSObject().apply {
                put("id", id)
                put("filename", filename)
                put("title", filename)
                put("status", status)
                put("progress", progress)
                put("speed", speed)
                put("downloadedBytes", downloaded)
                put("totalBytes", total)
                put("localFilePath", localPath)
            }
            notifyListeners("downloadProgress", ev)
        } catch (_: Exception) {}
    }

    fun emitDownloadStateChange(id: String, status: String, extra: JSObject? = null) {
        try {
            val ev = extra ?: JSObject()
            ev.put("id", id)
            ev.put("status", status)
            notifyListeners("downloadStateChange", ev)
        } catch (_: Exception) {}
    }

    private data class SpeedSample(
        var lastBytes: Long = 0L,
        var lastTime: Long = System.currentTimeMillis(),
        var speedMBps: Double = 0.0
    )

    private val httpClient = OkHttpClient.Builder()
        .dns(UniversalAntiCensorDns.instance)
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private fun extractFilenameFromContentDisposition(cd: String?): String? {
        if (cd.isNullOrBlank()) return null
        val utf8Match = Regex("""filename\*=UTF-8''([^;\r\n]+)""", RegexOption.IGNORE_CASE).find(cd)
        if (utf8Match != null) {
            try {
                val decoded = java.net.URLDecoder.decode(utf8Match.groupValues[1].trim('"', '\''), "UTF-8")
                if (decoded.isNotBlank()) return decoded.replace(Regex("[/\\\\?%*:|\"<>]"), "_").trim()
            } catch (_: Exception) {}
        }
        val fnMatch = Regex("""filename=["']?([^"';\r\n]+)["']?""", RegexOption.IGNORE_CASE).find(cd)
        if (fnMatch != null) {
            val raw = fnMatch.groupValues[1].trim()
            if (raw.isNotBlank()) return raw.replace(Regex("[/\\\\?%*:|\"<>]"), "_").trim()
        }
        return null
    }

    private fun getExtensionFromMimeType(contentType: String?): String? {
        if (contentType.isNullOrBlank()) return null
        val mime = contentType.substringBefore(';').trim().lowercase()
        return when (mime) {
            "application/vnd.android.package-archive" -> ".apk"
            "application/zip", "application/x-zip-compressed" -> ".zip"
            "application/x-rar-compressed", "application/vnd.rar", "application/x-rar" -> ".rar"
            "application/x-7z-compressed" -> ".7z"
            "application/x-tar" -> ".tar"
            "application/gzip", "application/x-gzip" -> ".gz"
            "application/pdf" -> ".pdf"
            "application/x-iso9660-image" -> ".iso"
            "audio/mpeg", "audio/mp3" -> ".mp3"
            "audio/mp4", "audio/m4a", "audio/x-m4a", "audio/aac" -> ".m4a"
            "audio/flac", "audio/x-flac" -> ".flac"
            "audio/wav", "audio/x-wav" -> ".wav"
            "audio/ogg", "application/ogg" -> ".ogg"
            "video/mp4" -> ".mp4"
            "video/x-matroska" -> ".mkv"
            "video/webm" -> ".webm"
            "video/quicktime" -> ".mov"
            "video/x-msvideo" -> ".avi"
            "image/jpeg" -> ".jpg"
            "image/png" -> ".png"
            "image/gif" -> ".gif"
            "image/webp" -> ".webp"
            "application/msword" -> ".doc"
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> ".docx"
            "application/vnd.ms-excel" -> ".xls"
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" -> ".xlsx"
            "application/epub+zip" -> ".epub"
            "text/plain" -> ".txt"
            else -> null
        }
    }

    fun isAlreadyDirectUrl(url: String): Boolean {
        val u = url.lowercase()
        if (u.startsWith("magnet:") || u.contains("/torrent/download/") || u.endsWith(".torrent")) return true
        if (u.contains("pixeldrain.com/api/file/") || u.contains("drive.usercontent.google.com/download") ||
            u.contains("cloudflarestorage.com") || u.contains("r2.cloudflarestorage") || u.contains("shegu.st")
        ) return true
        if (u.contains("workers.dev") || u.contains("ddl.sinhalasub.net") || u.contains("dlserver")) return true
        val path = try { Uri.parse(url).path ?: "" } catch (_: Exception) { "" }
        val extMatch = Regex("""\.(mp4|mkv|avi|webm|mov|flv|ts|m4v|3gp|mp3|m4a|aac|flac|wav|ogg|opus|zip|rar|7z|tar|gz|bz2|xz|iso|apk|xapk|pdf|epub|mobi|doc|docx|xls|xlsx|ppt|pptx|txt|jpg|jpeg|png|webp|bin)$""", RegexOption.IGNORE_CASE)
        if (extMatch.containsMatchIn(path) || extMatch.containsMatchIn(u.substringBefore('?'))) return true
        return false
    }

    private fun determineCategoryFromFilenameOrUrl(filename: String, url: String, fallbackCategory: String): String {
        val fLower = filename.lowercase()
        val uLower = url.lowercase()
        if (fallbackCategory.isNotEmpty() && fallbackCategory != "media" && fallbackCategory != "general") {
            return fallbackCategory
        }
        return when {
            fLower.endsWith(".apk") || fLower.endsWith(".xapk") -> "apps"
            fLower.endsWith(".zip") || fLower.endsWith(".rar") || fLower.endsWith(".7z") ||
            fLower.endsWith(".tar") || fLower.endsWith(".gz") || fLower.endsWith(".bz2") ||
            fLower.endsWith(".xz") || fLower.endsWith(".iso") -> "files"
            fLower.endsWith(".pdf") || fLower.endsWith(".epub") || fLower.endsWith(".mobi") ||
            fLower.endsWith(".doc") || fLower.endsWith(".docx") || fLower.endsWith(".xls") ||
            fLower.endsWith(".xlsx") || fLower.endsWith(".ppt") || fLower.endsWith(".pptx") ||
            fLower.endsWith(".txt") -> "documents"
            fLower.endsWith(".mp3") || fLower.endsWith(".m4a") || fLower.endsWith(".aac") ||
            fLower.endsWith(".flac") || fLower.endsWith(".wav") || fLower.endsWith(".ogg") ||
            fLower.endsWith(".opus") -> "music"
            uLower.contains("porn") || uLower.contains("hamster") || uLower.contains("eporner") ||
            uLower.contains("xvideos") || uLower.contains("xnxx") || uLower.contains("redtube") ||
            uLower.contains("xhcdn") || uLower.contains("phncdn") || fLower.contains("porn") ||
            fLower.contains("hamster") || fLower.contains("eporner") || fLower.contains("18+") ||
            fLower.contains("cuckold") || fLower.contains("jav") || fLower.contains("waka") -> "adult"
            uLower.contains("sinhala") || uLower.contains("baiscope") || uLower.contains("piratelk") ||
            uLower.contains("sub") || uLower.contains("movie") || fLower.contains("sinhala") ||
            fLower.contains("baiscope") || fLower.contains("sub") || fLower.contains("movie") ||
            fLower.endsWith(".mp4") || fLower.endsWith(".mkv") || fLower.endsWith(".webm") ||
            fLower.endsWith(".avi") || fLower.endsWith(".mov") -> "movies"
            else -> "files"
        }
    }

    companion object {
        private const val TAG = "NativeDownloadPlugin"

        @Volatile
        var instance: NativeDownloadPlugin? = null

        fun getSubFolderName(cat: String): String {
            return when (cat.lowercase()) {
                "movies" -> "CloudDrive Leech/Movies"
                "adult" -> "CloudDrive Leech/Adult"
                "apps" -> "CloudDrive Leech/Apps"
                "music" -> "CloudDrive Leech/Music"
                "documents" -> "CloudDrive Leech/Documents"
                "torrents" -> "CloudDrive Leech/Torrents"
                "browser" -> "CloudDrive Leech/Browser"
                else -> "CloudDrive Leech/Files"
            }
        }

        fun getSafeDownloadDir(cat: String): File {
            val subFolder = getSubFolderName(cat)
            val app = App.instance
            val relFolder = subFolder.substringAfter("CloudDrive Leech/")
            // Dedicated Scoped Storage folder created and owned by this APK (100% accessible without permission blocks)
            val appExtDir = File(app.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), relFolder)
            if (!appExtDir.exists()) appExtDir.mkdirs()
            return appExtDir
        }

        fun enqueueDownload(
            context: Context,
            url: String,
            title: String? = null,
            filename: String? = null,
            category: String = "browser",
            referer: String? = null,
            cookie: String? = null
        ): String {
            val plugin = instance
            return if (plugin != null) {
                val res = plugin.enqueueDownloadInternal(url, filename ?: title ?: "", category, referer, cookie)
                res.getString("id") ?: UUID.randomUUID().toString()
            } else {
                startStandaloneDownload(context, url, filename ?: title ?: "", category, referer, cookie)
            }
        }

        fun startStandaloneDownload(
            context: Context,
            url: String,
            rawFilename: String,
            category: String = "browser",
            customReferer: String? = null,
            customCookie: String? = null
        ): String {
            val downloadId = UUID.randomUUID().toString()
            var fname = if (rawFilename.isBlank()) "download_video.mp4" else rawFilename.replace(Regex("[/\\\\?%*:|\"<>]"), "_").trim()
            if (!fname.contains('.')) {
                fname += if (url.contains(".m3u8")) ".mp4" else if (url.contains(".webm")) ".webm" else ".mp4"
            }
            val initialEntity = DownloadEntity(
                id = downloadId,
                title = fname,
                url = url,
                filename = fname,
                localFilePath = "",
                downloadManagerId = -1L,
                status = "downloading",
                category = category,
                speed = "Connecting...",
                progress = 0,
                createdTimestamp = System.currentTimeMillis()
            )
            val p = instance ?: NativeDownloadPlugin()
            App.instance.downloadScope.launch(Dispatchers.IO) {
                App.instance.database.downloadDao().insertOrUpdate(initialEntity)
                val directUrl = if (p.isAlreadyDirectUrl(url)) url else p.resolveDirectDownloadUrl(url)
                if (directUrl.contains(".m3u8")) {
                    p.startHlsDownload(downloadId, directUrl, fname, category, customReferer, customCookie)
                } else {
                    p.startDirectStreamDownload(downloadId, directUrl, fname, category, customReferer, customCookie)
                }
            }
            return downloadId
        }

        fun resumeInterruptedDownloads() {
            App.instance.downloadScope.launch(Dispatchers.IO) {
                try {
                    val active = App.instance.database.downloadDao().getActiveDownloads()
                    if (active.isNotEmpty()) {
                        android.util.Log.i(TAG, "Found ${active.size} interrupted background downloads, resuming...")
                        val p = instance ?: NativeDownloadPlugin()
                        for (item in active) {
                            p.resumeDownloadItem(item)
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.w(TAG, "Failed to auto-resume interrupted downloads: ${e.message}")
                }
            }
        }
    }

    override fun load() {
        super.load()
        instance = this
    }

    @PluginMethod
    fun startDownload(call: PluginCall) {
        val initialUrl = call.getString("url") ?: ""
        val rawFilename = call.getString("filename") ?: call.getString("title") ?: ""
        val category = call.getString("category") ?: "media"
        val referer = call.getString("referer")
        val cookie = call.getString("cookie")

        if (initialUrl.isEmpty()) {
            call.reject("URL is required")
            return
        }

        val ret = enqueueDownloadInternal(initialUrl, rawFilename, category, referer, cookie)
        call.resolve(ret)
    }

    fun enqueueDownloadInternal(
        initialUrl: String,
        rawFilenameInput: String,
        categoryInput: String,
        customReferer: String? = null,
        customCookie: String? = null
    ): JSObject {
        var rawFilename = rawFilenameInput
        var category = categoryInput

        // Try to infer filename and extension from URL if generic or empty
        if (rawFilename.isEmpty() || rawFilename == "video_download" || rawFilename == "video_download.mp4" || rawFilename == "Media_Download.mp4") {
            try {
                val uri = Uri.parse(initialUrl)
                val lastSeg = uri.lastPathSegment ?: ""
                if (lastSeg.contains(".")) {
                    rawFilename = lastSeg
                }
            } catch (_: Exception) {}
        }
        if (rawFilename.isEmpty()) {
            rawFilename = "download_file"
        }

        var filename = rawFilename.replace(Regex("[/\\\\?%*:|\"<>]"), "_").trim()
        val dotIdx = filename.lastIndexOf('.')
        val baseName = if (dotIdx != -1) filename.substring(0, dotIdx) else filename
        val extension = if (dotIdx != -1) filename.substring(dotIdx) else ""

        // Safe base name length
        val safeBase = if (baseName.length > 60) baseName.take(60).trim() else baseName
        val diskFilename = if (extension.isNotEmpty()) "$safeBase$extension" else "$safeBase.mp4"

        // Auto-detect Category
        category = determineCategoryFromFilenameOrUrl(filename, initialUrl, category)

        val downloadId = UUID.randomUUID().toString()
        val isDirect = isAlreadyDirectUrl(initialUrl)

        // ⚡ 1. INSTANT ZERO-LATENCY REGISTRATION & IMMEDIATE CLIENT RESOLVE (0ms UI RESPONSE!)
        val initialEntity = DownloadEntity(
            id = downloadId,
            title = filename,
            url = initialUrl,
            filename = diskFilename,
            localFilePath = "",
            downloadManagerId = -1L,
            status = if (isDirect) "downloading" else "resolving",
            category = category,
            speed = if (isDirect) "Connecting..." else "Resolving link...",
            progress = 0,
            createdTimestamp = System.currentTimeMillis()
        )
        scope.launch(Dispatchers.IO) {
            App.instance.database.downloadDao().insertOrUpdate(initialEntity)
        }

        val ret = JSObject().apply {
            put("success", true)
            put("id", downloadId)
            put("dmId", -1L)
            put("filename", filename)
            put("url", initialUrl)
            put("status", if (isDirect) "downloading" else "resolving")
        }

        // ⚡ 2. BACKGROUND ASYNC STREAM RESOLUTION & TURBO DOWNLOAD PIPELINE
        scope.launch(Dispatchers.IO) {
            try {
                val directUrl = if (isDirect) initialUrl else resolveDirectDownloadUrl(initialUrl)

                // Case 0: Magnet Link or .torrent File -> In-App High-Speed Native BitTorrent Swarm Engine
                val isTorrent = directUrl.startsWith("magnet:") || directUrl.contains("/torrent/download/") || directUrl.endsWith(".torrent")
                if (isTorrent) {
                    val hash = com.clouddrive.leech.torrent.TorrentEngineManager.addTorrent(
                        uriOrMagnet = directUrl,
                        customTitle = filename,
                        category = category,
                        sequential = false
                    )

                    val assignedId = hash ?: downloadId
                    val entity = DownloadEntity(
                        id = assignedId,
                        title = filename,
                        url = directUrl,
                        filename = if (filename.endsWith(".torrent") || filename.endsWith(".magnet")) filename else "$filename.torrent",
                        localFilePath = File(com.clouddrive.leech.torrent.TorrentEngineManager.defaultSaveDir, filename).absolutePath,
                        downloadManagerId = -2L,
                        totalBytes = 0L,
                        downloadedBytes = 0L,
                        status = "downloading",
                        category = "torrents",
                        speed = "Connecting...",
                        progress = 0,
                        createdTimestamp = System.currentTimeMillis()
                    )
                    withContext(Dispatchers.IO) {
                        App.instance.database.downloadDao().insertOrUpdate(entity)
                    }
                    return@launch
                }

                // Case 1: HLS Stream (.m3u8 from Pornhub, xHamster, Redtube, etc.) -> Dedicated Stream Downloader
                if (directUrl.contains(".m3u8")) {
                    startHlsDownload(downloadId, directUrl, diskFilename, category, customReferer, customCookie)
                    return@launch
                }

                // Case 2: In-App Turbo Direct Stream Downloader (Instant start, 0ms queue overhead!)
                startDirectStreamDownload(downloadId, directUrl, diskFilename, category, customReferer, customCookie)

            } catch (e: Exception) {
                android.util.Log.e("NativeDownloadPlugin", "Download error: ${e.message}", e)
                App.instance.database.downloadDao().insertOrUpdate(
                    initialEntity.copy(
                        status = "failed",
                        errorMessage = e.message ?: "Failed to resolve stream link"
                    )
                )
            }
        }

        return ret
    }

    fun startHlsDownload(
        downloadId: String,
        m3u8Url: String,
        filename: String,
        category: String,
        customReferer: String? = null,
        customCookie: String? = null
    ) {
        pausedDownloadIds.remove(downloadId)
        cancelledDownloadIds.remove(downloadId)
        activeDownloadCount.incrementAndGet()
        val job = scope.launch(Dispatchers.IO) {
            val parentDir = getSafeDownloadDir(category)
            if (!parentDir.exists()) parentDir.mkdirs()
            if (category.equals("adult", ignoreCase = true)) {
                try {
                    val noMedia = File(parentDir, ".nomedia")
                    if (!noMedia.exists()) noMedia.createNewFile()
                } catch (_: Exception) {}
            }
            val destFile = File(parentDir, filename)
            try {
                if (destFile.exists()) destFile.delete()
                destFile.createNewFile()

                App.instance.database.downloadDao().insertOrUpdate(
                    DownloadEntity(
                        id = downloadId,
                        title = filename,
                        url = m3u8Url,
                        filename = filename,
                        downloadManagerId = -1L,
                        status = "downloading",
                        category = category,
                        localFilePath = destFile.absolutePath,
                        createdTimestamp = System.currentTimeMillis()
                    )
                )

                // 1. Fetch Master M3U8 with dynamic CDN Referer & Origin
                var referer = "https://xhamster.com/"
                var origin = "https://xhamster.com"
                var cookie = "age_verified=1; has_access=1"
                if (!customReferer.isNullOrEmpty()) {
                    referer = customReferer
                    try {
                        val uri = Uri.parse(customReferer)
                        origin = "${uri.scheme}://${uri.host}"
                    } catch (_: Exception) {}
                }
                if (!customCookie.isNullOrEmpty()) {
                    cookie = customCookie
                } else if (m3u8Url.contains("pornhub") || m3u8Url.contains("phncdn")) {
                    referer = "https://www.pornhub.com/"
                    origin = "https://www.pornhub.com"
                    cookie = "age_verified=1; accessAgeDisclaimerPH=1; has_access=1"
                } else if (m3u8Url.contains("xhamster") || m3u8Url.contains("xhcdn")) {
                    referer = "https://xhamster.com/"
                    origin = "https://xhamster.com"
                    cookie = "age_verified=1; has_access=1"
                } else if (m3u8Url.contains("redtube") || m3u8Url.contains("rdtcdn") || m3u8Url.contains("rncdn")) {
                    referer = "https://www.redtube.com/"
                    origin = "https://www.redtube.com"
                    cookie = "age_verified=1; has_access=1; il=v111"
                } else if (m3u8Url.contains("eporner")) {
                    referer = "https://www.eporner.com/"
                    origin = "https://www.eporner.com"
                    cookie = "age_verified=1; has_access=1"
                }

                val masterReq = Request.Builder()
                    .url(m3u8Url)
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36")
                    .header("Referer", referer)
                    .header("Origin", origin)
                    .header("Cookie", cookie)
                    .build()

                val masterCall = httpClient.newCall(masterReq)
                activeCalls[downloadId] = masterCall
                val masterText = try {
                    masterCall.execute().use { it.body?.string() ?: "" }
                } finally {
                    activeCalls.remove(downloadId)
                }

                if (pausedDownloadIds.contains(downloadId) || cancelledDownloadIds.contains(downloadId)) {
                    return@launch
                }

                var targetPlaylistUrl = m3u8Url
                var playlistContent = masterText
                val lines = masterText.lines().map { it.trim() }.filter { it.isNotEmpty() }

                val childM3u8 = lines.lastOrNull { (it.endsWith(".m3u8") || it.contains(".m3u8?")) && !it.startsWith("#") }
                    ?: lines.firstOrNull { (it.endsWith(".m3u8") || it.contains(".m3u8?")) && !it.startsWith("#") }
                if (childM3u8 != null) {
                    targetPlaylistUrl = try {
                        java.net.URI(m3u8Url).resolve(childM3u8).toString()
                    } catch (_: Exception) {
                        val base = m3u8Url.substring(0, m3u8Url.lastIndexOf('/') + 1)
                        base + childM3u8
                    }

                    val childReq = Request.Builder()
                        .url(targetPlaylistUrl)
                        .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36")
                        .header("Referer", referer)
                        .header("Origin", origin)
                        .header("Cookie", cookie)
                        .build()

                    val childCall = httpClient.newCall(childReq)
                    activeCalls[downloadId] = childCall
                    playlistContent = try {
                        childCall.execute().use { it.body?.string() ?: "" }
                    } finally {
                        activeCalls.remove(downloadId)
                    }
                }

                if (pausedDownloadIds.contains(downloadId) || cancelledDownloadIds.contains(downloadId)) {
                    return@launch
                }

                // Parse Segment URLs & fMP4 Init map
                val segmentLines = playlistContent.lines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }

                val segmentUrls = mutableListOf<String>()

                // Check for fragmented MP4 init segment: #EXT-X-MAP:URI="init.mp4"
                val mapMatch = Regex("""#EXT-X-MAP:URI="([^"]+)"""").find(playlistContent)
                if (mapMatch != null) {
                    val initRel = mapMatch.groupValues[1]
                    val initFull = try {
                        java.net.URI(targetPlaylistUrl).resolve(initRel).toString()
                    } catch (_: Exception) {
                        val base = targetPlaylistUrl.substring(0, targetPlaylistUrl.lastIndexOf('/') + 1)
                        base + initRel
                    }
                    segmentUrls.add(initFull)
                }

                for (seg in segmentLines) {
                    val fullSeg = try {
                        java.net.URI(targetPlaylistUrl).resolve(seg).toString()
                    } catch (_: Exception) {
                        val base = targetPlaylistUrl.substring(0, targetPlaylistUrl.lastIndexOf('/') + 1)
                        base + seg
                    }
                    segmentUrls.add(fullSeg)
                }

                val totalSegments = segmentUrls.size
                if (totalSegments == 0) throw Exception("No video segments found in HLS stream")

                var downloadedBytes = 0L
                val outStream = FileOutputStream(destFile, true)

                try {
                    for (i in segmentUrls.indices) {
                        if (pausedDownloadIds.contains(downloadId) || cancelledDownloadIds.contains(downloadId)) {
                            break
                        }
                        val segUrl = segmentUrls[i]
                        val segReq = Request.Builder()
                            .url(segUrl)
                            .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36")
                            .header("Referer", referer)
                            .header("Origin", origin)
                            .header("Cookie", cookie)
                            .build()

                        val segCall = httpClient.newCall(segReq)
                        activeCalls[downloadId] = segCall
                        val segBytes = try {
                            segCall.execute().use { resp ->
                                resp.body?.bytes() ?: ByteArray(0)
                            }
                        } catch (e: Exception) {
                            if (pausedDownloadIds.contains(downloadId) || cancelledDownloadIds.contains(downloadId)) {
                                break
                            }
                            ByteArray(0)
                        } finally {
                            activeCalls.remove(downloadId)
                        }

                        if (pausedDownloadIds.contains(downloadId) || cancelledDownloadIds.contains(downloadId)) {
                            break
                        }

                        if (segBytes.isNotEmpty()) {
                            outStream.write(segBytes)
                            downloadedBytes += segBytes.size
                            val progressPercent = (((i + 1) * 100) / totalSegments).coerceIn(0, 99)
                            val estimatedTotal = if (i > 0) (downloadedBytes / (i + 1)) * totalSegments else downloadedBytes * totalSegments

                            if (i % 3 == 0 || i == totalSegments - 1) {
                                if (pausedDownloadIds.contains(downloadId) || cancelledDownloadIds.contains(downloadId)) {
                                    break
                                }
                                val tracker = speedTracker.getOrPut(downloadId) { SpeedSample(downloadedBytes, System.currentTimeMillis(), 0.0) }
                                val now = System.currentTimeMillis()
                                val timeDiff = (now - tracker.lastTime) / 1000.0
                                if (timeDiff >= 0.5) {
                                    tracker.speedMBps = ((downloadedBytes - tracker.lastBytes) / timeDiff) / (1024.0 * 1024.0)
                                    tracker.lastBytes = downloadedBytes
                                    tracker.lastTime = now
                                }

                                DownloadNotificationHelper.updateProgress(
                                    App.instance,
                                    downloadId.hashCode(),
                                    filename,
                                    progressPercent,
                                    tracker.speedMBps,
                                    downloadedBytes,
                                    estimatedTotal
                                )

                                val curSpdStr = if (tracker.speedMBps > 0.05) "${String.format(java.util.Locale.US, "%.1f", tracker.speedMBps)} MB/s" else "Downloading..."

                                App.instance.database.downloadDao().insertOrUpdate(
                                    DownloadEntity(
                                        id = downloadId,
                                        title = filename,
                                        url = m3u8Url,
                                        filename = filename,
                                        downloadManagerId = -1L,
                                        status = "downloading",
                                        category = category,
                                        downloadedBytes = downloadedBytes,
                                        totalBytes = estimatedTotal,
                                        progress = progressPercent,
                                        speed = curSpdStr,
                                        localFilePath = destFile.absolutePath
                                    )
                                )

                                emitDownloadProgress(
                                    id = downloadId,
                                    filename = filename,
                                    status = "downloading",
                                    progress = progressPercent,
                                    speed = curSpdStr,
                                    downloaded = downloadedBytes,
                                    total = estimatedTotal,
                                    localPath = destFile.absolutePath
                                )
                            }
                        }
                    }
                } finally {
                    try { outStream.flush(); outStream.close() } catch (_: Exception) {}
                }

                if (pausedDownloadIds.contains(downloadId) || cancelledDownloadIds.contains(downloadId)) {
                    return@launch
                }

                // 3. Mark Completed & Scan Media in Android
                DownloadNotificationHelper.cancel(App.instance, downloadId.hashCode())
                DownloadNotificationHelper.showCompleted(App.instance, downloadId.hashCode(), filename, destFile.absolutePath)

                App.instance.database.downloadDao().insertOrUpdate(
                    DownloadEntity(
                        id = downloadId,
                        title = filename,
                        url = m3u8Url,
                        filename = filename,
                        downloadManagerId = -1L,
                        status = "completed",
                        category = category,
                        downloadedBytes = downloadedBytes,
                        totalBytes = downloadedBytes,
                        progress = 100,
                        localFilePath = destFile.absolutePath
                    )
                )

                emitDownloadProgress(
                    id = downloadId,
                    filename = filename,
                    status = "completed",
                    progress = 100,
                    speed = "Completed",
                    downloaded = downloadedBytes,
                    total = downloadedBytes,
                    localPath = destFile.absolutePath
                )
                emitDownloadStateChange(downloadId, "completed", JSObject().apply {
                    put("localFilePath", destFile.absolutePath)
                    put("filename", filename)
                })

                val ctx = context ?: App.instance
                val ext = destFile.extension.lowercase()
                val scanMime = if (ext == "mp4") "video/mp4" else "*/*"
                MediaScannerConnection.scanFile(ctx, arrayOf(destFile.absolutePath), arrayOf(scanMime), null)

            } catch (err: Exception) {
                if (pausedDownloadIds.contains(downloadId) || cancelledDownloadIds.contains(downloadId)) {
                    return@launch
                }
                DownloadNotificationHelper.cancel(App.instance, downloadId.hashCode())
                App.instance.database.downloadDao().insertOrUpdate(
                    DownloadEntity(
                        id = downloadId,
                        title = filename,
                        url = m3u8Url,
                        filename = filename,
                        downloadManagerId = -1L,
                        status = "failed",
                        category = category,
                        errorMessage = err.message ?: "HLS download error",
                        localFilePath = destFile.absolutePath
                    )
                )
                emitDownloadStateChange(downloadId, "failed", JSObject().apply {
                    put("errorMessage", err.message ?: "HLS download error")
                })
            } finally {
                activeCalls.remove(downloadId)
                activeHlsJobs.remove(downloadId)
                if (activeDownloadCount.decrementAndGet() <= 0) {
                    DownloadForegroundService.stop(App.instance)
                }
            }
        }
        activeHlsJobs[downloadId] = job
    }

    fun startDirectStreamDownload(
        downloadId: String,
        directUrl: String,
        filename: String,
        category: String,
        customReferer: String? = null,
        customCookie: String? = null
    ) {
        pausedDownloadIds.remove(downloadId)
        cancelledDownloadIds.remove(downloadId)
        activeDownloadCount.incrementAndGet()
        val job = scope.launch(Dispatchers.IO) {
            var actualCategory = category
            var currentFilename = filename

            var parentDir = getSafeDownloadDir(actualCategory)
            if (!parentDir.exists()) parentDir.mkdirs()
            if (actualCategory.equals("adult", ignoreCase = true)) {
                try {
                    val noMedia = File(parentDir, ".nomedia")
                    if (!noMedia.exists()) noMedia.createNewFile()
                } catch (_: Exception) {}
            }
            var destFile = File(parentDir, currentFilename)
            try {
                if (directUrl.contains("mega.nz") || directUrl.contains("mega.co.nz") || directUrl.contains("mega.io")) {
                    withContext(Dispatchers.Main) {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(directUrl)).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                            App.instance.startActivity(intent)
                        } catch (_: Exception) {}
                    }
                    App.instance.database.downloadDao().insertOrUpdate(
                        DownloadEntity(
                            id = downloadId,
                            title = currentFilename,
                            url = directUrl,
                            filename = currentFilename,
                            downloadManagerId = -1L,
                            status = "completed",
                            category = actualCategory,
                            speed = "Opened in MEGA Browser",
                            progress = 100,
                            createdTimestamp = System.currentTimeMillis()
                        )
                    )
                    return@launch
                }

                var existingBytes = if (destFile.exists()) destFile.length() else 0L

                App.instance.database.downloadDao().insertOrUpdate(
                    DownloadEntity(
                        id = downloadId,
                        title = currentFilename,
                        url = directUrl,
                        filename = currentFilename,
                        downloadManagerId = -1L,
                        status = "downloading",
                        category = actualCategory,
                        localFilePath = destFile.absolutePath,
                        downloadedBytes = existingBytes,
                        speed = "Connecting...",
                        createdTimestamp = System.currentTimeMillis()
                    )
                )

                DownloadForegroundService.startOrUpdate(
                    context = App.instance,
                    title = currentFilename,
                    statusText = "Starting download...",
                    progress = 0,
                    indeterminate = true,
                    activeCount = 1
                )

                fun applyRefererHeaders(builder: Request.Builder, url: String) {
                    val isWorkerOrCdn = url.contains("workers.dev") || url.contains("cloudflarestorage") || url.contains("r2.cloudflarestorage") || url.contains("shegu.st") || url.contains("4khdhub")
                    if (!customReferer.isNullOrEmpty()) {
                        builder.header("Referer", customReferer)
                        if (!isWorkerOrCdn) {
                            try {
                                val uri = Uri.parse(customReferer)
                                builder.header("Origin", "${uri.scheme}://${uri.host}")
                            } catch (_: Exception) {}
                        }
                    }
                    when {
                        url.contains("workers.dev") || url.contains("cinejoy") || url.contains("shegu.st") || url.contains("4khdhub") || url.contains("cloudflarestorage") -> {
                            builder.header("Referer", "https://cinejoy.to/")
                            // Note: DO NOT set Origin for Cloudflare Workers / R2! Sending Origin triggers Cloudflare CORS 403 Forbidden!
                        }
                        url.contains("xhamster") || url.contains("xhcdn") -> {
                            builder.header("Referer", "https://xhamster.com/")
                            builder.header("Origin", "https://xhamster.com")
                        }
                        url.contains("pornhub") || url.contains("phncdn") -> {
                            builder.header("Referer", "https://www.pornhub.com/")
                            builder.header("Cookie", "age_verified=1; accessAgeDisclaimerPH=1; has_access=1")
                        }
                        url.contains("redtube") || url.contains("rdtcdn") || url.contains("rncdn") -> {
                            builder.header("Referer", "https://www.redtube.com/")
                            builder.header("Origin", "https://www.redtube.com")
                            builder.header("Cookie", "age_verified=1; has_access=1; il=v111")
                        }
                        url.contains("eporner") -> {
                            builder.header("Referer", "https://www.eporner.com/")
                        }
                        url.contains("xvideos") || url.contains("cdn77") -> {
                            builder.header("Referer", "https://www.xvideos.com/")
                            builder.header("Origin", "https://www.xvideos.com")
                        }
                        url.contains("xnxx") -> {
                            builder.header("Referer", "https://www.xnxx.com/")
                            builder.header("Origin", "https://www.xnxx.com")
                        }
                        url.contains("tiktok") || url.contains("byteoversea") || url.contains("ibytedtos") -> {
                            builder.header("Referer", "https://www.tiktok.com/")
                        }
                        url.contains("instagram") || url.contains("cdninstagram") -> {
                            builder.header("Referer", "https://www.instagram.com/")
                        }
                        url.contains("facebook") || url.contains("fbcdn.net") -> {
                            builder.header("Referer", "https://www.facebook.com/")
                        }
                        url.contains("twimg.com") || url.contains("twitter") || url.contains("x.com") -> {
                            builder.header("Referer", "https://twitter.com/")
                        }
                        url.contains("yts.mx") || url.contains("yts.") -> {
                            builder.header("Referer", "https://yts.mx/")
                        }
                        url.contains("googlevideo.com") -> {
                            builder.header("Origin", "https://www.youtube.com")
                        }
                        url.contains("sinhalasub.net") || url.contains("cdn.sinhalasub.net") || url.contains("ddl.sinhalasub.net") -> {
                            builder.header("Referer", "https://sinhalasub.net/")
                            builder.header("Origin", "https://sinhalasub.net")
                        }
                        url.contains("sinhalasub") -> {
                            builder.header("Referer", "https://sinhalasub.lk/")
                            builder.header("Origin", "https://sinhalasub.lk")
                        }
                        url.contains("piratelk") -> {
                            builder.header("Referer", "https://piratelk.com/")
                            builder.header("Origin", "https://piratelk.com")
                        }
                        url.contains("sub.lk") || url.contains("dl.sub.lk") -> {
                            builder.header("Referer", "https://sub.lk/")
                            builder.header("Origin", "https://sub.lk")
                        }
                        url.contains("cinesubz") || url.contains("sonic-cloud") -> {
                            builder.header("Referer", "https://cinesubz.co/")
                            builder.header("Origin", "https://cinesubz.co")
                        }
                        url.contains("baiscope") || url.contains("baiscopelk") -> {
                            builder.header("Referer", "https://www.baiscopelk.com/")
                            builder.header("Origin", "https://www.baiscopelk.com")
                        }
                        url.contains("pixeldrain") -> {
                            builder.header("Referer", "https://pixeldrain.com/")
                        }
                        url.contains("usersdrive") || url.contains("userdrive") -> {
                            builder.header("Referer", "https://usersdrive.com/")
                            builder.header("Origin", "https://usersdrive.com")
                        }
                        url.contains("filespayout") -> {
                            builder.header("Referer", "https://filespayouts.com/")
                            builder.header("Origin", "https://filespayouts.com")
                        }
                        url.contains("drive.google") || url.contains("drive.usercontent") -> {
                            builder.header("Referer", "https://drive.google.com/")
                        }
                    }
                }

                var downloadFinished = false
                var retryAttempt = 0
                val maxRetries = 5

                while (!downloadFinished && retryAttempt < maxRetries && !pausedDownloadIds.contains(downloadId) && !cancelledDownloadIds.contains(downloadId)) {
                    retryAttempt++
                    var response: Response? = null
                    try {
                        existingBytes = if (destFile.exists()) destFile.length() else 0L

                        val safeDirectUrl = com.clouddrive.leech.extractor.resolvers.netflix.NetflixResolver.sanitizeAndEncodeMediaUrl(directUrl)
                        val reqBuilder = Request.Builder()
                            .url(safeDirectUrl)
                            .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36")

                        if (existingBytes > 0) {
                            reqBuilder.header("Range", "bytes=$existingBytes-")
                        } else if (safeDirectUrl.contains("workers.dev") || safeDirectUrl.contains("cloudflarestorage") || safeDirectUrl.contains("shegu.st")) {
                            reqBuilder.header("Range", "bytes=0-")
                        }

                        applyRefererHeaders(reqBuilder, safeDirectUrl)
                        val mainCall = httpClient.newCall(reqBuilder.build())
                        activeCalls[downloadId] = mainCall
                        response = mainCall.execute()

                        // ⚡ 403 / 416 Fallback: If server rejects Range resume with 403 or 416, retry from byte 0!
                        if ((response.code == 403 || response.code == 416) && existingBytes > 0) {
                            response.close()
                            existingBytes = 0L
                            if (destFile.exists()) destFile.delete()
                            val freshReq = Request.Builder()
                                .url(directUrl)
                                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36")
                            applyRefererHeaders(freshReq, directUrl)
                            val freshCall = httpClient.newCall(freshReq.build())
                            activeCalls[downloadId] = freshCall
                            response = freshCall.execute()
                        }

                        // ⚡ Range Validation: If server responded with 200 to a range request, do NOT append! Reset existingBytes and start fresh from byte 0
                        if (response.code == 200 && existingBytes > 0) {
                            existingBytes = 0L
                            if (destFile.exists()) destFile.delete()
                        }

                        if (!response.isSuccessful && response.code != 206) {
                            if (response.code == 403) {
                                val errSnippet = try { response.peekBody(2048).string() } catch (_: Exception) { "" }
                                if (errSnippet.contains("quota", ignoreCase = true) || errSnippet.contains("exceeded", ignoreCase = true)) {
                                    throw IOException("Google Drive daily quota exceeded on this mirror. Please select another mirror in Direct DL.")
                                }
                            }
                            throw IOException("Server responded with HTTP ${response.code}")
                        }

                        // ⚡ GOOGLE DRIVE VIRUS SCAN WARNING CONFIRMATION BYPASS
                        // Google Drive returns HTTP 200 with an HTML virus warning page for files >100MB.
                        // We parse the confirmation form, inputs (confirm, uuid, id), and cookies to request the real binary video stream!
                        val isGdriveHtml = (safeDirectUrl.contains("drive.google") || safeDirectUrl.contains("drive.usercontent") || directUrl.contains("drive.google") || directUrl.contains("drive.usercontent")) &&
                                response.header("Content-Type")?.contains("text/html", ignoreCase = true) == true

                        if (isGdriveHtml) {
                            val htmlBody = response.body?.string() ?: ""
                            val isWarning = htmlBody.contains("download_warning", ignoreCase = true) ||
                                    htmlBody.contains("download-form", ignoreCase = true) ||
                                    htmlBody.contains("uc-download-link", ignoreCase = true) ||
                                    htmlBody.contains("Google Drive - Virus scan warning", ignoreCase = true) ||
                                    htmlBody.contains("can't scan this file for viruses", ignoreCase = true)

                            if (isWarning) {
                                val formMatch = Regex("""<form[^>]+id=["']download-form["'][^>]*action=["']([^"']+)["'][^>]*>([\s\S]*?)<\/form>""", RegexOption.IGNORE_CASE).find(htmlBody)
                                val formAction = formMatch?.groupValues?.get(1) ?: "https://drive.usercontent.google.com/download"
                                val formInner = formMatch?.groupValues?.get(2) ?: htmlBody

                                val inputMatches = Regex("""<input[^>]+name=["']([^"']+)["'][^>]+value=["']([^"']*)["']""", RegexOption.IGNORE_CASE).findAll(formInner)
                                val queryParams = mutableListOf<String>()
                                var hasConfirm = false
                                var hasId = false
                                for (m in inputMatches) {
                                    val name = m.groupValues[1]
                                    val value = m.groupValues[2]
                                    if (name.equals("confirm", ignoreCase = true)) hasConfirm = true
                                    if (name.equals("id", ignoreCase = true)) hasId = true
                                    queryParams.add("${java.net.URLEncoder.encode(name, "UTF-8")}=${java.net.URLEncoder.encode(value, "UTF-8")}")
                                }

                                if (!hasConfirm) {
                                    val confirmTokenMatch = Regex("""confirm=([a-zA-Z0-9_-]+)""").find(htmlBody)
                                    val cToken = confirmTokenMatch?.groupValues?.get(1) ?: "t"
                                    queryParams.add("confirm=$cToken")
                                }
                                if (!hasId) {
                                    val idMatch = Regex("""(?:id=|\/d\/)([a-zA-Z0-9_-]+)""").find(safeDirectUrl) ?: Regex("""(?:id=|\/d\/)([a-zA-Z0-9_-]+)""").find(directUrl)
                                    if (idMatch != null) {
                                        queryParams.add("id=${idMatch.groupValues[1]}")
                                    }
                                }
                                if (!queryParams.any { it.startsWith("export=") }) {
                                    queryParams.add("export=download")
                                }

                                val step2Url = if (formAction.contains("?")) "$formAction&${queryParams.joinToString("&")}" else "$formAction?${queryParams.joinToString("&")}"
                                val cookies = response.headers("Set-Cookie").joinToString("; ") { it.substringBefore(';') }

                                response.close()
                                if (destFile.exists()) destFile.delete()
                                existingBytes = 0L

                                val step2Req = Request.Builder()
                                    .url(step2Url)
                                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36")
                                if (cookies.isNotEmpty()) {
                                    step2Req.header("Cookie", cookies)
                                }
                                val step2Call = httpClient.newCall(step2Req.build())
                                activeCalls[downloadId] = step2Call
                                response = step2Call.execute()
                            }
                        }

                        // ⚡ CORRUPT WEB-PAGE PROTECTION: If server sent HTML webpage without attachment disposition, abort
                        val ctSniff = response.header("Content-Type")?.lowercase() ?: ""
                        val cdSniff = response.header("Content-Disposition")?.lowercase() ?: ""
                        if (ctSniff.contains("text/html") && !cdSniff.contains("attachment") && !cdSniff.contains(".mp4") && !cdSniff.contains(".mkv")) {
                            throw IOException("Server returned an HTML web page instead of media file.")
                        }

                        // ⚡ SNIFF HEADERS: Content-Disposition & Content-Type for Any-File Support
                        val cdHeader = response.header("Content-Disposition")
                        val ctHeader = response.header("Content-Type")
                        val serverFilename = extractFilenameFromContentDisposition(cdHeader)
                        val mimeExt = getExtensionFromMimeType(ctHeader)

                        var updatedFilename = currentFilename
                        if (!serverFilename.isNullOrBlank()) {
                            updatedFilename = serverFilename
                        } else if (mimeExt != null) {
                            if (!updatedFilename.contains(".")) {
                                updatedFilename += mimeExt
                            } else if (updatedFilename.endsWith(".mp4", ignoreCase = true) && mimeExt in listOf(".apk", ".zip", ".rar", ".7z", ".pdf", ".iso", ".mp3", ".doc", ".xlsx", ".mkv")) {
                                updatedFilename = updatedFilename.substring(0, updatedFilename.length - 4) + mimeExt
                            }
                        }

                        // Re-evaluate category and directory with resolved filename
                        val newCategory = determineCategoryFromFilenameOrUrl(updatedFilename, directUrl, actualCategory)
                        if (newCategory != actualCategory || updatedFilename != currentFilename) {
                            actualCategory = newCategory
                            currentFilename = updatedFilename
                            val newParentDir = getSafeDownloadDir(actualCategory)
                            if (!newParentDir.exists()) newParentDir.mkdirs()
                            val newDestFile = File(newParentDir, currentFilename)
                            if (destFile.absolutePath != newDestFile.absolutePath) {
                                if (destFile.exists() && existingBytes == 0L) {
                                    destFile.delete()
                                } else if (destFile.exists() && existingBytes > 0L) {
                                    destFile.renameTo(newDestFile)
                                }
                                destFile = newDestFile
                                parentDir = newParentDir
                                existingBytes = if (destFile.exists()) destFile.length() else 0L
                            }
                        }

                        val body = response.body ?: throw IOException("Empty response body")
                        val isRangeResume = response.code == 206
                        val totalBytes = if (isRangeResume) existingBytes + body.contentLength() else body.contentLength()

                        var downloadedBytes = if (isRangeResume) existingBytes else 0L
                        val fos = FileOutputStream(destFile, isRangeResume)
                        val buffer = ByteArray(64 * 1024)
                        var bytesRead: Int
                        val inputStream = body.byteStream()

                        var lastNotifyTime = System.currentTimeMillis()
                        var lastReportBytes = downloadedBytes

                        inputStream.use { input ->
                            fos.use { output ->
                                while (input.read(buffer).also { bytesRead = it } != -1) {
                                    if (pausedDownloadIds.contains(downloadId) || cancelledDownloadIds.contains(downloadId)) {
                                        break
                                    }
                                    output.write(buffer, 0, bytesRead)
                                    downloadedBytes += bytesRead

                                    val now = System.currentTimeMillis()
                                    if (now - lastNotifyTime >= 1000) {
                                        val timeDiffSec = (now - lastNotifyTime) / 1000.0
                                        val speedMBps = if (timeDiffSec > 0) ((downloadedBytes - lastReportBytes) / (1024.0 * 1024.0)) / timeDiffSec else 0.0
                                        lastNotifyTime = now
                                        lastReportBytes = downloadedBytes

                                        val progress = if (totalBytes > 0) ((downloadedBytes * 100) / totalBytes).toInt().coerceIn(0, 100) else 0
                                        val speedStr = if (speedMBps > 0.05) "${String.format(java.util.Locale.US, "%.1f", speedMBps)} MB/s" else "Downloading..."

                                        val curDownloaded = downloadedBytes
                                        App.instance.downloadScope.launch(Dispatchers.IO) {
                                            runCatching {
                                                if (pausedDownloadIds.contains(downloadId) || cancelledDownloadIds.contains(downloadId)) {
                                                    return@launch
                                                }
                                                App.instance.database.downloadDao().insertOrUpdate(
                                                    DownloadEntity(
                                                        id = downloadId,
                                                        title = currentFilename,
                                                        url = directUrl,
                                                        filename = currentFilename,
                                                        downloadManagerId = -1L,
                                                        status = "downloading",
                                                        category = actualCategory,
                                                        downloadedBytes = curDownloaded,
                                                        totalBytes = if (totalBytes > 0) totalBytes else curDownloaded,
                                                        progress = progress,
                                                        speed = speedStr,
                                                        localFilePath = destFile.absolutePath,
                                                        createdTimestamp = System.currentTimeMillis()
                                                    )
                                                )

                                                 DownloadNotificationHelper.updateProgress(
                                                    context = App.instance,
                                                    notifId = downloadId.hashCode(),
                                                    title = currentFilename,
                                                    progress = progress,
                                                    speedMBps = speedMBps,
                                                    downloadedBytes = curDownloaded,
                                                    totalBytes = totalBytes
                                                )

                                                emitDownloadProgress(
                                                    id = downloadId,
                                                    filename = currentFilename,
                                                    status = "downloading",
                                                    progress = progress,
                                                    speed = speedStr,
                                                    downloaded = curDownloaded,
                                                    total = totalBytes,
                                                    localPath = destFile.absolutePath
                                                )
                                            }
                                        }
                                    }
                                }
                                output.flush()
                            }
                        }

                        if (pausedDownloadIds.contains(downloadId) || cancelledDownloadIds.contains(downloadId)) {
                            return@launch
                        }

                        // ⚡ Incomplete Download Detection: Check if stream was prematurely closed/truncated!
                        val isTruncated = totalBytes > 0 && downloadedBytes < (totalBytes - 4096L)
                        if (isTruncated) {
                            if (pausedDownloadIds.contains(downloadId) || cancelledDownloadIds.contains(downloadId)) {
                                return@launch
                            }
                            if (retryAttempt < maxRetries) {
                                android.util.Log.w("NativeDownloadPlugin", "Background stream truncated ($downloadedBytes/$totalBytes), retrying attempt $retryAttempt/$maxRetries...")
                                kotlinx.coroutines.delay(1500L)
                                continue
                            } else {
                                DownloadNotificationHelper.cancel(App.instance, downloadId.hashCode())
                                val dlMB = String.format(java.util.Locale.US, "%.1f", downloadedBytes / (1024.0 * 1024.0))
                                val totMB = String.format(java.util.Locale.US, "%.1f", totalBytes / (1024.0 * 1024.0))
                                val progress = ((downloadedBytes * 100) / totalBytes).toInt().coerceIn(0, 99)

                                App.instance.database.downloadDao().insertOrUpdate(
                                    DownloadEntity(
                                        id = downloadId,
                                        title = currentFilename,
                                        url = directUrl,
                                        filename = currentFilename,
                                        downloadManagerId = -1L,
                                        status = "failed",
                                        errorMessage = "Interrupted ($dlMB / $totMB MB) - Tap to resume",
                                        category = actualCategory,
                                        downloadedBytes = downloadedBytes,
                                        totalBytes = totalBytes,
                                        progress = progress,
                                        speed = "Paused",
                                        localFilePath = destFile.absolutePath,
                                        createdTimestamp = System.currentTimeMillis()
                                    )
                                )
                                emitDownloadStateChange(downloadId, "failed", JSObject().apply {
                                    put("errorMessage", "Interrupted ($dlMB / $totMB MB)")
                                })
                                return@launch
                            }
                        }

                        // Completed successfully
                        downloadFinished = true
                        DownloadNotificationHelper.cancel(App.instance, downloadId.hashCode())
                        DownloadNotificationHelper.showCompleted(App.instance, downloadId.hashCode(), currentFilename, destFile.absolutePath)

                        App.instance.database.downloadDao().insertOrUpdate(
                            DownloadEntity(
                                id = downloadId,
                                title = currentFilename,
                                url = directUrl,
                                filename = currentFilename,
                                downloadManagerId = -1L,
                                status = "completed",
                                category = actualCategory,
                                downloadedBytes = downloadedBytes,
                                totalBytes = if (totalBytes > 0) totalBytes else downloadedBytes,
                                progress = 100,
                                localFilePath = destFile.absolutePath
                            )
                        )

                        emitDownloadProgress(
                            id = downloadId,
                            filename = currentFilename,
                            status = "completed",
                            progress = 100,
                            speed = "Completed",
                            downloaded = downloadedBytes,
                            total = if (totalBytes > 0) totalBytes else downloadedBytes,
                            localPath = destFile.absolutePath
                        )
                        emitDownloadStateChange(downloadId, "completed", JSObject().apply {
                            put("localFilePath", destFile.absolutePath)
                            put("filename", currentFilename)
                        })

                        val ctx = context ?: App.instance
                        val ext = destFile.extension.lowercase()
                        val scanMime = when {
                            ext == "apk" -> "application/vnd.android.package-archive"
                            ext == "mkv" -> "video/x-matroska"
                            ext in listOf("mp4", "webm", "avi", "mov", "flv", "ts") -> "video/mp4"
                            ext in listOf("mp3", "m4a", "flac", "wav", "aac", "ogg") -> "audio/mpeg"
                            ext == "pdf" -> "application/pdf"
                            ext in listOf("zip", "rar", "7z", "tar", "gz") -> "application/zip"
                            else -> MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "*/*"
                        }
                        MediaScannerConnection.scanFile(ctx, arrayOf(destFile.absolutePath), arrayOf(scanMime), null)

                    } catch (err: Exception) {
                        if (pausedDownloadIds.contains(downloadId) || cancelledDownloadIds.contains(downloadId)) {
                            return@launch
                        }
                        if (retryAttempt < maxRetries) {
                            android.util.Log.w("NativeDownloadPlugin", "Network glitch during background download ($retryAttempt/$maxRetries): ${err.message}")
                            kotlinx.coroutines.delay(2000L)
                        } else {
                            DownloadNotificationHelper.cancel(App.instance, downloadId.hashCode())
                            App.instance.database.downloadDao().insertOrUpdate(
                                DownloadEntity(
                                    id = downloadId,
                                    title = currentFilename,
                                    url = directUrl,
                                    filename = currentFilename,
                                    downloadManagerId = -1L,
                                    status = "failed",
                                    category = actualCategory,
                                    errorMessage = err.message ?: "Download error",
                                    localFilePath = destFile.absolutePath
                                )
                            )
                            emitDownloadStateChange(downloadId, "failed", JSObject().apply {
                                put("errorMessage", err.message ?: "Download error")
                            })
                        }
                    } finally {
                        activeCalls.remove(downloadId)
                        try { response?.close() } catch (_: Exception) {}
                    }
                }

            } catch (err: Exception) {
                if (pausedDownloadIds.contains(downloadId) || cancelledDownloadIds.contains(downloadId)) {
                    return@launch
                }
                DownloadNotificationHelper.cancel(App.instance, downloadId.hashCode())
                App.instance.database.downloadDao().insertOrUpdate(
                    DownloadEntity(
                        id = downloadId,
                        title = currentFilename,
                        url = directUrl,
                        filename = currentFilename,
                        downloadManagerId = -1L,
                        status = "failed",
                        category = actualCategory,
                        errorMessage = err.message ?: "Download error",
                        localFilePath = destFile.absolutePath
                    )
                )
            } finally {
                activeCalls.remove(downloadId)
                activeHlsJobs.remove(downloadId)
                if (activeDownloadCount.decrementAndGet() <= 0) {
                    DownloadForegroundService.stop(App.instance)
                }
            }
        }
        activeHlsJobs[downloadId] = job
    }

    suspend fun resolveDirectDownloadUrl(rawUrl: String): String = withContext(Dispatchers.IO) {
        var url = rawUrl.trim()

        // ⚡ Fast-Pass: Skip heavy scrapers if URL is already a direct stream or file link
        if (isAlreadyDirectUrl(url)) {
            return@withContext url
        }

        // ⚡ UsersDrive Direct Resolver: Fast-path for usersdrive landing pages
        if (com.clouddrive.leech.extractor.resolvers.UsersDriveResolver.canHandle(url)) {
            try {
                val act = activity ?: (context as? android.app.Activity)
                if (act != null) {
                    val uRes = com.clouddrive.leech.extractor.resolvers.UsersDriveResolver.resolve(act, url)
                    if (uRes is com.clouddrive.leech.extractor.resolvers.UsersDriveResolver.ResolveResult.Success) {
                        return@withContext uRes.stream.directUrl
                    }
                }
            } catch (e: Exception) {
                android.util.Log.w("NativeDownloadPlugin", "UsersDrive download resolution error: ${e.message}")
            }
        }

        // 0. Unwrap nested URL params
        if (url.contains("url=")) {
            try {
                val parts = url.split("url=")
                if (parts.size > 1) {
                    val encoded = parts[1].split("&")[0]
                    val decoded = java.net.URLDecoder.decode(encoded, "UTF-8")
                    if (decoded.startsWith("http://") || decoded.startsWith("https://")) {
                        url = decoded
                    }
                }
            } catch (_: Exception) {}
        }

        // 1. PixelDrain Direct API URL
        val pdMatch = Regex("""pixeldrain\.com\/(?:u|l|api\/file|e)\/([a-zA-Z0-9_-]+)""").find(url)
        if (pdMatch != null) {
            val fileId = pdMatch.groupValues[1]
            return@withContext "https://pixeldrain.com/api/file/$fileId?download"
        }

        // 2. Google Drive Direct Download (direct usercontent CDN bypassing confirmation)
        val gdMatch = Regex("""(?:drive\.google\.com|drive\.usercontent\.google\.com)\/(?:file\/d\/|open\?id=|uc\?id=|download\?id=)([a-zA-Z0-9_-]+)""").find(url)
        if (gdMatch != null) {
            val fileId = gdMatch.groupValues[1]
            return@withContext "https://drive.usercontent.google.com/download?id=$fileId&export=download&authuser=0"
        }

        // 3. Extractor Manager Deep Resolution for Adult (Pornhub, xHamster, Eporner, XVideos, XNXX, RedTube)
        if (url.contains("eporner.com") || url.contains("xhamster.com") || url.contains("pornhub.com") ||
            url.contains("redtube.com") || url.contains("xnxx.com") || url.contains("xvideos.com")
        ) {
            try {
                val resolved = App.instance.extractorManager.adultProvider.resolveAdultStream(url)
                if (resolved.downloadUrl.isNotEmpty() && resolved.downloadUrl.startsWith("http") && !resolved.downloadUrl.contains("embed")) {
                    return@withContext resolved.downloadUrl
                }
                if (resolved.streamUrl.isNotEmpty() && resolved.streamUrl.startsWith("http") && !resolved.streamUrl.contains("embed")) {
                    return@withContext resolved.streamUrl
                }
                for (q in resolved.qualities) {
                    if (q.downloadUrl.isNotEmpty() && q.downloadUrl.startsWith("http") && !q.isEmbed) {
                        return@withContext q.downloadUrl
                    }
                    if (q.url.isNotEmpty() && q.url.startsWith("http") && !q.isEmbed) {
                        return@withContext q.url
                    }
                }
            } catch (_: Exception) {}
        }

        // 2b. Cloudflare Google Drive Index Workers (e.g. drive2.baiscopeslk.workers.dev)
        if (url.contains("workers.dev") || url.contains("drive2.baiscopeslk")) {
            return@withContext url
        }

        // 3b. Cinejoy & Netflix Direct Stream Resolver
        if (url.contains("cinejoy") || url.contains("shegu.st") || url.contains("netflix")) {
            try {
                val act = activity ?: (context as? android.app.Activity)
                val cjRes = com.clouddrive.leech.extractor.resolvers.netflix.NetflixResolver.resolve(act ?: android.app.Activity(), url)
                if (cjRes != null && cjRes.streamUrl.isNotEmpty() && !cjRes.streamUrl.contains("/watch/")) {
                    return@withContext com.clouddrive.leech.extractor.resolvers.netflix.NetflixResolver.sanitizeAndEncodeMediaUrl(cjRes.streamUrl)
                }
            } catch (_: Exception) {}
        }

        if (url.contains("sinhalasub") || url.contains("baiscope") || url.contains("piratelk") ||
            url.contains("sub.lk") || url.contains("cinesubz") || url.contains("dl.sub.lk") ||
            url.contains("/links/") || url.contains("usersdrive") || url.contains("filespayout") ||
            url.contains("cinerustreams") || url.contains("gofile")
        ) {
            try {
                val resolved = App.instance.extractorManager.movieProvider.resolveFinalDownloadUrl(url)
                val finalCandidate = when {
                    resolved.downloadUrl.isNotEmpty() && resolved.downloadUrl.startsWith("http") -> resolved.downloadUrl
                    resolved.streamUrl.isNotEmpty() && resolved.streamUrl.startsWith("http") -> resolved.streamUrl
                    else -> url
                }

                val pd2 = Regex("""pixeldrain\.com\/(?:u|l|api\/file|e)\/([a-zA-Z0-9_-]+)""").find(finalCandidate)
                if (pd2 != null) {
                    return@withContext "https://pixeldrain.com/api/file/${pd2.groupValues[1]}?download"
                }

                val gd2 = Regex("""(?:drive\.google\.com|drive\.usercontent\.google\.com)\/(?:file\/d\/|open\?id=|uc\?id=|download\?id=)([a-zA-Z0-9_-]+)""").find(finalCandidate)
                if (gd2 != null) {
                    return@withContext "https://drive.usercontent.google.com/download?id=${gd2.groupValues[1]}&export=download&authuser=0"
                }

                if (finalCandidate.isNotEmpty() && finalCandidate.startsWith("http")) {
                    return@withContext com.clouddrive.leech.extractor.resolvers.netflix.NetflixResolver.sanitizeAndEncodeMediaUrl(finalCandidate)
                }
            } catch (_: Exception) {}
        }

        // 4. Universal Social Media & Web Video Extractor Fallback (YouTube, TikTok, Facebook, IG, X, Reddit, Vimeo, Dailymotion)
        try {
            val extracted = App.instance.extractorManager.extractMedia(url)
            if (extracted.success && extracted.data != null) {
                val details = extracted.data
                val firstQual = details.qualities.firstOrNull { it.downloadUrl.isNotEmpty() || it.url.isNotEmpty() }
                val streamCandidate = firstQual?.downloadUrl?.ifEmpty { firstQual.url } ?: details.directDownloadUrl ?: details.directStreamUrl
                if (!streamCandidate.isNullOrEmpty() && streamCandidate.startsWith("http") && !streamCandidate.contains("embed")) {
                    return@withContext com.clouddrive.leech.extractor.resolvers.netflix.NetflixResolver.sanitizeAndEncodeMediaUrl(streamCandidate)
                }
            }
        } catch (_: Exception) {}

        com.clouddrive.leech.extractor.resolvers.netflix.NetflixResolver.sanitizeAndEncodeMediaUrl(url)
    }

    @PluginMethod
    fun getDownloads(call: PluginCall) {
        scope.launch(Dispatchers.IO) {
            val ctx = context ?: App.instance
            val dm = ctx.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
            val rawList = App.instance.database.downloadDao().getAllDownloads()
            val resultArr = JSArray()

            val now = System.currentTimeMillis()

            for (entity in rawList) {
                var currentStatus = entity.status
                var downloadedBytes = entity.downloadedBytes
                var totalBytes = entity.totalBytes
                var localFilePath = entity.localFilePath
                var downloadError = entity.errorMessage

                // Query Android DownloadManager only if dmId > 0
                if (dm != null && entity.downloadManagerId > 0 && currentStatus != "completed") {
                    try {
                        val q = DownloadManager.Query().setFilterById(entity.downloadManagerId)
                        dm.query(q)?.use { cursor ->
                            if (cursor.moveToFirst()) {
                                val bytesCol = cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                                val totalCol = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                                val statusCol = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                                val reasonCol = cursor.getColumnIndex(DownloadManager.COLUMN_REASON)
                                val localUriCol = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)

                                if (bytesCol != -1) downloadedBytes = cursor.getLong(bytesCol)
                                if (totalCol != -1) totalBytes = cursor.getLong(totalCol)
                                if (localUriCol != -1) {
                                    val uriStr = cursor.getString(localUriCol)
                                    if (!uriStr.isNullOrEmpty()) {
                                        val clean = try {
                                            if (uriStr.startsWith("file://")) {
                                                Uri.parse(uriStr).path ?: uriStr.removePrefix("file://")
                                            } else {
                                                uriStr
                                            }
                                        } catch (_: Exception) { uriStr }
                                        if (clean.isNotEmpty() && !clean.startsWith("content://")) {
                                            localFilePath = clean
                                        }
                                    }
                                }

                                if (statusCol != -1) {
                                    val dmStatus = cursor.getInt(statusCol)
                                    currentStatus = when (dmStatus) {
                                        DownloadManager.STATUS_SUCCESSFUL -> {
                                            if (localFilePath.isNotEmpty()) {
                                                try {
                                                    val ctx = context ?: App.instance
                                                    MediaScannerConnection.scanFile(ctx, arrayOf(localFilePath), null, null)
                                                } catch (_: Exception) {}
                                            }
                                            "completed"
                                        }
                                        DownloadManager.STATUS_RUNNING -> "downloading"
                                        DownloadManager.STATUS_PAUSED -> "paused"
                                        DownloadManager.STATUS_FAILED -> {
                                            downloadError = if (reasonCol != -1) {
                                                describeDownloadFailure(cursor.getInt(reasonCol))
                                            } else {
                                                "Android DownloadManager failed"
                                            }
                                            "failed"
                                        }
                                        DownloadManager.STATUS_PENDING -> "pending"
                                        else -> currentStatus
                                    }
                                }
                            }
                        }
                    } catch (_: Exception) {}

                    val progressInt = if (totalBytes > 0) ((downloadedBytes * 100) / totalBytes).toInt().coerceIn(0, 100) else if (currentStatus == "completed") 100 else 0
                    App.instance.database.downloadDao().insertOrUpdate(
                        entity.copy(
                            status = currentStatus,
                            downloadedBytes = downloadedBytes,
                            totalBytes = totalBytes,
                            progress = progressInt,
                            localFilePath = localFilePath,
                            errorMessage = downloadError
                        )
                    )
                }

                // Query Native LibTorrent Engine for live speed, bytes, and status
                val torrentStatus = if (entity.downloadManagerId == -2L || entity.url.startsWith("magnet:")) {
                    com.clouddrive.leech.torrent.TorrentEngineManager.getTorrentStatus(entity.id)
                } else null

                if (torrentStatus != null) {
                    downloadedBytes = torrentStatus.totalDone
                    if (torrentStatus.totalWanted > 0) totalBytes = torrentStatus.totalWanted
                    currentStatus = when (torrentStatus.state) {
                        "completed", "seeding" -> "completed"
                        "paused" -> "paused"
                        else -> "downloading"
                    }
                    if (torrentStatus.files.isNotEmpty()) {
                        val mainVid = torrentStatus.files.firstOrNull { it.isVideo } ?: torrentStatus.files[0]
                        localFilePath = java.io.File(torrentStatus.savePath, mainVid.path).absolutePath
                    }
                }

                // Speed & ETA Calculation
                val tracker = speedTracker.getOrPut(entity.id) { SpeedSample(downloadedBytes, now, 0.0) }
                val timeDiffSec = (now - tracker.lastTime) / 1000.0
                if (timeDiffSec >= 0.8 && currentStatus == "downloading") {
                    val bytesDiff = (downloadedBytes - tracker.lastBytes).coerceAtLeast(0)
                    val speedBytesPerSec = bytesDiff / timeDiffSec
                    tracker.speedMBps = speedBytesPerSec / (1024.0 * 1024.0)
                    tracker.lastBytes = downloadedBytes
                    tracker.lastTime = now
                }

                val speedMBps = if (torrentStatus != null) {
                    if (currentStatus == "downloading") torrentStatus.downloadRate / (1024.0 * 1024.0) else 0.0
                } else {
                    if (currentStatus == "downloading") tracker.speedMBps else 0.0
                }
                val downloadedMB = (downloadedBytes / (1024.0 * 1024.0))
                val totalMB = (totalBytes / (1024.0 * 1024.0))
                val percent = if (totalBytes > 0) ((downloadedBytes * 100) / totalBytes).toInt().coerceIn(0, 100) else if (currentStatus == "completed") 100 else 0
                val remainingBytes = (totalBytes - downloadedBytes).coerceAtLeast(0)
                val etaSec = if (speedMBps > 0.05 && remainingBytes > 0) (remainingBytes / (speedMBps * 1024 * 1024)).toInt() else 0

                val notifId = entity.id.hashCode()
                val appContext = context ?: App.instance
                // Cancel any legacy separate notifications so only the unified DownloadForegroundService shows
                DownloadNotificationHelper.cancel(appContext, notifId)

                val itemObj = JSObject().apply {
                    put("id", entity.id)
                    put("title", entity.title)
                    put("filename", entity.filename)
                    put("fileName", entity.filename)
                    put("url", entity.url)
                    put("downloadManagerId", entity.downloadManagerId)
                    put("status", currentStatus)
                    put("progress", percent)
                    put("percent", percent)
                    put("downloadedBytes", downloadedBytes)
                    put("totalBytes", totalBytes)
                    put("downloadedMB", String.format(java.util.Locale.US, "%.2f", downloadedMB))
                    put("totalMB", String.format(java.util.Locale.US, "%.2f", totalMB))
                    put("speedMBps", String.format(java.util.Locale.US, "%.2f", speedMBps))
                    put("etaSec", etaSec)
                    put("localFilePath", localFilePath)
                    put("errorMessage", downloadError)
                    put("createdTimestamp", entity.createdTimestamp)
                    put("numPeers", torrentStatus?.numPeers ?: 0)
                    put("numSeeds", torrentStatus?.numSeeds ?: 0)
                    put("isTorrent", entity.downloadManagerId == -2L || entity.url.startsWith("magnet:"))
                }
                resultArr.put(itemObj)
            }

            var activeCount = 0
            var totalSpeed = 0.0
            var primaryTitle = ""
            var primaryPercent = -1
            var primaryText = ""
            for (i in 0 until resultArr.length()) {
                val item = resultArr.getJSONObject(i)
                if (item.optString("status") == "downloading") {
                    activeCount++
                    val s = item.optDouble("speedMBps", 0.0)
                    totalSpeed += s
                    if (primaryTitle.isEmpty()) {
                        primaryTitle = item.optString("title", item.optString("filename", "Download"))
                        primaryPercent = item.optInt("percent", 0)
                        val spdStr = if (s > 0.05) "${String.format(java.util.Locale.US, "%.1f", s)} MB/s" else "Downloading..."
                        val dlMb = item.optString("downloadedMB", "0.0")
                        val totMb = item.optString("totalMB", "0.0")
                        val eta = item.optInt("etaSec", 0)
                        val etaStr = if (eta > 0) " • ETA: ${eta / 60}m ${eta % 60}s" else ""
                        primaryText = "$primaryPercent% • $spdStr • $dlMb / $totMb MB$etaStr"
                    }
                }
            }
            val appContext = context ?: App.instance
            if (activeCount > 0) {
                if (activeCount == 1) {
                    DownloadForegroundService.startOrUpdate(
                        context = appContext,
                        title = primaryTitle,
                        statusText = primaryText,
                        progress = primaryPercent,
                        indeterminate = primaryPercent < 0,
                        activeCount = 1
                    )
                } else {
                    DownloadForegroundService.startOrUpdate(
                        context = appContext,
                        title = "⚡ Downloading $activeCount files (${String.format(java.util.Locale.US, "%.1f", totalSpeed)} MB/s)",
                        statusText = "Active: $primaryTitle ($primaryPercent%)",
                        progress = primaryPercent,
                        indeterminate = false,
                        activeCount = activeCount
                    )
                }
            } else {
                DownloadForegroundService.stop(appContext)
            }

            val ret = JSObject().apply {
                put("success", true)
                put("downloads", resultArr)
            }
            call.resolve(ret)
        }
    }

    private fun describeDownloadFailure(reason: Int): String {
        return when (reason) {
            DownloadManager.ERROR_CANNOT_RESUME -> "Download cannot resume"
            DownloadManager.ERROR_DEVICE_NOT_FOUND -> "Download storage is unavailable"
            DownloadManager.ERROR_FILE_ALREADY_EXISTS -> "A file with this name already exists"
            DownloadManager.ERROR_FILE_ERROR -> "Storage file error"
            DownloadManager.ERROR_HTTP_DATA_ERROR -> "Server returned invalid download data"
            DownloadManager.ERROR_INSUFFICIENT_SPACE -> "Not enough storage space"
            DownloadManager.ERROR_TOO_MANY_REDIRECTS -> "Too many server redirects"
            DownloadManager.ERROR_UNHANDLED_HTTP_CODE -> "Server rejected the download (HTTP error)"
            DownloadManager.ERROR_UNKNOWN -> "Unknown Android download error"
            else -> "Android download failed (reason $reason)"
        }
    }

    @PluginMethod
    fun pauseDownload(call: PluginCall) {
        val id = call.getString("id") ?: ""
        if (id.isEmpty()) {
            call.reject("Download ID required")
            return
        }

        scope.launch(Dispatchers.IO) {
            try {
                val ctx = context ?: App.instance
                pausedDownloadIds.add(id)
                cancelledDownloadIds.remove(id)

                // 1. Immediately abort active OkHttp network socket
                try { activeCalls[id]?.cancel() } catch (_: Exception) {}
                activeCalls.remove(id)

                // 2. Cancel active coroutine Job
                try { activeHlsJobs[id]?.cancel() } catch (_: Exception) {}
                activeHlsJobs.remove(id)
                speedTracker.remove(id)

                val entity = App.instance.database.downloadDao().getById(id)
                if (entity != null) {
                    if (entity.downloadManagerId == -2L || entity.url.startsWith("magnet:")) {
                        com.clouddrive.leech.torrent.TorrentEngineManager.pauseTorrent(entity.id)
                    } else if (entity.downloadManagerId > 0) {
                        val dm = ctx.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
                        try { dm?.remove(entity.downloadManagerId) } catch (_: Exception) {}
                    }
                    val updated = entity.copy(
                        status = "paused",
                        speed = "Paused",
                        downloadManagerId = if (entity.downloadManagerId == -2L) -2L else -1L
                    )
                    App.instance.database.downloadDao().insertOrUpdate(updated)
                    DownloadNotificationHelper.cancel(ctx, entity.id.hashCode())
                }

                emitDownloadStateChange(id, "paused")
                checkAndUpdateForegroundService(ctx)

                withContext(Dispatchers.Main) {
                    call.resolve(JSObject().put("success", true).put("status", "paused"))
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    call.reject("Failed to pause: ${e.message}")
                }
            }
        }
    }

    fun resumeDownloadItem(entity: DownloadEntity) {
        val downloadUrl = entity.url
        val filename = entity.filename
        val category = entity.category

        pausedDownloadIds.remove(entity.id)
        cancelledDownloadIds.remove(entity.id)

        if (entity.downloadManagerId == -2L || entity.url.startsWith("magnet:")) {
            com.clouddrive.leech.torrent.TorrentEngineManager.resumeTorrent(entity.id)
            scope.launch(Dispatchers.IO) {
                App.instance.database.downloadDao().insertOrUpdate(entity.copy(status = "downloading"))
            }
        } else if (downloadUrl.contains(".m3u8") || downloadUrl.contains("/hls/")) {
            startHlsDownload(entity.id, downloadUrl, filename, category)
        } else {
            scope.launch(Dispatchers.IO) {
                App.instance.database.downloadDao().insertOrUpdate(entity.copy(status = "downloading", downloadManagerId = -1L))
            }
            startDirectStreamDownload(entity.id, downloadUrl, filename, category)
        }
        val ctx = context ?: App.instance
        DownloadForegroundService.startOrUpdate(ctx, filename, "Resuming download...", progress = 0, indeterminate = true, activeCount = 1)
    }

    @PluginMethod
    fun resumeDownload(call: PluginCall) {
        val id = call.getString("id") ?: ""
        if (id.isEmpty()) {
            call.reject("Download ID required")
            return
        }

        scope.launch(Dispatchers.IO) {
            try {
                val ctx = context ?: App.instance
                pausedDownloadIds.remove(id)
                cancelledDownloadIds.remove(id)

                val entity = App.instance.database.downloadDao().getById(id)
                if (entity == null) {
                    withContext(Dispatchers.Main) { call.reject("Download not found") }
                    return@launch
                }

                resumeDownloadItem(entity)
                emitDownloadStateChange(id, "downloading")

                withContext(Dispatchers.Main) {
                    call.resolve(JSObject().put("success", true).put("status", "downloading"))
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    call.reject("Failed to resume: ${e.message}")
                }
            }
        }
    }

    @PluginMethod
    fun cancelDownload(call: PluginCall) {
        val id = call.getString("id") ?: ""
        if (id.isEmpty()) {
            call.reject("Download ID required")
            return
        }

        scope.launch(Dispatchers.IO) {
            try {
                val ctx = context ?: App.instance
                cancelledDownloadIds.add(id)
                pausedDownloadIds.remove(id)

                try { activeCalls[id]?.cancel() } catch (_: Exception) {}
                activeCalls.remove(id)

                try { activeHlsJobs[id]?.cancel() } catch (_: Exception) {}
                activeHlsJobs.remove(id)
                speedTracker.remove(id)
                DownloadNotificationHelper.cancel(ctx, id.hashCode())

                val entity = App.instance.database.downloadDao().getById(id)
                if (entity != null) {
                    if (entity.downloadManagerId == -2L || entity.url.startsWith("magnet:")) {
                        com.clouddrive.leech.torrent.TorrentEngineManager.removeTorrent(entity.id, false)
                    } else if (entity.downloadManagerId > 0) {
                        val dm = ctx.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
                        try { dm?.remove(entity.downloadManagerId) } catch (_: Exception) {}
                    }
                    val updated = entity.copy(
                        status = "cancelled",
                        speed = "Cancelled"
                    )
                    App.instance.database.downloadDao().insertOrUpdate(updated)
                }

                emitDownloadStateChange(id, "cancelled")
                checkAndUpdateForegroundService(ctx)

                withContext(Dispatchers.Main) {
                    call.resolve(JSObject().put("success", true).put("status", "cancelled"))
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    call.reject("Failed to cancel: ${e.message}")
                }
            }
        }
    }

    @PluginMethod
    fun deleteDownload(call: PluginCall) {
        val id = call.getString("id") ?: ""
        val path = call.getString("path") ?: ""
        val filename = call.getString("filename") ?: ""

        scope.launch(Dispatchers.IO) {
            try {
                val ctx = context ?: App.instance

                if (id.isNotEmpty()) {
                    cancelledDownloadIds.add(id)
                    pausedDownloadIds.remove(id)

                    try { activeCalls[id]?.cancel() } catch (_: Exception) {}
                    activeCalls.remove(id)

                    try { activeHlsJobs[id]?.cancel() } catch (_: Exception) {}
                    activeHlsJobs.remove(id)
                    speedTracker.remove(id)
                    DownloadNotificationHelper.cancel(ctx, id.hashCode())
                }

                if (path.isNotEmpty()) {
                    DownloadNotificationHelper.cancel(ctx, path.hashCode())
                }

                if (filename.isNotEmpty()) {
                    DownloadNotificationHelper.cancel(ctx, filename.hashCode())
                }

                // 1. Check if entity exists in Room DB
                val entity = if (id.isNotEmpty()) App.instance.database.downloadDao().getById(id) else null
                if (entity != null) {
                    if (entity.downloadManagerId == -2L || entity.url.startsWith("magnet:")) {
                        com.clouddrive.leech.torrent.TorrentEngineManager.removeTorrent(entity.id, true)
                    }
                    DownloadNotificationHelper.cancel(ctx, entity.id.hashCode())
                    if (entity.downloadManagerId > 0) {
                        DownloadNotificationHelper.cancel(ctx, entity.downloadManagerId.toInt())
                        val dm = ctx.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
                        try { dm?.remove(entity.downloadManagerId) } catch (_: Exception) {}
                    }

                    if (entity.localFilePath.isNotEmpty()) {
                        try {
                            val f = File(entity.localFilePath)
                            if (f.exists()) f.delete()
                            MediaScannerConnection.scanFile(ctx, arrayOf(f.absolutePath), null, null)
                        } catch (_: Exception) {}
                    }

                    if (entity.filename.isNotEmpty()) {
                        try {
                            val f = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), entity.filename)
                            if (f.exists()) f.delete()
                            MediaScannerConnection.scanFile(ctx, arrayOf(f.absolutePath), null, null)
                        } catch (_: Exception) {}
                    }

                    App.instance.database.downloadDao().delete(id)
                } else if (id.isNotEmpty()) {
                    App.instance.database.downloadDao().delete(id)
                }

                // 2. Also delete explicitly by local file path
                if (path.isNotEmpty()) {
                    try {
                        val f = File(path)
                        if (f.exists()) f.delete()
                        MediaScannerConnection.scanFile(ctx, arrayOf(f.absolutePath), null, null)
                    } catch (_: Exception) {}
                }

                // 3. Also delete explicitly by filename in Public Download directory
                if (filename.isNotEmpty()) {
                    try {
                        val f = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), filename)
                        if (f.exists()) f.delete()
                        MediaScannerConnection.scanFile(ctx, arrayOf(f.absolutePath), null, null)
                    } catch (_: Exception) {}
                }

                if (id.isNotEmpty()) {
                    emitDownloadStateChange(id, "deleted")
                }
                checkAndUpdateForegroundService(ctx)

                val ret = JSObject().apply { put("success", true) }
                withContext(Dispatchers.Main) {
                    call.resolve(ret)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    call.reject("Failed to delete: ${e.message}")
                }
            }
        }
    }

    @PluginMethod
    fun clearDownloads(call: PluginCall) {
        scope.launch(Dispatchers.IO) {
            try {
                val all = App.instance.database.downloadDao().getAllDownloads()
                for (item in all) {
                    DownloadNotificationHelper.cancel(App.instance, item.id.hashCode())
                }
                App.instance.database.downloadDao().clearFinished()
                val ret = JSObject().apply { put("success", true) }
                call.resolve(ret)
            } catch (e: Exception) {
                call.reject("Failed to clear: ${e.message}")
            }
        }
    }

    fun resolveLocalFile(entity: DownloadEntity): File? {
        // 1. Try cleaned localFilePath from entity
        if (entity.localFilePath.isNotEmpty()) {
            val clean = try {
                if (entity.localFilePath.startsWith("file://")) {
                    Uri.parse(entity.localFilePath).path ?: entity.localFilePath.removePrefix("file://")
                } else {
                    entity.localFilePath
                }
            } catch (_: Exception) { entity.localFilePath }
            val f = File(clean)
            if (f.exists() && f.length() > 0) return f
        }

        val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val appExtDir = App.instance.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        val roots = listOfNotNull(downloadDir, appExtDir)

        // 2. Search category subfolders by entity.filename
        if (entity.filename.isNotEmpty()) {
            val candidateFolders = listOf(
                "CloudDrive Leech/Movies",
                "CloudDrive Leech/Adult",
                "CloudDrive Leech/Apps",
                "CloudDrive Leech/Files",
                "CloudDrive Leech/Music",
                "CloudDrive Leech/Documents",
                "CloudDrive Leech/Torrents",
                "CloudDrive Leech/Media",
                "Movies", "Adult", "Apps", "Files", "Music", "Documents", "Torrents", "Media",
                ""
            )
            for (root in roots) {
                for (sub in candidateFolders) {
                    val dir = if (sub.isNotEmpty()) File(root, sub) else root
                    val candidate = File(dir, entity.filename)
                    if (candidate.exists() && candidate.length() > 0) {
                        return candidate
                    }
                }
            }
        }
        return null
    }

    @PluginMethod
    fun playInAppPlayer(call: PluginCall) {
        val id = call.getString("id") ?: ""
        val url = call.getString("url") ?: ""
        val title = call.getString("title") ?: "Downloaded Video"

        activity?.let { act ->
            scope.launch(Dispatchers.IO) {
                var targetUri = url
                val entity = if (id.isNotEmpty()) App.instance.database.downloadDao().getById(id) else null
                if (entity != null) {
                    val file = resolveLocalFile(entity)
                    if (file != null && file.exists()) {
                        targetUri = file.absolutePath
                    } else if (entity.localFilePath.isNotEmpty()) {
                        val direct = File(entity.localFilePath)
                        if (direct.exists()) targetUri = direct.absolutePath
                    }
                }

                if (targetUri.isEmpty() || (targetUri.startsWith("/") && !File(targetUri).exists())) {
                    call.reject("File not found on device storage")
                    return@launch
                }

                withContext(Dispatchers.Main) {
                    PlayerActivity.start(act, targetUri, title, "", "downloads")
                    call.resolve(JSObject().apply { put("success", true) })
                }
            }
        } ?: run {
            call.reject("Activity context not available")
        }
    }

    @PluginMethod
    fun openFile(call: PluginCall) {
        val id = call.getString("id") ?: ""
        val rawPath = call.getString("path") ?: call.getString("localFilePath") ?: ""
        scope.launch(Dispatchers.IO) {
            try {
                val ctx = context ?: App.instance
                var file: File? = null
                if (id.isNotEmpty()) {
                    val entity = App.instance.database.downloadDao().getById(id)
                    if (entity != null) {
                        file = resolveLocalFile(entity)
                    }
                }
                if ((file == null || !file.exists()) && rawPath.isNotEmpty()) {
                    val clean = if (rawPath.startsWith("file://")) rawPath.substring(7) else rawPath
                    val candidate = File(clean)
                    if (candidate.exists()) {
                        file = candidate
                    }
                }
                if (file != null && file.exists()) {
                    val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
                    val ext = file.extension.lowercase()
                    val mime = when {
                        ext == "apk" -> "application/vnd.android.package-archive"
                        ext in listOf("mp4", "mkv", "webm", "avi", "mov", "flv", "ts") -> "video/*"
                        ext in listOf("mp3", "m4a", "flac", "wav", "aac", "ogg") -> "audio/*"
                        ext in listOf("jpg", "jpeg", "png", "gif", "webp", "bmp") -> "image/*"
                        ext == "pdf" -> "application/pdf"
                        ext in listOf("zip", "rar", "7z", "tar", "gz", "bz2", "xz", "iso") -> "application/zip"
                        ext in listOf("doc", "docx") -> "application/msword"
                        ext in listOf("xls", "xlsx") -> "application/vnd.ms-excel"
                        ext in listOf("ppt", "pptx") -> "application/vnd.ms-powerpoint"
                        ext == "txt" -> "text/plain"
                        else -> MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "*/*"
                    }
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, mime)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    if (ext == "apk") {
                        intent.action = Intent.ACTION_VIEW
                        intent.setDataAndType(uri, "application/vnd.android.package-archive")
                        ctx.startActivity(intent)
                    } else {
                        val chooser = Intent.createChooser(intent, "Open with").apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        ctx.startActivity(chooser)
                    }
                    call.resolve(JSObject().apply { put("success", true) })
                    return@launch
                }
                call.reject("File not found on device storage")
            } catch (e: Exception) {
                call.reject("Failed to open file: ${e.message}")
            }
        }
    }

    @PluginMethod
    fun checkStoragePermission(call: PluginCall) {
        // Scoped Storage: App-created folder has 100% full POSIX access without runtime permission popups
        call.resolve(JSObject().apply { put("granted", true) })
    }

    @PluginMethod
    fun requestStoragePermission(call: PluginCall) {
        call.resolve(JSObject().apply { put("success", true) })
    }

    @PluginMethod
    fun getOfflineGallery(call: PluginCall) {
        scope.launch(Dispatchers.IO) {
            try {
                val dbList = App.instance.database.downloadDao().getAllDownloads()
                val appExtDir = App.instance.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                val filesArr = JSArray()
                val seenPaths = HashSet<String>()
                var totalBytesOnDevice = 0L

                // 1. Process DB entries first
                for (entity in dbList) {
                    val file = resolveLocalFile(entity)

                    if (file != null && file.exists() && file.length() > 0) {
                        seenPaths.add(file.absolutePath)
                        val length = file.length()
                        totalBytesOnDevice += length
                        val mb = length / (1024.0 * 1024.0)
                        val sizeFormatted = if (mb >= 1024) String.format("%.2f GB", mb / 1024.0) else String.format("%.1f MB", mb)

                        var durationFormatted = "HD"
                        var resolutionFormatted = "1080p"

                        try {
                            val retriever = MediaMetadataRetriever()
                            retriever.setDataSource(file.absolutePath)
                            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                            if (durationMs > 0) {
                                val totalSecs = durationMs / 1000
                                val mins = totalSecs / 60
                                val secs = totalSecs % 60
                                val hrs = mins / 60
                                durationFormatted = if (hrs > 0) String.format("%d:%02d:%02d", hrs, mins % 60, secs) else String.format("%d:%02d", mins, secs)
                            }
                            val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
                            val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
                            if (height >= 2160 || width >= 3840) resolutionFormatted = "4K UHD"
                            else if (height >= 1080 || width >= 1920) resolutionFormatted = "1080p"
                            else if (height >= 720 || width >= 1280) resolutionFormatted = "720p"
                            else if (height > 0) resolutionFormatted = "${height}p"
                            retriever.release()
                        } catch (_: Exception) {}

                        val thumbnailBase64 = extractVideoThumbnail(file.absolutePath)

                        var cat = entity.category
                        val u = (entity.url ?: "").lowercase()
                        val n = (entity.title.ifEmpty { file.name }).lowercase()
                        if (cat.isNullOrEmpty() || cat == "media") {
                            cat = when {
                                u.contains("porn") || u.contains("hamster") || u.contains("eporner") || u.contains("xvideos") || u.contains("xnxx") || u.contains("redtube") || u.contains("xhcdn") || u.contains("phncdn") || n.contains("porn") || n.contains("hamster") || n.contains("eporner") || n.contains("xvideos") || n.contains("xnxx") || n.contains("18+") || n.contains("cuckold") || n.contains("jav") || n.contains("waka") || n.contains("hentai") || n.contains("stepmom") || n.contains("step mom") || n.contains("milf") || n.contains("blowjob") || n.contains("creampie") || n.contains("fetish") -> "adult"
                                u.contains("sinhala") || u.contains("baiscope") || u.contains("piratelk") || u.contains("sub") || u.contains("movie") || n.contains("sinhala") || n.contains("baiscope") || n.contains("sub") || n.contains("movie") -> "movies"
                                else -> "media"
                            }
                        }

                        val item = JSObject().apply {
                            put("id", entity.id)
                            put("title", entity.title.ifEmpty { file.name })
                            put("filename", file.name)
                            put("path", file.absolutePath)
                            put("uri", file.absolutePath)
                            put("sizeBytes", length)
                            put("sizeFormatted", sizeFormatted)
                            put("duration", durationFormatted)
                            put("resolution", resolutionFormatted)
                            put("thumbnail", thumbnailBase64)
                            put("category", cat)
                            put("lastModified", file.lastModified())
                            put("createdTimestamp", entity.createdTimestamp)
                        }
                        filesArr.put(item)
                    }
                }

                // 2. Scan APK's own created folder & subdirectories only
                val appFolders = listOfNotNull(
                    appExtDir,
                    File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "CloudDrive Leech").takeIf { it.exists() }
                )

                for (targetDir in appFolders) {
                    if (!targetDir.exists() || !targetDir.isDirectory) continue
                    val allVideoFiles = mutableListOf<File>()

                    fun scanDir(dir: File) {
                        val files = dir.listFiles() ?: return
                        for (f in files) {
                            if (f.isDirectory) {
                                scanDir(f)
                            } else if (f.isFile && (f.name.endsWith(".mp4", ignoreCase = true) ||
                                            f.name.endsWith(".mkv", ignoreCase = true) ||
                                            f.name.endsWith(".webm", ignoreCase = true) ||
                                            f.name.endsWith(".mov", ignoreCase = true))) {
                                allVideoFiles.add(f)
                            }
                        }
                    }
                    scanDir(targetDir)

                    for (f in allVideoFiles) {
                        if (!seenPaths.add(f.absolutePath) || f.length() <= 0) continue
                        val length = f.length()
                        totalBytesOnDevice += length
                        val mb = length / (1024.0 * 1024.0)
                        val sizeFormatted = if (mb >= 1024) String.format("%.2f GB", mb / 1024.0) else String.format("%.1f MB", mb)

                        val parentName = f.parentFile?.name?.lowercase() ?: ""
                        val cat = when {
                            parentName == "adult" || f.name.contains("porn", ignoreCase = true) || f.name.contains("hamster", ignoreCase = true) || f.name.contains("eporner", ignoreCase = true) || f.name.contains("xvideos", ignoreCase = true) || f.name.contains("xnxx", ignoreCase = true) || f.name.contains("waka", ignoreCase = true) || f.name.contains("cuckold", ignoreCase = true) || f.name.contains("jav", ignoreCase = true) || f.name.contains("18+", ignoreCase = true) || f.name.contains("hentai", ignoreCase = true) || f.name.contains("milf", ignoreCase = true) || f.name.contains("blowjob", ignoreCase = true) || f.name.contains("stepmom", ignoreCase = true) || f.name.contains("creampie", ignoreCase = true) -> "adult"
                            parentName == "movies" || f.name.contains("sinhala", ignoreCase = true) || f.name.contains("baiscope", ignoreCase = true) || f.name.contains("sub", ignoreCase = true) || f.name.contains("movie", ignoreCase = true) -> "movies"
                            else -> "media"
                        }

                        var durationFormatted = "HD"
                        var resolutionFormatted = "1080p"

                        try {
                            val retriever = MediaMetadataRetriever()
                            retriever.setDataSource(f.absolutePath)
                            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                            if (durationMs > 0) {
                                val totalSecs = durationMs / 1000
                                val mins = totalSecs / 60
                                val secs = totalSecs % 60
                                val hrs = mins / 60
                                durationFormatted = if (hrs > 0) String.format("%d:%02d:%02d", hrs, mins % 60, secs) else String.format("%d:%02d", mins, secs)
                            }
                            val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
                            val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
                            if (height >= 2160 || width >= 3840) resolutionFormatted = "4K UHD"
                            else if (height >= 1080 || width >= 1920) resolutionFormatted = "1080p"
                            else if (height >= 720 || width >= 1280) resolutionFormatted = "720p"
                            else if (height > 0) resolutionFormatted = "${height}p"
                            retriever.release()
                        } catch (_: Exception) {}

                        val thumbnailBase64 = extractVideoThumbnail(f.absolutePath)

                        val item = JSObject().apply {
                            put("id", f.name.hashCode().toString())
                            put("title", f.name.replace(".mp4", "").replace(".mkv", "").replace("_", " "))
                            put("filename", f.name)
                            put("path", f.absolutePath)
                            put("uri", f.absolutePath)
                            put("sizeBytes", length)
                            put("sizeFormatted", sizeFormatted)
                            put("duration", durationFormatted)
                            put("resolution", resolutionFormatted)
                            put("thumbnail", thumbnailBase64)
                            put("category", cat)
                            put("lastModified", f.lastModified())
                            put("createdTimestamp", f.lastModified())
                        }
                        filesArr.put(item)
                    }
                }

                val totalMb = totalBytesOnDevice / (1024.0 * 1024.0)
                val totalFormatted = if (totalMb >= 1024) String.format("%.2f GB", totalMb / 1024.0) else String.format("%.1f MB", totalMb)

                val ret = JSObject().apply {
                    put("success", true)
                    put("totalBytes", totalBytesOnDevice)
                    put("totalFormatted", totalFormatted)
                    put("permissionDenied", false)
                    put("files", filesArr)
                }
                call.resolve(ret)
            } catch (e: Exception) {
                call.reject("Failed to scan gallery: ${e.message}")
            }
        }
    }

    private fun extractVideoThumbnail(filePath: String): String {
        try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(filePath)
            var frameBitmap: Bitmap? = null
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                frameBitmap = retriever.getScaledFrameAtTime(2000000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 360, 200)
                if (frameBitmap == null) {
                    frameBitmap = retriever.getScaledFrameAtTime(1000000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 360, 200)
                }
                if (frameBitmap == null) {
                    frameBitmap = retriever.getScaledFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 360, 200)
                }
            }
            if (frameBitmap == null) {
                frameBitmap = retriever.getFrameAtTime(2000000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            }
            if (frameBitmap == null) {
                frameBitmap = retriever.getFrameAtTime(1000000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            }
            if (frameBitmap == null) {
                frameBitmap = retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            }
            if (frameBitmap == null) {
                frameBitmap = retriever.frameAtTime
            }
            try {
                retriever.release()
            } catch (_: Exception) {}

            if (frameBitmap == null) {
                @Suppress("DEPRECATION")
                frameBitmap = ThumbnailUtils.createVideoThumbnail(filePath, MediaStore.Images.Thumbnails.MINI_KIND)
            }

            if (frameBitmap != null) {
                val targetWidth = 360
                val targetHeight = (targetWidth.toDouble() * frameBitmap.height / frameBitmap.width).toInt().coerceAtLeast(200)
                val scaled = if (frameBitmap.width != targetWidth || frameBitmap.height != targetHeight) {
                    val s = Bitmap.createScaledBitmap(frameBitmap, targetWidth, targetHeight, true)
                    if (s != frameBitmap) frameBitmap.recycle()
                    s
                } else {
                    frameBitmap
                }
                val stream = ByteArrayOutputStream()
                scaled.compress(Bitmap.CompressFormat.JPEG, 75, stream)
                scaled.recycle()
                return "data:image/jpeg;base64," + Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
            }
        } catch (_: Exception) {}
        return ""
    }

    @PluginMethod
    fun shareDownload(call: PluginCall) {
        val path = call.getString("path") ?: ""
        val title = call.getString("title") ?: "Video"
        if (path.isEmpty()) {
            call.reject("File path is required")
            return
        }

        try {
            val cleanPath = if (path.startsWith("file://")) path.substring(7) else path
            val file = File(cleanPath)
            if (!file.exists()) {
                call.reject("File not found on device storage")
                return
            }

            val ctx = context ?: App.instance
            val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
            val ext = file.extension.ifEmpty { "mp4" }
            val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "video/mp4"

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = mime
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, title)
                putExtra(Intent.EXTRA_TEXT, "Shared from CloudDrive Leech: $title")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            val chooser = Intent.createChooser(shareIntent, "Share Video via").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            ctx.startActivity(chooser)
            call.resolve(JSObject().apply { put("success", true) })
        } catch (e: Exception) {
            call.reject("Failed to share file: ${e.message}")
        }
    }
}
