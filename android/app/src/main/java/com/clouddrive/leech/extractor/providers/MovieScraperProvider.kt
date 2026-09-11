package com.clouddrive.leech.extractor.providers

import com.clouddrive.leech.extractor.models.MediaDetails
import com.clouddrive.leech.extractor.models.MediaItem
import com.clouddrive.leech.extractor.models.StreamResult
import com.clouddrive.leech.extractor.providers.movies.*
import com.clouddrive.leech.vpn.net.UniversalAntiCensorDns
import kotlinx.coroutines.*
import okhttp3.OkHttpClient

/**
 * High-Speed Unified Cinema & Sinhala Subtitle Movie Provider.
 * Orchestrates modular sub-scrapers (Sinhalasub, Baiscope, SubLK/Subz, PirateLK, YTS)
 * with multi-threaded coroutine parallelism and in-memory cache.
 */
class MovieScraperProvider(
    okHttpClient: OkHttpClient = BaseMovieScraper.defaultClient
) : ExtractorProvider {

    override val providerId: String = "movies"
    override val providerName: String = "Cinema Movie Scraper"
    override val priority: Int = 20

    // Modular Sub-Scrapers
    private val sinhalasubScraper = SinhalasubScraper(okHttpClient)
    private val baiscopeScraper = BaiscopeScraper(okHttpClient)
    private val subLKScraper = SubLKScraper(okHttpClient)
    private val pirateLKScraper = PirateLKScraper(okHttpClient)
    private val ytsScraper = YtsScraper(okHttpClient)
    private val netflixScraper = NetflixScraper(okHttpClient)
    private val detailExtractor = MovieDetailExtractor(okHttpClient)
    private val streamResolver = MovieStreamResolver(okHttpClient)

    init {
        // DNS Pre-Warming: Pre-resolve hostnames on startup so the first movie tap has 0ms DNS latency
        @OptIn(DelicateCoroutinesApi::class)
        GlobalScope.launch(Dispatchers.IO) {
            val domains = listOf(
                "cinejoy.to",
                "api.themoviedb.org",
                "image.tmdb.org",
                "downloads.shegu.st",
                "vidsrc2.ru",
                "vidlink.pro",
                "multiembed.mov",
                "sinhalasub.lk",
                "sub.lk",
                "baiscope.lk",
                "piratelk.com",
                "yts.mx",
                "pixeldrain.com",
                "bot.cinerustreams.com"
            )
            for (d in domains) {
                try {
                    UniversalAntiCensorDns.instance.lookup(d)
                } catch (_: Exception) {}
            }
        }
    }

    override fun canHandle(url: String): Boolean {
        val lower = url.lowercase()
        return lower.contains("cinejoy") ||
                lower.contains("shegu.st") ||
                lower.contains("sinhalasub") ||
                lower.contains("sub.lk") ||
                lower.contains("subz.lk") ||
                lower.contains("piratelk") ||
                lower.contains("baiscope") ||
                lower.contains("cinedub") ||
                lower.contains("yts.") ||
                lower.contains("pixeldrain") ||
                lower.contains("drive.google") ||
                lower.contains("/links/")
    }

    fun clearCache() {
        // RAM-free: Zero in-memory state kept
    }

    override suspend fun search(query: String, page: Int): List<MediaItem> = withContext(Dispatchers.IO) {
        val cleanQ = query.trim()

        // Pure Netflix View: Return directly from Netflix live scraper
        if (cleanQ.equals("netflix", ignoreCase = true)) {
            val netflixOnly = withTimeoutOrNull(7000) { netflixScraper.search("netflix") } ?: emptyList()
            if (netflixOnly.isNotEmpty()) {
                return@withContext netflixOnly
            }
        }

        val results = coroutineScope {
            val taskSinhala = async {
                withTimeoutOrNull(5000) { sinhalasubScraper.search(cleanQ) } ?: emptyList()
            }
            val taskBaiscope = async {
                withTimeoutOrNull(5000) { baiscopeScraper.search(cleanQ) } ?: emptyList()
            }
            val taskSubLK = async {
                withTimeoutOrNull(5000) { subLKScraper.search(cleanQ) } ?: emptyList()
            }
            val taskPirate = async {
                withTimeoutOrNull(5000) { pirateLKScraper.search(cleanQ) } ?: emptyList()
            }
            val taskYts = async {
                withTimeoutOrNull(5000) { ytsScraper.search(cleanQ) } ?: emptyList()
            }
            val taskNetflix = async {
                withTimeoutOrNull(5000) { netflixScraper.search(cleanQ) } ?: emptyList()
            }

            val sinhalaList = taskSinhala.await()
            val subLKList = taskSubLK.await()
            val baiscopeList = taskBaiscope.await()
            val pirateList = taskPirate.await()
            val ytsList = taskYts.await()
            val netflixList = taskNetflix.await()

            val combined = mutableListOf<MediaItem>()

            // Interleave newest items across providers so newly uploaded movies appear naturally
            val maxLen = maxOf(sinhalaList.size, subLKList.size, baiscopeList.size, pirateList.size, ytsList.size, netflixList.size)
            for (i in 0 until maxLen) {
                if (i < netflixList.size) combined.add(netflixList[i])
                if (i < sinhalaList.size) combined.add(sinhalaList[i])
                if (i < subLKList.size) combined.add(subLKList[i])
                if (i < baiscopeList.size) combined.add(baiscopeList[i])
                if (i < pirateList.size) combined.add(pirateList[i])
                if (i < ytsList.size) combined.add(ytsList[i])
            }
            combined
        }

        val seen = HashSet<String>()
        val seenTitles = HashSet<String>()
        val unique = mutableListOf<MediaItem>()

        for (m in results) {
            val normTitle = m.title.lowercase().replace(Regex("[^a-z0-9]"), "")
            val idKey = if (m.id.isNotEmpty()) m.id else m.link
            val titleKey = "${m.source.lowercase()}_${normTitle}_${m.year}"
            if (seen.add(idKey) && seenTitles.add(titleKey)) {
                unique.add(m)
            }
        }

        unique
    }

    override suspend fun getDetails(url: String): MediaDetails = withContext(Dispatchers.IO) {
        detailExtractor.getDetails(url)
    }

    override suspend fun extractStreams(url: String): StreamResult = withContext(Dispatchers.IO) {
        streamResolver.resolve(url)
    }

    suspend fun resolveFinalDownloadUrl(targetUrl: String): StreamResult = withContext(Dispatchers.IO) {
        streamResolver.resolve(targetUrl)
    }
}
