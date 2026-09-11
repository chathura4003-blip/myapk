package com.clouddrive.leech.vpn.service

import com.clouddrive.leech.vpn.VpnEngineManager
import com.clouddrive.leech.vpn.model.VpnStatus
import com.clouddrive.leech.vpn.net.ConnectivityTester
import com.clouddrive.leech.vpn.net.TcpCheckResult
import com.clouddrive.leech.vpn.util.VpnLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class NodeHealthMonitor(private val manager: VpnEngineManager) {

    private val scope = CoroutineScope(Dispatchers.IO)
    private var monitorJob: Job? = null
    private var consecutiveFailures = 0

    private val probeEndpoints = listOf(
        "https://connectivitycheck.gstatic.com/generate_204",
        "https://cp.cloudflare.com/generate_204",
        "https://www.google.com/generate_204"
    )

    fun start() {
        stop()
        consecutiveFailures = 0
        VpnLogger.i("HEALTH MONITOR: Starting periodic background health probes (interval=6s)")

        monitorJob = scope.launch {
            var endpointIdx = 0
            while (isActive) {
                delay(6000)
                if (manager.currentState.status != VpnStatus.CONNECTED && manager.currentState.status != VpnStatus.DEGRADED) {
                    break
                }

                val currentConfig = manager.activeConfig ?: break

                val probeUrl = probeEndpoints[endpointIdx % probeEndpoints.size]
                endpointIdx++

                val realCoreDelay = if (com.clouddrive.leech.vpn.core.XrayCoreManager.isRunning) {
                    com.clouddrive.leech.vpn.core.XrayCoreManager.measureDelay("", probeUrl)
                } else {
                    -1L
                }

                if (realCoreDelay > 0) {
                    consecutiveFailures = 0
                    if (manager.currentState.status == VpnStatus.DEGRADED) {
                        manager.updateStatus(VpnStatus.CONNECTED)
                    }
                    manager.updateLivePing(realCoreDelay)
                } else {
                    val tcpRes = ConnectivityTester.checkTcpReachability(currentConfig, timeoutMs = 3500)
                    when (tcpRes) {
                        is TcpCheckResult.Success -> {
                            consecutiveFailures = 0
                            if (manager.currentState.status == VpnStatus.DEGRADED) {
                                manager.updateStatus(VpnStatus.CONNECTED)
                            }
                            manager.updateLivePing(tcpRes.latencyMs)
                        }
                        is TcpCheckResult.Failure -> {
                            consecutiveFailures++
                            VpnLogger.w("HEALTH MONITOR: Node probe failure #$consecutiveFailures (${tcpRes.message})")

                            if (consecutiveFailures in 3..5) {
                                manager.updateStatus(VpnStatus.DEGRADED, "Network latency high / degraded")
                            } else if (consecutiveFailures > 5) {
                                VpnLogger.e("HEALTH MONITOR: Consecutive probe timeouts. Triggering auto-heal...")
                                manager.onProbeTimeoutAutoHeal()
                                break
                            }
                        }
                    }
                }
            }
        }
    }

    fun stop() {
        monitorJob?.cancel()
        monitorJob = null
        consecutiveFailures = 0
    }
}
