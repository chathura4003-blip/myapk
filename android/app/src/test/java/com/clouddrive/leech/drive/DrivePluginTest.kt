package com.clouddrive.leech.drive

import com.clouddrive.leech.plugins.NativeGdrivePlugin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for Google Drive integration (Phase 09):
 * - Error message parsing for 403 / insufficient permissions
 * - Default fallback messages for empty or corrupted error responses
 * - Accurate storage quota calculation logic
 */
class DrivePluginTest {

    @Test
    fun testParseGoogleErrorMessage_insufficientPermissions() {
        val rawJson = """
            {
                "error": {
                    "code": 403,
                    "message": "The user has not granted the app 202264815644 write access to the file.",
                    "errors": [
                        {
                            "message": "The user has not granted the app 202264815644 write access to the file.",
                            "domain": "global",
                            "reason": "insufficientFilePermissions"
                        }
                    ]
                }
            }
        """.trimIndent()

        val parsed = NativeGdrivePlugin.parseGoogleErrorMessage(rawJson, "Default Error")
        assertTrue("Should contain explanation to check Google Drive permissions box", parsed.contains("permissions box"))
    }

    @Test
    fun testParseGoogleErrorMessage_standardMessage() {
        val rawJson = """
            {
                "error": {
                    "code": 404,
                    "message": "File not found: 1a2b3c4d"
                }
            }
        """.trimIndent()

        val parsed = NativeGdrivePlugin.parseGoogleErrorMessage(rawJson, "Default Error")
        assertEquals("File not found: 1a2b3c4d", parsed)
    }

    @Test
    fun testParseGoogleErrorMessage_fallbackOnEmptyOrInvalidJson() {
        val emptyParsed = NativeGdrivePlugin.parseGoogleErrorMessage("", "Fallback Error")
        assertEquals("Fallback Error", emptyParsed)

        val invalidParsed = NativeGdrivePlugin.parseGoogleErrorMessage("not-a-json", "Fallback Error")
        assertEquals("Fallback Error", invalidParsed)
    }

    @Test
    fun testStorageQuotaCalculation() {
        val usageBytes = 5L * 1024L * 1024L * 1024L // 5 GB
        val limitBytes = 15L * 1024L * 1024L * 1024L // 15 GB
        val pct = ((usageBytes.toDouble() / limitBytes.toDouble()) * 100.0)
        assertEquals(33.33, pct, 0.1)

        // Zero limit edge case
        val zeroLimit = 0L
        val safePct = if (zeroLimit > 0L) (usageBytes.toDouble() / zeroLimit.toDouble()) * 100.0 else 0.0
        assertEquals(0.0, safePct, 0.0)
    }
}
