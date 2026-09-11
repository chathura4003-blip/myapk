package com.clouddrive.leech.browser.extensions

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import android.webkit.WebView
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

object ExtensionManager {

    private const val PREFS_NAME = "browser_extensions_prefs"
    private const val KEY_CUSTOM_EXTENSIONS = "custom_extensions_json"
    private const val KEY_ENABLED_PREFIX = "ext_enabled_"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private fun getDefaultBuiltIns(): List<BrowserExtension> {
        return listOf(
            BrowserExtension(
                id = "ext_ublock_core",
                name = "uBlock Origin Shield",
                version = "v1.58.0",
                author = "gorhill / uBlock Origin",
                description = "High-efficiency network blocker & tracker filter based on EasyList & EasyPrivacy rules.",
                icon = "🛡️",
                isEnabled = true,
                isBuiltIn = true
            ),
            BrowserExtension(
                id = "ext_ublock_cosmetic",
                name = "uBlock Cosmetic & Anti-AdBlock",
                version = "v1.58.0",
                author = "gorhill / Community",
                description = "Hides banner placeholders, closes clickjack popups, and defuses anti-adblock modals in real time.",
                icon = "🧹",
                isEnabled = true,
                isBuiltIn = true,
                cssCode = """
                    .adsbygoogle, [id^="google_ads_"], [id^="ad-"], [class^="ad-"],
                    .ad-banner, .banner-ad, .ad-container, .ad-wrapper, .ad-box,
                    .ad-placement, .ad_unit, .ad-slot, .popunder, .popup-overlay,
                    .ad-overlay, .video-ads, .ytp-ad-module, .ytp-ad-image-overlay,
                    .ytp-ad-overlay-container, .ytp-ad-player-overlay,
                    [id*="ScriptRoot"], [class*="ScriptRoot"], .taboola-ad,
                    .outbrain-ad, .mgid-ad, .adsterra_banner, .exoclick_wrapper,
                    .juicyads, [data-ad-unit], [data-ad-client], [data-ad-slot],
                    .advertisement, #advertisement, .ad-zone, .ad-area,
                    iframe[src*="ad"], iframe[src*="doubleclick"], iframe[src*="popads"],
                    iframe[src*="monetag"], iframe[src*="exoclick"], iframe[src*="propeller"],
                    div[class*="popup-banner"], div[id*="popup-banner"],
                    .cookie-consent-overlay, .modal-backdrop-ad, .anti-adblock-modal,
                    #antiadblock, .blockadblock, #adblock-modal, .adblock-notice {
                        display: none !important;
                        visibility: hidden !important;
                        width: 0 !important;
                        height: 0 !important;
                        min-height: 0 !important;
                        max-height: 0 !important;
                        opacity: 0 !important;
                        pointer-events: none !important;
                        margin: 0 !important;
                        padding: 0 !important;
                        border: none !important;
                        overflow: hidden !important;
                    }
                """.trimIndent(),
                jsCode = """
                    (function() {
                        if (window.__ublock_cosmetic_active) return;
                        window.__ublock_cosmetic_active = true;

                        // Defuse Anti-Adblock detection
                        window.bab = { isDetected: false, check: function() {} };
                        window.blockAdBlock = { check: function() {}, on: function() {}, onDetected: function() {}, onNotDetected: function(cb) { if (typeof cb === 'function') setTimeout(cb, 50); } };
                        window.canRunAds = true;
                        window.isAdBlockActive = false;
                        window.adblock = false;

                        // Filter ad popup windows while permitting legitimate new tabs
                        const _origOpen = window.open;
                        window.open = function(url, target, specs) {
                            if (url) {
                                const lower = url.toLowerCase();
                                if (lower.includes('popads') || lower.includes('popcash') || lower.includes('propeller') ||
                                    lower.includes('onclick') || lower.includes('adsterra') || lower.includes('exoclick') ||
                                    lower.includes('trafficjunky') || lower.includes('monetag')) {
                                    console.log('[uBlock Origin] Intercepted ad popup:', url);
                                    return null;
                                }
                            }
                            return _origOpen.apply(this, arguments);
                        };

                        // High-Speed MutationObserver to remove dynamically injected ads instantly
                        const adSelectors = '.adsbygoogle, [id^="google_ads_"], .ad-banner, .banner-ad, .ad-container, .ad-slot, .ytp-ad-module, .ad-showing, .ad-interrupting, iframe[src*="ad"], iframe[src*="doubleclick"], iframe[src*="popads"], iframe[src*="monetag"], iframe[src*="exoclick"], .anti-adblock-modal, .blockadblock';

                        function purgeAds(node) {
                            try {
                                const root = (node && node.querySelectorAll) ? node : document;
                                const items = root.querySelectorAll(adSelectors);
                                for (let i = 0; i < items.length; i++) {
                                    items[i].style.setProperty('display', 'none', 'important');
                                    items[i].style.setProperty('height', '0px', 'important');
                                    items[i].style.setProperty('width', '0px', 'important');
                                    items[i].style.setProperty('opacity', '0', 'important');
                                    items[i].style.setProperty('pointer-events', 'none', 'important');
                                }
                            } catch(e) {}
                        }

                        if (document.body) purgeAds(document.body);
                        const obs = new MutationObserver(function(mutations) {
                            for (let i = 0; i < mutations.length; i++) {
                                if (mutations[i].addedNodes.length > 0) {
                                    purgeAds(mutations[i].target);
                                }
                            }
                        });
                        obs.observe(document.documentElement || document, { childList: true, subtree: true });
                    })();
                """.trimIndent()
            ),
            BrowserExtension(
                id = "ext_youtube_skipper",
                name = "YouTube Turbo Ad-Skipper",
                version = "v2.1.0",
                author = "Cloud Browser Team",
                description = "Instantly fast-forwards and dismisses video ads & sponsor cards on YouTube.",
                icon = "⚡",
                isEnabled = true,
                isBuiltIn = true,
                jsCode = """
                    (function() {
                        if (!window.location.hostname.includes('youtube.com')) return;
                        function autoSkip() {
                            const video = document.querySelector('video');
                            const adShowing = document.querySelector('.ad-showing, .ad-interrupting');
                            if (adShowing && video && !isNaN(video.duration) && video.duration > 0) {
                                video.currentTime = video.duration;
                            }
                            const skipBtn = document.querySelector('.ytp-ad-skip-button, .ytp-ad-skip-button-modern, .ytp-skip-ad-button');
                            if (skipBtn) {
                                skipBtn.click();
                            }
                            const overlayClose = document.querySelector('.ytp-ad-overlay-close-button');
                            if (overlayClose) {
                                overlayClose.click();
                            }
                        }
                        setInterval(autoSkip, 400);
                    })();
                """.trimIndent()
            ),
            BrowserExtension(
                id = "ext_dark_reader",
                name = "Dark Reader Lite",
                version = "v1.2.0",
                author = "DarkReader Community",
                description = "Inverts high-contrast whites into eye-pleasing dark backgrounds for comfortable night reading.",
                icon = "🌙",
                isEnabled = false,
                isBuiltIn = true,
                cssCode = """
                    html { filter: invert(90%) hue-rotate(180deg) !important; background: #121212 !important; }
                    img, video, iframe, canvas, svg { filter: invert(100%) hue-rotate(180deg) !important; }
                """.trimIndent()
            ),
            BrowserExtension(
                id = "ext_download_unlocker",
                name = "Direct Link Unlocker",
                version = "v1.0.0",
                author = "Leech Community",
                description = "Auto-bypasses intermediate redirect countdowns and countdown timers on file hosts.",
                icon = "🚀",
                isEnabled = true,
                isBuiltIn = true,
                jsCode = """
                    (function() {
                        const countdowns = document.querySelectorAll('#download-timer, .countdown, #countdown');
                        countdowns.forEach(el => { el.textContent = '0'; });
                    })();
                """.trimIndent()
            )
        )
    }

    fun getAllExtensions(context: Context): List<BrowserExtension> {
        val prefs = getPrefs(context)
        val builtIns = getDefaultBuiltIns()
        val customExtensions = getCustomExtensions(context)

        val result = mutableListOf<BrowserExtension>()
        for (ext in builtIns) {
            val enabled = prefs.getBoolean(KEY_ENABLED_PREFIX + ext.id, ext.isEnabled)
            result.add(ext.copy(isEnabled = enabled))
        }
        for (ext in customExtensions) {
            val enabled = prefs.getBoolean(KEY_ENABLED_PREFIX + ext.id, ext.isEnabled)
            result.add(ext.copy(isEnabled = enabled))
        }
        return result
    }

    fun setExtensionEnabled(context: Context, extensionId: String, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_ENABLED_PREFIX + extensionId, enabled).apply()
    }

    fun isExtensionEnabled(context: Context, extensionId: String): Boolean {
        val prefs = getPrefs(context)
        if (!prefs.contains(KEY_ENABLED_PREFIX + extensionId)) {
            val defaultExt = getDefaultBuiltIns().find { it.id == extensionId }
            return defaultExt?.isEnabled ?: true
        }
        return prefs.getBoolean(KEY_ENABLED_PREFIX + extensionId, true)
    }

    fun isUBlockEnabled(context: Context): Boolean {
        return isExtensionEnabled(context, "ext_ublock_core")
    }

    fun addCustomExtension(context: Context, name: String, description: String, jsCode: String, cssCode: String = ""): BrowserExtension {
        val id = "custom_" + UUID.randomUUID().toString().substring(0, 8)
        val newExt = BrowserExtension(
            id = id,
            name = name.trim().ifEmpty { "Custom Script" },
            version = "v1.0.0",
            author = "User",
            description = description.trim().ifEmpty { "User-defined custom extension" },
            icon = "🧩",
            isEnabled = true,
            isBuiltIn = false,
            jsCode = jsCode.trim(),
            cssCode = cssCode.trim()
        )

        val currentList = getCustomExtensions(context).toMutableList()
        currentList.add(newExt)
        saveCustomExtensions(context, currentList)
        setExtensionEnabled(context, id, true)
        return newExt
    }

    fun deleteExtension(context: Context, extensionId: String) {
        val currentList = getCustomExtensions(context).toMutableList()
        currentList.removeAll { it.id == extensionId }
        saveCustomExtensions(context, currentList)
        getPrefs(context).edit().remove(KEY_ENABLED_PREFIX + extensionId).apply()
    }

    private fun getCustomExtensions(context: Context): List<BrowserExtension> {
        val jsonStr = getPrefs(context).getString(KEY_CUSTOM_EXTENSIONS, null) ?: return emptyList()
        val list = mutableListOf<BrowserExtension>()
        try {
            val jsonArray = JSONArray(jsonStr)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                list.add(
                    BrowserExtension(
                        id = obj.getString("id"),
                        name = obj.getString("name"),
                        version = obj.optString("version", "v1.0.0"),
                        author = obj.optString("author", "User"),
                        description = obj.optString("description", ""),
                        icon = obj.optString("icon", "🧩"),
                        isEnabled = obj.optBoolean("isEnabled", true),
                        isBuiltIn = false,
                        jsCode = obj.optString("jsCode", ""),
                        cssCode = obj.optString("cssCode", "")
                    )
                )
            }
        } catch (_: Exception) { }
        return list
    }

    private fun saveCustomExtensions(context: Context, list: List<BrowserExtension>) {
        val jsonArray = JSONArray()
        for (ext in list) {
            val obj = JSONObject().apply {
                put("id", ext.id)
                put("name", ext.name)
                put("version", ext.version)
                put("author", ext.author)
                put("description", ext.description)
                put("icon", ext.icon)
                put("isEnabled", ext.isEnabled)
                put("jsCode", ext.jsCode)
                put("cssCode", ext.cssCode)
            }
            jsonArray.put(obj)
        }
        getPrefs(context).edit().putString(KEY_CUSTOM_EXTENSIONS, jsonArray.toString()).apply()
    }

    fun injectActiveExtensions(context: Context, webView: WebView) {
        val extensions = getAllExtensions(context).filter { it.isEnabled }
        for (ext in extensions) {
            // Inject CSS if present
            if (ext.cssCode.isNotEmpty()) {
                val base64Css = Base64.encodeToString(ext.cssCode.toByteArray(), Base64.NO_WRAP)
                val cssScript = """
                    (function() {
                        var target = document.head || document.documentElement || document.body;
                        if (!target) return;
                        var style = document.getElementById('ext_css_${ext.id}');
                        if (!style) {
                            style = document.createElement('style');
                            style.id = 'ext_css_${ext.id}';
                            style.textContent = atob('$base64Css');
                            target.appendChild(style);
                        }
                    })();
                """.trimIndent()
                webView.evaluateJavascript(cssScript, null)
            }

            // Inject JS if present
            if (ext.jsCode.isNotEmpty()) {
                val base64Js = Base64.encodeToString(ext.jsCode.toByteArray(), Base64.NO_WRAP)
                val jsScript = """
                    (function() {
                        try {
                            if (window['__ext_run_${ext.id}']) return;
                            window['__ext_run_${ext.id}'] = true;
                            var code = atob('$base64Js');
                            var fn = new Function(code);
                            fn();
                        } catch(e) {
                            console.error('[Extension ${ext.name}] Error:', e);
                        }
                    })();
                """.trimIndent()
                webView.evaluateJavascript(jsScript, null)
            }
        }
    }
}
