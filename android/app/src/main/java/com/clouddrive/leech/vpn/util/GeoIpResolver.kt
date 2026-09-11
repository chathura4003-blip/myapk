package com.clouddrive.leech.vpn.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

object GeoIpResolver {

    private val cache = ConcurrentHashMap<String, Pair<String, String>>()

    private val client = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(3, TimeUnit.SECONDS)
        .build()

    private val COUNTRY_FLAGS = mapOf(
        "SG" to "🇸🇬", "SINGAPORE" to "🇸🇬",
        "US" to "🇺🇸", "USA" to "🇺🇸", "UNITED STATES" to "🇺🇸",
        "JP" to "🇯🇵", "JAPAN" to "🇯🇵", "TOKYO" to "🇯🇵",
        "DE" to "🇩🇪", "GERMANY" to "🇩🇪", "FRANKFURT" to "🇩🇪",
        "GB" to "🇬🇧", "UK" to "🇬🇧", "LONDON" to "🇬🇧",
        "LK" to "🇱🇰", "SRI LANKA" to "🇱🇰",
        "IN" to "🇮🇳", "INDIA" to "🇮🇳", "MUMBAI" to "🇮🇳",
        "FR" to "🇫🇷", "FRANCE" to "🇫🇷", "PARIS" to "🇫🇷",
        "CA" to "🇨🇦", "CANADA" to "🇨🇦",
        "NL" to "🇳🇱", "NETHERLANDS" to "🇳🇱", "AMSTERDAM" to "🇳🇱",
        "HK" to "🇭🇰", "HONG KONG" to "🇭🇰",
        "AU" to "🇦🇺", "AUSTRALIA" to "🇦🇺", "SYDNEY" to "🇦🇺",
        "KR" to "🇰🇷", "KOREA" to "🇰🇷", "SEOUL" to "🇰🇷",
        "TR" to "🇹🇷", "TURKEY" to "🇹🇷",
        "AE" to "🇦🇪", "DUBAI" to "🇦🇪", "UAE" to "🇦🇪"
    )

    fun countryCodeToEmoji(code: String): String {
        val clean = code.uppercase().trim()
        if (clean.length != 2) return "🌐"
        return try {
            val first = Character.codePointAt(clean, 0) - 0x41 + 0x1F1E6
            val second = Character.codePointAt(clean, 1) - 0x41 + 0x1F1E6
            String(Character.toChars(first)) + String(Character.toChars(second))
        } catch (_: Exception) {
            "🌐"
        }
    }

    fun extractCountryFromRemark(remark: String, host: String): Pair<String, String>? {
        val upper = (remark + " " + host).uppercase()

        // 1. Emoji match
        val emojiRegex = Regex("""[\uD83C][\uDDE6-\uDDFF][\uD83C][\uDDE6-\uDDFF]""")
        val emojiMatch = emojiRegex.find(remark)
        if (emojiMatch != null) {
            val flag = emojiMatch.value
            val code = COUNTRY_FLAGS.entries.find { it.value == flag && it.key.length == 2 }?.key ?: "VPN"
            return Pair(flag, code)
        }

        // 2. Keyword match
        for ((key, flag) in COUNTRY_FLAGS) {
            if (upper.contains(Regex("""\b$key\b""")) || upper.contains(key)) {
                val code = if (key.length == 2) key else (COUNTRY_FLAGS.entries.find { it.value == flag && it.key.length == 2 }?.key ?: key.take(2))
                return Pair(flag, code)
            }
        }

        return null
    }

    suspend fun resolveHostCountry(host: String, remark: String = ""): Pair<String, String> = withContext(Dispatchers.IO) {
        val fromRemark = extractCountryFromRemark(remark, host)
        if (fromRemark != null) {
            return@withContext fromRemark
        }

        val cleanHost = host.replace(Regex("""^https?://"""), "").trim()
        if (cleanHost.isEmpty()) return@withContext Pair("🌐", "VPN")

        cache[cleanHost]?.let { return@withContext it }

        try {
            val req = Request.Builder()
                .url("http://ip-api.com/json/$cleanHost?fields=status,countryCode,country")
                .header("User-Agent", "Mozilla/5.0")
                .build()

            client.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val body = resp.body?.string() ?: ""
                    val json = JSONObject(body)
                    if (json.optString("status") == "success") {
                        val cCode = json.optString("countryCode", "").uppercase()
                        if (cCode.length == 2) {
                            val flag = countryCodeToEmoji(cCode)
                            val res = Pair(flag, cCode)
                            cache[cleanHost] = res
                            return@withContext res
                        }
                    }
                }
            }
        } catch (_: Exception) {}

        Pair("🌐", "VPN")
    }
}
