package com.clouddrive.leech.vpn.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.clouddrive.leech.MainActivity
import com.clouddrive.leech.vpn.VpnEngineManager
import com.clouddrive.leech.vpn.core.XrayConfigBuilder
import com.clouddrive.leech.vpn.core.XrayCoreManager
import com.clouddrive.leech.vpn.routing.VpnRoutingManager
import com.clouddrive.leech.vpn.routing.VpnRoutingMode
import com.clouddrive.leech.vpn.util.VpnLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class NativeVpnService : VpnService() {

    private var vpnInterface: ParcelFileDescriptor? = null
    private var connectivityManager: ConnectivityManager? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    @Volatile
    private var underlyingNetwork: Network? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var statsJob: Job? = null

    companion object {
        const val ACTION_CONNECT = "com.clouddrive.leech.vpn.CONNECT"
        const val ACTION_DISCONNECT = "com.clouddrive.leech.vpn.DISCONNECT"
        const val NOTIFICATION_CHANNEL_ID = "vpn_channel_cyber"
        const val NOTIFICATION_ID = 2026

        @Volatile
        var instance: NativeVpnService? = null
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        VpnLogger.i("NativeVpnService created")
        createNotificationChannel()
        registerNetworkChangeCallback()
        XrayCoreManager.init(applicationContext)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CONNECT -> {
                val host = intent.getStringExtra("host") ?: ""
                val port = intent.getIntExtra("port", 443)
                val name = intent.getStringExtra("name") ?: "V2Ray Tunnel"
                val flag = intent.getStringExtra("flag") ?: "🌐"
                stopTunnel()
                startForeground(NOTIFICATION_ID, buildNotification("Connecting to $flag $name...", "Establishing secure TUN interface"))
                establishTunnel(host, port, name, flag)
            }
            ACTION_DISCONNECT -> {
                stopTunnel()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return if (vpnInterface != null || intent?.action == ACTION_CONNECT) {
            START_STICKY
        } else {
            START_NOT_STICKY
        }
    }

    private fun establishTunnel(host: String, port: Int, name: String, flag: String) {
        try {
            VpnLogger.i("ESTABLISHING_TUN: Configuring Android VpnService Builder")
            val routingMgr = VpnRoutingManager(applicationContext)
            val routingCfg = routingMgr.getRoutingConfig()

            // Acquire Partial WakeLock to guarantee uninterrupted background operation for all apps
            try {
                val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager
                wakeLock = pm?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "CloudDrive:VpnTunnelWakeLock")?.apply {
                    setReferenceCounted(false)
                    acquire(24 * 60 * 60 * 1000L) // 24 hours safeguard
                }
                VpnLogger.i("WakeLock acquired for uninterrupted background VPN operation")
            } catch (e: Exception) {
                VpnLogger.w("Could not acquire WakeLock: ${e.message}")
            }

            val builder = Builder()
                .setSession("CloudDrive V2Ray/Xray Tunnel")
                .setMtu(routingCfg.mtu.coerceIn(1280, 1500))
                // Dual-stack TUN interface IPv4 address
                .addAddress("10.233.233.1", 30)
                .addDnsServer(routingCfg.primaryDns)
                .addDnsServer(routingCfg.secondaryDns)
                .addDnsServer("1.1.1.1")
                .addDnsServer("8.8.8.8")

            // Dynamic Per-App Routing (Never combine addAllowedApplication with addDisallowedApplication)
            when (routingCfg.mode) {
                VpnRoutingMode.PER_APP_ALLOW -> {
                    for (pkg in routingCfg.selectedPackages) {
                        if (pkg != packageName) {
                            try { builder.addAllowedApplication(pkg) } catch (_: Exception) {}
                        }
                    }
                }
                VpnRoutingMode.PER_APP_DISALLOW -> {
                    builder.addDisallowedApplication(packageName) // Prevent app itself and Xray socket from looping
                    for (pkg in routingCfg.selectedPackages) {
                        if (pkg != packageName) {
                            try { builder.addDisallowedApplication(pkg) } catch (_: Exception) {}
                        }
                    }
                }
                else -> {
                    // FULL_VPN or BYPASS_LAN: disallow current app to prevent Xray socket routing loop,
                    // while 100% of other background apps and system services are routed into the VPN tunnel
                    builder.addDisallowedApplication(packageName)
                }
            }

            // Dual-stack IPv4 Routes: Capture 100% of IPv4 traffic
            try {
                builder.addRoute("0.0.0.0", 0)
            } catch (e: Exception) {
                VpnLogger.d("0.0.0.0/0 route fallback to dual /1: ${e.message}")
                builder.addRoute("0.0.0.0", 1)
                builder.addRoute("128.0.0.0", 1)
            }

            // Dual-stack IPv6 Routes: dual-stack support to prevent IPv6 DNS/traffic leaks
            try {
                builder.addAddress("fdfe:dcba:9876::1", 126)
                builder.addRoute("::", 0)
            } catch (e: Exception) {
                VpnLogger.d("IPv6 route fallback to dual /1: ${e.message}")
                try {
                    builder.addRoute("::", 1)
                    builder.addRoute("8000::", 1)
                } catch (_: Exception) {}
            }

            // Open TUN interface
            vpnInterface = builder.establish()
            if (vpnInterface == null) {
                VpnLogger.e("TUN_ESTABLISH_FAILED: Builder.establish() returned null. User may have revoked permission.")
                VpnEngineManager.getInstance(applicationContext).onTunnelEstablishFailed("VPN Permission Revoked or TUN Creation Failed")
                stopSelf()
                return
            }

            val pfd = vpnInterface!!
            VpnLogger.i("ESTABLISHING_TUN SUCCESS: TUN FileDescriptor allocated (FD: ${pfd.fd})")

            // Immediately bind the current physical network to the VPN interface
            val initialNet = underlyingNetwork ?: findUsableUnderlyingNetwork()
            updateUnderlyingNetwork(initialNet)

            startForeground(NOTIFICATION_ID, buildNotification("🛡️ Connected: $flag $name", "Real-Time Encrypted Xray-Core Tunnel Active"))

            // Build official Xray JSON Configuration
            val activeCfg = VpnEngineManager.getInstance(applicationContext).activeConfig
            if (activeCfg == null) {
                VpnLogger.e("TUN_ESTABLISH_FAILED: Active VPN profile config is null")
                stopTunnel()
                VpnEngineManager.getInstance(applicationContext).onTunnelEstablishFailed("Invalid profile config")
                stopSelf()
                return
            }

            val xrayJson = XrayConfigBuilder.buildConfig(activeCfg, routingCfg, enableTunInbound = true)
            VpnLogger.i("XrayConfigBuilder: Generated valid Xray-core configuration")

            // Start native Xray-core processing loop
            val coreStarted = XrayCoreManager.start(xrayJson, pfd.fd)
            if (!coreStarted) {
                VpnLogger.e("TUN_ESTABLISH_FAILED: XrayCoreManager failed to start native core")
                stopTunnel()
                VpnEngineManager.getInstance(applicationContext).onTunnelEstablishFailed("Xray native core failed to start")
                stopSelf()
                return
            }

            // Route internal Java/Kotlin HTTP/HTTPS traffic through local Xray proxy
            try {
                System.setProperty("http.proxyHost", "127.0.0.1")
                System.setProperty("http.proxyPort", "10809")
                System.setProperty("https.proxyHost", "127.0.0.1")
                System.setProperty("https.proxyPort", "10809")
                VpnLogger.i("Set JVM HTTP proxy to 127.0.0.1:10809 for internal networking")
            } catch (e: Exception) {
                VpnLogger.w("Failed to set JVM proxy properties: ${e.message}")
            }

            // Set Android WebView Proxy Override for Capacitor & In-App Browser
            try {
                if (androidx.webkit.WebViewFeature.isFeatureSupported(androidx.webkit.WebViewFeature.PROXY_OVERRIDE)) {
                    val proxyConfig = androidx.webkit.ProxyConfig.Builder()
                        .addProxyRule("127.0.0.1:10809")
                        .addDirect()
                        .build()
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        androidx.webkit.ProxyController.getInstance().setProxyOverride(proxyConfig, { it.run() }) {}
                        VpnLogger.i("Applied WebView PROXY_OVERRIDE to 127.0.0.1:10809")
                    }
                }
            } catch (e: Throwable) {
                VpnLogger.w("WebView proxy override error: ${e.message}")
            }

            VpnEngineManager.getInstance(applicationContext).onTunnelEstablished()

        } catch (e: Exception) {
            VpnLogger.e("TUN_ESTABLISH_FAILED: ${e.message}", e)
            stopTunnel()
            VpnEngineManager.getInstance(applicationContext).onTunnelEstablishFailed(e.message ?: "TUN establish failed")
            stopSelf()
        }
    }

    fun updateNotificationTraffic(downStr: String, upStr: String, pingMs: Long, flag: String, name: String) {
        val notif = buildNotification(
            "🛡️ $flag $name • ${pingMs}ms ⚡",
            "↓ $downStr  |  ↑ $upStr  •  Encrypted Xray Tunnel Active"
        )
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, notif)
    }

    private fun stopTunnel() {
        VpnLogger.i("NativeVpnService: Closing TUN and stopping Xray core")
        statsJob?.cancel()
        statsJob = null

        // Clear JVM system proxy properties
        try {
            System.clearProperty("http.proxyHost")
            System.clearProperty("http.proxyPort")
            System.clearProperty("https.proxyHost")
            System.clearProperty("https.proxyPort")
        } catch (_: Exception) {}

        // Clear Android WebView Proxy Override
        try {
            if (androidx.webkit.WebViewFeature.isFeatureSupported(androidx.webkit.WebViewFeature.PROXY_OVERRIDE)) {
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    androidx.webkit.ProxyController.getInstance().clearProxyOverride({ it.run() }) {}
                    VpnLogger.i("Cleared WebView PROXY_OVERRIDE")
                }
            }
        } catch (_: Throwable) {}

        XrayCoreManager.stop()

        try {
            vpnInterface?.close()
        } catch (_: Exception) {}
        vpnInterface = null

        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
                VpnLogger.i("WakeLock released cleanly")
            }
        } catch (_: Exception) {}
        wakeLock = null
    }

    private fun registerNetworkChangeCallback() {
        connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val req = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            // Never receive the VPN network itself here. Using tun0 as the
            // underlying network can break Wi-Fi reconnects and background traffic.
            .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
            .build()

        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                if (!isUsableUnderlyingNetwork(network)) return
                val prevNetwork = underlyingNetwork
                underlyingNetwork = network
                val caps = connectivityManager?.getNetworkCapabilities(network)
                val isWifi = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
                val isCellular = caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true
                val typeStr = when {
                    isWifi -> "Wi-Fi"
                    isCellular -> "Cellular"
                    else -> "Other"
                }

                VpnLogger.i("Android Physical Network Available: $network ($typeStr)")
                updateUnderlyingNetwork(network)

                if (prevNetwork != null && prevNetwork != network) {
                    // Physical network handover (e.g. WiFi -> Cellular or Cellular -> WiFi)
                    VpnLogger.i("HANDOVER DETECTED: Physical network switched to $typeStr ($network)")
                    VpnEngineManager.getInstance(applicationContext).onPhysicalNetworkHandover(network, isWifi)
                } else {
                    VpnEngineManager.getInstance(applicationContext).onNetworkAvailable()
                }
            }

            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                if (network == underlyingNetwork) {
                    val isWifi = networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                    val isCellular = networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
                    val validated = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                    VpnLogger.d("Physical Network Capabilities: $network validated=$validated, wifi=$isWifi, cellular=$isCellular")
                }
            }

            override fun onLost(network: Network) {
                if (underlyingNetwork != network) return
                val replacement = findUsableUnderlyingNetwork(excluding = network)
                underlyingNetwork = replacement
                if (replacement != null) {
                    val repCaps = connectivityManager?.getNetworkCapabilities(replacement)
                    val isWifi = repCaps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
                    val isCellular = repCaps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true
                    val typeStr = when {
                        isWifi -> "Wi-Fi"
                        isCellular -> "Cellular"
                        else -> "Other"
                    }
                    VpnLogger.i("Android Physical Network Handover on lost: $network -> $replacement ($typeStr)")
                    updateUnderlyingNetwork(replacement)
                    VpnEngineManager.getInstance(applicationContext).onPhysicalNetworkHandover(replacement, isWifi)
                } else {
                    VpnLogger.w("Android Physical Network Lost (no replacement available): $network")
                    updateUnderlyingNetwork(null)
                    VpnEngineManager.getInstance(applicationContext).onNetworkLost()
                }
            }
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                // registerDefaultNetworkCallback is the most authoritative way on Android 7.0+
                // to receive OS routing transitions between WiFi and Cellular
                try {
                    connectivityManager?.registerDefaultNetworkCallback(networkCallback!!)
                    VpnLogger.i("Registered default network callback for seamless WiFi/Cellular handover")
                    return
                } catch (e: Exception) {
                    VpnLogger.w("registerDefaultNetworkCallback failed, falling back to registerNetworkCallback: ${e.message}")
                }
            }
            connectivityManager?.registerNetworkCallback(req, networkCallback!!)
        } catch (e: Exception) {
            VpnLogger.e("Failed to register network callback: ${e.message}")
        }
    }

    private fun isUsableUnderlyingNetwork(network: Network): Boolean {
        val caps = connectivityManager?.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN) &&
            (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET))
    }

    private fun findUsableUnderlyingNetwork(excluding: Network? = null): Network? {
        val manager = connectivityManager ?: return null
        return manager.allNetworks
            .asSequence()
            .filter { it != excluding && isUsableUnderlyingNetwork(it) }
            .maxByOrNull { network ->
                val caps = manager.getNetworkCapabilities(network)
                when {
                    caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true -> 2
                    caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true -> 1
                    else -> 0
                }
            }
    }

    fun updateUnderlyingNetwork(network: Network?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            try {
                if (network != null) {
                    setUnderlyingNetworks(arrayOf(network))
                    VpnLogger.d("setUnderlyingNetworks explicitly bound to: $network")
                } else {
                    setUnderlyingNetworks(null)
                    VpnLogger.d("setUnderlyingNetworks cleared (default OS routing)")
                }
            } catch (e: Exception) {
                VpnLogger.d("Unable to update VPN underlying network: ${e.message}")
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "V2Ray / Xray Proxy Tunnel",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows live connection status and real-time data metrics"
                setShowBadge(false)
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(title: String, content: String): Notification {
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onDestroy() {
        VpnLogger.i("NativeVpnService onDestroy")
        stopTunnel()
        try {
            networkCallback?.let { connectivityManager?.unregisterNetworkCallback(it) }
        } catch (_: Exception) {}
        underlyingNetwork = null
        instance = null
        super.onDestroy()
    }

    override fun onRevoke() {
        VpnLogger.w("Android VPN Permission REVOKED by user / system")
        VpnEngineManager.getInstance(applicationContext).disconnect("VPN permission revoked")
        super.onRevoke()
    }
}
