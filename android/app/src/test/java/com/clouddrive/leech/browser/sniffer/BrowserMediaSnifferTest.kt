package com.clouddrive.leech.browser.sniffer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserMediaSnifferTest {

    @Test
    fun testIsMediaUrlRecognition() {
        // Playable media formats
        assertTrue("HLS playlist should be media", BrowserMediaSniffer.isMediaUrl("https://example.com/hls/live/master.m3u8"))
        assertTrue("MP4 direct file should be media", BrowserMediaSniffer.isMediaUrl("https://cdn.site.com/videos/sample.mp4?token=123"))
        assertTrue("WebM video should be media", BrowserMediaSniffer.isMediaUrl("https://video.host.org/stream.webm"))
        assertTrue("DASH manifest should be media", BrowserMediaSniffer.isMediaUrl("https://streaming.com/dash/manifest.mpd"))
        assertTrue("YouTube videoplayback should be media", BrowserMediaSniffer.isMediaUrl("https://rr1---sn-4g5ednks.googlevideo.com/videoplayback?expire=123"))
        assertTrue("MKV video file should be media", BrowserMediaSniffer.isMediaUrl("https://files.com/movie.mkv"))

        // Common web assets should NOT be media
        assertFalse("PNG should not be media", BrowserMediaSniffer.isMediaUrl("https://example.com/images/thumb.png"))
        assertFalse("JPEG should not be media", BrowserMediaSniffer.isMediaUrl("https://example.com/photos/banner.jpg"))
        assertFalse("CSS stylesheet should not be media", BrowserMediaSniffer.isMediaUrl("https://example.com/style.css"))
        assertFalse("JavaScript should not be media", BrowserMediaSniffer.isMediaUrl("https://example.com/app.js"))
        assertFalse("Font woff2 should not be media", BrowserMediaSniffer.isMediaUrl("https://example.com/fonts/inter.woff2"))

        // Ad and tracker URLs should be rejected
        assertFalse("Doubleclick should be rejected", BrowserMediaSniffer.isMediaUrl("https://ad.doubleclick.net/video.mp4?ad=1"))
        assertFalse("Googleads should be rejected", BrowserMediaSniffer.isMediaUrl("https://pagead2.googlesyndication.com/pagead/ads?client=ca-pub"))
        assertFalse("Exoclick should be rejected", BrowserMediaSniffer.isMediaUrl("https://syndication.exoclick.com/splash.php"))
    }

    @Test
    fun testFormatDetection() {
        assertEquals("HLS (m3u8)", BrowserMediaSniffer.detectFormat("https://cdn.com/playlist.m3u8"))
        assertEquals("DASH (mpd)", BrowserMediaSniffer.detectFormat("https://cdn.com/manifest.mpd"))
        assertEquals("WebM", BrowserMediaSniffer.detectFormat("https://cdn.com/clip.webm"))
        assertEquals("MKV", BrowserMediaSniffer.detectFormat("https://cdn.com/film.mkv"))
        assertEquals("MP4", BrowserMediaSniffer.detectFormat("https://cdn.com/video.mp4"))
    }

    @Test
    fun testTabMediaStore() {
        val tabId = "test_tab_1"
        BrowserMediaSniffer.clearForTab(tabId)
        assertEquals(0, BrowserMediaSniffer.getMediaCountForTab(tabId))

        val media = SniffedMedia(
            url = "https://cdn.example.com/stream/index.m3u8",
            title = "Test Stream Video",
            format = "HLS (m3u8)",
            pageUrl = "https://example.com/watch",
            referer = "https://example.com/watch"
        )
        BrowserMediaSniffer.addFromJs(tabId, media)

        assertEquals(1, BrowserMediaSniffer.getMediaCountForTab(tabId))
        val items = BrowserMediaSniffer.getMediaForTab(tabId)
        assertEquals(1, items.size)
        assertEquals("Test Stream Video", items[0].title)
        assertEquals("HLS (m3u8)", items[0].format)

        BrowserMediaSniffer.clearForTab(tabId)
        assertEquals(0, BrowserMediaSniffer.getMediaCountForTab(tabId))
    }
}
