package com.clouddrive.leech.extractor.models

import java.io.Serializable

/**
 * Common data model representing a media search result item
 */
data class MediaItem(
    val id: String = "",
    val title: String,
    val link: String,
    val poster: String = "",
    val thumbnail: String = "",
    val duration: String = "",
    val rating: String = "HD",
    val year: String = "",
    val views: String = "",
    val source: String = "direct",
    val uploader: String = "",
    val isMovie: Boolean = false,
    val isNetflix: Boolean = false,
    val isTv: Boolean = false,
    val trendingScore: Int = 0,
    val torrentsJson: String = "",
    val imdb: String = ""
) : Serializable

/**
 * Detailed movie or video metadata
 */
data class MediaDetails(
    val title: String,
    val poster: String = "",
    val synopsis: String = "",
    val rating: String = "HD",
    val year: String = "",
    val uploader: String = "",
    val duration: Long = 0L,
    val qualities: List<StreamFormat> = emptyList(),
    val directStreamUrl: String? = null,
    val directDownloadUrl: String? = null,
    val embedUrl: String? = null
) : Serializable

/**
 * Stream format information (Resolution, audio/video stream, direct link, codec)
 */
data class StreamFormat(
    val formatId: String = "",
    val quality: String,
    val label: String = quality,
    val provider: String = "Direct Stream",
    val ext: String = "mp4",
    val url: String = "",
    val streamUrl: String = url,
    val downloadUrl: String = url,
    val embedUrl: String = "",
    val size: String = "Auto Stream",
    val isVideoOnly: Boolean = false,
    val isAudioOnly: Boolean = false,
    val isEmbed: Boolean = false,
    val isZip: Boolean = false,
    val type: String = if (isEmbed) "embed" else if (isZip) "download" else "video",
    val headers: Map<String, String> = emptyMap()
) : Serializable

/**
 * Resolved stream result
 */
data class StreamResult(
    val streamUrl: String,
    val downloadUrl: String = streamUrl,
    val embedUrl: String = "",
    val type: String = "video",
    val title: String = "",
    val isZip: Boolean = false,
    val filename: String = "",
    val qualities: List<StreamFormat> = emptyList(),
    val headers: Map<String, String> = emptyMap()
) : Serializable

/**
 * Standard Extractor Response envelope
 */
data class ExtractorResponse<T>(
    val success: Boolean,
    val data: T? = null,
    val error: String? = null,
    val totalCount: Int = 0
) : Serializable
