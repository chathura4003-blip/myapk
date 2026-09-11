package com.clouddrive.leech.plugins

import android.content.ClipboardManager
import android.content.ClipData
import android.net.VpnService
import com.clouddrive.leech.App
import com.clouddrive.leech.vpn.VpnEngineManager
import com.clouddrive.leech.vpn.net.ConnectivityTester
import com.clouddrive.leech.vpn.net.TcpCheckResult
import com.clouddrive.leech.vpn.parser.ParseResult
import com.clouddrive.leech.vpn.parser.VlessParser
import com.clouddrive.leech.vpn.repository.VpnProfile
import com.clouddrive.leech.vpn.repository.VpnSubscriptionManager
import com.clouddrive.leech.vpn.routing.VpnRoutingConfig
import com.clouddrive.leech.vpn.routing.VpnRoutingManager
import com.clouddrive.leech.vpn.routing.VpnRoutingMode
import com.clouddrive.leech.vpn.util.QrCodeDecoder
import com.clouddrive.leech.vpn.util.VpnLogger
import com.getcapacitor.JSArray
import com.getcapacitor.JSObject
import com.getcapacitor.Plugin
import com.getcapacitor.PluginCall
import com.getcapacitor.PluginMethod
import com.getcapacitor.annotation.CapacitorPlugin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.Proxy

@CapacitorPlugin(name = "NativeVpn")
class NativeVpnPlugin : Plugin() {

    private val scope = CoroutineScope(Dispatchers.Main)
    private val vpnManager: VpnEngineManager by lazy {
        VpnEngineManager.getInstance(context)
    }
    private val subManager: VpnSubscriptionManager by lazy {
        VpnSubscriptionManager(context)
    }
    private val routingManager: VpnRoutingManager by lazy {
        VpnRoutingManager(context)
    }

    companion object {
        val activeProxy: Proxy?
            get() = VpnEngineManager.globalProxy

        fun recordDownloadedBytes(bytes: Long) {
            VpnEngineManager.getInstance(App.instance).recordDownload(bytes)
        }

        fun recordUploadedBytes(bytes: Long) {
            VpnEngineManager.getInstance(App.instance).recordUpload(bytes)
        }
    }

    @PluginMethod
    fun getStatus(call: PluginCall) {
        val s = vpnManager.currentState
        call.resolve(JSObject().apply {
            put("status", s.status.name)
            put("isConnected", s.isRunning())
            put("server", s.server ?: "")
            put("port", s.port ?: 443)
            put("protocol", s.protocol ?: "VLESS")
            put("latencyMs", s.latencyMs ?: 0L)
            put("countryCode", s.countryCode ?: "VPN")
            put("countryFlag", s.countryFlag ?: "🌐")
            put("countryName", s.countryName ?: "")
            put("downloadBytes", s.downloadBytes)
            put("uploadBytes", s.uploadBytes)
            put("downloadMbps", s.downloadMbps)
            put("uploadMbps", s.uploadMbps)
            put("downloadFormatted", vpnManager.formatBytes(s.downloadBytes))
            put("uploadFormatted", vpnManager.formatBytes(s.uploadBytes))
            put("downloadSpeedFormatted", vpnManager.formatSpeed(s.downloadMbps))
            put("uploadSpeedFormatted", vpnManager.formatSpeed(s.uploadMbps))
            put("errorCode", s.errorCode ?: "")
            put("errorMessage", s.errorMessage ?: "")
        })
    }

    @PluginMethod
    fun parsePayload(call: PluginCall) {
        val raw = call.getString("payload") ?: (call.getString("config") ?: "")
        scope.launch(Dispatchers.IO) {
            val res = VlessParser.parse(raw)
            withContext(Dispatchers.Main) {
                when (res) {
                    is ParseResult.Success -> {
                        val c = res.config
                        call.resolve(JSObject().apply {
                            put("valid", true)
                            put("protocol", c.protocol)
                            put("host", c.host)
                            put("port", c.port)
                            put("security", c.security)
                            put("transport", c.transport)
                            put("sni", c.sni)
                            put("remark", c.remark)
                            put("countryCode", c.countryCode)
                            put("countryFlag", c.countryFlag)
                            val fixesArr = JSArray()
                            for (fix in c.autoFixes) fixesArr.put(fix)
                            put("autoFixes", fixesArr)
                            put("hasAutoFixes", c.autoFixes.isNotEmpty())
                        })
                    }
                    is ParseResult.Error -> {
                        call.resolve(JSObject().apply {
                            put("valid", false)
                            put("errorCode", res.code)
                            put("errorMessage", res.message)
                        })
                    }
                }
            }
        }
    }

    @PluginMethod
    fun connect(call: PluginCall) {
        val rawPayload = call.getString("payload") ?: ""
        if (rawPayload.trim().isEmpty()) {
            call.resolve(JSObject().apply {
                put("success", false)
                put("errorCode", "EMPTY_PAYLOAD")
                put("errorMessage", "Payload cannot be empty")
            })
            return
        }

        val prepareIntent = VpnService.prepare(activity ?: context)
        if (prepareIntent != null) {
            activity?.startActivityForResult(prepareIntent, 9021)
            call.resolve(JSObject().apply {
                put("success", false)
                put("isConnected", false)
                put("status", "PERMISSION_REQUIRED")
                put("errorCode", "VPN_PERMISSION_REQUIRED")
                put("errorMessage", "Allow VPN permission, then tap the switch again")
            })
            return
        }

        vpnManager.connect(rawPayload) { success, errorMsg ->
            val s = vpnManager.currentState
            call.resolve(JSObject().apply {
                put("success", success)
                put("isConnected", s.isRunning())
                put("status", s.status.name)
                put("server", s.server ?: "")
                put("port", s.port ?: 443)
                put("protocol", s.protocol ?: "VLESS")
                put("latencyMs", s.latencyMs ?: 0L)
                put("countryCode", s.countryCode ?: "VPN")
                put("countryFlag", s.countryFlag ?: "🌐")
                put("countryName", s.countryName ?: "")
                put("downloadFormatted", vpnManager.formatBytes(s.downloadBytes))
                put("uploadFormatted", vpnManager.formatBytes(s.uploadBytes))
                put("downloadMbps", s.downloadMbps)
                put("uploadMbps", s.uploadMbps)
                put("errorCode", s.errorCode ?: "")
                put("errorMessage", errorMsg ?: s.errorMessage ?: "")
            })
        }
    }

    @PluginMethod
    fun disconnect(call: PluginCall) {
        vpnManager.disconnect("User requested")
        val s = vpnManager.currentState
        call.resolve(JSObject().apply {
            put("success", true)
            put("isConnected", false)
            put("status", s.status.name)
        })
    }

    @PluginMethod
    fun toggleVpn(call: PluginCall) {
        val enable = call.getBoolean("enable") ?: !vpnManager.currentState.isRunning()
        if (enable) {
            connect(call)
        } else {
            disconnect(call)
        }
    }

    @PluginMethod
    fun measurePing(call: PluginCall) {
        val host = call.getString("host") ?: (vpnManager.activeConfig?.host ?: "")
        val port = call.getInt("port") ?: (vpnManager.activeConfig?.port ?: 443)

        if (host.isEmpty()) {
            call.resolve(JSObject().apply {
                put("status", "offline")
                put("pingMs", -1L)
            })
            return
        }

        scope.launch(Dispatchers.IO) {
            val realCoreDelay = if (com.clouddrive.leech.vpn.core.XrayCoreManager.isRunning) {
                com.clouddrive.leech.vpn.core.XrayCoreManager.measureDelay("", "https://cp.cloudflare.com/generate_204")
            } else {
                -1L
            }

            val latency = if (realCoreDelay > 0) {
                realCoreDelay
            } else {
                val cfg = vpnManager.activeConfig ?: com.clouddrive.leech.vpn.model.VlessConfig(
                    uuid = "00000000-0000-0000-0000-000000000000",
                    host = host,
                    port = port
                )
                val tcpRes = ConnectivityTester.checkTcpReachability(cfg, timeoutMs = 3500)
                if (tcpRes is TcpCheckResult.Success) tcpRes.latencyMs else -1L
            }

            withContext(Dispatchers.Main) {
                val s = vpnManager.currentState
                if (latency > 0) {
                    vpnManager.updateLivePing(latency)
                    call.resolve(JSObject().apply {
                        put("status", "online")
                        put("pingMs", latency)
                        put("downloadBytes", s.downloadBytes)
                        put("uploadBytes", s.uploadBytes)
                        put("downloadMbps", s.downloadMbps)
                        put("uploadMbps", s.uploadMbps)
                        put("downloadFormatted", vpnManager.formatBytes(s.downloadBytes))
                        put("uploadFormatted", vpnManager.formatBytes(s.uploadBytes))
                        put("downloadSpeedFormatted", vpnManager.formatSpeed(s.downloadMbps))
                        put("uploadSpeedFormatted", vpnManager.formatSpeed(s.uploadMbps))
                    })
                } else {
                    call.resolve(JSObject().apply {
                        put("status", "offline")
                        put("pingMs", -1L)
                        put("downloadBytes", s.downloadBytes)
                        put("uploadBytes", s.uploadBytes)
                        put("downloadMbps", s.downloadMbps)
                        put("uploadMbps", s.uploadMbps)
                        put("downloadFormatted", vpnManager.formatBytes(s.downloadBytes))
                        put("uploadFormatted", vpnManager.formatBytes(s.uploadBytes))
                        put("downloadSpeedFormatted", vpnManager.formatSpeed(s.downloadMbps))
                        put("uploadSpeedFormatted", vpnManager.formatSpeed(s.uploadMbps))
                        put("errorMessage", "Server unreachable")
                    })
                }
            }
        }
    }

    @PluginMethod
    fun getProfiles(call: PluginCall) {
        val list = vpnManager.profileRepository.getAllProfiles()
        val arr = JSArray()
        for (p in list) {
            arr.put(JSObject().apply {
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
                put("lastLatencyMs", p.lastLatencyMs ?: 0L)
                put("isVerified", p.isVerified)
                put("isFavorite", p.isFavorite)
                put("subscriptionId", p.subscriptionId ?: "")
            })
        }
        call.resolve(JSObject().apply { put("profiles", arr) })
    }

    @PluginMethod
    fun saveProfile(call: PluginCall) {
        val id = call.getString("id") ?: System.currentTimeMillis().toString()
        val name = call.getString("name") ?: "Custom Profile"
        val payload = call.getString("payload") ?: (call.getString("rawPayload") ?: "")
        val flag = call.getString("countryFlag") ?: "🌐"
        val code = call.getString("countryCode") ?: "VPN"
        val host = call.getString("host") ?: ""
        val port = call.getInt("port") ?: 443
        val proto = call.getString("protocol") ?: "VLESS"
        val security = call.getString("security") ?: "none"
        val transport = call.getString("transport") ?: "tcp"
        val latency = call.getLong("latencyMs") ?: 0L
        val fav = call.getBoolean("isFavorite") ?: false

        scope.launch(Dispatchers.IO) {
            var finalProto = proto
            var finalHost = host
            var finalPort = port
            var finalSecurity = security
            var finalTransport = transport
            var finalFlag = flag
            var finalCode = code

            if (payload.isNotBlank()) {
                val res = VlessParser.parse(payload)
                if (res is ParseResult.Success) {
                    val c = res.config
                    finalProto = c.protocol
                    if (finalHost.isBlank()) finalHost = c.host
                    if (finalPort == 443 && c.port != 443) finalPort = c.port
                    if (finalSecurity == "none" && c.security != "none") finalSecurity = c.security
                    if (finalTransport == "tcp" && c.transport != "tcp") finalTransport = c.transport
                    if (finalFlag == "🌐" || finalFlag == "⚡") finalFlag = c.countryFlag
                    if (finalCode == "VPN") finalCode = c.countryCode
                }
            }

            vpnManager.profileRepository.saveProfile(
                VpnProfile(
                    id = id,
                    name = name,
                    rawPayload = payload,
                    protocol = finalProto,
                    host = finalHost,
                    port = finalPort,
                    security = finalSecurity,
                    transport = finalTransport,
                    countryFlag = finalFlag,
                    countryCode = finalCode,
                    lastLatencyMs = if (latency > 0) latency else null,
                    isVerified = true,
                    isFavorite = fav
                )
            )

            withContext(Dispatchers.Main) {
                call.resolve(JSObject().apply { put("success", true) })
            }
        }
    }

    @PluginMethod
    fun deleteProfile(call: PluginCall) {
        val id = call.getString("id") ?: ""
        vpnManager.profileRepository.deleteProfile(id)
        call.resolve(JSObject().apply { put("success", true) })
    }

    @PluginMethod
    fun toggleFavorite(call: PluginCall) {
        val id = call.getString("id") ?: ""
        val isFav = vpnManager.profileRepository.toggleFavorite(id)
        call.resolve(JSObject().apply {
            put("success", true)
            put("isFavorite", isFav)
        })
    }

    @PluginMethod
    fun duplicateProfile(call: PluginCall) {
        val id = call.getString("id") ?: ""
        val dup = vpnManager.profileRepository.duplicateProfile(id)
        if (dup != null) {
            call.resolve(JSObject().apply {
                put("success", true)
                put("id", dup.id)
                put("name", dup.name)
            })
        } else {
            call.resolve(JSObject().apply {
                put("success", false)
                put("errorMessage", "Profile not found")
            })
        }
    }

    @PluginMethod
    fun readClipboard(call: PluginCall) {
        activity?.runOnUiThread {
            try {
                val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? ClipboardManager
                val clipData = clipboard?.primaryClip
                val text = if (clipData != null && clipData.itemCount > 0) {
                    clipData.getItemAt(0)?.coerceToText(context)?.toString() ?: ""
                } else {
                    ""
                }
                call.resolve(JSObject().apply {
                    put("success", true)
                    put("text", text.trim())
                })
            } catch (e: Exception) {
                call.resolve(JSObject().apply {
                    put("success", false)
                    put("text", "")
                    put("error", e.message ?: "Failed to read clipboard")
                })
            }
        } ?: run {
            call.resolve(JSObject().apply {
                put("success", false)
                put("text", "")
                put("error", "Activity not available")
            })
        }
    }

    @PluginMethod
    fun writeClipboard(call: PluginCall) {
        val text = call.getString("text") ?: ""
        activity?.runOnUiThread {
            try {
                val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as? ClipboardManager
                val clip = ClipData.newPlainText("CloudDriveLeech", text)
                clipboard?.setPrimaryClip(clip)
                call.resolve(JSObject().apply { put("success", true) })
            } catch (e: Exception) {
                call.resolve(JSObject().apply {
                    put("success", false)
                    put("error", e.message ?: "Failed to write clipboard")
                })
            }
        } ?: run {
            call.resolve(JSObject().apply {
                put("success", false)
                put("error", "Activity not available")
            })
        }
    }

    @PluginMethod
    fun exportProfiles(call: PluginCall) {
        val json = vpnManager.profileRepository.exportProfilesJson()
        call.resolve(JSObject().apply {
            put("success", true)
            put("json", json)
        })
    }

    @PluginMethod
    fun importProfiles(call: PluginCall) {
        val json = call.getString("json") ?: ""
        val count = vpnManager.profileRepository.importProfilesJson(json)
        call.resolve(JSObject().apply {
            put("success", count > 0)
            put("count", count)
        })
    }

    @PluginMethod
    fun decodeQrCode(call: PluginCall) {
        val base64 = call.getString("image") ?: ""
        if (base64.isBlank()) {
            call.resolve(JSObject().apply {
                put("success", false)
                put("errorMessage", "Image data is empty")
            })
            return
        }
        val text = QrCodeDecoder.decodeFromBase64(base64)
        if (!text.isNullOrBlank()) {
            call.resolve(JSObject().apply {
                put("success", true)
                put("text", text)
            })
        } else {
            call.resolve(JSObject().apply {
                put("success", false)
                put("errorMessage", "No valid QR code detected in image")
            })
        }
    }

    @PluginMethod
    fun testConfigDiagnostics(call: PluginCall) {
        val payload = call.getString("payload") ?: (call.getString("config") ?: "")
        if (payload.isBlank()) {
            call.resolve(JSObject().apply {
                put("success", false)
                put("errorMessage", "Payload cannot be empty")
            })
            return
        }

        scope.launch(Dispatchers.IO) {
            val parseRes = VlessParser.parse(payload)
            if (parseRes is ParseResult.Error) {
                withContext(Dispatchers.Main) {
                    call.resolve(JSObject().apply {
                        put("level1SyntaxOk", false)
                        put("level2FieldsOk", false)
                        put("level3ConfigOk", false)
                        put("level4DnsOk", false)
                        put("level5ServerReachable", false)
                        put("level6TunnelOk", false)
                        put("failureStage", "LEVEL_1_SYNTAX")
                        put("failureCode", parseRes.code)
                        put("failureReason", parseRes.message)
                    })
                }
                return@launch
            }

            val cfg = (parseRes as ParseResult.Success).config
            val report = ConnectivityTester.runMultiLevelDiagnostics(cfg)
            withContext(Dispatchers.Main) {
                call.resolve(JSObject().apply {
                    put("level1SyntaxOk", report.level1SyntaxOk)
                    put("level2FieldsOk", report.level2FieldsOk)
                    put("level3ConfigOk", report.level3ConfigOk)
                    put("level4DnsOk", report.level4DnsOk)
                    put("resolvedIp", report.resolvedIp ?: "")
                    put("level5ServerReachable", report.level5ServerReachable)
                    put("realLatencyMs", report.realLatencyMs ?: -1L)
                    put("level6TunnelOk", report.level6TunnelOk)
                    put("failureStage", report.failureStage ?: "")
                    put("failureCode", report.failureCode ?: "")
                    put("failureReason", report.failureReason ?: "")
                })
            }
        }
    }

    @PluginMethod
    fun getSubscriptions(call: PluginCall) {
        val list = subManager.getAllSubscriptions()
        val arr = JSArray()
        for (s in list) {
            arr.put(JSObject().apply {
                put("id", s.id)
                put("name", s.name)
                put("url", s.url)
                put("lastUpdated", s.lastUpdated)
                put("profileCount", s.profileCount)
                put("lastError", s.lastError ?: "")
            })
        }
        call.resolve(JSObject().apply { put("subscriptions", arr) })
    }

    @PluginMethod
    fun saveSubscription(call: PluginCall) {
        val name = call.getString("name") ?: "Subscription"
        val url = call.getString("url") ?: ""
        if (url.isBlank()) {
            call.resolve(JSObject().apply { put("success", false); put("errorMessage", "URL cannot be empty") })
            return
        }
        val sub = subManager.addOrUpdateSubscription(name, url)
        call.resolve(JSObject().apply { put("success", true); put("id", sub.id) })
    }

    @PluginMethod
    fun updateSubscription(call: PluginCall) {
        val id = call.getString("id") ?: ""
        scope.launch(Dispatchers.IO) {
            val (success, msg) = subManager.updateSubscription(id)
            withContext(Dispatchers.Main) {
                call.resolve(JSObject().apply {
                    put("success", success)
                    put("message", msg ?: "")
                })
            }
        }
    }

    @PluginMethod
    fun deleteSubscription(call: PluginCall) {
        val id = call.getString("id") ?: ""
        subManager.deleteSubscription(id)
        call.resolve(JSObject().apply { put("success", true) })
    }

    @PluginMethod
    fun getRoutingConfig(call: PluginCall) {
        val cfg = routingManager.getRoutingConfig()
        val pkgs = JSArray()
        for (p in cfg.selectedPackages) pkgs.put(p)
        call.resolve(JSObject().apply {
            put("mode", cfg.mode.name)
            put("selectedPackages", pkgs)
            put("primaryDns", cfg.primaryDns)
            put("secondaryDns", cfg.secondaryDns)
            put("mtu", cfg.mtu)
            put("autoReconnect", cfg.autoReconnect)
        })
    }

    @PluginMethod
    fun saveRoutingConfig(call: PluginCall) {
        val modeStr = call.getString("mode") ?: VpnRoutingMode.FULL_VPN.name
        val mode = try { VpnRoutingMode.valueOf(modeStr) } catch (_: Exception) { VpnRoutingMode.FULL_VPN }
        val pkgsArr = call.getArray("selectedPackages")
        val pkgs = mutableSetOf<String>()
        if (pkgsArr != null) {
            for (i in 0 until pkgsArr.length()) {
                pkgs.add(pkgsArr.getString(i))
            }
        }
        val dns1 = call.getString("primaryDns") ?: "1.1.1.1"
        val dns2 = call.getString("secondaryDns") ?: "8.8.8.8"
        val mtu = call.getInt("mtu") ?: 1400
        val autoReconnect = call.getBoolean("autoReconnect") ?: true

        routingManager.saveRoutingConfig(
            VpnRoutingConfig(
                mode = mode,
                selectedPackages = pkgs,
                primaryDns = dns1,
                secondaryDns = dns2,
                mtu = mtu,
                autoReconnect = autoReconnect
            )
        )
        call.resolve(JSObject().apply { put("success", true) })
    }

    @PluginMethod
    fun getInstalledApps(call: PluginCall) {
        val apps = routingManager.getInstalledApplications()
        val arr = JSArray()
        for (a in apps) {
            arr.put(JSObject().apply {
                put("packageName", a.packageName)
                put("appName", a.appName)
                put("isSystemApp", a.isSystemApp)
            })
        }
        call.resolve(JSObject().apply { put("apps", arr) })
    }

    @PluginMethod
    fun getLogs(call: PluginCall) {
        val limit = call.getInt("limit") ?: 100
        val level = call.getString("level")
        val logs = VpnLogger.getLogs(limit, level)
        val arr = JSArray()
        for (l in logs) {
            arr.put(JSObject().apply {
                put("timestamp", l.timestamp)
                put("time", l.timeFormatted)
                put("level", l.level)
                put("message", l.message)
            })
        }
        call.resolve(JSObject().apply { put("logs", arr) })
    }

    @PluginMethod
    fun clearLogs(call: PluginCall) {
        VpnLogger.clearLogs()
        call.resolve(JSObject().apply { put("success", true) })
    }

    @PluginMethod
    fun exportSafeLogs(call: PluginCall) {
        val text = VpnLogger.exportSafeLogs()
        call.resolve(JSObject().apply {
            put("success", true)
            put("logs", text)
        })
    }

    @PluginMethod
    fun isIgnoringBatteryOptimizations(call: PluginCall) {
        try {
            val pm = context.getSystemService(android.content.Context.POWER_SERVICE) as? android.os.PowerManager
            val isIgnoring = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                pm?.isIgnoringBatteryOptimizations(context.packageName) == true
            } else {
                true
            }
            call.resolve(JSObject().apply {
                put("isIgnoring", isIgnoring)
            })
        } catch (e: Exception) {
            call.resolve(JSObject().apply {
                put("isIgnoring", false)
                put("error", e.message ?: "Failed to check battery optimization")
            })
        }
    }

    @PluginMethod
    fun requestBatteryOptimizationExemption(call: PluginCall) {
        activity?.runOnUiThread {
            try {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                    val pm = context.getSystemService(android.content.Context.POWER_SERVICE) as? android.os.PowerManager
                    if (pm?.isIgnoringBatteryOptimizations(context.packageName) != true) {
                        val intent = android.content.Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                            data = android.net.Uri.parse("package:${context.packageName}")
                            flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        context.startActivity(intent)
                        call.resolve(JSObject().apply {
                            put("requested", true)
                            put("alreadyExempt", false)
                        })
                        return@runOnUiThread
                    }
                }
                call.resolve(JSObject().apply {
                    put("requested", false)
                    put("alreadyExempt", true)
                })
            } catch (e: Exception) {
                // Fallback to general battery settings
                try {
                    val fallbackIntent = android.content.Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                        flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(fallbackIntent)
                    call.resolve(JSObject().apply {
                        put("requested", true)
                        put("fallback", true)
                    })
                } catch (ex: Exception) {
                    call.resolve(JSObject().apply {
                        put("requested", false)
                        put("error", ex.message ?: "Failed to open battery settings")
                    })
                }
            }
        } ?: run {
            call.resolve(JSObject().apply {
                put("requested", false)
                put("error", "Activity unavailable")
            })
        }
    }
}
