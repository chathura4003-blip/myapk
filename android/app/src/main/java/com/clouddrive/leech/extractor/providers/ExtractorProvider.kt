package com.clouddrive.leech.extractor.providers

import com.clouddrive.leech.extractor.models.MediaDetails
import com.clouddrive.leech.extractor.models.MediaItem
import com.clouddrive.leech.extractor.models.StreamResult

/**
 * Base interface for all on-device extractors and providers
 */
interface ExtractorProvider {
    val providerId: String
    val providerName: String
    val priority: Int get() = 100

    /**
     * Determines if this provider can handle the given URL
     */
    fun canHandle(url: String): Boolean

    /**
     * Searches for media items
     */
    suspend fun search(query: String, page: Int = 1): List<MediaItem>

    /**
     * Extracts full media metadata and available stream qualities
     */
    suspend fun getDetails(url: String): MediaDetails

    /**
     * Resolves final direct stream URL / formats for playback
     */
    suspend fun extractStreams(url: String): StreamResult
}
