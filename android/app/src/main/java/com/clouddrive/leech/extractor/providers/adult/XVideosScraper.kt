package com.clouddrive.leech.extractor.providers.adult

import com.clouddrive.leech.extractor.models.MediaItem
import com.clouddrive.leech.extractor.models.StreamFormat
import com.clouddrive.leech.extractor.models.StreamResult
import com.clouddrive.leech.proxy.LocalMediaProxy
import okhttp3.OkHttpClient
import org.jsoup.Jsoup
import java.net.URLEncoder

class XVideosScraper(client: OkHttpClient = defaultClient) : BaseAdultScraper(client) {

    fun search(query: String, page: Int = 1): List<MediaItem> {
        val cleanQ = query.trim()
        val isTrending = cleanQ.isEmpty() ||
                cleanQ.equals("popular", ignoreCase = true) ||
                cleanQ.equals("trending", ignoreCase = true) ||
                cleanQ.equals("for-you", ignoreCase = true) ||
                cleanQ.equals("viral", ignoreCase = true) ||
                cleanQ.equals("surprise", ignoreCase = true) ||
                cleanQ.equals("fresh", ignoreCase = true) ||
                cleanQ.equals("all", ignoreCase = true) ||
                cleanQ.equals("top", ignoreCase = true)

        val targetUrls = mutableListOf<String>()
        if (isTrending) {
            when (page) {
                1 -> {
                    // Page 1: Home feed gives 48 videos! We also pull new/1 to yield 70+ videos
                    targetUrls.add("https://www.xvideos.com/")
                    targetUrls.add("https://www.xvideos.com/new/1")
                }
                2 -> {
                    targetUrls.add("https://www.xvideos.com/new/2")
                    targetUrls.add("https://www.xvideos.com/new/3")
                }
                3 -> {
                    targetUrls.add("https://www.xvideos.com/new/4")
                    targetUrls.add("https://www.xvideos.com/new/5")
                }
                else -> {
                    val p1 = (page - 1) * 2
                    val p2 = p1 + 1
                    targetUrls.add("https://www.xvideos.com/new/$p1")
                    targetUrls.add("https://www.xvideos.com/new/$p2")
                }
            }
        } else {
            val encoded = URLEncoder.encode(cleanQ, "UTF-8")
            val pIndex = (page - 1).coerceAtLeast(0) * 2
            targetUrls.add("https://www.xvideos.com/?k=$encoded&p=$pIndex")
            targetUrls.add("https://www.xvideos.com/?k=$encoded&p=${pIndex + 1}")
        }

        val allItems = mutableListOf<MediaItem>()
        val seenUrls = HashSet<String>()

        for (url in targetUrls) {
            try {
                val html = fetchText(url)
                if (html.isEmpty()) continue
                val doc = Jsoup.parse(html)

                val elements = doc.select(".thumb-block, div[id^='video_']")
                for (el in elements) {
                    val titleAnchor = el.selectFirst(".thumb-under p.title a, p.title a, .title a, a[title]")
                        ?: el.selectFirst("a[href*='/video']")
                        ?: continue

                    val href = titleAnchor.attr("href").ifEmpty { el.selectFirst("a[href*='/video']")?.attr("href") ?: "" }
                    if (href.isEmpty() || !href.contains("/video")) continue
                    // Exclude non-video links
                    if (href.contains("/videos-i-like") || href.contains("/video-channels") || href.contains("/tags/")) continue

                    val fullUrl = if (href.startsWith("http")) href else "https://www.xvideos.com$href"
                    if (!seenUrls.add(fullUrl)) continue

                    var rawTitle = titleAnchor.attr("title").ifEmpty {
                        el.selectFirst(".thumb-under p.title a, p.title a, .title a")?.text() ?: ""
                    }.ifEmpty {
                        el.selectFirst("img")?.attr("alt") ?: ""
                    }.trim()

                    // If title is just a resolution badge like "1080pCC" or "1440p", fallback to clean text
                    if (rawTitle.matches(Regex("""^(?:\d+p(?:CC)?|HD|FHD|4K)$""", RegexOption.IGNORE_CASE))) {
                        rawTitle = el.selectFirst(".thumb-under p.title a, p.title a")?.text()
                            ?: el.selectFirst("img")?.attr("alt")
                            ?: rawTitle
                    }

                    // Clean out trailing duration if in title
                    val title = rawTitle.replace(Regex("""\s+\d+\s*(?:min|sec|h|m|s)\s*$""", RegexOption.IGNORE_CASE), "").trim()
                    if (title.length < 3 || title.matches(Regex("""^(?:\d+p(?:CC)?|HD|FHD|4K)$""", RegexOption.IGNORE_CASE))) continue

                    val img = el.selectFirst("img")
                    var thumb = img?.attr("data-src")?.ifEmpty { null }
                        ?: img?.attr("data-mzl")?.ifEmpty { null }
                        ?: img?.attr("data-sfwthumb")?.ifEmpty { null }
                        ?: img?.attr("src")?.ifEmpty { null }
                        ?: ""
                    if (thumb.contains("blank.gif") || thumb.contains("pixel.gif")) {
                        thumb = img?.attr("data-src") ?: img?.attr("data-mzl") ?: ""
                    }
                    if (thumb.startsWith("//")) thumb = "https:$thumb"
                    if (thumb.isEmpty() || thumb.contains("blank.gif")) {
                        thumb = "https://images.unsplash.com/photo-1518173946687-a4c8a383392e?w=400"
                    }

                    val dur = el.selectFirst(".duration, span.duration")?.text()?.trim()?.ifEmpty { "HD" } ?: "HD"
                    val hdMark = el.selectFirst(".video-hd-mark")?.text()?.trim() ?: "1080p"
                    val rating = if (hdMark.contains("1440") || hdMark.contains("2160") || hdMark.contains("4k", ignoreCase = true)) {
                        "4K UHD"
                    } else if (hdMark.contains("1080")) {
                        "1080p FHD"
                    } else {
                        "720p HD"
                    }

                    val metaText = el.selectFirst(".metadata")?.text() ?: el.text()
                    val viewsMatch = Regex("""(\d+(?:\.\d+)?[KMkmbB]?)\s*views""", RegexOption.IGNORE_CASE).find(metaText)
                    val views = viewsMatch?.groupValues?.get(1)?.let { "$it views" } ?: "150K+"

                    allItems.add(
                        MediaItem(
                            id = fullUrl.hashCode().toString(),
                            title = title,
                            link = fullUrl,
                            poster = thumb,
                            thumbnail = thumb,
                            duration = dur,
                            rating = rating,
                            year = "2026",
                            views = views,
                            source = "XVideos"
                        )
                    )
                }
            } catch (_: Exception) {}
        }
        return allItems
    }

    fun resolve(targetUrl: String): StreamResult? {
        if (!targetUrl.contains("xvideos.com")) return null

        val vidMatch = Regex("""video\.([0-9a-zA-Z_]+)""").find(targetUrl)
            ?: Regex("""video(\d+)""").find(targetUrl)
            ?: Regex("""video-([0-9a-zA-Z_]+)""").find(targetUrl)
        val vidId = vidMatch?.groupValues?.get(1) ?: ""
        val embedUrl = if (vidId.isNotEmpty()) "https://www.xvideos.com/embedframe/$vidId" else targetUrl

        val qualities = mutableListOf<StreamFormat>()
        var directStream = ""

        try {
            val html = fetchText(targetUrl)
            val mp4High = Regex("""html5player\.setVideoUrlHigh\('([^']+)'\)""").find(html)?.groupValues?.get(1)
            val mp4Low = Regex("""html5player\.setVideoUrlLow\('([^']+)'\)""").find(html)?.groupValues?.get(1)
            val hls = Regex("""html5player\.setVideoHLS\('([^']+)'\)""").find(html)?.groupValues?.get(1)

            if (!mp4High.isNullOrEmpty()) {
                val proxiedHigh = LocalMediaProxy.getProxiedUrl(mp4High)
                qualities.add(StreamFormat(formatId = "xv-high", quality = "1080p", label = "1080p / 720p HD", url = proxiedHigh, streamUrl = proxiedHigh, downloadUrl = mp4High, isEmbed = false))
                directStream = proxiedHigh
            }
            if (!mp4Low.isNullOrEmpty()) {
                val proxiedLow = LocalMediaProxy.getProxiedUrl(mp4Low)
                qualities.add(StreamFormat(formatId = "xv-low", quality = "480p", label = "480p / 360p SD", url = proxiedLow, streamUrl = proxiedLow, downloadUrl = mp4Low, isEmbed = false))
                if (directStream.isEmpty()) directStream = proxiedLow
            }
            if (!hls.isNullOrEmpty()) {
                val proxiedHls = LocalMediaProxy.getProxiedUrl(hls)
                qualities.add(StreamFormat(formatId = "xv-hls", quality = "Auto", label = "Auto (Adaptive)", url = proxiedHls, streamUrl = proxiedHls, downloadUrl = hls, isEmbed = false))
            }
        } catch (e: Exception) {}

        qualities.add(StreamFormat(formatId = "xv-embed", quality = "Embed", label = "Cinema Web Player", url = embedUrl, streamUrl = embedUrl, downloadUrl = targetUrl, isEmbed = true))

        return StreamResult(
            title = "XVideos HD Stream",
            streamUrl = if (directStream.isNotEmpty()) directStream else embedUrl,
            downloadUrl = if (directStream.isNotEmpty()) directStream else targetUrl,
            embedUrl = embedUrl,
            type = if (directStream.isNotEmpty()) "video" else "embed",
            qualities = qualities
        )
    }
}
