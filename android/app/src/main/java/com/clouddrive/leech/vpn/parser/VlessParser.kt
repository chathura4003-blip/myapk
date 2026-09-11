package com.clouddrive.leech.vpn.parser

import android.util.Base64
import com.clouddrive.leech.vpn.model.VlessConfig
import com.clouddrive.leech.vpn.util.GeoIpResolver
import org.json.JSONObject
import java.net.URLDecoder

sealed class ParseResult {
    data class Success(val config: VlessConfig) : ParseResult()
    data class Error(val code: String, val message: String) : ParseResult()
}

object VlessParser {

    private val UUID_REGEX = Regex("""^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$""")
    private val HOST_REGEX = Regex("""^([a-zA-Z0-9.-]+|\[[a-fA-F0-9:]+])$""")
    private val SUPPORTED_PREFIXES = listOf(
        "vless://", "vmess://", "trojan://", "ss://", "ssr://", "tuic://", "hy2://", "hysteria2://", "hysteria://"
    )

    /**
     * Decodes Base64 strings safely (supports standard Base64, URL-safe Base64, and missing padding).
     */
    fun safeBase64Decode(raw: String): String {
        var s = raw.trim().replace("\r", "").replace("\n", "").replace(" ", "")
        if (s.isEmpty()) return ""
        s = s.replace('-', '+').replace('_', '/')
        val mod = s.length % 4
        if (mod > 0) {
            s += "=".repeat(4 - mod)
        }
        // Try standard Java 8+ Base64 first (works seamlessly on JVM and modern Android)
        try {
            val bytes = java.util.Base64.getDecoder().decode(s)
            if (bytes != null && bytes.isNotEmpty()) {
                return String(bytes, Charsets.UTF_8)
            }
        } catch (_: Throwable) {}

        // Fallback to Android Base64
        return try {
            val bytes = Base64.decode(s, Base64.DEFAULT or Base64.NO_WRAP or Base64.URL_SAFE)
            if (bytes != null) String(bytes, Charsets.UTF_8) else ""
        } catch (_: Throwable) {
            ""
        }
    }

    /**
     * Cleans raw input, removing surrounding quotes or backticks,
     * invisible zero-width unicode characters, HTML entities,
     * and picking the first matching protocol line if multi-line text or Base64 subscription is supplied.
     */
    fun cleanRawInput(raw: String): String {
        var s = raw.trim()
            .replace("\u200B", "") // Zero-width space
            .replace("\u200C", "") // Zero-width non-joiner
            .replace("\u200D", "") // Zero-width joiner
            .replace("\uFEFF", "") // Zero-width byte order mark
            .replace("\u00A0", " ") // Non-breaking space
            .replace("&amp;", "&")
            .replace("&#38;", "&")
            .replace("&quot;", "\"")

        if ((s.startsWith("\"") && s.endsWith("\"")) ||
            (s.startsWith("'") && s.endsWith("'")) ||
            (s.startsWith("`") && s.endsWith("`"))) {
            s = s.substring(1, s.length - 1).trim()
        }

        // If the entire text is a Base64 string (e.g. subscription response), try decoding first
        if (!SUPPORTED_PREFIXES.any { s.startsWith(it, ignoreCase = true) }) {
            val decoded = safeBase64Decode(s)
            if (decoded.isNotBlank() && SUPPORTED_PREFIXES.any { decoded.contains(it, ignoreCase = true) }) {
                s = decoded
            }
        }

        if (s.contains("\n") || s.contains("\r")) {
            val lines = s.split("\r\n", "\n", "\r").map { it.trim() }
            val candidate = lines.firstOrNull { line ->
                SUPPORTED_PREFIXES.any { line.startsWith(it, ignoreCase = true) }
            }
            if (candidate != null) {
                s = candidate
            }
        }
        return s
    }

    fun isIpAddress(host: String): Boolean {
        val clean = host.removePrefix("[").removeSuffix("]").trim()
        val ipv4Regex = Regex("""^(\d{1,3}\.){3}\d{1,3}$""")
        if (ipv4Regex.matches(clean)) return true
        if (clean.contains(":")) return true // IPv6
        return false
    }

    /**
     * Auto-repairs non-hyphenated 32-character UUIDs into standard RFC 4122 (8-4-4-4-12) format.
     */
    fun normalizeUuid(rawUuid: String, fixes: MutableList<String>): String {
        var clean = rawUuid.trim()
            .removeSurrounding("{", "}")
            .removeSurrounding("\"", "\"")
            .removeSurrounding("'", "'")

        // 32 hex characters without hyphens (e.g. e5a84e6f982a4721a998c253503b41d4)
        if (clean.length == 32 && clean.all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }) {
            val formatted = "${clean.substring(0, 8)}-${clean.substring(8, 12)}-${clean.substring(12, 16)}-${clean.substring(16, 20)}-${clean.substring(20)}".lowercase()
            fixes.add("Auto-repaired 32-char UUID to RFC 4122 format ($formatted)")
            return formatted
        }
        return clean.lowercase()
    }

    /**
     * Auto-sanitizes hostname: removes http:// or https:// prefixes, removes trailing paths/slashes.
     */
    fun autoFixHost(rawHost: String, fixes: MutableList<String>): String {
        var h = rawHost.trim()
        if (h.startsWith("http://", ignoreCase = true)) {
            h = h.substring(7)
            fixes.add("Removed http:// prefix from host")
        } else if (h.startsWith("https://", ignoreCase = true)) {
            h = h.substring(8)
            fixes.add("Removed https:// prefix from host")
        }
        if (h.contains("/")) {
            val cleanHost = h.substringBefore("/")
            fixes.add("Trimmed trailing path from host '$h' -> '$cleanHost'")
            h = cleanHost
        }
        return h.trim()
    }

    /**
     * Auto-detects and repairs ports.
     */
    fun autoFixPort(rawPort: Int, defaultPort: Int, fixes: MutableList<String>): Int {
        if (rawPort in 1..65535) return rawPort
        fixes.add("Auto-assigned port $defaultPort (original port: $rawPort)")
        return defaultPort
    }

    /**
     * Auto-detects security mode and Reality configuration.
     */
    fun autoFixSecurity(rawSec: String, pbk: String, port: Int, sni: String, fixes: MutableList<String>): String {
        val sec = rawSec.trim().lowercase()
        if (pbk.isNotBlank() && sec != "reality") {
            fixes.add("Auto-detected Reality security from public key (pbk)")
            return "reality"
        }
        if ((sec == "none" || sec.isBlank()) && port == 443 && sni.isNotBlank()) {
            fixes.add("Auto-enabled TLS security for port 443 endpoint")
            return "tls"
        }
        if (sec.isBlank()) return "none"
        return sec
    }

    /**
     * Auto-fixes SNI: strips port if present, infers from domain host or CDN host.
     */
    fun autoFixSni(rawSni: String, host: String, wsHost: String, queryHost: String, fixes: MutableList<String>): String {
        var s = rawSni.trim()
        if (s.contains(":")) {
            val colonIdx = s.lastIndexOf(':')
            if (colonIdx > 0 && s.substring(colonIdx + 1).all { it.isDigit() }) {
                val clean = s.substring(0, colonIdx)
                fixes.add("Removed port from SNI: $clean")
                s = clean
            }
        }
        if (s.isBlank()) {
            if (!isIpAddress(host) && host.isNotBlank()) {
                s = host
                fixes.add("Auto-derived SNI from host: $s")
            } else if (wsHost.isNotBlank() && !isIpAddress(wsHost)) {
                s = wsHost
                fixes.add("Auto-derived SNI from CDN Host: $s")
            } else if (queryHost.isNotBlank() && !isIpAddress(queryHost)) {
                s = queryHost
                fixes.add("Auto-derived SNI from query host: $s")
            } else {
                s = host
            }
        }
        return s
    }

    /**
     * Auto-cleans incompatible flow values (flow is only supported on TCP + TLS/Reality).
     */
    fun autoFixFlow(flow: String, transport: String, security: String, fixes: MutableList<String>): String {
        val f = flow.trim()
        if (f.isBlank()) return ""
        val net = transport.lowercase()
        val sec = security.lowercase()
        if (net != "tcp" || (sec != "tls" && sec != "reality")) {
            fixes.add("Removed incompatible flow '$f' (flow requires TCP + TLS/Reality)")
            return ""
        }
        if (f.equals("xtls-rprx-vision-udp443", ignoreCase = true)) {
            fixes.add("Normalized flow alias to xtls-rprx-vision")
            return "xtls-rprx-vision"
        }
        return f
    }

    /**
     * Normalizes WebSocket path (defaults to /, ensures leading slash).
     */
    fun autoFixWsPath(path: String, fixes: MutableList<String>): String {
        var p = path.trim()
        if (p.isBlank()) return "/"
        if (!p.startsWith("/")) {
            p = "/$p"
            fixes.add("Prepended leading slash to WebSocket path: $p")
        }
        return p
    }

    /**
     * Pre-splits any #remark fragment cleanly BEFORE URI parsing,
     * ensuring emojis, spaces, and pipe characters do not trigger URISyntaxException.
     */
    private fun extractRemarkAndUri(raw: String): Pair<String, String> {
        val idx = raw.indexOf('#')
        return if (idx >= 0) {
            val uriPart = raw.substring(0, idx).trim()
            val rawFrag = raw.substring(idx + 1).trim()
            val remark = try {
                URLDecoder.decode(rawFrag, "UTF-8")
            } catch (_: Exception) {
                rawFrag
            }
            Pair(uriPart, remark)
        } else {
            Pair(raw.trim(), "")
        }
    }

    private data class UriComponents(
        val scheme: String,
        val userInfo: String,
        val host: String,
        val port: Int,
        val queryParams: Map<String, String>,
        val path: String = ""
    )

    private fun parseQueryParams(queryStr: String): Map<String, String> {
        if (queryStr.isBlank()) return emptyMap()
        val map = mutableMapOf<String, String>()
        queryStr.split("&").forEach { pair ->
            val idx = pair.indexOf('=')
            if (idx > 0) {
                val k = try { URLDecoder.decode(pair.substring(0, idx).trim(), "UTF-8").lowercase() } catch (_: Exception) { pair.substring(0, idx).trim().lowercase() }
                val v = try { URLDecoder.decode(pair.substring(idx + 1).trim(), "UTF-8") } catch (_: Exception) { pair.substring(idx + 1).trim() }
                map[k] = v
            } else if (pair.isNotBlank()) {
                val k = try { URLDecoder.decode(pair.trim(), "UTF-8").lowercase() } catch (_: Exception) { pair.trim().lowercase() }
                map[k] = ""
            }
        }
        return map
    }

    /**
     * Parses URI safely without using java.net.URI, completely preventing URISyntaxException.
     */
    private fun parseUriSafely(rawUri: String, defaultPort: Int = 0): UriComponents? {
        val schemeIdx = rawUri.indexOf("://")
        if (schemeIdx <= 0) return null
        val scheme = rawUri.substring(0, schemeIdx).lowercase()
        val remainder = rawUri.substring(schemeIdx + 3)

        val qIdx = remainder.indexOf('?')
        val pathHostPart = if (qIdx >= 0) remainder.substring(0, qIdx) else remainder
        val queryPart = if (qIdx >= 0) remainder.substring(qIdx + 1) else ""

        val queryMap = parseQueryParams(queryPart)

        val atIdx = pathHostPart.lastIndexOf('@')
        val userInfo: String
        val hostPortPath: String
        if (atIdx >= 0) {
            userInfo = pathHostPart.substring(0, atIdx).trim()
            hostPortPath = pathHostPart.substring(atIdx + 1).trim()
        } else {
            userInfo = ""
            hostPortPath = pathHostPart.trim()
        }

        val slashIdx = hostPortPath.indexOf('/')
        val hostPort = if (slashIdx >= 0) hostPortPath.substring(0, slashIdx) else hostPortPath
        val path = if (slashIdx >= 0) hostPortPath.substring(slashIdx) else ""

        val (host, port) = parseHostPort(hostPort, defaultPort)

        return UriComponents(scheme, userInfo, host, port, queryMap, path)
    }

    suspend fun parse(rawInput: String): ParseResult {
        val clean = cleanRawInput(rawInput)
        if (clean.isEmpty()) {
            return ParseResult.Error("EMPTY_PAYLOAD", "Payload cannot be empty")
        }

        if (clean.startsWith("vless://", ignoreCase = true)) {
            return parseVless(clean)
        }

        if (clean.startsWith("vmess://", ignoreCase = true)) {
            return parseVmess(clean)
        }

        if (clean.startsWith("trojan://", ignoreCase = true)) {
            return parseTrojan(clean)
        }

        if (clean.startsWith("ssr://", ignoreCase = true)) {
            return parseShadowsocksR(clean)
        }

        if (clean.startsWith("ss://", ignoreCase = true)) {
            return parseShadowsocks(clean)
        }

        if (clean.startsWith("tuic://", ignoreCase = true)) {
            return parseTuic(clean)
        }

        if (clean.startsWith("hysteria2://", ignoreCase = true) ||
            clean.startsWith("hy2://", ignoreCase = true) ||
            clean.startsWith("hysteria://", ignoreCase = true)) {
            return parseHysteria2(clean)
        }

        return ParseResult.Error(
            "UNSUPPORTED_SCHEME",
            "Unsupported URI scheme. Must start with ss://, ssr://, trojan://, vless://, vmess://, tuic://, or hy2:// / hysteria2://"
        )
    }

    private suspend fun parseVless(rawUri: String): ParseResult {
        return try {
            val fixes = mutableListOf<String>()
            val (baseUri, remarkFromFrag) = extractRemarkAndUri(rawUri)
            val comp = parseUriSafely(baseUri, 0) ?: return ParseResult.Error("MALFORMED_URI", "Invalid VLESS URI structure")

            val rawUuid = comp.userInfo
            if (rawUuid.isEmpty()) {
                return ParseResult.Error("INVALID_UUID", "Missing UUID in VLESS URI")
            }
            val uuid = normalizeUuid(rawUuid, fixes)
            if (!UUID_REGEX.matches(uuid)) {
                return ParseResult.Error("INVALID_UUID", "UUID does not conform to RFC 4122 standard format ($uuid)")
            }

            val host = autoFixHost(comp.host, fixes)
            if (host.isEmpty() || !HOST_REGEX.matches(host)) {
                return ParseResult.Error("INVALID_HOST", "Invalid or missing server hostname / IP: $host")
            }

            val queryMap = comp.queryParams
            val pbk = queryMap["pbk"] ?: ""
            val sid = queryMap["sid"] ?: ""
            val spx = queryMap["spx"] ?: ""
            val fp = queryMap["fp"]?.ifBlank { "chrome" } ?: "chrome"

            val security = autoFixSecurity(queryMap["security"] ?: "none", pbk, comp.port, queryMap["sni"] ?: "", fixes)
            val port = autoFixPort(comp.port, if (security == "reality" || security == "tls") 443 else 80, fixes)
            val rawTransport = queryMap["type"]?.lowercase() ?: (queryMap["net"]?.lowercase() ?: "tcp")
            val transport = when (rawTransport) {
                "websocket" -> { fixes.add("Normalized transport 'websocket' to 'ws'"); "ws" }
                "httpupgrade" -> { fixes.add("Normalized transport 'httpupgrade' to 'ws'"); "ws" }
                else -> rawTransport
            }

            val rawSni = queryMap["sni"] ?: (queryMap["peer"] ?: "")
            val wsHost = queryMap["host"] ?: ""
            val sni = autoFixSni(rawSni, host, wsHost, queryMap["host"] ?: "", fixes)

            val rawFlow = queryMap["flow"] ?: ""
            val flow = autoFixFlow(rawFlow, transport, security, fixes)

            val wsPath = autoFixWsPath(if (queryMap["path"].isNullOrBlank()) (comp.path.ifBlank { "/" }) else queryMap["path"]!!, fixes)
            val serviceName = queryMap["servicename"] ?: ""
            val encryption = queryMap["encryption"] ?: "none"
            val alpn = queryMap["alpn"] ?: ""

            // Validate Reality
            if (security == "reality" && pbk.isEmpty()) {
                return ParseResult.Error("INVALID_REALITY", "Reality security requires a valid public key (pbk)")
            }

            val remark = if (remarkFromFrag.isNotEmpty()) remarkFromFrag else "VLESS ($host:$port)"
            val (flag, code) = GeoIpResolver.resolveHostCountry(host, remark)

            ParseResult.Success(
                VlessConfig(
                    protocol = "VLESS",
                    uuid = uuid,
                    host = host,
                    port = port,
                    security = security,
                    sni = sni,
                    fingerprint = fp,
                    publicKey = pbk,
                    shortId = sid,
                    spiderX = spx,
                    flow = flow,
                    encryption = encryption,
                    alpn = alpn,
                    transport = transport,
                    wsPath = wsPath,
                    wsHost = if (wsHost.isNotBlank()) wsHost else host,
                    serviceName = serviceName,
                    remark = remark,
                    countryCode = code,
                    countryFlag = flag,
                    rawPayload = rawUri,
                    autoFixes = fixes
                )
            )
        } catch (e: Exception) {
            ParseResult.Error("PARSE_FAILED", "Failed to parse VLESS URI: ${e.message}")
        }
    }

    private suspend fun parseVmess(rawUri: String): ParseResult {
        return try {
            val fixes = mutableListOf<String>()
            val (baseUri, remarkFromFrag) = extractRemarkAndUri(rawUri)
            val b64 = baseUri.substring(8).trim()
            if (b64.isEmpty()) return ParseResult.Error("INVALID_PAYLOAD", "VMess payload is empty")
            val jsonStr = safeBase64Decode(b64)
            if (jsonStr.isBlank()) return ParseResult.Error("DECODE_FAILED", "Failed to decode VMess Base64 payload")
            val obj = JSONObject(jsonStr)

            val rawHost = obj.optString("add", "").trim()
            val host = autoFixHost(rawHost, fixes)

            val portInt = obj.optInt("port", 0)
            val rawPort = if (portInt > 0) portInt else (obj.optString("port", "0").toIntOrNull() ?: 0)

            val rawUuid = obj.optString("id", "").trim()
            val uuid = normalizeUuid(rawUuid, fixes)

            if (uuid.isEmpty() || !UUID_REGEX.matches(uuid)) {
                return ParseResult.Error("INVALID_UUID", "VMess UUID is invalid or missing ($uuid)")
            }
            if (host.isEmpty() || !HOST_REGEX.matches(host)) {
                return ParseResult.Error("INVALID_HOST", "VMess server hostname is invalid: $host")
            }

            val tlsVal = obj.opt("tls")?.toString()?.trim()?.lowercase() ?: "none"
            val security = if (tlsVal == "tls" || tlsVal == "true" || tlsVal == "1") "tls" else "none"
            val port = autoFixPort(rawPort, if (security == "tls") 443 else 80, fixes)

            val rawNet = obj.optString("net", "tcp").trim().lowercase().ifBlank { "tcp" }
            val net = when (rawNet) {
                "websocket" -> { fixes.add("Normalized transport 'websocket' to 'ws'"); "ws" }
                "httpupgrade" -> { fixes.add("Normalized transport 'httpupgrade' to 'ws'"); "ws" }
                else -> rawNet
            }

            val scy = obj.optString("scy", "auto").trim().lowercase().ifBlank { "auto" }
            val fp = obj.optString("fp", "chrome").trim().ifBlank { "chrome" }
            val alpn = obj.optString("alpn", "").trim()

            val rawSni = obj.optString("sni", "").trim()
            val rawHeaderHost = obj.optString("host", "").trim()
            val sni = autoFixSni(rawSni, host, rawHeaderHost, rawHeaderHost, fixes)

            val rawPath = obj.optString("path", "/").trim().ifBlank { "/" }
            val wsPath = autoFixWsPath(rawPath, fixes)
            val wsHost = if (rawHeaderHost.isNotEmpty()) rawHeaderHost else host
            val psRemark = obj.optString("ps", "").trim()
            val remark = when {
                remarkFromFrag.isNotEmpty() -> remarkFromFrag
                psRemark.isNotEmpty() -> psRemark
                else -> "VMess ($host:$port)"
            }

            val (flag, code) = GeoIpResolver.resolveHostCountry(host, remark)

            ParseResult.Success(
                VlessConfig(
                    protocol = "VMESS",
                    uuid = uuid,
                    host = host,
                    port = port,
                    security = security,
                    sni = sni,
                    fingerprint = fp,
                    encryption = scy,
                    alpn = alpn,
                    transport = net,
                    wsPath = wsPath,
                    wsHost = wsHost,
                    remark = remark,
                    countryCode = code,
                    countryFlag = flag,
                    rawPayload = rawUri,
                    autoFixes = fixes
                )
            )
        } catch (e: Exception) {
            ParseResult.Error("PARSE_FAILED", "Failed to parse VMess JSON: ${e.message}")
        }
    }

    private suspend fun parseTrojan(rawUri: String): ParseResult {
        return try {
            val fixes = mutableListOf<String>()
            val (baseUri, remarkFromFrag) = extractRemarkAndUri(rawUri)
            val comp = parseUriSafely(baseUri, 0) ?: return ParseResult.Error("MALFORMED_URI", "Invalid Trojan URI structure")

            val password = comp.userInfo.trim()
            val host = autoFixHost(comp.host, fixes)

            if (password.isEmpty()) return ParseResult.Error("INVALID_AUTH", "Missing Trojan password")
            if (host.isEmpty() || !HOST_REGEX.matches(host)) return ParseResult.Error("INVALID_HOST", "Invalid Trojan server hostname: $host")

            val queryMap = comp.queryParams
            val port = autoFixPort(comp.port, 443, fixes)
            val wsHost = queryMap["host"] ?: host
            val sni = autoFixSni(queryMap["sni"] ?: (queryMap["peer"] ?: ""), host, wsHost, queryMap["host"] ?: "", fixes)
            val transport = queryMap["type"]?.lowercase() ?: (queryMap["net"]?.lowercase() ?: "tcp")
            val wsPath = autoFixWsPath(if (queryMap["path"].isNullOrBlank()) (comp.path.ifBlank { "/" }) else queryMap["path"]!!, fixes)
            val alpn = queryMap["alpn"] ?: ""
            val fp = queryMap["fp"] ?: "chrome"
            val security = queryMap["security"]?.lowercase() ?: "tls"
            val pbk = queryMap["pbk"] ?: ""
            val sid = queryMap["sid"] ?: ""

            val remark = if (remarkFromFrag.isNotEmpty()) remarkFromFrag else "Trojan ($host:$port)"
            val (flag, code) = GeoIpResolver.resolveHostCountry(host, remark)

            ParseResult.Success(
                VlessConfig(
                    protocol = "TROJAN",
                    uuid = password,
                    host = host,
                    port = port,
                    security = security,
                    sni = sni,
                    fingerprint = fp,
                    publicKey = pbk,
                    shortId = sid,
                    alpn = alpn,
                    transport = transport,
                    wsPath = wsPath,
                    wsHost = wsHost,
                    remark = remark,
                    countryCode = code,
                    countryFlag = flag,
                    rawPayload = rawUri,
                    autoFixes = fixes
                )
            )
        } catch (e: Exception) {
            ParseResult.Error("PARSE_FAILED", "Failed to parse Trojan URI: ${e.message}")
        }
    }

    private suspend fun parseShadowsocks(rawUri: String): ParseResult {
        return try {
            val fixes = mutableListOf<String>()
            val (baseUri, remarkFromFrag) = extractRemarkAndUri(rawUri)
            val withoutScheme = baseUri.substring(5).trim()

            var method = "aes-256-gcm"
            var password = ""
            var host = ""
            var port = 8388

            val atIdx = withoutScheme.indexOf('@')
            if (atIdx >= 0) {
                // SIP002 format (UserInfo@host:port?query)
                val userPart = withoutScheme.substring(0, atIdx)
                val hostPortQuery = withoutScheme.substring(atIdx + 1)

                val creds = if (userPart.contains(":")) {
                    userPart
                } else {
                    val dec = safeBase64Decode(userPart)
                    if (dec.isNotEmpty()) dec else userPart
                }

                val colonIdx = creds.indexOf(':')
                if (colonIdx > 0) {
                    method = creds.substring(0, colonIdx)
                    password = creds.substring(colonIdx + 1)
                } else {
                    password = creds
                }

                val qIdx = hostPortQuery.indexOf('?')
                val hostPort = if (qIdx >= 0) hostPortQuery.substring(0, qIdx) else hostPortQuery
                val (h, p) = parseHostPort(hostPort, 8388)
                host = autoFixHost(h, fixes)
                port = autoFixPort(p, 8388, fixes)
            } else {
                // Legacy Base64 format: ss://BASE64(method:password@host:port)
                val decoded = safeBase64Decode(withoutScheme)
                val decAtIdx = decoded.indexOf('@')
                if (decAtIdx >= 0) {
                    val creds = decoded.substring(0, decAtIdx)
                    val hostPort = decoded.substring(decAtIdx + 1)
                    val colonIdx = creds.indexOf(':')
                    if (colonIdx > 0) {
                        method = creds.substring(0, colonIdx)
                        password = creds.substring(colonIdx + 1)
                    } else {
                        password = creds
                    }
                    val (h, p) = parseHostPort(hostPort, 8388)
                    host = autoFixHost(h, fixes)
                    port = autoFixPort(p, 8388, fixes)
                } else {
                    return ParseResult.Error("INVALID_SS", "Cannot parse Shadowsocks legacy base64 payload")
                }
            }

            if (method.equals("chacha20-poly1305", ignoreCase = true)) {
                fixes.add("Normalized cipher chacha20-poly1305 to chacha20-ietf-poly1305")
                method = "chacha20-ietf-poly1305"
            }

            if (host.isEmpty() || !HOST_REGEX.matches(host)) {
                return ParseResult.Error("INVALID_HOST", "Invalid Shadowsocks server hostname: $host")
            }
            if (port !in 1..65535) {
                return ParseResult.Error("INVALID_PORT", "Port must be between 1 and 65535")
            }

            val remark = if (remarkFromFrag.isNotEmpty()) remarkFromFrag else "Shadowsocks ($host:$port)"
            val (flag, code) = GeoIpResolver.resolveHostCountry(host, remark)

            ParseResult.Success(
                VlessConfig(
                    protocol = "SS",
                    uuid = password,
                    host = host,
                    port = port,
                    security = "none",
                    encryption = method,
                    transport = "tcp",
                    remark = remark,
                    countryCode = code,
                    countryFlag = flag,
                    rawPayload = rawUri,
                    autoFixes = fixes
                )
            )
        } catch (e: Exception) {
            ParseResult.Error("PARSE_FAILED", "Failed to parse Shadowsocks URI: ${e.message}")
        }
    }

    private suspend fun parseShadowsocksR(rawUri: String): ParseResult {
        return try {
            val fixes = mutableListOf<String>()
            val (baseUri, remarkFromFrag) = extractRemarkAndUri(rawUri)
            val b64 = baseUri.substring(6).trim()
            if (b64.isEmpty()) return ParseResult.Error("EMPTY_PAYLOAD", "SSR payload is empty")
            val decoded = safeBase64Decode(b64)

            // Format: host:port:protocol:method:obfs:password_base64/?params
            val qIdx = decoded.indexOf('?')
            val mainPartWithSlash = if (qIdx >= 0) decoded.substring(0, qIdx) else decoded
            val mainPart = mainPartWithSlash.trim().removeSuffix("/")
            val queryPart = if (qIdx >= 0) decoded.substring(qIdx + 1) else ""

            val parts = mainPart.split(":")
            if (parts.size < 6) {
                return ParseResult.Error("INVALID_SSR", "Malformed SSR link format (expected 6 colon-separated parameters)")
            }

            val host = autoFixHost(parts[0].trim(), fixes)
            val port = autoFixPort(parts[1].toIntOrNull() ?: 8388, 8388, fixes)
            val method = parts[3].trim()
            val passwordB64 = parts[5].trim()
            val password = safeBase64Decode(passwordB64).ifBlank { passwordB64 }

            if (host.isEmpty() || !HOST_REGEX.matches(host)) {
                return ParseResult.Error("INVALID_HOST", "Invalid SSR server hostname: $host")
            }
            if (port !in 1..65535) {
                return ParseResult.Error("INVALID_PORT", "Port must be between 1 and 65535")
            }

            var remark = remarkFromFrag
            if (remark.isEmpty() && queryPart.isNotEmpty()) {
                val qMap = parseQueryParams(queryPart)
                val remarksB64 = qMap["remarks"] ?: ""
                if (remarksB64.isNotEmpty()) {
                    remark = safeBase64Decode(remarksB64).ifBlank { remarksB64 }
                }
            }

            if (remark.isEmpty()) {
                remark = "SSR ($host:$port)"
            }

            val (flag, code) = GeoIpResolver.resolveHostCountry(host, remark)

            ParseResult.Success(
                VlessConfig(
                    protocol = "SSR",
                    uuid = password,
                    host = host,
                    port = port,
                    security = "none",
                    encryption = method,
                    transport = "tcp",
                    remark = remark,
                    countryCode = code,
                    countryFlag = flag,
                    rawPayload = rawUri,
                    autoFixes = fixes
                )
            )
        } catch (e: Exception) {
            ParseResult.Error("PARSE_FAILED", "Failed to parse SSR URI: ${e.message}")
        }
    }

    private suspend fun parseTuic(rawUri: String): ParseResult {
        return try {
            val fixes = mutableListOf<String>()
            val (baseUri, remarkFromFrag) = extractRemarkAndUri(rawUri)
            val comp = parseUriSafely(baseUri, 0) ?: return ParseResult.Error("MALFORMED_URI", "Invalid TUIC URI structure")

            val userInfo = comp.userInfo.trim()
            if (userInfo.isEmpty()) {
                return ParseResult.Error("INVALID_AUTH", "Missing TUIC credentials (UUID or token)")
            }

            val host = autoFixHost(comp.host, fixes)
            if (host.isEmpty() || !HOST_REGEX.matches(host)) {
                return ParseResult.Error("INVALID_HOST", "Invalid or missing TUIC server host: $host")
            }

            val port = autoFixPort(comp.port, 443, fixes)
            val queryMap = comp.queryParams
            val sni = autoFixSni(queryMap["sni"] ?: (queryMap["peer"] ?: ""), host, host, host, fixes)
            val alpn = queryMap["alpn"] ?: "h3"
            val remark = if (remarkFromFrag.isNotEmpty()) remarkFromFrag else "TUIC ($host:$port)"

            val (flag, code) = GeoIpResolver.resolveHostCountry(host, remark)

            ParseResult.Success(
                VlessConfig(
                    protocol = "TUIC",
                    uuid = userInfo,
                    host = host,
                    port = port,
                    security = "tls",
                    sni = sni,
                    alpn = alpn,
                    transport = "quic",
                    remark = remark,
                    countryCode = code,
                    countryFlag = flag,
                    rawPayload = rawUri,
                    autoFixes = fixes
                )
            )
        } catch (e: Exception) {
            ParseResult.Error("PARSE_FAILED", "Failed to parse TUIC URI: ${e.message}")
        }
    }

    private suspend fun parseHysteria2(rawUri: String): ParseResult {
        return try {
            val fixes = mutableListOf<String>()
            val (baseUri, remarkFromFrag) = extractRemarkAndUri(rawUri)
            val comp = parseUriSafely(baseUri, 0) ?: return ParseResult.Error("MALFORMED_URI", "Invalid Hysteria2 URI structure")

            val host = autoFixHost(comp.host, fixes)
            if (host.isEmpty() || !HOST_REGEX.matches(host)) {
                return ParseResult.Error("INVALID_HOST", "Invalid Hysteria2 server host: $host")
            }

            val port = autoFixPort(comp.port, 443, fixes)
            val queryMap = comp.queryParams
            val auth = if (comp.userInfo.isNotBlank()) comp.userInfo else (queryMap["auth"] ?: "hy2")
            val sni = autoFixSni(queryMap["sni"] ?: (queryMap["peer"] ?: ""), host, host, host, fixes)
            val alpn = queryMap["alpn"] ?: "h3"
            val remark = if (remarkFromFrag.isNotEmpty()) remarkFromFrag else "Hysteria2 ($host:$port)"

            val (flag, code) = GeoIpResolver.resolveHostCountry(host, remark)

            ParseResult.Success(
                VlessConfig(
                    protocol = "HYSTERIA2",
                    uuid = auth,
                    host = host,
                    port = port,
                    security = "tls",
                    sni = sni,
                    alpn = alpn,
                    transport = "quic",
                    remark = remark,
                    countryCode = code,
                    countryFlag = flag,
                    rawPayload = rawUri,
                    autoFixes = fixes
                )
            )
        } catch (e: Exception) {
            ParseResult.Error("PARSE_FAILED", "Failed to parse Hysteria2 URI: ${e.message}")
        }
    }

    private fun parseHostPort(hostPort: String, defaultPort: Int): Pair<String, Int> {
        val clean = hostPort.trim()
        if (clean.startsWith("[")) {
            val closeBracket = clean.indexOf(']')
            if (closeBracket > 0) {
                val host = clean.substring(1, closeBracket)
                val portStr = clean.substring(closeBracket + 1).removePrefix(":")
                val port = portStr.toIntOrNull() ?: defaultPort
                return Pair(host, port)
            }
        }
        val idx = clean.lastIndexOf(':')
        return if (idx > 0) {
            val host = clean.substring(0, idx)
            val port = clean.substring(idx + 1).toIntOrNull() ?: defaultPort
            Pair(host, port)
        } else {
            Pair(clean, defaultPort)
        }
    }
}
