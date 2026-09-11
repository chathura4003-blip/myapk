package com.clouddrive.leech.vpn.net

import com.clouddrive.leech.vpn.service.NativeVpnService
import com.clouddrive.leech.vpn.util.VpnLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.InetAddress
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.net.SocketFactory

/**
 * Resilient Multi-Tier DNS-over-HTTPS (DoH) & Bug-Host Resolver.
 * Bypasses ISP domain blocks, supports custom SNI / bug hosts, and caches resolved IPs.
 */
object DnsOverHttpsResolver {

    private val ipPattern = Regex("""^([0-9]{1,3}\.){3}[0-9]{1,3}$|^([0-9a-fA-F]{0,4}:){1,7}[0-9a-fA-F]{0,4}$""")
    private val cache = ConcurrentHashMap<String, Pair<Long, List<InetAddress>>>()
    private const val CACHE_TTL_MS = 900_000L // 15 minutes

    private val dohClient = OkHttpClient.Builder()
        .connectTimeout(2, TimeUnit.SECONDS)
        .readTimeout(2, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .connectionPool(okhttp3.ConnectionPool(16, 10, TimeUnit.MINUTES))
        .socketFactory(object : SocketFactory() {
            override fun createSocket(): Socket {
                val s = Socket()
                NativeVpnService.instance?.protect(s)
                return s
            }
            override fun createSocket(host: String?, port: Int): Socket = createSocket()
            override fun createSocket(host: String?, port: Int, localHost: InetAddress?, localPort: Int): Socket = createSocket()
            override fun createSocket(host: InetAddress?, port: Int): Socket = createSocket()
            override fun createSocket(address: InetAddress?, port: Int, localAddress: InetAddress?, localPort: Int): Socket = createSocket()
        })
        .build()

    suspend fun resolve(host: String): List<InetAddress> = withContext(Dispatchers.IO) {
        val cleanHost = host.trim().lowercase().removePrefix("https://").removePrefix("http://").split(":")[0]
        if (cleanHost.isEmpty()) return@withContext emptyList()

        // 1. Direct IP Check (0ms)
        if (ipPattern.matches(cleanHost)) {
            try {
                return@withContext listOf(InetAddress.getByName(cleanHost))
            } catch (_: Exception) {}
        }

        // 2. Cache Hit
        cache[cleanHost]?.let { (timestamp, addrs) ->
            if (System.currentTimeMillis() - timestamp < CACHE_TTL_MS && addrs.isNotEmpty()) {
                return@withContext addrs
            }
        }

        // 3. Local / Intranet Domains (Fast path)
        if (cleanHost == "localhost" || cleanHost.endsWith(".local") || cleanHost.endsWith(".lan") || cleanHost.endsWith(".internal")) {
            try {
                val sysAddrs = InetAddress.getAllByName(cleanHost).toList()
                if (sysAddrs.isNotEmpty()) return@withContext sysAddrs
            } catch (_: Exception) {}
        }

        // 4. Cloudflare DNS-over-HTTPS (1.1.1.1 direct IP - Bypasses Wi-Fi / ISP Port 53 Poisoning)
        val cfRes = queryDoh("https://1.1.1.1/dns-query?name=$cleanHost&type=A", cleanHost)
        if (cfRes.isNotEmpty()) {
            cache[cleanHost] = Pair(System.currentTimeMillis(), cfRes)
            return@withContext cfRes
        }

        // 5. Google DNS-over-HTTPS (8.8.8.8 direct IP - Bypasses Wi-Fi / ISP Port 53 Poisoning)
        val googleRes = queryDoh("https://8.8.8.8/resolve?name=$cleanHost&type=A", cleanHost)
        if (googleRes.isNotEmpty()) {
            cache[cleanHost] = Pair(System.currentTimeMillis(), googleRes)
            return@withContext googleRes
        }

        // 6. Cloudflare Secondary DoH (1.0.0.1)
        val cfSecRes = queryDoh("https://1.0.0.1/dns-query?name=$cleanHost&type=A", cleanHost)
        if (cfSecRes.isNotEmpty()) {
            cache[cleanHost] = Pair(System.currentTimeMillis(), cfSecRes)
            return@withContext cfSecRes
        }

        // 7. Google Secondary DoH (8.8.4.4)
        val gSecRes = queryDoh("https://8.8.4.4/resolve?name=$cleanHost&type=A", cleanHost)
        if (gSecRes.isNotEmpty()) {
            cache[cleanHost] = Pair(System.currentTimeMillis(), gSecRes)
            return@withContext gSecRes
        }

        // 8. Fallback: System DNS (only if all encrypted DoH endpoints failed)
        try {
            val systemAddrs = InetAddress.getAllByName(cleanHost).toList().filter {
                val ip = it.hostAddress ?: ""
                ip.isNotEmpty() && ip != "0.0.0.0" && ip != "127.0.0.1" && !it.isLoopbackAddress && !ip.startsWith("192.248.")
            }
            if (systemAddrs.isNotEmpty()) {
                cache[cleanHost] = Pair(System.currentTimeMillis(), systemAddrs)
                return@withContext systemAddrs
            }
        } catch (_: Exception) {
            VpnLogger.d("System DNS fallback failed for $cleanHost")
        }

        emptyList()
    }

    private fun queryDoh(endpoint: String, hostname: String): List<InetAddress> {
        try {
            val req = Request.Builder()
                .url(endpoint)
                .header("Accept", "application/dns-json")
                .header("User-Agent", "CloudDriveLeech/2026.1")
                .build()

            dohClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return emptyList()
                val body = resp.body?.string() ?: return emptyList()
                val json = JSONObject(body)
                val answers = json.optJSONArray("Answer") ?: return emptyList()

                val result = mutableListOf<InetAddress>()
                for (i in 0 until answers.length()) {
                    val obj = answers.getJSONObject(i)
                    val data = obj.optString("data", "").trim()
                    if (data.isNotEmpty() && ipPattern.matches(data) && data != "0.0.0.0" && data != "127.0.0.1" && !data.startsWith("192.248.")) {
                        try {
                            val addr = InetAddress.getByName(data)
                            if (!addr.isLoopbackAddress && !addr.isAnyLocalAddress) {
                                result.add(InetAddress.getByAddress(hostname, addr.address))
                            }
                        } catch (_: Exception) {}
                    }
                }
                return result
            }
        } catch (_: Exception) {
            return emptyList()
        }
    }
}
