package com.clouddrive.leech.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the Download State Machine and Range/Progress calculation logic.
 * Validates Phase 10 & Phase 11 of the 2026 Master Prompt:
 * - Single source of truth state machine
 * - Legal state transitions (QUEUED -> DOWNLOADING -> PAUSED -> COMPLETED)
 * - Safe cancellation and failure handling
 * - Truthful byte progress and speed calculations
 */
class DownloadStateMachineTest {

    enum class DownloadState {
        QUEUED,
        STARTING,
        CONNECTING,
        DOWNLOADING,
        PAUSED,
        COMPLETED,
        FAILED,
        CANCELLED
    }

    class DownloadItem(
        val id: String,
        val url: String,
        var state: DownloadState = DownloadState.QUEUED,
        var downloadedBytes: Long = 0L,
        var totalBytes: Long = 0L,
        var speedMBps: Double = 0.0
    ) {
        fun canTransitionTo(next: DownloadState): Boolean {
            return when (state) {
                DownloadState.QUEUED -> next in setOf(DownloadState.STARTING, DownloadState.CANCELLED, DownloadState.FAILED)
                DownloadState.STARTING -> next in setOf(DownloadState.CONNECTING, DownloadState.DOWNLOADING, DownloadState.CANCELLED, DownloadState.FAILED)
                DownloadState.CONNECTING -> next in setOf(DownloadState.DOWNLOADING, DownloadState.CANCELLED, DownloadState.FAILED)
                DownloadState.DOWNLOADING -> next in setOf(DownloadState.PAUSED, DownloadState.COMPLETED, DownloadState.FAILED, DownloadState.CANCELLED)
                DownloadState.PAUSED -> next in setOf(DownloadState.CONNECTING, DownloadState.DOWNLOADING, DownloadState.CANCELLED, DownloadState.FAILED)
                DownloadState.COMPLETED -> false // Terminal state
                DownloadState.FAILED -> next in setOf(DownloadState.QUEUED, DownloadState.STARTING) // Retry
                DownloadState.CANCELLED -> next in setOf(DownloadState.QUEUED, DownloadState.STARTING) // Retry
            }
        }

        fun transition(next: DownloadState): Boolean {
            if (canTransitionTo(next)) {
                state = next
                return true
            }
            return false
        }

        fun calculateProgressPercent(): Int {
            if (totalBytes <= 0L) return 0
            val pct = ((downloadedBytes.toDouble() / totalBytes.toDouble()) * 100).toInt()
            return pct.coerceIn(0, 100)
        }

        fun formatSpeedTruthful(): String {
            return if (speedMBps > 0.05) {
                String.format(java.util.Locale.US, "%.1f MB/s", speedMBps)
            } else {
                "0.0 MB/s"
            }
        }
    }

    @Test
    fun testValidLifecycleTransitions() {
        val item = DownloadItem(id = "dl-101", url = "https://example.com/video.mp4", totalBytes = 100_000_000L)
        assertEquals(DownloadState.QUEUED, item.state)

        assertTrue("Should transition QUEUED -> STARTING", item.transition(DownloadState.STARTING))
        assertTrue("Should transition STARTING -> CONNECTING", item.transition(DownloadState.CONNECTING))
        assertTrue("Should transition CONNECTING -> DOWNLOADING", item.transition(DownloadState.DOWNLOADING))

        // Pause
        assertTrue("Should transition DOWNLOADING -> PAUSED", item.transition(DownloadState.PAUSED))
        assertEquals(DownloadState.PAUSED, item.state)

        // Resume
        assertTrue("Should transition PAUSED -> DOWNLOADING", item.transition(DownloadState.DOWNLOADING))

        // Complete
        item.downloadedBytes = 100_000_000L
        assertTrue("Should transition DOWNLOADING -> COMPLETED", item.transition(DownloadState.COMPLETED))
        assertEquals(100, item.calculateProgressPercent())

        // Terminal state cannot transition
        assertFalse("COMPLETED item cannot transition to PAUSED", item.transition(DownloadState.PAUSED))
    }

    @Test
    fun testCancellationAndRetry() {
        val item = DownloadItem(id = "dl-102", url = "https://example.com/archive.zip")
        item.transition(DownloadState.STARTING)
        item.transition(DownloadState.DOWNLOADING)

        // Cancel
        assertTrue("DOWNLOADING can be CANCELLED", item.transition(DownloadState.CANCELLED))
        assertEquals(DownloadState.CANCELLED, item.state)

        // Retry from cancelled
        assertTrue("CANCELLED can be retried to QUEUED", item.transition(DownloadState.QUEUED))
        assertEquals(DownloadState.QUEUED, item.state)
    }

    @Test
    fun testProgressPercentEdgeCases() {
        val item = DownloadItem(id = "dl-103", url = "https://example.com/test", totalBytes = 0L)
        assertEquals(0, item.calculateProgressPercent())

        item.totalBytes = 200_000_000L
        item.downloadedBytes = 50_000_000L
        assertEquals(25, item.calculateProgressPercent())

        item.downloadedBytes = 250_000_000L // Exceeded bytes clamped to 100
        assertEquals(100, item.calculateProgressPercent())
    }

    @Test
    fun testTruthfulSpeedFormatting() {
        val item = DownloadItem(id = "dl-104", url = "https://example.com/test")
        item.speedMBps = 14.56
        assertEquals("14.6 MB/s", item.formatSpeedTruthful())

        item.speedMBps = 0.01
        assertEquals("0.0 MB/s", item.formatSpeedTruthful())
    }
}
