package com.clouddrive.leech.extractor.providers.movies

import com.clouddrive.leech.extractor.models.MediaItem
import okhttp3.OkHttpClient
import org.jsoup.Jsoup
import java.net.URLEncoder

/**
 * Dedicated Scraper for Sinhalasub.lk and its mirrors.
 */
class SinhalasubScraper(
    client: OkHttpClient = defaultClient
) : BaseMovieScraper(client) {

    private val mirrors = listOf(
        "https://sinhalasub.lk/",
        "https://sinhalasub.net/",
        "https://sinhalasub.info/"
    )

    fun search(query: String): List<MediaItem> {
        val cleanQ = query.trim()
        val isGeneric = cleanQ.isEmpty() || cleanQ == "2026" || cleanQ == "trending" || cleanQ == "popular" || cleanQ == "all" || cleanQ == "latest"
        
        for (baseUrl in mirrors) {
            try {
                val list = mutableListOf<MediaItem>()
                val seen = HashSet<String>()
                
                val pageUrls = if (isGeneric) {
                    listOf("${baseUrl}movies/")
                } else {
                    val encoded = URLEncoder.encode(cleanQ, "UTF-8")
                    listOf("${baseUrl}?s=$encoded")
                }

                for (targetUrl in pageUrls) {
                    try {
                        val html = fetchHtml(targetUrl)
                        if (html.isEmpty()) continue

                        val doc = Jsoup.parse(html)
                        val items = doc.select(".item-box, .display-item, .result-item, article, a[href*='/movies/'], .item, .movies-list .item")

                        for (el in items) {
                            val a = if (el.tagName().equals("a", ignoreCase = true)) el else el.selectFirst("a[href*='/movies/'], a") ?: continue
                            val href = a.attr("abs:href").ifEmpty { a.attr("href") }
                            if (href.isEmpty() || !href.contains("/movies/") || !seen.add(href)) continue

                            val rawTitle = a.attr("title").ifEmpty {
                                el.selectFirst(".item-desc-title h3, h3, h2, .title, .entry-title")?.text()?.trim() ?: ""
                            }
                            if (rawTitle.isEmpty() || rawTitle.contains("Homepage", ignoreCase = true) || rawTitle.equals("Movie", ignoreCase = true)) continue

                            val imgEl = el.selectFirst("img, .post-thumb img, .entry-thumb img, picture img")
                            var poster = imgEl?.attr("data-src")?.takeIf { it.isNotEmpty() }
                                ?: imgEl?.attr("data-lazy-src")?.takeIf { it.isNotEmpty() }
                                ?: imgEl?.attr("data-orig-file")?.takeIf { it.isNotEmpty() }
                                ?: imgEl?.attr("data-original")?.takeIf { it.isNotEmpty() }
                                ?: imgEl?.attr("src") ?: ""
                            if (poster.startsWith("//")) poster = "https:$poster"
                            if (poster.startsWith("data:image")) poster = ""

                            val ratingScore = extractRating(el.text(), rawTitle)

                            val cleanTitle = rawTitle.replace(Regex("(?i)Sinhala Subtitles.*"), "").replace(Regex("""\|.*"""), "").replace(Regex("""\[.*\]"""), "").trim()
                            list.add(
                                MediaItem(
                                    id = href,
                                    title = cleanTitle,
                                    link = href,
                                    poster = poster,
                                    thumbnail = poster,
                                    rating = ratingScore,
                                    year = "2026",
                                    source = "Sinhalasub",
                                    isMovie = true
                                )
                            )
                        }
                    } catch (_: Exception) {}
                }
                if (list.isNotEmpty()) return list
            } catch (_: Exception) {}
        }
        return emptyList()
    }
}
