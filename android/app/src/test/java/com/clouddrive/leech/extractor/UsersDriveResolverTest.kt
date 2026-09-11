package com.clouddrive.leech.extractor

import com.clouddrive.leech.extractor.resolvers.UsersDriveResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UsersDriveResolverTest {

    // TEST 1 & TEST 20: UsersDrive HTML with <a class="btn btn-download" href="...">Click To Download</a>
    @Test
    fun testExtractionFromTargetHtml() {
        val sampleHtml = """
            <!DOCTYPE html>
            <html>
            <body>
                <div class="download-box">
                    <a class="btn btn-download" href="https://d300.userdrive.org:8443/d/cqmhg5rjpgst_n/movie.mp4?ffp=a8f7c6e5">Click To Download</a>
                </div>
            </body>
            </html>
        """.trimIndent()

        val extracted = UsersDriveResolver.extractCandidateFromHtml(sampleHtml)
        assertNotNull("Should extract candidate direct download link", extracted)
        assertEquals(
            "https://d300.userdrive.org:8443/d/cqmhg5rjpgst_n/movie.mp4?ffp=a8f7c6e5",
            extracted
        )
    }

    // TEST 2: Whitespace around href
    @Test
    fun testWhitespaceNormalization() {
        val rawCandidate = "   https://d300.userdrive.org:8443/d/abc/video.mp4?ffp=123   \n"
        val validated = UsersDriveResolver.validateCandidateDirectUrl(rawCandidate)
        assertNotNull(validated)
        assertEquals("https://d300.userdrive.org:8443/d/abc/video.mp4?ffp=123", validated)

        val rawLanding = "  \t https://usersdrive.com/cqmhg5rjpgst.html \r\n "
        val normalized = UsersDriveResolver.normalizeAndClassifyUrl(rawLanding)
        assertNotNull(normalized)
        assertEquals("https://usersdrive.com/cqmhg5rjpgst.html", normalized)
    }

    // TEST 3: HTML encoded URL
    @Test
    fun testHtmlEncodedUrl() {
        val encodedCandidate = "https://d300.userdrive.org:8443/d/test/movie.mp4?ffp=123&amp;token=abc&amp;expires=999"
        val validated = UsersDriveResolver.validateCandidateDirectUrl(encodedCandidate)
        assertNotNull(validated)
        assertEquals("https://d300.userdrive.org:8443/d/test/movie.mp4?ffp=123&token=abc&expires=999", validated)
    }

    // TEST 4: Port preservation
    @Test
    fun testPortPreserved() {
        val urlWithPort = "https://d300.userdrive.org:8443/d/cqmhg5rjpgst/movie.mp4"
        val validated = UsersDriveResolver.validateCandidateDirectUrl(urlWithPort)
        assertNotNull(validated)
        assertTrue("Port 8443 must be preserved", validated!!.contains(":8443"))
    }

    // TEST 5: Query parameters preserved without truncation
    @Test
    fun testQueryParamsPreserved() {
        val urlWithParams = "https://d300.userdrive.org:8443/d/xyz/movie.mp4?ffp=a8f7c6e5b4a3&sig=98765"
        val validated = UsersDriveResolver.validateCandidateDirectUrl(urlWithParams)
        assertNotNull(validated)
        assertEquals(urlWithParams, validated)
    }

    // TEST 6: Multiple anchors (Download button preferred over social/ads)
    @Test
    fun testMultipleAnchorsFiltering() {
        val htmlWithMultipleAnchors = """
            <div>
                <a href="https://facebook.com/share?url=abc">Share on Facebook</a>
                <a href="https://t.me/share/url?url=abc">Telegram Channel</a>
                <a href="javascript:void(0)">Play Video</a>
                <a class="nav-link" href="https://usersdrive.com/login.html">Login</a>
                <a class="btn btn-download" href="https://d300.userdrive.org:8443/d/direct_media/video.mp4?ffp=112233">Click To Download</a>
                <a href="https://usersdrive.com/faq.html">FAQ</a>
            </div>
        """.trimIndent()

        val extracted = UsersDriveResolver.extractCandidateFromHtml(htmlWithMultipleAnchors)
        assertNotNull("Should prioritize download anchor over social and nav links", extracted)
        assertEquals("https://d300.userdrive.org:8443/d/direct_media/video.mp4?ffp=112233", extracted)
    }

    // TEST 8: Invalid URL classification
    @Test
    fun testInvalidUrlsRejected() {
        assertNull("javascript: URI must be rejected", UsersDriveResolver.normalizeAndClassifyUrl("javascript:alert(1)"))
        assertNull("file: URI must be rejected", UsersDriveResolver.normalizeAndClassifyUrl("file:///etc/passwd"))
        assertNull("content: URI must be rejected", UsersDriveResolver.normalizeAndClassifyUrl("content://media/external"))
        assertNull("Empty URL must be rejected", UsersDriveResolver.normalizeAndClassifyUrl(""))
        assertNull("Unsupported host must be rejected", UsersDriveResolver.normalizeAndClassifyUrl("https://evil-hacker.com/download.html"))
        assertNull("Login URL must not be classified as landing page", UsersDriveResolver.normalizeAndClassifyUrl("https://usersdrive.com/login.html"))
    }

    // TEST 9: No direct link
    @Test
    fun testNoDirectLinkReturnsNull() {
        val emptyPage = "<html><body><h1>File Not Found</h1><p>The file has been deleted.</p></body></html>"
        val extracted = UsersDriveResolver.extractCandidateFromHtml(emptyPage)
        assertNull("Should return null when no direct download link is exposed", extracted)
    }

    // TEST 14 & PHASE 15: SSRF Security Validation
    @Test
    fun testSsrfProtection() {
        assertTrue("localhost must be blocked", UsersDriveResolver.isPrivateOrReservedHost("localhost"))
        assertTrue("127.0.0.1 must be blocked", UsersDriveResolver.isPrivateOrReservedHost("127.0.0.1"))
        assertTrue("10.0.0.5 must be blocked", UsersDriveResolver.isPrivateOrReservedHost("10.0.0.5"))
        assertTrue("172.16.0.1 must be blocked", UsersDriveResolver.isPrivateOrReservedHost("172.16.0.1"))
        assertTrue("172.31.255.255 must be blocked", UsersDriveResolver.isPrivateOrReservedHost("172.31.255.255"))
        assertTrue("192.168.1.100 must be blocked", UsersDriveResolver.isPrivateOrReservedHost("192.168.1.100"))
        assertTrue("169.254.169.254 must be blocked", UsersDriveResolver.isPrivateOrReservedHost("169.254.169.254"))
        assertTrue("IPv6 loopback must be blocked", UsersDriveResolver.isPrivateOrReservedHost("::1"))

        assertFalse("Public host userdrive.org must be allowed", UsersDriveResolver.isPrivateOrReservedHost("d300.userdrive.org"))
        assertFalse("Public host usersdrive.com must be allowed", UsersDriveResolver.isPrivateOrReservedHost("usersdrive.com"))
    }

    // Landing Page Classifier
    @Test
    fun testCanHandleLandingPages() {
        assertTrue(UsersDriveResolver.canHandle("https://usersdrive.com/cqmhg5rjpgst.html"))
        assertTrue(UsersDriveResolver.canHandle("http://usersdrive.com/cqmhg5rjpgst.html"))
        assertTrue(UsersDriveResolver.canHandle("https://userdrive.org/abc123xyz.html"))

        // Direct media links should not trigger the HTML scraper
        assertFalse(UsersDriveResolver.canHandle("https://d300.userdrive.org:8443/d/cqmhg5rjpgst/movie.mp4?ffp=123"))
        assertTrue(UsersDriveResolver.isAlreadyDirectMediaUrl("https://d300.userdrive.org:8443/d/cqmhg5rjpgst/movie.mp4?ffp=123"))
    }
}
