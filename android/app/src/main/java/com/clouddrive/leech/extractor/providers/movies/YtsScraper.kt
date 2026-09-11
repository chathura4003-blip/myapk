package com.clouddrive.leech.extractor.providers.movies

import com.clouddrive.leech.extractor.models.MediaItem
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.atomic.AtomicReference

/**
 * Dedicated Scraper for YTS 4K/1080p Cinema REST API.
 * High-speed concurrent parallel mirror race across yts.lt, yts.ag, yts.am, yts.bz, yts.mx
 */
class YtsScraper(
    client: OkHttpClient = defaultClient
) : BaseMovieScraper(client) {

    private val ytsMirrors = listOf(
        "https://yts.gg/api/v2/list_movies.json",
        "https://yts.lt/api/v2/list_movies.json",
        "https://yts.ag/api/v2/list_movies.json",
        "https://yts.am/api/v2/list_movies.json",
        "https://yts.bz/api/v2/list_movies.json",
        "https://yts.mx/api/v2/list_movies.json"
    )

    private fun parseMoviesFromJson(jsonStr: String): List<MediaItem> {
        val list = mutableListOf<MediaItem>()
        try {
            val jsonObj = JsonParser.parseString(jsonStr).asJsonObject
            val dataObj = jsonObj.getAsJsonObject("data") ?: return emptyList()
            val moviesArr = dataObj.getAsJsonArray("movies") ?: return emptyList()

            for (elem in moviesArr) {
                val m = elem.asJsonObject
                val title = m.get("title_long")?.asString ?: m.get("title")?.asString ?: "Movie"
                val imdbCode = m.get("imdb_code")?.asString ?: ""
                val slug = m.get("slug")?.asString ?: ""
                val link = if (imdbCode.isNotEmpty()) {
                    "https://yts.mx/movies/$slug?imdb=$imdbCode"
                } else {
                    m.get("url")?.asString ?: "https://yts.mx/movies/$slug"
                }

                var poster = m.get("large_cover_image")?.asString
                    ?: m.get("medium_cover_image")?.asString
                    ?: ""
                if (poster.startsWith("http://")) poster = poster.replace("http://", "https://")

                val rating = m.get("rating")?.asString ?: "7.5"
                val year = m.get("year")?.asString ?: "2026"
                val torrentsArr = m.getAsJsonArray("torrents")
                val torrentsJsonStr = torrentsArr?.toString() ?: ""

                list.add(
                    MediaItem(
                        id = link,
                        title = title,
                        link = link,
                        poster = poster,
                        thumbnail = poster,
                        rating = "⭐ $rating",
                        year = year,
                        source = "YTS 4K",
                        isMovie = true,
                        torrentsJson = torrentsJsonStr,
                        imdb = imdbCode
                    )
                )
            }
        } catch (_: Exception) {}
        return list
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun search(query: String): List<MediaItem> {
        val cleanQ = query.trim()
        val isGeneric = cleanQ.isEmpty() || cleanQ == "2026" || cleanQ == "trending" || cleanQ == "popular" || cleanQ == "all" || cleanQ == "latest"
        val encoded = URLEncoder.encode(cleanQ, "UTF-8")

        val candidates = ytsMirrors.map { baseUrl ->
            if (!isGeneric) {
                "$baseUrl?query_term=$encoded&limit=50"
            } else {
                "$baseUrl?sort_by=date_added&order_by=desc&limit=50&page=1"
            }
        }

        val deferredResult = kotlinx.coroutines.CompletableDeferred<Pair<List<MediaItem>, String>>()

        // ⚡ Parallel Mirror Race: Query all mirrors concurrently and return the fastest (200ms)
        try {
            runBlocking {
                val jobs = candidates.map { url ->
                    async(Dispatchers.IO) {
                        if (deferredResult.isCompleted) return@async
                        try {
                            val req = Request.Builder()
                                .url(url)
                                .header("User-Agent", userAgent)
                                .header("Accept", "application/json")
                                .build()

                            okHttpClient.newCall(req).execute().use { res ->
                                if (!res.isSuccessful || deferredResult.isCompleted) return@use
                                val jsonStr = res.body?.string() ?: return@use
                                val list = parseMoviesFromJson(jsonStr)
                                if (list.isNotEmpty() && !deferredResult.isCompleted) {
                                    deferredResult.complete(Pair(list, url.substringBefore("?")))
                                }
                            }
                        } catch (_: Exception) {}
                    }
                }

                // Wait up to 3.5s or until first successful mirror wins
                withTimeoutOrNull(3500L) {
                    deferredResult.await()
                }
                jobs.forEach { it.cancel() }
            }

            // Deep Pages 2 and 3 Fetch for generic queries to maximize library breadth concurrently
            val (initial, baseMirror) = if (deferredResult.isCompleted) deferredResult.getCompleted() else Pair(null, null)
            if (isGeneric && !initial.isNullOrEmpty() && !baseMirror.isNullOrEmpty()) {
                try {
                    val extraPages: List<Int> = listOf(2, 3)
                    val extraItems: List<MediaItem> = runBlocking(Dispatchers.IO) {
                        extraPages.map { p: Int ->
                            async {
                                val pUrl = "$baseMirror?sort_by=date_added&order_by=desc&limit=50&page=$p"
                                try {
                                    val req = Request.Builder()
                                        .url(pUrl)
                                        .header("User-Agent", userAgent)
                                        .header("Accept", "application/json")
                                        .build()
                                    okHttpClient.newCall(req).execute().use { res ->
                                        if (res.isSuccessful) {
                                            val json = res.body?.string() ?: ""
                                            parseMoviesFromJson(json)
                                        } else emptyList()
                                    }
                                } catch (_: Exception) {
                                    emptyList()
                                }
                            }
                        }.awaitAll().flatten()
                    }
                    if (extraItems.isNotEmpty()) {
                        return initial + extraItems
                    }
                } catch (_: Exception) {}
            }
            if (!initial.isNullOrEmpty()) {
                return initial
            }
        } catch (_: Exception) {}

        return emptyList()
    }
}
