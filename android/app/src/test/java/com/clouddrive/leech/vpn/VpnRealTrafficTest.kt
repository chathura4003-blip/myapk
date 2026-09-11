package com.clouddrive.leech.vpn

import com.clouddrive.leech.vpn.core.XrayConfigBuilder
import com.clouddrive.leech.vpn.net.ConnectivityTester
import com.clouddrive.leech.vpn.net.ProbeResult
import com.clouddrive.leech.vpn.net.TcpCheckResult
import com.clouddrive.leech.vpn.parser.ParseResult
import com.clouddrive.leech.vpn.parser.VlessParser
import com.clouddrive.leech.vpn.routing.VpnRoutingConfig
import com.clouddrive.leech.vpn.routing.VpnRoutingMode
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VpnRealTrafficTest {

    private val userVlessPayload = "vless://ee7feec1-6ae5-4a2e-aa18-9346bd2fc3cf@sg.connfull.org:9443?type=ws&encryption=none&path=%2Fstream&host=sg.connfull.org&security=tls&fp=chrome&alpn=h2%2Chttp%2F1.1#sg.connfull.org-racevpn-kqd8oe"

    @Test
    fun testParseVlessPayload() = runBlocking {
        println("=== 1. Testing VlessParser ===")
        val result = VlessParser.parse(userVlessPayload)
        assertTrue("VlessParser must return ParseResult.Success", result is ParseResult.Success)
        val config = (result as ParseResult.Success).config
        assertEquals("VLESS", config.protocol)
        assertEquals("sg.connfull.org", config.host)
        assertEquals(9443, config.port)
        assertEquals("ee7feec1-6ae5-4a2e-aa18-9346bd2fc3cf", config.uuid)
        assertEquals("/stream", config.wsPath)
        assertEquals("tls", config.security)
        println("SUCCESS: Parsed config: ${config.host}:${config.port}, UUID: ${config.uuid}, Path: ${config.wsPath}")
    }

    @Test
    fun testStageATcpReachability() = runBlocking {
        println("=== 2. Testing Stage A: TCP Reachability ===")
        val result = VlessParser.parse(userVlessPayload) as ParseResult.Success
        val tcpRes = ConnectivityTester.checkTcpReachability(result.config, timeoutMs = 8000)
        println("TCP Reachability result: $tcpRes")
    }

    @Test
    fun testStageBProtocolHandshake() = runBlocking {
        println("=== 3. Testing Stage B: TLS / SNI Protocol Handshake ===")
        val result = VlessParser.parse(userVlessPayload) as ParseResult.Success
        val hsRes = ConnectivityTester.testProtocolHandshake(result.config)
        println("Protocol Handshake result: $hsRes")
    }

    @Test
    fun testXrayConfigGeneration() {
        println("=== 4. Testing Xray Config Builder ===")
        val result = runBlocking { VlessParser.parse(userVlessPayload) } as ParseResult.Success
        val routing = VpnRoutingConfig(mode = VpnRoutingMode.FULL_VPN)
        val json = XrayConfigBuilder.buildConfig(result.config, routing, enableTunInbound = true)
        assertTrue(json.contains("xray0"))
        assertTrue(json.contains("10808"))
        assertTrue(json.contains("10809"))
        println("SUCCESS: Generated valid Xray JSON config")
    }
}
