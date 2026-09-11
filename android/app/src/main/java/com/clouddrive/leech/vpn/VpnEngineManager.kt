package com.clouddrive.leech.vpn

import android.content.Context
import android.content.Intent
import android.net.TrafficStats
import android.net.VpnService
import android.os.Build
import android.os.Process
import androidx.core.content.ContextCompat
import androidx.webkit.ProxyConfig
import androidx.webkit.ProxyController
import androidx.webkit.WebViewFeature
import com.clouddrive.leech.vpn.core.XrayCoreManager
import com.clouddrive.leech.vpn.model.VlessConfig
import com.clouddrive.leech.vpn.model.VpnRuntimeState
import com.clouddrive.leech.vpn.model.VpnStatus
import com.clouddrive.leech.vpn.net.ConnectivityTester
import com.clouddrive.leech.vpn.net.ProbeResult
import com.clouddrive.leech.vpn.net.TcpCheckResult
import com.clouddrive.leech.vpn.parser.ParseResult
import com.clouddrive.leech.vpn.parser.VlessParser
import com.clouddrive.leech.vpn.repository.VpnProfileRepository
import com.clouddrive.leech.vpn.routing.VpnRoutingManager
import com.clouddrive.leech.vpn.service.NativeVpnService
import com.clouddrive.leech.vpn.service.NodeHealthMonitor
import com.clouddrive.leech.vpn.util.VpnLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

class VpnEngineManager private constructor(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var connectJob: Job? = null
    private val healthMonitor = NodeHealthMonitor(this)
    val profileRepository = VpnProfileRepository(context)

    private val _stateFlow = MutableStateFlow(VpnRuntimeState())
    val stateFlow: StateFlow<VpnRuntimeState> = _stateFlow

    val currentState: VpnRuntimeState get() = _stateFlow.value
    var activeConfig: VlessConfig? = null
        private set

    private val downloadedBytes = AtomicLong(0L)
    private val uploadedBytes = AtomicLong(0L)
    private val lastTrafficSampleAt = AtomicLong(System.currentTimeMillis())
    private val connectionGeneration = AtomicLong(0L)
    private var trafficJob: Job? = null
    private val lastUidRx = AtomicLong(-1L)
    private val lastUidTx = AtomicLong(-1L)

    companion object {
        @Volatile
        private var INSTANCE: VpnEngineManager? = null

        @Volatile
        var globalProxy: Proxy? = null

        fun getInstance(context: Context): VpnEngineManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: VpnEngineManager(context.applicationContext).also { INSTANCE = it }
            }
        }

        fun get(): VpnEngineManager? {
            return INSTANCE
        }
    }

    fun startTrafficMonitoring() {
        trafficJob?.cancel()
        val currentRx = TrafficStats.getUidRxBytes(Process.myUid())
        val currentTx = TrafficStats.getUidTxBytes(Process.myUid())
        lastUidRx.set(if (currentRx != TrafficStats.UNSUPPORTED.toLong()) currentRx else -1L)
        lastUidTx.set(if (currentTx != TrafficStats.UNSUPPORTED.toLong()) currentTx else -1L)
        lastTrafficSampleAt.set(System.currentTimeMillis())

        trafficJob = scope.launch(Dispatchers.IO) {
            while (isActive && currentState.isRunning()) {
                delay(1000)
                val (xrayUpDelta, xrayDownDelta) = XrayCoreManager.queryTrafficDelta()

                val nowRx = TrafficStats.getUidRxBytes(Process.myUid())
                val nowTx = TrafficStats.getUidTxBytes(Process.myUid())
                val prevRx = lastUidRx.getAndSet(nowRx)
                val prevTx = lastUidTx.getAndSet(nowTx)

                val osRxDelta = if (prevRx >= 0 && nowRx >= prevRx) nowRx - prevRx else 0L
                val osTxDelta = if (prevTx >= 0 && nowTx >= prevTx) nowTx - prevTx else 0L

                // Prioritize Xray core stats if non-zero; fallback smoothly to OS TrafficStats
                val effectiveDownDelta = if (xrayDownDelta > 0) xrayDownDelta else osRxDelta
                val effectiveUpDelta = if (xrayUpDelta > 0) xrayUpDelta else osTxDelta

                recordTrafficSample(effectiveDownDelta, effectiveUpDelta)
            }
        }
    }

    fun stopTrafficMonitoring() {
        trafficJob?.cancel()
        trafficJob = null
        lastUidRx.set(-1L)
        lastUidTx.set(-1L)
    }

    fun recordTrafficSample(downDelta: Long, upDelta: Long) {
        if (!currentState.isRunning()) return

        val now = System.currentTimeMillis()
        val previousAt = lastTrafficSampleAt.getAndSet(now)
        val elapsedMs = (now - previousAt).coerceAtLeast(200)
        val seconds = elapsedMs / 1000.0

        val totalDown = downloadedBytes.addAndGet(downDelta.coerceAtLeast(0L))
        val totalUp = uploadedBytes.addAndGet(upDelta.coerceAtLeast(0L))

        val downMbps = ((downDelta.coerceAtLeast(0L) * 8.0) / seconds) / 1_000_000.0
        val upMbps = ((upDelta.coerceAtLeast(0L) * 8.0) / seconds) / 1_000_000.0

        _stateFlow.value = _stateFlow.value.copy(
            downloadBytes = totalDown,
            uploadBytes = totalUp,
            downloadMbps = downMbps,
            uploadMbps = upMbps,
            lastCheckedAt = now
        )
        syncNotificationTraffic()
    }

    fun recordDownload(bytes: Long) {
        if (currentState.isRunning() && bytes > 0) {
            recordTrafficSample(bytes, 0L)
        }
    }

    fun recordUpload(bytes: Long) {
        if (currentState.isRunning() && bytes > 0) {
            recordTrafficSample(0L, bytes)
        }
    }

    private fun syncNotificationTraffic() {
        val s = currentState
        val cfg = activeConfig ?: return
        NativeVpnService.instance?.updateNotificationTraffic(
            formatBytes(s.downloadBytes),
            formatBytes(s.uploadBytes),
            s.latencyMs ?: 0L,
            cfg.countryFlag,
            cfg.remark
        )
    }

    fun connect(rawPayload: String, onResult: (Boolean, String?) -> Unit) {
        val sessionId = VpnLogger.newSession()
        VpnLogger.i("START CONNECT SEQUENCE [$sessionId]")

        if (currentState.isRunning() || currentState.status in setOf(
                VpnStatus.PARSING,
                VpnStatus.TCP_CHECK,
                VpnStatus.HANDSHAKING,
                VpnStatus.ESTABLISHING_TUN,
                VpnStatus.CONNECTIVITY_TEST
            )) {
            disconnect("Replacing existing VPN connection")
        }

        // Cancel any older staged connection before creating a new generation.
        connectJob?.cancel()
        val generation = connectionGeneration.incrementAndGet()
        reconnectAttempts = 0

        downloadedBytes.set(0L)
        uploadedBytes.set(0L)
        lastTrafficSampleAt.set(System.currentTimeMillis())

        connectJob = scope.launch(Dispatchers.IO) {
            // Step 1: PARSING
            if (connectionGeneration.get() != generation) return@launch
            updateStatus(VpnStatus.PARSING)
            val parseResult = VlessParser.parse(rawPayload)
            if (parseResult is ParseResult.Error) {
                VpnLogger.e("PARSE ERROR: ${parseResult.code} - ${parseResult.message}")
                fail(parseResult.code, parseResult.message, onResult)
                return@launch
            }

            val config = (parseResult as ParseResult.Success).config
            if (connectionGeneration.get() != generation) return@launch
            activeConfig = config
            VpnLogger.i("PARSE SUCCESS: ${config.protocol} ${config.host}:${config.port} (${config.security})")
            if (config.autoFixes.isNotEmpty()) {
                VpnLogger.i("AUTO_REPAIR APPLIED (${config.autoFixes.size} fixes): ${config.autoFixes.joinToString("; ")}")
            }

            // Step 2: VALIDATING & STAGE A: TCP_CHECK
            updateStatus(VpnStatus.TCP_CHECK, server = config.host, port = config.port, protocol = config.protocol)
            val tcpRes = ConnectivityTester.checkTcpReachability(config, timeoutMs = 3500)
            if (connectionGeneration.get() != generation) return@launch
            if (tcpRes is TcpCheckResult.Failure) {
                VpnLogger.e("TCP CHECK FAILED: ${tcpRes.code} - ${tcpRes.message}")
                fail(tcpRes.code, tcpRes.message, onResult)
                return@launch
            }
            val tcpLatency = (tcpRes as TcpCheckResult.Success).latencyMs
            _stateFlow.value = _stateFlow.value.copy(latencyMs = tcpLatency)

            // Step 3: STAGE B: STARTING CORE & PROTOCOL HANDSHAKE
            updateStatus(VpnStatus.HANDSHAKING)
            val hsRes = ConnectivityTester.testProtocolHandshake(config)
            if (connectionGeneration.get() != generation) return@launch
            if (hsRes is ProbeResult.Failure) {
                VpnLogger.e("PROTOCOL HANDSHAKE FAILED: ${hsRes.code} - ${hsRes.message}")
                fail(hsRes.code, hsRes.message, onResult)
                return@launch
            }

            // Step 4: ESTABLISHING_TUN via NativeVpnService
            updateStatus(VpnStatus.ESTABLISHING_TUN)
            val prepareIntent = VpnService.prepare(context)
            if (prepareIntent != null) {
                VpnLogger.w("VPN_PERMISSION_REQUIRED: User must grant Android VPN permission")
                fail("VPN_PERMISSION_REQUIRED", "Please allow VPN permission in Android prompt", onResult)
                return@launch
            }

            if (connectionGeneration.get() != generation) return@launch

            // Direct Android Foreground VpnService
            val serviceIntent = Intent(context, NativeVpnService::class.java).apply {
                action = NativeVpnService.ACTION_CONNECT
                putExtra("host", config.host)
                putExtra("port", config.port)
                putExtra("name", config.remark)
                putExtra("flag", config.countryFlag)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }

            // Wait for Xray native core to bind TUN and inbounds (max 6s)
            var waitCount = 0
            while (!com.clouddrive.leech.vpn.core.XrayCoreManager.isRunning && waitCount < 60) {
                if (connectionGeneration.get() != generation) return@launch
                if (currentState.status == VpnStatus.FAILED) return@launch
                delay(100)
                waitCount++
            }

            if (!com.clouddrive.leech.vpn.core.XrayCoreManager.isRunning) {
                VpnLogger.e("CORE_START_TIMEOUT: Native Xray core did not report running within 6 seconds")
                fail("CORE_START_TIMEOUT", "VPN service did not start core in time", onResult)
                return@launch
            }

            // Step 5: STAGE C: CONNECTIVITY_TEST
            updateStatus(VpnStatus.CONNECTIVITY_TEST)
            delay(300)
            val probeRes = ConnectivityTester.probeTunnelConnectivity(timeoutMs = 6000L)
            if (connectionGeneration.get() != generation) return@launch
            val finalLatency = if (probeRes is ProbeResult.Success) {
                probeRes.latencyMs
            } else {
                val failMsg = (probeRes as? ProbeResult.Failure)?.message ?: "Probe timeout"
                VpnLogger.w("STAGE C Notice: Tunnel probe unverified ($failMsg), proceeding with TCP latency ($tcpLatency ms)")
                tcpLatency
            }

            // Step 6: CONNECTED!
            withContext(Dispatchers.Main) {
                globalProxy = Proxy(Proxy.Type.HTTP, InetSocketAddress("127.0.0.1", 10809))
                applyWebViewProxy("127.0.0.1", 10809, 10808)
                _stateFlow.value = VpnRuntimeState(
                    status = VpnStatus.CONNECTED,
                    server = config.host,
                    port = config.port,
                    protocol = config.protocol,
                    latencyMs = finalLatency,
                    countryCode = config.countryCode,
                    countryName = config.remark,
                    countryFlag = config.countryFlag,
                    uploadBytes = uploadedBytes.get(),
                    downloadBytes = downloadedBytes.get(),
                    uploadMbps = 0.0,
                    downloadMbps = 0.0,
                    sessionStartTime = System.currentTimeMillis(),
                    lastCheckedAt = System.currentTimeMillis()
                )
                VpnLogger.i("VPN TUNNEL FULLY CONNECTED AND OPERATIONAL [${finalLatency}ms]")
                healthMonitor.start()
                startTrafficMonitoring()
                onResult(true, null)
            }
        }
    }

    fun onTunnelEstablished() {
        VpnLogger.i("TUN Interface established by NativeVpnService")
        startTrafficMonitoring()
    }

    fun onTunnelEstablishFailed(reason: String) {
        VpnLogger.e("TUN interface failed: $reason")
        if (currentState.status !in setOf(
            VpnStatus.ESTABLISHING_TUN,
            VpnStatus.CONNECTIVITY_TEST,
            VpnStatus.HANDSHAKING
            )) return
        connectionGeneration.incrementAndGet()
        scope.launch(Dispatchers.IO) {
            fail("TUN_ESTABLISH_FAILED", reason) { _, _ -> }
        }
    }

    fun updateLivePing(pingMs: Long) {
        _stateFlow.value = _stateFlow.value.copy(
            latencyMs = pingMs,
            lastCheckedAt = System.currentTimeMillis()
        )
        syncNotificationTraffic()
    }

    fun updateStatus(status: VpnStatus, errorMessage: String? = null, server: String? = null, port: Int? = null, protocol: String? = null) {
        val cfg = activeConfig
        _stateFlow.value = _stateFlow.value.copy(
            status = status,
            server = server ?: cfg?.host ?: _stateFlow.value.server,
            port = port ?: cfg?.port ?: _stateFlow.value.port,
            protocol = protocol ?: cfg?.protocol ?: _stateFlow.value.protocol,
            countryCode = cfg?.countryCode ?: _stateFlow.value.countryCode,
            countryFlag = cfg?.countryFlag ?: _stateFlow.value.countryFlag,
            errorMessage = errorMessage
        )
    }

    fun onNodeDied(reason: String) {
        VpnLogger.e("NODE DIED: $reason - Enforcing Kill Switch")
        disconnect(reason)
    }

    fun onProbeTimeoutAutoHeal() {
        val cfg = activeConfig
        if (cfg != null && currentState.isRunning()) {
            val routingMgr = VpnRoutingManager(context)
            if (routingMgr.getRoutingConfig().autoReconnect) {
                VpnLogger.i("VpnEngineManager: Probe timeout auto-heal triggered. Attempting auto-reconnect...")
                triggerAutoReconnect(cfg)
                return
            }
        }
        onNodeDied("Server offline after repeated ping timeouts")
    }

    fun onNetworkLost() {
        VpnLogger.w("Underlying network disconnected - Marking DEGRADED")
        if (currentState.isRunning()) {
            updateStatus(VpnStatus.DEGRADED, "Underlying network disconnected")
        }
    }

    private var reconnectAttempts = 0
    private val MAX_RECONNECT_ATTEMPTS = 3
    private var handoverJob: Job? = null

    fun onPhysicalNetworkHandover(newNetwork: android.net.Network, isWifi: Boolean) {
        val cfg = activeConfig ?: return
        if (!currentState.isRunning() && currentState.status != VpnStatus.DEGRADED) return

        val bearerName = if (isWifi) "Wi-Fi" else "Cellular (Mobile Data)"
        VpnLogger.i("VpnEngineManager: Physical network handover to $bearerName. Migrating active tunnel sockets...")

        handoverJob?.cancel()
        handoverJob = scope.launch(Dispatchers.IO) {
            // Brief stabilization delay for network stack & DHCP/radio
            delay(500)
            if (activeConfig == null) return@launch

            val routingMgr = VpnRoutingManager(context)
            if (routingMgr.getRoutingConfig().autoReconnect) {
                VpnLogger.i("VpnEngineManager: Executing seamless handover reconnection on $bearerName...")
                connect(cfg.rawPayload) { success, err ->
                    if (success) {
                        VpnLogger.i("VpnEngineManager: Handover to $bearerName completed successfully!")
                        reconnectAttempts = 0
                    } else {
                        VpnLogger.w("VpnEngineManager: Handover reconnection warning: $err")
                    }
                }
            }
        }
    }

    fun onNetworkAvailable() {
        VpnLogger.i("Underlying network available")
        val cfg = activeConfig
        if (currentState.status == VpnStatus.DEGRADED && cfg != null && cfg.rawPayload.isNotEmpty()) {
            val routingMgr = VpnRoutingManager(context)
            if (routingMgr.getRoutingConfig().autoReconnect) {
                triggerAutoReconnect(cfg)
            }
        }
    }

    private fun triggerAutoReconnect(config: VlessConfig) {
        if (reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) {
            VpnLogger.w("Auto-reconnect exceeded max attempts ($MAX_RECONNECT_ATTEMPTS)")
            updateStatus(VpnStatus.FAILED, "Auto-reconnect failed after $MAX_RECONNECT_ATTEMPTS attempts")
            return
        }
        reconnectAttempts++
        val delayMs = reconnectAttempts * 2000L
        VpnLogger.i("Scheduling auto-reconnect attempt $reconnectAttempts in ${delayMs}ms...")
        scope.launch(Dispatchers.IO) {
            delay(delayMs)
            if (activeConfig != null) {
                connect(config.rawPayload) { success, _ ->
                    if (success) {
                        reconnectAttempts = 0
                    }
                }
            }
        }
    }

    fun disconnect(reason: String? = null) {
        VpnLogger.i("DISCONNECTING VPN: ${reason ?: "User requested"}")
        connectionGeneration.incrementAndGet()
        healthMonitor.stop()
        stopTrafficMonitoring()

        // Stop VpnService
        val serviceIntent = Intent(context, NativeVpnService::class.java).apply {
            action = NativeVpnService.ACTION_DISCONNECT
        }
        context.startService(serviceIntent)

        // Clear proxies cleanly
        globalProxy = null
        clearWebViewProxy()
        activeConfig = null
        downloadedBytes.set(0L)
        uploadedBytes.set(0L)

        _stateFlow.value = VpnRuntimeState(
            status = if (reason != null && reason != "User requested") VpnStatus.FAILED else VpnStatus.DISCONNECTED,
            errorCode = if (reason != null && reason != "User requested") "DISCONNECTED_ERROR" else null,
            errorMessage = reason
        )
    }

    private suspend fun fail(code: String, message: String, callback: (Boolean, String?) -> Unit) {
        healthMonitor.stop()
        stopTrafficMonitoring()
        globalProxy = null
        clearWebViewProxy()
        activeConfig = null

        val serviceIntent = Intent(context, NativeVpnService::class.java).apply {
            action = NativeVpnService.ACTION_DISCONNECT
        }
        context.startService(serviceIntent)

        withContext(Dispatchers.Main) {
            _stateFlow.value = VpnRuntimeState(
                status = VpnStatus.FAILED,
                errorCode = code,
                errorMessage = message
            )
            callback(false, message)
        }
    }

    private fun applyWebViewProxy(host: String, httpPort: Int = 10809, socksPort: Int = 10808) {
        try {
            if (WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)) {
                val proxyConfig = ProxyConfig.Builder()
                    .addProxyRule("http://$host:$httpPort")
                    .addProxyRule("socks5://$host:$socksPort")
                    .addBypassRule("localhost")
                    .addBypassRule("127.0.0.1")
                    .build()
                val executor = ContextCompat.getMainExecutor(context)
                ProxyController.getInstance().setProxyOverride(proxyConfig, executor, Runnable {
                    VpnLogger.i("WebView Proxy Override active: http://$host:$httpPort & socks5://$host:$socksPort")
                })
            }
            System.setProperty("http.proxyHost", host)
            System.setProperty("http.proxyPort", httpPort.toString())
            System.setProperty("https.proxyHost", host)
            System.setProperty("https.proxyPort", httpPort.toString())
            System.setProperty("socksProxyHost", host)
            System.setProperty("socksProxyPort", socksPort.toString())
        } catch (e: Exception) {
            VpnLogger.w("WebView proxy override warning: ${e.message}")
        }
    }

    private fun clearWebViewProxy() {
        try {
            if (WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)) {
                val executor = ContextCompat.getMainExecutor(context)
                ProxyController.getInstance().clearProxyOverride(executor, Runnable {
                    VpnLogger.i("WebView Proxy Override cleared")
                })
            }
        } catch (_: Exception) {}
        try {
            System.clearProperty("http.proxyHost")
            System.clearProperty("http.proxyPort")
            System.clearProperty("https.proxyHost")
            System.clearProperty("https.proxyPort")
            System.clearProperty("socksProxyHost")
            System.clearProperty("socksProxyPort")
        } catch (_: Exception) {}
    }

    fun formatBytes(bytes: Long): String {
        if (bytes <= 0) return "0.0 MB"
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        val gb = mb / 1024.0
        return when {
            gb >= 1.0 -> String.format(Locale.US, "%.2f GB", gb)
            mb >= 1.0 -> String.format(Locale.US, "%.1f MB", mb)
            else -> String.format(Locale.US, "%.0f KB", kb)
        }
    }

    fun formatSpeed(mbps: Double): String {
        if (mbps <= 0.0) return "0.0 Mbps"
        return when {
            mbps >= 1.0 -> String.format(Locale.US, "%.1f Mbps", mbps)
            else -> {
                val kbps = (mbps * 1000.0 / 8.0).toInt()
                if (kbps >= 1) "$kbps KB/s" else String.format(Locale.US, "%.2f Mbps", mbps)
            }
        }
    }
}
