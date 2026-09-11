package com.clouddrive.leech.vpn.model

data class VlessConfig(
    val protocol: String = "VLESS",
    val uuid: String,
    val host: String,
    val port: Int,
    val security: String = "none", // none, tls, reality
    val sni: String = "",
    val fingerprint: String = "chrome",
    val publicKey: String = "",
    val shortId: String = "",
    val spiderX: String = "",
    val flow: String = "",
    val encryption: String = "none",
    val alpn: String = "",
    val transport: String = "tcp", // tcp, ws, grpc, http
    val wsPath: String = "/",
    val wsHost: String = "",
    val serviceName: String = "",
    val remark: String = "",
    val countryCode: String = "VPN",
    val countryFlag: String = "🌐",
    val rawPayload: String = "",
    val autoFixes: List<String> = emptyList()
) {
    fun getEndpoint(): String = "$host:$port"
    fun isTlsEnabled(): Boolean = security.equals("tls", ignoreCase = true) || security.equals("reality", ignoreCase = true)
    fun isReality(): Boolean = security.equals("reality", ignoreCase = true)
}
