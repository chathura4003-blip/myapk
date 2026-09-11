package com.clouddrive.leech.extractor.providers.movies

import com.clouddrive.leech.extractor.models.MediaDetails
import com.clouddrive.leech.extractor.models.StreamFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.net.URLEncoder
import java.util.concurrent.atomic.AtomicReference

/**
 * Dedicated Extractor for Movie Details, Direct Cloud Download Links, and Universal Cinema Player Qualities.
 */
class MovieDetailExtractor(
    client: OkHttpClient = defaultClient
) : BaseMovieScraper(client) {

    fun getDetails(url: String): MediaDetails {
        return try {
            var rawUrl = url.trim()
            val slugTitle = rawUrl.split("?").firstOrNull()?.trimEnd('/')?.split("/")?.lastOrNull()
                ?.replace(Regex("""[-_]"""), " ")
                ?.replace(Regex("""(?i)\b(sinhala|subtitles?|2026|2025|2024|movie|film)\b"""), "")
                ?.trim() ?: ""

            // ⚡ 0. Fast YTS 4K Cinema REST API Handler (Instant Parallel Mirror Race, bypasses Cloudflare HTML blocks)
            if (rawUrl.contains("yts.mx") || rawUrl.contains("yts.ag") || rawUrl.contains("yts.lt") || rawUrl.contains("yts.am") || rawUrl.contains("yts.bz") || rawUrl.contains("yts.gg") || rawUrl.contains("yts.")) {
                try {
                    val imdbMatch = Regex("""tt\d+""").find(rawUrl)?.value ?: ""
                    val queryTerm = if (imdbMatch.isNotEmpty()) imdbMatch else slugTitle
                    val encodedQ = java.net.URLEncoder.encode(queryTerm, "UTF-8")
                    val ytsApiUrls = listOf(
                        "https://yts.gg/api/v2/list_movies.json?query_term=$encodedQ&limit=1",
                        "https://yts.lt/api/v2/list_movies.json?query_term=$encodedQ&limit=1",
                        "https://yts.ag/api/v2/list_movies.json?query_term=$encodedQ&limit=1",
                        "https://yts.am/api/v2/list_movies.json?query_term=$encodedQ&limit=1",
                        "https://yts.bz/api/v2/list_movies.json?query_term=$encodedQ&limit=1",
                        "https://yts.mx/api/v2/list_movies.json?query_term=$encodedQ&limit=1"
                    )

                    val deferredBody = kotlinx.coroutines.CompletableDeferred<String>()
                    runBlocking {
                        val jobs = ytsApiUrls.map { apiUrl ->
                            async(Dispatchers.IO) {
                                if (deferredBody.isCompleted) return@async
                                try {
                                    val req = Request.Builder().url(apiUrl).header("User-Agent", userAgent).build()
                                    okHttpClient.newCall(req).execute().use { resp ->
                                        if (resp.isSuccessful && !deferredBody.isCompleted) {
                                            val b = resp.body?.string() ?: ""
                                            if (b.contains("\"movies\"")) {
                                                deferredBody.complete(b)
                                            }
                                        }
                                    }
                                } catch (_: Exception) {}
                            }
                        }
                        withTimeoutOrNull(6000L) {
                            deferredBody.await()
                        }
                        jobs.forEach { it.cancel() }
                    }

                    val body = if (deferredBody.isCompleted) deferredBody.getCompleted() else ""
                    if (body.contains("\"movies\"")) {
                        val jsonObj = com.google.gson.JsonParser.parseString(body).asJsonObject
                        val dataObj = jsonObj.getAsJsonObject("data")
                        val moviesArr = dataObj?.getAsJsonArray("movies")
                        if (moviesArr != null && moviesArr.size() > 0) {
                            val mObj = moviesArr[0].asJsonObject
                            val imdbCodeStr = mObj.get("imdb_code")?.asString ?: imdbMatch
                            val title = mObj.get("title_long")?.asString ?: mObj.get("title")?.asString ?: slugTitle
                            var poster = mObj.get("large_cover_image")?.asString ?: mObj.get("medium_cover_image")?.asString ?: ""
                            if (poster.startsWith("http://")) poster = poster.replace("http://", "https://")
                            val rating = mObj.get("rating")?.asString ?: "7.5"
                            val year = mObj.get("year")?.asString ?: "2026"
                            val synopsis = mObj.get("description_full")?.asString ?: mObj.get("summary")?.asString ?: "YTS 4K Cinema Stream & Downloads available."

                            val qualities = mutableListOf<StreamFormat>()
                            val seenUrls = HashSet<String>()

                            if (imdbCodeStr.isNotEmpty()) {
                                qualities.add(
                                    StreamFormat(
                                        formatId = "yts-stream-1",
                                        quality = "🌟 Server 1: VidSrc2 Pro – 1080p Full HD • Multi-Audio",
                                        label = "🌟 Server 1: VidSrc2 Pro – 1080p Full HD • Multi-Audio",
                                        provider = "VidSrc2 Pro",
                                        ext = "mp4",
                                        url = "https://vidsrc2.ru/embed/movie/$imdbCodeStr",
                                        streamUrl = "https://vidsrc2.ru/embed/movie/$imdbCodeStr",
                                        downloadUrl = "https://vidsrc2.ru/embed/movie/$imdbCodeStr",
                                        size = "1080p Full HD",
                                        isEmbed = true
                                    )
                                )
                                qualities.add(
                                    StreamFormat(
                                        formatId = "yts-stream-cinejoy",
                                        quality = "🚀 Server 2: Cinejoy VIP – 4K UHD / 1080p Stream",
                                        label = "🚀 Server 2: Cinejoy VIP – 4K UHD / 1080p Stream",
                                        provider = "Cinejoy VIP",
                                        ext = "mp4",
                                        url = "https://cinejoy.to/watch/movie/$imdbCodeStr",
                                        streamUrl = "https://cinejoy.to/watch/movie/$imdbCodeStr",
                                        downloadUrl = "https://cinejoy.to/watch/movie/$imdbCodeStr",
                                        size = "4K / 1080p Ultra HD",
                                        isEmbed = true
                                    )
                                )
                            }

                            val torrents = mObj.getAsJsonArray("torrents")
                            if (torrents != null) {
                                for (tElem in torrents) {
                                    val t = tElem.asJsonObject
                                    val qRaw = t.get("quality")?.asString ?: "1080p"
                                    val tType = t.get("type")?.asString ?: "Bluray"
                                    val sizeStr = t.get("size")?.asString ?: "1.4 GB"
                                    val hash = t.get("hash")?.asString ?: ""
                                    val tUrl = t.get("url")?.asString ?: ""
                                    val magnetUrl = if (hash.isNotEmpty()) {
                                        "magnet:?xt=urn:btih:$hash&dn=${java.net.URLEncoder.encode(title, "UTF-8")}&tr=udp://open.demonii.com:1337/announce&tr=udp://tracker.openbittorrent.com:80&tr=udp://tracker.coppersurfer.tk:6969"
                                    } else ""

                                    val qualLabel = when {
                                        qRaw.contains("2160") || qRaw.contains("4k") -> "4K UHD ($tType)"
                                        qRaw.contains("1080") -> "1080p FHD ($tType)"
                                        qRaw.contains("720") -> "720p HD ($tType)"
                                        else -> "$qRaw ($tType)"
                                    }

                                    if (tUrl.isNotEmpty() && seenUrls.add(tUrl)) {
                                        qualities.add(
                                            StreamFormat(
                                                formatId = "yts-torrent-${qualities.size}",
                                                quality = qualLabel,
                                                label = "$qualLabel - Direct Torrent File",
                                                provider = "YTS High-Speed Torrent ($sizeStr)",
                                                ext = "torrent",
                                                url = tUrl,
                                                streamUrl = if (magnetUrl.isNotEmpty()) magnetUrl else tUrl,
                                                downloadUrl = tUrl,
                                                size = sizeStr,
                                                isEmbed = false
                                            )
                                        )
                                    }
                                    if (magnetUrl.isNotEmpty() && seenUrls.add(magnetUrl)) {
                                        qualities.add(
                                            StreamFormat(
                                                formatId = "yts-magnet-${qualities.size}",
                                                quality = qualLabel,
                                                label = "$qualLabel - 1-Click Magnet URI",
                                                provider = "YTS Magnet Pipe ($sizeStr)",
                                                ext = "magnet",
                                                url = magnetUrl,
                                                streamUrl = magnetUrl,
                                                downloadUrl = magnetUrl,
                                                size = sizeStr,
                                                isEmbed = false
                                            )
                                        )
                                    }
                                }
                            }

                            val directStream = qualities.firstOrNull { it.isEmbed }?.streamUrl
                            val directDl = qualities.firstOrNull { !it.isEmbed && it.downloadUrl.isNotEmpty() }?.downloadUrl

                            return MediaDetails(
                                title = title,
                                poster = poster,
                                synopsis = synopsis,
                                rating = "⭐ $rating",
                                year = year,
                                qualities = qualities,
                                directStreamUrl = directStream,
                                directDownloadUrl = directDl
                            )
                        }
                    }
                } catch (_: Exception) {}
            }

            var html = ""
            try {
                html = fetchHtml(rawUrl)
            } catch (_: Exception) {}

            var doc = if (html.isNotEmpty()) Jsoup.parse(html, rawUrl) else Jsoup.parse("<html><head><title>$slugTitle</title></head><body></body></html>")

            val titleEl = doc.selectFirst(".details-title h3, .details-title, h1.entry-title, h1, .sheader .title, .entry-title, .title-head")
            var rawTitle = titleEl?.text()?.trim() ?: ""

            if (rawTitle.isEmpty() || rawTitle.contains("problem", true) || rawTitle.contains("advertisement", true) || rawTitle.contains("comment", true) || rawTitle.contains("timeout", true) || rawTitle.contains("500 internal", true) || rawTitle.contains("index of", true)) {
                rawTitle = doc.selectFirst("meta[property=og:title]")?.attr("content")
                    ?: doc.title()
                    ?: ""
            }

            if (rawTitle.isEmpty() || rawTitle.contains("500", true) || rawTitle.contains("timeout", true) || rawTitle.contains("error", true) || rawTitle.contains("index of", true)) {
                if (slugTitle.isNotEmpty()) {
                    rawTitle = slugTitle.replaceFirstChar { it.uppercase() }
                }
            }

            var cleanTitle = rawTitle
                .replace(Regex("""(?i)\b(Sinhala\s*Subtitles?|සිංහල\s*උපසිර[ැසි]+|SinhalaSub|Baiscope|CineSubz|Sub\.lk|PirateLK|–\s*SinhalaSub\.LK)\b.*"""), "")
                .replace(Regex("""[\|–—]\s*(SinhalaSub|Baiscope|CineSubz|Sub\.lk|සිංහල).*""", RegexOption.IGNORE_CASE), "")
                .replace(Regex("""\[.*?\]"""), "")
                .trim()

            if (cleanTitle.isEmpty() || cleanTitle.length < 2 || cleanTitle.contains("problem", true) || cleanTitle.contains("error", true)) {
                cleanTitle = if (slugTitle.isNotEmpty()) slugTitle.replaceFirstChar { it.uppercase() } else "Cinema Movie"
            }

            var poster = doc.selectFirst("meta[property=og:image]")?.attr("content")
                ?: doc.selectFirst(".sheader .poster img, .poster img, #info .poster img, .details-pic img, .entry-content img, .featured-image img, picture img, img.wp-post-image, .post-thumbnail img, img")?.attr("data-lazy-src")
                ?: doc.selectFirst(".sheader .poster img, .poster img, #info .poster img, .details-pic img, .entry-content img, .featured-image img, picture img, img.wp-post-image, .post-thumbnail img, img")?.attr("abs:src")
                ?: doc.selectFirst(".sheader .poster img, .poster img, #info .poster img, .details-pic img, .entry-content img, .featured-image img, picture img, img.wp-post-image, .post-thumbnail img, img")?.attr("src")
                ?: ""
            if (poster.startsWith("//")) poster = "https:$poster"
            if (poster.startsWith("http://")) poster = poster.replace("http://", "https://")
            if (poster.startsWith("data:image") || poster.contains("PinExt") || poster.contains("transparent")) poster = ""
            poster = poster.replace(Regex("""-\d+x\d+(\.(webp|jpg|png|jpeg))$""", RegexOption.IGNORE_CASE), "$1")

            var synopsis = doc.selectFirst(".details-desc, .data-story, #info .wp-content, .description, .synopsis, #sinopsis, .movie-description, .storyline, .entry-content p")?.text()?.trim()
                ?: doc.selectFirst("meta[property=og:description]")?.attr("content")?.trim()
                ?: ""

            // Extract Cast, Director, Country & Details
            val extraDetails = StringBuilder()
            val director = doc.select("p:contains(Director:), p:contains(අධ්‍යක්ෂණය:)").firstOrNull()?.text()?.trim()
            val stars = doc.select("p:contains(Stars:), p:contains(රංගනය:)").firstOrNull()?.text()?.trim()
            val country = doc.select("p:contains(Country:), p:contains(රට:)").firstOrNull()?.text()?.trim()

            if (!director.isNullOrEmpty()) extraDetails.append("\n🎬 ").append(director)
            if (!stars.isNullOrEmpty()) extraDetails.append("\n⭐ ").append(stars)
            if (!country.isNullOrEmpty()) extraDetails.append("\n🌍 ").append(country)

            if (poster.isEmpty() || poster.contains("unsplash")) {
                try {
                    val searcher = SinhalasubScraper(okHttpClient)
                    val sMatches = searcher.search(cleanTitle)
                    val match = sMatches.firstOrNull { it.poster.isNotEmpty() && !it.poster.contains("unsplash") }
                    if (match != null) {
                        poster = match.poster
                    }
                } catch (_: Exception) {}
            }

            if (synopsis.isEmpty()) {
                synopsis = "High-speed cloud stream and multi-quality downloads available."
            }
            if (extraDetails.isNotEmpty()) {
                synopsis = "$synopsis\n$extraDetails".trim()
            }
            if (synopsis.length > 750) synopsis = synopsis.substring(0, 750) + "..."

            val qualities = mutableListOf<StreamFormat>()
            val seenUrls = HashSet<String>()

            // 0. Resolve YTS Movies directly via REST API (Concurrent Parallel Mirror Race)
            if (url.contains("yts.mx") || url.contains("yts.ag") || url.contains("yts.lt") || url.contains("yts.am") || url.contains("yts.bz") || url.contains("yts.gg")) {
                try {
                    val imdbMatch = Regex("""tt\d+""").find(url)?.value ?: ""
                    val queryTerm = if (imdbMatch.isNotEmpty()) imdbMatch else cleanTitle
                    val encodedQ = java.net.URLEncoder.encode(queryTerm, "UTF-8")
                    val ytsApiUrls = listOf(
                        "https://yts.gg/api/v2/list_movies.json?query_term=$encodedQ&limit=1",
                        "https://yts.lt/api/v2/list_movies.json?query_term=$encodedQ&limit=1",
                        "https://yts.ag/api/v2/list_movies.json?query_term=$encodedQ&limit=1",
                        "https://yts.am/api/v2/list_movies.json?query_term=$encodedQ&limit=1",
                        "https://yts.mx/api/v2/list_movies.json?query_term=$encodedQ&limit=1"
                    )

                    val deferredBody = kotlinx.coroutines.CompletableDeferred<String>()
                    runBlocking {
                        val jobs = ytsApiUrls.map { apiUrl ->
                            async(Dispatchers.IO) {
                                if (deferredBody.isCompleted) return@async
                                try {
                                    val req = Request.Builder().url(apiUrl).header("User-Agent", userAgent).build()
                                    okHttpClient.newCall(req).execute().use { resp ->
                                        if (resp.isSuccessful && !deferredBody.isCompleted) {
                                            val b = resp.body?.string() ?: ""
                                            if (b.contains("\"movies\"")) {
                                                deferredBody.complete(b)
                                            }
                                        }
                                    }
                                } catch (_: Exception) {}
                            }
                        }
                        withTimeoutOrNull(3500L) {
                            deferredBody.await()
                        }
                        jobs.forEach { it.cancel() }
                    }

                    val body = if (deferredBody.isCompleted) deferredBody.getCompleted() else ""
                    if (body.contains("\"movies\"")) {
                        val jsonObj = com.google.gson.JsonParser.parseString(body).asJsonObject
                        val dataObj = jsonObj.getAsJsonObject("data")
                        val moviesArr = dataObj?.getAsJsonArray("movies")
                        if (moviesArr != null && moviesArr.size() > 0) {
                                    val mObj = moviesArr[0].asJsonObject
                                    val imdbCodeStr = mObj.get("imdb_code")?.asString ?: imdbMatch
                                    if (imdbCodeStr.isNotEmpty()) {
                                        qualities.add(
                                            StreamFormat(
                                                formatId = "yts-stream-1",
                                                quality = "1080p FHD Direct Cinema Stream",
                                                label = "1080p FHD - Ultra Fast Direct Stream",
                                                provider = "VidSrc2 High-Speed Stream",
                                                ext = "mp4",
                                                url = "https://vidsrc2.ru/embed/movie/$imdbCodeStr",
                                                streamUrl = "https://vidsrc2.ru/embed/movie/$imdbCodeStr",
                                                downloadUrl = "https://vidsrc2.ru/embed/movie/$imdbCodeStr",
                                                size = "Instant Stream",
                                                isEmbed = true
                                            )
                                        )
                                    }

                                    val torrents = mObj.getAsJsonArray("torrents")
                                    if (torrents != null) {
                                        for (tElem in torrents) {
                                            val t = tElem.asJsonObject
                                            val qRaw = t.get("quality")?.asString ?: "1080p"
                                            val tType = t.get("type")?.asString ?: "Bluray"
                                            val sizeStr = t.get("size")?.asString ?: "1.4 GB"
                                            val hash = t.get("hash")?.asString ?: ""
                                            val tUrl = t.get("url")?.asString ?: ""
                                            val magnetUrl = if (hash.isNotEmpty()) {
                                                "magnet:?xt=urn:btih:$hash&dn=${java.net.URLEncoder.encode(cleanTitle, "UTF-8")}&tr=udp://open.demonii.com:1337/announce&tr=udp://tracker.openbittorrent.com:80&tr=udp://tracker.coppersurfer.tk:6969"
                                            } else ""

                                            val qualLabel = when {
                                                qRaw.contains("2160") || qRaw.contains("4k") -> "4K UHD ($tType)"
                                                qRaw.contains("1080") -> "1080p FHD ($tType)"
                                                qRaw.contains("720") -> "720p HD ($tType)"
                                                else -> "$qRaw ($tType)"
                                            }

                                            if (tUrl.isNotEmpty() && seenUrls.add(tUrl)) {
                                                qualities.add(
                                                    StreamFormat(
                                                        formatId = "yts-torrent-${qualities.size}",
                                                        quality = qualLabel,
                                                        label = "$qualLabel - Direct Torrent File",
                                                        provider = "YTS High-Speed Torrent ($sizeStr)",
                                                        ext = "torrent",
                                                        url = tUrl,
                                                        streamUrl = if (imdbCodeStr.isNotEmpty()) "https://vidsrc2.ru/embed/movie/$imdbCodeStr" else (if (magnetUrl.isNotEmpty()) magnetUrl else tUrl),
                                                        downloadUrl = tUrl,
                                                        size = sizeStr,
                                                        isEmbed = false
                                                    )
                                                )
                                            }
                                            if (magnetUrl.isNotEmpty() && seenUrls.add(magnetUrl)) {
                                                qualities.add(
                                                    StreamFormat(
                                                        formatId = "yts-magnet-${qualities.size}",
                                                        quality = qualLabel,
                                                        label = "$qualLabel - 1-Click Magnet URI",
                                                        provider = "YTS Magnet Pipe ($sizeStr)",
                                                        ext = "magnet",
                                                        url = magnetUrl,
                                                        streamUrl = if (imdbCodeStr.isNotEmpty()) "https://vidsrc2.ru/embed/movie/$imdbCodeStr" else magnetUrl,
                                                        downloadUrl = magnetUrl,
                                                        size = sizeStr,
                                                        isEmbed = false
                                                    )
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        } catch (_: Exception) {}
                    }

            // 0. Fetch sub.lk Dynamic AJAX Download Panel (GDRIVE, MEGA, USERSDRIVE, HLS Watch)
            if ((url.contains("sub.lk") || url.contains("cineru.lk")) && !url.contains("sinhalasub")) {
                val postId = Regex("""postid-(\d+)""").find(html)?.groupValues?.get(1)
                    ?: Regex("""post_id.*?value="?(\d+)"?""").find(html)?.groupValues?.get(1)
                    ?: Regex(""""post_id":\s*"?(\d+)"?""").find(html)?.groupValues?.get(1)
                    ?: Regex("""class="[^"]*post-(\d+)[^"]*"""").find(html)?.groupValues?.get(1)
                    ?: doc.selectFirst("body[class*='postid-']")?.attr("class")?.let { Regex("""postid-(\d+)""").find(it)?.groupValues?.get(1) }
                    ?: doc.selectFirst("input[name='comment_post_ID'], #post_id, input[name='post_id']")?.attr("value")

                if (!postId.isNullOrEmpty()) {
                    try {
                        val formBody = okhttp3.FormBody.Builder()
                            .add("action", "cs_download_data")
                            .add("post_id", postId)
                            .build()

                        val req = Request.Builder()
                            .url("https://sub.lk/wp-admin/admin-ajax.php")
                            .post(formBody)
                            .header("User-Agent", userAgent)
                            .header("Referer", url)
                            .header("X-Requested-With", "XMLHttpRequest")
                            .build()

                        val resp = okHttpClient.newCall(req).execute()
                        val respBody = resp.body?.string() ?: ""
                        if (respBody.isNotEmpty() && respBody.contains("\"data\"")) {
                            val dataJson = org.json.JSONObject(respBody)
                            val panelHtml = dataJson.optString("data", "")
                            if (panelHtml.isNotEmpty()) {
                                // Direct Streams (1080p & 720p HLS Streams) - Microsecond Regex first
                                val watch1080 = Regex("""data-watch-1080=["']([^"']+)["']""").find(panelHtml)?.groupValues?.get(1)
                                val watch720 = Regex("""data-watch-720=["']([^"']+)["']""").find(panelHtml)?.groupValues?.get(1)

                                val panelDoc = Jsoup.parse(panelHtml, url)

                                if (!watch1080.isNullOrEmpty() && seenUrls.add(watch1080)) {
                                    qualities.add(
                                        StreamFormat(
                                            formatId = "sublk-watch-1080",
                                            quality = "1080p FHD Cinema",
                                            label = "1080p FHD - Cineru Web Player",
                                            provider = "Cineru Web Player",
                                            ext = "embed",
                                            url = watch1080,
                                            streamUrl = watch1080,
                                            downloadUrl = "",
                                            size = "Web Stream",
                                            isEmbed = true
                                        )
                                    )
                                }
                                if (!watch720.isNullOrEmpty() && seenUrls.add(watch720)) {
                                    qualities.add(
                                        StreamFormat(
                                            formatId = "sublk-watch-720",
                                            quality = "720p HD Cinema",
                                            label = "720p HD - Cineru Web Player",
                                            provider = "Cineru Web Player",
                                            ext = "embed",
                                            url = watch720,
                                            streamUrl = watch720,
                                            downloadUrl = "",
                                            size = "Web Stream",
                                            isEmbed = true
                                        )
                                    )
                                }

                                // Download Cards with exact .namer file size & resolution
                                val cards = panelDoc.select(".download-card")
                                if (cards.isNotEmpty()) {
                                    for (card in cards) {
                                        val namerEl = card.selectFirst(".namer, .copy p, p")
                                        val namerText = namerEl?.text()?.trim() ?: ""

                                        // 1. Extract resolution
                                        var qual = when {
                                            Regex("""4k|2160p""", RegexOption.IGNORE_CASE).containsMatchIn(namerText) -> "4K UHD"
                                            Regex("""1080p""", RegexOption.IGNORE_CASE).containsMatchIn(namerText) -> "1080p FHD"
                                            Regex("""720p""", RegexOption.IGNORE_CASE).containsMatchIn(namerText) -> "720p HD"
                                            Regex("""480p|sd""", RegexOption.IGNORE_CASE).containsMatchIn(namerText) -> "480p SD"
                                            else -> ""
                                        }

                                        // 2. Extract accurate MB/GB size
                                        val sizeMatch = Regex("""\b\d+(\.\d+)?\s*(GB|MB)\b""", RegexOption.IGNORE_CASE).find(namerText)
                                        var fileSize = sizeMatch?.value?.uppercase() ?: ""

                                        if (qual.isEmpty()) {
                                            if (fileSize.contains("MB")) {
                                                val mb = fileSize.replace(Regex("""[^0-9.]"""), "").toDoubleOrNull() ?: 0.0
                                                qual = if (mb <= 500.0) "480p SD" else "720p HD"
                                            } else if (fileSize.contains("GB")) {
                                                val gb = fileSize.replace(Regex("""[^0-9.]"""), "").toDoubleOrNull() ?: 0.0
                                                qual = if (gb >= 1.5) "1080p FHD" else "720p HD"
                                            } else {
                                                qual = "720p HD"
                                            }
                                        }
                                        if (fileSize.isEmpty()) {
                                            fileSize = if (qual.contains("1080")) "2.1 GB" else (if (qual.contains("720")) "900 MB" else "400 MB")
                                        }

                                        // 3. Process top-level .btns in this card
                                        val btns = card.select(".btns, a.btns, span.btns")
                                        for (btn in btns) {
                                            val tUrl = btn.attr("data-link").ifEmpty { btn.attr("href") }
                                            val btnClass = btn.className().lowercase()
                                            val txt = btn.text().trim().uppercase()

                                            if (tUrl.isEmpty() || !tUrl.startsWith("http") || btnClass.contains("telegram") || txt.contains("TELEGRAM") || tUrl.contains("telegram") || tUrl.contains("t.me")) continue

                                            val prov = when {
                                                btnClass.contains("pixeldrain") || txt.contains("PIXELDRAIN") -> "PixelDrain (1 Gbps Direct Cloud)"
                                                btnClass.contains("mega") || txt.contains("MEGA") -> "MEGA High-Speed Cloud"
                                                btnClass.contains("userdrive") || btnClass.contains("usersdrive") || txt.contains("USERDRIVE") -> "UsersDrive Direct Cloud"
                                                btnClass.contains("gdrive") || txt.contains("GDRIVE") || txt.contains("GOOGLE") -> "Google Drive Direct Cloud"
                                                else -> "Direct Cloud Mirror"
                                            }

                                            if (seenUrls.add(tUrl)) {
                                                val isEmbedFormat = false // Sub.lk tokens must always be resolved first
                                                val isMkvFormat = Regex("""mkv|x265|hevc""", RegexOption.IGNORE_CASE).containsMatchIn(namerText)
                                                qualities.add(
                                                    StreamFormat(
                                                        formatId = "sublk-token-${qualities.size}",
                                                        quality = qual,
                                                        label = "$qual - $prov",
                                                        provider = prov,
                                                        ext = if (isMkvFormat) "mkv" else "mp4",
                                                        url = tUrl,
                                                        streamUrl = tUrl,
                                                        downloadUrl = tUrl,
                                                        size = fileSize,
                                                        isEmbed = isEmbedFormat
                                                    )
                                                )
                                            }
                                        }
                                    }
                                } else {
                                    // Fallback if cards not present
                                    val tokenLinks = panelDoc.select(".btns[data-link*='dl.sub.lk'], a.btns[data-link*='dl.sub.lk']")
                                    for (tEl in tokenLinks) {
                                        val tUrl = tEl.attr("data-link").ifEmpty { tEl.attr("href") }
                                        val btnClass = tEl.className().lowercase()
                                        val txt = tEl.text().trim().uppercase()
                                        if (tUrl.isEmpty() || !tUrl.startsWith("http") || btnClass.contains("telegram") || txt.contains("TELEGRAM") || tUrl.contains("telegram")) continue

                                        val prov = when {
                                            btnClass.contains("pixeldrain") || txt.contains("PIXELDRAIN") -> "PixelDrain (1 Gbps Direct Cloud)"
                                            btnClass.contains("mega") || txt.contains("MEGA") -> "MEGA High-Speed Cloud"
                                            btnClass.contains("userdrive") || btnClass.contains("usersdrive") || txt.contains("USERDRIVE") -> "UsersDrive Direct Cloud"
                                            btnClass.contains("filespayout") || txt.contains("FILESPAYOUTS") -> "FilesPayouts Direct Cloud"
                                            btnClass.contains("gdrive") || txt.contains("GDRIVE") || txt.contains("GOOGLE") -> "Google Drive Direct Cloud"
                                            else -> "Direct Cloud Mirror"
                                        }
                                        val qual = if (btnClass.contains("hc_film") || txt.contains("720")) "720p HD" else "1080p FHD"
                                        val fileSize = if (qual == "720p HD") "900 MB" else "2.1 GB"

                                        if (seenUrls.add(tUrl)) {
                                            val isEmbedFormat = false
                                            qualities.add(
                                                StreamFormat(
                                                    formatId = "sublk-token-${qualities.size}",
                                                    quality = qual,
                                                    label = "$qual - $prov",
                                                    provider = prov,
                                                    ext = "mp4",
                                                    url = tUrl,
                                                    streamUrl = tUrl,
                                                    downloadUrl = tUrl,
                                                    size = fileSize,
                                                    isEmbed = isEmbedFormat
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    } catch (_: Exception) {}
                }
            }

            // 0. Extract TMDB / IMDb ID and generate Global Cloud Streams (VidLink, VidSrc, VidVault, 2Embed)
            val tmdbMatch = Regex("""themoviedb\.org\/movie\/(\d+)""").find(html)
                ?: Regex("""themoviedb\.org\/tv\/(\d+)""").find(html)
                ?: Regex("""data-tmdb=["'](\d+)["']""").find(html)
                ?: Regex("""cinejoy\.to\/(?:watch\/)?(?:movie|tv)\/(\d+)""").find(url)
                ?: Regex("""^netflix_(\d+)$""").find(url)
            val imdbMatch = Regex("""(?:imdb\.com\/title\/)(tt\d+)""").find(html)?.groupValues?.get(1)
                ?: Regex("""\b(tt\d{7,8})\b""").find(html)?.groupValues?.get(1)

            var effectiveMovieId = tmdbMatch?.groupValues?.get(1) ?: imdbMatch
            if (effectiveMovieId.isNullOrEmpty() && cleanTitle.isNotEmpty() && cleanTitle.length >= 3) {
                try {
                    val encodedTitle = java.net.URLEncoder.encode(cleanTitle, "UTF-8")
                    val cinemetaUrl = "https://v3-cinemeta.strem.io/catalog/movie/top/search=$encodedTitle.json"
                    val cReq = Request.Builder().url(cinemetaUrl).header("User-Agent", userAgent).build()
                    val cResp = okHttpClient.newCall(cReq).execute()
                    val cBody = cResp.body?.string() ?: ""
                    if (cBody.isNotEmpty()) {
                        val cJson = org.json.JSONObject(cBody)
                        val metas = cJson.optJSONArray("metas")
                        if (metas != null && metas.length() > 0) {
                            val firstMeta = metas.getJSONObject(0)
                            val fImdb = firstMeta.optString("imdb_id").ifEmpty { firstMeta.optString("id") }
                            if (fImdb.isNotEmpty() && fImdb.startsWith("tt")) {
                                effectiveMovieId = fImdb
                            }
                        }
                    }
                } catch (_: Exception) {}
            }
            val isNetflixOrCinejoy = url.contains("cinejoy.to") || url.contains("netflix") || url.startsWith("netflix_")
            if (isNetflixOrCinejoy && !effectiveMovieId.isNullOrEmpty()) {
                val isImdb = effectiveMovieId.startsWith("tt")

                // ⚡ 0. Cinejoy Direct Cloud MKV Streams (4K UHD / 1080p FHD / 720p HD - Native Full Speed!)
                if (!isImdb) {
                    try {
                        val isTv = url.contains("/tv/")
                        val dlApiUrl = if (isTv) "https://downloads.shegu.st/tv/$effectiveMovieId/1/1" else "https://downloads.shegu.st/movie/$effectiveMovieId"
                        val dlReq = Request.Builder()
                            .url(dlApiUrl)
                            .header("User-Agent", userAgent)
                            .header("Referer", "https://cinejoy.to/")
                            .build()
                        okHttpClient.newCall(dlReq).execute().use { dlResp ->
                            if (dlResp.isSuccessful) {
                                val dlBody = dlResp.body?.string() ?: ""
                                if (dlBody.isNotEmpty()) {
                                    val dlJson = org.json.JSONObject(dlBody)
                                    val links = dlJson.optJSONArray("links")
                                    if (links != null) {
                                        for (idx in 0 until links.length()) {
                                            val lObj = links.optJSONObject(idx) ?: continue
                                            val rawLUrl = lObj.optString("url", "")
                                            if (rawLUrl.isEmpty()) continue
                                            val lUrl = com.clouddrive.leech.extractor.resolvers.netflix.NetflixResolver.sanitizeAndEncodeMediaUrl(rawLUrl)
                                            if (lUrl.isEmpty() || !seenUrls.add(lUrl)) continue
                                            val qNum = lObj.optInt("quality", 1080)
                                            val resTag = if (qNum >= 2160) "4K UHD" else (if (qNum >= 1080) "1080p FHD" else "${qNum}p HD")
                                            val lSize = lObj.optString("size", "Direct")
                                            val lName = lObj.optString("name", "").replace(Regex("""^4K CINEJOY\s*""", RegexOption.IGNORE_CASE), "").trim()
                                            qualities.add(
                                                StreamFormat(
                                                    formatId = "cinejoy-direct-$idx",
                                                    quality = resTag,
                                                    label = "⚡ Cinejoy Direct Stream: $resTag ($lSize) $lName",
                                                    provider = "Cinejoy Direct Cloud • $resTag ($lSize)",
                                                    ext = "mkv",
                                                    url = lUrl,
                                                    streamUrl = lUrl,
                                                    downloadUrl = lUrl,
                                                    size = lSize,
                                                    isEmbed = false
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    } catch (_: Exception) {}
                }

                // Fallback Stream 1: VidSrc2 Pro (Multi-Audio & Subtitles)
                val vidsrc2Url = "https://vidsrc2.ru/embed/movie/$effectiveMovieId"
                if (seenUrls.add(vidsrc2Url)) {
                    qualities.add(
                        StreamFormat(
                            formatId = "movie-vidsrc2-$effectiveMovieId",
                            quality = "1080p FHD",
                            label = "🌟 Server 1: VidSrc2 Pro – 1080p Full HD • Multi-Audio (සිංහල/English/හින්දි)",
                            provider = "VidSrc2 VIP Multi-Audio & Subtitles",
                            ext = "mp4",
                            url = vidsrc2Url,
                            streamUrl = vidsrc2Url,
                            downloadUrl = "",
                            size = "1080p FHD Multi-Audio",
                            isEmbed = true
                        )
                    )
                }

                // Fallback Stream 2: Cinejoy VIP Web Embed
                val cinejoyUrl = "https://cinejoy.to/watch/movie/$effectiveMovieId"
                if (seenUrls.add(cinejoyUrl)) {
                    qualities.add(
                        StreamFormat(
                            formatId = "movie-cinejoy-$effectiveMovieId",
                            quality = "4K / 1080p UHD",
                            label = "🚀 Server 2: Cinejoy VIP Web Stream – 4K UHD / 1080p Multi-Server",
                            provider = "Cinejoy VIP Stream (4K / 1080p)",
                            ext = "mp4",
                            url = cinejoyUrl,
                            streamUrl = cinejoyUrl,
                            downloadUrl = "",
                            size = "4K / 1080p Ultra HD Multi-Server",
                            isEmbed = true
                        )
                    )
                }
            }

            // 1. Extract Structured Table Rows (Sinhalasub, Baiscopes, Sub.lk, PirateLK)
            val rows = doc.select("table tr, .download-table tr, #download tr, .download tr")
            for (row in rows) {
                val linkEl = row.selectFirst("a[href*='/links/'], a[href*='goto'], a[href*='pixeldrain'], a[href*='drive.google'], a[href*='usersdrive'], a[href*='gofile'], a[href*='dlserver'], a[href*='workers.dev'], a[href*='download'], a.btn-download, a") ?: continue
                val href = linkEl.attr("abs:href").ifEmpty { linkEl.attr("href") }
                val rowText = row.text().trim()
                if (href.isEmpty() || href.startsWith("#") || href.startsWith("javascript") || isIgnoredLink(href, linkEl.text(), rowText) || !seenUrls.add(href)) continue

                val lowerHref = href.lowercase()
                val lowerRowText = rowText.lowercase()

                val cells = row.select("td")
                var extractedQuality = ""
                var extractedSize = ""

                for ((cIdx, td) in cells.withIndex()) {
                    val t = td.text().trim()
                    if (cIdx == 1) extractedQuality = t
                    else if (cIdx == 2) extractedSize = t

                    val sizeM = Regex("""\b\d+(\.\d+)?\s*(GB|MB)\b""", RegexOption.IGNORE_CASE).find(t)
                    if (extractedSize.isEmpty() && sizeM != null) extractedSize = sizeM.value

                    val qualM = Regex("""\b(2160p|4k|1080p|720p|480p|360p|FHD|HD|SD)\b""", RegexOption.IGNORE_CASE).find(t)
                    if (extractedQuality.isEmpty() && qualM != null) extractedQuality = qualM.value
                }

                val qual = when {
                    extractedQuality.contains("2160", true) || extractedQuality.contains("4k", true) || lowerRowText.contains("4k") -> "4K UHD"
                    extractedQuality.contains("1080", true) || extractedQuality.contains("fhd", true) || lowerRowText.contains("1080") -> "1080p FHD"
                    extractedQuality.contains("720", true) || extractedQuality.contains("hd", true) || lowerRowText.contains("720") -> "720p HD"
                    extractedQuality.contains("480", true) || extractedQuality.contains("sd", true) || lowerRowText.contains("480") -> "480p SD"
                    else -> "1080p FHD"
                }

                val size = if (extractedSize.isNotEmpty()) extractedSize else {
                    Regex("""\b\d+(\.\d+)?\s*(GB|MB)\b""", RegexOption.IGNORE_CASE).find(rowText)?.value ?: when (qual) {
                        "4K UHD" -> "4.8 GB"
                        "1080p FHD" -> "2.1 GB"
                        "720p HD" -> "1.1 GB"
                        "480p SD" -> "650 MB"
                        else -> "1.4 GB"
                    }
                }

                var finalHref = href
                var provider = when {
                    href.contains("pixeldrain") || rowText.contains("pixeldrain", true) -> "PixelDrain (1 Gbps Direct Cloud)"
                    href.contains("drive2.baiscopeslk") || href.contains("workers.dev") -> "Baiscopes Google Drive (1 Gbps Direct Cloud)"
                    href.contains("drive.google") || rowText.contains("drive.google", true) || rowText.contains("gdrive", true) -> "Google Drive Direct Cloud"
                    href.contains("mega.nz") || rowText.contains("mega", true) -> "MEGA High-Speed Cloud"
                    href.contains("usersdrive") || href.contains("userdrive") || rowText.contains("usersdrive", true) -> "UsersDrive Direct Cloud"
                    rowText.contains("DLServer-01", true) || href.contains("dlserver-01") -> "DLServer-01 (1 Gbps Direct CDN)"
                    rowText.contains("DLServer-02", true) || href.contains("dlserver-02") -> "DLServer-02 Fast CDN Mirror"
                    rowText.contains("FilesPayout", true) || href.contains("filespayout") -> "FilesPayouts Direct Cloud"
                    rowText.contains("akirabox", true) || href.contains("akirabox") -> "AkiraBox Cloud Server"
                    href.contains("zt-links") || url.contains("cinesubz") -> "CineSubz Sonic-Cloud Pipe"
                    url.contains("baiscope") || href.contains("baiscope") -> "Baiscopes Google Drive (1 Gbps Direct Cloud)"
                    href.contains("gofile") -> "GoFile Unlimited Cloud"
                    href.contains("1fichier") -> "1Fichier Cloud Storage"
                    else -> "Fast Cloud Server"
                }

                val isEmbedFormat = provider.contains("FilesPayout") || provider.contains("UsersDrive") || provider.contains("MEGA") || finalHref.contains("filespayout") || finalHref.contains("usersdrive") || finalHref.contains("mega")
                qualities.add(
                    StreamFormat(
                        formatId = "movie-row-${qualities.size}",
                        quality = qual,
                        label = "$qual - $provider",
                        provider = provider,
                        ext = "mp4",
                        url = finalHref,
                        streamUrl = finalHref,
                        downloadUrl = finalHref,
                        size = size,
                        isEmbed = isEmbedFormat
                    )
                )
            }

            // 2.1 Resolve dl.sub.lk Token Links (PixelDrain Direct Video / Subtitle)
            val dlTokenEl = doc.selectFirst("[data-link*='dl.sub.lk'], a[href*='dl.sub.lk']")
            val dlTokenUrl = dlTokenEl?.attr("data-link")?.ifEmpty { dlTokenEl.attr("href") } ?: ""
            if (dlTokenUrl.isNotEmpty() && dlTokenUrl.startsWith("http")) {
                try {
                    val tokenHtml = fetchHtml(dlTokenUrl)
                    if (tokenHtml.contains("pixeldrain", ignoreCase = true)) {
                        val pdId = Regex(""""id":"([a-zA-Z0-9]+)"""").find(tokenHtml)?.groupValues?.get(1)
                            ?: Regex("""pixeldrain\.com/(?:u|api/file)/([a-zA-Z0-9]+)""").find(tokenHtml)?.groupValues?.get(1)
                        val pdName = Regex(""""name":"([^"]+)"""").find(tokenHtml)?.groupValues?.get(1)
                            ?: Regex("""<title>(.*?)(?:~|\s*-\s*pixeldrain)""").find(tokenHtml)?.groupValues?.get(1)?.trim()
                            ?: "Video File"
                        val pdSize = Regex(""""size":(\d+)""").find(tokenHtml)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
                        val sizeMb = if (pdSize > 0) String.format(java.util.Locale.US, "%.1f MB", pdSize / (1024.0 * 1024.0)) else "HD Video"

                        if (!pdId.isNullOrEmpty()) {
                            val directVideoUrl = "https://pixeldrain.com/api/file/$pdId"
                            if (seenUrls.add(directVideoUrl)) {
                                val qual = if (pdName.contains("1080")) "1080p FHD" else if (pdName.contains("720")) "720p HD" else "HD Video"
                                qualities.add(
                                    0,
                                    StreamFormat(
                                        formatId = "dl-sublk-${qualities.size}",
                                        quality = qual,
                                        label = "$qual - PixelDrain (1 Gbps Direct Cloud)",
                                        provider = "PixelDrain (1 Gbps Direct Cloud)",
                                        ext = "mp4",
                                        url = directVideoUrl,
                                        streamUrl = directVideoUrl,
                                        downloadUrl = directVideoUrl,
                                        size = sizeMb,
                                        isEmbed = false
                                    )
                                )
                            }
                        }
                    }
                } catch (_: Exception) {}
            }

            // 2. Extract Download Buttons (Sub.lk, PirateLK, Baiscopes, Direct Links)
            val buttons = doc.select("table tr a, a.su-button, a.dlm-download-link, a.movie-download-button, a[href*='pixeldrain'], a[href*='drive.google'], a[href*='usersdrive'], a[href*='userscloud'], a[href*='/links/'], a[href*='workers.dev'], a[href*='mega.nz'], a[href*='gofile'], a[href*='1fichier'], a[href*='mediafire'], a.btn-download, a.btn, a.maxbutton, .download-links a, a[class*='download'], a[href*='piratelk.com/download/']")
            for (a in buttons) {
                val href = a.attr("abs:href").ifEmpty { a.attr("href") }
                val text = a.text().trim()
                val parentText = a.parent()?.text() ?: ""
                val trText = a.closest("tr")?.text() ?: ""
                val btnContext = "$parentText $trText ${a.attr("class")} ${a.attr("id")}"

                if (href.isEmpty() || href.startsWith("#") || href.startsWith("javascript:") || isIgnoredLink(href, text, btnContext) || !seenUrls.add(href)) continue

                val lowerText = "$text $btnContext $href".lowercase()

                val qual = when {
                    lowerText.contains("2160") || lowerText.contains("4k") -> "4K UHD"
                    lowerText.contains("1080") || lowerText.contains("fhd") -> "1080p FHD"
                    lowerText.contains("720") || lowerText.contains("hd") -> "720p HD"
                    lowerText.contains("480") || lowerText.contains("sd") -> "480p SD"
                    else -> "1080p FHD"
                }

                val sizeMatch = Regex("""\b\d+(\.\d+)?\s*(GB|MB)\b""", RegexOption.IGNORE_CASE).find(lowerText)
                val size = sizeMatch?.value?.uppercase() ?: when (qual) {
                    "4K UHD" -> "4.8 GB"
                    "1080p FHD" -> "1.5 GB"
                    "720p HD" -> "950 MB"
                    "480p SD" -> "450 MB"
                    else -> "1.4 GB"
                }

                val baiscopeServerNames = listOf(
                    "PixelDrain (1 Gbps Direct Cloud - Server 1)",
                    "Google Drive High-Speed Cloud (Server 2)",
                    "Cloudflare Workers Direct Pipe (Server 3)",
                    "Baiscope High-Speed Cloud Mirror 01 (Server 4)",
                    "Baiscope High-Speed Cloud Mirror 02 (Server 5)",
                    "Baiscope Ultra Fast CDN (Server 6)"
                )

                val provider = when {
                    href.contains("pixeldrain") -> "PixelDrain (1 Gbps Direct Cloud)"
                    href.contains("drive.google") -> "Google Drive Direct Cloud"
                    href.contains("mega.nz") || href.contains("mega") -> "MEGA High-Speed Cloud"
                    href.contains("usersdrive") || href.contains("userdrive") -> "UsersDrive Direct Cloud"
                    href.contains("dlserver-01") -> "DLServer-01 (1 Gbps Direct CDN)"
                    href.contains("dlserver-02") -> "DLServer-02 Fast CDN Mirror"
                    href.contains("filespayout") -> "FilesPayouts Direct Cloud"
                    href.contains("akirabox") -> "AkiraBox Cloud Server"
                    href.contains("workers.dev") -> "Cloudflare Workers Direct Pipe"
                    url.contains("baiscope") || href.contains("baiscope") -> {
                        val count = qualities.count { it.provider.contains("Server") || it.provider.contains("Baiscope") }
                        if (count < baiscopeServerNames.size) baiscopeServerNames[count] else "Baiscope High-Speed Cloud (Server ${count + 1})"
                    }
                    href.contains("gofile") -> "GoFile Unlimited Cloud"
                    href.contains("1fichier") -> "1Fichier Cloud Storage"
                    url.contains("piratelk") || href.contains("piratelk") -> "PirateLK Direct Cloud"
                    else -> "Direct Cloud Stream"
                }

                val isZip = href.endsWith(".zip", true) || href.endsWith(".rar", true) || lowerText.contains(".zip") || lowerText.contains("zip archive")
                val finalQual = if (isZip) "$qual (ZIP Package)" else qual
                val finalProvider = if (isZip && provider.contains("UsersDrive")) "UsersDrive Direct Cloud (ZIP Archive)" else provider
                val isEmbedFormat = !isZip && (href.contains("usersdrive") || href.contains("userdrive") || href.contains("filespayout") || href.contains("mega") || provider.contains("UsersDrive") || provider.contains("FilesPayouts") || provider.contains("MEGA"))
                qualities.add(
                    StreamFormat(
                        formatId = "movie-btn-${qualities.size}",
                        quality = finalQual,
                        label = "$finalQual - $finalProvider",
                        provider = finalProvider,
                        ext = if (isZip) "zip" else "mp4",
                        url = href,
                        streamUrl = href,
                        downloadUrl = href,
                        size = size,
                        isEmbed = isEmbedFormat,
                        isZip = isZip
                    )
                )
            }



            // Deduplicate: Keep only unique high-speed CDN & Cloud streams
            val dedupedQualities = mutableListOf<StreamFormat>()
            val seenKeys = mutableSetOf<String>()
            val seenCleanUrls = mutableSetOf<String>()

            for (q in qualities) {
                val rawUrl = if (q.downloadUrl.isNotEmpty()) q.downloadUrl else q.streamUrl
                if (isIgnoredLink(rawUrl, q.label, "${q.provider} ${q.streamUrl}")) {
                    continue
                }
                val cleanUrl = if (rawUrl.contains("token=") || rawUrl.contains("id=")) rawUrl.trim() else rawUrl.substringBefore("?").trim().trimEnd('/')
                val provKey = q.provider.lowercase().replace(Regex("[^a-z0-9]"), "")
                val qualKey = q.quality.lowercase().replace(Regex("[^a-z0-9]"), "")
                val cardFingerprint = "$provKey-$qualKey-${cleanUrl.takeLast(15)}"

                if (cleanUrl.isNotEmpty() && (!seenCleanUrls.add(cleanUrl) || !seenKeys.add(cardFingerprint))) {
                    continue
                }
                dedupedQualities.add(q)
            }

            // Prioritize high-speed direct cloud and CDN mirrors (PixelDrain, DLServer, Direct CDN, GDrive) over ad-heavy captcha hosts
            val sortedQualities = dedupedQualities.sortedWith(
                compareBy<StreamFormat> { q ->
                    val p = (q.provider + " " + q.label + " " + q.url).lowercase()
                    when {
                        p.contains("pixeldrain") -> 0
                        p.contains("dlserver") || p.contains("sinhalasub.net") -> 1
                        p.contains("drive.google") || p.contains("workers.dev") -> 2
                        p.contains("cineru") -> 3
                        p.contains("vidsrc2") || p.contains("vidlink") || p.contains("vidsrc") -> 4
                        p.contains("filespayout") -> 8
                        p.contains("usersdrive") || p.contains("userdrive") -> 9
                        else -> 5
                    }
                }.thenByDescending { q ->
                    val qual = q.quality.lowercase()
                    when {
                        qual.contains("4k") || qual.contains("2160") -> 4
                        qual.contains("1080") || qual.contains("fhd") -> 3
                        qual.contains("720") || qual.contains("hd") -> 2
                        else -> 1
                    }
                }
            )

            val bestDirectDl = sortedQualities.firstOrNull { 
                !it.isEmbed && it.downloadUrl.isNotEmpty() && !it.downloadUrl.contains("cinerustreams") && !it.downloadUrl.contains("vidsrc2") && !it.downloadUrl.contains("vidlink") && !it.downloadUrl.contains("vidsrc") && !it.downloadUrl.contains("multiembed") 
            }
            val bestDirectStream = sortedQualities.firstOrNull { !it.isEmbed } ?: sortedQualities.firstOrNull()

            MediaDetails(
                title = cleanTitle,
                poster = poster.ifEmpty { "https://images.unsplash.com/photo-1536440136628-849c177e76a1?w=600&q=80" },
                synopsis = synopsis,
                rating = "HD Cinema",
                year = "2026",
                qualities = sortedQualities,
                directStreamUrl = bestDirectStream?.streamUrl,
                directDownloadUrl = bestDirectDl?.downloadUrl ?: sortedQualities.firstOrNull { it.downloadUrl.isNotEmpty() }?.downloadUrl
            )
        } catch (e: Exception) {
            val fallbackTitle = url.split("/").filter { it.isNotEmpty() }.lastOrNull()?.replace("-", " ") ?: "Cinema Movie"
            MediaDetails(
                title = fallbackTitle,
                poster = "https://images.unsplash.com/photo-1536440136628-849c177e76a1?w=600&q=80",
                synopsis = "No streams available for this title.",
                qualities = emptyList()
            )
        }
    }

    private fun isIgnoredLink(href: String, text: String, extraContext: String = ""): Boolean {
        val h = href.lowercase()
        val t = text.lowercase()
        val ctx = extraContext.lowercase()
        val combined = "$h $t $ctx"

        // 1. Social & Telegram filtering
        if (combined.contains("telegram") || combined.contains("telagram") || combined.contains("t.me") ||
            combined.contains("tg://") || combined.contains("telegram.me") || combined.contains("telegram.dog") ||
            combined.contains("tg.me") || combined.contains("facebook.com") || combined.contains("whatsapp") ||
            combined.contains("instagram.com") || combined.contains("twitter.com") || combined.contains("x.com") ||
            combined.contains("youtube.com") || combined.contains("tiktok.com") || combined.contains("pinterest.com") ||
            combined.contains("join channel") || combined.contains("join group") || combined.contains("contact us") ||
            combined.contains("about us") || combined.contains("dmca") || combined.contains("privacy-policy")) {
            return true
        }

        // 2. Ads, non-media tag/category links, and Subtitle zip/rar/srt filtering
        if (combined.contains("affpa") || combined.contains("1xbet") || combined.contains("betway") ||
            combined.contains("admin-ajax") || combined.contains("trailer") ||
            h.contains("/tag/") || h.contains("/category/") || h.contains("/author/") ||
            h.contains("/comments/") || h.contains("/feed/") || h.contains("xmlrpc.php") ||
            h.contains("wp-content") || h.contains("wp-json") || h.contains("#mega-cat") ||
            h.contains("multiembed.mov") || h.contains("multiembed") || h.contains("vidlink.pro") ||
            h.contains("filespayout") || t.contains("filespayout") || ctx.contains("filespayout") ||
            t.contains("බෙංගාලි") || t.contains("මලයාලම්") || t.contains("සිංහල හඬකැවූ") ||
            h.endsWith(".srt") || h.endsWith(".vtt") || h.endsWith(".sub")) {
            return true
        }

        if (h.endsWith(".zip") || h.endsWith(".rar") || h.endsWith(".7z")) {
            val isMovieRelease = h.contains("usersdrive") || h.contains("userdrive") ||
                Regex("""(?i)\b(2160p|4k|1080p|720p|480p|fhd|hd|webrip|bluray|\d+(\.\d+)?\s*(gb|mb)|lama|film|movie|පිටපත)\b""").containsMatchIn(combined)
            if (!isMovieRelease && (t.contains("subtitle") || t.contains("sub") || t.contains("උපසිරැසි පමණක්"))) {
                return true
            }
        }

        return false
    }
}
