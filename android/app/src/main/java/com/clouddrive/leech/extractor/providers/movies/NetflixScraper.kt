package com.clouddrive.leech.extractor.providers.movies

import android.util.Log
import com.clouddrive.leech.extractor.models.MediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder

/**
 * Cinejoy.to Powered Netflix Provider Scraper.
 * Retains the 'Netflix' brand & UI badges while sourcing directly from Cinejoy's
 * TMDB Watch Provider 8 (Netflix US) high-definition movie & series catalog.
 * Directs streams to Cinejoy VIP player (https://cinejoy.to/watch/movie/{id}).
 * High-speed parallel fetching of Pages 1, 2, and 3.
 */
class NetflixScraper(
    client: OkHttpClient = defaultClient
) : BaseMovieScraper(client) {

    companion object {
        private const val TAG = "NetflixScraper"
        private const val CINEJOY_TMDB_API_KEY = "8476a7ab80ad76f0936744df0430e67c"
        private const val TMDB_BASE_URL = "https://api.themoviedb.org/3"
        private const val TMDB_IMAGE_BASE = "https://image.tmdb.org/t/p/w500"

        @Volatile
        private var cachedCatalog: List<MediaItem> = emptyList()
        @Volatile
        private var cacheTime: Long = 0L
    }

    fun search(query: String): List<MediaItem> {
        val cleanQ = query.trim()
        val isGeneric = cleanQ.isEmpty() ||
                cleanQ == "2026" ||
                cleanQ == "trending" ||
                cleanQ == "popular" ||
                cleanQ == "all" ||
                cleanQ == "latest" ||
                cleanQ.equals("netflix", ignoreCase = true)

        // Return from in-memory cache if generic and fresh (10 mins)
        if (isGeneric && cachedCatalog.isNotEmpty() && (System.currentTimeMillis() - cacheTime < 600_000L)) {
            return cachedCatalog
        }

        val list = mutableListOf<MediaItem>()
        val seenIds = HashSet<String>()
        val seenTitles = HashSet<String>()

        fun addDeduplicated(items: List<MediaItem>) {
            for (item in items) {
                val normTitle = item.title.lowercase().replace(Regex("[^a-z0-9]"), "")
                if (seenIds.add(item.id) && seenTitles.add(normTitle)) {
                    list.add(item)
                }
            }
        }

        try {
            if (isGeneric) {
                val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())

                // Prepare request tasks for Pages 1, 2, and 3 across:
                // 1. Trending Cinejoy Movies (Pages 1..3)
                // 2. Trending Cinejoy TV Series (Pages 1..3)
                // 3. Latest Updated Cinejoy Movies (Pages 1..3)
                // 4. Latest Updated Cinejoy TV Series (Pages 1..3)
                val querySpecs = mutableListOf<Pair<String, Boolean>>() // Pair(url, isMovie)
                for (page in 1..3) {
                    querySpecs.add(Pair("$TMDB_BASE_URL/discover/movie?api_key=$CINEJOY_TMDB_API_KEY&with_watch_providers=8&watch_region=US&sort_by=popularity.desc&page=$page", true))
                    querySpecs.add(Pair("$TMDB_BASE_URL/discover/tv?api_key=$CINEJOY_TMDB_API_KEY&with_watch_providers=8&watch_region=US&sort_by=popularity.desc&page=$page", false))
                    querySpecs.add(Pair("$TMDB_BASE_URL/discover/movie?api_key=$CINEJOY_TMDB_API_KEY&with_watch_providers=8&watch_region=US&sort_by=primary_release_date.desc&primary_release_date.lte=$today&page=$page", true))
                    querySpecs.add(Pair("$TMDB_BASE_URL/discover/tv?api_key=$CINEJOY_TMDB_API_KEY&with_watch_providers=8&watch_region=US&sort_by=first_air_date.desc&first_air_date.lte=$today&page=$page", false))
                }

                // ⚡ Fetch all Pages 1, 2, and 3 queries concurrently in parallel
                val fetchedBatches = runBlocking(Dispatchers.IO) {
                    querySpecs.map { (url, isMovie) ->
                        async {
                            try {
                                val json = fetchJson(url)
                                parseTmdbItems(json, isMovie = isMovie)
                            } catch (_: Exception) {
                                emptyList()
                            }
                        }
                    }.awaitAll()
                }

                for (batch in fetchedBatches) {
                    addDeduplicated(batch)
                }

                if (list.isNotEmpty()) {
                    cachedCatalog = list
                    cacheTime = System.currentTimeMillis()
                }
            } else {
                // Specific search query across multi-search for Pages 1, 2, and 3
                val encodedQ = URLEncoder.encode(cleanQ, "UTF-8")
                val searchUrls = listOf(
                    "$TMDB_BASE_URL/search/multi?api_key=$CINEJOY_TMDB_API_KEY&query=$encodedQ&include_adult=false&page=1",
                    "$TMDB_BASE_URL/search/multi?api_key=$CINEJOY_TMDB_API_KEY&query=$encodedQ&include_adult=false&page=2",
                    "$TMDB_BASE_URL/search/multi?api_key=$CINEJOY_TMDB_API_KEY&query=$encodedQ&include_adult=false&page=3"
                )
                val searchBatches = runBlocking(Dispatchers.IO) {
                    searchUrls.map { sUrl ->
                        async {
                            try {
                                val json = fetchJson(sUrl)
                                parseTmdbItems(json, isMulti = true)
                            } catch (_: Exception) {
                                emptyList()
                            }
                        }
                    }.awaitAll()
                }
                for (batch in searchBatches) {
                    addDeduplicated(batch)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error fetching Cinejoy Netflix catalog: ${e.message}", e)
        }

        if (list.isEmpty() && cachedCatalog.isNotEmpty()) {
            return cachedCatalog
        }

        return list
    }

    private fun fetchJson(url: String): String {
        return try {
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", userAgent)
                .header("Accept", "application/json")
                .header("Referer", "https://cinejoy.to/")
                .build()

            okHttpClient.newCall(req).execute().use { res ->
                if (res.isSuccessful) res.body?.string() ?: "" else ""
            }
        } catch (e: Exception) {
            Log.w(TAG, "HTTP fetch failed for $url: ${e.message}")
            ""
        }
    }

    private fun parseTmdbItems(jsonStr: String, isMovie: Boolean = true, isMulti: Boolean = false): List<MediaItem> {
        if (jsonStr.isEmpty()) return emptyList()
        val items = mutableListOf<MediaItem>()

        try {
            val root = JSONObject(jsonStr)
            val results = root.optJSONArray("results") ?: return emptyList()

            for (i in 0 until results.length()) {
                val obj = results.optJSONObject(i) ?: continue
                val id = obj.optInt("id", 0)
                if (id <= 0) continue

                val mediaType = if (isMulti) obj.optString("media_type", "movie") else if (isMovie) "movie" else "tv"
                if (mediaType != "movie" && mediaType != "tv") continue

                val isItemMovie = mediaType == "movie"
                val rawTitle = if (isItemMovie) obj.optString("title") else obj.optString("name")
                val cleanTitle = rawTitle.trim()
                if (cleanTitle.isEmpty()) continue

                val posterPath = obj.optString("poster_path", "")
                val posterUrl = if (posterPath.isNotEmpty()) "$TMDB_IMAGE_BASE$posterPath" else ""

                val dateStr = if (isItemMovie) obj.optString("release_date", "") else obj.optString("first_air_date", "")
                val year = if (dateStr.length >= 4) dateStr.substring(0, 4) else "2026"

                val voteAvg = obj.optDouble("vote_average", 0.0)
                val rating = if (voteAvg > 0.0) String.format("⭐ %.1f", voteAvg) else "⭐ 8.5"

                // Generate Cinejoy Watch URL
                val watchUrl = if (isItemMovie) {
                    "https://cinejoy.to/watch/movie/$id"
                } else {
                    "https://cinejoy.to/watch/tv/$id/1/1"
                }

                items.add(
                    MediaItem(
                        id = "netflix_$id",
                        title = cleanTitle,
                        link = watchUrl,
                        poster = posterUrl,
                        thumbnail = posterUrl,
                        rating = rating,
                        year = year,
                        source = "Netflix",
                        isMovie = isItemMovie,
                        isTv = !isItemMovie,
                        isNetflix = true
                    )
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "JSON parse error: ${e.message}")
        }

        return items
    }
}
