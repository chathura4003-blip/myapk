package com.clouddrive.leech.extractor.providers.adult

import com.clouddrive.leech.extractor.models.MediaItem
import com.clouddrive.leech.extractor.models.StreamFormat
import com.clouddrive.leech.extractor.models.StreamResult
import com.clouddrive.leech.proxy.LocalMediaProxy
import okhttp3.OkHttpClient
import org.json.JSONObject
import org.jsoup.Jsoup
import java.net.URLDecoder
import java.net.URLEncoder

class XHamsterScraper(client: OkHttpClient = defaultClient) : BaseAdultScraper(client) {

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
            when (page % 3) {
                1 -> if (page == 1) "https://xhamster.com/best/weekly" else "https://xhamster.com/best/weekly/$page"
                2 -> "https://xhamster.com/best/monthly/$page"
                else -> "https://xhamster.com/rankings/videos/$page"
            }
        } else {
            val encoded = URLEncoder.encode(cleanQ, "UTF-8")
            "https://xhamster.com/search/$encoded?page=$page"
        }

        return try {
            val html = fetchText(searchUrl, mapOf(
                "Referer" to "https://xhamster.com/",
                "Cookie" to "age_verified=1; has_access=1"
            ))

            val list = mutableListOf<MediaItem>()
            val seen = HashSet<String>()

            // 1. High-Precision window.initials parser (46+ items)
            val initialsMatch = Regex("""window\.initials\s*=\s*(\{.+?\});""", RegexOption.DOT_MATCHES_ALL).find(html)
            if (initialsMatch != null) {
                try {
                    val root = JSONObject(initialsMatch.groupValues[1])
                    val searchResult = root.optJSONObject("searchResult") ?: root.optJSONObject("videoListModel")
                    val videoThumbProps = searchResult?.optJSONArray("videoThumbProps") ?: searchResult?.optJSONArray("videos")
                    if (videoThumbProps != null) {
                        for (i in 0 until videoThumbProps.length()) {
                            val v = videoThumbProps.optJSONObject(i) ?: continue
                            val pageUrl = v.optString("pageURL").ifEmpty {
                                val vId = v.optString("id")
                                if (vId.isNotEmpty()) "https://xhamster.com/videos/$vId" else ""
                            }
                            if (pageUrl.isEmpty() || !seen.add(pageUrl)) continue

                            val title = v.optString("title").trim().ifEmpty { "xHamster Video" }
                            val durSec = v.optInt("duration", 600)
                            val durStr = String.format("%d:%02d", durSec / 60, durSec % 60)
                            val thumb = v.optString("imageURL").ifEmpty {
                                v.optString("thumbURL").ifEmpty { v.optString("previewThumbURL") }
                            }.ifEmpty { "https://images.unsplash.com/photo-1518173946687-a4c8a383392e?w=400" }

                            val viewsNum = v.optLong("views", 25000)
                            val viewsStr = if (viewsNum >= 1_000_000) "${viewsNum / 1_000_000}M+" else "${viewsNum / 1000}K+"

                            list.add(
                                MediaItem(
                                    id = pageUrl.hashCode().toString(),
                                    title = title,
                                    link = pageUrl,
                                    poster = thumb,
                                    thumbnail = thumb,
                                    duration = durStr,
                                    rating = "1080p Full HD",
                                    year = "2026",
                                    views = viewsStr,
                                    source = "xHamster"
                                )
                            )
                        }
                    }
                } catch (_: Exception) {}
            }

            // 2. DOM fallback
            if (list.size < 5) {
                val doc = Jsoup.parse(html)
                val elements = doc.select("div[data-video-id], div.video-thumb, article.video-thumb")
                for (el in elements) {
                    val a = el.selectFirst("a[href*='/videos/']") ?: continue
                    val href = a.attr("href")
                    if (href.isEmpty() || !href.contains("/videos/")) continue

                    val fullUrl = if (href.startsWith("http")) href else "https://xhamster.com$href"
                    if (!seen.add(fullUrl)) continue

                    val title = el.selectFirst("[data-role='video-title']")?.text()?.trim()
                        ?: el.selectFirst("a.video-thumb-info__name")?.text()?.trim()
                        ?: a.attr("title")?.trim()
                        ?: el.selectFirst("img")?.attr("alt")?.trim()
                        ?: "xHamster Video"

                    val img = el.selectFirst("img")
                    val thumb = img?.attr("data-src")?.ifEmpty { img.attr("src") }
                        ?: "https://images.unsplash.com/photo-1518173946687-a4c8a383392e?w=400"

                    val rawDur = el.selectFirst("[data-role='video-duration'], .thumb-image-container__duration, .duration")?.text()?.trim() ?: ""
                    val durMatch = Regex("""\d{1,2}:\d{2}(?::\d{2})?""").find(rawDur)
                    val cleanDur = durMatch?.value ?: (if (rawDur.isNotEmpty()) rawDur.split("\\s+".toRegex())[0] else "HD")

                    if (title.length > 2) {
                        list.add(
                            MediaItem(
                                id = fullUrl.hashCode().toString(),
                                title = title,
                                link = fullUrl,
                                poster = thumb,
                                thumbnail = thumb,
                                duration = cleanDur,
                                rating = "1080p Full HD",
                                year = "2026",
                                views = "25K+",
                                source = "xHamster"
                            )
                        )
                    }
                }
            }

            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun resolve(targetUrl: String): StreamResult? {
        if (!targetUrl.contains("xhamster.com")) return null

        val cleanUrl = targetUrl.trim().replace(Regex("""/+$"""), "").split("?")[0]
        val idMatch = Regex("""(xh[a-zA-Z0-9]+)$""", RegexOption.IGNORE_CASE).find(cleanUrl)
            ?: Regex("""-(\d{5,15})$""").find(cleanUrl)
            ?: Regex("""-(\d{5,15})""").find(cleanUrl)
            ?: Regex("""/videos/([^/]+)-([a-zA-Z0-9]+)""").find(cleanUrl)
            ?: Regex("""/videos/([^/]+)""").find(cleanUrl)

        val videoId = if (idMatch != null) {
            if (idMatch.groupValues.size > 2) idMatch.groupValues[2] else idMatch.groupValues[1].replace("-", "")
        } else ""

        val embedUrl = if (videoId.isNotEmpty()) "https://xhamster.com/embed/$videoId" else targetUrl

        val qualities = mutableListOf<StreamFormat>()
        var masterHlsUrl = ""

        try {
            val html = fetchText(targetUrl, mapOf(
                "Referer" to "https://xhamster.com/",
                "Cookie" to "age_verified=1; has_access=1"
            ))

            // Method A: JSON initials parse
            val initialsMatch = Regex("""window\.initials\s*=\s*(\{.+?\});""", RegexOption.DOT_MATCHES_ALL).find(html)
            if (initialsMatch != null) {
                try {
                    val root = JSONObject(initialsMatch.groupValues[1])
                    val xplayer = root.optJSONObject("xplayerSettings")
                    val sources = xplayer?.optJSONObject("sources")
                    val hlsObj = sources?.optJSONObject("hls")
                    val hlsH264 = hlsObj?.optJSONObject("h264")?.optString("url")
                        ?: hlsObj?.optJSONObject("av1")?.optString("url")
                        ?: sources?.optJSONObject("standard")?.optJSONObject("h264")?.optString("url")
                    if (!hlsH264.isNullOrEmpty()) {
                        decryptHex(hlsH264)?.let { masterHlsUrl = it }
                    }
                } catch (_: Exception) {}
            }

            // Method B: Resilient Regex Hex Hunter (if JSON parsing failed)
            if (masterHlsUrl.isEmpty()) {
                val hexMatches = Regex(""""url"\s*:\s*"([0-9a-fA-F]{24,})"""").findAll(html)
                for (m in hexMatches) {
                    val hex = m.groupValues[1]
                    val dec = decryptHex(hex)
                    if (dec != null && dec.contains(".m3u8")) {
                        masterHlsUrl = dec
                        break
                    }
                }
            }

            if (masterHlsUrl.isNotEmpty() && masterHlsUrl.contains(".m3u8")) {
                val multiMatch = Regex("""multi=([^/]+)""").find(masterHlsUrl)
                val availableResolutions = mutableListOf<String>()
                if (multiMatch != null) {
                    val parts = multiMatch.groupValues[1].split(",")
                    for (p in parts) {
                        val q = if (p.contains(":")) p.split(":")[1] else p
                        if (q.isNotEmpty()) availableResolutions.add(q.trim())
                    }
                }
                val resList = if (availableResolutions.isNotEmpty()) availableResolutions else listOf("720p", "480p", "240p")
                val rank = listOf("1080p", "720p", "480p", "240p", "144p")
                val highest = rank.firstOrNull { resList.contains(it) } ?: resList.last()
                val bestDownloadUrl = masterHlsUrl.replace("_TPL_", highest)
                val proxiedMaster = LocalMediaProxy.getProxiedUrl(masterHlsUrl)

                qualities.add(StreamFormat(formatId = "xh-auto", quality = "Auto", label = "✨ Auto (Adaptive)", url = proxiedMaster, streamUrl = proxiedMaster, downloadUrl = bestDownloadUrl, isEmbed = false))

                if (resList.contains("1080p")) {
                    val q1080 = masterHlsUrl.replace("_TPL_", "1080p")
                    val proxied1080 = LocalMediaProxy.getProxiedUrl(q1080)
                    qualities.add(StreamFormat(formatId = "xh-1080", quality = "1080p", label = "1080p Full HD", url = proxied1080, streamUrl = proxied1080, downloadUrl = q1080, isEmbed = false))
                }
                if (resList.contains("720p")) {
                    val q720 = masterHlsUrl.replace("_TPL_", "720p")
                    val proxied720 = LocalMediaProxy.getProxiedUrl(q720)
                    qualities.add(StreamFormat(formatId = "xh-720", quality = "720p", label = "720p HD", url = proxied720, streamUrl = proxied720, downloadUrl = q720, isEmbed = false))
                }
                if (resList.contains("480p")) {
                    val q480 = masterHlsUrl.replace("_TPL_", "480p")
                    val proxied480 = LocalMediaProxy.getProxiedUrl(q480)
                    qualities.add(StreamFormat(formatId = "xh-480", quality = "480p", label = "480p SD", url = proxied480, streamUrl = proxied480, downloadUrl = q480, isEmbed = false))
                }
                if (resList.contains("240p")) {
                    val q240 = masterHlsUrl.replace("_TPL_", "240p")
                    val proxied240 = LocalMediaProxy.getProxiedUrl(q240)
                    qualities.add(StreamFormat(formatId = "xh-240", quality = "240p", label = "240p Low", url = proxied240, streamUrl = proxied240, downloadUrl = q240, isEmbed = false))
                }
                if (resList.contains("144p")) {
                    val q144 = masterHlsUrl.replace("_TPL_", "144p")
                    val proxied144 = LocalMediaProxy.getProxiedUrl(q144)
                    qualities.add(StreamFormat(formatId = "xh-144", quality = "144p", label = "144p Mobile", url = proxied144, streamUrl = proxied144, downloadUrl = q144, isEmbed = false))
                }

                qualities.add(StreamFormat(formatId = "xh-embed", quality = "Embed", label = "1080p Web Player", url = embedUrl, streamUrl = embedUrl, downloadUrl = bestDownloadUrl, isEmbed = true))

                return StreamResult(
                    title = "xHamster $highest HD Stream",
                    streamUrl = proxiedMaster,
                    downloadUrl = bestDownloadUrl,
                    embedUrl = embedUrl,
                    type = "hls",
                    qualities = qualities
                )
            }

            // Fallback direct MP4
            val matches = Regex("""https?:\\?/\\?/video\d*\.xhcdn\.com\\?/[^"'<>\s]+\.mp4""", RegexOption.IGNORE_CASE).findAll(html)
            val mp4s = matches.map { it.value.replace("\\", "") }.filter { !it.contains(".m3u8") && !it.contains("_TPL_") }.distinct().toList()

            for (mp4 in mp4s) {
                val qLabel = when {
                    mp4.contains("720p") -> "720p HD"
                    mp4.contains("480p") -> "480p SD"
                    mp4.contains("240p") -> "240p Low"
                    else -> "Direct MP4"
                }
                val proxiedMp4 = LocalMediaProxy.getProxiedUrl(mp4)
                qualities.add(StreamFormat(formatId = "xh-$qLabel", quality = qLabel, label = qLabel, url = proxiedMp4, streamUrl = proxiedMp4, downloadUrl = mp4, isEmbed = false))
            }
        } catch (_: Exception) {}

        qualities.add(StreamFormat(formatId = "xh-embed", quality = "Embed", label = "1080p Web Player", url = embedUrl, streamUrl = embedUrl, downloadUrl = targetUrl, isEmbed = true))

        val finalPlayUrl = if (masterHlsUrl.isNotEmpty()) LocalMediaProxy.getProxiedUrl(masterHlsUrl) else embedUrl
        return StreamResult(
            title = "xHamster HD Stream",
            streamUrl = finalPlayUrl,
            downloadUrl = targetUrl,
            embedUrl = embedUrl,
            type = if (masterHlsUrl.isNotEmpty()) "hls" else "embed",
            qualities = qualities
        )
    }

    private fun imul(a: Int, b: Int): Int = a * b

    fun decryptHex(hexStr: String): String? {
        val len = hexStr.length
        if (len < 12 || (len and 1) != 0 || !hexStr.matches(Regex("^[0-9a-fA-F]+$"))) return null

        val bytes = IntArray(len shr 1)
        for (i in 0 until len step 2) {
            bytes[i shr 1] = hexStr.substring(i, i + 2).toInt(16)
        }

        if (bytes.size < 5) return null
        val algoId = bytes[0]
        if (algoId !in 1..7) return null

        var seed = bytes[1] or (bytes[2] shl 8) or (bytes[3] shl 16) or (bytes[4] shl 24)

        val prng: () -> Int = when (algoId) {
            1 -> {
                {
                    seed = imul(seed, 1664525) + 0x3c6ef35f
                    seed and 0xFF
                }
            }
            2 -> {
                {
                    seed = seed xor (seed shl 13)
                    seed = seed xor (seed ushr 17)
                    seed = seed xor (seed shl 5)
                    seed and 0xFF
                }
            }
            3 -> {
                {
                    seed = seed + 0x9e3779b9.toInt()
                    var e = seed
                    e = e xor (e ushr 16)
                    e = imul(e, 0x85ebca77.toInt())
                    e = e xor (e ushr 13)
                    e = imul(e, 0xc2b2ae3d.toInt())
                    (e xor (e ushr 16)) and 0xFF
                }
            }
            4 -> {
                {
                    seed = seed + 0x6d2b79f5.toInt()
                    var e = (seed shl 7) or (seed ushr 25)
                    e = e + 0x9e3779b9.toInt()
                    e = e xor (e ushr 11)
                    e = imul(e, 0x27d4eb2d)
                    e and 0xFF
                }
            }
            5 -> {
                {
                    seed = seed xor (seed shl 7)
                    seed = seed xor (seed ushr 9)
                    seed = seed xor (seed shl 8)
                    seed = seed + 0xa5a5a5a5.toInt()
                    seed and 0xFF
                }
            }
            6 -> {
                {
                    seed = imul(seed, 0x2c9277b5) + 0xac564b05.toInt()
                    val e = (seed xor (seed ushr 18)) ushr ((seed ushr 27) and 31)
                    e and 0xFF
                }
            }
            7 -> {
                {
                    seed = seed + 0x9e3779b9.toInt()
                    var e = seed xor (seed shl 5)
                    e = imul(e, 0x7feb352d)
                    e = e xor (e ushr 15)
                    e = imul(e, 0x846ca68b.toInt())
                    e and 0xFF
                }
            }
            else -> return null
        }

        val out = ByteArray(bytes.size - 5)
        for (idx in 5 until bytes.size) {
            out[idx - 5] = ((bytes[idx] xor prng()) and 0xFF).toByte()
        }

        return try {
            val sb = StringBuilder()
            for (b in out) {
                val hex = String.format("%02x", b.toInt() and 0xFF)
                sb.append("%").append(hex)
            }
            URLDecoder.decode(sb.toString(), "UTF-8")
        } catch (_: Exception) {
            String(out, Charsets.ISO_8859_1)
        }
    }
}
