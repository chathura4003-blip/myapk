package com.clouddrive.leech.vpn

import com.clouddrive.leech.vpn.parser.ParseResult
import com.clouddrive.leech.vpn.parser.VlessParser
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VlessAutoRepairTest {

    @Test
    fun testUnhyphenatedUuidAutoRepair() = runBlocking {
        // 32-hex character UUID without hyphens
        val raw = "vless://ee7feec16ae54a2eaa189346bd2fc3cf@example.com:443?type=tcp&security=tls#TestAutoFix"
        val res = VlessParser.parse(raw)
        assertTrue(res is ParseResult.Success)
        val config = (res as ParseResult.Success).config
        assertEquals("ee7feec1-6ae5-4a2e-aa18-9346bd2fc3cf", config.uuid)
        assertTrue(config.autoFixes.any { it.contains("RFC 4122") })
    }

    @Test
    fun testMissingPortAutoRepair() = runBlocking {
        // No port specified, TLS enabled -> should default to 443
        val raw = "vless://ee7feec1-6ae5-4a2e-aa18-9346bd2fc3cf@example.com?type=ws&security=tls#TestPort"
        val res = VlessParser.parse(raw)
        assertTrue(res is ParseResult.Success)
        val config = (res as ParseResult.Success).config
        assertEquals(443, config.port)
        assertTrue(config.autoFixes.any { it.contains("Auto-assigned port") })
    }

    @Test
    fun testRealityAutoDetection() = runBlocking {
        // Has pbk parameter but security is not reality -> auto promote to reality
        val raw = "vless://ee7feec1-6ae5-4a2e-aa18-9346bd2fc3cf@example.com:443?type=tcp&pbk=bm90YXJlYWxwdWJsaWNrZXk=&sni=example.com#TestReality"
        val res = VlessParser.parse(raw)
        assertTrue(res is ParseResult.Success)
        val config = (res as ParseResult.Success).config
        assertEquals("reality", config.security)
        assertTrue(config.autoFixes.any { it.contains("Auto-detected Reality security") })
    }

    @Test
    fun testIncompatibleFlowStripped() = runBlocking {
        // xtls-rprx-vision is not compatible with websocket -> auto strip
        val raw = "vless://ee7feec1-6ae5-4a2e-aa18-9346bd2fc3cf@example.com:443?type=ws&security=tls&flow=xtls-rprx-vision&path=/stream#TestFlow"
        val res = VlessParser.parse(raw)
        assertTrue(res is ParseResult.Success)
        val config = (res as ParseResult.Success).config
        assertEquals("", config.flow)
        assertTrue(config.autoFixes.any { it.contains("Removed incompatible flow") })
    }

    @Test
    fun testWsLeadingSlashAdded() = runBlocking {
        // ws path missing leading slash
        val raw = "vless://ee7feec1-6ae5-4a2e-aa18-9346bd2fc3cf@example.com:443?type=ws&security=tls&path=stream#TestSlash"
        val res = VlessParser.parse(raw)
        assertTrue(res is ParseResult.Success)
        val config = (res as ParseResult.Success).config
        assertEquals("/stream", config.wsPath)
        assertTrue(config.autoFixes.any { it.contains("Prepended leading slash") })
    }

    @Test
    fun testMissingSniAutoDerived() = runBlocking {
        // TLS enabled, but sni missing -> derived from host
        val raw = "vless://ee7feec1-6ae5-4a2e-aa18-9346bd2fc3cf@my-server.com:443?type=tcp&security=tls#TestSni"
        val res = VlessParser.parse(raw)
        assertTrue(res is ParseResult.Success)
        val config = (res as ParseResult.Success).config
        assertEquals("my-server.com", config.sni)
        assertTrue(config.autoFixes.any { it.contains("Auto-derived SNI") })
    }
}
