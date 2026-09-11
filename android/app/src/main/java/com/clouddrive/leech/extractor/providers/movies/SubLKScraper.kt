package com.clouddrive.leech.extractor.providers.movies

import com.clouddrive.leech.extractor.models.MediaItem
import okhttp3.OkHttpClient
import org.jsoup.Jsoup
import java.net.URLEncoder

/**
 * Dedicated Scraper for Sub.lk (Sinhala Subtitles & Cinema).
 */
class SubLKScraper(
    client: OkHttpClient = defaultClient
) : BaseMovieScraper(client) {

    fun search(query: String): List<MediaItem> {
        val cleanQ = query.trim()
        val isGeneric = cleanQ.isEmpty() || cleanQ == "2026" || cleanQ == "trending" || cleanQ == "popular" || cleanQ == "all" || cleanQ == "latest"
        val urls = if (isGeneric) {
            listOf(
                "https://sub.lk/category/%e0%b6%94%e0%b6%9a%e0%b7%8a%e0%b6%9a%e0%b7%9c%e0%b6%b8-%e0%b6%91%e0%b6%9a%e0%b6%a7/films/",
                "https://sub.lk/category/%e0%b6%94%e0%b6%9a%e0%b7%8a%e0%b6%9a%e0%b7%9c%e0%b6%b8-%e0%b6%91%e0%b6%9a%e0%b6%a7/films/page/2/",
                "https://sub.lk/category/%e0%b6%94%e0%b6%9a%e0%b7%8a%e0%b6%9a%e0%b7%9c%e0%b6%b8-%e0%b6%91%e0%b6%9a%e0%b6%a7/films/page/3/",
                "https://sub.lk/category/%e0%b6%94%e0%b6%9a%e0%b7%8a%e0%b6%9a%e0%b7%9c%e0%b6%b8-%e0%b6%91%e0%b6%9a%e0%b6%a7/films/page/4/",
              
            )
        } else {
            val encoded = URLEncoder.encode(cleanQ, "UTF-8")
            listOf(
                "https://sub.lk/?s=$encoded"
            )
        }

        val list = mutableListOf<MediaItem>()
        val seen = HashSet<String>()

        for (targetUrl in urls) {
            try {
                val html = fetchHtml(targetUrl)
                if (html.isEmpty()) continue

                val doc = Jsoup.parse(html)

                // If not found, skip
                if (!isGeneric && (doc.select(".not-found, .no-results").isNotEmpty() || html.contains("nothing matched your search terms", ignoreCase = true))) {
                    break
                }

                val items = doc.select("article.item-list, .post-listing article, .archive-box article, article, div.entry, li")

                for (el in items) {
                    val a = el.selectFirst("h2.post-box-title a, h2 a, h3 a, a[href*='-sinhala-subtitles/'], a[href*='sub.lk/']") ?: continue
                    val href = a.attr("abs:href").ifEmpty { a.attr("href") }
                    if (href.isEmpty() || href.contains("/category/") || href.contains("/author/") || href.contains("/tag/") || href == "https://sub.lk/" || !seen.add(href)) continue
                    if (!href.contains("-sinhala-subtitles") && !href.contains("/tv_series/") && !href.contains("/films/")) continue

                    var rawTitle = a.text().trim().ifEmpty { el.selectFirst("h2, h3, .entry-title")?.text()?.trim() ?: "" }
                    rawTitle = rawTitle.replace("Login", "", ignoreCase = true).trim()

                    val lowerTitle = rawTitle.lowercase()
                    if (lowerTitle.contains("not found") || lowerTitle.contains("nothing found") || lowerTitle.contains("404")) continue

                    if (rawTitle.length < 3 || lowerTitle.contains("sub.lk")) {
                        val slug = href.trimEnd('/').split('/').lastOrNull() ?: ""
                        rawTitle = slug.replace(Regex("(?i)-sinhala-subtitles?"), "").replace("-", " ")
                    }
                    if (rawTitle.length < 3) continue

                    val imgEl = el.selectFirst("img, .post-thumb img, .entry-thumb img, picture img")
                    var poster = imgEl?.attr("data-lazy-src")?.takeIf { it.isNotEmpty() }
                        ?: imgEl?.attr("data-src")?.takeIf { it.isNotEmpty() }
                        ?: imgEl?.attr("data-original")?.takeIf { it.isNotEmpty() }
                        ?: imgEl?.attr("src") ?: ""
                    if (poster.startsWith("//")) poster = "https:$poster"
                    poster = poster.replace(Regex("""-\d+x\d+(\.(webp|jpg|png|jpeg))$""", RegexOption.IGNORE_CASE), "$1")

                    val cleanTitle = rawTitle.replace(Regex("(?i)Sinhala Subtitles.*"), "").replace(Regex("""\|.*"""), "").trim()
                    list.add(
                        MediaItem(
                            id = href,
                            title = cleanTitle,
                            link = href,
                            poster = poster,
                            rating = extractRating(el.text(), rawTitle),
                            year = "2026",
                            source = "Sub.lk",
                            isMovie = true
                        )
                    )
                }
            } catch (e: Exception) {}
        }
        return list
    }
}
