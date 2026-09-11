package com.clouddrive.leech.extractor.providers.adult

import com.clouddrive.leech.extractor.models.MediaItem
import com.clouddrive.leech.extractor.models.StreamFormat
import com.clouddrive.leech.extractor.models.StreamResult
import com.google.gson.JsonParser
import okhttp3.OkHttpClient
import java.net.URLEncoder

class EpornerScraper(client: OkHttpClient = defaultClient) : BaseAdultScraper(client) {

    fun search(query: String, page: Int = 1): List<MediaItem> {
        val cleanQ = query.trim()
        val isTrending = cleanQ.isEmpty() ||
                cleanQ.equals("popular", ignoreCase = true) ||
                cleanQ.equals("trending", ignoreCase = true) ||
                cleanQ.equals("for-you", ignoreCase = true) ||
                cleanQ.equals("viral", ignoreCase = true) ||
                cleanQ.equals("surprise", ignoreCase = true) ||
                cleanQ.equals("top", ignoreCase = true)

        val order = when (page % 4) {
            1 -> "top-weekly"
            2 -> "top-monthly"
            3 -> "most-popular"
            else -> "top-all"
        }

        val apiUrl = if (isTrending) {
            "https://www.eporner.com/api/v2/video/search/?query=&page=$page&per_page=30&thumbsize=big&order=$order&format=json"
        } else {
            val encoded = URLEncoder.encode(cleanQ, "UTF-8")
            "https://www.eporner.com/api/v2/video/search/?query=$encoded&page=$page&per_page=30&thumbsize=big&order=top-weekly&format=json"
        }

        return try {
            val jsonStr = fetchText(apiUrl, mapOf("Accept" to "application/json"))
            val jsonObj = JsonParser.parseString(jsonStr).asJsonObject
            val videosArr = jsonObj.getAsJsonArray("videos") ?: return emptyList()

            val list = mutableListOf<MediaItem>()
            for (elem in videosArr) {
                val v = elem.asJsonObject
                val id = v.get("id")?.asString ?: ""
                val title = v.get("title")?.asString ?: "Video"
                val videoUrl = v.get("url")?.asString ?: ""
                val defaultThumb = v.getAsJsonObject("default_thumb")?.get("src")?.asString
                val thumb = defaultThumb ?: "https://images.unsplash.com/photo-1518173946687-a4c8a383392e?w=400"
                val lengthSec = v.get("length_sec")?.asInt ?: 600
                val durationStr = String.format("%d:%02d", lengthSec / 60, lengthSec % 60)
                val viewsNum = v.get("views")?.asInt ?: 1000
                val viewsStr = if (viewsNum >= 1_000_000) "${viewsNum / 1_000_000}M+" else "${viewsNum / 1_000}K+"

                list.add(
                    MediaItem(
                        id = id,
                        title = title,
                        link = videoUrl,
                        poster = thumb,
                        thumbnail = thumb,
                        duration = durationStr,
                        rating = "4K Ultra HD",
                        year = "2026",
                        views = viewsStr,
                        source = "Eporner 4K"
                    )
                )
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun resolve(targetUrl: String): StreamResult? {
        if (!targetUrl.contains("eporner.com")) return null

        val vidMatch = Regex("""\/video-([a-zA-Z0-9]+)""").find(targetUrl)
            ?: Regex("""eporner\.com\/([a-zA-Z0-9]+)""").find(targetUrl)
            ?: return null

        val vidId = vidMatch.groupValues[1]
        val embedUrl = "https://www.eporner.com/embed/$vidId/"
        val qList = mutableListOf<StreamFormat>()
        var directMp4 = ""

        try {
            val embedHtml = fetchText(embedUrl, mapOf(
                "Referer" to "https://www.eporner.com/",
                "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36"
            ))

            val hashMatch = Regex("""hash\s*=\s*['"]([a-f0-9]{32})['"]""", RegexOption.IGNORE_CASE).find(embedHtml)
            val rawHash = hashMatch?.groupValues?.get(1) ?: ""

            if (rawHash.length == 32) {
                val base36Hash = calculateEpornerHash(rawHash)
                val xhrUrl = "https://www.eporner.com/xhr/video/$vidId?hash=$base36Hash&device=generic&domain=www.eporner.com&fallback=false"
                val xhrJsonStr = fetchText(xhrUrl, mapOf(
                    "Referer" to embedUrl,
                    "X-Requested-With" to "XMLHttpRequest",
                    "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
                ))

                val jsonObj = JsonParser.parseString(xhrJsonStr).asJsonObject
                if (jsonObj.has("sources")) {
                    val sources = jsonObj.getAsJsonObject("sources")
                    if (sources.has("mp4")) {
                        val mp4Obj = sources.getAsJsonObject("mp4")
                        for (key in mp4Obj.keySet()) {
                            val qData = mp4Obj.getAsJsonObject(key)
                            val srcUrl = qData.get("src")?.asString ?: ""
                            if (srcUrl.isNotEmpty() && !srcUrl.contains("na.mp4")) {
                                val label = qData.get("labelShort")?.asString ?: key
                                val proxied = com.clouddrive.leech.proxy.LocalMediaProxy.getProxiedUrl(srcUrl)
                                qList.add(StreamFormat(
                                    formatId = "ep-$label",
                                    quality = label,
                                    label = "$label Ultra HD Direct",
                                    url = proxied,
                                    streamUrl = proxied,
                                    downloadUrl = srcUrl,
                                    isEmbed = false
                                ))

                                if (directMp4.isEmpty() || label.contains("1080") || label.contains("720") || label.contains("480")) {
                                    directMp4 = proxied
                                }
                            }
                        }
                    }
                }
            }
        } catch (_: Exception) {}

        if (qList.isNotEmpty()) {
            val bestDownload = qList.firstOrNull { !it.isEmbed }?.downloadUrl ?: directMp4
            qList.add(StreamFormat(formatId = "ep-embed", quality = "Embed", label = "1080p Web Player", url = embedUrl, streamUrl = embedUrl, downloadUrl = targetUrl, isEmbed = true))
            return StreamResult(
                title = "Eporner 4K Ultra HD Direct",
                streamUrl = directMp4.ifEmpty { embedUrl },
                downloadUrl = bestDownload.ifEmpty { embedUrl },
                embedUrl = embedUrl,
                type = "video",
                qualities = qList
            )
        }

        return StreamResult(
            title = "Eporner 4K Video",
            streamUrl = embedUrl,
            downloadUrl = targetUrl,
            embedUrl = embedUrl,
            type = "embed",
            qualities = listOf(
                StreamFormat(formatId = "ep-1080", quality = "1080p", label = "4K / 1080p Stream", url = embedUrl, streamUrl = embedUrl, downloadUrl = targetUrl, isEmbed = true)
            )
        )
    }

    private fun calculateEpornerHash(hexHash: String): String {
        if (hexHash.length != 32) return ""
        return try {
            java.lang.Long.parseLong(hexHash.substring(0, 8), 16).toString(36) +
            java.lang.Long.parseLong(hexHash.substring(8, 16), 16).toString(36) +
            java.lang.Long.parseLong(hexHash.substring(16, 24), 16).toString(36) +
            java.lang.Long.parseLong(hexHash.substring(24, 32), 16).toString(36)
        } catch (_: Exception) {
            ""
        }
    }
}
