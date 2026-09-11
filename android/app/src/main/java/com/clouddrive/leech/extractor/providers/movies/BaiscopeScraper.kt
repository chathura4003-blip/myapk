package com.clouddrive.leech.extractor.providers.movies

import com.clouddrive.leech.extractor.models.MediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.jsoup.Jsoup
import java.net.URLEncoder

/**
 * Dedicated Scraper for Baiscopes.lk (Sinhala Subtitles & Cinema).
 * High-speed parallel fetching of Pages 1, 2, and 3.
 */
class BaiscopeScraper(
    client: OkHttpClient = defaultClient
) : BaseMovieScraper(client) {

    private val primaryUrl = "https://baiscopes.lk/"

    fun search(query: String): List<MediaItem> {
        val cleanQ = query.trim()
        val isGeneric = cleanQ.isEmpty() || cleanQ == "2026" || cleanQ == "trending" || cleanQ == "popular" || cleanQ == "all" || cleanQ == "latest"
        try {
            val pageUrls = if (!isGeneric) {
                val encoded = URLEncoder.encode(cleanQ, "UTF-8")
                listOf(
                    "$primaryUrl?s=$encoded",
                    "${primaryUrl}page/2/?s=$encoded",
                    "${primaryUrl}page/3/?s=$encoded"
                )
            } else {
                listOf(
                    primaryUrl,
                    "${primaryUrl}page/2/",
                    "${primaryUrl}page/3/"
                )
            }

            // ⚡ Fetch Pages 1, 2, and 3 concurrently in parallel
            val htmlPages = runBlocking(Dispatchers.IO) {
                pageUrls.map { targetUrl ->
                    async {
                        try {
                            fetchHtml(targetUrl)
                        } catch (_: Exception) {
                            ""
                        }
                    }
                }.awaitAll()
            }

            val list = mutableListOf<MediaItem>()
            val seen = HashSet<String>()

            for (html in htmlPages) {
                if (html.isEmpty()) continue
                val doc = Jsoup.parse(html)
                val items = doc.select(".result-item, .search-page article, article, .item, .movies-list .item, .items .item, [class*='result'], [class*='post-'], .item-box")

                for (el in items) {
                    val a = el.selectFirst("a[href*='/movies/'], a[href*='/tvshows/'], h2 a, h3 a, .entry-title a, .post-title a, a") ?: continue
                    val href = a.attr("abs:href").ifEmpty { a.attr("href") }
                    if (href.isEmpty() || !href.contains("baiscope") || href.contains("/category/") || href.contains("/author/") || href.contains("/genre/") || href.contains("/tag/") || !seen.add(href)) continue

                    val rawTitle = el.selectFirst(".title, h2, h3, .entry-title, .data h3")?.text()?.trim()
                        ?: a.attr("title").trim().ifEmpty { a.text().trim() }
                    val lowerTitle = rawTitle.lowercase()
                    if (rawTitle.length < 3 || lowerTitle.contains("baiscope") || lowerTitle.contains("login") || lowerTitle.contains("account") || lowerTitle.contains("cricket") || lowerTitle.contains("sign in")) continue

                    val cleanTitle = rawTitle
                        .replace(Regex("(?i)Sinhala Subtitles.*"), "")
                        .replace(Regex("""\|.*"""), "")
                        .replace(Regex("""\[.*?\]"""), "")
                        .trim()

                    // In-memory query filter if specific search passed
                    if (!isGeneric) {
                        val qWords = cleanQ.lowercase().split(" ").filter { it.length > 1 }
                        val matches = qWords.any { cleanTitle.lowercase().contains(it) }
                        if (!matches) continue
                    }

                    val imgEl = el.selectFirst("img, .poster img, .featured-image img, picture img")
                    var poster = imgEl?.attr("data-src")?.takeIf { it.isNotEmpty() }
                        ?: imgEl?.attr("data-lazy-src")?.takeIf { it.isNotEmpty() }
                        ?: imgEl?.attr("data-original")?.takeIf { it.isNotEmpty() }
                        ?: imgEl?.attr("src") ?: ""
                    if (poster.isEmpty()) {
                        val bgStyle = el.selectFirst("[style*='background']")?.attr("style") ?: ""
                        val bgMatch = Regex("""url\(['"]?(https?:\/\/[^'"\)]+)['"]?\)""").find(bgStyle)
                        if (bgMatch != null) poster = bgMatch.groupValues[1]
                    }
                    if (poster.startsWith("//")) poster = "https:$poster"
                    if (poster.contains("image.tmdb.org/t/p/")) {
                        poster = poster.replace(Regex("""/t/p/w\d+/"""), "/t/p/w500/")
                    }

                    val ratingScore = extractRating(el.text(), rawTitle)

                    list.add(
                        MediaItem(
                            id = href,
                            title = cleanTitle,
                            link = href,
                            poster = poster,
                            thumbnail = poster,
                            rating = ratingScore,
                            year = "2026",
                            source = "Baiscope",
                            isMovie = true
                        )
                    )
                }
            }
            return list
        } catch (_: Exception) {}

        return emptyList()
    }
}
