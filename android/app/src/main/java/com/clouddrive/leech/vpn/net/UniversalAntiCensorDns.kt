package com.clouddrive.leech.vpn.net

import kotlinx.coroutines.runBlocking
import okhttp3.Dns
import java.net.InetAddress
import java.util.concurrent.ConcurrentHashMap

/**
 * Universal Anti-Censor & Wi-Fi DNS-Poisoning Bypass Resolver.
 * Solves Wi-Fi blocking (SLT Fibre, Dialog Broadband, Mobitel Wi-Fi) by resolving
 * all Movie, 18+ Adult, and Download domains via Cloudflare (1.1.1.1) and Google (8.8.8.8) DoH directly.
 */
class UniversalAntiCensorDns : Dns {

    companion object {
        val instance = UniversalAntiCensorDns()
        private val cache = ConcurrentHashMap<String, Pair<Long, List<InetAddress>>>()

        // Known ISP Block IP ranges (e.g. SLT / Dialog redirect block landing pages)
        private val BLOCKED_IPS = setOf(
            "0.0.0.0", "127.0.0.1", "::1"
        )

        fun isKnownCensoredDomain(hostname: String): Boolean {
            val h = hostname.lowercase()
            return h.contains("pornhub") || h.contains("phncdn") ||
                   h.contains("xhamster") || h.contains("xhcdn") ||
                   h.contains("xvideos") || h.contains("xnxx") ||
                   h.contains("redtube") || h.contains("rdtcdn") ||
                   h.contains("eporner") || h.contains("cdn77") ||
                   h.contains("baiscope") || h.contains("cineru") ||
                   h.contains("cinesubz") || h.contains("sub.lk") ||
                   h.contains("piratelk") || h.contains("sinhalasub") ||
                   h.contains("cinejoy") || h.contains("shegu.st") ||
                   h.contains("workers.dev") || h.contains("4khdhub") ||
                   h.contains("yts.") || h.contains("1337x") ||
                   h.contains("torrent") || h.contains("seedr")
        }

        fun isInvalidOrIspRedirectIp(ip: String): Boolean {
            if (BLOCKED_IPS.contains(ip)) return true
            if (ip.startsWith("192.248.")) return true // SLT TRCSL Redirect
            if (ip.startsWith("10.") || ip.startsWith("127.")) return true
            return false
        }
    }

    override fun lookup(hostname: String): List<InetAddress> {
        // Direct IP check
        if (hostname.matches(Regex("^\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}$"))) {
            return listOf(InetAddress.getByName(hostname))
        }

        // Check in-memory 15-min cache (0ms)
        val cached = cache[hostname]
        if (cached != null && System.currentTimeMillis() - cached.first < 900_000L) {
            return cached.second
        }

        fun prioritizeIpv4(addresses: List<InetAddress>): List<InetAddress> {
            val v4 = addresses.filterIsInstance<java.net.Inet4Address>()
            val v6 = addresses.filterIsInstance<java.net.Inet6Address>()
            return if (v4.isNotEmpty()) v4 + v6 else addresses
        }

        // 1. Primary Path: Cloudflare (1.1.1.1) & Google (8.8.8.8) DoH
        // 100% Bypasses all Mobile Carrier (Dialog, Mobitel, Airtel) & Wi-Fi (SLT) DNS blocking & throttling
        try {
            val dohIps = runBlocking {
                DnsOverHttpsResolver.resolve(hostname)
            }
            val validDoh = dohIps.filter { address ->
                val hostAddress = address.hostAddress ?: ""
                !isInvalidOrIspRedirectIp(hostAddress) && !address.isLoopbackAddress
            }
            val sorted = prioritizeIpv4(validDoh)
            if (sorted.isNotEmpty()) {
                cache[hostname] = Pair(System.currentTimeMillis(), sorted)
                return sorted
            }
        } catch (_: Exception) {}

        // 2. Secondary Fallback: System DNS (if DoH is unreachable or for local intranet)
        try {
            val sysAddrs = Dns.SYSTEM.lookup(hostname)
            val valid = sysAddrs.filter { address ->
                val hostAddress = address.hostAddress ?: ""
                !isInvalidOrIspRedirectIp(hostAddress) && !address.isLoopbackAddress
            }
            val sorted = prioritizeIpv4(valid)
            if (sorted.isNotEmpty()) {
                cache[hostname] = Pair(System.currentTimeMillis(), sorted)
                return sorted
            }
        } catch (_: Exception) {}

        return prioritizeIpv4(Dns.SYSTEM.lookup(hostname))
    }

    fun clearCache() {
        cache.clear()
    }
}
