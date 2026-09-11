package com.clouddrive.leech.extractor.providers.adult

import com.clouddrive.leech.extractor.models.MediaItem
import com.clouddrive.leech.extractor.models.StreamFormat
import com.clouddrive.leech.extractor.models.StreamResult
import com.clouddrive.leech.proxy.LocalMediaProxy
import okhttp3.OkHttpClient
import org.jsoup.Jsoup
import java.net.URLEncoder

class XNXXScraper(client: OkHttpClient = defaultClient) : BaseAdultScraper(client) {

    fun search(query: String, page: Int = 1): List<MediaItem> {
        val cleanQ = query.trim()
        val isTrending = cleanQ.isEmpty() ||
                cleanQ.equals("popular", ignoreCase = true) ||
                cleanQ.equals("trending", ignoreCase = true) ||
                cleanQ.equals("for-you", ignoreCase = true) ||
                cleanQ.equals("viral", ignoreCase = true) ||
                cleanQ.equals("surprise", ignoreCase = true) ||
                cleanQ.equals("top", ignoreCase = true)

        val searchUrl = if (isTrending) {
            when (page % 2) {
                1 -> if (page == 1) "https://www.xnxx.com/best" else "https://www.xnxx.com/best/$page"
                else -> "https://www.xnxx.com/hits/$page"
            }
        } else {
            val encoded = URLEncoder.encode(cleanQ, "UTF-8")
            if (page > 1) "https://www.xnxx.com/search/$encoded/$page" else "https://www.xnxx.com/search/$encoded"
        }

        return try {
            val html = fetchText(searchUrl, mapOf(
                "Cookie" to "age_verified=1",
                "Referer" to "https://www.xnxx.com/"
            ))
            val doc = Jsoup.parse(html)
            val list = mutableListOf<MediaItem>()
            val seen = HashSet<String>()

            val elements = doc.select(".thumb-block, div[id*='video_'], .mozaique > div")
            for (el in elements) {
                val a = el.selectFirst("a[href*='/video-'], a[href*='/video']") ?: continue
                val href = a.attr("href")
                if (href.isEmpty() || !href.contains("/video")) continue

                val fullUrl = if (href.startsWith("http")) href else "https://www.xnxx.com$href"
                if (!seen.add(fullUrl)) continue

                // Proper XNXX title extraction from .thumb-under
                val titleA = el.selectFirst(".thumb-under p a, .thumb-under a, p a[title], a.video-title")
                val title = titleA?.attr("title")?.trim()?.ifEmpty { titleA.text().trim() }
                    ?: a.attr("title")?.trim()?.ifEmpty { a.text().trim() }
                    ?: el.selectFirst("img")?.attr("alt")?.trim()
                    ?: "XNXX Video"

                val img = el.selectFirst("img")
                var thumb = img?.attr("data-src")?.ifEmpty { img.attr("data-mzl") }
                    ?: img?.attr("data-sfwthumb")
                    ?: img?.attr("src")
                    ?: "https://images.unsplash.com/photo-1518173946687-a4c8a383392e?w=400"
                if (thumb.startsWith("//")) thumb = "https:$thumb"

                val metaText = el.selectFirst(".metadata, p.metadata")?.text()?.trim() ?: ""
                val durMatch = Regex("""(\d+\s*min|\d+:\d+(?::\d+)?)""", RegexOption.IGNORE_CASE).find(metaText)
                val dur = durMatch?.value?.replace(Regex("""\s+"""), "") ?: "HD"

                val viewsMatch = Regex("""(\d+(?:\.\d+)?[KMkmbB]?)\s*(?:views|\s*<)""").find(metaText)
                val views = viewsMatch?.groupValues?.get(1) ?: "30K+"

                if (title.length > 2) {
                    list.add(
                        MediaItem(
                            id = fullUrl.hashCode().toString(),
                            title = title,
                            link = fullUrl,
                            poster = thumb,
                            thumbnail = thumb,
                            duration = dur,
                            rating = "1080p FHD",
                            year = "2026",
                            views = views,
                            source = "XNXX"
                        )
                    )
                }
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun resolve(targetUrl: String): StreamResult? {
        if (!targetUrl.contains("xnxx.com")) return null

        val cleanUrl = targetUrl.trim().replace(Regex("""/+$"""), "").split("?")[0]
        val vidMatch = Regex("""video-([0-9a-zA-Z_]+)""").find(cleanUrl) ?: Regex("""video(\d+)""").find(cleanUrl)
        val vidId = vidMatch?.groupValues?.get(1) ?: ""
        val embedUrl = if (vidId.isNotEmpty()) "https://www.xnxx.com/embedframe/$vidId" else targetUrl

        val qualities = mutableListOf<StreamFormat>()
        var directStream = ""

        try {
            val html = fetchText(targetUrl, mapOf(
                "Referer" to "https://www.xnxx.com/",
                "Cookie" to "age_verified=1"
            ))
            val mp4High = Regex("""html5player\.setVideoUrlHigh\('([^']+)'\)""").find(html)?.groupValues?.get(1)
            val mp4Low = Regex("""html5player\.setVideoUrlLow\('([^']+)'\)""").find(html)?.groupValues?.get(1)
            val hls = Regex("""html5player\.setVideoHLS\('([^']+)'\)""").find(html)?.groupValues?.get(1)

            if (!mp4High.isNullOrEmpty()) {
                val proxiedHigh = LocalMediaProxy.getProxiedUrl(mp4High)
                qualities.add(StreamFormat(formatId = "xn-high", quality = "1080p", label = "1080p / 720p HD", url = proxiedHigh, streamUrl = proxiedHigh, downloadUrl = mp4High, isEmbed = false))
                directStream = proxiedHigh
            }
            if (!mp4Low.isNullOrEmpty()) {
                val proxiedLow = LocalMediaProxy.getProxiedUrl(mp4Low)
                qualities.add(StreamFormat(formatId = "xn-low", quality = "480p", label = "480p / 360p SD", url = proxiedLow, streamUrl = proxiedLow, downloadUrl = mp4Low, isEmbed = false))
                if (directStream.isEmpty()) directStream = proxiedLow
            }
            if (!hls.isNullOrEmpty()) {
                val proxiedHls = LocalMediaProxy.getProxiedUrl(hls)
                qualities.add(StreamFormat(formatId = "xn-hls", quality = "Auto", label = "Auto (Adaptive HLS)", url = proxiedHls, streamUrl = proxiedHls, downloadUrl = hls, isEmbed = false))
            }
        } catch (e: Exception) {}

        if (qualities.isNotEmpty()) {
            return StreamResult(
                title = "XNXX HD Stream",
                streamUrl = directStream.ifEmpty { embedUrl },
                downloadUrl = directStream.ifEmpty { embedUrl },
                embedUrl = embedUrl,
                type = "video",
                qualities = qualities
            )
        }

        return StreamResult(
            title = "XNXX HD Stream",
            streamUrl = embedUrl,
            downloadUrl = targetUrl,
            embedUrl = embedUrl,
            type = "embed",
            qualities = listOf(
                StreamFormat(formatId = "xn-1080", quality = "1080p", label = "1080p HD", url = embedUrl, streamUrl = embedUrl, downloadUrl = targetUrl, isEmbed = true)
            )
        )
    }
}
