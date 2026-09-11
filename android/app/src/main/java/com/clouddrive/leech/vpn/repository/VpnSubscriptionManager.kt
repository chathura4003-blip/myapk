package com.clouddrive.leech.vpn.repository

import android.content.Context
import android.util.Base64
import com.clouddrive.leech.vpn.parser.ParseResult
import com.clouddrive.leech.vpn.parser.VlessParser
import com.clouddrive.leech.vpn.util.VpnLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class VpnSubscription(
    val id: String,
    val name: String,
    val url: String,
    val lastUpdated: Long = 0L,
    val profileCount: Int = 0,
    val lastError: String? = null
)

class VpnSubscriptionManager(private val context: Context) {

    private val prefs = context.getSharedPreferences("cdl_vpn_subscriptions", Context.MODE_PRIVATE)
    private val profileRepo = VpnProfileRepository(context)

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    fun getAllSubscriptions(): List<VpnSubscription> {
        val raw = prefs.getString("subs_json", "[]") ?: "[]"
        val list = mutableListOf<VpnSubscription>()
        try {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    VpnSubscription(
                        id = obj.optString("id", System.currentTimeMillis().toString()),
                        name = obj.optString("name", "Subscription"),
                        url = obj.optString("url", ""),
                        lastUpdated = obj.optLong("lastUpdated", 0L),
                        profileCount = obj.optInt("profileCount", 0),
                        lastError = if (obj.has("lastError") && !obj.isNull("lastError")) obj.getString("lastError") else null
                    )
                )
            }
        } catch (_: Exception) {}
        return list
    }

    suspend fun updateSubscription(subId: String): Pair<Boolean, String?> = withContext(Dispatchers.IO) {
        val subs = getAllSubscriptions().toMutableList()
        val idx = subs.indexOfFirst { it.id == subId }
        if (idx < 0) return@withContext Pair(false, "Subscription not found")

        val sub = subs[idx]
        VpnLogger.i("Updating subscription '${sub.name}' from ${sub.url}")

        if (!sub.url.startsWith("https://", ignoreCase = true) && !sub.url.startsWith("http://", ignoreCase = true)) {
            return@withContext Pair(false, "Subscription URL must begin with https://")
        }

        try {
            val req = Request.Builder()
                .url(sub.url)
                .header("User-Agent", "v2rayNG/1.8.5 (Android; CloudDrive)")
                .build()

            val resp = httpClient.newCall(req).execute()
            if (!resp.isSuccessful) {
                val err = "HTTP Error: ${resp.code}"
                subs[idx] = sub.copy(lastError = err)
                persist(subs)
                return@withContext Pair(false, err)
            }

            val body = resp.body?.string()?.trim() ?: ""
            if (body.isEmpty()) {
                val err = "Empty subscription response"
                subs[idx] = sub.copy(lastError = err)
                persist(subs)
                return@withContext Pair(false, err)
            }

            // Decode content (usually Base64 encoded list of links)
            val decoded = try {
                String(Base64.decode(body, Base64.DEFAULT), Charsets.UTF_8)
            } catch (_: Exception) {
                body // Plaintext list fallback
            }

            val lines = decoded.lines().map { it.trim() }.filter { it.isNotEmpty() }
            var importedCount = 0

            for (line in lines) {
                val parseResult = VlessParser.parse(line)
                if (parseResult is ParseResult.Success) {
                    val cfg = parseResult.config
                    profileRepo.saveProfile(
                        VpnProfile(
                            id = "sub_${sub.id}_${System.currentTimeMillis()}_$importedCount",
                            name = cfg.remark.ifEmpty { "${sub.name} #$importedCount" },
                            rawPayload = line,
                            protocol = cfg.protocol,
                            host = cfg.host,
                            port = cfg.port,
                            security = cfg.security,
                            transport = cfg.transport,
                            countryFlag = cfg.countryFlag,
                            countryCode = cfg.countryCode,
                            isVerified = true,
                            subscriptionId = sub.id
                        )
                    )
                    importedCount++
                }
            }

            subs[idx] = sub.copy(
                lastUpdated = System.currentTimeMillis(),
                profileCount = importedCount,
                lastError = null
            )
            persist(subs)
            VpnLogger.i("Subscription '${sub.name}' updated successfully ($importedCount nodes)")
            Pair(true, "Successfully imported $importedCount profiles")

        } catch (e: Exception) {
            VpnLogger.e("Subscription update failed: ${e.message}")
            subs[idx] = sub.copy(lastError = e.message)
            persist(subs)
            Pair(false, e.message ?: "Failed to fetch subscription")
        }
    }

    fun addOrUpdateSubscription(name: String, url: String): VpnSubscription {
        val subs = getAllSubscriptions().toMutableList()
        val existing = subs.find { it.url.trim() == url.trim() }
        val sub = if (existing != null) {
            existing.copy(name = name)
        } else {
            VpnSubscription(
                id = System.currentTimeMillis().toString(),
                name = name,
                url = url
            )
        }
        val idx = subs.indexOfFirst { it.id == sub.id }
        if (idx >= 0) subs[idx] = sub else subs.add(0, sub)
        persist(subs)
        return sub
    }

    fun deleteSubscription(id: String) {
        val subs = getAllSubscriptions().filterNot { it.id == id }
        persist(subs)
    }

    private fun persist(list: List<VpnSubscription>) {
        val arr = JSONArray()
        for (s in list) {
            arr.put(JSONObject().apply {
                put("id", s.id)
                put("name", s.name)
                put("url", s.url)
                put("lastUpdated", s.lastUpdated)
                put("profileCount", s.profileCount)
                s.lastError?.let { put("lastError", it) }
            })
        }
        prefs.edit().putString("subs_json", arr.toString()).apply()
    }
}
