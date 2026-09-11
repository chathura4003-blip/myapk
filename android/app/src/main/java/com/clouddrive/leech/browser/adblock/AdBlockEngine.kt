package com.clouddrive.leech.browser.adblock

import android.content.Context
import android.net.Uri
import com.clouddrive.leech.browser.extensions.ExtensionManager
import java.util.Locale

object AdBlockEngine {

    var isEnabled: Boolean = true

    // Comprehensive high-efficiency ad, tracker, popunder and telemetry hosts from gorhill/uBlock Origin & EasyList
    private val blockedHostSuffixes = hashSetOf(
        // Google Ad & Tracking Network
        "doubleclick.net",
        "googleads.g.doubleclick.net",
        "googlesyndication.com",
        "pagead2.googlesyndication.com",
        "tpc.googlesyndication.com",
        "adservice.google.com",
        "adservice.google.lk",
        "googleadservices.com",
        "partner.googleadservices.com",
        "securepubads.g.doubleclick.net",
        "stats.g.doubleclick.net",
        "fundingchoicesmessages.google.com",
        "google-analytics.com",
        "analytics.google.com",
        "googletagmanager.com",
        "googletagservices.com",
        "admob.com",
        "ads.youtube.com",
        "imasdk.googleapis.com",

        // Popups, Popunders, Malvertising & Clickjacking
        "popads.net",
        "serve.popads.net",
        "popcash.net",
        "propellerads.com",
        "propellerclick.com",
        "propellerpush.com",
        "propush.me",
        "adsterra.com",
        "exoclick.com",
        "syndication.exoclick.com",
        "main.exoclick.com",
        "exosrv.com",
        "juicyads.com",
        "adsupply.com",
        "clickadu.com",
        "clickadu.net",
        "monetag.com",
        "asg.monetag.com",
        "creative.monetag.com",
        "trafficjunky.net",
        "delivery.trafficjunky.net",
        "hilltopads.net",
        "richads.com",
        "richpush.com",
        "clickaine.com",
        "adxadcm.com",
        "tsyndicate.com",
        "adbrau.com",
        "onclickpredictiv.com",
        "onclkds.com",
        "onclickalgo.com",
        "onclickperformance.com",
        "alwingulla.com",
        "deloplen.com",
        "adtrue.com",
        "adcash.com",
        "popmyads.com",
        "adkernel.com",
        "admaven.com",
        "ad-maven.com",
        "yllix.com",
        "adoperator.com",
        "ad-delivery.net",
        "trafficstars.com",
        "plugrush.com",
        "ero-advertising.com",
        "evadav.com",
        "adsco.re",
        "c.adsco.re",

        // Major Display, Native & Video Ad Networks
        "adnxs.com",
        "rubiconproject.com",
        "criteo.com",
        "criteo.net",
        "taboola.com",
        "trc.taboola.com",
        "outbrain.com",
        "widgets.outbrain.com",
        "revcontent.com",
        "servedby.revcontent.com",
        "cdn.revcontent.com",
        "mgid.com",
        "marketgid.com",
        "zedo.com",
        "infolinks.com",
        "chitika.com",
        "bidvertiser.com",
        "media.net",
        "openx.net",
        "pubmatic.com",
        "casalemedia.com",
        "smartadserver.com",
        "contextweb.com",
        "sovrn.com",
        "indexexchange.com",
        "yieldmo.com",
        "adform.net",
        "sharethrough.com",
        "zergnet.com",
        "adbutler.com",
        "adzerk.net",
        "engine.adzerk.net",
        "adpushup.com",
        "ezoic.net",
        "ezodn.com",
        "adnuntius.com",
        "adrunnr.com",
        "aniview.com",
        "springserve.com",
        "spotxchange.com",
        "innovid.com",
        "teads.tv",
        "vidazoo.com",

        // Mobile Ads & SDK Telemetry
        "adcolony.com",
        "vungle.com",
        "chartboost.com",
        "applovin.com",
        "unityads.unity3d.com",
        "ironsrc.com",
        "inmobi.com",
        "flurry.com",
        "kochava.com",
        "appsflyer.com",
        "adjust.com",
        "branch.io",
        "scorecardresearch.com",
        "quantserve.com",
        "hotjar.com",
        "clarity.ms",
        "yandex.ru/ads",
        "an.yandex.ru",

        // Cryptominers & Malicious Trackers
        "coinhive.com",
        "crypto-loot.com",
        "coin-have.com",
        "minr.pw",
        "clarium.io",

        // Invasive Betting & Scam Redirects
        "bet365.com",
        "1xbet.com",
        "melbet.com",
        "mostbet.com",
        "parimatch.com",
        "linebet.com",
        "dafabet.com",
        "bc.game",
        "stake.com",
        "refpa.top",
        "refpaio.top"
    )

    private val blockedUrlKeywords = arrayOf(
        "/ads/",
        "/ads.js",
        "/ad.js",
        "/show_ads.js",
        "/adserver/",
        "/ad_banner",
        "/popunder",
        "/popup-ad",
        "/ad-placement",
        "/ad_unit",
        "/ad-loader",
        "/banner.js",
        "banner_id=",
        "ad_type=",
        "google_ad",
        "googlesyndication",
        "pagead2",
        "doubleclick",
        "adservice",
        "adsystem",
        "adclick",
        "tracking.php",
        "analytics.js",
        "/telemetry/",
        "traffic_source=",
        "popcash",
        "popads",
        "propeller",
        "adsterra",
        "exoclick",
        "trafficjunky",
        "juicyads",
        "monetag",
        "onclickpredictiv",
        "onclickperformance",
        "alwingulla",
        "deloplen",
        "admaven",
        "yllix"
    )

    fun isAd(url: String, context: Context? = null): Boolean {
        if (!isEnabled || url.isEmpty()) return false

        if (context != null && !ExtensionManager.isUBlockEnabled(context)) {
            return false
        }

        try {
            val uri = Uri.parse(url)
            val host = uri.host?.lowercase(Locale.ROOT) ?: return false

            for (blocked in blockedHostSuffixes) {
                if (host == blocked || host.endsWith(".$blocked")) {
                    return true
                }
            }

            val lowerUrl = url.lowercase(Locale.ROOT)
            for (keyword in blockedUrlKeywords) {
                if (lowerUrl.contains(keyword)) {
                    return true
                }
            }
        } catch (_: Exception) {
            // ignore parse exceptions
        }
        return false
    }
}
