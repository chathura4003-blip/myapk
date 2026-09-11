package com.clouddrive.leech.extractor.providers.movies

import com.clouddrive.leech.extractor.models.StreamResult
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.jsoup.Jsoup
import java.util.concurrent.TimeUnit

/**
 * Dedicated Stream Resolver for Direct Cloud Video Streams (PixelDrain, Google Drive, CineSubz Sonic Cloud, /links/ redirectors).
 */
class MovieStreamResolver(
    client: OkHttpClient = defaultClient
) : BaseMovieScraper(client) {

    private val fastOkClient: OkHttpClient by lazy {
        okHttpClient.newBuilder()
            .connectTimeout(7, TimeUnit.SECONDS)
            .readTimeout(7, TimeUnit.SECONDS)
            .writeTimeout(7, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    private fun fetchFastHtml(url: String): String {
        return try {
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", userAgent)
                .header("Referer", url)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .build()
            val resp = fastOkClient.newCall(req).execute()
            val landedUrl = resp.request.url.toString()
            val body = resp.body?.string() ?: ""
            if (landedUrl != url && (landedUrl.contains("pixeldrain") || landedUrl.contains(".mp4") || landedUrl.contains("workers.dev") || landedUrl.contains("usersdrive") || landedUrl.contains("mega.nz") || landedUrl.contains("cinerustreams"))) {
                return """<!-- landed: $landedUrl --> <a href="$landedUrl">direct</a> $body"""
            }
            body
        } catch (_: Exception) {
            ""
        }
    }

    fun resolve(targetUrl: String): StreamResult {
        val target = targetUrl.trim()

        // 0. Cineru Web Player / HLS Stream (e.g. https://bot.cinerustreams.com/watch/4971?hash=b29bbc)
        if (target.contains("cinerustreams.com")) {
            if (target.contains(".m3u8")) {
                return StreamResult(
                    streamUrl = target,
                    downloadUrl = target,
                    embedUrl = target,
                    type = "video"
                )
            }
            return StreamResult(
                streamUrl = target,
                downloadUrl = target,
                embedUrl = target,
                type = "embed"
            )
        }

        // 0b. Cinejoy Direct Cloud Stream Resolver
        if (target.contains("cinejoy.to")) {
            val cjRes = resolveCinejoyDirect(target)
            if (cjRes != null) return cjRes
        }

        // 1. PixelDrain Direct API URL
        val pdMatch = Regex("""pixeldrain\.com\/(?:u|l|api\/file|e)\/([a-zA-Z0-9_-]+)""").find(target)
        if (pdMatch != null) {
            val fileId = pdMatch.groupValues[1]
            return StreamResult(
                streamUrl = "https://pixeldrain.com/api/file/$fileId",
                downloadUrl = "https://pixeldrain.com/api/file/$fileId?download",
                embedUrl = "https://pixeldrain.com/e/$fileId",
                type = "video"
            )
        }

        // 2. Google Drive Direct ID & UserContent CDN
        val gdMatch = Regex("""(?:drive\.google\.com|drive\.usercontent\.google\.com)\/(?:file\/d\/|open\?id=|uc\?id=|download\?id=)([a-zA-Z0-9_-]+)""").find(target)
        if (gdMatch != null) {
            val gId = gdMatch.groupValues[1]
            return StreamResult(
                streamUrl = "https://drive.google.com/file/d/$gId/preview",
                downloadUrl = "https://drive.usercontent.google.com/download?id=$gId&export=download&authuser=0",
                embedUrl = "https://drive.google.com/file/d/$gId/preview",
                type = "embed"
            )
        }

        // 3. MEGA Cloud Link (Supports both mega.nz/file/ID#KEY and mega.nz/#!ID!KEY)
        if (target.contains("mega.nz") || target.contains("mega.co.nz")) {
            val megaEmbed = when {
                target.contains("/file/") -> target.replace("/file/", "/embed/")
                target.contains("/#!") -> target.replace("/#!", "/embed#!")
                target.contains("#!") -> target.replace("#!", "embed#!")
                target.contains("/embed/") -> target
                else -> {
                    val mMatch = Regex("""mega\.nz\/(?:file|embed)\/([a-zA-Z0-9#_-]+)""").find(target)
                    if (mMatch != null) "https://mega.nz/embed/${mMatch.groupValues[1]}" else target
                }
            }
            return StreamResult(
                streamUrl = target,
                downloadUrl = target,
                embedUrl = megaEmbed,
                type = "embed"
            )
        }

        // 4. UsersDrive Link
        if (target.contains("userdrive.org") || target.contains("dl.usersdrive.com")) {
            val isZip = target.contains(".zip", true) || target.contains(".rar", true)
            return StreamResult(
                title = "UsersDrive Direct Stream",
                streamUrl = target,
                downloadUrl = target,
                embedUrl = "",
                type = if (isZip) "download" else "video",
                isZip = isZip
            )
        }

        val udMatch = Regex("""(?:usersdrive\.com|userdrive\.org)\/(?:embed-)?([a-zA-Z0-9]+)""").find(target)
        if (udMatch != null) {
            val udId = udMatch.groupValues[1]
            val fullUdUrl = if (target.startsWith("http")) target else "https://usersdrive.com/$udId.html"
            var videoTitle = "UsersDrive Movie ($udId)"
            var resolvedDirect = fullUdUrl
            var isZip = false
            var fileName = ""

            try {
                val html = fetchFastHtml(fullUdUrl)
                val titleMatch = Regex("""<h4[^>]*>([^<]+)</h4>""", RegexOption.IGNORE_CASE).find(html)
                    ?: Regex("""<title>([^<]+)</title>""", RegexOption.IGNORE_CASE).find(html)
                if (titleMatch != null) {
                    val raw = titleMatch.groupValues[1].replace(Regex("""(?i)\s*-\s*usersdrive.*$"""), "").trim()
                    if (raw.isNotEmpty() && raw != "usersdrive") {
                        val parsed = org.jsoup.Jsoup.parse(raw).text()
                        videoTitle = parsed
                        fileName = parsed
                    }
                }
                isZip = videoTitle.endsWith(".zip", true) || videoTitle.endsWith(".rar", true) ||
                        videoTitle.contains("zip", true) || html.contains(".zip", true)
                resolvedDirect = resolveUsersDriveDirect(fullUdUrl, html)
                if (resolvedDirect.contains(".zip", true) || resolvedDirect.contains(".rar", true)) {
                    isZip = true
                }
            } catch (_: Exception) {}

            val isDirectBinary = resolvedDirect.contains("userdrive.org") || resolvedDirect.contains("dl.usersdrive.com") ||
                    resolvedDirect.contains(".mp4") || resolvedDirect.contains(".mkv") || resolvedDirect.contains(".zip")

            return StreamResult(
                title = videoTitle,
                streamUrl = resolvedDirect,
                downloadUrl = resolvedDirect,
                embedUrl = if (isDirectBinary) "" else fullUdUrl,
                type = if (isZip) "download" else (if (isDirectBinary) "video" else "embed"),
                isZip = isZip,
                filename = fileName
            )
        }

        // 5. FilesPayouts Link
        val fpMatch = Regex("""filespayouts?\.com\/(?:d\/|e\/|download\/)?([a-zA-Z0-9_-]{3,32})""").find(target)
        if (fpMatch != null) {
            val fpId = fpMatch.groupValues[1]
            val fullFpUrl = if (target.startsWith("http")) target else "https://filespayouts.com/$fpId"
            val embedFpUrl = "https://filespayouts.com/e/$fpId"
            var videoTitle = "FilesPayouts Movie ($fpId)"
            var resolvedDirect = fullFpUrl

            // Check if URL itself has filename (e.g. /swfpyp4qveuq/Avatar_Aang...)
            val urlNameMatch = Regex("""filespayouts?\.com\/[a-zA-Z0-9_-]+\/([^\s?#]+)""").find(target)
            if (urlNameMatch != null) {
                try {
                    val decoded = java.net.URLDecoder.decode(urlNameMatch.groupValues[1], "UTF-8")
                    videoTitle = decoded.replace(Regex("""(?i)\.(mp4|mkv|avi|webm)$"""), "").replace("_", " ").trim()
                } catch (_: Exception) {}
            }

            try {
                val html = fetchFastHtml(fullFpUrl)
                if (videoTitle.startsWith("FilesPayouts Movie")) {
                    val fnameInput = Regex("""name="fname"\s+value="([^"]+)"""", RegexOption.IGNORE_CASE).find(html)
                        ?: Regex("""<h4[^>]*>([^<]+)</h4>""", RegexOption.IGNORE_CASE).find(html)
                        ?: Regex("""<title>([^<]+)</title>""", RegexOption.IGNORE_CASE).find(html)
                    if (fnameInput != null) {
                        val raw = org.jsoup.Jsoup.parse(fnameInput.groupValues[1]).text()
                        if (raw.isNotEmpty() && !raw.contains("filespayout", true)) {
                            videoTitle = raw.replace(Regex("""(?i)\.(mp4|mkv|avi|webm)$"""), "").replace("_", " ").trim()
                        }
                    }
                }
                resolvedDirect = resolveFilesPayoutsDirect(fullFpUrl, html)
            } catch (_: Exception) {}

            val isDirectBinary = resolvedDirect.contains("/d/") || resolvedDirect.contains(".mp4")
            return StreamResult(
                title = videoTitle,
                streamUrl = if (isDirectBinary) resolvedDirect else embedFpUrl,
                downloadUrl = resolvedDirect,
                embedUrl = embedFpUrl,
                type = if (isDirectBinary) "video" else "embed"
            )
        }

        // 6. Direct Video format, Sinhalasub DDL/CDN/DLServer, Sonic Cloud Direct, or Workers.dev High-Speed Cloud
        if (target.contains("ddl.sinhalasub.net") || target.contains("cdn.sinhalasub.net") || target.contains("dlserver") || target.contains("sonic-cloud.online") || target.contains("workers.dev") || (target.contains(Regex("""\.(mp4|mkv|webm|avi)(\?.*)?$""", RegexOption.IGNORE_CASE)) && !target.contains("filespayouts.com"))) {
            return StreamResult(
                streamUrl = target,
                downloadUrl = target,
                type = "video"
            )
        }

        // 7. CineSubz (zt-links) Redirector
        if (target.contains("zt-links") || target.contains("cinesubz")) {
            try {
                val html = fetchHtml(target)
                if (html.isNotEmpty()) {
                    val doc = Jsoup.parse(html)
                    val linkHref = doc.selectFirst("#link, a#link")?.attr("href") ?: ""
                    if (linkHref.isNotEmpty()) {
                        var modified = linkHref
                            .replace("https://google.com/server11/1:/", "https://bot3.sonic-cloud.online/server1/")
                            .replace("https://google.com/server12/1:/", "https://bot3.sonic-cloud.online/server1/")
                            .replace("https://google.com/server13/1:/", "https://bot3.sonic-cloud.online/server1/")
                            .replace("https://google.com/server21/1:/", "https://bot3.sonic-cloud.online/server2/")
                            .replace("https://google.com/server22/1:/", "https://bot3.sonic-cloud.online/server2/")
                            .replace("https://google.com/server23/1:/", "https://bot3.sonic-cloud.online/server2/")
                            .replace("https://google.com/server3/1:/", "https://bot3.sonic-cloud.online/server3/")
                            .replace("https://google.com/server4/1:/", "https://bot3.sonic-cloud.online/server4/")
                            .replace("https://google.com/server5/1:/", "https://bot3.sonic-cloud.online/server5/")
                            .replace("https://google.com/server6/", "https://bot3.sonic-cloud.online/server6/")
                            .replace("https://google.com/server7/", "https://bot3.sonic-cloud.online/server7/")

                        if (modified.contains(".mp4?bot=cscloud2bot&code=")) {
                            modified = modified.replace(".mp4?bot=cscloud2bot&code=", "?ext=mp4&bot=cscloud2bot&code=")
                        } else if (modified.contains(".mp4")) {
                            modified = modified.replace(".mp4", "?ext=mp4")
                        } else if (modified.contains(".mkv")) {
                            modified = modified.replace(".mkv", "?ext=mkv")
                        }

                        if (modified != linkHref) {
                            return StreamResult(
                                streamUrl = modified,
                                downloadUrl = modified,
                                type = "video"
                            )
                        }
                    }
                }
            } catch (e: Exception) {}
        }

        // 8. Dedicated Sub.lk Token & Download Resolver (dl.sub.lk / sub.lk/download/ / sub.lk/links/)
        if (target.contains("dl.sub.lk") || target.contains("sub.lk/download") || target.contains("sub.lk/links") || (target.contains("sub.lk") && target.contains("token="))) {
            try {
                val tokenReq = okhttp3.Request.Builder()
                    .url(target)
                    .header("User-Agent", userAgent)
                    .header("Referer", "https://sub.lk/")
                    .build()
                val tokenResp = fastOkClient.newCall(tokenReq).execute()
                val tokenBody = tokenResp.body?.string() ?: ""
                val landedUrl = tokenResp.request.url.toString()

                // Check landed redirect url first
                if (landedUrl != target && !landedUrl.contains("sub.lk")) {
                    return resolve(landedUrl)
                }

                // Check HTTP 301/302 Location header
                val locHeader = tokenResp.header("Location") ?: tokenResp.priorResponse?.header("Location")
                if (!locHeader.isNullOrEmpty() && !locHeader.contains("sub.lk")) {
                    return resolve(locHeader)
                }

                // 8a-1. Google Drive inside sub.lk token redirect or body
                if (tokenBody.contains("drive.google.com") || tokenBody.contains("drive.usercontent.google.com") || landedUrl.contains("drive.google.com")) {
                    val gdId = Regex("""(?:drive\.google\.com|drive\.usercontent\.google\.com)\/(?:file\/d\/|open\?id=|uc\?id=|download\?id=)([a-zA-Z0-9_-]+)""").find(tokenBody)?.groupValues?.get(1)
                        ?: Regex("""(?:drive\.google\.com|drive\.usercontent\.google\.com)\/(?:file\/d\/|open\?id=|uc\?id=|download\?id=)([a-zA-Z0-9_-]+)""").find(landedUrl)?.groupValues?.get(1)
                    if (!gdId.isNullOrEmpty()) {
                        return StreamResult(
                            streamUrl = "https://drive.google.com/file/d/$gdId/preview",
                            downloadUrl = "https://drive.usercontent.google.com/download?id=$gdId&export=download&authuser=0",
                            embedUrl = "https://drive.google.com/file/d/$gdId/preview",
                            type = "embed"
                        )
                    }
                }

                // 8a-2. MEGA inside sub.lk token redirect or body
                if (tokenBody.contains("mega.nz") || tokenBody.contains("mega.co.nz") || tokenBody.contains("mega.io") || landedUrl.contains("mega.nz")) {
                    val megaLink = Regex("""https?:\/\/mega\.(?:nz|io|co\.nz)\/(?:file|embed|#|#!)[\w#!\-_]+""").find(tokenBody)?.value
                        ?: Regex("""https?:\/\/mega\.(?:nz|io|co\.nz)\/(?:file|embed|#|#!)[\w#!\-_]+""").find(landedUrl)?.value
                    if (!megaLink.isNullOrEmpty()) {
                        return resolve(megaLink)
                    }
                }

                // 8a-3. PixelDrain in sub.lk token body or header
                if (tokenBody.contains("pixeldrain") || landedUrl.contains("pixeldrain")) {
                    val pdId = Regex("""pixeldrain\.com\/(?:u|api\/file|e)\/([a-zA-Z0-9_-]+)""").find(tokenBody)?.groupValues?.get(1)
                        ?: Regex("""pixeldrain\.com\/(?:u|api\/file|e)\/([a-zA-Z0-9_-]+)""").find(landedUrl)?.groupValues?.get(1)
                        ?: Regex(""""id"\s*:\s*"([a-zA-Z0-9_-]+)"""").find(tokenBody)?.groupValues?.get(1)
                    if (!pdId.isNullOrEmpty()) {
                        return StreamResult(
                            streamUrl = "https://pixeldrain.com/api/file/$pdId",
                            downloadUrl = "https://pixeldrain.com/api/file/$pdId?download",
                            embedUrl = "https://pixeldrain.com/e/$pdId",
                            type = "video"
                        )
                    }
                }

                // 8b. UsersDrive in sub.lk
                if (landedUrl.contains("usersdrive") || tokenBody.contains("usersdrive")) {
                    val udId = Regex("""usersdrive\.com\/([a-zA-Z0-9]+)\.html""").find(landedUrl)?.groupValues?.get(1)
                        ?: Regex("""usersdrive\.com\/([a-zA-Z0-9]+)\.html""").find(tokenBody)?.groupValues?.get(1)
                        ?: Regex("""usersdrive\.com\/([a-zA-Z0-9]+)""").find(landedUrl)?.groupValues?.get(1)
                    if (udId != null) {
                        val rawUd = "https://usersdrive.com/$udId.html"
                        return StreamResult(
                            streamUrl = rawUd,
                            downloadUrl = rawUd,
                            embedUrl = rawUd,
                            type = "embed"
                        )
                    }
                }

                // 8c. FilesPayouts in sub.lk
                if (landedUrl.contains("filespayout") || tokenBody.contains("filespayout")) {
                    val fpId = Regex("""filespayouts?\.com\/(?:d\/|e\/)?([a-zA-Z0-9_-]+)""").find(tokenBody)?.groupValues?.get(1)
                        ?: Regex("""filespayouts?\.com\/(?:d\/|e\/)?([a-zA-Z0-9_-]+)""").find(landedUrl)?.groupValues?.get(1)
                    if (!fpId.isNullOrEmpty()) {
                        val fpUrl = "https://filespayouts.com/e/$fpId"
                        return StreamResult(
                            streamUrl = fpUrl,
                            downloadUrl = fpUrl,
                            embedUrl = fpUrl,
                            type = "embed"
                        )
                    }
                }

                // 8d. Direct MP4 / MKV inside token response
                val directMediaMatch = Regex("""href=["'](https?:\/\/[^"']*\.(?:mp4|mkv|webm)[^"']*)["']""", RegexOption.IGNORE_CASE).find(tokenBody)
                    ?: Regex("""https?:\/\/[^\s"'<>]+\.(?:mp4|mkv|webm)[^\s"'<>]*""", RegexOption.IGNORE_CASE).find(tokenBody)
                if (directMediaMatch != null) {
                    val directUrl = directMediaMatch.value
                    return StreamResult(
                        streamUrl = directUrl,
                        downloadUrl = directUrl,
                        type = "video"
                    )
                }

                // 8e. Any other direct anchor href inside token html
                val doc = Jsoup.parse(tokenBody)
                for (a in doc.select("a[href]")) {
                    val h = a.attr("href").trim()
                    if (h.startsWith("http") && !h.contains("sub.lk") && !h.contains("wa.me") && !h.contains("1xbet")) {
                        return resolve(h)
                    }
                }
            } catch (e: Exception) {}
        }

        // 9. Generic Sinhalasub & Baiscope /links/ Redirectors
        if (target.contains("/links/") || target.contains("sinhalasub") || target.contains("baiscope") || target.contains("piratelk")) {
            try {
                val html = fetchFastHtml(target)
                if (html.isNotEmpty()) {
                    // 9a. Fast Regex 1: Redirect script patterns (zluFinalLink, window.location, data-url)
                    val directMatch = Regex("""(?:var\s+)?zluFinalLink\s*=\s*['"]([^'"]+)['"]""", RegexOption.IGNORE_CASE).find(html)
                        ?: Regex("""window\.location(?:\.href)?\s*=\s*['"]([^'"]+)['"]""", RegexOption.IGNORE_CASE).find(html)
                        ?: Regex("""data-(?:url|link)\s*=\s*['"]([^'"]+)['"]""", RegexOption.IGNORE_CASE).find(html)
                        ?: Regex("""['"]([^'"]*pixeldrain\.com\/(?:u|l|api\/file|e)\/[a-zA-Z0-9_-]+)['"]""", RegexOption.IGNORE_CASE).find(html)

                    if (directMatch != null) {
                        val nextUrl = directMatch.groupValues[1].replace("\\/", "/").trim()
                        if (nextUrl.isNotEmpty() && !nextUrl.contains("/links/")) {
                            return resolve(nextUrl)
                        }
                    }

                    // 9b. Fast Regex 2: Direct CDN MP4 Stream from Sinhalasub (cdn, ddl, dlserver-01/02)
                    val cdnMatch = Regex("""https?:\/\/(?:cdn|ddl|dlserver-?\d*)\.sinhalasub\.net\/[^\s"'<>]+""").find(html)
                    if (cdnMatch != null) {
                        val directCdn = cdnMatch.value
                        return StreamResult(
                            streamUrl = directCdn,
                            downloadUrl = directCdn,
                            type = "video"
                        )
                    }

                    // 9c. Fast Regex 3: Check for PixelDrain directly inside raw html
                    val pdInHtml = Regex("""pixeldrain\.com\/(?:u|l|api\/file|e)\/([a-zA-Z0-9_-]+)""").find(html)
                    if (pdInHtml != null) {
                        val fileId = pdInHtml.groupValues[1]
                        return StreamResult(
                            streamUrl = "https://pixeldrain.com/api/file/$fileId",
                            downloadUrl = "https://pixeldrain.com/api/file/$fileId?download",
                            embedUrl = "https://pixeldrain.com/e/$fileId",
                            type = "video"
                        )
                    }

                    // 9d. Fast Regex 4: Check for MEGA link
                    val megaInHtml = Regex("""https?:\/\/mega\.(?:nz|io|co\.nz)\/(?:file|embed|#|#!)[\w#!\-_]+""").find(html)
                    if (megaInHtml != null) {
                        return resolve(megaInHtml.value)
                    }

                    // 9e. Fast Regex 5: Anchor href in HTML
                    val hrefMatch = Regex("""href=["'](https?:\/\/[^"']*(?:workers\.dev|pixeldrain|drive\.google|usersdrive|mega\.nz|gofile|filespayout|sinhalasub\.net|cinesubz|akirabox|\.mp4|\.mkv)[^"']*)["']""", RegexOption.IGNORE_CASE).find(html)
                    if (hrefMatch != null) {
                        val nextUrl = hrefMatch.groupValues[1].replace("\\/", "/").trim()
                        if (nextUrl.isNotEmpty() && !nextUrl.contains("/links/")) {
                            return resolve(nextUrl)
                        }
                    }

                    // 9f. Only parse DOM with Jsoup if fast regex patterns did not hit
                    val doc = Jsoup.parse(html)
                    var pickedLink = ""
                    for (el in doc.select("a[href], button[data-href], a[data-href], div[data-link]")) {
                        val h = el.attr("href").ifEmpty { el.attr("data-href") }.ifEmpty { el.attr("data-link") }.trim()
                        if (h.startsWith("http") && !h.contains("1xbet") && !h.contains("affpa") && !h.contains("wa.me") && !h.contains("/links/")) {
                            if (h.contains("pixeldrain") || h.contains("sinhalasub") || h.contains("workers.dev") || h.contains("drive.google") || h.contains("mega.nz") || h.contains("usersdrive") || h.contains("filespayout") || h.contains(".mp4") || h.contains(".mkv") || h.contains("gofile") || h.contains("1fichier")) {
                                pickedLink = h
                                break
                            }
                        }
                    }
                    if (pickedLink.isNotEmpty()) {
                        return resolve(pickedLink)
                    }
                }
            } catch (e: Exception) {}
        }

        val isRedirector = target.contains("/links/") || target.contains("goto")
        val isEmbedTarget = isRedirector || target.contains("embed") || target.contains("vidlink") || target.contains("multiembed") || target.contains("autoembed") || target.contains("2embed") || target.contains("vidsrc") || target.contains("filespayout") || target.contains("mega.nz") || target.contains("usersdrive") || target.contains("drive.google") || target.contains("cinejoy")
        return StreamResult(
            streamUrl = target,
            downloadUrl = target,
            embedUrl = if (isEmbedTarget) target else "",
            type = if (isEmbedTarget) "embed" else "video"
        )
    }

    private fun resolveUsersDriveDirect(fullUdUrl: String, html: String): String {
        try {
            val doc = Jsoup.parse(html)
            val existingDirect = doc.selectFirst("a[href*='userdrive.org'], a[href*='dl.usersdrive.com'], a.btn-download[href*='/d/'], a[href*='/d/']")?.attr("href")
            if (!existingDirect.isNullOrEmpty() && (existingDirect.contains("http://") || existingDirect.contains("https://"))) {
                return existingDirect.trim()
            }
            val form = doc.selectFirst("form[name='F1'], form[action*='usersdrive'], form")
            if (form != null) {
                val formBuilder = okhttp3.FormBody.Builder()
                for (input in form.select("input[name]")) {
                    val name = input.attr("name")
                    val value = input.attr("value")
                    if (name != "method_free" && name != "down_script" && name != "adblock_detected") {
                        formBuilder.add(name, value)
                    }
                }
                formBuilder.add("op", "download2")
                formBuilder.add("method_free", "Free Download")
                formBuilder.add("down_script", "1")
                formBuilder.add("adblock_detected", "0")
                val req = okhttp3.Request.Builder()
                    .url(fullUdUrl)
                    .header("User-Agent", userAgent)
                    .header("Referer", fullUdUrl)
                    .header("Origin", "https://usersdrive.com")
                    .post(formBuilder.build())
                    .build()
                val resp = okHttpClient.newCall(req).execute()
                val postHtml = resp.body?.string() ?: ""
                val dlLink = Jsoup.parse(postHtml).selectFirst("a#direct_link, a.btn-download, a[href*='userdrive.org'], a[href*='dl.usersdrive.com'], a[href*='/d/'], a#downloadbtn[href]")?.attr("href")
                    ?: Regex("""href=["']\s*(https?:\/\/[^"']*(?:userdrive\.org|usersdrive\.com)[^"']*(?:\/d\/|\.mp4|\.mkv|\.zip)[^"']*)["']""", RegexOption.IGNORE_CASE).find(postHtml)?.groupValues?.get(1)
                    ?: Regex("""href=["']\s*(https?:\/\/d\d+\.userdrive\.org[^\s"']+)["']""").find(postHtml)?.groupValues?.get(1)
                    ?: Regex("""href=["']\s*(https?:\/\/dl\.usersdrive\.com\/[^"']+)["']""").find(postHtml)?.groupValues?.get(1)
                if (!dlLink.isNullOrEmpty()) return dlLink.trim()
            }
        } catch (_: Exception) {}
        return fullUdUrl
    }

    private fun resolveFilesPayoutsDirect(fullFpUrl: String, html: String): String {
        try {
            val doc = Jsoup.parse(html)
            val form = doc.selectFirst("form[name='F1'], form[action*='filespayout'], form")
            if (form != null) {
                val formBuilder = okhttp3.FormBody.Builder()
                for (input in form.select("input[name]")) {
                    val name = input.attr("name")
                    val value = input.attr("value")
                    formBuilder.add(name, value)
                }
                formBuilder.add("op", "download2")
                formBuilder.add("method_free", "")
                val req = okhttp3.Request.Builder()
                    .url(fullFpUrl)
                    .header("User-Agent", userAgent)
                    .header("Referer", fullFpUrl)
                    .post(formBuilder.build())
                    .build()
                val resp = okHttpClient.newCall(req).execute()
                val postHtml = resp.body?.string() ?: ""
                val dlLink = Jsoup.parse(postHtml).selectFirst("a#direct_link, a.btn-download, a[href*='/d/'], a[href*='download']")?.attr("href")
                    ?: Regex("""href=["'](https?:\/\/[^"']*filespayout[^"']*\/d\/[^"']+)["']""").find(postHtml)?.groupValues?.get(1)
                if (!dlLink.isNullOrEmpty()) return dlLink
            }
        } catch (_: Exception) {}
        return fullFpUrl
    }

    private fun resolveCinejoyDirect(targetUrl: String): StreamResult? {
        return try {
            val isTv = targetUrl.contains("/tv/")
            var tmdbId = ""
            var season = 1
            var episode = 1

            val tvMatch = Regex("""/tv/(\d+)(?:/(\d+)/(\d+))?""").find(targetUrl)
            if (tvMatch != null) {
                tmdbId = tvMatch.groupValues[1]
                season = tvMatch.groupValues.getOrNull(2)?.toIntOrNull() ?: 1
                episode = tvMatch.groupValues.getOrNull(3)?.toIntOrNull() ?: 1
            } else {
                val movieMatch = Regex("""/movie/(\d+)""").find(targetUrl)
                if (movieMatch != null) {
                    tmdbId = movieMatch.groupValues[1]
                } else {
                    val anyDigit = Regex("""(\d{4,9})""").find(targetUrl)
                    if (anyDigit != null) {
                        tmdbId = anyDigit.groupValues[1]
                    }
                }
            }

            if (tmdbId.isNotEmpty()) {
                val dlApiUrl = if (isTv) "https://downloads.shegu.st/tv/$tmdbId/$season/$episode" else "https://downloads.shegu.st/movie/$tmdbId"
                val req = Request.Builder()
                    .url(dlApiUrl)
                    .header("User-Agent", userAgent)
                    .header("Referer", "https://cinejoy.to/")
                    .build()

                fastOkClient.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val body = resp.body?.string() ?: ""
                        if (body.isNotEmpty()) {
                            val json = JSONObject(body)
                            val links = json.optJSONArray("links")
                            if (links != null && links.length() > 0) {
                                val candidates = mutableListOf<JSONObject>()
                                for (i in 0 until links.length()) {
                                    val linkObj = links.optJSONObject(i) ?: continue
                                    val url = linkObj.optString("url", "")
                                    if (url.isNotEmpty()) {
                                        candidates.add(linkObj)
                                    }
                                }

                                // Sort candidates: prefer 1080p FHD (ideal bitrate), then 4K UHD, then others
                                val sortedCandidates = candidates.sortedWith(Comparator { a, b ->
                                    val qA = a.optInt("quality", 1080)
                                    val qB = b.optInt("quality", 1080)
                                    val scoreA = if (qA == 1080) 3 else (if (qA >= 2160) 2 else 1)
                                    val scoreB = if (qB == 1080) 3 else (if (qB >= 2160) 2 else 1)
                                    scoreB.compareTo(scoreA)
                                })

                                for (cand in sortedCandidates) {
                                    val rawUrl = cand.optString("url", "")
                                    val encodedUrl = com.clouddrive.leech.extractor.resolvers.netflix.NetflixResolver.sanitizeAndEncodeMediaUrl(rawUrl)
                                    if (encodedUrl.isEmpty()) continue

                                    // Quick preflight probe to bypass 403 downloadQuotaExceeded
                                    val isAlive = try {
                                        val probeReq = Request.Builder()
                                            .url(encodedUrl)
                                            .header("User-Agent", userAgent)
                                            .header("Referer", "https://cinejoy.to/")
                                            .header("Origin", "https://cinejoy.to")
                                            .header("Range", "bytes=0-0")
                                            .build()
                                        val probeClient = fastOkClient.newBuilder()
                                            .connectTimeout(2500, TimeUnit.MILLISECONDS)
                                            .readTimeout(2500, TimeUnit.MILLISECONDS)
                                            .build()
                                        probeClient.newCall(probeReq).execute().use { pr ->
                                            pr.isSuccessful || pr.code == 206 || pr.code == 200
                                        }
                                    } catch (_: Exception) { false }

                                    if (isAlive) {
                                        val qNum = cand.optInt("quality", 1080)
                                        val qTag = if (qNum >= 2160) "4K UHD" else (if (qNum >= 1080) "1080p FHD" else "${qNum}p HD")
                                        val size = cand.optString("size", "")
                                        val rawName = cand.optString("name", "").replace(Regex("""^4K CINEJOY\s*""", RegexOption.IGNORE_CASE), "").trim()
                                        val bestTitle = if (rawName.isNotEmpty()) rawName else "Cinejoy Direct Cloud Stream"
                                        val bestQualityTag = if (size.isNotEmpty()) "$qTag ($size)" else qTag
                                        val fullTitle = "$bestTitle [$bestQualityTag]"

                                        return StreamResult(
                                            title = fullTitle,
                                            streamUrl = encodedUrl,
                                            downloadUrl = encodedUrl,
                                            embedUrl = targetUrl,
                                            type = "video",
                                            headers = mapOf(
                                                "Referer" to "https://cinejoy.to/",
                                                "Origin" to "https://cinejoy.to"
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // If direct streams are not available or alive, return working high-speed embed stream
                val fallbackUrl = if (isTv) {
                    "https://vidsrc2.ru/embed/tv/$tmdbId/$season/$episode"
                } else {
                    "https://vidsrc2.ru/embed/movie/$tmdbId"
                }
                return StreamResult(
                    title = "Cinejoy VIP Stream",
                    streamUrl = fallbackUrl,
                    downloadUrl = fallbackUrl,
                    embedUrl = fallbackUrl,
                    type = "embed"
                )
            }
            null
        } catch (_: Exception) {
            null
        }
    }
}
