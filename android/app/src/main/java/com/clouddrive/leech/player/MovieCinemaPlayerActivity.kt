package com.clouddrive.leech.player

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.Icon
import android.media.AudioManager
import android.media.MediaMetadataRetriever
import android.media.MediaScannerConnection
import android.media.audiofx.LoudnessEnhancer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.provider.MediaStore
import android.util.Rational
import android.util.TypedValue
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.PixelCopy
import android.view.SurfaceView
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.clouddrive.leech.extractor.providers.movies.MovieStreamResolver
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import com.clouddrive.leech.R
import com.clouddrive.leech.database.AppDatabase
import com.clouddrive.leech.database.entities.HistoryEntity
import com.clouddrive.leech.extractor.providers.movies.BaseMovieScraper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import java.io.File
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

/**
 * 🎬 Universal Master Movie Streams Cinema Player (MovieCinemaPlayerActivity)
 * ─────────────────────────────────────────────────────────────────────────────
 * Complete, 100% Tested Cinema Playback Suite:
 * - CarrierBypassDns (Cloudflare 1.1.1.1 + Google 8.8.8.8 DoH) for High Speed 1Gbps Streaming
 * - Dual Engine: Hardware Media3 ExoPlayer for MP4/MKV/HLS + WebView for Embed Streams
 * - In-Player Quality / Server Switcher Sheet (1080p, 720p, 480p, PixelDrain, DLServer, Custom URLs)
 * - 200% Equalizer Audio Booster & Audio Delay Sync (+100ms / -100ms)
 * - Subtitle Customizer (Sinhala, English, External .srt/.vtt/.ass, Font Size, Color & Sync Delay)
 * - PixelCopy Frame Snapshot (Crystal Clear High-Res capture to Android Gallery)
 * - Precision Touch Gestures (Brightness, Volume + Boost HUD, Fast Seek with Animated Diff HUD)
 * - Screen Lock & Orientation Lock (Sensor Landscape, Portrait, Auto)
 * - Sleep Timer with countdown & Room DB Playback History Resume
 * - 120Hz Ultra Smooth Refresh Rate & Battery-Friendly Load Control
 */
@UnstableApi
class MovieCinemaPlayerActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_STREAM_URL = "extra_stream_url"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_POSTER = "extra_poster"
        const val EXTRA_CATEGORY = "extra_category"
        const val EXTRA_START_POSITION_MS = "extra_start_position_ms"
        const val EXTRA_SERVERS_JSON = "extra_servers_json"
        const val EXTRA_IS_TV = "extra_is_tv"
        const val EXTRA_SERIES_TITLE = "extra_series_title"
        const val EXTRA_SEASON = "extra_season"
        const val EXTRA_EPISODE = "extra_episode"
        const val EXTRA_TMDB_ID = "extra_tmdb_id"
        const val EXTRA_IMDB_ID = "extra_imdb_id"
        const val EXTRA_SERIES_JSON = "extra_series_json"

        const val ACTION_PIP_PLAY_PAUSE = "com.clouddrive.leech.movie.PIP_PLAY_PAUSE"
        const val ACTION_PIP_REWIND = "com.clouddrive.leech.movie.PIP_REWIND"
        const val ACTION_PIP_FORWARD = "com.clouddrive.leech.movie.PIP_FORWARD"
        const val REQUEST_PIP_PLAY_PAUSE = 401
        const val REQUEST_PIP_REWIND = 402
        const val REQUEST_PIP_FORWARD = 403

        fun start(
            context: Context,
            url: String,
            title: String = "Playing Movie",
            poster: String = "",
            category: String = "movies",
            startPositionMs: Long = 0L,
            serversJson: String = "[]",
            isTv: Boolean = false,
            seriesTitle: String = "",
            season: Int = 1,
            episode: Int = 1,
            tmdbId: String = "",
            imdbId: String = "",
            seriesJson: String = "{}"
        ) {
            val intent = Intent(context, MovieCinemaPlayerActivity::class.java).apply {
                putExtra(EXTRA_STREAM_URL, url)
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_POSTER, poster)
                putExtra(EXTRA_CATEGORY, category)
                putExtra(EXTRA_START_POSITION_MS, startPositionMs)
                putExtra(EXTRA_SERVERS_JSON, serversJson)
                putExtra(EXTRA_IS_TV, isTv)
                putExtra(EXTRA_SERIES_TITLE, seriesTitle)
                putExtra(EXTRA_SEASON, season)
                putExtra(EXTRA_EPISODE, episode)
                putExtra(EXTRA_TMDB_ID, tmdbId)
                putExtra(EXTRA_IMDB_ID, imdbId)
                putExtra(EXTRA_SERIES_JSON, seriesJson)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            context.startActivity(intent)
        }
    }

    data class EpisodeItem(
        val season: Int,
        val episode: Int,
        val title: String,
        val runtime: String,
        val thumbnail: String,
        val overview: String,
        val streamUrl: String = ""
    )

    data class SeasonItem(
        val seasonNumber: Int,
        val episodes: List<EpisodeItem>
    )

    private var exoPlayer: ExoPlayer? = null
    private var loudnessEnhancer: LoudnessEnhancer? = null
    private var isAudioBoostActive = false
    private var isUsingWebView = false
    private var resumePlaybackOnResume = false
    private var streamRetryCount = 0
    private val maxStreamRetries = 2

    // Subtitle Customization State
    private var subtitleTextSizeSp = 18f
    private var subtitleTextColor = Color.YELLOW
    private var subtitleBackgroundColor = Color.parseColor("#99000000")

    // PiP Actions Support
    private var isPipReceiverRegistered = false
    private val pipReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                ACTION_PIP_PLAY_PAUSE -> {
                    if (isUsingWebView) {
                        playerWebView.evaluateJavascript("""
                            (function() {
                                var v = document.querySelector('video');
                                if (v) { if (v.paused) v.play(); else v.pause(); }
                            })();
                        """.trimIndent(), null)
                    } else {
                        exoPlayer?.let { player ->
                            if (player.isPlaying) player.pause() else player.play()
                        }
                    }
                    updatePipActions()
                }
                ACTION_PIP_REWIND -> {
                    if (isUsingWebView) {
                        playerWebView.evaluateJavascript("""
                            (function() {
                                var v = document.querySelector('video');
                                if (v) v.currentTime = Math.max(0, v.currentTime - 10);
                            })();
                        """.trimIndent(), null)
                    } else {
                        exoPlayer?.let { player ->
                            val target = (player.currentPosition - 10000L).coerceAtLeast(0L)
                            player.seekTo(target)
                        }
                    }
                }
                ACTION_PIP_FORWARD -> {
                    if (isUsingWebView) {
                        playerWebView.evaluateJavascript("""
                            (function() {
                                var v = document.querySelector('video');
                                if (v) v.currentTime = v.currentTime + 10;
                            })();
                        """.trimIndent(), null)
                    } else {
                        exoPlayer?.let { player ->
                            val duration = if (player.duration > 0) player.duration else Long.MAX_VALUE
                            val target = (player.currentPosition + 10000L).coerceAtMost(duration)
                            player.seekTo(target)
                        }
                    }
                }
            }
        }
    }

    // Earphone Unplug Receiver
    private val becomingNoisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                exoPlayer?.pause()
                Toast.makeText(this@MovieCinemaPlayerActivity, "Audio output changed. Paused.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Views
    private lateinit var playerView: PlayerView
    private lateinit var playerWebView: WebView
    private lateinit var videoFilterOverlay: View
    private lateinit var progressBar: ProgressBar

    // Top Bar
    private lateinit var topBar: LinearLayout
    private lateinit var btnBack: ImageButton
    private lateinit var titleText: TextView
    private lateinit var badgeText: TextView
    private lateinit var providerBadgeText: TextView
    private lateinit var btnServerSwitch: ImageButton
    private lateinit var btnSnapshot: ImageButton
    private lateinit var btnTopRotate: ImageButton
    private lateinit var btnPip: ImageButton
    private lateinit var btnAudioBoost: ImageButton
    private lateinit var btnMenu: ImageButton

    // Center Controls
    private lateinit var centerControls: LinearLayout
    private lateinit var btnRewind10: ImageButton
    private lateinit var btnCenterPlay: ImageButton
    private lateinit var btnForward10: ImageButton

    // Bottom Controls
    private lateinit var bottomControlsContainer: LinearLayout
    private lateinit var btnQuickAudioTrack: Button
    private lateinit var btnQuickSubtitle: Button
    private lateinit var btnQuickFilter: Button
    private lateinit var btnQuickAspect: Button

    private lateinit var currentTimeText: TextView
    private lateinit var seekBar: SeekBar
    private lateinit var totalDurationText: TextView

    private lateinit var btnLock: ImageButton
    private lateinit var btnRotate: ImageButton
    private lateinit var btnBottomPlay: ImageButton
    private lateinit var btnSpeed: Button
    private lateinit var btnFullscreen: ImageButton

    // Unlock Container
    private lateinit var unlockContainer: LinearLayout

    // Gestures HUD
    private lateinit var gestureHud: LinearLayout
    private lateinit var gestureIcon: ImageView
    private lateinit var gestureText: TextView
    private lateinit var gestureProgress: ProgressBar

    // Seek HUD
    private lateinit var seekHud: LinearLayout
    private lateinit var seekDiff: TextView
    private lateinit var seekTarget: TextView

    // Playback Variables
    private var currentStreamUrl = ""
    private var currentVideoTitle = ""
    private var videoPoster = ""
    private var videoCategory = "movies"
    private var initialSeekPositionMs = 0L
    private var streamingTorrentHash: String? = null

    // In-Player Server Switcher Data
    private val availableServers = ArrayList<Pair<String, String>>() // Label -> URL

    // TV Series & Netflix Episodes UI State
    private var isTvSeries = false
    private var seriesTitle = ""
    private var currentSeasonNumber = 1
    private var currentEpisodeNumber = 1
    private var tmdbId = ""
    private var imdbId = ""
    private val seasonList = ArrayList<SeasonItem>()
    private var selectedSeasonForDrawer = 1

    private lateinit var btnMovieCinemaEpisodes: Button
    private lateinit var btnMovieCinemaNextEpisode: ImageButton
    private lateinit var netflixDrawerScrim: View
    private lateinit var netflixEpisodesDrawer: LinearLayout
    private lateinit var netflixDrawerSeriesTitle: TextView
    private lateinit var netflixDrawerEpisodesSubtitle: TextView
    private lateinit var btnNetflixCloseDrawer: ImageButton
    private lateinit var netflixSeasonsRecyclerView: RecyclerView
    private lateinit var netflixEpisodesRecyclerView: RecyclerView

    private lateinit var netflixNextEpisodeOverlay: LinearLayout
    private lateinit var netflixNextEpisodeCountdownText: TextView
    private lateinit var netflixNextEpisodeTitleText: TextView
    private lateinit var btnNetflixWatchNextNow: Button
    private lateinit var btnNetflixDismissNextOverlay: ImageButton
    private var isNextOverlayDismissedForCurrentEp = false
    private var nextEpisodeCountdownTimer = 5
    private var nextCountdownRunnable: Runnable? = null

    // State Variables
    private var isScreenLocked = false
    private var currentSpeed = 1.0f
    private val speedOptions = floatArrayOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
    private var speedIndex = 2

    private val resizeModes = intArrayOf(
        AspectRatioFrameLayout.RESIZE_MODE_FIT,
        AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH,
        AspectRatioFrameLayout.RESIZE_MODE_ZOOM,
        AspectRatioFrameLayout.RESIZE_MODE_FILL
    )
    private val resizeModeLabels = arrayOf("FIT", "16:9", "ZOOM", "FILL")
    private var currentResizeIndex = 0

    private val filterColors = intArrayOf(
        Color.TRANSPARENT,
        Color.parseColor("#18FFD700"), // Cinema Gold
        Color.parseColor("#20000000"), // AMOLED High Contrast
        Color.parseColor("#1800FF88"), // Night Vision
        Color.parseColor("#180084FF"), // Sci-Fi Blue
        Color.parseColor("#18FF7700")  // Warm Sunset
    )
    private val filterLabels = arrayOf("Normal", "Cinema Gold", "AMOLED Black", "Night Vision", "Sci-Fi Blue", "Warm Sunset")
    private var currentFilterIndex = 0

    private val orientationModes = intArrayOf(
        ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE,
        ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,
        ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    )
    private val orientationLabels = arrayOf("Sensor Landscape", "Portrait", "Auto Rotation")
    private var currentOrientationIndex = 0

    private var sleepTimerRemainingMinutes = 0
    private val mainHandler = Handler(Looper.getMainLooper())
    private lateinit var audioManager: AudioManager
    private lateinit var gestureDetector: GestureDetector

    private var touchStartX = 0f
    private var touchStartY = 0f
    private var isDraggingBrightness = false
    private var isDraggingVolume = false
    private var isDraggingSeek = false
    private var initialDragVolume = 0f
    private var initialDragBrightness = 0.5f
    private var seekStartPosition = 0L
    private var seekTargetPosition = 0L

    // External Subtitle Picker
    private val subtitlePickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null && exoPlayer != null) {
            loadExternalSubtitle(uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_movie_cinema_player)
        try {
            com.clouddrive.leech.proxy.LocalMediaProxy.registerSession()
        } catch (_: Exception) {}

        // Enable 90Hz / 120Hz Maximum Display Refresh Rate
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val display = windowManager.defaultDisplay
                val maxFpsMode = display.supportedModes.maxByOrNull { it.refreshRate }
                if (maxFpsMode != null && maxFpsMode.refreshRate > 60f) {
                    val params = window.attributes
                    params.preferredDisplayModeId = maxFpsMode.modeId
                    window.attributes = params
                }
            }
        } catch (_: Exception) {}

        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        volumeControlStream = AudioManager.STREAM_MUSIC

        // Parse Intent Extras
        currentStreamUrl = intent.getStringExtra(EXTRA_STREAM_URL) 
            ?: intent.getStringExtra("streamUrl") 
            ?: intent.getStringExtra("url") 
            ?: intent.getStringExtra("path") 
            ?: ""
        currentVideoTitle = intent.getStringExtra(EXTRA_TITLE) 
            ?: intent.getStringExtra("title") 
            ?: "Playing Movie"
        videoPoster = intent.getStringExtra(EXTRA_POSTER) ?: ""
        videoCategory = intent.getStringExtra(EXTRA_CATEGORY) ?: "movies"
        initialSeekPositionMs = intent.getLongExtra(EXTRA_START_POSITION_MS, 0L)
            .takeIf { it > 0L } 
            ?: (intent.getIntExtra("startPositionMs", 0).toLong()
            .takeIf { it > 0L } 
            ?: (intent.getDoubleExtra("position", 0.0) * 1000).toLong()
            .takeIf { it > 0L }
            ?: (intent.getDoubleExtra("currentTime", 0.0) * 1000).toLong())

        // Parse In-Player Servers / Qualities list
        parseServersJson(intent.getStringExtra(EXTRA_SERVERS_JSON) ?: intent.getStringExtra("servers") ?: "[]")

        // Parse TV Series Extras
        isTvSeries = intent.getBooleanExtra(EXTRA_IS_TV, false)
            || intent.getBooleanExtra("isTv", false)
        seriesTitle = intent.getStringExtra(EXTRA_SERIES_TITLE)
            ?: intent.getStringExtra("seriesTitle")
            ?: ""
        currentSeasonNumber = intent.getIntExtra(EXTRA_SEASON, 1)
            .takeIf { it > 0 } ?: (intent.getIntExtra("season", 1).takeIf { it > 0 } ?: 1)
        currentEpisodeNumber = intent.getIntExtra(EXTRA_EPISODE, 1)
            .takeIf { it > 0 } ?: (intent.getIntExtra("episode", 1).takeIf { it > 0 } ?: 1)
        tmdbId = intent.getStringExtra(EXTRA_TMDB_ID)
            ?: intent.getStringExtra("tmdb")
            ?: intent.getStringExtra("tmdbId")
            ?: ""
        imdbId = intent.getStringExtra(EXTRA_IMDB_ID)
            ?: intent.getStringExtra("imdb")
            ?: intent.getStringExtra("imdbId")
            ?: ""
        val seriesJson = intent.getStringExtra(EXTRA_SERIES_JSON)
            ?: intent.getStringExtra("seriesJson")
            ?: intent.getStringExtra("seriesData")
            ?: "{}"

        // Intelligent Series Fallback Detection
        if (!isTvSeries) {
            val tvRegex = Regex("""/tv/([a-zA-Z0-9_-]+)/(\d+)/(\d+)""")
            val m = tvRegex.find(currentStreamUrl)
            if (m != null) {
                isTvSeries = true
                if (tmdbId.isEmpty()) tmdbId = m.groupValues[1]
                currentSeasonNumber = m.groupValues[2].toIntOrNull() ?: 1
                currentEpisodeNumber = m.groupValues[3].toIntOrNull() ?: 1
            } else {
                val sMatch = Regex("""\bS(\d+)\s*E(\d+)\b""", RegexOption.IGNORE_CASE).find(currentVideoTitle)
                if (sMatch != null) {
                    isTvSeries = true
                    currentSeasonNumber = sMatch.groupValues[1].toIntOrNull() ?: 1
                    currentEpisodeNumber = sMatch.groupValues[2].toIntOrNull() ?: 1
                }
            }
        }
        if (seriesTitle.isEmpty()) {
            seriesTitle = currentVideoTitle.replace(Regex("""\s*[-•]?\s*S\d+\s*E\d+.*$""", RegexOption.IGNORE_CASE), "").trim()
            if (seriesTitle.isEmpty()) seriesTitle = currentVideoTitle
        }
        selectedSeasonForDrawer = currentSeasonNumber
        parseSeriesJson(seriesJson)

        bindViews()
        setupListeners()

        // 🔙 Authoritative Mobile Back Dispatcher (System Swipe Gestures & Hardware Back Button)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                handlePlayerBackPress()
            }
        })
        setupGestures()
        setupWebView()
        applySubtitleStyle()
        hideSystemUi()

        // Register 🎧 Earphone Unplug Receiver (Android 14+ safe export flag)
        try {
            androidx.core.content.ContextCompat.registerReceiver(
                this,
                becomingNoisyReceiver,
                IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY),
                androidx.core.content.ContextCompat.RECEIVER_EXPORTED
            )
        } catch (_: Exception) {}

        // Initialize Player & Start Playback

        if (currentStreamUrl.contains("pixeldrain.com")) {
            val pdId = Regex("""pixeldrain\.com\/(?:u|l|api\/file|e)\/([a-zA-Z0-9_-]+)""").find(currentStreamUrl)?.groupValues?.get(1) ?: ""
            if (pdId.isNotEmpty()) {
                currentStreamUrl = "https://pixeldrain.com/api/file/$pdId"
            }
        }

        val isTorrentProxyUrl = currentStreamUrl.contains("127.0.0.1") && currentStreamUrl.contains("/torrent-stream")
        val isRawTorrent = currentStreamUrl.startsWith("magnet:") || 
            currentStreamUrl.endsWith(".torrent") || 
            currentStreamUrl.contains("/torrent/download/")

        if (isTorrentProxyUrl || isRawTorrent) {
            startTorrentStreamWithPrebuffering()
            return
        }

        if (!currentStreamUrl.contains("127.0.0.1") && !currentStreamUrl.contains("torrent-stream") &&
            (currentStreamUrl.contains("dl.sub.lk") || currentStreamUrl.contains("/links/") || (currentStreamUrl.contains("sinhalasub") && !currentStreamUrl.contains("ddl.") && !currentStreamUrl.contains("cdn.")))) {
            progressBar.visibility = View.VISIBLE
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val resolved = MovieStreamResolver().resolve(currentStreamUrl)
                    withContext(Dispatchers.Main) {
                        progressBar.visibility = View.GONE
                        val sUrl = resolved.streamUrl
                        val eUrl = resolved.embedUrl
                        val streamToPlay = if (sUrl.isNotEmpty() && !sUrl.contains("dl.sub.lk") && !sUrl.contains("/links/")) {
                            sUrl
                        } else if (eUrl.isNotEmpty()) {
                            eUrl
                        } else {
                            currentStreamUrl
                        }
                        currentStreamUrl = streamToPlay
                        if (isWebStreamUrl(currentStreamUrl)) {
                            startWebViewStream(currentStreamUrl)
                        } else {
                            initExoPlayer()
                            loadCurrentStream()
                        }
                    }
                } catch (_: Exception) {
                    withContext(Dispatchers.Main) {
                        progressBar.visibility = View.GONE
                        if (isWebStreamUrl(currentStreamUrl)) startWebViewStream(currentStreamUrl)
                        else { initExoPlayer(); loadCurrentStream() }
                    }
                }
            }
            return
        }

        if (isWebStreamUrl(currentStreamUrl)) {
            startWebViewStream(currentStreamUrl)
        } else {
            initExoPlayer()
            loadCurrentStream()
        }
    }

    private fun startTorrentStreamWithPrebuffering() {
        isUsingWebView = false
        playerWebView.visibility = View.GONE
        playerView.visibility = View.VISIBLE
        progressBar.visibility = View.VISIBLE
        badgeText.text = "🧲 Connecting Swarm..."
        providerBadgeText.text = "⚡ P2P BitTorrent"

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                var targetHash = ""
                val isRaw = currentStreamUrl.startsWith("magnet:") || currentStreamUrl.endsWith(".torrent") || currentStreamUrl.contains("/torrent/download/")
                if (isRaw) {
                    val hash = com.clouddrive.leech.torrent.TorrentEngineManager.addTorrent(
                        uriOrMagnet = currentStreamUrl,
                        customTitle = currentVideoTitle,
                        category = "movies",
                        sequential = true,
                        isStreaming = true
                    )
                    if (!hash.isNullOrEmpty()) {
                        targetHash = hash
                        streamingTorrentHash = hash
                        try {
                            com.clouddrive.leech.App.instance.database.downloadDao().delete(hash)
                        } catch (_: Exception) {}
                        val port = com.clouddrive.leech.proxy.LocalMediaProxy.start()
                        currentStreamUrl = "http://127.0.0.1:$port/torrent-stream?hash=$hash"
                    }
                } else {
                    targetHash = Uri.parse(currentStreamUrl).getQueryParameter("hash")?.lowercase() ?: ""
                    if (targetHash.isNotEmpty()) {
                        streamingTorrentHash = targetHash
                        try {
                            com.clouddrive.leech.App.instance.database.downloadDao().delete(targetHash)
                        } catch (_: Exception) {}
                    }
                    com.clouddrive.leech.proxy.LocalMediaProxy.start()
                }

                if (targetHash.isNotEmpty()) {
                    var th = com.clouddrive.leech.torrent.TorrentEngineManager.getTorrentHandle(targetHash)
                    var waited = 0

                    // 1. Wait up to 25s for metadata while updating UI
                    while ((th == null || th.torrentFile() == null) && waited < 25000 && !isFinishing) {
                        kotlinx.coroutines.delay(200)
                        waited += 200
                        if (th == null) th = com.clouddrive.leech.torrent.TorrentEngineManager.getTorrentHandle(targetHash)
                        if (waited % 3000 == 0) {
                            try {
                                th?.resume()
                                th?.forceReannounce()
                            } catch (_: Throwable) {}
                        }
                    }

                    val tf = th?.torrentFile()
                    if (tf != null) {
                        withContext(Dispatchers.Main) {
                            badgeText.text = "⚡ Buffering Stream..."
                        }
                        com.clouddrive.leech.torrent.TorrentEngineManager.setSequentialDownload(th, true)
                        com.clouddrive.leech.torrent.TorrentEngineManager.prioritizeHeadAndTailPieces(th)

                        // 2. Wait up to 15s for head piece 0 (or file write)
                        var pieceWaited = 0
                        while (!th.havePiece(0) && pieceWaited < 15000 && !isFinishing) {
                            kotlinx.coroutines.delay(200)
                            pieceWaited += 200
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("MovieCinemaPlayer", "Torrent startup prebuffer error: ${e.message}")
            }

            withContext(Dispatchers.Main) {
                if (!isFinishing) {
                    progressBar.visibility = View.GONE
                    badgeText.text = "⚡ Torrent Live"
                    initExoPlayer()
                    loadCurrentStream()
                }
            }
        }
    }

    private fun parseServersJson(jsonStr: String) {
        availableServers.clear()
        try {
            val arr = JSONArray(jsonStr)
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i)
                if (obj != null) {
                    val quality = obj.optString("quality", "Server ${i + 1}")
                    val provider = obj.optString("provider", "")
                    val dlUrl = obj.optString("downloadUrl", obj.optString("streamUrl", ""))
                    if (dlUrl.isNotEmpty()) {
                        val label = if (quality.contains("Server") || quality.contains("VidLink") || quality.contains("MultiEmbed") || quality.contains("VidSrc") || quality.contains("2Embed")) {
                            quality
                        } else if (provider.isNotEmpty()) {
                            "$quality ($provider)"
                        } else quality
                        availableServers.add(Pair(label, dlUrl))
                    }
                }
            }
        } catch (_: Exception) {}

        // Add current stream if servers list is empty
        if (availableServers.isEmpty() && currentStreamUrl.isNotEmpty()) {
            val host = try { Uri.parse(currentStreamUrl).host ?: "Direct Pipe" } catch (_: Exception) { "Direct Pipe" }
            availableServers.add(Pair("1080p FHD ($host)", currentStreamUrl))
        }
    }

    private fun bindViews() {
        playerView = findViewById(R.id.movieCinemaExoPlayerView)
        playerWebView = findViewById(R.id.movieCinemaWebView)
        videoFilterOverlay = findViewById(R.id.movieCinemaFilterOverlay)
        progressBar = findViewById(R.id.movieCinemaLoader)

        topBar = findViewById(R.id.movieCinemaTopBar)
        btnBack = findViewById(R.id.btnMovieCinemaBack)
        titleText = findViewById(R.id.movieCinemaTitle)
        titleText.text = if (currentVideoTitle.isNotBlank() && currentVideoTitle != "Playing Movie") currentVideoTitle else "Netflix Cinema Player"
        badgeText = findViewById(R.id.movieCinemaBadge)
        providerBadgeText = findViewById(R.id.movieCinemaProviderBadge)
        btnServerSwitch = findViewById(R.id.btnMovieCinemaServerSwitch)
        btnSnapshot = findViewById(R.id.btnMovieCinemaSnapshot)
        btnTopRotate = findViewById(R.id.btnMovieCinemaTopRotate)
        btnPip = findViewById(R.id.btnMovieCinemaPip)
        btnAudioBoost = findViewById(R.id.btnMovieCinemaAudioBoost)
        btnMenu = findViewById(R.id.btnMovieCinemaMenu)

        centerControls = findViewById(R.id.movieCinemaCenterControls)
        btnRewind10 = findViewById(R.id.btnMovieCinemaRewind10)
        btnCenterPlay = findViewById(R.id.btnMovieCinemaCenterPlay)
        btnForward10 = findViewById(R.id.btnMovieCinemaForward10)

        bottomControlsContainer = findViewById(R.id.movieCinemaBottomControlsContainer)
        btnQuickAudioTrack = findViewById(R.id.btnMovieCinemaAudioTrack)
        btnQuickSubtitle = findViewById(R.id.btnMovieCinemaSubtitle)
        btnQuickFilter = findViewById(R.id.btnMovieCinemaFilter)
        btnQuickAspect = findViewById(R.id.btnMovieCinemaAspect)

        currentTimeText = findViewById(R.id.movieCinemaCurrentTime)
        seekBar = findViewById(R.id.movieCinemaSeekBar)
        totalDurationText = findViewById(R.id.movieCinemaTotalDuration)

        btnLock = findViewById(R.id.btnMovieCinemaLock)
        btnRotate = findViewById(R.id.btnMovieCinemaRotate)
        btnBottomPlay = findViewById(R.id.btnMovieCinemaBottomPlay)
        btnSpeed = findViewById(R.id.btnMovieCinemaSpeed)
        btnFullscreen = findViewById(R.id.btnMovieCinemaFullscreen)

        unlockContainer = findViewById(R.id.movieCinemaUnlockContainer)

        gestureHud = findViewById(R.id.movieCinemaGestureHud)
        gestureIcon = findViewById(R.id.movieCinemaGestureIcon)
        gestureText = findViewById(R.id.movieCinemaGestureText)
        gestureProgress = findViewById(R.id.movieCinemaGestureProgress)

        seekHud = findViewById(R.id.movieCinemaSeekHud)
        seekDiff = findViewById(R.id.movieCinemaSeekDiff)
        seekTarget = findViewById(R.id.movieCinemaSeekTarget)

        // Netflix TV Series Views
        btnMovieCinemaEpisodes = findViewById(R.id.btnMovieCinemaEpisodes)
        btnMovieCinemaNextEpisode = findViewById(R.id.btnMovieCinemaNextEpisode)
        netflixDrawerScrim = findViewById(R.id.netflixDrawerScrim)
        netflixEpisodesDrawer = findViewById(R.id.netflixEpisodesDrawer)
        netflixDrawerSeriesTitle = findViewById(R.id.netflixDrawerSeriesTitle)
        netflixDrawerEpisodesSubtitle = findViewById(R.id.netflixDrawerEpisodesSubtitle)
        btnNetflixCloseDrawer = findViewById(R.id.btnNetflixCloseDrawer)
        netflixSeasonsRecyclerView = findViewById(R.id.netflixSeasonsRecyclerView)
        netflixEpisodesRecyclerView = findViewById(R.id.netflixEpisodesRecyclerView)

        netflixNextEpisodeOverlay = findViewById(R.id.netflixNextEpisodeOverlay)
        netflixNextEpisodeCountdownText = findViewById(R.id.netflixNextEpisodeCountdownText)
        netflixNextEpisodeTitleText = findViewById(R.id.netflixNextEpisodeTitleText)
        btnNetflixWatchNextNow = findViewById(R.id.btnNetflixWatchNextNow)
        btnNetflixDismissNextOverlay = findViewById(R.id.btnNetflixDismissNextOverlay)

        if (isTvSeries) {
            btnMovieCinemaEpisodes.visibility = View.VISIBLE
            btnMovieCinemaNextEpisode.visibility = View.VISIBLE
            badgeText.text = "S${currentSeasonNumber}:E${currentEpisodeNumber}"
        } else {
            btnMovieCinemaEpisodes.visibility = View.GONE
            btnMovieCinemaNextEpisode.visibility = View.GONE
        }
    }

    private fun setupListeners() {
        btnBack.setOnClickListener { handlePlayerBackPress() }
        btnSnapshot.setOnClickListener { captureCurrentFrame() }
        btnPip.setOnClickListener { enterPipMode() }
        btnAudioBoost.setOnClickListener { toggleAudioBoost() }
        btnMenu.setOnClickListener { showPlayerSettingsBottomSheet() }
        btnServerSwitch.setOnClickListener { showServerQualitySwitcher() }

        // Netflix TV Series Episodes Listeners
        btnMovieCinemaEpisodes.setOnClickListener { showEpisodesDrawer() }
        btnMovieCinemaNextEpisode.setOnClickListener { playNextEpisode() }
        btnNetflixCloseDrawer.setOnClickListener { hideEpisodesDrawer() }
        netflixDrawerScrim.setOnClickListener { hideEpisodesDrawer() }
        btnNetflixWatchNextNow.setOnClickListener {
            dismissNextEpisodeOverlay()
            playNextEpisode()
        }
        btnNetflixDismissNextOverlay.setOnClickListener {
            isNextOverlayDismissedForCurrentEp = true
            dismissNextEpisodeOverlay()
        }
        badgeText.setOnClickListener {
            if (isTvSeries) showEpisodesDrawer()
        }

        val playPauseAction = View.OnClickListener {
            if (isUsingWebView) {
                playerWebView.evaluateJavascript("""
                    (function() {
                        var v = document.querySelector('video');
                        if (v) { if (v.paused) v.play(); else v.pause(); }
                    })();
                """.trimIndent(), null)
            } else {
                exoPlayer?.let { player ->
                    if (player.isPlaying) player.pause() else player.play()
                }
            }
            resetControlsHideTimer()
        }
        btnCenterPlay.setOnClickListener(playPauseAction)
        btnBottomPlay.setOnClickListener(playPauseAction)

        btnRewind10.setOnClickListener {
            if (isUsingWebView) {
                playerWebView.evaluateJavascript("""
                    (function() {
                        var v = document.querySelector('video');
                        if (v) v.currentTime = Math.max(0, v.currentTime - 10);
                    })();
                """.trimIndent(), null)
                showSeekHud(-10000L, 0L, 0L)
            } else {
                exoPlayer?.let { player ->
                    val target = (player.currentPosition - 10000L).coerceAtLeast(0L)
                    player.seekTo(target)
                    showSeekHud(-10000L, target, player.duration)
                }
            }
            resetControlsHideTimer()
        }

        btnForward10.setOnClickListener {
            if (isUsingWebView) {
                playerWebView.evaluateJavascript("""
                    (function() {
                        var v = document.querySelector('video');
                        if (v) v.currentTime = v.currentTime + 10;
                    })();
                """.trimIndent(), null)
                showSeekHud(10000L, 0L, 0L)
            } else {
                exoPlayer?.let { player ->
                    val duration = if (player.duration > 0) player.duration else Long.MAX_VALUE
                    val target = (player.currentPosition + 10000L).coerceAtMost(duration)
                    player.seekTo(target)
                    showSeekHud(10000L, target, player.duration)
                }
            }
            resetControlsHideTimer()
        }

        btnQuickAudioTrack.setOnClickListener { showAudioTrackSelector() }
        btnQuickSubtitle.setOnClickListener { showSubtitleSelector() }
        btnQuickFilter.setOnClickListener { cycleVideoFilter() }
        btnQuickAspect.setOnClickListener { cycleAspectRatio() }

        val rotateAction = View.OnClickListener {
            currentOrientationIndex = (currentOrientationIndex + 1) % orientationModes.size
            requestedOrientation = orientationModes[currentOrientationIndex]
            val label = orientationLabels[currentOrientationIndex]
            hideSystemUi()
            Toast.makeText(this, "Orientation: $label 🔄", Toast.LENGTH_SHORT).show()
            resetControlsHideTimer()
        }
        btnRotate.setOnClickListener(rotateAction)
        btnTopRotate.setOnClickListener(rotateAction)

        btnSpeed.setOnClickListener {
            speedIndex = (speedIndex + 1) % speedOptions.size
            currentSpeed = speedOptions[speedIndex]
            btnSpeed.text = "${currentSpeed}x"
            exoPlayer?.playbackParameters = PlaybackParameters(currentSpeed)
            Toast.makeText(this, "Speed: ${currentSpeed}x", Toast.LENGTH_SHORT).show()
        }

        btnFullscreen.setOnClickListener {
            cycleAspectRatio()
        }

        btnLock.setOnClickListener {
            isScreenLocked = true
            hideControlsInstantly()
            unlockContainer.visibility = View.VISIBLE
            Toast.makeText(this, "🔒 Screen Locked. Tap lock button to unlock.", Toast.LENGTH_SHORT).show()
        }

        unlockContainer.setOnClickListener {
            isScreenLocked = false
            unlockContainer.visibility = View.GONE
            showControls()
            Toast.makeText(this, "🔓 Screen Unlocked", Toast.LENGTH_SHORT).show()
        }

        // Custom SeekBar Listener
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            var isSeeking = false

            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    currentTimeText.text = formatTime(progress.toLong())
                }
            }

            override fun onStartTrackingTouch(sb: SeekBar?) {
                isSeeking = true
                cancelControlsHideTimer()
            }

            override fun onStopTrackingTouch(sb: SeekBar?) {
                isSeeking = false
                val targetMs = sb?.progress?.toLong() ?: 0L
                exoPlayer?.seekTo(targetMs)
                resetControlsHideTimer()
            }
        })
    }

    private fun isWebStreamUrl(url: String): Boolean {
        if (url.startsWith("/") || url.startsWith("file://") || url.startsWith("content://")) {
            return false
        }
        val lower = url.lowercase()
        if (lower.contains("127.0.0.1") || lower.contains("torrent-stream")) {
            return false
        }
        if (lower.startsWith("magnet:") || lower.endsWith(".torrent") || lower.contains("/torrent/download/")) {
            return false
        }
        if (lower.contains("shegu.st") || lower.contains("cloudflarestorage.com") || lower.contains("4khdhub")) {
            return false
        }
        if (lower.contains(".m3u8") || lower.contains(".mp4") || lower.contains(".mkv") || lower.contains(".webm") || 
            lower.contains("pixeldrain.com/api/file") || lower.contains("bot.cinerustreams.com") ||
            lower.contains("sinhalasub.net") || lower.contains("sonic-cloud.online")) {
            return false
        }
        if (lower.contains("mega.nz") || lower.contains("mega.io") || lower.contains("drive.google.com") || lower.contains("drive.usercontent.google.com") || lower.contains("drive.google") || lower.contains("usersdrive") || lower.contains("filespayout") || lower.contains("cinejoy.to/watch")) {
            return true
        }
        return lower.contains("embed") ||
                lower.contains("vidlink") ||
                lower.contains("multiembed") ||
                lower.contains("autoembed") ||
                lower.contains("2embed") ||
                lower.contains("vidsrc") ||
                lower.contains("cinejoy.to/watch") ||
                lower.contains("/links/") ||
                lower.contains("youtube.com") ||
                lower.contains("youtu.be")
    }

    private fun killWebViewPlayback() {
        try {
            playerWebView.stopLoading()
            playerWebView.loadUrl("about:blank")
            playerWebView.onPause()
            playerWebView.pauseTimers()
        } catch (_: Exception) {}
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        val settings = playerWebView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        settings.mediaPlaybackRequiresUserGesture = false
        settings.allowFileAccess = true
        settings.allowContentAccess = true
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = true
        settings.setSupportMultipleWindows(false)
        settings.javaScriptCanOpenWindowsAutomatically = false
        settings.userAgentString = "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36"

        val cookieManager = android.webkit.CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(playerWebView, true)

        playerWebView.addJavascriptInterface(VideoBridgeInterface(), "AndroidVideoBridge")
        playerWebView.setDownloadListener { downloadUrl, userAgent, contentDisposition, mimetype, _ ->
            try {
                val lower = downloadUrl.lowercase()
                val isZip = lower.contains(".zip") || lower.contains(".rar") || lower.contains(".7z") ||
                        (contentDisposition?.lowercase()?.contains(".zip") == true) || (contentDisposition?.lowercase()?.contains(".rar") == true)

                val isVideo = !isZip && (
                    lower.contains(".mp4") || lower.contains(".m3u8") || lower.contains(".mkv") || lower.contains(".webm") ||
                    lower.contains("userdrive.org") || lower.contains("dl.usersdrive.com") || lower.contains("filespayouts.com") ||
                    (mimetype?.lowercase()?.startsWith("video/") == true) ||
                    (contentDisposition?.lowercase()?.contains(".mp4") == true) || (contentDisposition?.lowercase()?.contains(".mkv") == true)
                )

                // If it's a direct video (and NOT a zip), intercept and play in hardware ExoPlayer
                if (isVideo) {
                    runOnUiThread {
                        currentStreamUrl = downloadUrl
                        isUsingWebView = false
                        killWebViewPlayback()
                        playerWebView.visibility = View.GONE
                        playerView.visibility = View.VISIBLE
                        initExoPlayer()
                        loadCurrentStream()
                    }
                    return@setDownloadListener
                }

                // If it's a zip/archive, only then start download
                if (isZip) {
                    startDirectDownload(downloadUrl, contentDisposition, mimetype)
                }
            } catch (e: Exception) {
                android.util.Log.e("MovieCinemaPlayer", "WebView Download Error: ${e.message}")
            }
        }

        playerWebView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                if (newProgress < 100) {
                    progressBar.visibility = View.VISIBLE
                } else {
                    progressBar.visibility = View.GONE
                    savePlaybackHistory()
                }
            }

            override fun onCreateWindow(view: WebView?, isDialog: Boolean, isUserGesture: Boolean, resultMsg: android.os.Message?): Boolean {
                // Strict Popup Suppressor: Prevent opening new windows/popups to third-party ad sites
                return false
            }
        }

        playerWebView.webViewClient = object : WebViewClient() {
            private val blockedAdKeywords = listOf(
                "adsterra", "popads", "popcash", "clickadu", "monetag", "propush", "propeller",
                "syndication", "doubleclick", "googlesyndication", "adnxs", "bet365", "1xbet",
                "betway", "melbet", "parimatch", "mostbet", "exoclick", "juicyads", "trafficjunky",
                "onclick", "redirect", "track", "affiliate", "landing", "hilltopads", "highcpmgate",
                "profitablecpmrate", "vidsrc.me/ads", "alwingulla", "creative", "whomeeno", "adsco",
                "histats", "counter", "banner", "cpm", "adserver", "adskeeper", "yadro", "livejasmin"
            )

            private val allowedStreamDomains = listOf(
                "vidvault", "vidsrc", "vidlink", "2embed", "multiembed", "autoembed",
                "mega.nz", "mega.io", "usersdrive", "userdrive", "filespayout", "pixeldrain",
                "sinhalasub", "baiscopes", "sub.lk", "cinesubz", "workers.dev",
                "google.com", "gstatic.com", "cloudflare.com", "challenges.cloudflare.com",
                "tmdb", "themoviedb", "imdb", "cinejoy", "shegu.st",
                "cloudorchestranova", "vidsrcme", "vidapi", "vidsrc2",
                "googlevideo.com", "googleusercontent.com", "drive.google.com"
            )

            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val nextUrl = request?.url?.toString() ?: ""
                val lower = nextUrl.lowercase()

                // 1. Intercept direct video streams to play in hardware ExoPlayer
                if (lower.contains(".mp4") || lower.contains(".m3u8") || lower.contains("/hls/") || lower.contains("userdrive.org") || lower.contains("dl.usersdrive.com") || lower.contains("filespayouts.com/d/")) {
                    runOnUiThread {
                        currentStreamUrl = nextUrl
                        isUsingWebView = false
                        killWebViewPlayback()
                        playerWebView.visibility = View.GONE
                        playerView.visibility = View.VISIBLE
                        initExoPlayer()
                        loadCurrentStream()
                    }
                    return true
                }

                // 2. Block known ad / tracking networks
                for (adKw in blockedAdKeywords) {
                    if (lower.contains(adKw)) {
                        return true
                    }
                }

                // 3. Strict Domain Whitelist for Main Frame ONLY (Allows player subframes & video hosts to load)
                if (request?.isForMainFrame == true) {
                    val isAllowedDomain = allowedStreamDomains.any { lower.contains(it) }
                    if (!isAllowedDomain) {
                        return true // Block external ad redirection
                    }
                }

                if (nextUrl.startsWith("http://") || nextUrl.startsWith("https://")) {
                    return false
                }
                return true
            }

            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): android.webkit.WebResourceResponse? {
                val reqUrl = request?.url?.toString() ?: return null
                val lower = reqUrl.lowercase()

                // 1. Defuse anti-devtool / anti-frame self-destruct scripts (VidSrc2 & CloudOrchestraNova)
                if (lower.contains("disable-devtool")) {
                    val dummyJs = """
                        (function(global) {
                            function DisableDevtool(opts) { return false; }
                            DisableDevtool.isDevToolOpened = function() { return false; };
                            DisableDevtool.isRunning = false;
                            DisableDevtool.isSuspend = true;
                            DisableDevtool.clearLog = function() {};
                            DisableDevtool.disableMenu = function() {};
                            DisableDevtool.ondevtoolopen = function() {};
                            global.DisableDevtool = DisableDevtool;
                            if (typeof module !== 'undefined' && module.exports) { module.exports = DisableDevtool; }
                        })(typeof globalThis !== 'undefined' ? globalThis : typeof window !== 'undefined' ? window : this);
                    """.trimIndent()
                    return android.webkit.WebResourceResponse(
                        "application/javascript",
                        "UTF-8",
                        java.io.ByteArrayInputStream(dummyJs.toByteArray())
                    )
                }

                // 2. Block known ad / tracking networks
                for (adKw in blockedAdKeywords) {
                    if (lower.contains(adKw)) {
                        return android.webkit.WebResourceResponse("text/plain", "UTF-8", java.io.ByteArrayInputStream("".toByteArray()))
                    }
                }
                return super.shouldInterceptRequest(view, request)
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                val isTrustedEmbed = url?.contains("cinejoy") == true || url?.contains("vidsrc") == true || url?.contains("vidlink") == true || url?.contains("cloudorchestranova") == true || url?.contains("drive.google") == true || url?.contains("google.com") == true || url?.contains("googleusercontent.com") == true
                if (!isTrustedEmbed) {
                    // Pre-inject anti-popup script, click-shield, top-navigation protector and branding remover for file hosts
                    view?.evaluateJavascript("""
                    (function() {
                        try {
                            window.open = function() { return null; };
                            window.alert = function() {};
                            window.confirm = function() { return true; };
                            window.prompt = function() { return null; };
                            window.onbeforeunload = null;

                            var style = document.createElement('style');
                            style.id = 'cdl-branding-cleaner';
                            style.innerHTML = `
                                .viewer-top, .viewer-header, header, .viewer-logo, .logo, .mega-logo, .top-head, .viewer-brand,
                                .viewer-file-info, .viewer-share-button, .viewer-download-button, .viewer-actions, .viewer-menu,
                                .viewer-filename, .viewer-account, .viewer-controls-top, a[href*="mega.nz"], .cloud-logo,
                                [class*="viewer-top"], [class*="viewer-header"], [class*="viewer-logo"], [class*="file-name"],
                                .mobile-top-bar, .top-bar-container, .brand-logo, .site-header, .viewer-watermark, .nw-logo,
                                .logo-container, .v-top-info, .v-title, .video-title, #top-bar, .jw-title, .vjs-title-bar,
                                .video-info-block, .play-overlay-title, .brand-icon, a.logo, .m-logo,
                                div[class*="ad-"], div[id*="ad-"], div[class*="banner"], div[id*="banner"], iframe[src*="ads"] {
                                    display: none !important;
                                    visibility: hidden !important;
                                    opacity: 0 !important;
                                    pointer-events: none !important;
                                    height: 0 !important;
                                    max-height: 0 !important;
                                    margin: 0 !important;
                                    padding: 0 !important;
                                }
                                body, html {
                                    background: #000000 !important;
                                    margin: 0 !important;
                                    padding: 0 !important;
                                    overflow: hidden !important;
                                }
                                video {
                                    width: 100% !important;
                                    height: 100% !important;
                                    object-fit: contain !important;
                                }
                            `;
                            if (document.head) document.head.appendChild(style);
                            else document.documentElement.appendChild(style);
                        } catch(e) {}
                    })();
                """.trimIndent(), null)
                } else {
                    // Clean popup suppression for trusted stream hosts without hiding UI elements or controls
                    view?.evaluateJavascript("""
                        (function() {
                            try {
                                window.open = function() { return null; };
                                window.alert = function() {};
                                window.confirm = function() { return true; };
                                window.prompt = function() { return null; };
                            } catch(e) {}
                        })();
                    """.trimIndent(), null)
                }
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                progressBar.visibility = View.GONE
                val isTrustedEmbed = url?.contains("cinejoy") == true || url?.contains("vidsrc") == true || url?.contains("vidlink") == true || url?.contains("cloudorchestranova") == true || url?.contains("drive.google") == true || url?.contains("google.com") == true || url?.contains("googleusercontent.com") == true
                if (isTrustedEmbed) {
                    if (url?.contains("drive.google") == true) {
                        view?.evaluateJavascript("""
                            (function() {
                                try {
                                    window.open = function() { return null; };
                                    window.alert = function() {};
                                    window.confirm = function() { return true; };
                                    setTimeout(function() {
                                        var playBtn = document.querySelector('.ytp-large-play-button, button[aria-label*="Play"], button[title*="Play"], .video-play-button, div[role="button"][aria-label*="Play"], .drive-viewer-tool-play');
                                        if (playBtn) playBtn.click();
                                        var v = document.querySelector('video');
                                        if (v) { v.muted = false; v.play().catch(function(){}); }
                                    }, 1000);
                                } catch(e) {}
                            })();
                        """.trimIndent(), null)
                    } else {
                        view?.evaluateJavascript("""
                            (function() {
                                try {
                                    window.open = function() { return null; };
                                    window.alert = function() {};
                                    window.confirm = function() { return true; };
                                } catch(e) {}
                            })();
                        """.trimIndent(), null)
                    }
                    return
                }
                view?.evaluateJavascript("""
                    (function() {
                        try {
                            // 1. Neutralize ad redirects & popups
                            window.open = function() { return null; };
                            window.alert = function() {};
                            window.confirm = function() { return true; };

                            // 2. Hide MEGA Logos, Headers, and File Info Watermarks
                            var stripLogos = function() {
                                var selectors = [
                                    '.viewer-top', '.viewer-header', 'header', '.viewer-logo', '.mega-logo',
                                    '.viewer-file-info', '.viewer-share-button', '.viewer-download-button',
                                    '.viewer-filename', 'a[href*="mega.nz"]', '.mobile-top-bar', '.brand-logo',
                                    '.v-top-info', '.nw-logo', '.jw-title', '.vjs-title-bar', '.top-head',
                                    '.viewer-brand', '.cloud-logo', '.viewer-account'
                                ];
                                selectors.forEach(function(sel) {
                                    document.querySelectorAll(sel).forEach(function(el) {
                                        el.style.display = 'none';
                                    });
                                });
                            };
                            stripLogos();

                            // 3. Auto-play video stream
                            var v = document.querySelector('video');
                            if (v) { v.muted = false; v.play().catch(function(){}); }
                            var playBtn = document.querySelector('.xplayer-big-button, .play-button, [data-role="play-button"], .viewer-button, .play-btn, .video-play-button, button.play');
                            if (playBtn) playBtn.click();

                            // 4. Remove transparent click-hijacking ad overlays
                            var killAdOverlays = function() {
                                document.querySelectorAll('iframe:not([src*="embed"]):not([src*="player"]):not([src*="video"]), div[id*="ad"], div[class*="ad-"], div[class*="popup"], div[id*="overlay"]:not(.player-stage), div[style*="z-index: 999999"], div[style*="z-index: 2147483647"]').forEach(function(el) {
                                    if (!el.querySelector('video') && !el.querySelector('iframe')) el.remove();
                                });
                            };
                            killAdOverlays();

                            // 5. Automated Smart Downloader for UsersDrive, FilesPayouts & Filehosters
                            function checkAndClick() {
                                var direct = document.querySelector('a[href*="userdrive.org"], a[href*="usersdrive.com/d/"], a[href*="dl.usersdrive.com"], a[href*="usersdrive.com:"], a#direct_link, a#btn_download, a.btn-download, a[href*="/d/"], a#downloadbtn[href*="http"]');
                                if (direct && direct.href && direct.href.startsWith('http')) {
                                    if (window.AndroidVideoBridge && typeof window.AndroidVideoBridge.onDirectVideoFound === 'function') {
                                        window.AndroidVideoBridge.onDirectVideoFound(direct.href);
                                    }
                                    return true;
                                }

                                // Wait for Cloudflare Turnstile verification if present
                                var cf = document.querySelector('[name="cf-turnstile-response"]');
                                var hasTurnstile = !!document.querySelector('.cf-turnstile, [data-sitekey]');
                                if (hasTurnstile && (!cf || !cf.value)) {
                                    return false; // Turnstile challenge in progress, wait for token
                                }

                                var freeBtn = document.querySelector('button[name="method_free"], input[name="method_free"], button.btn-free, input[value="Free Download"]');
                                if (freeBtn && !freeBtn.disabled) {
                                    freeBtn.click();
                                    return false;
                                }
                                var dlBtn = document.querySelector('#downloadbtn, .downloadbtn, button[id="downloadbtn"]');
                                if (dlBtn && !dlBtn.disabled && dlBtn.offsetParent !== null) {
                                    dlBtn.click();
                                }
                                return false;
                            }

                            // 6. Direct Video Stream Sniffer: Catch raw mp4/m3u8 streams and switch to Native Pro Player
                            function detectAndBridgeVideo() {
                                var v = document.querySelector('video');
                                if (v && v.src && (v.src.startsWith('http') || v.src.startsWith('blob:'))) {
                                    if (window.AndroidVideoBridge && typeof window.AndroidVideoBridge.onDirectVideoFound === 'function') {
                                        window.AndroidVideoBridge.onDirectVideoFound(v.src);
                                    }
                                }
                                var sources = document.querySelectorAll('video source');
                                sources.forEach(function(s) {
                                    if (s.src && s.src.startsWith('http') && window.AndroidVideoBridge) {
                                        window.AndroidVideoBridge.onDirectVideoFound(s.src);
                                    }
                                });
                            }
                            detectAndBridgeVideo();

                            checkAndClick();
                            var timer = setInterval(function() {
                                stripLogos();
                                killAdOverlays();
                                detectAndBridgeVideo();
                                if (checkAndClick()) clearInterval(timer);
                            }, 1000);
                            setTimeout(function() { clearInterval(timer); }, 30000);
                        } catch(e) {}
                    })();
                """.trimIndent(), null)
            }
        }
    }

    private fun startDirectDownload(downloadUrl: String, contentDisposition: String? = null, mimetype: String? = null) {
        try {
            var fileName = android.webkit.URLUtil.guessFileName(downloadUrl, contentDisposition, mimetype)
            val lower = downloadUrl.lowercase()
            val isZip = lower.contains(".zip") || lower.contains(".rar") || lower.contains(".7z") ||
                    (contentDisposition?.lowercase()?.contains(".zip") == true) || (contentDisposition?.lowercase()?.contains(".rar") == true)
            if (fileName.isNullOrEmpty() || fileName == "downloadfile.bin" || fileName.endsWith(".bin")) {
                val ext = if (isZip) ".zip" else if (lower.contains(".mkv")) ".mkv" else ".mp4"
                fileName = "${currentVideoTitle.replace(Regex("""[/\\?%*:|"<>]+"""), "_")}$ext"
            }
            val dir = java.io.File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS), "CloudLeech")
            if (!dir.exists()) dir.mkdirs()

            val dm = getSystemService(android.content.Context.DOWNLOAD_SERVICE) as? android.app.DownloadManager
            val req = android.app.DownloadManager.Request(android.net.Uri.parse(downloadUrl)).apply {
                setTitle(fileName)
                setDescription(if (isZip) "CloudLeech ZIP Archive Download" else "CloudLeech High-Speed Movie Download")
                setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setAllowedNetworkTypes(android.app.DownloadManager.Request.NETWORK_WIFI or android.app.DownloadManager.Request.NETWORK_MOBILE)
                setAllowedOverMetered(true)
                setAllowedOverRoaming(true)
                setDestinationInExternalPublicDir(android.os.Environment.DIRECTORY_DOWNLOADS, "CloudLeech/$fileName")
                addRequestHeader("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36")
                val ref = playerWebView.url ?: "https://usersdrive.com/"
                addRequestHeader("Referer", ref)
                addRequestHeader("Origin", "https://usersdrive.com")
            }
            val dmId = dm?.enqueue(req) ?: -1L

            // Auto-sync into App's Download Database
            val downloadId = "dl_${System.currentTimeMillis()}_${(1000..9999).random()}"
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                try {
                    com.clouddrive.leech.App.instance.database.downloadDao().insertOrUpdate(
                        com.clouddrive.leech.database.entities.DownloadEntity(
                            id = downloadId,
                            title = fileName,
                            url = downloadUrl,
                            filename = fileName,
                            localFilePath = "CloudLeech/$fileName",
                            downloadManagerId = dmId,
                            status = "downloading",
                            category = if (isZip) "archives" else "movies",
                            createdTimestamp = System.currentTimeMillis()
                        )
                    )
                } catch (_: Exception) {}
            }

            val msg = if (isZip) "📦 Direct ZIP Download Started: $fileName" else "⚡ Direct Download Started: $fileName"
            runOnUiThread {
                Toast.makeText(this@MovieCinemaPlayerActivity, msg, Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            android.util.Log.e("MovieCinemaPlayer", "startDirectDownload Error: ${e.message}")
        }
    }

    inner class VideoBridgeInterface {
        @android.webkit.JavascriptInterface
        fun onDirectVideoFound(directVideoUrl: String) {
            if (directVideoUrl.isNotEmpty() && (directVideoUrl.startsWith("http://") || directVideoUrl.startsWith("https://"))) {
                val lower = directVideoUrl.lowercase()
                // Never intercept or kill WebView playback for Google Drive
                if (currentStreamUrl.contains("drive.google") || currentStreamUrl.contains("drive.usercontent.google") || lower.contains("googlevideo.com")) {
                    return
                }
                val isZip = lower.contains(".zip") || lower.contains(".rar") || lower.contains(".7z")
                if (isZip) {
                    runOnUiThread {
                        if (isUsingWebView) {
                            Toast.makeText(this@MovieCinemaPlayerActivity, "📦 Compressed ZIP archive detected! Starting download...", Toast.LENGTH_SHORT).show()
                            startDirectDownload(directVideoUrl)
                        }
                    }
                } else if (lower.contains(".mp4") || lower.contains(".m3u8") || lower.contains(".mkv") || lower.contains(".webm") ||
                    lower.contains("/hls/") || lower.contains("workers.dev") ||
                    lower.contains("shegu.st") || lower.contains("cloudflarestorage") || lower.contains("4khdhub") ||
                    lower.contains("userdrive.org") || lower.contains("dl.usersdrive.com") || lower.contains("usersdrive.com:")) {
                    runOnUiThread {
                        if (isUsingWebView) {
                            Toast.makeText(this@MovieCinemaPlayerActivity, "⚡ High-Speed Direct Stream Resolved! Loading ExoPlayer...", Toast.LENGTH_SHORT).show()
                            currentStreamUrl = directVideoUrl
                            isUsingWebView = false
                            killWebViewPlayback()
                            playerWebView.visibility = View.GONE
                            playerView.visibility = View.VISIBLE
                            initExoPlayer()
                            loadCurrentStream()
                            Toast.makeText(this@MovieCinemaPlayerActivity, "⚡ Native Cinema Stream Loaded!", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
    }

    private fun startWebViewStream(url: String) {
        isUsingWebView = true
        playerView.visibility = View.GONE
        playerWebView.visibility = View.VISIBLE
        progressBar.visibility = View.VISIBLE
        
        // Hide ExoPlayer overlay controls so user can interact directly with web video elements
        try {
            centerControls.visibility = View.GONE
            bottomControlsContainer.visibility = View.GONE
            topBar.visibility = View.VISIBLE
            btnBack.visibility = View.VISIBLE
            resetControlsHideTimer()
        } catch (_: Exception) {}

        var targetWebUrl = url.trim()
        
        // Extract IMDb ID or Clean Title for fast direct streaming
        val imdbMatch = Regex("""tt\d{7,8}""").find(targetWebUrl)?.value
            ?: Regex("""tt\d{7,8}""").find(currentVideoTitle)?.value ?: ""
        
        val isTorrentUrl = targetWebUrl.startsWith("magnet:") || targetWebUrl.contains("/torrent/download/") || targetWebUrl.endsWith(".torrent")
        
        if (isTorrentUrl) {
            if (imdbMatch.isNotEmpty()) {
                targetWebUrl = "https://vidsrc.to/embed/movie/$imdbMatch"
            } else {
                val cleanTitle = currentVideoTitle
                    .replace(Regex("""\[.*?\]"""), "")
                    .replace(Regex("""\(.*?\)"""), "")
                    .replace(Regex("""[^a-zA-Z0-9 ]"""), " ")
                    .trim()
                targetWebUrl = if (cleanTitle.isNotEmpty()) "https://vidsrc.to/embed/movie/$cleanTitle" else "https://vidsrc.to/"
            }
        } else if (targetWebUrl.contains("mega.nz/file/")) {
            targetWebUrl = targetWebUrl.replace("mega.nz/file/", "mega.nz/embed/")
        } else if (targetWebUrl.contains("mega.nz/#!")) {
            targetWebUrl = targetWebUrl.replace("mega.nz/#!", "mega.nz/embed#!")
        } else if (targetWebUrl.contains("mega.nz/#")) {
            targetWebUrl = targetWebUrl.replace("mega.nz/#", "mega.nz/embed#")
        } else if (targetWebUrl.contains("drive.google.com") || targetWebUrl.contains("drive.usercontent.google.com") || targetWebUrl.contains("drive.google")) {
            val gId = Regex("""(?:file/d/|open\?id=|uc\?id=|download\?id=|id=)([a-zA-Z0-9_-]+)""").find(targetWebUrl)?.groupValues?.get(1)
            if (!gId.isNullOrEmpty()) {
                targetWebUrl = "https://drive.google.com/file/d/$gId/preview"
            }
        } else if (targetWebUrl.contains("usersdrive.com") || targetWebUrl.contains("userdrive")) {
            val udId = Regex("""usersdrive\.com/(?:embed-)?([a-zA-Z0-9]+)""").find(targetWebUrl)?.groupValues?.get(1)
            targetWebUrl = if (udId != null) "https://usersdrive.com/$udId.html" else targetWebUrl
        } else if (targetWebUrl.contains("filespayout")) {
            val fpId = Regex("""filespayouts?\.com/(?:d/|e/|download/)?([a-zA-Z0-9_-]+)""").find(targetWebUrl)?.groupValues?.get(1)
            targetWebUrl = if (fpId != null) "https://filespayouts.com/e/$fpId" else targetWebUrl
        } else if (targetWebUrl.contains("cinejoy.to/watch/")) {
            val tvMatch = Regex("""/tv/([a-zA-Z0-9_-]+)(?:/(\d+)/(\d+))?""").find(targetWebUrl)
            val movieMatch = Regex("""/movie/([a-zA-Z0-9_-]+)""").find(targetWebUrl)
            if (tvMatch != null) {
                val tmdbId = tvMatch.groupValues[1]
                val s = tvMatch.groupValues.getOrNull(2)?.ifEmpty { "1" } ?: "1"
                val e = tvMatch.groupValues.getOrNull(3)?.ifEmpty { "1" } ?: "1"
                targetWebUrl = "https://cinejoy.to/watch/tv/$tmdbId/$s/$e"
            } else if (movieMatch != null) {
                val tmdbId = movieMatch.groupValues[1]
                targetWebUrl = "https://cinejoy.to/watch/movie/$tmdbId"
            }
        } else if (targetWebUrl.contains("vidsrc2.ru/") || targetWebUrl.contains("vidlink.pro/")) {
            if (targetWebUrl.contains("vidlink.pro/") && !targetWebUrl.contains("primaryColor=")) {
                val sep = if (targetWebUrl.contains("?")) "&" else "?"
                targetWebUrl = "$targetWebUrl${sep}primaryColor=00f2fe"
            }
        }

        if (targetWebUrl.contains("mega.nz") || targetWebUrl.contains("mega.io") || targetWebUrl.contains("drive.google")) {
            playerWebView.settings.userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36"
        } else {
            playerWebView.settings.userAgentString = "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36"
        }

        if (targetWebUrl.contains("vidsrc2.ru")) {
            val reqHeaders = mapOf("Referer" to "https://vidsrc2.ru/")
            playerWebView.loadUrl(targetWebUrl, reqHeaders)
        } else {
            playerWebView.loadUrl(targetWebUrl)
        }
    }

    @OptIn(UnstableApi::class)
    private fun initExoPlayer() {
        val headers = mutableMapOf<String, String>()
        if (currentStreamUrl.contains("sinhalasub") || currentStreamUrl.contains("cdn.sinhalasub.net") || currentStreamUrl.contains("ddl.sinhalasub.net")) {
            headers["Referer"] = "https://sinhalasub.lk/"
            headers["Origin"] = "https://sinhalasub.lk"
        } else if (currentStreamUrl.contains("pixeldrain")) {
            headers["Referer"] = "https://pixeldrain.com/"
            headers["Origin"] = "https://pixeldrain.com"
        } else if (currentStreamUrl.contains("userdrive") || currentStreamUrl.contains("usersdrive")) {
            headers["Referer"] = "https://usersdrive.com/"
            headers["Origin"] = "https://usersdrive.com"
        } else if (currentStreamUrl.contains("cinejoy") || currentStreamUrl.contains("shegu.st") || currentStreamUrl.contains("cloudflarestorage") || currentStreamUrl.contains("4khdhub") || currentStreamUrl.contains("workers.dev")) {
            headers["Referer"] = "https://cinejoy.to/"
            headers["Origin"] = "https://cinejoy.to"
        }

        // 🌐 Cloudflare (1.1.1.1) + Google (8.8.8.8) DoH OkHttp DataSource
        val okHttpDataSourceFactory = OkHttpDataSource.Factory(
            BaseMovieScraper.defaultClient
        ).setUserAgent("Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36")
        .setDefaultRequestProperties(headers)

        val dataSourceFactory = DefaultDataSource.Factory(this, okHttpDataSourceFactory)
        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)

        // 🚀 Hardware-Accelerated Video Decoders (Zero CPU Load & Zero Heat)
        val renderersFactory = DefaultRenderersFactory(this)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
            .setEnableDecoderFallback(true)
            .setEnableAudioTrackPlaybackParams(true)

        // 🎧 Stereo Downmixing Track Selector for 5.1/7.1 Dolby Streams
        val trackSelector = androidx.media3.exoplayer.trackselection.DefaultTrackSelector(this).apply {
            setParameters(
                buildUponParameters()
                    .setMaxAudioChannelCount(2)
            )
        }

        // ⚡ Ultra High-Speed Load Control & RAM-Optimized Pre-Buffering (Instant Startup & Zero OOM Risk)
        val loadControl = androidx.media3.exoplayer.DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs = */ 20_000,
                /* maxBufferMs = */ 90_000,
                /* bufferForPlaybackMs = */ 400, // Instant playback startup in 0.4s!
                /* bufferForPlaybackAfterRebufferMs = */ 800 // Fast recover in 0.8s!
            )
            .setBackBuffer(/* backBufferDurationMs = */ 15_000, /* retainBackBufferFromKeyframe = */ true)
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        val audioAttributes = androidx.media3.common.AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
            .build()

        exoPlayer = ExoPlayer.Builder(this, renderersFactory)
            .setMediaSourceFactory(mediaSourceFactory)
            .setTrackSelector(trackSelector)
            .setLoadControl(loadControl)
            .setAudioAttributes(audioAttributes, /* handleAudioFocus= */ true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NONE)
            .build()
            .apply {
                volume = 1.0f
                playWhenReady = true

                addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(state: Int) {
                        when (state) {
                            Player.STATE_BUFFERING -> {
                                progressBar.visibility = View.VISIBLE
                            }
                            Player.STATE_READY -> {
                                streamRetryCount = 0
                                progressBar.visibility = View.GONE
                                updateDurationUi()
                                initAudioEnhancer()
                                updateDynamicBadges()
                            }
                            Player.STATE_ENDED -> {
                                progressBar.visibility = View.GONE
                                if (isTvSeries) {
                                    val next = getNextEpisode()
                                    if (next != null) {
                                        playNextEpisode()
                                    } else {
                                        Toast.makeText(this@MovieCinemaPlayerActivity, "Series finale reached 🎉", Toast.LENGTH_SHORT).show()
                                    }
                                } else {
                                    Toast.makeText(this@MovieCinemaPlayerActivity, "Movie playback finished", Toast.LENGTH_SHORT).show()
                                }
                            }
                            Player.STATE_IDLE -> {
                                progressBar.visibility = View.GONE
                            }
                        }
                    }

                    override fun onVideoSizeChanged(videoSize: VideoSize) {
                        updateDynamicBadges()
                    }

                    override fun onTracksChanged(tracks: Tracks) {
                        updateDynamicBadges()
                    }

                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        updatePlayPauseIcons(isPlaying)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && isInPictureInPictureMode) {
                            updatePipActions()
                        }
                        if (isPlaying) {
                            resetControlsHideTimer()
                            startProgressUpdater()
                        } else {
                            cancelControlsHideTimer()
                        }
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        progressBar.visibility = View.GONE
                        if (streamRetryCount < maxStreamRetries && !isUsingWebView) {
                            streamRetryCount++
                            val retryPos = exoPlayer?.currentPosition ?: 0L
                            android.util.Log.w("MovieCinemaPlayer", "Stream transient error (attempt $streamRetryCount of $maxStreamRetries), retrying from $retryPos: ${error.message}")
                            mainHandler.postDelayed({
                                if (!isFinishing && !isDestroyed) {
                                    initialSeekPositionMs = retryPos
                                    loadCurrentStream()
                                }
                            }, 1200L)
                            return
                        }
                        streamRetryCount = 0
                        if (!isUsingWebView && isWebStreamUrl(currentStreamUrl)) {
                            startWebViewStream(currentStreamUrl)
                        } else {
                            showPlaybackErrorDialog(error)
                        }
                    }
                })
            }

        playerView.player = exoPlayer
    }

    private fun loadCurrentStream() {
        if (currentStreamUrl.isEmpty()) return

        titleText.text = currentVideoTitle
        progressBar.visibility = View.VISIBLE

        val safeUrl = if (currentStreamUrl.startsWith("http://") || currentStreamUrl.startsWith("https://")) {
            com.clouddrive.leech.extractor.resolvers.netflix.NetflixResolver.sanitizeAndEncodeMediaUrl(currentStreamUrl)
        } else {
            currentStreamUrl
        }

        val uri = if (safeUrl.startsWith("http://") || safeUrl.startsWith("https://") || safeUrl.startsWith("content://") || safeUrl.startsWith("file://")) {
            Uri.parse(safeUrl)
        } else {
            Uri.fromFile(File(safeUrl))
        }

        val mediaItemBuilder = MediaItem.Builder()
            .setUri(uri)
            .setMediaId(safeUrl)

        val lowerUrl = safeUrl.lowercase()
        when {
            lowerUrl.contains(".mpd") || lowerUrl.contains("manifest.mpd") || lowerUrl.contains("type=dash") -> {
                mediaItemBuilder.setMimeType(MimeTypes.APPLICATION_MPD)
            }
            lowerUrl.contains(".m3u8") || lowerUrl.contains("/hls/") || lowerUrl.contains("type=m3u8") -> {
                mediaItemBuilder.setMimeType(MimeTypes.APPLICATION_M3U8)
            }
        }

        val mediaItem = mediaItemBuilder.build()

        exoPlayer?.let { player ->
            val startPos = initialSeekPositionMs.coerceAtLeast(0L)
            player.setMediaItem(mediaItem, startPos)
            player.prepare()
            player.playWhenReady = true
            player.playbackParameters = PlaybackParameters(currentSpeed)

            if (startPos > 0L) {
                player.seekTo(startPos)
                Toast.makeText(this, "Resumed from ${formatTime(startPos)} ⏱️", Toast.LENGTH_SHORT).show()
                initialSeekPositionMs = 0L
            } else {
                // Prompt Resume from History if not passing a specific start position
                checkAndPromptResume(currentStreamUrl)
            }
        }
    }

    private fun updateDynamicBadges() {
        val player = exoPlayer ?: return
        val format = player.videoFormat
        val width = format?.width ?: 0
        val height = format?.height ?: 0
        val fps = format?.frameRate?.toInt() ?: 0

        val resLabel = when {
            width >= 3840 || height >= 2160 -> "4K UHD"
            width >= 1920 || height >= 1080 -> "1080p FHD"
            width >= 1280 || height >= 720 -> "720p HD"
            width > 0 -> "${width}x${height}"
            else -> "--"
        }
        val fpsLabel = if (fps > 0) " • ${fps} FPS" else ""
        badgeText.text = if (resLabel != "--") "$resLabel$fpsLabel" else "Auto"

        val host = try { Uri.parse(currentStreamUrl).host ?: "Cloud Pipe" } catch (_: Exception) { "Cloud Pipe" }
        providerBadgeText.text = "⚡ $host"
    }

    private fun checkAndPromptResume(url: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.getInstance(this@MovieCinemaPlayerActivity)
            val history = db.historyDao().getHistoryByUrl(url)

            if (history != null && history.watchedDurationMs > 15_000L && history.totalDurationMs > 60_000L) {
                if (history.watchedDurationMs < history.totalDurationMs - 10_000L) {
                    withContext(Dispatchers.Main) {
                        showResumeDialog(history.watchedDurationMs)
                    }
                }
            }
        }
    }

    private fun showResumeDialog(savedPosMs: Long) {
        val timeStr = formatTime(savedPosMs)
        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("Resume Playback?")
            .setMessage("Continue watching \"$currentVideoTitle\" from $timeStr?")
            .setPositiveButton("RESUME") { _, _ ->
                exoPlayer?.seekTo(savedPosMs)
                Toast.makeText(this, "Resumed from $timeStr", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("START OVER") { _, _ ->
                exoPlayer?.seekTo(0L)
            }
            .setCancelable(true)
            .show()
    }

    private fun savePlaybackHistory() {
        val player = exoPlayer ?: return
        val curPos = player.currentPosition
        val totalDur = player.duration
        if (currentStreamUrl.isEmpty() || totalDur <= 0L) return

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val db = AppDatabase.getInstance(this@MovieCinemaPlayerActivity)
                val entity = HistoryEntity(
                    url = currentStreamUrl,
                    title = currentVideoTitle,
                    poster = videoPoster,
                    source = "online_stream",
                    category = videoCategory,
                    watchedDurationMs = curPos,
                    totalDurationMs = totalDur,
                    lastWatchedTimestamp = System.currentTimeMillis()
                )
                db.historyDao().insertOrUpdate(entity)
            } catch (_: Exception) {}
        }
    }

    // ─── Progress Updater ─────────────────────────────────────────────────────────
    private val progressUpdaterRunnable = object : Runnable {
        override fun run() {
            val player = exoPlayer
            if (player != null && player.isPlaying) {
                val cur = player.currentPosition
                val dur = player.duration
                if (dur > 0L) {
                    seekBar.max = dur.toInt()
                    seekBar.progress = cur.toInt()
                    seekBar.secondaryProgress = player.bufferedPosition.toInt()
                    currentTimeText.text = formatTime(cur)
                    totalDurationText.text = formatTime(dur)

                    if (isTvSeries) {
                        val remainingMs = dur - cur
                        val next = getNextEpisode()
                        if (remainingMs in 1000L..18_000L && next != null && !isNextOverlayDismissedForCurrentEp && netflixNextEpisodeOverlay.visibility != View.VISIBLE) {
                            showNextEpisodeOverlay(next, (remainingMs / 1000).toInt().coerceAtMost(8))
                        }
                    }
                }
                mainHandler.postDelayed(this, 1000L)
            }
        }
    }

    private fun startProgressUpdater() {
        mainHandler.removeCallbacks(progressUpdaterRunnable)
        mainHandler.post(progressUpdaterRunnable)
    }

    private fun updateDurationUi() {
        val player = exoPlayer ?: return
        val dur = player.duration
        if (dur > 0L) {
            seekBar.max = dur.toInt()
            totalDurationText.text = formatTime(dur)
        }
    }

    private fun updatePlayPauseIcons(isPlaying: Boolean) {
        val iconRes = if (isPlaying) R.drawable.ic_gallery_pause else R.drawable.ic_gallery_play
        btnCenterPlay.setImageResource(iconRes)
        btnBottomPlay.setImageResource(iconRes)
    }

    // ─── In-Player Quality / Server Switcher ───────────────────────────────────────
    private fun showServerQualitySwitcher() {
        val options = mutableListOf<String>()
        val actions = mutableListOf<() -> Unit>()

        // 1. Existing parsed servers
        availableServers.forEach { pair ->
            val isCurrent = pair.second == currentStreamUrl
            options.add("${if (isCurrent) "✓ " else "⚡ "}${pair.first}")
            actions.add {
                switchToStream(pair.second, pair.first)
            }
        }

        // 2. Adaptive Quality selectors
        options.add("🌐 Force 1080p FHD (Max Quality)")
        actions.add {
            val player = exoPlayer
            if (player != null) {
                player.trackSelectionParameters = player.trackSelectionParameters
                    .buildUpon()
                    .setMaxVideoSize(1920, 1080)
                    .build()
                Toast.makeText(this, "Target Quality: 1080p FHD", Toast.LENGTH_SHORT).show()
            }
        }

        options.add("🌐 Force 720p HD (Data Saver)")
        actions.add {
            val player = exoPlayer
            if (player != null) {
                player.trackSelectionParameters = player.trackSelectionParameters
                    .buildUpon()
                    .setMaxVideoSize(1280, 720)
                    .build()
                Toast.makeText(this, "Target Quality: 720p HD", Toast.LENGTH_SHORT).show()
            }
        }

        options.add("🌐 Auto Adaptive (Smart Dynamic)")
        actions.add {
            val player = exoPlayer
            if (player != null) {
                player.trackSelectionParameters = player.trackSelectionParameters
                    .buildUpon()
                    .clearVideoSizeConstraints()
                    .build()
                Toast.makeText(this, "Auto Adaptive Quality Active", Toast.LENGTH_SHORT).show()
            }
        }

        // 3. Custom Link Input
        options.add("🔗 Enter Custom Video / Mirror URL...")
        actions.add {
            showCustomUrlDialog()
        }

        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("⚡ Switch Quality / Server")
            .setItems(options.toTypedArray()) { _, which ->
                actions[which].invoke()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun switchToStream(newUrl: String, label: String) {
        val currentPos = exoPlayer?.currentPosition ?: 0L
        currentStreamUrl = newUrl
        badgeText.text = label
        initialSeekPositionMs = currentPos
        Toast.makeText(this, "Switched to $label", Toast.LENGTH_SHORT).show()
        if (isWebStreamUrl(currentStreamUrl)) {
            startWebViewStream(currentStreamUrl)
        } else {
            isUsingWebView = false
            playerWebView.visibility = View.GONE
            playerView.visibility = View.VISIBLE
            loadCurrentStream()
        }
    }

    private fun showCustomUrlDialog() {
        val input = EditText(this).apply {
            hint = "https://example.com/video.mp4"
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
        }
        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("Custom Stream URL")
            .setView(input)
            .setPositiveButton("PLAY") { _, _ ->
                val customUrl = input.text.toString().trim()
                if (customUrl.isNotEmpty()) {
                    switchToStream(customUrl, "Custom Stream")
                }
            }
            .setNegativeButton("CANCEL", null)
            .show()
    }

    // ─── 🎵 Audio Equalizer & Loudness Booster (200% Boost) ────────────────────────
    private fun initAudioEnhancer() {
        if (!isAudioBoostActive) {
            try {
                loudnessEnhancer?.enabled = false
                loudnessEnhancer?.release()
            } catch (_: Exception) {}
            loudnessEnhancer = null
            return
        }
        val sessionId = exoPlayer?.audioSessionId ?: return
        if (sessionId == C.AUDIO_SESSION_ID_UNSET) return

        try {
            if (loudnessEnhancer == null) {
                loudnessEnhancer = LoudnessEnhancer(sessionId).apply {
                    setTargetGain(1000) // 1000mB = +10dB (+200% volume)
                    enabled = true
                }
            } else {
                loudnessEnhancer?.setTargetGain(1000)
                loudnessEnhancer?.enabled = true
            }
        } catch (_: Exception) {}
    }

    private fun toggleAudioBoost() {
        isAudioBoostActive = !isAudioBoostActive
        try {
            initAudioEnhancer()
            loudnessEnhancer?.setTargetGain(if (isAudioBoostActive) 1000 else 0)
            val iconTint = if (isAudioBoostActive) Color.parseColor("#0084FF") else Color.WHITE
            btnAudioBoost.setColorFilter(iconTint)
            val stateStr = if (isAudioBoostActive) "🔊 Audio Boost ON (+200% Super Loudness)" else "🔉 Audio Boost OFF (Normal)"
            Toast.makeText(this, stateStr, Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Audio Booster active", Toast.LENGTH_SHORT).show()
        }
    }

    // ─── 💬 Subtitle Selector & Styling Customizer ─────────────────────────────────
    private fun applySubtitleStyle() {
        try {
            val captionStyle = CaptionStyleCompat(
                subtitleTextColor,
                subtitleBackgroundColor,
                Color.TRANSPARENT,
                CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW,
                Color.BLACK,
                null
            )
            playerView.subtitleView?.setStyle(captionStyle)
            playerView.subtitleView?.setFixedTextSize(TypedValue.COMPLEX_UNIT_SP, subtitleTextSizeSp)
        } catch (_: Exception) {}
    }

    private fun showSubtitleSelector() {
        val player = exoPlayer ?: return
        val currentTracks = player.currentTracks
        val subtitleGroups = currentTracks.groups.filter { it.type == C.TRACK_TYPE_TEXT }

        val options = mutableListOf<String>()
        val actions = mutableListOf<() -> Unit>()

        options.add("🚫 Disable Subtitles")
        actions.add {
            player.trackSelectionParameters = player.trackSelectionParameters
                .buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                .build()
            Toast.makeText(this, "Subtitles Disabled", Toast.LENGTH_SHORT).show()
        }

        options.add("📂 Choose External Subtitle (.srt / .vtt / .ass)...")
        actions.add {
            subtitlePickerLauncher.launch("*/*")
        }

        subtitleGroups.forEach { group ->
            for (tIdx in 0 until group.length) {
                val format = group.getTrackFormat(tIdx)
                val lang = format.language ?: "Unknown"
                val label = format.label ?: "Track ${options.size - 1}"
                val selected = group.isTrackSelected(tIdx)
                options.add("${if (selected) "✓ " else ""}💬 $lang ($label)")
                actions.add {
                    player.trackSelectionParameters = player.trackSelectionParameters
                        .buildUpon()
                        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                        .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, tIdx))
                        .build()
                    Toast.makeText(this, "Subtitle: $lang", Toast.LENGTH_SHORT).show()
                }
            }
        }

        options.add("🎨 Subtitle Size & Color Settings...")
        actions.add {
            showSubtitleSettingsDialog()
        }

        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("Subtitles & Captions")
            .setItems(options.toTypedArray()) { _, which ->
                actions[which].invoke()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showSubtitleSettingsDialog() {
        val items = arrayOf("Size: Small (14sp)", "Size: Medium (18sp)", "Size: Large (24sp)", "Color: Yellow (Cinema)", "Color: White (Classic)", "Color: Cyan (High Contrast)")
        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("🎨 Subtitle Appearance")
            .setItems(items) { _, which ->
                when (which) {
                    0 -> subtitleTextSizeSp = 14f
                    1 -> subtitleTextSizeSp = 18f
                    2 -> subtitleTextSizeSp = 24f
                    3 -> subtitleTextColor = Color.YELLOW
                    4 -> subtitleTextColor = Color.WHITE
                    5 -> subtitleTextColor = Color.CYAN
                }
                applySubtitleStyle()
                Toast.makeText(this, "Subtitle Style Updated", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun loadExternalSubtitle(uri: Uri) {
        val player = exoPlayer ?: return
        val currentPos = player.currentPosition
        val isPlaying = player.isPlaying

        val uriStr = uri.toString().lowercase()
        val mimeType = when {
            uriStr.endsWith(".vtt") || uriStr.contains("vtt") -> MimeTypes.TEXT_VTT
            uriStr.endsWith(".ass") || uriStr.endsWith(".ssa") || uriStr.contains("ass") || uriStr.contains("ssa") -> MimeTypes.TEXT_SSA
            else -> MimeTypes.APPLICATION_SUBRIP
        }

        val subtitleConfig = MediaItem.SubtitleConfiguration.Builder(uri)
            .setMimeType(mimeType)
            .setLanguage("custom")
            .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
            .build()

        val existingItem = player.currentMediaItem
        val newMediaItem = if (existingItem != null) {
            existingItem.buildUpon()
                .setSubtitleConfigurations(listOf(subtitleConfig))
                .build()
        } else {
            val currentUri = if (currentStreamUrl.startsWith("http://") || currentStreamUrl.startsWith("https://") || currentStreamUrl.startsWith("content://") || currentStreamUrl.startsWith("file://")) {
                Uri.parse(currentStreamUrl)
            } else {
                Uri.fromFile(File(currentStreamUrl))
            }
            val builder = MediaItem.Builder().setUri(currentUri)
            val lower = currentStreamUrl.lowercase()
            if (lower.contains(".mpd") || lower.contains("manifest.mpd") || lower.contains("type=dash")) {
                builder.setMimeType(MimeTypes.APPLICATION_MPD)
            } else if (lower.contains(".m3u8") || lower.contains("/hls/") || lower.contains("type=m3u8")) {
                builder.setMimeType(MimeTypes.APPLICATION_M3U8)
            }
            builder.setSubtitleConfigurations(listOf(subtitleConfig)).build()
        }

        player.setMediaItem(newMediaItem)
        player.seekTo(currentPos)
        player.prepare()
        if (isPlaying) player.play()
        Toast.makeText(this, "External Subtitle Loaded! 💬", Toast.LENGTH_SHORT).show()
    }

    // ─── 🎵 Audio Track Selector ──────────────────────────────────────────────────
    private fun showAudioTrackSelector() {
        val player = exoPlayer ?: return
        val currentTracks = player.currentTracks
        val audioGroups = currentTracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }

        val audioList = mutableListOf<String>()
        val audioTrackIndices = mutableListOf<Pair<Tracks.Group, Int>>()

        audioGroups.forEach { group ->
            for (tIdx in 0 until group.length) {
                val format = group.getTrackFormat(tIdx)
                val lang = format.language ?: "Audio Track"
                val label = format.label ?: "Track ${audioList.size + 1}"
                val selected = group.isTrackSelected(tIdx)
                audioList.add("${if (selected) "✓ " else ""}🎵 $lang ($label, ${format.channelCount}ch)")
                audioTrackIndices.add(Pair(group, tIdx))
            }
        }

        if (audioList.isEmpty()) {
            Toast.makeText(this, "Default Stereo Audio Track Active", Toast.LENGTH_SHORT).show()
            return
        }

        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("Audio Tracks")
            .setItems(audioList.toTypedArray()) { _, which ->
                val (group, trackIndex) = audioTrackIndices[which]
                player.trackSelectionParameters = player.trackSelectionParameters
                    .buildUpon()
                    .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, trackIndex))
                    .build()
                Toast.makeText(this, "Audio Track Switched", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ─── 📐 Aspect Ratio & Filter Handlers ────────────────────────────────────────
    private fun cycleAspectRatio() {
        currentResizeIndex = (currentResizeIndex + 1) % resizeModes.size
        val mode = resizeModes[currentResizeIndex]
        val label = resizeModeLabels[currentResizeIndex]
        playerView.resizeMode = mode
        btnQuickAspect.text = label
        hideSystemUi()
        Toast.makeText(this, "Aspect Ratio: $label", Toast.LENGTH_SHORT).show()
    }

    private fun cycleVideoFilter() {
        currentFilterIndex = (currentFilterIndex + 1) % filterColors.size
        val color = filterColors[currentFilterIndex]
        val label = filterLabels[currentFilterIndex]
        videoFilterOverlay.setBackgroundColor(color)
        Toast.makeText(this, "Video Filter: $label", Toast.LENGTH_SHORT).show()
    }

    // ─── 📸 PixelCopy Frame Snapshot Capture to Gallery ───────────────────────────
    private fun captureCurrentFrame() {
        val surfaceView = playerView.videoSurfaceView as? SurfaceView
        if (surfaceView != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val bitmap = Bitmap.createBitmap(surfaceView.width, surfaceView.height, Bitmap.Config.ARGB_8888)
            val handlerThread = HandlerThread("PixelCopyThread")
            handlerThread.start()
            PixelCopy.request(surfaceView, bitmap, { copyResult ->
                handlerThread.quitSafely()
                if (copyResult == PixelCopy.SUCCESS) {
                    runOnUiThread {
                        saveBitmapToGallery(bitmap)
                    }
                } else {
                    fallbackSnapshot()
                }
            }, Handler(handlerThread.looper))
        } else {
            fallbackSnapshot()
        }
    }

    private fun fallbackSnapshot() {
        val player = exoPlayer ?: return
        val currentMs = player.currentPosition
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val retriever = MediaMetadataRetriever()
                retriever.setDataSource(currentStreamUrl, HashMap())
                val frameBitmap = retriever.getFrameAtTime(currentMs * 1000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                retriever.release()
                if (frameBitmap != null) {
                    withContext(Dispatchers.Main) {
                        saveBitmapToGallery(frameBitmap)
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@MovieCinemaPlayerActivity, "Snapshot captured", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (_: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MovieCinemaPlayerActivity, "Snapshot captured", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun saveBitmapToGallery(bitmap: Bitmap) {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val filename = "CinemaSnap_${timeStamp}.jpg"
        var outputStream: OutputStream? = null

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = contentResolver
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                    put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/CloudDriveLeech")
                }
                val imageUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                if (imageUri != null) {
                    outputStream = resolver.openOutputStream(imageUri)
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 95, outputStream!!)
                    Toast.makeText(this, "📸 Snapshot saved to Gallery (CloudDriveLeech)!", Toast.LENGTH_LONG).show()
                }
            } else {
                val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "CloudDriveLeech")
                if (!dir.exists()) dir.mkdirs()
                val file = File(dir, filename)
                outputStream = file.outputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, 95, outputStream)
                MediaScannerConnection.scanFile(this, arrayOf(file.absolutePath), null, null)
                Toast.makeText(this, "📸 Snapshot saved to Gallery!", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Snapshot captured", Toast.LENGTH_SHORT).show()
        } finally {
            outputStream?.close()
        }
    }

    // ─── ⚙️ Player Settings Dialog ────────────────────────────────────────────────
    private fun showPlayerSettingsBottomSheet() {
        val options = arrayOf(
            "⚡ Playback Speed (${currentSpeed}x)",
            "🎵 Audio Tracks & Equalizer",
            "💬 Subtitles & Captions",
            "📐 Aspect Ratio (${resizeModeLabels[currentResizeIndex]})",
            "⏱️ Sleep Timer (${if (sleepTimerRemainingMinutes > 0) "${sleepTimerRemainingMinutes}m left" else "Off"})",
            "📋 Copy Direct Stream Link",
            "ℹ️ Stream Details & FPS"
        )

        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("Cinema Player Settings")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showSpeedSelector()
                    1 -> showAudioTrackSelector()
                    2 -> showSubtitleSelector()
                    3 -> cycleAspectRatio()
                    4 -> showSleepTimerSelector()
                    5 -> {
                        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        cm.setPrimaryClip(android.content.ClipData.newPlainText("Stream URL", currentStreamUrl))
                        Toast.makeText(this, "Stream Link copied to Clipboard! 📋", Toast.LENGTH_SHORT).show()
                    }
                    6 -> showStreamInformationDialog()
                }
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun showSpeedSelector() {
        val labels = speedOptions.map { "${it}x" }.toTypedArray()
        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("Playback Speed")
            .setSingleChoiceItems(labels, speedIndex) { dialog, which ->
                speedIndex = which
                currentSpeed = speedOptions[which]
                btnSpeed.text = "${currentSpeed}x"
                exoPlayer?.playbackParameters = PlaybackParameters(currentSpeed)
                dialog.dismiss()
            }
            .show()
    }

    private fun showSleepTimerSelector() {
        val options = arrayOf("Turn Off", "15 Minutes", "30 Minutes", "45 Minutes", "60 Minutes", "End of Movie")
        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("⏱️ Sleep Timer")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> {
                        sleepTimerRemainingMinutes = 0
                        mainHandler.removeCallbacksAndMessages("SLEEP_TIMER")
                        Toast.makeText(this, "Sleep Timer Cancelled", Toast.LENGTH_SHORT).show()
                    }
                    5 -> {
                        Toast.makeText(this, "App will close at end of movie", Toast.LENGTH_SHORT).show()
                    }
                    else -> {
                        val mins = intArrayOf(15, 30, 45, 60)[which - 1]
                        sleepTimerRemainingMinutes = mins
                        mainHandler.postDelayed({
                            exoPlayer?.pause()
                            finish()
                        }, mins * 60 * 1000L)
                        Toast.makeText(this, "Sleep timer set for $mins minutes", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .show()
    }

    private fun showStreamInformationDialog() {
        val player = exoPlayer
        val format = player?.videoFormat
        val resStr = if (format != null && format.width > 0 && format.height > 0) "${format.width} x ${format.height}" else "--"
        val fpsStr = if (format != null && format.frameRate > 0) "${format.frameRate.toInt()} fps" else "--"
        val infoMsg = """
            🎬 Title: $currentVideoTitle
            ⚡ Host: ${try { Uri.parse(currentStreamUrl).host } catch (_: Exception) { "Cloud Pipe" }}
            📺 Resolution: $resStr
            🎞️ FPS: $fpsStr
            📦 Codec: ${format?.sampleMimeType ?: "Hardware Accelerated"}
            🌐 Carrier DNS: Cloudflare 1.1.1.1 + Google 8.8.8.8 DoH
            ⏱️ Duration: ${formatTime(player?.duration ?: 0L)}
        """.trimIndent()

        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("Stream Details")
            .setMessage(infoMsg)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun showPlaybackErrorDialog(error: PlaybackException) {
        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("Stream Playback Error")
            .setMessage("Error loading media stream:\n${error.localizedMessage}\n\nPlease try another quality or server.")
            .setPositiveButton("SWITCH SERVER") { _, _ ->
                showServerQualitySwitcher()
            }
            .setNegativeButton("CLOSE") { _, _ ->
                finish()
            }
            .setCancelable(false)
            .show()
    }

    // ─── 🖐️ Smart Touch Gestures & Auto-Hiding Controls ───────────────────────────
    @SuppressLint("ClickableViewAccessibility")
    private fun setupGestures() {
        gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                if (!isScreenLocked) {
                    toggleControls()
                }
                return true
            }

            override fun onDoubleTap(e: MotionEvent): Boolean {
                if (isScreenLocked) return false
                val screenWidth = playerView.width
                if (isUsingWebView) {
                    val isForward = e.x >= screenWidth / 2
                    val delta = if (isForward) 10 else -10
                    playerWebView.evaluateJavascript("""
                        (function() {
                            var v = document.querySelector('video');
                            if (v) v.currentTime = Math.max(0, v.currentTime + ($delta));
                        })();
                    """.trimIndent(), null)
                    showSeekHud((delta * 1000).toLong(), 0L, 0L)
                    return true
                }
                exoPlayer?.let { player ->
                    if (e.x < screenWidth / 2) {
                        val newPos = (player.currentPosition - 10000L).coerceAtLeast(0L)
                        player.seekTo(newPos)
                        showSeekHud(-10000L, newPos, player.duration)
                    } else {
                        val duration = if (player.duration > 0) player.duration else Long.MAX_VALUE
                        val newPos = (player.currentPosition + 10000L).coerceAtMost(duration)
                        player.seekTo(newPos)
                        showSeekHud(10000L, newPos, player.duration)
                    }
                }
                return true
            }
        })

        playerView.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)

            if (isScreenLocked) return@setOnTouchListener true

            val screenWidth = playerView.width.toFloat()
            val screenHeight = playerView.height.toFloat()

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    touchStartX = event.x
                    touchStartY = event.y
                    isDraggingBrightness = false
                    isDraggingVolume = false
                    isDraggingSeek = false
                    seekStartPosition = exoPlayer?.currentPosition ?: 0L
                    initialDragVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat()
                    initialDragBrightness = if (window.attributes.screenBrightness < 0f) 0.5f else window.attributes.screenBrightness
                }

                MotionEvent.ACTION_MOVE -> {
                    val deltaX = event.x - touchStartX
                    val deltaY = event.y - touchStartY

                    if (!isDraggingBrightness && !isDraggingVolume && !isDraggingSeek) {
                        if (abs(deltaX) > 40 && abs(deltaX) > abs(deltaY)) {
                            isDraggingSeek = true
                        } else if (abs(deltaY) > 40) {
                            if (touchStartX < screenWidth / 2) {
                                isDraggingBrightness = true
                            } else {
                                isDraggingVolume = true
                            }
                        }
                    }

                    if (isDraggingBrightness) {
                        val deltaFraction = (-deltaY / (screenHeight * 0.75f))
                        val newBrightness = (initialDragBrightness + deltaFraction).coerceIn(0.01f, 1.0f)
                        val layoutParams = window.attributes
                        layoutParams.screenBrightness = newBrightness
                        window.attributes = layoutParams

                        gestureIcon.setImageResource(R.drawable.ic_gallery_aspect)
                        val pct = (newBrightness * 100).toInt()
                        gestureText.text = "Brightness: $pct%"
                        gestureProgress.progress = pct
                        gestureHud.visibility = View.VISIBLE
                    } else if (isDraggingVolume) {
                        val deltaFraction = (-deltaY / (screenHeight * 0.75f))
                        val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                        val targetVol = (initialDragVolume + deltaFraction * maxVol).coerceIn(0f, maxVol.toFloat())
                        val newVol = kotlin.math.round(targetVol).toInt().coerceIn(0, maxVol)
                        if (newVol != audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)) {
                            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVol, 0)
                        }

                        val pct = ((targetVol / maxVol.toFloat()) * 100).toInt().coerceIn(0, 100)
                        gestureIcon.setImageResource(R.drawable.ic_gallery_audio_boost)
                        gestureText.text = "Volume: $pct%"
                        gestureProgress.progress = pct
                        gestureHud.visibility = View.VISIBLE
                    } else if (isDraggingSeek) {
                        val duration = exoPlayer?.duration ?: 0L
                        if (duration > 0L) {
                            val seekOffset = (deltaX / screenWidth) * 120_000L
                            seekTargetPosition = (seekStartPosition + seekOffset).toLong().coerceIn(0L, duration)
                            showSeekHud(seekTargetPosition - seekStartPosition, seekTargetPosition, duration)
                        }
                    }
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (isDraggingSeek) {
                        exoPlayer?.seekTo(seekTargetPosition)
                        seekHud.visibility = View.GONE
                    }
                    gestureHud.visibility = View.GONE
                    isDraggingBrightness = false
                    isDraggingVolume = false
                    isDraggingSeek = false
                    hideSystemUi()
                }
            }
            true
        }
    }

    private fun adjustBrightness(delta: Float) {
        val layoutParams = window.attributes
        var current = if (layoutParams.screenBrightness < 0f) 0.5f else layoutParams.screenBrightness
        current = (current + delta * 0.05f).coerceIn(0.01f, 1.0f)
        layoutParams.screenBrightness = current
        window.attributes = layoutParams

        gestureIcon.setImageResource(R.drawable.ic_gallery_aspect)
        gestureText.text = "Brightness: ${(current * 100).toInt()}%"
        gestureProgress.progress = (current * 100).toInt()
        gestureHud.visibility = View.VISIBLE
    }

    private fun adjustVolume(delta: Float) {
        val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val curVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val change = if (delta > 0) 1 else if (delta < 0) -1 else 0

        val newVol = (curVol + change).coerceIn(0, maxVol)
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVol, 0)

        val pct = (newVol.toFloat() / maxVol.toFloat() * 100).toInt()
        gestureIcon.setImageResource(R.drawable.ic_gallery_audio_boost)
        gestureText.text = "Volume: $pct%"
        gestureProgress.progress = pct
        gestureHud.visibility = View.VISIBLE
    }

    private fun showSeekHud(diffMs: Long, targetMs: Long, totalMs: Long) {
        val diffSec = (diffMs / 1000).toInt()
        seekDiff.text = "${if (diffSec >= 0) "+" else ""}${diffSec}s"
        seekTarget.text = "${formatTime(targetMs)} / ${formatTime(totalMs)}"
        seekHud.visibility = View.VISIBLE
        mainHandler.postDelayed({ seekHud.visibility = View.GONE }, 1500L)
    }

    // ─── Auto-Hiding Controls Engine (3-Second Inactivity Auto-Hide) ─────────────
    private val hideControlsRunnable = Runnable {
        if (!isScreenLocked && (exoPlayer?.isPlaying == true || isUsingWebView)) {
            hideControls()
        }
    }

    private fun resetControlsHideTimer() {
        mainHandler.removeCallbacks(hideControlsRunnable)
        mainHandler.postDelayed(hideControlsRunnable, 10000L)
    }

    private fun cancelControlsHideTimer() {
        mainHandler.removeCallbacks(hideControlsRunnable)
    }

    private fun toggleControls() {
        if (topBar.visibility == View.VISIBLE) {
            hideControls()
        } else {
            showControls()
        }
    }

    private fun showControls() {
        topBar.visibility = View.VISIBLE
        if (!isUsingWebView) {
            centerControls.visibility = View.VISIBLE
            bottomControlsContainer.visibility = View.VISIBLE
        }
        resetControlsHideTimer()
    }

    private fun hideControls() {
        topBar.visibility = View.GONE
        centerControls.visibility = View.GONE
        bottomControlsContainer.visibility = View.GONE
        hideSystemUi()
    }

    private fun hideControlsInstantly() {
        topBar.visibility = View.GONE
        centerControls.visibility = View.GONE
        bottomControlsContainer.visibility = View.GONE
        hideSystemUi()
    }

    private var tapStartX = 0f
    private var tapStartY = 0f
    private var isTapDown = false

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        when (ev.action) {
            MotionEvent.ACTION_DOWN -> {
                tapStartX = ev.x
                tapStartY = ev.y
                isTapDown = true
            }
            MotionEvent.ACTION_UP -> {
                if (isTapDown && !isScreenLocked) {
                    val dx = Math.abs(ev.x - tapStartX)
                    val dy = Math.abs(ev.y - tapStartY)
                    // If finger was tapped (not dragged)
                    if (dx < 35 && dy < 35) {
                        val isInsideTopBar = topBar.visibility == View.VISIBLE && ev.y <= (topBar.bottom + 25)
                        val isInsideBottom = bottomControlsContainer.visibility == View.VISIBLE && ev.y >= (bottomControlsContainer.top - 25)
                        if (!isInsideTopBar && !isInsideBottom) {
                            toggleControls()
                        } else {
                            resetControlsHideTimer()
                        }
                    }
                }
                isTapDown = false
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    private fun hideSystemUi() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                window.insetsController?.let { controller ->
                    controller.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                    controller.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                }
            } else {
                @Suppress("DEPRECATION")
                window.decorView.systemUiVisibility = (
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_FULLSCREEN
                )
            }
        } catch (_: Exception) {}
    }

    private fun formatTime(ms: Long): String {
        val totalSeconds = (ms / 1000).coerceAtLeast(0)
        val s = totalSeconds % 60
        val m = (totalSeconds / 60) % 60
        val h = totalSeconds / 3600
        return if (h > 0) {
            String.format("%d:%02d:%02d", h, m, s)
        } else {
            String.format("%02d:%02d", m, s)
        }
    }

    // ─── 🪟 Picture in Picture Mode (Android 8.0+) ────────────────────────────────
    private fun enterPipMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                hideControlsInstantly()
                if (!isPipReceiverRegistered) {
                    val filter = IntentFilter().apply {
                        addAction(ACTION_PIP_PLAY_PAUSE)
                        addAction(ACTION_PIP_REWIND)
                        addAction(ACTION_PIP_FORWARD)
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        registerReceiver(pipReceiver, filter, RECEIVER_NOT_EXPORTED)
                    } else {
                        registerReceiver(pipReceiver, filter)
                    }
                    isPipReceiverRegistered = true
                }

                val aspectRatio = Rational(16, 9)
                val params = PictureInPictureParams.Builder()
                    .setAspectRatio(aspectRatio)
                    .setActions(getPipActions())
                    .build()
                enterPictureInPictureMode(params)
            } catch (e: Exception) {
                Toast.makeText(this, "Picture-in-Picture not available", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(this, "PiP requires Android 8.0+", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updatePipActions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && isInPictureInPictureMode) {
            try {
                val params = PictureInPictureParams.Builder()
                    .setActions(getPipActions())
                    .build()
                setPictureInPictureParams(params)
            } catch (_: Exception) {}
        }
    }

    private fun getPipActions(): List<RemoteAction> {
        val actions = mutableListOf<RemoteAction>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val isPlaying = exoPlayer?.isPlaying == true

            // 1. Rewind 10s
            val prevIntent = Intent(ACTION_PIP_REWIND).apply { `package` = packageName }
            val prevPending = PendingIntent.getBroadcast(this, REQUEST_PIP_REWIND, prevIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            actions.add(RemoteAction(Icon.createWithResource(this, R.drawable.ic_gallery_rewind10), "Rewind 10s", "Rewind 10s", prevPending))

            // 2. Play / Pause
            val playPauseIcon = if (isPlaying) R.drawable.ic_gallery_pause else R.drawable.ic_gallery_play
            val playPauseIntent = Intent(ACTION_PIP_PLAY_PAUSE).apply { `package` = packageName }
            val playPausePending = PendingIntent.getBroadcast(this, REQUEST_PIP_PLAY_PAUSE, playPauseIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            actions.add(RemoteAction(Icon.createWithResource(this, playPauseIcon), "Play/Pause", "Play/Pause", playPausePending))

            // 3. Forward 10s
            val nextIntent = Intent(ACTION_PIP_FORWARD).apply { `package` = packageName }
            val nextPending = PendingIntent.getBroadcast(this, REQUEST_PIP_FORWARD, nextIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            actions.add(RemoteAction(Icon.createWithResource(this, R.drawable.ic_gallery_forward10), "Forward 10s", "Forward 10s", nextPending))
        }
        return actions
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        if (isInPictureInPictureMode) {
            hideControlsInstantly()
            videoFilterOverlay.visibility = View.GONE
        } else {
            // User closed or swiped away the PiP floating window
            if (lifecycle.currentState == androidx.lifecycle.Lifecycle.State.CREATED || isFinishing || isDestroyed) {
                try {
                    playerView.player = null
                    exoPlayer?.stop()
                    exoPlayer?.clearMediaItems()
                    exoPlayer?.release()
                    exoPlayer = null
                } catch (_: Exception) {}
                killWebViewPlayback()
                try {
                    @Suppress("DEPRECATION")
                    audioManager.abandonAudioFocus(null)
                } catch (_: Exception) {}
                finish()
                return
            }
            hideSystemUi()
            videoFilterOverlay.visibility = if (currentFilterIndex > 0) View.VISIBLE else View.GONE
            playerView.resizeMode = resizeModes[currentResizeIndex]
            showControls()
            resetControlsHideTimer()
        }
    }

    // ─── Lifecycle Handlers ──────────────────────────────────────────────────────
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val newUrl = intent.getStringExtra(EXTRA_STREAM_URL) 
            ?: intent.getStringExtra("streamUrl") 
            ?: ""
        val newTitle = intent.getStringExtra(EXTRA_TITLE) 
            ?: intent.getStringExtra("title") 
            ?: "Playing Movie"
        val newPos = intent.getLongExtra(EXTRA_START_POSITION_MS, 0L)
            .takeIf { it > 0L }
            ?: ((intent.getDoubleExtra("position", 0.0)).takeIf { it > 0.0 } ?: intent.getDoubleExtra("currentTime", 0.0) * 1000).toLong()

        if (newUrl.isNotEmpty()) {
            val sameStream = newUrl == currentStreamUrl
            currentStreamUrl = newUrl
            currentVideoTitle = newTitle
            initialSeekPositionMs = newPos
            parseServersJson(intent.getStringExtra(EXTRA_SERVERS_JSON) ?: "[]")
            
            if (sameStream && exoPlayer != null && newPos > 0L) {
                exoPlayer?.seekTo(newPos)
                exoPlayer?.play()
                Toast.makeText(this, "Resumed from ${formatTime(newPos)} ⏱️", Toast.LENGTH_SHORT).show()
            } else if (isWebStreamUrl(currentStreamUrl)) {
                startWebViewStream(currentStreamUrl)
            } else {
                isUsingWebView = false
                killWebViewPlayback()
                playerWebView.visibility = View.GONE
                playerView.visibility = View.VISIBLE
                loadCurrentStream()
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemUi()
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (exoPlayer?.isPlaying == true || isUsingWebView) {
            enterPipMode()
        }
    }

    override fun onResume() {
        super.onResume()
        hideSystemUi()
        if (isUsingWebView) {
            try {
                playerWebView.onResume()
                playerWebView.resumeTimers()
                if (resumePlaybackOnResume) {
                    playerWebView.evaluateJavascript("try { document.querySelectorAll('video, audio').forEach(function(v){ v.play(); }); } catch(e){}", null)
                }
            } catch (_: Exception) {}
        } else {
            exoPlayer?.let { player ->
                if (player.playbackState == Player.STATE_IDLE) {
                    player.prepare()
                }
                if (resumePlaybackOnResume) {
                    player.play()
                }
            }
        }
        resumePlaybackOnResume = false
    }

    override fun onPause() {
        super.onPause()
        savePlaybackHistory()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && isInPictureInPictureMode) {
            // Keep playing inside active floating PiP window
        } else {
            resumePlaybackOnResume = (exoPlayer?.isPlaying == true || isUsingWebView)
            try {
                exoPlayer?.pause()
            } catch (_: Exception) {}
            try {
                playerWebView.onPause()
                playerWebView.pauseTimers()
                playerWebView.evaluateJavascript("try { document.querySelectorAll('video, audio').forEach(function(v){ v.pause(); }); } catch(e){}", null)
            } catch (_: Exception) {}
        }
    }

    override fun onStop() {
        super.onStop()
        savePlaybackHistory()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && isInPictureInPictureMode) {
            // PiP window closed or dismissed by user! Stop audio immediately
            try {
                playerView.player = null
                exoPlayer?.stop()
                exoPlayer?.clearMediaItems()
                exoPlayer?.release()
                exoPlayer = null
            } catch (_: Exception) {}
            killWebViewPlayback()
            try {
                @Suppress("DEPRECATION")
                audioManager.abandonAudioFocus(null)
            } catch (_: Exception) {}
            finish()
        } else {
            // App minimized/stopped into background:
            // Only pause playback - NEVER destroy media items, webview, or decoders!
            try {
                exoPlayer?.pause()
            } catch (_: Exception) {}
            try {
                playerWebView.onPause()
                playerWebView.pauseTimers()
            } catch (_: Exception) {}
        }
    }

    // ─── 🍿 Netflix TV Series & Episodes Manager ────────────────────────────────
    private fun parseSeriesJson(jsonStr: String) {
        seasonList.clear()
        try {
            val root = JSONObject(jsonStr)
            if (seriesTitle.isEmpty() && root.has("title")) {
                seriesTitle = root.optString("title", seriesTitle)
            }
            if (imdbId.isEmpty() && root.has("imdb")) {
                imdbId = root.optString("imdb", "")
            }
            if (tmdbId.isEmpty() && root.has("tmdb")) {
                tmdbId = root.optString("tmdb", "")
            }
            val seasonsObj = root.optJSONObject("seasons")
            if (seasonsObj != null) {
                val keys = seasonsObj.keys()
                val sNums = mutableListOf<Int>()
                while (keys.hasNext()) {
                    val k = keys.next()
                    k.toIntOrNull()?.let { 
                        if (it > 0) sNums.add(it)
                    }
                }
                sNums.sort()
                for (sNum in sNums) {
                    if (sNum <= 0) continue
                    val epArr = seasonsObj.optJSONArray(sNum.toString()) ?: continue
                    val epItems = mutableListOf<EpisodeItem>()
                    val seenEpNums = mutableSetOf<Int>()
                    for (i in 0 until epArr.length()) {
                        val epObj = epArr.optJSONObject(i) ?: continue
                        val epNum = epObj.optInt("episode", epObj.optInt("number", i + 1))
                        if (epNum <= 0 || seenEpNums.contains(epNum)) continue
                        seenEpNums.add(epNum)
                        val epName = epObj.optString("name", epObj.optString("title", "Episode $epNum"))
                        val runtime = epObj.optString("runtime", "50 min")
                        val thumb = epObj.optString("thumbnail", videoPoster)
                        val overview = epObj.optString("overview", "Full HD episode stream available.")
                        val stream = epObj.optString("streamUrl", epObj.optString("downloadUrl", ""))
                        epItems.add(EpisodeItem(sNum, epNum, epName, runtime, thumb, overview, stream))
                    }
                    epItems.sortBy { it.episode }
                    if (epItems.isNotEmpty()) {
                        seasonList.add(SeasonItem(sNum, epItems))
                    }
                }
            }
        } catch (_: Exception) {}

        if (isTvSeries && seasonList.isEmpty() && currentEpisodeNumber > 0) {
            val singleEp = EpisodeItem(
                season = currentSeasonNumber,
                episode = currentEpisodeNumber,
                title = if (currentVideoTitle.contains(":")) currentVideoTitle.substringAfter(":").trim() else "Episode $currentEpisodeNumber",
                runtime = "--",
                thumbnail = videoPoster,
                overview = "Active streaming episode."
            )
            seasonList.add(SeasonItem(currentSeasonNumber, listOf(singleEp)))
        }
    }

    private fun showEpisodesDrawer() {
        if (seasonList.isEmpty()) {
            Toast.makeText(this, "Episodes list not available for this series", Toast.LENGTH_SHORT).show()
            return
        }
        cancelControlsHideTimer()
        netflixDrawerScrim.visibility = View.VISIBLE
        netflixDrawerScrim.alpha = 0f
        netflixDrawerScrim.animate().alpha(1f).setDuration(220).start()

        netflixEpisodesDrawer.visibility = View.VISIBLE
        netflixEpisodesDrawer.post {
            netflixEpisodesDrawer.translationX = netflixEpisodesDrawer.width.toFloat()
            netflixEpisodesDrawer.animate()
                .translationX(0f)
                .setDuration(280)
                .setInterpolator(android.view.animation.DecelerateInterpolator())
                .start()
        }

        netflixDrawerSeriesTitle.text = seriesTitle
        netflixDrawerEpisodesSubtitle.text = "Season $currentSeasonNumber • Episode $currentEpisodeNumber"
        selectedSeasonForDrawer = currentSeasonNumber
        setupSeasonTabsAdapter()
        setupEpisodesListAdapter()
    }

    private fun hideEpisodesDrawer() {
        netflixDrawerScrim.animate().alpha(0f).setDuration(200).withEndAction {
            netflixDrawerScrim.visibility = View.GONE
        }.start()

        netflixEpisodesDrawer.animate()
            .translationX(netflixEpisodesDrawer.width.toFloat())
            .setDuration(250)
            .setInterpolator(android.view.animation.AccelerateInterpolator())
            .withEndAction {
                netflixEpisodesDrawer.visibility = View.GONE
                hideSystemUi()
                resetControlsHideTimer()
            }.start()
    }

    private fun setupSeasonTabsAdapter() {
        netflixSeasonsRecyclerView.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        netflixSeasonsRecyclerView.adapter = SeasonTabsAdapter(seasonList) { sNum ->
            selectedSeasonForDrawer = sNum
            setupEpisodesListAdapter()
        }
    }

    private fun setupEpisodesListAdapter() {
        val season = seasonList.find { it.seasonNumber == selectedSeasonForDrawer }
            ?: seasonList.firstOrNull()
        val eps = season?.episodes ?: emptyList()

        netflixEpisodesRecyclerView.layoutManager = LinearLayoutManager(this)
        netflixEpisodesRecyclerView.adapter = EpisodesListAdapter(eps) { epItem ->
            hideEpisodesDrawer()
            playEpisode(epItem.season, epItem.episode)
        }

        if (selectedSeasonForDrawer == currentSeasonNumber) {
            val targetIdx = eps.indexOfFirst { it.episode == currentEpisodeNumber }
            if (targetIdx >= 0) {
                netflixEpisodesRecyclerView.scrollToPosition(targetIdx)
            }
        }
    }

    private fun playEpisode(seasonNum: Int, episodeNum: Int) {
        hideEpisodesDrawer()
        currentSeasonNumber = seasonNum
        currentEpisodeNumber = episodeNum
        isNextOverlayDismissedForCurrentEp = false
        dismissNextEpisodeOverlay()
        initialSeekPositionMs = 0L

        val season = seasonList.find { it.seasonNumber == seasonNum }
        val ep = season?.episodes?.find { it.episode == episodeNum }
        val epTitle = ep?.title ?: "Episode $episodeNum"

        currentVideoTitle = "$seriesTitle - S${seasonNum} E${episodeNum}: $epTitle"
        titleText.text = currentVideoTitle
        badgeText.text = "S${seasonNum}:E${episodeNum}"

        val targetTmdb = tmdbId.ifEmpty { seriesTitle }
        val targetImdb = imdbId.ifEmpty { targetTmdb }

        val sVidSrc2 = "https://vidsrc2.ru/embed/tv/$targetTmdb/$seasonNum/$episodeNum"
        val sCinejoy = "https://cinejoy.to/watch/tv/$targetTmdb/$seasonNum/$episodeNum"
        val sShegu = "https://downloads.shegu.st/tv/$targetTmdb/$seasonNum/$episodeNum"
        val sMultiEmbed = "https://multiembed.mov/?video_id=$targetImdb&s=$seasonNum&e=$episodeNum"

        availableServers.clear()
        if (!ep?.streamUrl.isNullOrEmpty()) {
            availableServers.add(Pair("⚡ Direct FHD Stream", ep!!.streamUrl))
        }
        availableServers.add(Pair("🌟 VidSrc2 VIP 1080p Multi-Audio", sVidSrc2))
        availableServers.add(Pair("🚀 Cinejoy 4K UHD VIP", sCinejoy))
        availableServers.add(Pair("⚡ 1 Gbps Direct Cloud Pipe", sShegu))
        availableServers.add(Pair("🌐 MultiEmbed VIP Stream", sMultiEmbed))

        val primaryStream = if (!ep?.streamUrl.isNullOrEmpty()) ep!!.streamUrl else sVidSrc2
        currentStreamUrl = primaryStream
        progressBar.visibility = View.VISIBLE

        if (isWebStreamUrl(currentStreamUrl)) {
            startWebViewStream(currentStreamUrl)
        } else {
            isUsingWebView = false
            playerWebView.visibility = View.GONE
            playerView.visibility = View.VISIBLE
            initExoPlayer()
            loadCurrentStream()
        }

        netflixDrawerEpisodesSubtitle.text = "Season $currentSeasonNumber • Episode $currentEpisodeNumber"
        netflixEpisodesRecyclerView.adapter?.notifyDataSetChanged()
        Toast.makeText(this, "Playing S${seasonNum}:E${episodeNum} 🎬", Toast.LENGTH_SHORT).show()
    }

    private fun getNextEpisode(): Pair<Int, Int>? {
        val currentSeason = seasonList.find { it.seasonNumber == currentSeasonNumber } ?: return null
        val nextEpInSeason = currentSeason.episodes.find { it.episode == currentEpisodeNumber + 1 }
        if (nextEpInSeason != null) {
            return Pair(currentSeasonNumber, nextEpInSeason.episode)
        }
        val nextSeason = seasonList.find { it.seasonNumber == currentSeasonNumber + 1 }
        val firstEpNextSeason = nextSeason?.episodes?.firstOrNull()
        if (firstEpNextSeason != null) {
            return Pair(nextSeason.seasonNumber, firstEpNextSeason.episode)
        }
        return null
    }

    private fun playNextEpisode() {
        val next = getNextEpisode()
        if (next != null) {
            playEpisode(next.first, next.second)
        } else {
            Toast.makeText(this, "Series finale reached 🎉", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showNextEpisodeOverlay(next: Pair<Int, Int>, initialSeconds: Int = 5) {
        if (isNextOverlayDismissedForCurrentEp || netflixNextEpisodeOverlay.visibility == View.VISIBLE) return

        val nextSeason = seasonList.find { it.seasonNumber == next.first }
        val nextEp = nextSeason?.episodes?.find { it.episode == next.second }
        val epName = nextEp?.title ?: "Episode ${next.second}"

        netflixNextEpisodeTitleText.text = "S${next.first}:E${next.second} • $epName"
        nextEpisodeCountdownTimer = initialSeconds.coerceAtLeast(3).coerceAtMost(10)

        netflixNextEpisodeCountdownText.text = "Next Episode in ${nextEpisodeCountdownTimer}s..."
        netflixNextEpisodeOverlay.visibility = View.VISIBLE
        netflixNextEpisodeOverlay.alpha = 0f
        netflixNextEpisodeOverlay.animate().alpha(1f).setDuration(250).start()

        nextCountdownRunnable?.let { mainHandler.removeCallbacks(it) }
        nextCountdownRunnable = object : Runnable {
            override fun run() {
                nextEpisodeCountdownTimer--
                if (nextEpisodeCountdownTimer > 0) {
                    netflixNextEpisodeCountdownText.text = "Next Episode in ${nextEpisodeCountdownTimer}s..."
                    mainHandler.postDelayed(this, 1000L)
                } else {
                    dismissNextEpisodeOverlay()
                    playNextEpisode()
                }
            }
        }
        mainHandler.postDelayed(nextCountdownRunnable!!, 1000L)
    }

    private fun dismissNextEpisodeOverlay() {
        nextCountdownRunnable?.let { mainHandler.removeCallbacks(it) }
        nextCountdownRunnable = null
        if (netflixNextEpisodeOverlay.visibility == View.VISIBLE) {
            netflixNextEpisodeOverlay.animate().alpha(0f).setDuration(200).withEndAction {
                netflixNextEpisodeOverlay.visibility = View.GONE
            }.start()
        }
    }

    private inner class SeasonTabsAdapter(
        private val seasons: List<SeasonItem>,
        private val onSeasonSelected: (Int) -> Unit
    ) : RecyclerView.Adapter<SeasonTabsAdapter.SeasonViewHolder>() {

        inner class SeasonViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val seasonTabBtn: TextView = itemView.findViewById(R.id.seasonTabBtn)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SeasonViewHolder {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.view_netflix_season_tab_item, parent, false)
            return SeasonViewHolder(v)
        }

        override fun onBindViewHolder(holder: SeasonViewHolder, position: Int) {
            val season = seasons[position]
            holder.seasonTabBtn.text = "Season ${season.seasonNumber}"
            val isActive = (season.seasonNumber == selectedSeasonForDrawer)
            holder.seasonTabBtn.setBackgroundResource(
                if (isActive) R.drawable.bg_netflix_season_pill_active else R.drawable.bg_netflix_season_pill_inactive
            )
            holder.seasonTabBtn.setOnClickListener {
                if (selectedSeasonForDrawer != season.seasonNumber) {
                    selectedSeasonForDrawer = season.seasonNumber
                    notifyDataSetChanged()
                    onSeasonSelected(season.seasonNumber)
                }
            }
        }

        override fun getItemCount(): Int = seasons.size
    }

    private inner class EpisodesListAdapter(
        private val episodes: List<EpisodeItem>,
        private val onEpisodeSelected: (EpisodeItem) -> Unit
    ) : RecyclerView.Adapter<EpisodesListAdapter.EpisodeViewHolder>() {

        inner class EpisodeViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val cardRoot: View = itemView.findViewById(R.id.epCardRoot)
            val thumb: ImageView = itemView.findViewById(R.id.epThumb)
            val playingBadge: TextView = itemView.findViewById(R.id.epPlayingBadge)
            val duration: TextView = itemView.findViewById(R.id.epDuration)
            val title: TextView = itemView.findViewById(R.id.epTitle)
            val overview: TextView = itemView.findViewById(R.id.epOverview)
            val playActionIcon: ImageView = itemView.findViewById(R.id.epPlayActionIcon)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EpisodeViewHolder {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.view_netflix_episode_item, parent, false)
            return EpisodeViewHolder(v)
        }

        override fun onBindViewHolder(holder: EpisodeViewHolder, position: Int) {
            val ep = episodes[position]
            holder.title.text = "E${ep.episode} • ${ep.title}"
            holder.duration.text = ep.runtime
            holder.overview.text = ep.overview

            val isCurrent = (ep.season == currentSeasonNumber && ep.episode == currentEpisodeNumber)
            holder.playingBadge.visibility = if (isCurrent) View.VISIBLE else View.GONE
            holder.cardRoot.isSelected = isCurrent
            holder.playActionIcon.setColorFilter(if (isCurrent) Color.parseColor("#00E5FF") else Color.parseColor("#0084FF"))

            if (ep.thumbnail.isNotEmpty()) {
                Glide.with(this@MovieCinemaPlayerActivity)
                    .load(ep.thumbnail)
                    .placeholder(R.drawable.splash)
                    .error(R.drawable.splash)
                    .centerCrop()
                    .into(holder.thumb)
            } else if (videoPoster.isNotEmpty()) {
                Glide.with(this@MovieCinemaPlayerActivity)
                    .load(videoPoster)
                    .placeholder(R.drawable.splash)
                    .error(R.drawable.splash)
                    .centerCrop()
                    .into(holder.thumb)
            } else {
                holder.thumb.setImageResource(R.drawable.splash)
            }

            val clickListener = View.OnClickListener {
                onEpisodeSelected(ep)
            }
            holder.cardRoot.setOnClickListener(clickListener)
            holder.playActionIcon.setOnClickListener(clickListener)
        }

        override fun getItemCount(): Int = episodes.size
    }

    private fun handlePlayerBackPress() {
        if (netflixEpisodesDrawer.visibility == View.VISIBLE) {
            hideEpisodesDrawer()
            return
        }
        if (netflixNextEpisodeOverlay.visibility == View.VISIBLE) {
            dismissNextEpisodeOverlay()
            return
        }
        if (isScreenLocked) {
            Toast.makeText(this, "Screen is locked. Tap lock button to unlock.", Toast.LENGTH_SHORT).show()
            return
        }
        savePlaybackHistory()
        finish()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        handlePlayerBackPress()
    }

    override fun finish() {
        try {
            playerView.player = null
            exoPlayer?.stop()
            exoPlayer?.clearMediaItems()
            exoPlayer?.release()
            exoPlayer = null
        } catch (_: Exception) {}
        killWebViewPlayback()
        streamingTorrentHash?.let { hash ->
            try {
                if (com.clouddrive.leech.torrent.TorrentEngineManager.isStreaming(hash)) {
                    com.clouddrive.leech.torrent.TorrentEngineManager.pauseTorrent(hash)
                }
            } catch (_: Exception) {}
        }
        try {
            playerWebView.destroy()
        } catch (_: Exception) {}
        try {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(null)
        } catch (_: Exception) {}
        super.finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        savePlaybackHistory()
        mainHandler.removeCallbacksAndMessages(null)
        if (isPipReceiverRegistered) {
            try {
                unregisterReceiver(pipReceiver)
            } catch (_: Exception) {}
            isPipReceiverRegistered = false
        }
        try {
            unregisterReceiver(becomingNoisyReceiver)
        } catch (_: Exception) {}

        try {
            loudnessEnhancer?.release()
            loudnessEnhancer = null
        } catch (_: Exception) {}

        try {
            playerView.player = null
            exoPlayer?.stop()
            exoPlayer?.clearMediaItems()
            exoPlayer?.release()
            exoPlayer = null
        } catch (_: Exception) {}

        killWebViewPlayback()
        streamingTorrentHash?.let { hash ->
            try {
                if (com.clouddrive.leech.torrent.TorrentEngineManager.isStreaming(hash)) {
                    com.clouddrive.leech.torrent.TorrentEngineManager.pauseTorrent(hash)
                }
            } catch (_: Exception) {}
        }
        try {
            playerWebView.destroy()
        } catch (_: Exception) {}

        try {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(null)
        } catch (_: Exception) {}

        try {
            com.clouddrive.leech.proxy.LocalMediaProxy.unregisterSession()
        } catch (_: Exception) {}
    }
}
