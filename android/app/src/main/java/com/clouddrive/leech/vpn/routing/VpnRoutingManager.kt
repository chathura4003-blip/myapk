package com.clouddrive.leech.vpn.routing

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager

enum class VpnRoutingMode {
    FULL_VPN,
    BYPASS_LAN,
    PER_APP_ALLOW,
    PER_APP_DISALLOW
}

data class AppInfoItem(
    val packageName: String,
    val appName: String,
    val isSystemApp: Boolean
)

data class VpnRoutingConfig(
    val mode: VpnRoutingMode = VpnRoutingMode.FULL_VPN,
    val selectedPackages: Set<String> = emptySet(),
    val primaryDns: String = "1.1.1.1",
    val secondaryDns: String = "8.8.8.8",
    val mtu: Int = 1400,
    val autoReconnect: Boolean = true
)

class VpnRoutingManager(private val context: Context) {

    private val prefs = context.getSharedPreferences("cdl_vpn_routing_config", Context.MODE_PRIVATE)

    fun getRoutingConfig(): VpnRoutingConfig {
        val modeStr = prefs.getString("routing_mode", VpnRoutingMode.FULL_VPN.name) ?: VpnRoutingMode.FULL_VPN.name
        val mode = try { VpnRoutingMode.valueOf(modeStr) } catch (_: Exception) { VpnRoutingMode.FULL_VPN }
        val pkgs = prefs.getStringSet("selected_packages", emptySet()) ?: emptySet()
        val dns1 = prefs.getString("primary_dns", "1.1.1.1") ?: "1.1.1.1"
        val dns2 = prefs.getString("secondary_dns", "8.8.8.8") ?: "8.8.8.8"
        val mtu = prefs.getInt("vpn_mtu", 1400)
        val autoReconnect = prefs.getBoolean("auto_reconnect", true)

        return VpnRoutingConfig(
            mode = mode,
            selectedPackages = pkgs,
            primaryDns = dns1,
            secondaryDns = dns2,
            mtu = mtu,
            autoReconnect = autoReconnect
        )
    }

    fun saveRoutingConfig(config: VpnRoutingConfig) {
        prefs.edit()
            .putString("routing_mode", config.mode.name)
            .putStringSet("selected_packages", config.selectedPackages)
            .putString("primary_dns", config.primaryDns)
            .putString("secondary_dns", config.secondaryDns)
            .putInt("vpn_mtu", config.mtu)
            .putBoolean("auto_reconnect", config.autoReconnect)
            .apply()
    }

    fun getInstalledApplications(): List<AppInfoItem> {
        val pm = context.packageManager
        val installed = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        val list = mutableListOf<AppInfoItem>()

        val ourPkg = context.packageName
        for (app in installed) {
            if (app.packageName == ourPkg) continue
            // Only list apps with launch intents or user-installed
            val hasLaunchIntent = pm.getLaunchIntentForPackage(app.packageName) != null
            val isSystem = (app.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            if (hasLaunchIntent || !isSystem) {
                val label = pm.getApplicationLabel(app).toString()
                list.add(AppInfoItem(app.packageName, label, isSystem))
            }
        }
        return list.sortedBy { it.appName.lowercase() }
    }
}
