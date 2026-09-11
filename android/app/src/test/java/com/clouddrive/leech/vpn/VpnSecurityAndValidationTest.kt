package com.clouddrive.leech.vpn

import com.clouddrive.leech.vpn.model.VpnStatus
import com.clouddrive.leech.vpn.model.VpnRuntimeState
import com.clouddrive.leech.vpn.parser.ParseResult
import com.clouddrive.leech.vpn.parser.VlessParser
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests validating Phase 10 network security constraints:
 * - Real error states on invalid VLESS configurations
 * - Truthful VPN status mappings
 * - Strict schema validation (URI, UUID, host, port, security)
 */
class VpnSecurityAndValidationTest {

    @Test
    fun testInvalidPrefixReturnsError() = runBlocking {
        val raw = "http://example.com/not-a-vpn-payload"
        val res = VlessParser.parse(raw)
        assertTrue("Invalid protocol prefix must return ParseResult.Error", res is ParseResult.Error)
        val err = res as ParseResult.Error
        assertEquals("UNSUPPORTED_SCHEME", err.code)
    }

    @Test
    fun testEmptyPayloadReturnsError() = runBlocking {
        val res = VlessParser.parse("")
        assertTrue("Empty payload must return ParseResult.Error", res is ParseResult.Error)
        val err = res as ParseResult.Error
        assertEquals("EMPTY_PAYLOAD", err.code)
    }

    @Test
    fun testInvalidUuidReturnsError() = runBlocking {
        val raw = "vless://invalid-uuid-format@example.com:443?type=tcp&security=tls#Test"
        val res = VlessParser.parse(raw)
        assertTrue("Invalid UUID that cannot be repaired must return ParseResult.Error", res is ParseResult.Error)
        val err = res as ParseResult.Error
        assertEquals("INVALID_UUID", err.code)
    }

    @Test
    fun testMissingRealityPublicKeyReturnsError() = runBlocking {
        val raw = "vless://ee7feec1-6ae5-4a2e-aa18-9346bd2fc3cf@example.com:443?type=tcp&security=reality#Test"
        val res = VlessParser.parse(raw)
        assertTrue("Reality without public key must return ParseResult.Error", res is ParseResult.Error)
        val err = res as ParseResult.Error
        assertEquals("INVALID_REALITY", err.code)
    }

    @Test
    fun testMissingHostReturnsError() = runBlocking {
        val raw = "vless://ee7feec1-6ae5-4a2e-aa18-9346bd2fc3cf@:443?type=tcp&security=tls#Test"
        val res = VlessParser.parse(raw)
        assertTrue("Missing server host must return ParseResult.Error", res is ParseResult.Error)
        val err = res as ParseResult.Error
        assertEquals("INVALID_HOST", err.code)
    }

    @Test
    fun testVpnRuntimeStateTruthfulRunningState() {
        val idleState = VpnRuntimeState(status = VpnStatus.IDLE)
        assertFalse(idleState.isRunning())

        val connectingState = VpnRuntimeState(status = VpnStatus.CONNECTIVITY_TEST)
        assertFalse(connectingState.isRunning())

        val connectedState = VpnRuntimeState(status = VpnStatus.CONNECTED)
        assertTrue(connectedState.isRunning())

        val disconnectedState = VpnRuntimeState(status = VpnStatus.DISCONNECTED)
        assertFalse(disconnectedState.isRunning())

        val failedState = VpnRuntimeState(status = VpnStatus.FAILED)
        assertFalse(failedState.isRunning())
    }
}
