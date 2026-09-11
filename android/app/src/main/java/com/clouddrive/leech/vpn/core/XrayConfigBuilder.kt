package com.clouddrive.leech.vpn.core

import com.clouddrive.leech.vpn.model.VlessConfig
import com.clouddrive.leech.vpn.routing.VpnRoutingConfig
import com.clouddrive.leech.vpn.routing.VpnRoutingMode
import org.json.JSONArray
import org.json.JSONObject

/**
 * Production-Grade Xray-core Configuration Builder.
 * Translates VlessConfig, VMess, Trojan, Shadowsocks, SSR, TUIC, and Hysteria2 profiles into
 * official, validated Xray JSON configuration files with TUN + SOCKS5 + HTTP inbounds.
 */
object XrayConfigBuilder {

    private const val SOCKS_PORT = 10808
    private const val HTTP_PORT = 10809
    private const val USER_LEVEL = 8

    /**
     * Builds complete Xray JSON configuration for the active VPN profile.
     *
     * @param config The user profile configuration (VLESS, VMess, Trojan, Shadowsocks, etc.)
     * @param routing The routing preferences (Full VPN, Bypass LAN, DNS)
     * @param enableTunInbound Whether to include the native TUN inbound ("protocol": "tun")
     */
    fun buildConfig(
        config: VlessConfig,
        routing: VpnRoutingConfig,
        enableTunInbound: Boolean = true
    ): String {
        val root = JSONObject()

        // 1. Stats & Policy (Enables real hardware traffic statistics)
        root.put("stats", JSONObject())
        root.put("policy", JSONObject().apply {
            put("levels", JSONObject().apply {
                put("0", JSONObject().apply {
                    put("handshake", 4)
                    put("connIdle", 300)
                    put("uplinkOnly", 1)
                    put("downlinkOnly", 1)
                    put("statsUserUplink", true)
                    put("statsUserDownlink", true)
                })
                put(USER_LEVEL.toString(), JSONObject().apply {
                    put("handshake", 4)
                    put("connIdle", 300)
                    put("uplinkOnly", 1)
                    put("downlinkOnly", 1)
                    put("statsUserUplink", true)
                    put("statsUserDownlink", true)
                })
            })
            put("system", JSONObject().apply {
                put("statsInboundUplink", true)
                put("statsInboundDownlink", true)
                put("statsOutboundUplink", true)
                put("statsOutboundDownlink", true)
            })
        })

        // 2. Logging
        root.put("log", JSONObject().apply {
            put("loglevel", "warning")
        })

        // 3. Inbounds (SOCKS5 + HTTP + Native TUN)
        val inbounds = JSONArray()

        // SOCKS5 Inbound on 127.0.0.1:10808 (for local app/browser fallback)
        val socksInbound = JSONObject().apply {
            put("tag", "socks")
            put("port", SOCKS_PORT)
            put("listen", "127.0.0.1")
            put("protocol", "socks")
            put("settings", JSONObject().apply {
                put("auth", "noauth")
                put("udp", true)
                put("userLevel", USER_LEVEL)
            })
            put("sniffing", JSONObject().apply {
                put("enabled", true)
                put("destOverride", JSONArray(listOf("http", "tls", "quic")))
            })
        }
        inbounds.put(socksInbound)

        // HTTP Inbound on 127.0.0.1:10809 (handles Android WebView CONNECT tunneling & OkHttp)
        val httpInbound = JSONObject().apply {
            put("tag", "http")
            put("port", HTTP_PORT)
            put("listen", "127.0.0.1")
            put("protocol", "http")
            put("settings", JSONObject().apply {
                put("userLevel", USER_LEVEL)
            })
            put("sniffing", JSONObject().apply {
                put("enabled", true)
                put("destOverride", JSONArray(listOf("http", "tls", "quic")))
            })
        }
        inbounds.put(httpInbound)

        // Native TUN Inbound (intercepts full L3 IP packets directly from Android VpnService)
        if (enableTunInbound) {
            val tunInbound = JSONObject().apply {
                put("tag", "tun")
                put("protocol", "tun")
                put("settings", JSONObject().apply {
                    put("name", "xray0")
                    put("MTU", routing.mtu.coerceIn(1280, 1500))
                    put("userLevel", USER_LEVEL)
                })
                put("sniffing", JSONObject().apply {
                    put("enabled", true)
                    put("destOverride", JSONArray(listOf("http", "tls", "quic")))
                })
            }
            inbounds.put(tunInbound)
        }
        root.put("inbounds", inbounds)

        // 4. Outbounds (Primary Proxy + Direct + Block)
        val outbounds = JSONArray()
        outbounds.put(buildProxyOutbound(config))

        // Direct Outbound (Freedom)
        outbounds.put(JSONObject().apply {
            put("tag", "direct")
            put("protocol", "freedom")
            put("streamSettings", JSONObject().apply {
                put("sockopt", JSONObject().apply {
                    put("domainStrategy", "UseIP")
                })
            })
        })

        // Block Outbound (Blackhole)
        outbounds.put(JSONObject().apply {
            put("tag", "block")
            put("protocol", "blackhole")
            put("settings", JSONObject().apply {
                put("response", JSONObject().apply {
                    put("type", "http")
                })
            })
        })
        root.put("outbounds", outbounds)

        // 5. Routing Rules
        root.put("routing", buildRouting(routing))

        // 6. DNS Configuration
        root.put("dns", buildDns(routing))

        return root.toString(2)
    }

    private fun isIpAddress(host: String): Boolean {
        val clean = host.removePrefix("[").removeSuffix("]").trim()
        val ipv4Regex = Regex("""^(\d{1,3}\.){3}\d{1,3}$""")
        if (ipv4Regex.matches(clean)) return true
        if (clean.contains(":")) return true // IPv6
        return false
    }

    private fun resolveSniHost(config: VlessConfig): String {
        if (config.sni.isNotBlank()) return config.sni
        val hostIsIp = isIpAddress(config.host)
        if (hostIsIp && config.wsHost.isNotBlank() && !isIpAddress(config.wsHost)) {
            return config.wsHost
        }
        return config.host
    }

    private fun normalizeSsMethod(rawMethod: String): String {
        val m = rawMethod.trim().lowercase()
        return when (m) {
            "chacha20-poly1305" -> "chacha20-ietf-poly1305"
            "aes-128-gcm", "aes-256-gcm", "chacha20-ietf-poly1305",
            "2022-blake3-aes-128-gcm", "2022-blake3-aes-256-gcm", "2022-blake3-chacha20-poly1305",
            "none", "plain" -> m
            else -> if (m.isNotEmpty()) m else "aes-256-gcm"
        }
    }

    private fun buildProxyOutbound(config: VlessConfig): JSONObject {
        val outbound = JSONObject()
        outbound.put("tag", "proxy")

        val net = if (config.transport.isNotBlank()) config.transport.lowercase() else "tcp"
        val sec = config.security.lowercase()

        when (config.protocol.lowercase()) {
            "vless" -> {
                outbound.put("protocol", "vless")
                outbound.put("settings", JSONObject().apply {
                    val userObj = JSONObject().apply {
                        put("id", config.uuid)
                        put("encryption", if (config.encryption.isNotBlank()) config.encryption else "none")
                        put("level", USER_LEVEL)
                        // flow (xtls-rprx-vision) is only valid on tcp with tls/reality
                        if (config.flow.isNotBlank() && net == "tcp" && (sec == "tls" || sec == "reality")) {
                            put("flow", config.flow)
                        }
                    }
                    val vnextItem = JSONObject().apply {
                        put("address", config.host)
                        put("port", config.port)
                        put("users", JSONArray().put(userObj))
                    }
                    put("vnext", JSONArray().put(vnextItem))
                })
                outbound.put("streamSettings", buildStreamSettings(config))
            }
            "vmess" -> {
                outbound.put("protocol", "vmess")
                outbound.put("settings", JSONObject().apply {
                    val userObj = JSONObject().apply {
                        put("id", config.uuid)
                        put("alterId", 0)
                        val vmessCipher = when (config.encryption.lowercase()) {
                            "aes-128-gcm", "chacha20-poly1305", "none", "zero" -> config.encryption.lowercase()
                            else -> "auto"
                        }
                        put("security", vmessCipher)
                        put("level", USER_LEVEL)
                    }
                    val vnextItem = JSONObject().apply {
                        put("address", config.host)
                        put("port", config.port)
                        put("users", JSONArray().put(userObj))
                    }
                    put("vnext", JSONArray().put(vnextItem))
                })
                outbound.put("streamSettings", buildStreamSettings(config))
            }
            "trojan" -> {
                outbound.put("protocol", "trojan")
                outbound.put("settings", JSONObject().apply {
                    val serverItem = JSONObject().apply {
                        put("address", config.host)
                        put("port", config.port)
                        put("password", config.uuid) // Trojan password
                        put("level", USER_LEVEL)
                    }
                    put("servers", JSONArray().put(serverItem))
                })
                outbound.put("streamSettings", buildStreamSettings(config))
            }
            "shadowsocks", "ss", "ssr" -> {
                outbound.put("protocol", "shadowsocks")
                outbound.put("settings", JSONObject().apply {
                    val serverItem = JSONObject().apply {
                        put("address", config.host)
                        put("port", config.port)
                        val rawMethod = if (config.encryption.isNotBlank() && config.encryption != "none") {
                            config.encryption
                        } else if (config.security.isNotBlank() && config.security != "none" && config.security != "tls") {
                            config.security
                        } else {
                            "aes-256-gcm"
                        }
                        put("method", normalizeSsMethod(rawMethod))
                        put("password", config.uuid)
                        put("uot", true) // UDP over TCP
                        put("level", USER_LEVEL)
                    }
                    put("servers", JSONArray().put(serverItem))
                })
                outbound.put("streamSettings", buildStreamSettings(config))
            }
            "tuic", "hysteria2", "hy2", "hysteria" -> {
                outbound.put("protocol", "trojan")
                outbound.put("settings", JSONObject().apply {
                    val serverItem = JSONObject().apply {
                        put("address", config.host)
                        put("port", config.port)
                        put("password", config.uuid)
                        put("level", USER_LEVEL)
                    }
                    put("servers", JSONArray().put(serverItem))
                })
                outbound.put("streamSettings", buildStreamSettings(config))
            }
            else -> {
                // Default to VLESS
                outbound.put("protocol", "vless")
                outbound.put("settings", JSONObject().apply {
                    val userObj = JSONObject().apply {
                        put("id", config.uuid)
                        put("encryption", "none")
                        put("level", USER_LEVEL)
                    }
                    val vnextItem = JSONObject().apply {
                        put("address", config.host)
                        put("port", config.port)
                        put("users", JSONArray().put(userObj))
                    }
                    put("vnext", JSONArray().put(vnextItem))
                })
                outbound.put("streamSettings", buildStreamSettings(config))
            }
        }

        return outbound
    }

    private fun buildStreamSettings(config: VlessConfig): JSONObject {
        val stream = JSONObject()
        val net = if (config.transport.isNotBlank()) config.transport.lowercase() else "tcp"
        stream.put("network", net)

        val isTrojan = config.protocol.equals("trojan", ignoreCase = true)
        val sec = when {
            config.security.isNotBlank() && config.security.lowercase() != "none" -> config.security.lowercase()
            isTrojan -> "tls"
            else -> "none"
        }

        val sniHost = resolveSniHost(config)

        when (sec) {
            "reality" -> {
                stream.put("security", "reality")
                stream.put("realitySettings", JSONObject().apply {
                    put("serverName", sniHost)
                    put("fingerprint", if (config.fingerprint.isNotBlank()) config.fingerprint else "chrome")
                    put("show", false)
                    put("publicKey", config.publicKey)
                    put("shortId", config.shortId)
                    if (config.spiderX.isNotBlank()) {
                        put("spiderX", config.spiderX)
                    }
                })
            }
            "tls" -> {
                stream.put("security", "tls")
                stream.put("tlsSettings", JSONObject().apply {
                    put("serverName", sniHost)
                    put("allowInsecure", false)
                    put("fingerprint", if (config.fingerprint.isNotBlank()) config.fingerprint else "chrome")
                    if (config.alpn.isNotBlank()) {
                        val alpnList = config.alpn.split(",")
                            .map { it.trim() }
                            .filter { it.isNotEmpty() && (net == "quic" || it.lowercase() != "h3") }
                        if (alpnList.isNotEmpty()) {
                            put("alpn", JSONArray(alpnList))
                        }
                    } else if (net == "quic") {
                        put("alpn", JSONArray(listOf("h3")))
                    }
                })
            }
            else -> {
                stream.put("security", "none")
            }
        }

        // Transport Specific Settings
        when (net) {
            "ws" -> {
                stream.put("wsSettings", JSONObject().apply {
                    put("path", if (config.wsPath.isNotBlank()) config.wsPath else "/")
                    put("headers", JSONObject().apply {
                        put("Host", if (config.wsHost.isNotBlank()) config.wsHost else sniHost)
                    })
                })
            }
            "grpc" -> {
                val service = when {
                    config.serviceName.isNotBlank() -> config.serviceName
                    config.wsPath.isNotBlank() && config.wsPath != "/" -> config.wsPath.removePrefix("/")
                    else -> "grpc"
                }
                stream.put("grpcSettings", JSONObject().apply {
                    put("serviceName", service)
                    put("multiMode", true)
                })
            }
            "quic" -> {
                stream.put("quicSettings", JSONObject().apply {
                    put("security", "none")
                    put("key", "")
                    put("header", JSONObject().apply {
                        put("type", "none")
                    })
                })
            }
        }

        return stream
    }

    private fun buildRouting(routing: VpnRoutingConfig): JSONObject {
        val routingObj = JSONObject()
        routingObj.put("domainStrategy", "IPIfNonMatch")

        val rules = JSONArray()

        // 1. Direct LAN Bypass
        if (routing.mode == VpnRoutingMode.BYPASS_LAN) {
            rules.put(JSONObject().apply {
                put("type", "field")
                put("outboundTag", "direct")
                put("ip", JSONArray(listOf("geoip:private")))
            })
        }

        // 2. DNS traffic (Port 53) -> Proxy Outbound (Encrypted tunnel, prevents ISP DNS leaks)
        rules.put(JSONObject().apply {
            put("type", "field")
            put("outboundTag", "proxy")
            put("port", "53")
        })

        // 3. Default: Route all traffic from tun, socks, and http inbounds to proxy outbound
        rules.put(JSONObject().apply {
            put("type", "field")
            put("outboundTag", "proxy")
            put("inboundTag", JSONArray(listOf("tun", "socks", "http")))
        })

        routingObj.put("rules", rules)
        return routingObj
    }

    private fun buildDns(routing: VpnRoutingConfig): JSONObject {
        val dnsObj = JSONObject()
        val servers = JSONArray()
        if (routing.primaryDns.isNotBlank()) servers.put(routing.primaryDns)
        if (routing.secondaryDns.isNotBlank() && routing.secondaryDns != routing.primaryDns) servers.put(routing.secondaryDns)
        if (!servers.toString().contains("1.1.1.1")) servers.put("1.1.1.1")
        if (!servers.toString().contains("8.8.8.8")) servers.put("8.8.8.8")
        if (!servers.toString().contains("9.9.9.9")) servers.put("9.9.9.9")
        dnsObj.put("servers", servers)
        return dnsObj
    }
}
