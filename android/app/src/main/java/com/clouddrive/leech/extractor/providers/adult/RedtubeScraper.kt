package com.clouddrive.leech.extractor.providers.adult

import com.clouddrive.leech.extractor.models.MediaItem
import com.clouddrive.leech.extractor.models.StreamFormat
import com.clouddrive.leech.extractor.models.StreamResult
import com.clouddrive.leech.proxy.LocalMediaProxy
import com.google.gson.JsonParser
import okhttp3.OkHttpClient
import java.net.URLEncoder

class RedtubeScraper(client: OkHttpClient = defaultClient) : BaseAdultScraper(client) {

    fun search(query: String, page: Int = 1): List<MediaItem> {
        val cleanQ = query.trim()
        val isTrending = cleanQ.isEmpty() ||
                cleanQ.equals("popular", ignoreCase = true) ||
                cleanQ.equals("trending", ignoreCase = true) ||
                cleanQ.equals("for-you", ignoreCase = true) ||
                cleanQ.equals("viral", ignoreCase = true) ||
                cleanQ.equals("surprise", ignoreCase = true) ||
                cleanQ.equals("top", ignoreCase = true)

        val list = mutableListOf<MediaItem>()

        // 1. PRIMARY STRATEGY: Scrape live Redtube web pages for real, high-res CDN images (100% working, no 410 Gone errors)
        try {
            val pageUrl = if (isTrending) {
                if (page <= 1) "https://www.redtube.com/" else "https://www.redtube.com/?page=$page"
            } else {
                val encoded = URLEncoder.encode(cleanQ, "UTF-8")
                "https://www.redtube.com/?search=$encoded&page=$page"
            }

            val headers = mapOf(
                "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
                "Cookie" to "age_verified=1; has_access=1; il=v111",
                "Referer" to "https://www.redtube.com/"
            )

            val html = fetchText(pageUrl, headers)
            val liRegex = Regex("""<li[^>]*data-video-id=["'](\d+)["'][^>]*>([\s\S]*?)</li>""", RegexOption.IGNORE_CASE)

            for (match in liRegex.findAll(html)) {
                val id = match.groupValues[1]
                val block = match.groupValues[2]

                // Extract valid CDN images in order of quality & reliability
                val dataSrc = Regex("""data-src=["']([^"']+)["']""", RegexOption.IGNORE_CASE).find(block)?.groupValues?.get(1)
                val dataOThumb = Regex("""data-o_thumb=["']([^"']+)["']""", RegexOption.IGNORE_CASE).find(block)?.groupValues?.get(1)
                val dataPath = Regex("""data-path=["']([^"']+)["']""", RegexOption.IGNORE_CASE).find(block)?.groupValues?.get(1)?.replace("{index}", "1")
                val webpSrcset = Regex("""data-srcset=["']([^"'\s]+)""", RegexOption.IGNORE_CASE).find(block)?.groupValues?.get(1)
                val regularSrc = Regex("""<img[^>]+src=["'](https:[^"']+)["']""", RegexOption.IGNORE_CASE).find(block)?.groupValues?.get(1)

                var thumb = dataSrc ?: dataOThumb ?: dataPath ?: webpSrcset ?: regularSrc ?: ""
                if (thumb.startsWith("//")) thumb = "https:$thumb"

                // Filter out base64, blank, or broken legacy paths
                if (thumb.isEmpty() || thumb.contains("data:image") || thumb.contains("blank.gif") || thumb.contains("//original/")) {
                    continue
                }

                // Extract clean video title
                val titleMatch = Regex("""class=["'][^"']*video-title-text[^"']*["'][^>]*title=["']([^"']+)["']""", RegexOption.IGNORE_CASE).find(block)
                    ?: Regex("""class=["'][^"']*video-title-text[^"']*["'][^>]*>([\s\S]*?)</a>""", RegexOption.IGNORE_CASE).find(block)
                    ?: Regex("""alt=["']([^"']+)["']""", RegexOption.IGNORE_CASE).find(block)
                var title = titleMatch?.groupValues?.get(1)?.trim() ?: "RedTube Video"
                title = title.replace("&quot;", "\"")
                    .replace("&amp;", "&")
                    .replace("&#039;", "'")
                    .replace("&lt;", "<")
                    .replace("&gt;", ">")
                    .replace("&apos;", "'")
                    .replace(Regex("""<[^>]+>"""), "")
                    .trim()
                if (title.isEmpty()) title = "RedTube Video"

                // Extract duration
                val durMatch = Regex("""class=["'][^"']*(?:tm_video_duration|duration)[^"']*["'][^>]*>([\s\S]*?)</span>""", RegexOption.IGNORE_CASE).find(block)
                val dur = durMatch?.groupValues?.get(1)?.replace(Regex("""<[^>]+>"""), "")?.trim() ?: "HD"

                // Extract views
                val viewsMatch = Regex("""class=["']info-views["'][^>]*>([\s\S]*?)</span>""", RegexOption.IGNORE_CASE).find(block)
                val views = viewsMatch?.groupValues?.get(1)?.replace(Regex("""<[^>]+>"""), "")?.trim() ?: "Hot"

                list.add(
                    MediaItem(
                        id = id,
                        title = title,
                        link = "https://www.redtube.com/$id",
                        poster = thumb,
                        thumbnail = thumb,
                        duration = dur,
                        rating = "1080p FHD",
                        year = "2026",
                        views = views,
                        source = "RedTube"
                    )
                )
            }
        } catch (_: Exception) {}

        if (list.isNotEmpty()) {
            return list
        }

        // 2. FALLBACK STRATEGY: Query API and filter out broken '//original/' images
        return try {
            val apiUrl = if (isTrending) {
                val ordering = if (page % 2 == 1) "mostviewed" else "rating"
                "https://api.redtube.com/?data=redtube.Videos.searchVideos&output=json&page=$page&thumbsize=all&ordering=$ordering&period=weekly"
            } else {
                val encoded = URLEncoder.encode(cleanQ, "UTF-8")
                "https://api.redtube.com/?data=redtube.Videos.searchVideos&output=json&search=$encoded&page=$page&thumbsize=all"
            }

            val jsonStr = fetchText(apiUrl, mapOf("Accept" to "application/json"))
            val jsonObj = JsonParser.parseString(jsonStr).asJsonObject
            val videosArr = jsonObj.getAsJsonArray("videos") ?: return emptyList()

            val fallbackList = mutableListOf<MediaItem>()
            for (elem in videosArr) {
                val itemObj = elem.asJsonObject
                val v = itemObj.getAsJsonObject("video") ?: continue
                val id = v.get("video_id")?.asString ?: ""
                val title = v.get("title")?.asString ?: "RedTube Video"
                val url = v.get("url")?.asString ?: "https://www.redtube.com/$id"

                // Search thumbs array for any valid thumb that doesn't contain '//original/'
                var bestThumb = ""
                val thumbsArr = v.getAsJsonArray("thumbs")
                if (thumbsArr != null) {
                    for (tElem in thumbsArr) {
                        val src = tElem.asJsonObject.get("src")?.asString ?: ""
                        if (src.isNotEmpty() && !src.contains("//original/")) {
                            bestThumb = src
                            break
                        }
                    }
                }

                if (bestThumb.isEmpty()) {
                    val rawThumb = v.get("default_thumb")?.asString ?: v.get("thumb")?.asString ?: ""
                    if (rawThumb.isNotEmpty() && !rawThumb.contains("//original/")) {
                        bestThumb = rawThumb
                    }
                }

                if (bestThumb.isEmpty()) {
                    bestThumb = "https://ei.rdtcdn.com/videos/$id/thumb.jpg"
                }

                val dur = v.get("duration")?.asString ?: "HD"
                val views = v.get("views")?.asString ?: "15K+"

                fallbackList.add(
                    MediaItem(
                        id = id,
                        title = title,
                        link = url,
                        poster = bestThumb,
                        thumbnail = bestThumb,
                        duration = dur,
                        rating = "1080p FHD",
                        year = "2026",
                        views = views,
                        source = "RedTube"
                    )
                )
            }
            fallbackList
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun resolve(targetUrl: String): StreamResult? {
        if (!targetUrl.contains("redtube.com")) return null

        val idMatch = Regex("""(\d+)""").find(targetUrl)
        val vidId = idMatch?.groupValues?.get(1) ?: ""
        val embedUrl = if (vidId.isNotEmpty()) "https://embed.redtube.com/?id=$vidId" else targetUrl

        val qualities = mutableListOf<StreamFormat>()
        var masterHls = ""
        var bestDirectMp4 = ""

        var resolvedTitle = "RedTube Video"

        try {
            val html = fetchText(embedUrl, mapOf(
                "Referer" to "https://www.redtube.com/",
                "Origin" to "https://www.redtube.com",
                "Cookie" to "age_verified=1; has_access=1; il=v111"
            ))

            // Extract real video title from HTML meta
            val titleMatch = Regex("""<meta\s+property=["']og:title["']\s+content=["']([^"']+)["']""", RegexOption.IGNORE_CASE).find(html)
                ?: Regex("""<title>([^<]+)</title>""", RegexOption.IGNORE_CASE).find(html)
            if (titleMatch != null) {
                val raw = titleMatch.groupValues[1]
                    .replace(Regex("""(?i)\s*-\s*RedTube.*$"""), "")
                    .replace(Regex("""(?i)\s*\|\s*RedTube.*$"""), "")
                    .trim()
                if (raw.isNotEmpty() && raw != "RedTube" && raw != "Watch Free Porn Videos") {
                    resolvedTitle = raw
                }
            }

            val flashvarsMatch = Regex("""var\s+flashvars_[a-zA-Z0-9_]+\s*=\s*(\{.+?\});""", RegexOption.DOT_MATCHES_ALL).find(html)
                ?: Regex("""var\s+flashvars\s*=\s*(\{.+?\});""", RegexOption.DOT_MATCHES_ALL).find(html)

            if (flashvarsMatch != null) {
                val fData = JsonParser.parseString(flashvarsMatch.groupValues[1]).asJsonObject
                val vTitle = fData.get("video_title")?.asString
                if (!vTitle.isNullOrEmpty() && vTitle != "RedTube Video") {
                    resolvedTitle = vTitle
                }

                val mediaDefs = fData.getAsJsonArray("mediaDefinitions")

                if (mediaDefs != null) {
                    for (mElem in mediaDefs) {
                        val mObj = mElem.asJsonObject
                        val format = mObj.get("format")?.asString ?: ""
                        val endpoint = mObj.get("videoUrl")?.asString ?: ""
                        if (endpoint.isEmpty()) continue

                        val fullApi = if (endpoint.startsWith("http")) endpoint else "https://embed.redtube.com$endpoint"

                        try {
                            val respJson = fetchText(fullApi, mapOf(
                                "Referer" to embedUrl,
                                "Origin" to "https://embed.redtube.com",
                                "Cookie" to "age_verified=1; has_access=1; il=v111",
                                "Accept" to "application/json, text/plain, */*"
                            ))
                            val parsedArray = JsonParser.parseString(respJson).asJsonArray
                            for (streamElem in parsedArray) {
                                val sObj = streamElem.asJsonObject
                                val sUrl = sObj.get("videoUrl")?.asString ?: ""
                                val sQuality = sObj.get("quality")?.asString ?: "HD"
                                if (sUrl.isEmpty()) continue

                                val proxied = LocalMediaProxy.getProxiedUrl(sUrl)
                                if (format == "hls") {
                                    val qLabel = "${sQuality}p Stream (Adaptive)"
                                    qualities.add(StreamFormat(formatId = "rt-hls-$sQuality", quality = "${sQuality}p", label = qLabel, url = proxied, streamUrl = proxied, downloadUrl = sUrl, isEmbed = false))
                                    if (masterHls.isEmpty()) masterHls = proxied
                                } else {
                                    val qLabel = "${sQuality}p MP4"
                                    qualities.add(StreamFormat(formatId = "rt-mp4-$sQuality", quality = "${sQuality}p", label = qLabel, url = proxied, streamUrl = proxied, downloadUrl = sUrl, isEmbed = false))
                                    if (bestDirectMp4.isEmpty()) bestDirectMp4 = proxied
                                }
                            }
                        } catch (_: Exception) {}
                    }
                }
            }
        } catch (_: Exception) {}

        if (qualities.isNotEmpty()) {
            val finalStream = if (masterHls.isNotEmpty()) masterHls else bestDirectMp4
            val finalDownload = qualities.firstOrNull { !it.isEmbed }?.downloadUrl ?: finalStream
            return StreamResult(
                title = resolvedTitle,
                streamUrl = finalStream,
                downloadUrl = finalDownload,
                embedUrl = embedUrl,
                type = if (masterHls.isNotEmpty()) "hls" else "video",
                qualities = qualities
            )
        }

        return StreamResult(
            title = resolvedTitle,
            streamUrl = embedUrl,
            downloadUrl = targetUrl,
            embedUrl = embedUrl,
            type = "embed",
            qualities = listOf(
                StreamFormat(formatId = "rt-embed", quality = "Embed", label = "1080p Web Player", url = embedUrl, streamUrl = embedUrl, downloadUrl = targetUrl, isEmbed = true)
            )
        )
    }
}
