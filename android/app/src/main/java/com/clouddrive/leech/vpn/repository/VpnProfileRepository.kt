package com.clouddrive.leech.vpn.repository

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class VpnProfile(
    val id: String,
    val name: String,
    val rawPayload: String,
    val protocol: String = "VLESS",
    val host: String = "",
    val port: Int = 443,
    val security: String = "none",
    val transport: String = "tcp",
    val countryFlag: String = "🌐",
    val countryCode: String = "VPN",
    val lastLatencyMs: Long? = null,
    val isVerified: Boolean = false,
    val isFavorite: Boolean = false,
    val subscriptionId: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

class VpnProfileRepository(context: Context) {

    private val prefs = context.getSharedPreferences("cdl_vpn_profiles_store", Context.MODE_PRIVATE)

    fun getAllProfiles(): List<VpnProfile> {
        val raw = prefs.getString("saved_profiles_json", "[]") ?: "[]"
        val list = mutableListOf<VpnProfile>()
        try {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    VpnProfile(
                        id = obj.optString("id", System.currentTimeMillis().toString()),
                        name = obj.optString("name", "Custom Profile"),
                        rawPayload = obj.optString("rawPayload", ""),
                        protocol = obj.optString("protocol", "VLESS"),
                        host = obj.optString("host", ""),
                        port = obj.optInt("port", 443),
                        security = obj.optString("security", "none"),
                        transport = obj.optString("transport", "tcp"),
                        countryFlag = obj.optString("countryFlag", "🌐"),
                        countryCode = obj.optString("countryCode", "VPN"),
                        lastLatencyMs = if (obj.has("lastLatencyMs") && !obj.isNull("lastLatencyMs")) obj.getLong("lastLatencyMs") else null,
                        isVerified = obj.optBoolean("isVerified", true),
                        isFavorite = obj.optBoolean("isFavorite", false),
                        subscriptionId = if (obj.has("subscriptionId")) obj.optString("subscriptionId") else null,
                        createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                        updatedAt = obj.optLong("updatedAt", System.currentTimeMillis())
                    )
                )
            }
        } catch (_: Exception) {}
        return list
    }

    fun saveProfile(profile: VpnProfile) {
        val list = getAllProfiles().toMutableList()
        val existingIndex = list.indexOfFirst { it.id == profile.id || (it.rawPayload.isNotBlank() && it.rawPayload.trim() == profile.rawPayload.trim()) }
        val updated = profile.copy(updatedAt = System.currentTimeMillis())
        if (existingIndex >= 0) {
            list[existingIndex] = updated
        } else {
            list.add(0, updated)
        }
        persist(list)
    }

    fun deleteProfile(id: String) {
        val list = getAllProfiles().filterNot { it.id == id }
        persist(list)
    }

    fun toggleFavorite(id: String): Boolean {
        val list = getAllProfiles().toMutableList()
        val idx = list.indexOfFirst { it.id == id }
        if (idx >= 0) {
            val current = list[idx]
            val nextFav = !current.isFavorite
            list[idx] = current.copy(isFavorite = nextFav, updatedAt = System.currentTimeMillis())
            persist(list)
            return nextFav
        }
        return false
    }

    fun duplicateProfile(id: String): VpnProfile? {
        val list = getAllProfiles().toMutableList()
        val orig = list.find { it.id == id } ?: return null
        val dup = orig.copy(
            id = System.currentTimeMillis().toString(),
            name = "${orig.name} (Copy)",
            isFavorite = false,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        list.add(0, dup)
        persist(list)
        return dup
    }

    fun exportProfilesJson(): String {
        val list = getAllProfiles()
        val arr = JSONArray()
        for (p in list) {
            arr.put(toJsonObject(p))
        }
        val wrapper = JSONObject().apply {
            put("version", 1)
            put("app", "CloudDrive Leech VPN")
            put("timestamp", System.currentTimeMillis())
            put("profiles", arr)
        }
        return wrapper.toString(2)
    }

    fun importProfilesJson(jsonStr: String): Int {
        var count = 0
        try {
            val root = JSONObject(jsonStr)
            val arr = if (root.has("profiles")) root.getJSONArray("profiles") else JSONArray(jsonStr)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val raw = obj.optString("rawPayload", "")
                if (raw.isNotBlank()) {
                    saveProfile(
                        VpnProfile(
                            id = System.currentTimeMillis().toString() + "_" + i,
                            name = obj.optString("name", "Imported Profile"),
                            rawPayload = raw,
                            protocol = obj.optString("protocol", "VLESS"),
                            host = obj.optString("host", ""),
                            port = obj.optInt("port", 443),
                            security = obj.optString("security", "none"),
                            transport = obj.optString("transport", "tcp"),
                            countryFlag = obj.optString("countryFlag", "🌐"),
                            countryCode = obj.optString("countryCode", "VPN"),
                            isVerified = true,
                            createdAt = System.currentTimeMillis()
                        )
                    )
                    count++
                }
            }
        } catch (_: Exception) {}
        return count
    }

    private fun persist(list: List<VpnProfile>) {
        val arr = JSONArray()
        for (p in list) {
            arr.put(toJsonObject(p))
        }
        prefs.edit().putString("saved_profiles_json", arr.toString()).apply()
    }

    private fun toJsonObject(p: VpnProfile): JSONObject {
        return JSONObject().apply {
            put("id", p.id)
            put("name", p.name)
            put("rawPayload", p.rawPayload)
            put("protocol", p.protocol)
            put("host", p.host)
            put("port", p.port)
            put("security", p.security)
            put("transport", p.transport)
            put("countryFlag", p.countryFlag)
            put("countryCode", p.countryCode)
            p.lastLatencyMs?.let { put("lastLatencyMs", it) }
            put("isVerified", p.isVerified)
            put("isFavorite", p.isFavorite)
            p.subscriptionId?.let { put("subscriptionId", it) }
            put("createdAt", p.createdAt)
            put("updatedAt", p.updatedAt)
        }
    }
}
