package com.clouddrive.leech.browser.ui

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.Dialog
import com.google.android.material.bottomsheet.BottomSheetDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.net.http.SslError
import android.os.Bundle
import android.os.Message
import android.text.Editable
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.URLUtil
import android.webkit.ValueCallback
import androidx.activity.result.contract.ActivityResultContracts
import android.webkit.CookieManager
import android.webkit.JsResult
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.appcompat.widget.SwitchCompat
import com.getcapacitor.BridgeActivity
import com.clouddrive.leech.R
import com.clouddrive.leech.browser.adblock.AdBlockEngine
import com.clouddrive.leech.browser.extensions.ExtensionManager
import com.clouddrive.leech.browser.data.BookmarkRepository
import com.clouddrive.leech.browser.data.BrowserSettings
import com.clouddrive.leech.browser.data.HistoryRepository
import com.clouddrive.leech.browser.engine.WebViewTab
import com.clouddrive.leech.browser.engine.WebViewTabManager
import com.clouddrive.leech.player.PlayerActivity
import com.clouddrive.leech.plugins.NativeGdrivePlugin
import com.clouddrive.leech.plugins.NativeDownloadPlugin
import com.clouddrive.leech.browser.sniffer.BrowserMediaSniffer
import com.clouddrive.leech.browser.sniffer.SniffedMedia
import com.google.android.material.snackbar.Snackbar
import java.io.ByteArrayInputStream
import java.util.Locale

class NativeBrowserFragment : Fragment() {

    private var webViewContainer: FrameLayout? = null
    private var addressBar: EditText? = null
    private var progressBar: ProgressBar? = null
    private var tvTabCounter: TextView? = null
    private var btnClearUrl: ImageButton? = null
    private var startPageContainer: ScrollView? = null
    private var snifferPill: LinearLayout? = null
    private var snifferText: TextView? = null
    private var tvSnifferCount: TextView? = null
    private var btnSecurityInfo: ImageView? = null
    private var tvStartBrand: TextView? = null

    private var btnBack: ImageButton? = null
    private var btnForward: ImageButton? = null

    // Fullscreen Custom View & File Chooser
    private var browserMainContent: View? = null
    private var fullscreenContainer: FrameLayout? = null
    private var customView: View? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null
    private var fileUploadCallback: ValueCallback<Array<Uri>>? = null

    private val fileChooserLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val uris = WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data)
        fileUploadCallback?.onReceiveValue(uris)
        fileUploadCallback = null
    }

    private val snifferListener = object : BrowserMediaSniffer.SnifferListener {
        override fun onMediaSniffed(tabId: String, totalCount: Int, mediaList: List<SniffedMedia>) {
            val active = WebViewTabManager.getActiveTab()
            if (active?.id == tabId) {
                updateSnifferPillState(totalCount)
            }
        }
    }

    override fun onPause() {
        super.onPause()
        BrowserMediaSniffer.removeListener(snifferListener)
    }

    private fun updateSnifferPillState(count: Int) {
        activity?.runOnUiThread {
            if (count > 0 && startPageContainer?.visibility != View.VISIBLE) {
                tvSnifferCount?.text = count.toString()
                snifferText?.text = if (count == 1) "1 Video Found" else "$count Videos Found"
                if (snifferPill?.visibility != View.VISIBLE) {
                    snifferPill?.alpha = 0f
                    snifferPill?.translationY = 30f
                    snifferPill?.visibility = View.VISIBLE
                    snifferPill?.animate()?.alpha(1f)?.translationY(0f)?.setDuration(260)?.start()
                }
            } else {
                if (snifferPill?.visibility == View.VISIBLE) {
                    snifferPill?.animate()?.alpha(0f)?.translationY(30f)?.setDuration(200)?.withEndAction {
                        snifferPill?.visibility = View.GONE
                    }?.start()
                } else {
                    snifferPill?.visibility = View.GONE
                }
            }
        }
    }

    companion object {
        const val TAG = "NativeBrowserFragment"

        fun newInstance(initialUrl: String? = null): NativeBrowserFragment {
            val fragment = NativeBrowserFragment()
            if (!initialUrl.isNullOrEmpty()) {
                val args = Bundle()
                args.putString("initial_url", initialUrl)
                fragment.arguments = args
            }
            return fragment
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_gecko_browser, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        browserMainContent = view.findViewById(R.id.browser_main_content)
        fullscreenContainer = view.findViewById(R.id.fullscreen_custom_view_container)
        webViewContainer = view.findViewById(R.id.webview_container)
        addressBar = view.findViewById(R.id.address_bar)
        progressBar = view.findViewById(R.id.progress_bar)
        tvTabCounter = view.findViewById(R.id.tv_tab_counter)
        btnClearUrl = view.findViewById(R.id.btn_clear_url)
        startPageContainer = view.findViewById(R.id.start_page_container)
        snifferPill = view.findViewById(R.id.sniffer_pill)
        snifferText = view.findViewById(R.id.sniffer_text)
        tvSnifferCount = view.findViewById(R.id.tv_sniffer_count)
        btnSecurityInfo = view.findViewById(R.id.btn_security_info)
        tvStartBrand = view.findViewById(R.id.tv_start_brand)

        btnBack = view.findViewById(R.id.btn_back)
        btnForward = view.findViewById(R.id.btn_forward)
        val btnHome: ImageButton? = view.findViewById(R.id.btn_home)
        val btnExtensions: ImageButton? = view.findViewById(R.id.btn_extensions)
        val btnReload: ImageButton? = view.findViewById(R.id.btn_reload)
        val btnTabsOverview: View? = view.findViewById(R.id.btn_tabs_overview)
        val btnMainMenu: ImageButton? = view.findViewById(R.id.btn_main_menu)

        // Setup Speed Dial Shortcuts
        setupSpeedDial(view)

        // Bottom Command Bar Handlers
        btnBack?.setOnClickListener { handleBackPress() }
        btnForward?.setOnClickListener {
            val tab = WebViewTabManager.getActiveTab()
            if (tab?.webView?.canGoForward() == true) {
                tab.webView.goForward()
            }
        }
        btnHome?.setOnClickListener { showStartPage() }
        btnReload?.setOnClickListener {
            val tab = WebViewTabManager.getActiveTab()
            if (startPageContainer?.visibility == View.VISIBLE) {
                // Already at home
            } else {
                tab?.webView?.reload()
            }
        }
        btnExtensions?.setOnClickListener { showExtensionsDialog() }
        btnTabsOverview?.setOnClickListener { showTabOverview() }
        btnMainMenu?.setOnClickListener { showMainMenu() }
        btnSecurityInfo?.setOnClickListener { showSiteSecurityInfo() }

        // Sniffer Leech Pill Click -> Opens Pro Glassmorphic Bottom Sheet
        snifferPill?.setOnClickListener {
            val tab = WebViewTabManager.getActiveTab()
            if (tab != null) {
                showMediaSnifferBottomSheet(tab.id)
            }
        }

        // Omnibox URL Clear Button
        btnClearUrl?.setOnClickListener {
            addressBar?.setText("")
            btnClearUrl?.visibility = View.GONE
        }

        addressBar?.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                btnClearUrl?.visibility = if (!s.isNullOrEmpty() && addressBar?.hasFocus() == true) View.VISIBLE else View.GONE
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        addressBar?.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_GO ||
                (event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)
            ) {
                val input = addressBar?.text?.toString()?.trim() ?: ""
                if (input.isNotEmpty()) {
                    val resolvedUrl = BrowserSettings.normalizeUrl(requireContext(), input)
                    loadUrl(resolvedUrl)
                    hideKeyboard()
                }
                true
            } else {
                false
            }
        }

        addressBar?.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                btnClearUrl?.visibility = if (!addressBar?.text.isNullOrEmpty()) View.VISIBLE else View.GONE
                addressBar?.selectAll()
            } else {
                btnClearUrl?.visibility = View.GONE
                val tab = WebViewTabManager.getActiveTab()
                if (tab != null && tab.url != "about:home") {
                    addressBar?.setText(tab.url)
                }
            }
        }

        // Initialize First Tab
        val initialUrl = arguments?.getString("initial_url") ?: "about:home"
        if (WebViewTabManager.getTabs().isEmpty()) {
            WebViewTabManager.createTab(requireContext(), initialUrl, false)
        }
        bindActiveTab()
    }

    override fun onResume() {
        super.onResume()
        updateGoogleDriveUI()
        BrowserMediaSniffer.addListener(snifferListener)
        val active = WebViewTabManager.getActiveTab()
        if (active != null) {
            updateSnifferPillState(BrowserMediaSniffer.getMediaCountForTab(active.id))
        }
    }

    private fun updateGoogleDriveUI() {
        val ctx = context ?: return
        val prefs = ctx.getSharedPreferences("cdl_gdrive_prefs", Context.MODE_PRIVATE)
        val isConnected = prefs.getBoolean("is_connected", false)
        val email = prefs.getString("active_email", null)
        val name = prefs.getString("active_name", null)
        val quotaUsage = prefs.getLong("quota_usage", 0L)
        val quotaLimit = prefs.getLong("quota_limit", 15L * 1024 * 1024 * 1024)

        val tvName = view?.findViewById<TextView>(R.id.tv_browser_gdrive_name)
        val tvEmail = view?.findViewById<TextView>(R.id.tv_browser_gdrive_email)
        val tvBadge = view?.findViewById<TextView>(R.id.tv_browser_gdrive_badge)
        val llQuota = view?.findViewById<View>(R.id.ll_browser_gdrive_quota)
        val tvQuotaText = view?.findViewById<TextView>(R.id.tv_browser_gdrive_quota_text)
        val pbQuota = view?.findViewById<ProgressBar>(R.id.pb_browser_gdrive_quota)
        val btnOpenDrive = view?.findViewById<View>(R.id.btn_browser_open_drive)
        val tvBtnOpenDrive = view?.findViewById<TextView>(R.id.tv_btn_browser_open_drive)
        val btnSwitchDrive = view?.findViewById<View>(R.id.btn_browser_switch_drive)

        if (isConnected && !email.isNullOrEmpty()) {
            tvName?.text = name ?: "Google Drive"
            tvEmail?.text = email
            tvBadge?.text = "Connected"
            tvBadge?.setTextColor(Color.parseColor("#10B981"))

            val usedGb = quotaUsage.toDouble() / (1024.0 * 1024.0 * 1024.0)
            val limitGb = quotaLimit.toDouble() / (1024.0 * 1024.0 * 1024.0)
            val pct = if (quotaLimit > 0) ((quotaUsage.toDouble() / quotaLimit) * 100).toInt() else 0

            llQuota?.visibility = View.VISIBLE
            tvQuotaText?.text = String.format(Locale.US, "%.1f GB / %.1f GB (%d%%)", usedGb, limitGb, pct)
            pbQuota?.progress = pct
            tvBtnOpenDrive?.text = "📂 Open My Drive"

            btnOpenDrive?.setOnClickListener {
                val driveUrl = "https://accounts.google.com/ServiceLogin?service=wise&passive=1209600&continue=https://drive.google.com/drive/my-drive&Email=$email"
                loadUrl(driveUrl)
            }
        } else {
            tvName?.text = "Google Drive"
            tvEmail?.text = "Link account for 15GB Cloud Storage & 0MB Leech"
            tvBadge?.text = "Not Linked"
            tvBadge?.setTextColor(Color.parseColor("#9CA3AF"))
            llQuota?.visibility = View.GONE
            tvBtnOpenDrive?.text = "🔗 Link Account"

            btnOpenDrive?.setOnClickListener {
                val act = activity as? BridgeActivity
                act?.runOnUiThread {
                    act.bridge?.webView?.evaluateJavascript("window.switchTab && window.switchTab('drive')", null)
                }
            }
        }

        btnSwitchDrive?.setOnClickListener {
            val act = activity as? BridgeActivity
            act?.runOnUiThread {
                act.bridge?.webView?.evaluateJavascript("window.switchTab && window.switchTab('drive')", null)
            }
        }
    }

    private fun setupSpeedDial(view: View) {
        view.findViewById<View>(R.id.shortcut_google)?.setOnClickListener { loadUrl("https://www.google.com") }
        view.findViewById<View>(R.id.shortcut_gdrive)?.setOnClickListener {
            val prefs = requireContext().getSharedPreferences("cdl_gdrive_prefs", Context.MODE_PRIVATE)
            val isConnected = prefs.getBoolean("is_connected", false)
            val email = prefs.getString("active_email", null)
            if (isConnected && !email.isNullOrEmpty()) {
                loadUrl("https://accounts.google.com/ServiceLogin?service=wise&passive=1209600&continue=https://drive.google.com/drive/my-drive&Email=$email")
            } else {
                loadUrl("https://drive.google.com")
            }
        }
        view.findViewById<View>(R.id.shortcut_youtube)?.setOnClickListener { loadUrl("https://www.youtube.com") }
        view.findViewById<View>(R.id.shortcut_sinhalasub)?.setOnClickListener { loadUrl("https://sinhalasub.lk") }
        view.findViewById<View>(R.id.shortcut_yts)?.setOnClickListener { loadUrl("https://yts.mx") }
        view.findViewById<View>(R.id.shortcut_tgx)?.setOnClickListener { loadUrl("https://torrentgalaxy.to") }
        view.findViewById<View>(R.id.shortcut_1337x)?.setOnClickListener { loadUrl("https://1337x.to") }
        view.findViewById<View>(R.id.shortcut_baiscope)?.setOnClickListener { loadUrl("https://www.baiscopelk.com") }
        view.findViewById<View>(R.id.shortcut_wiki)?.setOnClickListener { loadUrl("https://www.wikipedia.org") }

        updateGoogleDriveUI()

        val etHomeSearch = view.findViewById<EditText>(R.id.et_home_search)
        val btnHomeGo = view.findViewById<View>(R.id.btn_home_search_go)

        val doHomeSearch = {
            val q = etHomeSearch?.text?.toString()?.trim() ?: ""
            if (q.isNotEmpty()) {
                hideKeyboard()
                loadUrl(BrowserSettings.normalizeUrl(requireContext(), q))
            }
        }

        btnHomeGo?.setOnClickListener { doHomeSearch() }
        etHomeSearch?.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_GO ||
                actionId == EditorInfo.IME_ACTION_SEARCH ||
                actionId == EditorInfo.IME_ACTION_DONE ||
                (event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)
            ) {
                doHomeSearch()
                true
            } else false
        }
    }

    private fun bindActiveTab() {
        val tab = WebViewTabManager.getActiveTab() ?: return
        val container = webViewContainer ?: return

        try {
            (tab.webView.parent as? ViewGroup)?.removeView(tab.webView)
        } catch (_: Exception) {}
        container.removeAllViews()
        setupWebViewClients(tab)
        container.addView(tab.webView)

        if (tab.url == "about:home" || tab.url.isEmpty() || tab.url == "about:blank") {
            showStartPage()
        } else {
            hideStartPage()
        }
        updateUIState()
        updateSnifferPillState(BrowserMediaSniffer.getMediaCountForTab(tab.id))
    }

    private fun setupWebViewClients(tab: WebViewTab) {
        val wv = tab.webView

        wv.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(
                view: WebView?,
                request: WebResourceRequest?
            ): WebResourceResponse? {
                val reqUrl = request?.url?.toString() ?: return null

                // Native High-Speed AdBlock with gorhill/uBlock Origin rules
                if (AdBlockEngine.isAd(reqUrl, context)) {
                    val mime = when {
                        reqUrl.contains(".js") || reqUrl.contains("javascript") -> "application/javascript"
                        reqUrl.contains(".css") -> "text/css"
                        reqUrl.contains(".png") -> "image/png"
                        reqUrl.contains(".jpg") || reqUrl.contains(".jpeg") -> "image/jpeg"
                        reqUrl.contains(".gif") -> "image/gif"
                        else -> "text/plain"
                    }
                    val data = if (mime == "application/javascript") {
                        "/* [uBlock Origin] Blocked */".toByteArray()
                    } else {
                        ByteArray(0)
                    }
                    return WebResourceResponse(mime, "UTF-8", ByteArrayInputStream(data))
                }

                // Universal Multi-Layer Video Sniffer
                BrowserMediaSniffer.checkAndSniffRequest(tab.id, tab.url, tab.title, reqUrl)

                return super.shouldInterceptRequest(view, request)
            }

            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                val targetUrl = request?.url?.toString() ?: return false

                // Block rogue popup/ad redirect schemes or ad URLs
                if (AdBlockEngine.isAd(targetUrl, context)) {
                    return true // Block navigation to ad host!
                }

                // Intercept magnet links
                if (targetUrl.startsWith("magnet:", ignoreCase = true)) {
                    val rawDn = try { Uri.parse(targetUrl).getQueryParameter("dn") } catch (_: Exception) { null }
                    val magnetTitle = rawDn?.replace("+", " ")?.ifEmpty { "BitTorrent Magnet Stream" } ?: "BitTorrent Magnet Stream"
                    activity?.runOnUiThread {
                        showMagnetOptionsDialog(targetUrl, magnetTitle)
                    }
                    return true
                }

                // Intercept direct .torrent links
                if (targetUrl.contains(".torrent", ignoreCase = true)) {
                    val torrentName = try {
                        Uri.parse(targetUrl).lastPathSegment ?: "download.torrent"
                    } catch (_: Exception) {
                        "download.torrent"
                    }
                    activity?.runOnUiThread {
                        showDownloadOptionsDialog(
                            url = targetUrl,
                            fileName = torrentName,
                            mimeType = "application/x-bittorrent",
                            contentLength = -1L,
                            contentDisposition = null
                        )
                    }
                    return true
                }

                if (targetUrl.startsWith("http://") || targetUrl.startsWith("https://")) {
                    return false
                }

                // Handle external protocols (intent, tel, mailto, etc.)
                try {
                    val intent = Intent.parseUri(targetUrl, Intent.URI_INTENT_SCHEME)
                    if (intent != null) {
                        startActivity(intent)
                        return true
                    }
                } catch (_: Exception) {}

                return true
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                val currentUrl = url ?: return
                activity?.runOnUiThread {
                    if (currentUrl == "about:blank" || currentUrl == "about:home") {
                        showStartPage()
                        return@runOnUiThread
                    }
                    BrowserMediaSniffer.clearForTab(tab.id)
                    updateSnifferPillState(0)
                    tab.isLoading = true
                    tab.url = currentUrl
                    tab.progress = 10
                    progressBar?.visibility = View.VISIBLE
                    progressBar?.progress = 10
                    if (addressBar?.hasFocus() != true) {
                        addressBar?.setText(currentUrl)
                    }
                    hideStartPage()
                    updateSecurityIndicator(currentUrl.startsWith("https://"))
                }
                // Inject early so cosmetic CSS, anti-adblock, and media sniffer hooks are ready before DOM finishes parsing
                if (view != null && currentUrl != "about:blank" && currentUrl != "about:home") {
                    context?.let { ctx -> ExtensionManager.injectActiveExtensions(ctx, view) }
                    view.evaluateJavascript(BrowserMediaSniffer.MEDIA_SNIFFER_HOOK_JS, null)
                }
            }

            override fun onPageCommitVisible(view: WebView?, url: String?) {
                super.onPageCommitVisible(view, url)
                val currentUrl = url ?: return
                if (view != null && currentUrl != "about:blank" && currentUrl != "about:home") {
                    context?.let { ctx -> ExtensionManager.injectActiveExtensions(ctx, view) }
                    view.evaluateJavascript(BrowserMediaSniffer.MEDIA_SNIFFER_HOOK_JS, null)
                }
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                val currentUrl = url ?: return
                activity?.runOnUiThread {
                    if (currentUrl == "about:blank" || currentUrl == "about:home") {
                        showStartPage()
                        return@runOnUiThread
                    }
                    tab.isLoading = false
                    tab.progress = 100
                    progressBar?.visibility = View.GONE
                    if (addressBar?.hasFocus() != true) {
                        addressBar?.setText(currentUrl)
                    }
                    val pageTitle = view?.title ?: currentUrl
                    tab.title = pageTitle
                    if (!tab.isPrivate) {
                        HistoryRepository.addHistory(requireContext(), pageTitle, currentUrl, false)
                    }
                    if (view != null) {
                        ExtensionManager.injectActiveExtensions(requireContext(), view)
                        view.evaluateJavascript(BrowserMediaSniffer.MEDIA_SNIFFER_HOOK_JS, null)
                        if (currentUrl.contains("usersdrive") || currentUrl.contains("userdrive")) {
                            view.evaluateJavascript("""
                                (function() {
                                    try {
                                        var timer = setInterval(function() {
                                            var direct = document.querySelector('a[href*="userdrive.org"], a[href*="dl.usersdrive.com"], a.btn-download[href*="/d/"], a[href*="/d/"]');
                                            if (direct && direct.href && direct.href.startsWith('http')) {
                                                clearInterval(timer);
                                                return;
                                            }
                                            var cf = document.querySelector('[name="cf-turnstile-response"]');
                                            var hasTurnstile = !!document.querySelector('.cf-turnstile, [data-sitekey]');
                                            if (hasTurnstile && (!cf || !cf.value)) return;
                                            var btn = document.querySelector('#downloadbtn, .downloadbtn, button[id="downloadbtn"]');
                                            if (btn && !btn.disabled && btn.offsetParent !== null) {
                                                btn.click();
                                            }
                                        }, 1500);
                                        setTimeout(function() { clearInterval(timer); }, 30000);
                                    } catch(e) {}
                                })();
                            """.trimIndent(), null)
                        }
                    }
                    updateNavButtons()
                }
            }

            override fun onReceivedSslError(
                view: WebView?,
                handler: SslErrorHandler?,
                error: SslError?
            ) {
                tab.isSecure = false
                btnSecurityInfo?.setColorFilter(Color.parseColor("#E74C3C"))
                super.onReceivedSslError(view, handler, error)
            }
        }

        wv.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                super.onProgressChanged(view, newProgress)
                activity?.runOnUiThread {
                    tab.progress = newProgress
                    progressBar?.progress = newProgress
                    if (newProgress >= 100) {
                        progressBar?.visibility = View.GONE
                    } else if (tab.url != "about:home") {
                        progressBar?.visibility = View.VISIBLE
                    }
                }
                // Inject during intermediate loads to keep hiding dynamic ads
                if (view != null && (newProgress == 30 || newProgress == 70)) {
                    context?.let { ctx -> ExtensionManager.injectActiveExtensions(ctx, view) }
                }
            }

            override fun onReceivedTitle(view: WebView?, title: String?) {
                super.onReceivedTitle(view, title)
                if (!title.isNullOrEmpty() && tab.url != "about:home") {
                    tab.title = title
                }
            }

            override fun onCreateWindow(
                view: WebView?,
                isDialog: Boolean,
                isUserGesture: Boolean,
                resultMsg: Message?
            ): Boolean {
                if (resultMsg == null) return false
                val transport = resultMsg.obj as? WebView.WebViewTransport ?: return false

                val tempWv = WebView(requireContext()).apply {
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(
                            v: WebView?,
                            request: WebResourceRequest?
                        ): Boolean {
                            val targetUrl = request?.url?.toString() ?: return false

                            // Block if recognized as an ad or popunder
                            if (AdBlockEngine.isAd(targetUrl, requireContext())) {
                                v?.destroy()
                                return true
                            }

                            // Open valid popup tab as a new browser tab!
                            activity?.runOnUiThread {
                                val newTab = WebViewTabManager.createTab(
                                    requireContext(),
                                    targetUrl,
                                    WebViewTabManager.isPrivateMode
                                )
                                setupWebViewClients(newTab)
                                bindActiveTab()
                                Toast.makeText(requireContext(), "Opened in new tab", Toast.LENGTH_SHORT).show()
                                v?.destroy()
                            }
                            return true
                        }
                    }
                }

                transport.webView = tempWv
                resultMsg.sendToTarget()
                return true
            }

            override fun onJsAlert(
                view: WebView?,
                url: String?,
                message: String?,
                result: JsResult?
            ): Boolean {
                val msg = message?.lowercase(Locale.ROOT) ?: ""
                if (msg.contains("virus") || msg.contains("infected") || msg.contains("trojan") || msg.contains("hacked")) {
                    result?.cancel()
                    return true
                }
                return super.onJsAlert(view, url, message, result)
            }

            override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                if (customView != null) {
                    callback?.onCustomViewHidden()
                    return
                }
                customView = view
                customViewCallback = callback
                activity?.runOnUiThread {
                    browserMainContent?.visibility = View.GONE
                    fullscreenContainer?.removeAllViews()
                    fullscreenContainer?.addView(view)
                    fullscreenContainer?.visibility = View.VISIBLE
                    activity?.window?.decorView?.systemUiVisibility = (
                        View.SYSTEM_UI_FLAG_FULLSCREEN or
                        View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                        View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    )
                }
            }

            override fun onHideCustomView() {
                activity?.runOnUiThread {
                    fullscreenContainer?.visibility = View.GONE
                    fullscreenContainer?.removeAllViews()
                    browserMainContent?.visibility = View.VISIBLE
                    customView = null
                    customViewCallback?.onCustomViewHidden()
                    customViewCallback = null
                    activity?.window?.decorView?.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
                }
            }

            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                fileUploadCallback?.onReceiveValue(null)
                fileUploadCallback = filePathCallback
                try {
                    val intent = fileChooserParams?.createIntent() ?: Intent(Intent.ACTION_GET_CONTENT).apply {
                        type = "*/*"
                        addCategory(Intent.CATEGORY_OPENABLE)
                    }
                    fileChooserLauncher.launch(intent)
                    return true
                } catch (e: Exception) {
                    fileUploadCallback = null
                    return false
                }
            }
        }

        // Smart Universal Download Listener for Files, Videos, Torrents & Archives
        wv.setDownloadListener { downloadUrl, _, contentDisposition, mimetype, contentLength ->
            try {
                val guessedName = URLUtil.guessFileName(downloadUrl, contentDisposition, mimetype)
                activity?.runOnUiThread {
                    showDownloadOptionsDialog(
                        url = downloadUrl,
                        fileName = guessedName,
                        mimeType = mimetype ?: "",
                        contentLength = contentLength,
                        contentDisposition = contentDisposition
                    )
                }
            } catch (e: Exception) {
                activity?.runOnUiThread {
                    Toast.makeText(requireContext(), "Download error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun updateSecurityIndicator(isSecure: Boolean) {
        val tab = WebViewTabManager.getActiveTab()
        tab?.isSecure = isSecure
        if (isSecure) {
            btnSecurityInfo?.setColorFilter(Color.parseColor("#34A853"))
        } else {
            btnSecurityInfo?.setColorFilter(Color.parseColor("#9AA0A6"))
        }
    }

    private fun showStartPage() {
        startPageContainer?.visibility = View.VISIBLE
        webViewContainer?.visibility = View.GONE
        updateSnifferPillState(0)
        addressBar?.setText("")
        addressBar?.hint = "Search or enter address"
        val tab = WebViewTabManager.getActiveTab()
        tab?.url = "about:home"
        btnSecurityInfo?.setColorFilter(Color.parseColor("#9AA0A6"))
        if (tab?.isPrivate == true) {
            tvStartBrand?.text = "🕶️ Private Browser"
        } else {
            tvStartBrand?.text = "Cloud Browser"
        }
        view?.findViewById<EditText>(R.id.et_home_search)?.setText("")
        updateGoogleDriveUI()
        updateNavButtons()
    }

    private fun hideStartPage() {
        startPageContainer?.visibility = View.GONE
        webViewContainer?.visibility = View.VISIBLE
    }

    fun loadUrl(url: String) {
        val target = url.trim()
        if (target.isEmpty() || target == "about:home") {
            showStartPage()
            return
        }
        hideStartPage()
        val tab = WebViewTabManager.getActiveTab() ?: return
        tab.url = target
        addressBar?.setText(target)
        tab.webView.loadUrl(target)
    }

    private fun updateUIState() {
        val count = WebViewTabManager.getTabCount()
        tvTabCounter?.text = count.toString()
        val tab = WebViewTabManager.getActiveTab()
        if (tab != null) {
            if (tab.url != "about:home" && tab.url != "about:blank" && tab.url.isNotEmpty() && addressBar?.hasFocus() != true) {
                addressBar?.setText(tab.url)
            } else if (addressBar?.hasFocus() != true) {
                addressBar?.setText("")
                addressBar?.hint = "Search or enter address"
            }
            updateNavButtons()
        }
    }

    private fun updateNavButtons() {
        val tab = WebViewTabManager.getActiveTab()
        val canBack = tab?.webView?.canGoBack() == true || startPageContainer?.visibility != View.VISIBLE
        val canFwd = tab?.webView?.canGoForward() == true

        btnBack?.alpha = if (canBack) 1.0f else 0.4f
        btnForward?.alpha = if (canFwd) 1.0f else 0.4f
    }

    fun handleBackPress(): Boolean {
        if (customView != null) {
            activity?.runOnUiThread {
                fullscreenContainer?.visibility = View.GONE
                fullscreenContainer?.removeAllViews()
                browserMainContent?.visibility = View.VISIBLE
                customView = null
                customViewCallback?.onCustomViewHidden()
                customViewCallback = null
                activity?.window?.decorView?.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
            }
            return true
        }
        if (startPageContainer?.visibility == View.VISIBLE) {
            return false
        }
        val tab = WebViewTabManager.getActiveTab()
        if (tab?.webView?.canGoBack() == true) {
            tab.webView.goBack()
            return true
        } else {
            showStartPage()
            return true
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        if (customView != null) {
            fullscreenContainer?.removeAllViews()
            customView = null
            customViewCallback?.onCustomViewHidden()
            customViewCallback = null
        }
        fileUploadCallback?.onReceiveValue(null)
        fileUploadCallback = null
    }

    private fun showTabOverview() {
        val dialog = Dialog(requireContext(), android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val dialogView = layoutInflater.inflate(R.layout.dialog_tab_overview, null)
        dialog.setContentView(dialogView)

        val grid = dialogView.findViewById<GridLayout>(R.id.tabs_grid)
        val scrollContainer = dialogView.findViewById<View>(R.id.tabs_scroll_container)
        val emptyContainer = dialogView.findViewById<View>(R.id.empty_tabs_container)
        val btnNormal = dialogView.findViewById<TextView>(R.id.btn_filter_normal)
        val btnPrivate = dialogView.findViewById<TextView>(R.id.btn_filter_private)
        val btnCloseAll = dialogView.findViewById<TextView>(R.id.btn_close_all_tabs)
        val btnCloseOverview = dialogView.findViewById<View>(R.id.btn_close_overview)
        val btnNewTab = dialogView.findViewById<View>(R.id.btn_new_tab_action)
        val btnEmptyNewTab = dialogView.findViewById<View>(R.id.btn_empty_new_tab)
        val tvNewTabBtnText = dialogView.findViewById<TextView>(R.id.tv_new_tab_btn_text)

        var showPrivate = WebViewTabManager.isPrivateMode

        fun renderCards() {
            grid.removeAllViews()

            val normalCount = WebViewTabManager.getTabCount(false)
            val privateCount = WebViewTabManager.getTabCount(true)

            btnNormal?.text = "Tabs ($normalCount)"
            btnPrivate?.text = "Private ($privateCount)"

            if (!showPrivate) {
                btnNormal?.setBackgroundResource(R.drawable.bg_tab_pill_active)
                btnNormal?.setTextColor(Color.WHITE)
                btnPrivate?.setBackgroundResource(R.drawable.bg_tab_pill_inactive)
                btnPrivate?.setTextColor(Color.parseColor("#9AA0A6"))
                tvNewTabBtnText?.text = "New Tab"
            } else {
                btnNormal?.setBackgroundResource(R.drawable.bg_tab_pill_inactive)
                btnNormal?.setTextColor(Color.parseColor("#9AA0A6"))
                btnPrivate?.setBackgroundResource(R.drawable.bg_tab_pill_active)
                btnPrivate?.setTextColor(Color.WHITE)
                tvNewTabBtnText?.text = "New Private Tab"
            }

            val tabs = WebViewTabManager.getTabs(showPrivate)
            val activeTab = WebViewTabManager.getActiveTab()

            if (tabs.isEmpty()) {
                scrollContainer?.visibility = View.GONE
                emptyContainer?.visibility = View.VISIBLE
                btnCloseAll?.visibility = View.GONE
            } else {
                scrollContainer?.visibility = View.VISIBLE
                emptyContainer?.visibility = View.GONE
                btnCloseAll?.visibility = View.VISIBLE

                val inflater = LayoutInflater.from(requireContext())
                for (tab in tabs) {
                    val card = inflater.inflate(R.layout.item_browser_tab_card, grid, false)
                    val tvTitle = card.findViewById<TextView>(R.id.tab_card_title)
                    val tvUrl = card.findViewById<TextView>(R.id.tab_card_url)
                    val tvBadge = card.findViewById<TextView>(R.id.tab_card_badge)
                    val tvActive = card.findViewById<TextView>(R.id.tab_card_active_indicator)
                    val rootCard = card.findViewById<View>(R.id.tab_card_root)

                    val isSelected = (activeTab?.id == tab.id)
                    rootCard?.setBackgroundResource(if (isSelected) R.drawable.bg_tab_card_active else R.drawable.bg_tab_card_normal)
                    tvActive?.visibility = if (isSelected) View.VISIBLE else View.GONE

                    val displayTitle = if (tab.url == "about:home" || tab.url.isEmpty() || tab.url == "about:blank") {
                        if (tab.isPrivate) "Private Start Page" else "Start Page"
                    } else {
                        tab.title.ifEmpty { tab.url }
                    }
                    tvTitle?.text = displayTitle

                    val displayUrl = if (tab.url == "about:home" || tab.url.isEmpty() || tab.url == "about:blank") {
                        "about:home"
                    } else {
                        tab.url
                    }
                    tvUrl?.text = displayUrl
                    tvBadge?.text = if (tab.isPrivate) "🕶️ Private" else "Standard"

                    card.findViewById<View>(R.id.tab_card_close)?.setOnClickListener {
                        WebViewTabManager.closeTab(tab.id)
                        renderCards()
                        bindActiveTab()
                    }

                    card.setOnClickListener {
                        WebViewTabManager.switchTab(tab.id)
                        bindActiveTab()
                        dialog.dismiss()
                    }

                    grid.addView(card)
                }
            }
        }

        btnNormal?.setOnClickListener {
            showPrivate = false
            renderCards()
        }

        btnPrivate?.setOnClickListener {
            showPrivate = true
            renderCards()
        }

        btnCloseOverview?.setOnClickListener {
            if (WebViewTabManager.getTabCount() == 0) {
                WebViewTabManager.createTab(requireContext(), "about:home", false)
                bindActiveTab()
            }
            dialog.dismiss()
        }

        val openNewTabAction = {
            WebViewTabManager.createTab(requireContext(), "about:home", showPrivate)
            bindActiveTab()
            dialog.dismiss()
        }

        btnNewTab?.setOnClickListener { openNewTabAction() }
        btnEmptyNewTab?.setOnClickListener { openNewTabAction() }

        btnCloseAll?.setOnClickListener {
            WebViewTabManager.closeAllTabs(showPrivate)
            renderCards()
            bindActiveTab()
        }

        renderCards()
        dialog.setOnDismissListener {
            if (WebViewTabManager.getTabCount() == 0) {
                WebViewTabManager.createTab(requireContext(), "about:home", false)
            }
            bindActiveTab()
        }
        dialog.show()
    }

    private fun toggleAdBlock() {
        AdBlockEngine.isEnabled = !AdBlockEngine.isEnabled
        val status = if (AdBlockEngine.isEnabled) "🛡️ AdBlock & Tracking Shield: ACTIVE" else "⚠️ AdBlock Shield: DISABLED"
        Toast.makeText(requireContext(), status, Toast.LENGTH_SHORT).show()
    }

    private fun showSiteSecurityInfo() {
        val tab = WebViewTabManager.getActiveTab() ?: return
        val url = tab.url
        val isHttps = url.startsWith("https://")
        val host = try { Uri.parse(url).host ?: url } catch (_: Exception) { url }

        AlertDialog.Builder(requireContext())
            .setTitle(if (isHttps) "🔒 Secure Connection" else "⚠️ Not Secure")
            .setMessage(
                if (isHttps) {
                    "Domain: $host\n\nYour connection to this site is encrypted and private. AdBlock Shield is active."
                } else {
                    "Domain: $host\n\nYou should not enter sensitive information (passwords, credit cards) on this site because it is not encrypted."
                }
            )
            .setPositiveButton("OK", null)
            .show()
    }

    private fun showMediaSnifferBottomSheet(tabId: String) {
        val bottomSheet = BottomSheetDialog(requireContext())
        val dialogView = layoutInflater.inflate(R.layout.dialog_media_sniffer, null)
        bottomSheet.setContentView(dialogView)
        (dialogView.parent as? View)?.setBackgroundColor(Color.TRANSPARENT)

        val btnClose = dialogView.findViewById<View>(R.id.btn_sniffer_close)
        btnClose?.setOnClickListener { bottomSheet.dismiss() }

        val listContainer = dialogView.findViewById<LinearLayout>(R.id.ll_sniffer_list)
        val emptyContainer = dialogView.findViewById<View>(R.id.ll_sniffer_empty)
        val tvSub = dialogView.findViewById<TextView>(R.id.tv_sniffer_count_sub)

        val mediaList = BrowserMediaSniffer.getMediaForTab(tabId)
        val count = mediaList.size

        tvSub?.text = if (count == 1) "1 stream detected on this page" else "$count streams detected on this page"

        if (mediaList.isEmpty()) {
            emptyContainer?.visibility = View.VISIBLE
            listContainer?.visibility = View.GONE
        } else {
            emptyContainer?.visibility = View.GONE
            listContainer?.visibility = View.VISIBLE
            listContainer?.removeAllViews()

            val inflater = LayoutInflater.from(requireContext())
            for (media in mediaList) {
                val row = inflater.inflate(R.layout.item_sniffed_media, listContainer, false)

                val tvTitle = row.findViewById<TextView>(R.id.tv_media_title)
                val tvFormat = row.findViewById<TextView>(R.id.tv_media_format)
                val tvRes = row.findViewById<TextView>(R.id.tv_media_res)
                val tvMeta = row.findViewById<TextView>(R.id.tv_media_meta)
                val btnDownload = row.findViewById<View>(R.id.btn_download_task)
                val btnPlay = row.findViewById<View>(R.id.btn_play_media)
                val btnCloud = row.findViewById<View>(R.id.btn_cloud_leech)
                val btnCopy = row.findViewById<View>(R.id.btn_copy_link)

                tvTitle.text = media.title
                tvFormat.text = media.format

                if (!media.resolution.isNullOrBlank()) {
                    tvRes.visibility = View.VISIBLE
                    tvRes.text = media.resolution
                } else {
                    tvRes.visibility = View.GONE
                }

                val metaParts = mutableListOf<String>()
                if (media.sizeBytes > 0) {
                    val mb = media.sizeBytes / (1024.0 * 1024.0)
                    metaParts.add(String.format(Locale.US, "%.1f MB", mb))
                }
                if (!media.duration.isNullOrBlank()) {
                    metaParts.add(media.duration)
                }
                tvMeta.text = if (metaParts.isNotEmpty()) metaParts.joinToString(" • ") else "Direct Stream"

                // 1. Download directly to app's Downloads Tab & Switch smoothly!
                btnDownload.setOnClickListener {
                    bottomSheet.dismiss()
                    val ext = if (media.format.contains("HLS", ignoreCase = true)) ".mp4" else if (media.format.contains("WebM", ignoreCase = true)) ".webm" else ".mp4"
                    val safeName = "${media.title.replace(Regex("""[/\\?%*:|"<>]+"""), "_")}$ext"

                    NativeDownloadPlugin.enqueueDownload(
                        context = requireContext(),
                        url = media.url,
                        title = media.title,
                        filename = safeName,
                        category = "browser",
                        referer = media.referer,
                        cookie = media.cookie
                    )

                    Toast.makeText(requireContext(), "⚡ Download started! Opening Downloads Tab...", Toast.LENGTH_SHORT).show()

                    // Seamlessly hide browser container and switch directly to Downloads Tab
                    activity?.runOnUiThread {
                        try {
                            val contentRoot = activity?.findViewById<ViewGroup>(android.R.id.content)
                            contentRoot?.findViewById<View>(R.id.native_browser_container)?.visibility = View.GONE

                            (activity as? BridgeActivity)?.bridge?.eval(
                                """
                                if (window.switchTab) window.switchTab('downloads');
                                if (window.switchToDeviceDownloadsSubTab) window.switchToDeviceDownloadsSubTab();
                                if (window.startDownloadsPolling) window.startDownloadsPolling();
                                """.trimIndent(),
                                null
                            )
                        } catch (_: Exception) {}
                    }
                }

                // 2. Play in App ExoPlayer
                btnPlay.setOnClickListener {
                    bottomSheet.dismiss()
                    val intent = Intent(requireContext(), PlayerActivity::class.java).apply {
                        putExtra("url", media.url)
                        putExtra("title", media.title)
                    }
                    startActivity(intent)
                }

                // 3. Cloud Leech to Drive
                btnCloud.setOnClickListener {
                    bottomSheet.dismiss()
                    startCloudLeechToDrive(media.url, media.title)
                }

                // 4. Copy URL
                btnCopy.setOnClickListener {
                    val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("Media URL", media.url))
                    Toast.makeText(requireContext(), "Media URL copied to clipboard!", Toast.LENGTH_SHORT).show()
                }

                listContainer?.addView(row)
            }
        }

        bottomSheet.show()
    }

    private fun showDownloadOptionsDialog(
        url: String,
        fileName: String,
        mimeType: String,
        contentLength: Long,
        contentDisposition: String?
    ) {
        val bottomSheet = BottomSheetDialog(requireContext())
        val dialogView = layoutInflater.inflate(R.layout.dialog_browser_download, null)
        bottomSheet.setContentView(dialogView)
        (dialogView.parent as? View)?.setBackgroundColor(Color.TRANSPARENT)

        val tvIcon = dialogView.findViewById<TextView>(R.id.tv_dl_icon)
        val tvTitle = dialogView.findViewById<TextView>(R.id.tv_dl_title)
        val tvMeta = dialogView.findViewById<TextView>(R.id.tv_dl_meta)
        val tvUrl = dialogView.findViewById<TextView>(R.id.tv_dl_url)
        val btnClose = dialogView.findViewById<View>(R.id.btn_dl_close)

        val btnCloud = dialogView.findViewById<View>(R.id.btn_action_cloud_leech)
        val btnLocal = dialogView.findViewById<View>(R.id.btn_action_local_download)
        val btnPlay = dialogView.findViewById<View>(R.id.btn_action_play_video)
        val btnCopy = dialogView.findViewById<View>(R.id.btn_action_copy_url)

        val lowerName = fileName.lowercase()
        val lowerMime = mimeType.lowercase()
        val isVideo = lowerMime.startsWith("video/") || lowerName.endsWith(".mp4") || lowerName.endsWith(".mkv") || lowerName.endsWith(".webm") || lowerName.endsWith(".avi") || lowerName.endsWith(".mov") || lowerName.endsWith(".m3u8")
        val isTorrent = lowerMime.contains("torrent") || lowerName.endsWith(".torrent")
        val isZip = lowerName.endsWith(".zip") || lowerName.endsWith(".rar") || lowerName.endsWith(".7z") || lowerName.endsWith(".tar") || lowerName.endsWith(".gz")
        val isApk = lowerName.endsWith(".apk")

        tvIcon?.text = when {
            isVideo -> "🎬"
            isTorrent -> "🧲"
            isZip -> "📦"
            isApk -> "🤖"
            else -> "📥"
        }

        tvTitle?.text = fileName
        val metaParts = mutableListOf<String>()
        if (contentLength > 0) {
            val mb = contentLength / (1024.0 * 1024.0)
            metaParts.add(String.format(Locale.US, "%.1f MB", mb))
        }
        metaParts.add(when {
            isVideo -> "Video Media"
            isTorrent -> "Torrent File"
            isZip -> "Archive File"
            isApk -> "Android Package"
            mimeType.isNotEmpty() -> mimeType
            else -> "Direct Download"
        })
        tvMeta?.text = metaParts.joinToString(" • ")
        tvUrl?.text = url

        btnClose?.setOnClickListener { bottomSheet.dismiss() }

        // 1. Cloud Leech to Google Drive (0 MB Mobile Data)
        btnCloud?.setOnClickListener {
            bottomSheet.dismiss()
            startCloudLeechToDrive(url, fileName)
        }

        // 2. Turbo Download to Device
        btnLocal?.setOnClickListener {
            bottomSheet.dismiss()
            val activeTab = WebViewTabManager.getActiveTab()
            val referer = activeTab?.url ?: url
            val cookie = CookieManager.getInstance().getCookie(url)

            NativeDownloadPlugin.enqueueDownload(
                context = requireContext(),
                url = url,
                title = fileName,
                filename = fileName,
                category = "browser",
                referer = referer,
                cookie = cookie
            )

            Toast.makeText(requireContext(), "⚡ Turbo Download started! Opening Downloads Tab...", Toast.LENGTH_SHORT).show()

            activity?.runOnUiThread {
                try {
                    val contentRoot = activity?.findViewById<ViewGroup>(android.R.id.content)
                    contentRoot?.findViewById<View>(R.id.native_browser_container)?.visibility = View.GONE

                    (activity as? BridgeActivity)?.bridge?.eval(
                        """
                        if (window.switchTab) window.switchTab('downloads');
                        if (window.switchToDeviceDownloadsSubTab) window.switchToDeviceDownloadsSubTab();
                        if (window.startDownloadsPolling) window.startDownloadsPolling();
                        """.trimIndent(),
                        null
                    )
                } catch (_: Exception) {}
            }
        }

        // 3. Play Video (If media)
        if (isVideo) {
            btnPlay?.visibility = View.VISIBLE
            btnPlay?.setOnClickListener {
                bottomSheet.dismiss()
                val intent = Intent(requireContext(), PlayerActivity::class.java).apply {
                    putExtra("url", url)
                    putExtra("title", fileName)
                }
                startActivity(intent)
            }
        } else {
            btnPlay?.visibility = View.GONE
        }

        // 4. Copy URL
        btnCopy?.setOnClickListener {
            val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("Download URL", url))
            Toast.makeText(requireContext(), "Download URL copied to clipboard!", Toast.LENGTH_SHORT).show()
            bottomSheet.dismiss()
        }

        bottomSheet.show()
    }

    private fun showMagnetOptionsDialog(magnetUrl: String, magnetTitle: String) {
        val bottomSheet = BottomSheetDialog(requireContext())
        val dialogView = layoutInflater.inflate(R.layout.dialog_browser_download, null)
        bottomSheet.setContentView(dialogView)
        (dialogView.parent as? View)?.setBackgroundColor(Color.TRANSPARENT)

        val tvIcon = dialogView.findViewById<TextView>(R.id.tv_dl_icon)
        val tvTitle = dialogView.findViewById<TextView>(R.id.tv_dl_title)
        val tvMeta = dialogView.findViewById<TextView>(R.id.tv_dl_meta)
        val tvUrl = dialogView.findViewById<TextView>(R.id.tv_dl_url)
        val btnClose = dialogView.findViewById<View>(R.id.btn_dl_close)

        val btnCloud = dialogView.findViewById<View>(R.id.btn_action_cloud_leech)
        val btnLocal = dialogView.findViewById<View>(R.id.btn_action_local_download)
        val tvLocalSub = dialogView.findViewById<TextView>(R.id.tv_local_dl_sub)
        val btnPlay = dialogView.findViewById<View>(R.id.btn_action_play_video)
        val btnCopy = dialogView.findViewById<View>(R.id.btn_action_copy_url)

        tvIcon?.text = "🧲"
        tvTitle?.text = magnetTitle
        tvMeta?.text = "BitTorrent Magnet Link • High-Speed Swarm"
        tvUrl?.text = magnetUrl.take(65) + if (magnetUrl.length > 65) "..." else ""
        tvLocalSub?.text = "Download via Built-in Torrent Downloader"
        btnPlay?.visibility = View.GONE

        btnClose?.setOnClickListener { bottomSheet.dismiss() }

        // 1. Cloud Leech to Google Drive (0 MB Data)
        btnCloud?.setOnClickListener {
            bottomSheet.dismiss()
            startCloudLeechToDrive(magnetUrl, magnetTitle)
        }

        // 2. Download via Built-in Torrent Downloader
        btnLocal?.setOnClickListener {
            bottomSheet.dismiss()
            Toast.makeText(requireContext(), "🧲 Adding Torrent to Downloader Tab...", Toast.LENGTH_SHORT).show()

            activity?.runOnUiThread {
                try {
                    val contentRoot = activity?.findViewById<ViewGroup>(android.R.id.content)
                    contentRoot?.findViewById<View>(R.id.native_browser_container)?.visibility = View.GONE

                    val safeMagnet = magnetUrl.replace("'", "\\'")
                    (activity as? BridgeActivity)?.bridge?.eval(
                        """
                        if (window.switchTab) window.switchTab('downloads');
                        var dlInput = document.getElementById('dlUniversalInput');
                        if (dlInput) {
                            dlInput.value = '$safeMagnet';
                            if (window.handleUniversalDownload) {
                                window.handleUniversalDownload();
                            }
                        }
                        """.trimIndent(),
                        null
                    )
                } catch (_: Exception) {}
            }
        }

        // 3. Copy Magnet Link
        btnCopy?.setOnClickListener {
            val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("Magnet Link", magnetUrl))
            Toast.makeText(requireContext(), "Magnet link copied to clipboard!", Toast.LENGTH_SHORT).show()
            bottomSheet.dismiss()
        }

        bottomSheet.show()
    }

    private fun startCloudLeechToDrive(mediaUrl: String, title: String) {
        val ctx = context?.applicationContext ?: return
        Toast.makeText(requireContext(), "🚀 Cloud Leeching directly to Google Drive...", Toast.LENGTH_LONG).show()

        NativeGdrivePlugin.performCloudTransfer(
            context = ctx,
            sourceUrl = mediaUrl,
            rawTitle = title,
            onStart = {
                activity?.runOnUiThread {
                    Toast.makeText(ctx, "☁️ Cloud stream connected! Piping directly to Drive (0 MB mobile data)", Toast.LENGTH_SHORT).show()
                }
            },
            onProgress = { pct, transferred, total ->
                // Cloud transfer in progress
            },
            onSuccess = { fileId, fileName ->
                activity?.runOnUiThread {
                    Toast.makeText(ctx, "✅ Saved '$fileName' to Google Drive (Cloud Drive Leech)!", Toast.LENGTH_LONG).show()
                }
            },
            onError = { err ->
                activity?.runOnUiThread {
                    Toast.makeText(ctx, "⚠️ Cloud Leech failed: $err", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    private fun showMainMenu() {
        val tab = WebViewTabManager.getActiveTab()
        val bottomSheet = BottomSheetDialog(requireContext())
        val view = layoutInflater.inflate(R.layout.dialog_browser_main_menu, null)
        bottomSheet.setContentView(view)
        (view.parent as? View)?.setBackgroundColor(Color.TRANSPARENT)

        val titleView = view.findViewById<TextView>(R.id.menu_page_title)
        val urlView = view.findViewById<TextView>(R.id.menu_page_url)
        val btnClose = view.findViewById<View>(R.id.btn_menu_close)

        val isStartPage = (tab == null || tab.url == "about:home" || tab.url.isEmpty() || tab.url == "about:blank")
        val currentTitle = if (isStartPage) "Cloud Browser" else tab!!.title.ifEmpty { tab.url }
        val currentUrl = if (isStartPage) "about:home" else tab!!.url

        titleView?.text = currentTitle
        urlView?.text = currentUrl
        btnClose?.setOnClickListener { bottomSheet.dismiss() }

        // Top Quick Actions
        val actReload = view.findViewById<View>(R.id.action_menu_reload)
        val actBookmark = view.findViewById<View>(R.id.action_menu_bookmark)
        val actDesktop = view.findViewById<View>(R.id.action_menu_desktop)
        val actShare = view.findViewById<View>(R.id.action_menu_share)
        val actDownloads = view.findViewById<View>(R.id.action_menu_downloads)

        val tvDesktopLabel = view.findViewById<TextView>(R.id.tv_menu_desktop_label)
        val isDesktop = (tab?.webView?.settings?.userAgentString?.contains("Windows") == true)
        tvDesktopLabel?.text = if (isDesktop) "Mobile" else "Desktop"

        actReload?.setOnClickListener {
            bottomSheet.dismiss()
            tab?.webView?.reload()
        }

        actBookmark?.setOnClickListener {
            if (tab != null && !isStartPage) {
                BookmarkRepository.addBookmark(requireContext(), tab.title, tab.url)
                Toast.makeText(requireContext(), "⭐ Saved to Bookmarks!", Toast.LENGTH_SHORT).show()
                bottomSheet.dismiss()
            } else {
                Toast.makeText(requireContext(), "Browse to a page to bookmark", Toast.LENGTH_SHORT).show()
            }
        }

        actDesktop?.setOnClickListener {
            bottomSheet.dismiss()
            if (tab != null) {
                val settings = tab.webView.settings
                if (isDesktop) {
                    settings.userAgentString = "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36"
                } else {
                    settings.userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36"
                }
                tab.webView.reload()
            }
        }

        actShare?.setOnClickListener {
            bottomSheet.dismiss()
            if (tab != null && !isStartPage) {
                val sendIntent = Intent().apply {
                    action = Intent.ACTION_SEND
                    putExtra(Intent.EXTRA_TEXT, tab.url)
                    type = "text/plain"
                }
                val shareIntent = Intent.createChooser(sendIntent, "Share Page Link")
                startActivity(shareIntent)
            } else {
                Toast.makeText(requireContext(), "Nothing to share", Toast.LENGTH_SHORT).show()
            }
        }

        actDownloads?.setOnClickListener {
            bottomSheet.dismiss()
            val act = activity as? BridgeActivity
            act?.runOnUiThread {
                act.bridge?.webView?.evaluateJavascript("window.switchTab && window.switchTab('downloads')", null)
            }
        }

        // Menu Items
        val itemGdrive = view.findViewById<View>(R.id.item_menu_gdrive)
        val tvGdriveTitle = view.findViewById<TextView>(R.id.tv_menu_gdrive_title)
        val tvGdriveSubtitle = view.findViewById<TextView>(R.id.tv_menu_gdrive_subtitle)
        val tvGdriveStatus = view.findViewById<TextView>(R.id.tv_menu_gdrive_status)

        val prefs = requireContext().getSharedPreferences("cdl_gdrive_prefs", Context.MODE_PRIVATE)
        val isConnected = prefs.getBoolean("is_connected", false)
        val email = prefs.getString("active_email", null)

        if (isConnected && !email.isNullOrEmpty()) {
            tvGdriveTitle?.text = email
            tvGdriveSubtitle?.text = "Google Drive • Connected"
            tvGdriveStatus?.text = "Open Drive"
            tvGdriveStatus?.setTextColor(Color.parseColor("#10B981"))
            itemGdrive?.setOnClickListener {
                bottomSheet.dismiss()
                loadUrl("https://accounts.google.com/ServiceLogin?service=wise&passive=1209600&continue=https://drive.google.com/drive/my-drive&Email=$email")
            }
        } else {
            tvGdriveTitle?.text = "Google Drive"
            tvGdriveSubtitle?.text = "Link Account for 15GB Cloud Storage"
            tvGdriveStatus?.text = "Link Now"
            tvGdriveStatus?.setTextColor(Color.parseColor("#00E5FF"))
            itemGdrive?.setOnClickListener {
                bottomSheet.dismiss()
                val act = activity as? BridgeActivity
                act?.runOnUiThread {
                    act.bridge?.webView?.evaluateJavascript("window.switchTab && window.switchTab('drive')", null)
                }
            }
        }

        val itemNewTab = view.findViewById<View>(R.id.item_menu_new_tab)
        val itemNewPrivateTab = view.findViewById<View>(R.id.item_menu_new_private_tab)
        val itemAdblock = view.findViewById<View>(R.id.item_menu_adblock)
        val tvAdblockStatus = view.findViewById<TextView>(R.id.tv_menu_adblock_status)
        val itemSecurity = view.findViewById<View>(R.id.item_menu_security)
        val itemBookmarks = view.findViewById<View>(R.id.item_menu_bookmarks)
        val tvBookmarksCount = view.findViewById<TextView>(R.id.tv_menu_bookmarks_count)
        val itemHistory = view.findViewById<View>(R.id.item_menu_history)
        val itemClearData = view.findViewById<View>(R.id.item_menu_clear_data)
        val itemSettings = view.findViewById<View>(R.id.item_menu_settings)
        val tvSearchEngine = view.findViewById<TextView>(R.id.tv_menu_search_engine_name)

        tvAdblockStatus?.text = if (AdBlockEngine.isEnabled) "ON" else "OFF"
        tvAdblockStatus?.setTextColor(Color.parseColor(if (AdBlockEngine.isEnabled) "#00E5FF" else "#9CA3AF"))
        tvBookmarksCount?.text = BookmarkRepository.getBookmarks(requireContext()).size.toString()
        tvSearchEngine?.text = BrowserSettings.getSearchEngine(requireContext())

        itemNewTab?.setOnClickListener {
            bottomSheet.dismiss()
            WebViewTabManager.createTab(requireContext(), "about:home", false)
            bindActiveTab()
        }

        itemNewPrivateTab?.setOnClickListener {
            bottomSheet.dismiss()
            WebViewTabManager.createTab(requireContext(), "about:home", true)
            bindActiveTab()
        }

        itemAdblock?.setOnClickListener {
            toggleAdBlock()
            tvAdblockStatus?.text = if (AdBlockEngine.isEnabled) "ON" else "OFF"
            tvAdblockStatus?.setTextColor(Color.parseColor(if (AdBlockEngine.isEnabled) "#00E5FF" else "#9CA3AF"))
        }

        itemSecurity?.setOnClickListener {
            bottomSheet.dismiss()
            showSiteSecurityInfo()
        }

        itemBookmarks?.setOnClickListener {
            bottomSheet.dismiss()
            showBookmarksDialog()
        }

        itemHistory?.setOnClickListener {
            bottomSheet.dismiss()
            showHistoryDialog()
        }

        itemClearData?.setOnClickListener {
            bottomSheet.dismiss()
            clearBrowsingData()
        }

        val itemExtensions = view.findViewById<View>(R.id.item_menu_extensions)
        val tvExtensionsCount = view.findViewById<TextView>(R.id.tv_menu_extensions_count)
        val activeExtCount = ExtensionManager.getAllExtensions(requireContext()).count { it.isEnabled }
        tvExtensionsCount?.text = "$activeExtCount Active"

        itemExtensions?.setOnClickListener {
            bottomSheet.dismiss()
            showExtensionsDialog()
        }

        itemSettings?.setOnClickListener {
            bottomSheet.dismiss()
            showSettingsDialog()
        }

        bottomSheet.show()
    }

    private fun showExtensionsDialog() {
        val bottomSheet = BottomSheetDialog(requireContext())
        val view = layoutInflater.inflate(R.layout.dialog_browser_extensions, null)
        bottomSheet.setContentView(view)
        (view.parent as? View)?.setBackgroundColor(Color.TRANSPARENT)

        val btnClose = view.findViewById<View>(R.id.btn_extensions_close)
        val btnAdd = view.findViewById<View>(R.id.btn_add_extension)
        val tvSubtitle = view.findViewById<TextView>(R.id.tv_extensions_subtitle)
        val listContainer = view.findViewById<LinearLayout>(R.id.ll_extensions_list)

        btnClose?.setOnClickListener { bottomSheet.dismiss() }

        fun populateList() {
            listContainer?.removeAllViews()
            val extensions = ExtensionManager.getAllExtensions(requireContext())
            val activeCount = extensions.count { it.isEnabled }
            tvSubtitle?.text = "uBlock Origin Engine • $activeCount Active"

            val inflater = LayoutInflater.from(requireContext())
            for (ext in extensions) {
                val row = inflater.inflate(R.layout.item_browser_extension, listContainer, false)
                row.findViewById<TextView>(R.id.tv_extension_icon).text = ext.icon
                row.findViewById<TextView>(R.id.tv_extension_name).text = ext.name
                row.findViewById<TextView>(R.id.tv_extension_version).text = ext.version
                row.findViewById<TextView>(R.id.tv_extension_author).text = "by ${ext.author}"
                row.findViewById<TextView>(R.id.tv_extension_desc).text = ext.description

                val switchView = row.findViewById<SwitchCompat>(R.id.switch_extension_enabled)
                switchView.isChecked = ext.isEnabled
                switchView.setOnCheckedChangeListener { _, isChecked ->
                    ExtensionManager.setExtensionEnabled(requireContext(), ext.id, isChecked)
                    if (ext.id == "ext_ublock_core") {
                        AdBlockEngine.isEnabled = isChecked
                    }
                    val updatedCount = ExtensionManager.getAllExtensions(requireContext()).count { it.isEnabled }
                    tvSubtitle?.text = "uBlock Origin Engine • $updatedCount Active"

                    // Inject immediately if enabled
                    val tab = WebViewTabManager.getActiveTab()
                    if (tab != null && isChecked) {
                        ExtensionManager.injectActiveExtensions(requireContext(), tab.webView)
                    }
                    Toast.makeText(requireContext(), "${ext.name}: ${if (isChecked) "Enabled" else "Disabled"}", Toast.LENGTH_SHORT).show()
                }

                val btnDelete = row.findViewById<View>(R.id.btn_extension_delete)
                if (!ext.isBuiltIn) {
                    btnDelete.visibility = View.VISIBLE
                    btnDelete.setOnClickListener {
                        ExtensionManager.deleteExtension(requireContext(), ext.id)
                        Toast.makeText(requireContext(), "Extension '${ext.name}' removed", Toast.LENGTH_SHORT).show()
                        populateList()
                    }
                } else {
                    btnDelete.visibility = View.GONE
                }

                listContainer?.addView(row)
            }
        }

        btnAdd?.setOnClickListener {
            showAddExtensionDialog {
                populateList()
            }
        }

        populateList()
        bottomSheet.show()
    }

    private fun showAddExtensionDialog(onAdded: () -> Unit) {
        val bottomSheet = BottomSheetDialog(requireContext())
        val view = layoutInflater.inflate(R.layout.dialog_add_extension, null)
        bottomSheet.setContentView(view)
        (view.parent as? View)?.setBackgroundColor(Color.TRANSPARENT)

        val etName = view.findViewById<EditText>(R.id.et_ext_name)
        val etDesc = view.findViewById<EditText>(R.id.et_ext_desc)
        val etJs = view.findViewById<EditText>(R.id.et_ext_js)
        val etCss = view.findViewById<EditText>(R.id.et_ext_css)
        val btnCancel = view.findViewById<View>(R.id.btn_ext_cancel)
        val btnSave = view.findViewById<View>(R.id.btn_ext_save)

        btnCancel?.setOnClickListener { bottomSheet.dismiss() }

        btnSave?.setOnClickListener {
            val name = etName?.text?.toString()?.trim() ?: ""
            val desc = etDesc?.text?.toString()?.trim() ?: ""
            val js = etJs?.text?.toString()?.trim() ?: ""
            val css = etCss?.text?.toString()?.trim() ?: ""

            if (name.isEmpty()) {
                Toast.makeText(requireContext(), "Please enter an Extension Name", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (js.isEmpty() && css.isEmpty()) {
                Toast.makeText(requireContext(), "Please enter JavaScript code or CSS rules", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            ExtensionManager.addCustomExtension(requireContext(), name, desc, js, css)
            Toast.makeText(requireContext(), "🎉 Extension '$name' Added & Activated!", Toast.LENGTH_SHORT).show()

            val tab = WebViewTabManager.getActiveTab()
            if (tab != null) {
                ExtensionManager.injectActiveExtensions(requireContext(), tab.webView)
            }

            bottomSheet.dismiss()
            onAdded()
        }

        bottomSheet.show()
    }

    private fun showBookmarksDialog() {
        val bottomSheet = BottomSheetDialog(requireContext())
        val view = layoutInflater.inflate(R.layout.dialog_browser_bookmarks, null)
        bottomSheet.setContentView(view)
        (view.parent as? View)?.setBackgroundColor(Color.TRANSPARENT)

        val btnClose = view.findViewById<View>(R.id.btn_bookmarks_close)
        btnClose?.setOnClickListener { bottomSheet.dismiss() }

        val listContainer = view.findViewById<LinearLayout>(R.id.ll_bookmarks_list)
        val tvEmpty = view.findViewById<View>(R.id.tv_bookmarks_empty)
        val bookmarks = BookmarkRepository.getBookmarks(requireContext())

        if (bookmarks.isEmpty()) {
            tvEmpty?.visibility = View.VISIBLE
            listContainer?.visibility = View.GONE
        } else {
            tvEmpty?.visibility = View.GONE
            listContainer?.visibility = View.VISIBLE
            listContainer?.removeAllViews()

            val inflater = LayoutInflater.from(requireContext())
            for (bm in bookmarks) {
                val row = inflater.inflate(R.layout.item_menu_list_entry, listContainer, false)
                row.findViewById<TextView>(R.id.tv_entry_icon).text = "⭐"
                row.findViewById<TextView>(R.id.tv_entry_title).text = bm.title
                row.findViewById<TextView>(R.id.tv_entry_url).text = bm.url

                val btnDelete = row.findViewById<ImageView>(R.id.btn_entry_action)
                btnDelete.visibility = View.VISIBLE
                btnDelete.setOnClickListener {
                    BookmarkRepository.removeBookmark(requireContext(), bm.url)
                    bottomSheet.dismiss()
                    showBookmarksDialog()
                }

                row.setOnClickListener {
                    bottomSheet.dismiss()
                    loadUrl(bm.url)
                }
                listContainer?.addView(row)
            }
        }

        bottomSheet.show()
    }

    private fun showHistoryDialog() {
        val bottomSheet = BottomSheetDialog(requireContext())
        val view = layoutInflater.inflate(R.layout.dialog_browser_history, null)
        bottomSheet.setContentView(view)
        (view.parent as? View)?.setBackgroundColor(Color.TRANSPARENT)

        val btnClose = view.findViewById<View>(R.id.btn_history_close)
        val btnClearAll = view.findViewById<View>(R.id.btn_history_clear_all)
        btnClose?.setOnClickListener { bottomSheet.dismiss() }

        val listContainer = view.findViewById<LinearLayout>(R.id.ll_history_list)
        val tvEmpty = view.findViewById<View>(R.id.tv_history_empty)
        val history = HistoryRepository.getHistory(requireContext())

        btnClearAll?.setOnClickListener {
            HistoryRepository.clearHistory(requireContext())
            Toast.makeText(requireContext(), "History cleared!", Toast.LENGTH_SHORT).show()
            bottomSheet.dismiss()
        }

        if (history.isEmpty()) {
            tvEmpty?.visibility = View.VISIBLE
            listContainer?.visibility = View.GONE
        } else {
            tvEmpty?.visibility = View.GONE
            listContainer?.visibility = View.VISIBLE
            listContainer?.removeAllViews()

            val inflater = LayoutInflater.from(requireContext())
            for (item in history) {
                val row = inflater.inflate(R.layout.item_menu_list_entry, listContainer, false)
                row.findViewById<TextView>(R.id.tv_entry_icon).text = "🕒"
                row.findViewById<TextView>(R.id.tv_entry_title).text = item.title.ifEmpty { item.url }
                row.findViewById<TextView>(R.id.tv_entry_url).text = item.url

                row.setOnClickListener {
                    bottomSheet.dismiss()
                    loadUrl(item.url)
                }
                listContainer?.addView(row)
            }
        }

        bottomSheet.show()
    }

    private fun clearBrowsingData() {
        AlertDialog.Builder(requireContext())
            .setTitle("Clear Browsing Data")
            .setMessage("Clear all cookies, cache, and history?")
            .setPositiveButton("Clear") { _, _ ->
                CookieManager.getInstance().removeAllCookies(null)
                CookieManager.getInstance().flush()
                HistoryRepository.clearHistory(requireContext())
                val tab = WebViewTabManager.getActiveTab()
                tab?.webView?.clearCache(true)
                Toast.makeText(requireContext(), "Browsing data cleared!", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showSettingsDialog() {
        val bottomSheet = BottomSheetDialog(requireContext())
        val view = layoutInflater.inflate(R.layout.dialog_browser_settings, null)
        bottomSheet.setContentView(view)
        (view.parent as? View)?.setBackgroundColor(Color.TRANSPARENT)

        val btnClose = view.findViewById<View>(R.id.btn_settings_close)
        btnClose?.setOnClickListener { bottomSheet.dismiss() }

        val optGoogle = view.findViewById<View>(R.id.opt_engine_google)
        val optDdg = view.findViewById<View>(R.id.opt_engine_ddg)
        val optBing = view.findViewById<View>(R.id.opt_engine_bing)

        val indGoogle = view.findViewById<View>(R.id.indicator_engine_google)
        val indDdg = view.findViewById<View>(R.id.indicator_engine_ddg)
        val indBing = view.findViewById<View>(R.id.indicator_engine_bing)

        fun updateIndicators() {
            val cur = BrowserSettings.getSearchEngine(requireContext())
            indGoogle?.visibility = if (cur.equals("Google", true)) View.VISIBLE else View.GONE
            indDdg?.visibility = if (cur.equals("DuckDuckGo", true)) View.VISIBLE else View.GONE
            indBing?.visibility = if (cur.equals("Bing", true)) View.VISIBLE else View.GONE
        }

        updateIndicators()

        optGoogle?.setOnClickListener {
            BrowserSettings.setSearchEngine(requireContext(), "Google")
            updateIndicators()
            Toast.makeText(requireContext(), "Search Engine: Google", Toast.LENGTH_SHORT).show()
        }

        optDdg?.setOnClickListener {
            BrowserSettings.setSearchEngine(requireContext(), "DuckDuckGo")
            updateIndicators()
            Toast.makeText(requireContext(), "Search Engine: DuckDuckGo", Toast.LENGTH_SHORT).show()
        }

        optBing?.setOnClickListener {
            BrowserSettings.setSearchEngine(requireContext(), "Bing")
            updateIndicators()
            Toast.makeText(requireContext(), "Search Engine: Bing", Toast.LENGTH_SHORT).show()
        }

        bottomSheet.show()
    }

    private fun hideKeyboard() {
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(addressBar?.windowToken, 0)
    }
}

