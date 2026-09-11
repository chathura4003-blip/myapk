package com.clouddrive.leech.player

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.drawable.Icon
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Rational
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.clouddrive.leech.App
import com.clouddrive.leech.R
import com.clouddrive.leech.database.entities.HistoryEntity
import com.clouddrive.leech.extractor.providers.movies.BaseMovieScraper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.abs

/**
 * 2026 High-Performance Native Cinema Pro Media3 / ExoPlayer Activity.
 * Full Support for:
 * - Smart Touch Gestures (Brightness left-swipe, Volume right-swipe, Horizontal Seek Scrubbing, Double-tap 10s seek)
 * - Screen Touch Lock / Child Lock
 * - Aspect Ratio Switcher (Fit, Fill, Zoom, 16:9)
 * - Playback Speed Switcher (0.5x - 2.0x)
 * - Picture-in-Picture (PiP) and Background Playback
 */
class PlayerActivity : AppCompatActivity() {

    private var exoPlayer: ExoPlayer? = null
    private var streamRetryCount = 0
    private val maxStreamRetries = 2
    private var isPipReceiverRegistered = false
    private val pipReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                ACTION_PIP_PLAY_PAUSE -> {
                    exoPlayer?.let { player ->
                        if (player.isPlaying) {
                            player.pause()
                        } else {
                            player.play()
                        }
                        updatePipActions()
                    }
                }
                ACTION_PIP_REWIND -> {
                    exoPlayer?.let { player ->
                        val target = (player.currentPosition - 10000L).coerceAtLeast(0L)
                        player.seekTo(target)
                    }
                }
                ACTION_PIP_FORWARD -> {
                    exoPlayer?.let { player ->
                        val target = (player.currentPosition + 10000L).coerceAtMost(player.duration)
                        player.seekTo(target)
                    }
                }
            }
        }
    }

    private lateinit var playerView: PlayerView
    private lateinit var playerWebView: WebView
    private lateinit var progressBar: ProgressBar
    private lateinit var titleText: TextView
    private lateinit var btnClose: ImageButton
    private lateinit var btnPip: ImageButton
    private lateinit var btnRotateScreen: ImageButton
    private lateinit var btnSpeed: Button
    private lateinit var btnAspectRatio: Button
    private lateinit var btnLockScreen: ImageButton
    private lateinit var btnUnlockScreen: ImageButton
    private lateinit var playerTopBar: LinearLayout

    // Gesture HUDs
    private lateinit var gestureHudContainer: LinearLayout
    private lateinit var gestureHudIcon: ImageView
    private lateinit var gestureHudText: TextView
    private lateinit var gestureHudProgress: ProgressBar

    private lateinit var seekHudContainer: LinearLayout
    private lateinit var seekHudDiff: TextView
    private lateinit var seekHudTarget: TextView

    private lateinit var audioManager: AudioManager
    private val hudHandler = Handler(Looper.getMainLooper())
    private var hideHudRunnable = Runnable {
        gestureHudContainer.visibility = View.GONE
        seekHudContainer.visibility = View.GONE
    }

    private var videoUrl: String = ""
    private var videoTitle: String = ""
    private var videoPoster: String = ""
    private var videoCategory: String = "movies"
    private var isUsingWebView = false
    private var isScreenLocked = false
    private var resumePlaybackOnResume = false

    // Playback Speed & Aspect Ratio States
    private val speedList = floatArrayOf(1.0f, 1.25f, 1.5f, 2.0f, 0.5f, 0.75f)
    private var currentSpeedIndex = 0

    private val resizeModes = intArrayOf(
        AspectRatioFrameLayout.RESIZE_MODE_FIT,
        AspectRatioFrameLayout.RESIZE_MODE_ZOOM,
        AspectRatioFrameLayout.RESIZE_MODE_FILL,
        AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH
    )
    private val resizeModeLabels = arrayOf("FIT", "ZOOM", "FILL", "16:9")
    private var currentResizeIndex = 0

    // Gesture Tracking Variables
    private var isGestureTracking = false
    private var gestureType: GestureType = GestureType.NONE
    private var touchStartX = 0f
    private var touchStartY = 0f
    private var initialVolume = 0
    private var initialBrightness = 0.5f
    private var initialSeekPosition = 0L
    private var targetSeekPosition = 0L

    private enum class GestureType {
        NONE, BRIGHTNESS, VOLUME, SEEK
    }

    private lateinit var gestureDetector: GestureDetector

    companion object {
        const val EXTRA_URL = "extra_video_url"
        const val EXTRA_TITLE = "extra_video_title"
        const val EXTRA_POSTER = "extra_video_poster"
        const val EXTRA_CATEGORY = "extra_video_category"

        const val ACTION_PIP_PLAY_PAUSE = "com.clouddrive.leech.player.PIP_PLAY_PAUSE"
        const val ACTION_PIP_REWIND = "com.clouddrive.leech.player.PIP_REWIND"
        const val ACTION_PIP_FORWARD = "com.clouddrive.leech.player.PIP_FORWARD"
        const val REQUEST_PIP_PLAY_PAUSE = 301
        const val REQUEST_PIP_REWIND = 302
        const val REQUEST_PIP_FORWARD = 303

        fun start(context: Context, url: String, title: String, poster: String = "", category: String = "movies") {
            val intent = Intent(context, PlayerActivity::class.java).apply {
                putExtra(EXTRA_URL, url)
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_POSTER, poster)
                putExtra(EXTRA_CATEGORY, category)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)

        // 🚀 Unlock Maximum Display Refresh Rate (90Hz / 120Hz FPS)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val display = windowManager.defaultDisplay
                val modes = display.supportedModes
                val maxFpsMode = modes.maxByOrNull { it.refreshRate }
                if (maxFpsMode != null && maxFpsMode.refreshRate > 60f) {
                    val params = window.attributes
                    params.preferredDisplayModeId = maxFpsMode.modeId
                    window.attributes = params
                }
            }
        } catch (_: Exception) {}

        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager

        videoUrl = intent.getStringExtra(EXTRA_URL) ?: ""
        videoTitle = intent.getStringExtra(EXTRA_TITLE) ?: "Playing Video"
        videoPoster = intent.getStringExtra(EXTRA_POSTER) ?: ""
        videoCategory = intent.getStringExtra(EXTRA_CATEGORY) ?: "movies"

        playerView = findViewById(R.id.playerView)
        playerWebView = findViewById(R.id.playerWebView)
        progressBar = findViewById(R.id.playerProgressBar)
        titleText = findViewById(R.id.playerTitleText)
        btnClose = findViewById(R.id.btnClosePlayer)
        btnPip = findViewById(R.id.btnPip)
        btnRotateScreen = findViewById(R.id.btnRotateScreen)
        btnSpeed = findViewById(R.id.btnSpeed)
        btnAspectRatio = findViewById(R.id.btnAspectRatio)
        btnLockScreen = findViewById(R.id.btnLockScreen)
        btnUnlockScreen = findViewById(R.id.btnUnlockScreen)
        playerTopBar = findViewById(R.id.playerTopBar)

        gestureHudContainer = findViewById(R.id.gestureHudContainer)
        gestureHudIcon = findViewById(R.id.gestureHudIcon)
        gestureHudText = findViewById(R.id.gestureHudText)
        gestureHudProgress = findViewById(R.id.gestureHudProgress)

        seekHudContainer = findViewById(R.id.seekHudContainer)
        seekHudDiff = findViewById(R.id.seekHudDiff)
        seekHudTarget = findViewById(R.id.seekHudTarget)

        titleText.text = videoTitle

        // Button Listeners
        btnClose.setOnClickListener { finish() }
        btnPip.setOnClickListener { enterPipMode() }

        btnRotateScreen.setOnClickListener {
            val orientation = resources.configuration.orientation
            if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                Toast.makeText(this, "📱 Portrait Mode", Toast.LENGTH_SHORT).show()
            } else {
                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                Toast.makeText(this, "🔄 Landscape Mode", Toast.LENGTH_SHORT).show()
            }
        }

        btnSpeed.setOnClickListener {
            currentSpeedIndex = (currentSpeedIndex + 1) % speedList.size
            val spd = speedList[currentSpeedIndex]
            btnSpeed.text = "${spd}x"
            exoPlayer?.setPlaybackSpeed(spd)
            Toast.makeText(this, "Playback Speed: ${spd}x", Toast.LENGTH_SHORT).show()
        }

        btnAspectRatio.setOnClickListener {
            currentResizeIndex = (currentResizeIndex + 1) % resizeModes.size
            val mode = resizeModes[currentResizeIndex]
            val label = resizeModeLabels[currentResizeIndex]
            btnAspectRatio.text = label
            playerView.resizeMode = mode
            Toast.makeText(this, "Aspect Ratio: $label", Toast.LENGTH_SHORT).show()
        }

        btnLockScreen.setOnClickListener {
            isScreenLocked = true
            playerTopBar.visibility = View.GONE
            playerView.useController = false
            btnUnlockScreen.visibility = View.VISIBLE
            Toast.makeText(this, "🔒 Screen Locked", Toast.LENGTH_SHORT).show()
        }

        btnUnlockScreen.setOnClickListener {
            isScreenLocked = false
            btnUnlockScreen.visibility = View.GONE
            playerTopBar.visibility = View.VISIBLE
            playerView.useController = true
            Toast.makeText(this, "🔓 Screen Unlocked", Toast.LENGTH_SHORT).show()
        }

        if (videoUrl.isEmpty()) {
            Toast.makeText(this, "Invalid video stream URL", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setupGestures()
        setupWebView()

        if (isWebStreamUrl(videoUrl)) {
            startWebViewStream(videoUrl)
        } else {
            initPlayer()
        }
    }

    // ================= Smart Gestures Detector (Brightness, Volume, Seek, Double-Tap) ================= //
    @SuppressLint("ClickableViewAccessibility")
    private fun setupGestures() {
        gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(e: MotionEvent): Boolean {
                if (isScreenLocked || isUsingWebView) return false
                val width = playerView.width
                val x = e.x
                val player = exoPlayer ?: return false

                if (x < width * 0.4f) {
                    // Double Tap Left: Seek -10s
                    val target = (player.currentPosition - 10000).coerceAtLeast(0L)
                    player.seekTo(target)
                    showSeekFeedback(-10, target, player.duration)
                    return true
                } else if (x > width * 0.6f) {
                    // Double Tap Right: Seek +10s
                    val duration = if (player.duration > 0) player.duration else Long.MAX_VALUE
                    val target = (player.currentPosition + 10000).coerceAtMost(duration)
                    player.seekTo(target)
                    showSeekFeedback(10, target, player.duration)
                    return true
                }
                return false
            }

            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                if (isScreenLocked) {
                    btnUnlockScreen.visibility = if (btnUnlockScreen.visibility == View.VISIBLE) View.GONE else View.VISIBLE
                    return true
                }
                return false
            }
        })

        playerView.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)

            if (isScreenLocked || isUsingWebView) return@setOnTouchListener false

            val width = playerView.width.toFloat()
            val height = playerView.height.toFloat()

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    touchStartX = event.x
                    touchStartY = event.y
                    gestureType = GestureType.NONE
                    isGestureTracking = true

                    val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                    initialVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)

                    val lp = window.attributes
                    initialBrightness = if (lp.screenBrightness < 0) 0.5f else lp.screenBrightness
                    initialSeekPosition = exoPlayer?.currentPosition ?: 0L
                    targetSeekPosition = initialSeekPosition
                }

                MotionEvent.ACTION_MOVE -> {
                    if (!isGestureTracking) return@setOnTouchListener false

                    val deltaX = event.x - touchStartX
                    val deltaY = touchStartY - event.y // Swipe up is positive

                    if (gestureType == GestureType.NONE) {
                        if (abs(deltaY) > 30 && abs(deltaY) > abs(deltaX)) {
                            gestureType = if (touchStartX < width * 0.5f) GestureType.BRIGHTNESS else GestureType.VOLUME
                        } else if (abs(deltaX) > 40 && abs(deltaX) > abs(deltaY)) {
                            gestureType = GestureType.SEEK
                        }
                    }

                    when (gestureType) {
                        GestureType.BRIGHTNESS -> {
                            val change = deltaY / height
                            var newBrightness = (initialBrightness + change).coerceIn(0.01f, 1.0f)
                            val lp = window.attributes
                            lp.screenBrightness = newBrightness
                            window.attributes = lp
                            showGestureHud("Brightness", (newBrightness * 100).toInt(), android.R.drawable.ic_menu_day)
                        }

                        GestureType.VOLUME -> {
                            val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                            val change = (deltaY / (height * 0.75f)) * maxVol
                            val newVol = (initialVolume + change.toInt()).coerceIn(0, maxVol)
                            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVol, 0)
                            val percent = if (maxVol > 0) ((newVol * 100) / maxVol) else 0
                            showGestureHud("Volume", percent, android.R.drawable.ic_lock_idle_charging)
                        }

                        GestureType.SEEK -> {
                            val totalDuration = exoPlayer?.duration ?: 0L
                            if (totalDuration > 0) {
                                val seekDeltaMs = ((deltaX / width) * 120000).toLong() // +/- 2 minutes max per swipe
                                targetSeekPosition = (initialSeekPosition + seekDeltaMs).coerceIn(0L, totalDuration)
                                val diffSec = ((targetSeekPosition - initialSeekPosition) / 1000).toInt()
                                showSeekFeedback(diffSec, targetSeekPosition, totalDuration)
                            }
                        }

                        GestureType.NONE -> {}
                    }
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (gestureType == GestureType.SEEK) {
                        exoPlayer?.seekTo(targetSeekPosition)
                    }
                    isGestureTracking = false
                    gestureType = GestureType.NONE
                    scheduleHideHud(800)
                }
            }
            false
        }
    }

    private fun showGestureHud(label: String, percent: Int, iconRes: Int) {
        hudHandler.removeCallbacks(hideHudRunnable)
        gestureHudIcon.setImageResource(iconRes)
        gestureHudText.text = "$label: $percent%"
        gestureHudProgress.progress = percent
        gestureHudContainer.visibility = View.VISIBLE
        seekHudContainer.visibility = View.GONE
    }

    private fun showSeekFeedback(diffSec: Int, targetMs: Long, totalMs: Long) {
        hudHandler.removeCallbacks(hideHudRunnable)
        val sign = if (diffSec >= 0) "+" else ""
        seekHudDiff.text = String.format("%s%d:%02d", sign, diffSec / 60, abs(diffSec % 60))

        val curSec = targetMs / 1000
        val totSec = (if (totalMs > 0) totalMs else 0L) / 1000
        seekHudTarget.text = String.format("%02d:%02d / %02d:%02d", curSec / 60, curSec % 60, totSec / 60, totSec % 60)

        seekHudContainer.visibility = View.VISIBLE
        gestureHudContainer.visibility = View.GONE
    }

    private fun scheduleHideHud(delayMs: Long) {
        hudHandler.removeCallbacks(hideHudRunnable)
        hudHandler.postDelayed(hideHudRunnable, delayMs)
    }

    private fun isWebStreamUrl(url: String): Boolean {
        if (url.startsWith("/") || url.startsWith("file://") || url.startsWith("content://")) {
            return false
        }
        val lower = url.lowercase()
        if (lower.contains(".m3u8") || lower.contains(".mp4") || lower.contains(".mkv") || lower.contains(".webm") || lower.contains("xhcdn.com") || lower.contains("rdtcdn.com") || lower.contains("phncdn.com") || lower.contains("eporner")) {
            return false
        }
        if (lower.contains("mega.nz") || lower.contains("mega.io") || lower.contains("drive.google.com") || lower.contains("drive.usercontent.google.com") || lower.contains("drive.google") || lower.contains("usersdrive") || lower.contains("filespayout") || lower.contains("cinejoy.to/watch")) {
            return true
        }
        return lower.contains("embed") ||
                lower.contains("/videos/") ||
                lower.contains("vidsrc") ||
                lower.contains("cinejoy.to/watch") ||
                lower.contains("/links/") ||
                lower.contains("youtube.com") ||
                lower.contains("youtu.be") ||
                lower.contains("streamtape") ||
                lower.contains("dood")
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

        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(playerWebView, true)

        playerWebView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                if (newProgress < 100) {
                    progressBar.visibility = View.VISIBLE
                } else {
                    progressBar.visibility = View.GONE
                    saveHistory()
                }
            }

            override fun onCreateWindow(view: WebView?, isDialog: Boolean, isUserGesture: Boolean, resultMsg: android.os.Message?): Boolean {
                return false
            }
        }

        playerWebView.webViewClient = object : WebViewClient() {
            private val blockedAdKeywords = listOf(
                "adsterra", "popads", "popcash", "clickadu", "monetag", "propush", "propeller",
                "syndication", "doubleclick", "googlesyndication", "adnxs", "bet365", "1xbet",
                "betway", "melbet", "parimatch", "mostbet", "exoclick", "juicyads", "trafficjunky"
            )

            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val nextUrl = request?.url?.toString() ?: ""
                val lower = nextUrl.lowercase()
                for (adKw in blockedAdKeywords) {
                    if (lower.contains(adKw)) return true
                }
                if (nextUrl.startsWith("http://") || nextUrl.startsWith("https://")) {
                    return false
                }
                return true
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                val isTrusted = url?.contains("drive.google") == true || url?.contains("google.com") == true || url?.contains("googleusercontent.com") == true
                if (isTrusted) {
                    view?.evaluateJavascript("""
                        (function() {
                            try {
                                window.open = function() { return null; };
                                window.alert = function() {};
                                window.confirm = function() { return true; };
                            } catch(e) {}
                        })();
                    """.trimIndent(), null)
                    return
                }
                view?.evaluateJavascript("""
                    (function() {
                        try {
                            window.open = function() { return null; };
                            window.alert = function() {};
                            window.confirm = function() { return true; };

                            var style = document.createElement('style');
                            style.id = 'cdl-branding-cleaner';
                            style.innerHTML = `
                                .viewer-top, .viewer-header, header, .viewer-logo, .logo, .mega-logo, .top-head, .viewer-brand,
                                .viewer-file-info, .viewer-share-button, .viewer-download-button, .viewer-actions, .viewer-menu,
                                .viewer-filename, .viewer-account, .viewer-controls-top, a[href*="mega.nz"], .cloud-logo,
                                [class*="viewer-top"], [class*="viewer-header"], [class*="viewer-logo"], [class*="file-name"],
                                .mobile-top-bar, .top-bar-container, .brand-logo, .site-header, .viewer-watermark, .nw-logo,
                                .logo-container, .v-top-info, .v-title, .video-title, #top-bar, .jw-title, .vjs-title-bar,
                                .video-info-block, .play-overlay-title, .brand-icon, a.logo, .m-logo {
                                    display: none !important;
                                    visibility: hidden !important;
                                    opacity: 0 !important;
                                    pointer-events: none !important;
                                    height: 0 !important;
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
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                progressBar.visibility = View.GONE
                saveHistory()
                val isTrusted = url?.contains("drive.google") == true || url?.contains("google.com") == true || url?.contains("googleusercontent.com") == true
                if (isTrusted) {
                    view?.evaluateJavascript("""
                        (function() {
                            try {
                                window.open = function() { return null; };
                                window.alert = function() {};
                                window.confirm = function() { return true; };
                                setTimeout(function() {
                                    var playBtn = document.querySelector('.ytp-large-play-button, button[aria-label*="Play"], button[title*="Play"], .video-play-button, div[role="button"][aria-label*="Play"]');
                                    if (playBtn) playBtn.click();
                                    var v = document.querySelector('video');
                                    if (v) { v.muted = false; v.play().catch(function(){}); }
                                }, 1000);
                            } catch(e) {}
                        })();
                    """.trimIndent(), null)
                    return
                }
                view?.evaluateJavascript("""
                    (function() {
                        try {
                            window.open = function() { return null; };
                            window.alert = function() {};
                            window.confirm = function() { return true; };

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

                            var v = document.querySelector('video');
                            if (v) { v.muted = false; v.play().catch(function(){}); }
                            var playBtn = document.querySelector('.xplayer-big-button, .play-button, [data-role="play-button"], .viewer-button, .play-btn, .video-play-button');
                            if (playBtn) playBtn.click();

                            var timer = setInterval(stripLogos, 1000);
                            setTimeout(function() { clearInterval(timer); }, 25000);
                        } catch(e) {}
                    })();
                """.trimIndent(), null)
            }
        }
    }

    private fun startWebViewStream(url: String) {
        isUsingWebView = true
        playerView.visibility = View.GONE
        playerWebView.visibility = View.VISIBLE
        progressBar.visibility = View.VISIBLE

        var targetWebUrl = url.trim()
        if (targetWebUrl.contains("mega.nz/file/")) {
            targetWebUrl = targetWebUrl.replace("mega.nz/file/", "mega.nz/embed/")
        } else if (targetWebUrl.contains("mega.nz/#!")) {
            targetWebUrl = targetWebUrl.replace("mega.nz/#!", "mega.nz/embed/!")
        } else if (targetWebUrl.contains("mega.nz/#")) {
            targetWebUrl = targetWebUrl.replace("mega.nz/#", "mega.nz/embed/#")
        } else if (targetWebUrl.contains("drive.google.com") || targetWebUrl.contains("drive.usercontent.google.com") || targetWebUrl.contains("drive.google")) {
            val gId = Regex("""(?:file/d/|open\?id=|uc\?id=|download\?id=|id=)([a-zA-Z0-9_-]+)""").find(targetWebUrl)?.groupValues?.get(1)
            if (!gId.isNullOrEmpty()) {
                targetWebUrl = "https://drive.google.com/file/d/$gId/preview"
            }
        }

        if (targetWebUrl.contains("mega.nz") || targetWebUrl.contains("mega.io") || targetWebUrl.contains("drive.google")) {
            playerWebView.settings.userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36"
        } else {
            playerWebView.settings.userAgentString = "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36"
        }

        val webHeaders = mutableMapOf<String, String>()
        if (targetWebUrl.contains("xhamster") || targetWebUrl.contains("xhcdn")) {
            webHeaders["Referer"] = "https://xhamster.com/"
            webHeaders["Origin"] = "https://xhamster.com"
        } else if (targetWebUrl.contains("xnxx") || targetWebUrl.contains("xvideos")) {
            webHeaders["Referer"] = "https://www.xnxx.com/"
        }
        playerWebView.loadUrl(targetWebUrl, webHeaders)
    }

    @OptIn(UnstableApi::class)
    private fun initPlayer() {
        try {
            val headers = mutableMapOf<String, String>()
            if (videoUrl.contains("phncdn.com") || videoUrl.contains("pornhub.com")) {
                headers["Referer"] = "https://www.pornhub.com/"
                headers["Origin"] = "https://www.pornhub.com"
                headers["Cookie"] = "age_verified=1; accessAgeDisclaimerPH=1; has_access=1; il=v111"
            } else if (videoUrl.contains("rdtcdn.com") || videoUrl.contains("redtube.com")) {
                headers["Referer"] = "https://www.redtube.com/"
                headers["Origin"] = "https://www.redtube.com"
                headers["Cookie"] = "age_verified=1; accessAgeDisclaimerPH=1; has_access=1; il=v111"
            } else if (videoUrl.contains("xhcdn.com") || videoUrl.contains("xhamster.com")) {
                headers["Referer"] = "https://xhamster.com/"
                headers["Origin"] = "https://xhamster.com"
            } else if (videoUrl.contains("xvideos") || videoUrl.contains("xnxx")) {
                headers["Referer"] = "https://www.xvideos.com/"
                headers["Origin"] = "https://www.xvideos.com"
            } else if (videoUrl.contains("eporner.com")) {
                headers["Referer"] = "https://www.eporner.com/"
            } else if (videoUrl.contains("cinejoy") || videoUrl.contains("shegu.st") || videoUrl.contains("workers.dev") || videoUrl.contains("4khdhub") || videoUrl.contains("cloudflarestorage")) {
                headers["Referer"] = "https://cinejoy.to/"
                headers["Origin"] = "https://cinejoy.to"
            }

            val okHttpDataSourceFactory = OkHttpDataSource.Factory(
                BaseMovieScraper.defaultClient
            ).setUserAgent("Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36")
            .setDefaultRequestProperties(headers)

            val dataSourceFactory = DefaultDataSource.Factory(this, okHttpDataSourceFactory)
            val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)

            val loadControl = DefaultLoadControl.Builder()
                .setBufferDurationsMs(25000, 120000, 400, 800)
                .setPrioritizeTimeOverSizeThresholds(true)
                .build()

            // Force GPU Hardware-Accelerated Video Decoders (Zero CPU Load)
            val renderersFactory = DefaultRenderersFactory(this)
                .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
                .setEnableDecoderFallback(true)

            val audioAttributes = androidx.media3.common.AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                .build()

            exoPlayer = ExoPlayer.Builder(this, renderersFactory)
                .setMediaSourceFactory(mediaSourceFactory)
                .setLoadControl(loadControl)
                .setAudioAttributes(audioAttributes, /* handleAudioFocus= */ true)
                .setSeekBackIncrementMs(10000)
                .setSeekForwardIncrementMs(10000)
                .build().apply {
                    val mediaUri = if (videoUrl.startsWith("/") && !videoUrl.startsWith("http")) {
                        Uri.fromFile(File(videoUrl))
                    } else {
                        Uri.parse(videoUrl)
                    }
                    val mediaItemBuilder = MediaItem.Builder().setUri(mediaUri)
                    val lower = videoUrl.lowercase()
                    when {
                        lower.contains(".mpd") || lower.contains("manifest.mpd") || lower.contains("type=dash") -> {
                            mediaItemBuilder.setMimeType(androidx.media3.common.MimeTypes.APPLICATION_MPD)
                        }
                        lower.contains(".m3u8") || lower.contains("/hls/") || lower.contains("type=m3u8") -> {
                            mediaItemBuilder.setMimeType(androidx.media3.common.MimeTypes.APPLICATION_M3U8)
                        }
                    }
                    val mediaItem = mediaItemBuilder.build()

                    setMediaItem(mediaItem)
                    prepare()
                    playWhenReady = true

                    addListener(object : Player.Listener {
                        override fun onPlaybackStateChanged(playbackState: Int) {
                            when (playbackState) {
                                Player.STATE_BUFFERING -> {
                                    progressBar.visibility = View.VISIBLE
                                }
                                Player.STATE_READY -> {
                                    streamRetryCount = 0
                                    progressBar.visibility = View.GONE
                                    saveHistory()
                                }
                                Player.STATE_ENDED -> {
                                    progressBar.visibility = View.GONE
                                    saveHistory()
                                }
                                Player.STATE_IDLE -> {}
                            }
                        }

                        override fun onIsPlayingChanged(isPlaying: Boolean) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && isInPictureInPictureMode) {
                                updatePipActions()
                            }
                        }

                        override fun onPlayerError(error: PlaybackException) {
                            progressBar.visibility = View.GONE
                            if (streamRetryCount < maxStreamRetries && !isUsingWebView) {
                                streamRetryCount++
                                val retryPos = exoPlayer?.currentPosition ?: 0L
                                android.util.Log.w("PlayerActivity", "Playback retry $streamRetryCount of $maxStreamRetries from $retryPos: ${error.message}")
                                Handler(Looper.getMainLooper()).postDelayed({
                                    if (!isFinishing && !isDestroyed) {
                                        exoPlayer?.seekTo(retryPos)
                                        exoPlayer?.prepare()
                                        exoPlayer?.play()
                                    }
                                }, 1200L)
                                return
                            }
                            streamRetryCount = 0
                            // Seamless automatic fallback to Web Cinema Stream
                            startWebViewStream(videoUrl)
                        }
                    })
                }

            playerView.player = exoPlayer
            playerView.controllerShowTimeoutMs = 3000
            playerView.controllerHideOnTouch = true
            playerView.setControllerVisibilityListener(PlayerView.ControllerVisibilityListener { visibility ->
                if (!isScreenLocked) {
                    if (visibility == View.VISIBLE) {
                        playerTopBar.visibility = View.VISIBLE
                        playerTopBar.animate().alpha(1.0f).setDuration(200).start()
                    } else {
                        playerTopBar.animate().alpha(0.0f).setDuration(250).withEndAction {
                            playerTopBar.visibility = View.GONE
                        }.start()
                    }
                }
            })
        } catch (e: Exception) {
            startWebViewStream(videoUrl)
        }
    }

    fun updatePipActions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val isPlaying = exoPlayer?.isPlaying == true
                val actions = ArrayList<RemoteAction>()

                // 1. Rewind Button
                val prevIntent = Intent(ACTION_PIP_REWIND).apply { `package` = packageName }
                val prevPendingIntent = PendingIntent.getBroadcast(
                    this, REQUEST_PIP_REWIND, prevIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                actions.add(RemoteAction(Icon.createWithResource(this, R.drawable.ic_gallery_rewind10), "Rewind 10s", "Rewind 10 seconds", prevPendingIntent))

                // 2. Play / Pause Button
                val playPauseIntent = Intent(ACTION_PIP_PLAY_PAUSE).apply { `package` = packageName }
                val playPausePendingIntent = PendingIntent.getBroadcast(
                    this, REQUEST_PIP_PLAY_PAUSE, playPauseIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                val playPauseIcon = if (isPlaying) Icon.createWithResource(this, R.drawable.ic_gallery_pause) else Icon.createWithResource(this, R.drawable.ic_gallery_play)
                actions.add(RemoteAction(playPauseIcon, if (isPlaying) "Pause" else "Play", "Play / Pause", playPausePendingIntent))

                // 3. Forward Button
                val nextIntent = Intent(ACTION_PIP_FORWARD).apply { `package` = packageName }
                val nextPendingIntent = PendingIntent.getBroadcast(
                    this, REQUEST_PIP_FORWARD, nextIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                actions.add(RemoteAction(Icon.createWithResource(this, R.drawable.ic_gallery_forward10), "Forward 10s", "Forward 10 seconds", nextPendingIntent))

                val params = PictureInPictureParams.Builder()
                    .setAspectRatio(Rational(16, 9))
                    .setActions(actions)
                    .build()

                setPictureInPictureParams(params)
            } catch (_: Exception) {}
        }
    }

    private fun enterPipMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val isPlaying = exoPlayer?.isPlaying == true
                val actions = ArrayList<RemoteAction>()

                val prevIntent = Intent(ACTION_PIP_REWIND).apply { `package` = packageName }
                val prevPendingIntent = PendingIntent.getBroadcast(
                    this, REQUEST_PIP_REWIND, prevIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                actions.add(RemoteAction(Icon.createWithResource(this, R.drawable.ic_gallery_rewind10), "Rewind 10s", "Rewind 10 seconds", prevPendingIntent))

                val playPauseIntent = Intent(ACTION_PIP_PLAY_PAUSE).apply { `package` = packageName }
                val playPausePendingIntent = PendingIntent.getBroadcast(
                    this, REQUEST_PIP_PLAY_PAUSE, playPauseIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                val playPauseIcon = if (isPlaying) Icon.createWithResource(this, R.drawable.ic_gallery_pause) else Icon.createWithResource(this, R.drawable.ic_gallery_play)
                actions.add(RemoteAction(playPauseIcon, if (isPlaying) "Pause" else "Play", "Play / Pause", playPausePendingIntent))

                val nextIntent = Intent(ACTION_PIP_FORWARD).apply { `package` = packageName }
                val nextPendingIntent = PendingIntent.getBroadcast(
                    this, REQUEST_PIP_FORWARD, nextIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                actions.add(RemoteAction(Icon.createWithResource(this, R.drawable.ic_gallery_forward10), "Forward 10s", "Forward 10 seconds", nextPendingIntent))

                val params = PictureInPictureParams.Builder()
                    .setAspectRatio(Rational(16, 9))
                    .setActions(actions)
                    .build()

                enterPictureInPictureMode(params)
            } catch (e: Exception) {
                Toast.makeText(this, "Picture-in-Picture not supported", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        if (isInPictureInPictureMode) {
            playerView.useController = false
            playerTopBar.visibility = View.GONE
            gestureHudContainer.visibility = View.GONE
            seekHudContainer.visibility = View.GONE
            btnUnlockScreen.visibility = View.GONE

            if (!isPipReceiverRegistered) {
                try {
                    val filter = IntentFilter().apply {
                        addAction(ACTION_PIP_PLAY_PAUSE)
                        addAction(ACTION_PIP_REWIND)
                        addAction(ACTION_PIP_FORWARD)
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        registerReceiver(pipReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
                    } else {
                        registerReceiver(pipReceiver, filter)
                    }
                    isPipReceiverRegistered = true
                } catch (_: Exception) {}
            }
            updatePipActions()
        } else {
            if (isPipReceiverRegistered) {
                try {
                    unregisterReceiver(pipReceiver)
                } catch (_: Exception) {}
                isPipReceiverRegistered = false
            }

            if (lifecycle.currentState == Lifecycle.State.CREATED || isFinishing) {
                // PiP dismissed by user
                exoPlayer?.stop()
                exoPlayer?.release()
                exoPlayer = null
                finish()
                return
            }

            playerView.resizeMode = resizeModes[currentResizeIndex]
            if (!isScreenLocked) {
                playerView.useController = true
                playerTopBar.visibility = View.VISIBLE
            } else {
                btnUnlockScreen.visibility = View.VISIBLE
            }
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (exoPlayer?.isPlaying == true || isUsingWebView) {
            enterPipMode()
        }
    }

    private fun saveHistory() {
        val player = exoPlayer
        val currentMs = player?.currentPosition ?: 0L
        val durationMs = if (player != null && player.duration > 0) player.duration else 0L

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                App.instance.database.historyDao().insertOrUpdate(
                    HistoryEntity(
                        url = videoUrl,
                        title = videoTitle,
                        poster = videoPoster,
                        source = "Cinema Player",
                        category = videoCategory,
                        watchedDurationMs = currentMs,
                        totalDurationMs = durationMs,
                        lastWatchedTimestamp = System.currentTimeMillis()
                    )
                )
            } catch (e: Exception) {}
        }
    }

    override fun onPause() {
        super.onPause()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && isInPictureInPictureMode) {
            // Keep playing inside active PiP window
        } else {
            try {
                exoPlayer?.pause()
            } catch (_: Exception) {}
            try {
                playerWebView.onPause()
                playerWebView.pauseTimers()
                playerWebView.evaluateJavascript("try { document.querySelectorAll('video, audio').forEach(function(v){ v.pause(); }); } catch(e){}", null)
            } catch (_: Exception) {}
            saveHistory()
        }
    }

    override fun onStop() {
        super.onStop()
        saveHistory()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && isInPictureInPictureMode) {
            // PiP closed or dismissed by user! Stop audio immediately
            exoPlayer?.stop()
            exoPlayer?.release()
            exoPlayer = null
            finish()
        } else {
            try {
                resumePlaybackOnResume = exoPlayer?.isPlaying == true
                exoPlayer?.pause()
            } catch (_: Exception) {}
            try {
                playerWebView.onPause()
                playerWebView.pauseTimers()
            } catch (_: Exception) {}
        }
    }

    override fun onResume() {
        super.onResume()
        if (isUsingWebView) {
            playerWebView.onResume()
        } else if (resumePlaybackOnResume) {
            try { exoPlayer?.play() } catch (_: Exception) {}
            resumePlaybackOnResume = false
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (isScreenLocked) {
            Toast.makeText(this, "Screen is locked. Tap unlock button to exit.", Toast.LENGTH_SHORT).show()
            return
        }
        finish()
    }

    override fun finish() {
        try {
            playerView.player = null
            exoPlayer?.stop()
            exoPlayer?.clearMediaItems()
            exoPlayer?.release()
            exoPlayer = null
        } catch (_: Exception) {}
        try {
            playerWebView.stopLoading()
            playerWebView.loadUrl("about:blank")
            playerWebView.onPause()
            playerWebView.pauseTimers()
            playerWebView.destroy()
        } catch (_: Exception) {}
        try {
            @Suppress("DEPRECATION")
            (getSystemService(Context.AUDIO_SERVICE) as? AudioManager)?.abandonAudioFocus(null)
        } catch (_: Exception) {}
        super.finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        saveHistory()
        if (isPipReceiverRegistered) {
            try {
                unregisterReceiver(pipReceiver)
            } catch (_: Exception) {}
            isPipReceiverRegistered = false
        }
        try {
            playerView.player = null
            exoPlayer?.stop()
            exoPlayer?.clearMediaItems()
            exoPlayer?.release()
            exoPlayer = null
        } catch (_: Exception) {}
        try {
            playerWebView.stopLoading()
            playerWebView.loadUrl("about:blank")
            playerWebView.onPause()
            playerWebView.pauseTimers()
            playerWebView.destroy()
        } catch (e: Exception) {}
        try {
            @Suppress("DEPRECATION")
            (getSystemService(Context.AUDIO_SERVICE) as? AudioManager)?.abandonAudioFocus(null)
        } catch (_: Exception) {}
    }
}
