package com.clouddrive.leech.extractor.resolvers.netflix

data class NetflixStreamResult(
    val success: Boolean,
    val title: String = "",
    val streamUrl: String = "",
    val quality: String = "1080p",
    val type: String = "video",
    val isHls: Boolean = false,
    val subtitles: List<NetflixSubtitle> = emptyList(),
    val audioTracks: List<String> = emptyList(),
    val error: String? = null
)

data class NetflixSubtitle(
    val label: String,
    val language: String,
    val url: String
)
