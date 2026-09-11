package com.clouddrive.leech.torrent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests validating Phase 10 Torrent engine utility logic:
 * - Magnet BTIH hash extraction
 * - Media file classification
 */
class TorrentEngineValidationTest {

    @Test
    fun testExtractHashFromStandardMagnet() {
        val magnet = "magnet:?xt=urn:btih:c12fe1c06bba254a9dc9f519b335aa7c1367a80a&dn=Ubuntu+ISO"
        val hash = TorrentEngineManager.extractHashFromMagnet(magnet)
        assertNotNull(hash)
        assertEquals("c12fe1c06bba254a9dc9f519b335aa7c1367a80a", hash)
    }

    @Test
    fun testIsVideoFile() {
        assertTrue(TorrentEngineManager.isVideoFile("Movie.2026.1080p.mp4"))
        assertTrue(TorrentEngineManager.isVideoFile("Stream.mkv"))
        assertTrue(TorrentEngineManager.isVideoFile("Clip.webm"))
        assertTrue(TorrentEngineManager.isVideoFile("Episode.avi"))
        assertTrue(TorrentEngineManager.isVideoFile("Recording.mov"))
        assertFalse(TorrentEngineManager.isVideoFile("Document.pdf"))
        assertFalse(TorrentEngineManager.isVideoFile("Archive.zip"))
        assertFalse(TorrentEngineManager.isVideoFile("Song.mp3"))
        assertFalse(TorrentEngineManager.isVideoFile("Readme.txt"))
    }
}
