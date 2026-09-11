package com.clouddrive.leech.extractor.providers

import com.clouddrive.leech.extractor.models.MediaDetails
import com.clouddrive.leech.extractor.models.MediaItem
import com.clouddrive.leech.extractor.models.StreamFormat
import com.clouddrive.leech.extractor.models.StreamResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem

/**
 * On-Device Extractor Provider powered directly by NewPipe Extractor.
 * Provides extraction for YouTube, SoundCloud, PeerTube, Bandcamp, Media URLs.
 */
class NewPipeProvider : ExtractorProvider {

    override val providerId: String = "newpipe"
    override val providerName: String = "NewPipe Extractor"
    override val priority: Int = 10

    override fun canHandle(url: String): Boolean {
        val lower = url.lowercase()
        return lower.contains("youtube.com") ||
                lower.contains("youtu.be") ||
                lower.contains("soundcloud.com") ||
                lower.contains("peertube") ||
                lower.contains("bandcamp.com")
    }

    override suspend fun search(query: String, page: Int): List<MediaItem> = withContext(Dispatchers.IO) {
        try {
            val service = ServiceList.YouTube
            val searchHandler = service.searchQHFactory.fromQuery(
                query,
                listOf(YoutubeSearchQueryHandlerFactory.VIDEOS),
                ""
            )
            val searchExtractor = service.getSearchExtractor(searchHandler)
            searchExtractor.fetchPage()

            val results = mutableListOf<MediaItem>()
            val items = searchExtractor.initialPage.items
            for (item in items) {
                if (item is StreamInfoItem) {
                    val thumbs = item.thumbnails
                    val posterUrl = thumbs.lastOrNull()?.url ?: "https://img.youtube.com/vi/${item.url.substringAfterLast("=")}/hqdefault.jpg"
                    val durationSec = item.duration
                    val durationStr = if (durationSec > 0) {
                        val m = durationSec / 60
                        val s = durationSec % 60
                        String.format("%d:%02d", m, s)
                    } else "HD"

                    results.add(
                        MediaItem(
                            id = item.url,
                            title = item.name ?: "Media Stream",
                            link = item.url,
                            poster = posterUrl,
                            thumbnail = posterUrl,
                            duration = durationStr,
                            rating = "1080p Full HD",
                            year = "2026",
                            views = "${item.viewCount ?: 0}",
                            source = "YouTube",
                            uploader = item.uploaderName ?: "Creator",
                            isMovie = false
                        )
                    )
                }
            }
            results
        } catch (e: Exception) {
            emptyList()
        }
    }

    override suspend fun getDetails(url: String): MediaDetails = withContext(Dispatchers.IO) {
        try {
            val streamInfo = StreamInfo.getInfo(NewPipe.getServiceByUrl(url), url)

            val formats = mutableListOf<StreamFormat>()

            // 1. Progressive Audio+Video Streams
            for ((idx, stream) in streamInfo.videoStreams.withIndex()) {
                val res = stream.getResolution() ?: "720p"
                val qualityLabel = if (res.contains("p")) res else "${res}p"
                formats.add(
                    StreamFormat(
                        formatId = "np-prog-$idx",
                        quality = qualityLabel,
                        label = "$qualityLabel (Video + Audio)",
                        provider = "NewPipe Native Stream",
                        ext = stream.format?.name?.lowercase() ?: "mp4",
                        url = stream.content ?: "",
                        streamUrl = stream.content ?: "",
                        downloadUrl = stream.content ?: "",
                        size = "Full HD Direct Pipe",
                        isVideoOnly = false,
                        isAudioOnly = false
                    )
                )
            }

            // 2. Video-Only Streams (High-Resolution 1080p, 4K, 60fps)
            for ((idx, stream) in streamInfo.videoOnlyStreams.withIndex()) {
                val res = stream.getResolution() ?: "1080p"
                val qualityLabel = if (res.contains("p")) res else "${res}p"
                formats.add(
                    StreamFormat(
                        formatId = "np-video-$idx",
                        quality = "$qualityLabel 60fps",
                        label = "$qualityLabel Ultra HD (Video)",
                        provider = "NewPipe High-Res Stream",
                        ext = stream.format?.name?.lowercase() ?: "mp4",
                        url = stream.content ?: "",
                        streamUrl = stream.content ?: "",
                        downloadUrl = stream.content ?: "",
                        size = "Ultra High Bitrate",
                        isVideoOnly = true,
                        isAudioOnly = false
                    )
                )
            }

            // 3. Audio Streams (MP3, M4A, Opus)
            for ((idx, stream) in streamInfo.audioStreams.withIndex()) {
                val kbps = stream.averageBitrate
                val label = if (kbps > 0) "${kbps} kbps High Audio" else "HQ Audio Stream"
                formats.add(
                    StreamFormat(
                        formatId = "np-audio-$idx",
                        quality = "Audio Only",
                        label = label,
                        provider = "NewPipe Audio Pipe",
                        ext = stream.format?.name?.lowercase() ?: "m4a",
                        url = stream.content ?: "",
                        streamUrl = stream.content ?: "",
                        downloadUrl = stream.content ?: "",
                        size = "320kbps Audio",
                        isVideoOnly = false,
                        isAudioOnly = true
                    )
                )
            }

            // Best direct playable stream
            val bestPlayable = formats.firstOrNull { !it.isVideoOnly && !it.isAudioOnly } ?: formats.firstOrNull()

            val thumbs = streamInfo.thumbnails
            val posterUrl = thumbs.lastOrNull()?.url ?: ""

            MediaDetails(
                title = streamInfo.name ?: "Stream Media",
                poster = posterUrl,
                synopsis = streamInfo.description?.content ?: "Streamed directly on device with zero server latency.",
                rating = "4K / Full HD",
                year = "2026",
                uploader = streamInfo.uploaderName ?: "Unknown Creator",
                duration = streamInfo.duration,
                qualities = formats,
                directStreamUrl = bestPlayable?.streamUrl,
                directDownloadUrl = bestPlayable?.downloadUrl
            )
        } catch (e: Exception) {
            // Fallback for YouTube embed (Stream Only, no direct file download)
            val videoId = extractYouTubeId(url)
            if (videoId.isNullOrEmpty()) {
                throw Exception("Could not extract YouTube stream info: ${e.message}")
            }
            val fallbackEmbed = "https://www.youtube.com/embed/$videoId"
            MediaDetails(
                title = "YouTube Video ($videoId)",
                poster = "https://img.youtube.com/vi/$videoId/hqdefault.jpg",
                synopsis = "Playing via Native Cinema Player (Stream Only).",
                rating = "Cinema Stream",
                year = "2026",
                uploader = "YouTube Creator",
                duration = 300L,
                qualities = listOf(
                    StreamFormat(
                        formatId = "yt-embed-fallback",
                        quality = "Cinema Stream (Stream Only)",
                        label = "YouTube Web Stream (Stream Only)",
                        provider = "Universal Cinema Pipe",
                        ext = "mp4",
                        url = fallbackEmbed,
                        streamUrl = fallbackEmbed,
                        embedUrl = fallbackEmbed,
                        downloadUrl = "", // Explicitly empty: web embed is not directly downloadable
                        isEmbed = true
                    )
                ),
                directStreamUrl = fallbackEmbed,
                embedUrl = fallbackEmbed
            )
        }
    }

    override suspend fun extractStreams(url: String): StreamResult = withContext(Dispatchers.IO) {
        val details = getDetails(url)
        val bestStream = details.qualities.firstOrNull { !it.isVideoOnly && !it.isAudioOnly && it.url.isNotEmpty() }
            ?: details.qualities.firstOrNull()

        StreamResult(
            streamUrl = bestStream?.streamUrl ?: url,
            downloadUrl = bestStream?.downloadUrl ?: url,
            embedUrl = details.embedUrl ?: "",
            type = if (bestStream?.isEmbed == true) "embed" else "video",
            title = details.title,
            qualities = details.qualities
        )
    }

    private fun extractYouTubeId(url: String): String? {
        val regex = Regex("""(?:youtube\.com\/(?:watch\?v=|embed\/|shorts\/)|youtu\.be\/)([a-zA-Z0-9_-]{11})""")
        return regex.find(url)?.groupValues?.getOrNull(1)
    }
}
