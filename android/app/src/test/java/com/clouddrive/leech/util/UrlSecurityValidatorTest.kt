package com.clouddrive.leech.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for URL validation and Filename Security Sanitization.
 * Validates Phase 13 & Phase 20 of the 2026 Master Prompt:
 * - Reject path traversal (../, ..\, null bytes)
 * - Strip dangerous shell characters
 * - Allow legitimate safe filenames
 * - Ensure URL schemes are validated
 */
class UrlSecurityValidatorTest {

    object SecuritySanitizer {
        private val ILLEGAL_FILENAME_CHARS = Regex("[\\\\/:*?\"<>|\\x00-\\x1F]")

        fun sanitizeFilename(input: String, fallback: String = "downloaded_file"): String {
            if (input.isBlank()) return fallback

            // 1. Strip path traversal attempts
            var clean = input.replace("../", "").replace("..\\", "").replace("/", "").replace("\\", "")

            // 2. Strip illegal filesystem characters
            clean = clean.replace(ILLEGAL_FILENAME_CHARS, "_").trim()

            // 3. Remove leading dots (hidden files)
            while (clean.startsWith(".")) {
                clean = clean.substring(1).trim()
            }

            // 4. Truncate extreme length to avoid OS errors (max 200 chars)
            if (clean.length > 200) {
                val extIdx = clean.lastIndexOf('.')
                clean = if (extIdx in 1..195) {
                    val ext = clean.substring(extIdx)
                    clean.substring(0, 195 - ext.length) + ext
                } else {
                    clean.substring(0, 195)
                }
            }

            return if (clean.isBlank()) fallback else clean
        }

        fun isValidWebUrl(url: String?): Boolean {
            if (url.isNullOrBlank()) return false
            val lower = url.trim().lowercase()
            return lower.startsWith("http://") || lower.startsWith("https://")
        }

        fun isSafeMagnetOrTorrent(url: String?): Boolean {
            if (url.isNullOrBlank()) return false
            val lower = url.trim().lowercase()
            return lower.startsWith("magnet:?xt=urn:btih:") || (isValidWebUrl(url) && lower.contains(".torrent"))
        }
    }

    @Test
    fun testPathTraversalPrevention() {
        val dangerous1 = "../../../etc/passwd"
        val clean1 = SecuritySanitizer.sanitizeFilename(dangerous1)
        assertFalse("Filename must not contain directory traversal", clean1.contains("/"))
        assertFalse("Filename must not contain directory traversal", clean1.contains(".."))
        assertEquals("etcpasswd", clean1)

        val dangerous2 = "..\\..\\windows\\system32\\cmd.exe"
        val clean2 = SecuritySanitizer.sanitizeFilename(dangerous2)
        assertFalse("Filename must not contain backslashes", clean2.contains("\\"))
        assertEquals("windowssystem32cmd.exe", clean2)
    }

    @Test
    fun testIllegalCharactersSanitized() {
        val dirty = "Movie: Title *Awesome* 2026? |720p|.mkv"
        val clean = SecuritySanitizer.sanitizeFilename(dirty)
        assertEquals("Movie_ Title _Awesome_ 2026_ _720p_.mkv", clean)
        assertFalse(clean.contains(":"))
        assertFalse(clean.contains("*"))
        assertFalse(clean.contains("?"))
        assertFalse(clean.contains("|"))
    }

    @Test
    fun testSafeUrlValidation() {
        assertTrue(SecuritySanitizer.isValidWebUrl("https://example.com/stream.m3u8"))
        assertTrue(SecuritySanitizer.isValidWebUrl("http://example.com/video.mp4"))

        assertFalse(SecuritySanitizer.isValidWebUrl("javascript:alert(1)"))
        assertFalse(SecuritySanitizer.isValidWebUrl("file:///etc/hosts"))
        assertFalse(SecuritySanitizer.isValidWebUrl("data:text/html;base64,PHNjcmlwdD4="))
        assertFalse(SecuritySanitizer.isValidWebUrl(""))
        assertFalse(SecuritySanitizer.isValidWebUrl(null))
    }

    @Test
    fun testMagnetLinkValidation() {
        assertTrue(SecuritySanitizer.isSafeMagnetOrTorrent("magnet:?xt=urn:btih:d14f4e24a5697669d675aa6e87900b3f815858cf&dn=Ubuntu"))
        assertTrue(SecuritySanitizer.isSafeMagnetOrTorrent("https://webtorrent.io/torrents/sintel.torrent"))

        assertFalse(SecuritySanitizer.isSafeMagnetOrTorrent("http://malicious.site/script.sh"))
        assertFalse(SecuritySanitizer.isSafeMagnetOrTorrent("magnet:?fake=invalid"))
    }
}
