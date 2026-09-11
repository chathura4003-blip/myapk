package com.clouddrive.leech.extractor.providers.movies

import com.clouddrive.leech.extractor.models.MediaItem
import okhttp3.OkHttpClient
import org.jsoup.Jsoup
import java.net.URLEncoder

/**
 * Clean & High-Precision Scraper for PirateLK Cinema portal.
 * Direct parsing of WordPress / Sahifa post streams with 100% accurate HD posters.
 */
class PirateLKScraper(
    client: OkHttpClient = defaultClient
) : BaseMovieScraper(client) {

    fun search(query: String): List<MediaItem> {
        return try {
            val cleanQ = query.trim()
            val isGeneric = cleanQ.isEmpty() || cleanQ == "2026" || cleanQ == "trending" || cleanQ == "popular" || cleanQ == "all" || cleanQ == "latest"
            val urls = if (isGeneric) {
                listOf(
                    "https://piratelk.com/category/%e0%b7%83%e0%b7%92%e0%b6%82%e0%b7%84%e0%b6%bd-%e0%b6%8b%e0%b6%b4%e0%b7%83%e0%b7%92%e0%b6%bb%e0%b7%90%e0%b7%83%e0%b7%92/%e0%b6%a0%e0%b7%92%e0%b6%ad%e0%b7%8a%e0%b6%bb%e0%b6%b4%e0%b6%a7%e0%b7%92/",
                    "https://piratelk.com/category/%e0%b7%83%e0%b7%92%e0%b6%82%e0%b7%84%e0%b6%bd-%e0%b6%8b%e0%b6%b4%e0%b7%83%e0%b7%92%e0%b6%bb%e0%b7%90%e0%b7%83%e0%b7%92/%e0%b6%a0%e0%b7%92%e0%b6%ad%e0%b7%8a%e0%b6%bb%e0%b6%b4%e0%b6%a7%e0%b7%92/page/2/",
                    "https://piratelk.com/category/%e0%b7%83%e0%b7%92%e0%b6%82%e0%b7%84%e0%b6%bd-%e0%b6%8b%e0%b6%b4%e0%b7%83%e0%b7%92%e0%b6%bb%e0%b7%90%e0%b7%83%e0%b7%92/%e0%b6%a0%e0%b7%92%e0%b6%ad%e0%b7%8a%e0%b6%bb%e0%b6%b4%e0%b6%a7%e0%b7%92/page/3/",
                    "https://piratelk.com/category/trending-movies/"
                )
            } else {
                val encoded = URLEncoder.encode(cleanQ, "UTF-8")
                listOf("https://piratelk.com/?s=$encoded")
            }

            val list = mutableListOf<MediaItem>()
            val seen = HashSet<String>()

            for (targetUrl in urls) {
                val html = fetchHtml(targetUrl)
                if (html.isEmpty()) continue

                val doc = Jsoup.parse(html)
                val items = doc.select("article.item-list, .post-item, .post-listing article, article")

                for (el in items) {
                    // Strictly exclude sidebar and footer widgets
                    if (el.parents().any { it.hasClass("widget") || it.hasClass("textwidget") || it.id() == "sidebar" || it.tagName() == "footer" }) continue

                    val a = el.selectFirst("h2.post-box-title a, h2 a, .entry-title a, a[rel='bookmark'], a") ?: continue
                    val href = a.attr("abs:href").ifEmpty { a.attr("href") }
                    val rawTitle = el.selectFirst("h2.post-box-title, h2, h3, .entry-title")?.text()?.trim()
                        ?: a.text().trim().ifEmpty { a.attr("title").trim() }

                    if (href.isEmpty() || !href.contains("piratelk.com") || rawTitle.isEmpty() || rawTitle.contains("Page", ignoreCase = true) || rawTitle.contains("Read More", ignoreCase = true) || href.contains("/category/") || href.contains("/author/") || href.contains("/tag/") || !seen.add(href)) continue

                    val imgEl = el.selectFirst(".post-thumbnail img, img.wp-post-image, .post-thumb img, a img, img")
                    var poster = imgEl?.attr("data-lazy-src")?.takeIf { it.isNotEmpty() }
                        ?: imgEl?.attr("data-src")?.takeIf { it.isNotEmpty() }
                        ?: imgEl?.attr("data-original")?.takeIf { it.isNotEmpty() }
                        ?: imgEl?.attr("src") ?: ""

                    if (poster.startsWith("//")) poster = "https:$poster"
                    if (poster.startsWith("http://")) poster = poster.replace("http://", "https://")
                    if (poster.startsWith("data:image") || poster.contains("PinExt") || poster.contains("transparent")) poster = ""
                    
                    // Remove WordPress thumbnail dimensions to get the original HD poster
                    poster = poster.replace(Regex("""-\d+x\d+(\.(webp|jpg|png|jpeg))$""", RegexOption.IGNORE_CASE), "$1")

                    val ratingScore = extractRating(el.text(), rawTitle)
                    val yearMatch = Regex("""\b(19\d\d|20\d\d)\b""").find(rawTitle)?.value ?: "2026"

                    val cleanTitle = rawTitle.replace(Regex("(?i)Sinhala\\s*Subtitles?.*"), "")
                        .replace(Regex("(?i)සිංහල\\s*උපසිර[ැසි]+.*"), "")
                        .replace(Regex("""\|.*"""), "")
                        .replace(Regex("""\[.*\]"""), "")
                        .replace(Regex("""–.*"""), "")
                        .trim()

                    list.add(
                        MediaItem(
                            id = href,
                            title = cleanTitle.ifEmpty { rawTitle },
                            link = href,
                            poster = poster,
                            thumbnail = poster,
                            rating = ratingScore,
                            year = yearMatch,
                            source = "PirateLK (Sinhala)",
                            isMovie = true
                        )
                    )
                }
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }
}
