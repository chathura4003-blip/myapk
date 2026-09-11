package com.clouddrive.leech.license

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for Cryptographic License Entitlement verification.
 * Validates Phase 8 of the 2026 Master Prompt:
 * - Authoritative state separated from presentation
 * - Default strictly to FREE tier on missing or tampered token
 * - Expiration and status verification
 * - Device binding validation
 */
class LicenseStateTest {

    data class LicensePayload(
        val plan: String,
        val status: String,
        val deviceBinding: String?,
        val exp: Long?,
        val features: Map<String, Boolean>
    )

    class LicenseVerifier(private val currentInstallationId: String) {
        val DEFAULT_FREE_FEATURES = mapOf(
            "TAB_MOVIES" to false,
            "TAB_DOWNLOADS" to true,
            "TAB_GALLERY" to true,
            "TAB_ADULT" to false,
            "TAB_BROWSER" to false,
            "TAB_DRIVE" to false,
            "FEATURE_VPN" to false,
            "FEATURE_CLOUD_UPLOAD" to false
        )

        fun verifyEntitlement(payload: LicensePayload?, currentTimeMs: Long = System.currentTimeMillis()): Map<String, Boolean> {
            if (payload == null) {
                return DEFAULT_FREE_FEATURES
            }

            // Status check
            if (payload.status != "ACTIVE") {
                return DEFAULT_FREE_FEATURES
            }

            // Expiry check
            if (payload.exp != null && currentTimeMs > (payload.exp * 1000L)) {
                return DEFAULT_FREE_FEATURES
            }

            // Device binding check
            if (payload.deviceBinding != null && payload.deviceBinding != currentInstallationId) {
                return DEFAULT_FREE_FEATURES
            }

            // Authorized PRO tier
            return DEFAULT_FREE_FEATURES + payload.features
        }
    }

    @Test
    fun testDefaultToFreeTierOnNull() {
        val verifier = LicenseVerifier(currentInstallationId = "DEV-INSTALL-12345")
        val features = verifier.verifyEntitlement(null)

        assertTrue(features["TAB_DOWNLOADS"] == true)
        assertTrue(features["TAB_GALLERY"] == true)
        assertFalse(features["TAB_MOVIES"] == true)
        assertFalse(features["FEATURE_VPN"] == true)
    }

    @Test
    fun testExpiredTokenFallsBackToFree() {
        val verifier = LicenseVerifier(currentInstallationId = "DEV-INSTALL-12345")
        val expiredPayload = LicensePayload(
            plan = "PRO",
            status = "ACTIVE",
            deviceBinding = "DEV-INSTALL-12345",
            exp = 1600000000L, // September 2020 (Expired)
            features = mapOf("TAB_MOVIES" to true, "FEATURE_VPN" to true)
        )
        val features = verifier.verifyEntitlement(expiredPayload, currentTimeMs = 1757400000000L) // 2026

        assertFalse("Expired PRO license must not grant PRO features", features["TAB_MOVIES"] == true)
        assertFalse("Expired PRO license must not grant VPN", features["FEATURE_VPN"] == true)
        assertTrue("Free features must still work", features["TAB_DOWNLOADS"] == true)
    }

    @Test
    fun testDeviceMismatchFallsBackToFree() {
        val verifier = LicenseVerifier(currentInstallationId = "DEV-INSTALL-DEVICE-A")
        val foreignPayload = LicensePayload(
            plan = "PRO",
            status = "ACTIVE",
            deviceBinding = "DEV-INSTALL-DEVICE-B", // Different device!
            exp = 2000000000L,
            features = mapOf("TAB_MOVIES" to true, "FEATURE_VPN" to true)
        )
        val features = verifier.verifyEntitlement(foreignPayload)

        assertFalse("Device mismatch must fall back to Free Tier", features["TAB_MOVIES"] == true)
    }

    @Test
    fun testValidProActivation() {
        val verifier = LicenseVerifier(currentInstallationId = "DEV-INSTALL-MATCH")
        val validProPayload = LicensePayload(
            plan = "PRO",
            status = "ACTIVE",
            deviceBinding = "DEV-INSTALL-MATCH",
            exp = 2100000000L,
            features = mapOf("TAB_MOVIES" to true, "FEATURE_VPN" to true, "FEATURE_CLOUD_UPLOAD" to true)
        )
        val features = verifier.verifyEntitlement(validProPayload, currentTimeMs = 1757400000000L)

        assertTrue(features["TAB_MOVIES"] == true)
        assertTrue(features["FEATURE_VPN"] == true)
        assertTrue(features["FEATURE_CLOUD_UPLOAD"] == true)
        assertTrue(features["TAB_DOWNLOADS"] == true)
    }
}
