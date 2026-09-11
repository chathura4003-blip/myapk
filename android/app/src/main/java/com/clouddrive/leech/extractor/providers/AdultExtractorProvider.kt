package com.clouddrive.leech.extractor.providers

import com.clouddrive.leech.extractor.models.MediaDetails
import com.clouddrive.leech.extractor.models.MediaItem
import com.clouddrive.leech.extractor.models.StreamResult
import com.clouddrive.leech.extractor.providers.adult.BaseAdultScraper
import com.clouddrive.leech.extractor.providers.adult.EpornerScraper
import com.clouddrive.leech.extractor.providers.adult.PornhubScraper
import com.clouddrive.leech.extractor.providers.adult.RedtubeScraper
import com.clouddrive.leech.extractor.providers.adult.XHamsterScraper
import com.clouddrive.leech.extractor.providers.adult.XNXXScraper
import com.clouddrive.leech.extractor.providers.adult.XVideosScraper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

/**
 * Unified 18+ Adult Velvet Hub Orchestrator with Intelligent Recommendation Algorithm,
 * Dynamic Topic Pool Sampling, and Interleaved Multi-Portal Scrapers.
 */
class AdultExtractorProvider(
    client: OkHttpClient = BaseAdultScraper.defaultClient
) : ExtractorProvider {

    override val providerId: String = "adult"
    override val providerName: String = "18+ Adult Hub Engine"
    override val priority: Int = 40

    val eporner = EpornerScraper(client)
    val pornhub = PornhubScraper(client)
    val xhamster = XHamsterScraper(client)
    val xvideos = XVideosScraper(client)
    val xnxx = XNXXScraper(client)
    val redtube = RedtubeScraper(client)

    // ⚡ 5-Minute In-Memory Feed Cache (Instant 0ms Category Switching)
    private val queryCache = ConcurrentHashMap<String, Pair<Long, List<MediaItem>>>()

    // ⚡ 10-Minute Stream URL Resolution Cache (Instant 0ms Watch & Download Start)
    private val streamCache = ConcurrentHashMap<String, Pair<Long, StreamResult>>()

    private val dynamicTopics = listOf(
        "4k", "trending", "amateur", "japanese", "latina", "blonde", "erotic",
        "vr", "milf", "hd", "romance", "pov", "teen", "asian", "ebony", "lesbian",
        "brunette", "massage", "anal", "threesome", "creampie", "cosplay", "babe"
    )

    override fun canHandle(url: String): Boolean {
        val lower = url.lowercase()
        return lower.contains("eporner.com") ||
                lower.contains("pornhub.com") ||
                lower.contains("xhamster.com") ||
                lower.contains("xvideos.com") ||
                lower.contains("xnxx.com") ||
                lower.contains("redtube.com")
    }

    override suspend fun search(query: String, page: Int): List<MediaItem> = withContext(Dispatchers.IO) {
        searchWithSource(query, page, "all")
    }

    suspend fun searchWithSource(query: String, page: Int, source: String = "all"): List<MediaItem> = withContext(Dispatchers.IO) {
        val rawQ = query.trim().lowercase()
        val isDiscoverMode = rawQ.isEmpty() || rawQ == "popular" || rawQ == "trending" || rawQ == "for-you" || rawQ == "all" || rawQ == "discover" || rawQ == "surprise" || rawQ == "fresh"
        val normSource = source.trim().lowercase()

        // Check in-memory cache first
        val cacheKey = "${normSource}_${rawQ}_$page"
        val cached = queryCache[cacheKey]
        val now = System.currentTimeMillis()
        if (cached != null && (now - cached.first) < 5 * 60 * 1000L && cached.second.isNotEmpty()) {
            return@withContext cached.second
        }

        // 🧠 1. Specialized Algorithm Routing & Dynamic Topic Discovery
        val targetKw: String
        val targetPage: Int

        when (rawQ) {
            "4k" -> {
                targetKw = "4k"
                targetPage = page
            }
            "top-rated" -> {
                targetKw = "top"
                targetPage = page
            }
            "trending", "popular", "viral", "hot" -> {
                targetKw = "trending"
                targetPage = page
            }
            "surprise" -> {
                val shuffled = dynamicTopics.shuffled()
                targetKw = shuffled[0]
                targetPage = if (page > 1) page else Random.nextInt(1, 4)
            }
            "for-you", "discover", "fresh", "", "all" -> {
                // For You: Real Multi-Portal Trending with page awareness
                targetKw = "trending"
                targetPage = page
            }
            else -> {
                targetKw = rawQ
                targetPage = page
            }
        }

        // Single Portal Query with Fast 4s Timeout
        if (normSource != "all") {
            val singleList = when (normSource) {
                "eporner" -> try { withTimeoutOrNull(4000L) { eporner.search(targetKw, targetPage) } ?: emptyList() } catch (_: Exception) { emptyList() }
                "pornhub" -> try { withTimeoutOrNull(4000L) { pornhub.search(targetKw, targetPage) } ?: emptyList() } catch (_: Exception) { emptyList() }
                "xhamster" -> try { withTimeoutOrNull(4000L) { xhamster.search(targetKw, targetPage) } ?: emptyList() } catch (_: Exception) { emptyList() }
                "xvideos" -> try { withTimeoutOrNull(4000L) { xvideos.search(targetKw, targetPage) } ?: emptyList() } catch (_: Exception) { emptyList() }
                "xnxx" -> try { withTimeoutOrNull(4000L) { xnxx.search(targetKw, targetPage) } ?: emptyList() } catch (_: Exception) { emptyList() }
                "redtube" -> try { withTimeoutOrNull(4000L) { redtube.search(targetKw, targetPage) } ?: emptyList() } catch (_: Exception) { emptyList() }
                else -> emptyList()
            }
            val deduplicated = deduplicateMediaItems(if (isDiscoverMode) singleList.shuffled() else singleList)
            if (deduplicated.isNotEmpty()) {
                queryCache[cacheKey] = Pair(now, deduplicated)
            }
            return@withContext deduplicated
        }

        // 🧠 2. Multi-Portal Fast Parallel Fetch with Fast-Yield Channel
        val completedResults = mutableListOf<List<MediaItem>>()
        coroutineScope {
            val channel = Channel<List<MediaItem>>(6)
            val startTime = System.currentTimeMillis()
            val jobs = listOf(
                launch { channel.send(try { withTimeoutOrNull(2500L) { eporner.search(targetKw, targetPage) } ?: emptyList() } catch (_: Exception) { emptyList() }) },
                launch { channel.send(try { withTimeoutOrNull(2500L) { xvideos.search(targetKw, targetPage) } ?: emptyList() } catch (_: Exception) { emptyList() }) },
                launch { channel.send(try { withTimeoutOrNull(2500L) { xhamster.search(targetKw, targetPage) } ?: emptyList() } catch (_: Exception) { emptyList() }) },
                launch { channel.send(try { withTimeoutOrNull(2500L) { pornhub.search(targetKw, targetPage) } ?: emptyList() } catch (_: Exception) { emptyList() }) },
                launch { channel.send(try { withTimeoutOrNull(2500L) { xnxx.search(targetKw, targetPage) } ?: emptyList() } catch (_: Exception) { emptyList() }) },
                launch { channel.send(try { withTimeoutOrNull(2500L) { redtube.search(targetKw, targetPage) } ?: emptyList() } catch (_: Exception) { emptyList() }) }
            )

            var totalItems = 0
            for (i in jobs.indices) {
                val remainingMs = (2400L - (System.currentTimeMillis() - startTime)).coerceAtLeast(50L)
                val res = withTimeoutOrNull(remainingMs) { channel.receive() } ?: emptyList()
                if (res.isNotEmpty()) {
                    completedResults.add(res)
                    totalItems += res.size
                }
                val elapsed = System.currentTimeMillis() - startTime
                // Fast-yield: If we have at least 2 fast portals and 20+ items after 1000ms, or total elapsed >= 2000ms
                if ((completedResults.size >= 2 && totalItems >= 20 && elapsed >= 1000L) || elapsed >= 2000L) {
                    break
                }
            }

            jobs.forEach { it.cancel() }
        }

        val providerArrays = completedResults.filter { it.isNotEmpty() }.map { if (isDiscoverMode) it.shuffled() else it }

        if (providerArrays.isEmpty()) return@withContext emptyList()

        // 🧠 3. Perfect Round-Robin Multi-Portal Interleaving
        val maxLen = providerArrays.maxOf { it.size }
        val mixed = mutableListOf<MediaItem>()
        for (i in 0 until maxLen) {
            for (arr in providerArrays) {
                if (i < arr.size) {
                    mixed.add(arr[i])
                }
            }
        }

        // 🧠 4. Discovery Jitter Shuffle
        if (isDiscoverMode && mixed.size > 2) {
            for (i in mixed.indices.reversed()) {
                if (Random.nextDouble() < 0.35) {
                    val swapIdx = (i - Random.nextInt(1, 4)).coerceAtLeast(0)
                    val tmp = mixed[i]
                    mixed[i] = mixed[swapIdx]
                    mixed[swapIdx] = tmp
                }
            }
        }

        val finalDeduplicated = deduplicateMediaItems(mixed)
        if (finalDeduplicated.isNotEmpty()) {
            if (queryCache.size >= 20) {
                val oldestKey = queryCache.minByOrNull { it.value.first }?.key
                if (oldestKey != null) {
                    queryCache.remove(oldestKey)
                }
            }
            queryCache[cacheKey] = Pair(now, finalDeduplicated)
        }
        finalDeduplicated
    }

    private fun deduplicateMediaItems(items: List<MediaItem>): List<MediaItem> {
        val seenUrls = HashSet<String>()
        val seenTitles = HashSet<String>()
        val unique = mutableListOf<MediaItem>()

        for (item in items) {
            val normUrl = item.link.trim().lowercase().replace(Regex("""/+$"""), "")
            val normTitle = item.title.trim().lowercase().replace(Regex("""\s+"""), " ").trim()

            if (normUrl.isNotEmpty() && seenUrls.add(normUrl) && (normTitle.isEmpty() || seenTitles.add(normTitle))) {
                unique.add(item)
            }
        }
        return unique
    }

    override suspend fun getDetails(url: String): MediaDetails = withContext(Dispatchers.IO) {
        val resolved = resolveAdultStream(url)
        MediaDetails(
            title = resolved.title.ifEmpty { "18+ HD Video Stream" },
            poster = "https://images.unsplash.com/photo-1518173946687-a4c8a383392e?w=400",
            synopsis = "Ultra High Definition Stream.",
            rating = "4K Ultra HD",
            year = "2026",
            uploader = "Verified Creator",
            duration = 900L,
            qualities = resolved.qualities,
            directStreamUrl = resolved.streamUrl,
            directDownloadUrl = resolved.downloadUrl,
            embedUrl = resolved.embedUrl
        )
    }

    override suspend fun extractStreams(url: String): StreamResult = withContext(Dispatchers.IO) {
        resolveAdultStream(url)
    }

    suspend fun resolveAdultStream(targetUrl: String): StreamResult = withContext(Dispatchers.IO) {
        val target = targetUrl.trim()
        val now = System.currentTimeMillis()

        // ⚡ Check in-memory stream cache (Instant 0ms resolution)
        val cached = streamCache[target]
        if (cached != null && (now - cached.first) < 20 * 60 * 1000L) {
            return@withContext cached.second
        }

        val resolved = when {
            target.contains("eporner.com") -> withTimeoutOrNull(4500L) { eporner.resolve(target) }
            target.contains("pornhub.com") -> withTimeoutOrNull(4500L) { pornhub.resolve(target) }
            target.contains("xhamster.com") -> withTimeoutOrNull(4500L) { xhamster.resolve(target) }
            target.contains("xvideos.com") -> withTimeoutOrNull(4500L) { xvideos.resolve(target) }
            target.contains("xnxx.com") -> withTimeoutOrNull(4500L) { xnxx.resolve(target) }
            target.contains("redtube.com") -> withTimeoutOrNull(4500L) { redtube.resolve(target) }
            else -> null
        }

        val finalResult = resolved ?: StreamResult(
            title = "Direct Video Stream",
            streamUrl = target,
            downloadUrl = target,
            embedUrl = target,
            type = if (target.contains("embed")) "embed" else "video"
        )

        if (streamCache.size >= 100) {
            val oldest = streamCache.minByOrNull { it.value.first }?.key
            if (oldest != null) streamCache.remove(oldest)
        }
        streamCache[target] = Pair(now, finalResult)

        finalResult
    }

    fun clearCache() {
        queryCache.clear()
        streamCache.clear()
    }
}
