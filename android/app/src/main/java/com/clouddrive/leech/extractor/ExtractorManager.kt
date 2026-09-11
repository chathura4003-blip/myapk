package com.clouddrive.leech.extractor

import com.clouddrive.leech.extractor.models.ExtractorResponse
import com.clouddrive.leech.extractor.models.MediaDetails
import com.clouddrive.leech.extractor.models.MediaItem
import com.clouddrive.leech.extractor.models.StreamResult
import com.clouddrive.leech.extractor.providers.AdultExtractorProvider
import com.clouddrive.leech.extractor.providers.ExtractorProvider
import com.clouddrive.leech.extractor.providers.MovieScraperProvider
import com.clouddrive.leech.extractor.providers.NewPipeProvider
import com.clouddrive.leech.extractor.providers.SocialMediaExtractorProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Central Extractor Manager coordinating on-device providers.
 * Manages provider registration, URL routing, fallback strategies, and error resilience.
 */
class ExtractorManager private constructor() {

    private val providers = mutableListOf<ExtractorProvider>()

    val movieProvider = MovieScraperProvider()
    val newPipeProvider = NewPipeProvider()
    val socialMediaProvider = SocialMediaExtractorProvider()
    val adultProvider = AdultExtractorProvider()

    init {
        // Register default providers ordered by priority
        registerProvider(newPipeProvider)
        registerProvider(movieProvider)
        registerProvider(socialMediaProvider)
        registerProvider(adultProvider)
    }

    companion object {
        @Volatile
        private var instance: ExtractorManager? = null

        fun getInstance(): ExtractorManager {
            return instance ?: synchronized(this) {
                instance ?: ExtractorManager().also { instance = it }
            }
        }
    }

    fun registerProvider(provider: ExtractorProvider) {
        providers.removeAll { it.providerId == provider.providerId }
        providers.add(provider)
        providers.sortBy { it.priority }
    }

    fun clearCache() {
        movieProvider.clearCache()
        adultProvider.clearCache()
    }

    /**
     * Resolves the best provider for a given URL
     */
    fun findProviderForUrl(url: String): ExtractorProvider? {
        return providers.firstOrNull { it.canHandle(url) }
    }

    /**
     * Search movies across on-device movie scrapers
     */
    suspend fun searchMovies(query: String, page: Int = 1): ExtractorResponse<List<MediaItem>> = withContext(Dispatchers.IO) {
        try {
            val results = movieProvider.search(query, page)
            ExtractorResponse(success = true, data = results, totalCount = results.size)
        } catch (e: Exception) {
            ExtractorResponse(success = false, error = e.message ?: "Failed to search movies on device", data = emptyList())
        }
    }

    /**
     * Get Movie details & available download/stream qualities
     */
    suspend fun getMovieDetails(url: String): ExtractorResponse<MediaDetails> = withContext(Dispatchers.IO) {
        try {
            val details = movieProvider.getDetails(url)
            ExtractorResponse(success = true, data = details)
        } catch (e: Exception) {
            ExtractorResponse(success = false, error = e.message ?: "Failed to extract movie details", data = null)
        }
    }

    /**
     * Resolve final movie download link / stream
     */
    suspend fun resolveMovieStream(url: String): ExtractorResponse<StreamResult> = withContext(Dispatchers.IO) {
        try {
            val result = movieProvider.resolveFinalDownloadUrl(url)
            ExtractorResponse(success = true, data = result)
        } catch (e: Exception) {
            ExtractorResponse(success = false, error = e.message ?: "Failed to resolve stream link", data = null)
        }
    }

    /**
     * Extract universal social media stream (YouTube, TikTok, Facebook, IG, X, PixelDrain, direct)
     */
    suspend fun extractMedia(url: String): ExtractorResponse<MediaDetails> = withContext(Dispatchers.IO) {
        val targetUrl = url.trim()
        if (targetUrl.isEmpty()) {
            return@withContext ExtractorResponse(success = false, error = "No media URL provided")
        }

        try {
            val provider = findProviderForUrl(targetUrl) ?: socialMediaProvider
            val details = provider.getDetails(targetUrl)
            ExtractorResponse(success = true, data = details)
        } catch (e: Exception) {
            // Fallback to socialMediaProvider
            try {
                val details = socialMediaProvider.getDetails(targetUrl)
                ExtractorResponse(success = true, data = details)
            } catch (ex: Exception) {
                ExtractorResponse(success = false, error = ex.message ?: "Extraction failed")
            }
        }
    }

    /**
     * Search 18+ adult hub
     */
    suspend fun searchAdult(query: String, page: Int = 1, source: String = "all"): ExtractorResponse<List<MediaItem>> = withContext(Dispatchers.IO) {
        try {
            val results = adultProvider.searchWithSource(query, page, source)
            ExtractorResponse(success = true, data = results, totalCount = results.size)
        } catch (e: Exception) {
            ExtractorResponse(success = false, error = e.message ?: "Failed to search adult hub", data = emptyList())
        }
    }

    /**
     * Resolve 18+ stream
     */
    suspend fun resolveAdultStream(url: String): ExtractorResponse<StreamResult> = withContext(Dispatchers.IO) {
        try {
            val result = adultProvider.resolveAdultStream(url)
            ExtractorResponse(success = true, data = result)
        } catch (e: Exception) {
            ExtractorResponse(success = false, error = e.message ?: "Failed to resolve 18+ stream", data = null)
        }
    }
}
