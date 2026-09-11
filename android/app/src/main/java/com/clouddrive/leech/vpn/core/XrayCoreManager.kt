package com.clouddrive.leech.vpn.core

import android.content.Context
import android.net.ConnectivityManager
import android.os.Build
import android.system.OsConstants
import com.clouddrive.leech.vpn.util.VpnLogger
import go.Seq
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import libv2ray.CoreCallbackHandler
import libv2ray.CoreController
import libv2ray.Libv2ray
import libv2ray.ProcessFinder
import java.io.File
import java.io.FileOutputStream
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Authoritative Xray-Core Controller & Native Lifecycle Manager.
 * Direct bridge to libv2ray.aar (Gomobile official Xray-core).
 */
object XrayCoreManager {

    private val isInitialized = AtomicBoolean(false)
    private var coreController: CoreController? = null
    private var appContext: Context? = null

    private val totalUploadBytes = AtomicLong(0L)
    private val totalDownloadBytes = AtomicLong(0L)

    val isRunning: Boolean
        get() = coreController?.isRunning == true

    /**
     * Initializes the Xray native environment and asset directory.
     * Safe to call multiple times (idempotent).
     */
    fun init(context: Context) {
        try {
            appContext = context.applicationContext
            Seq.setContext(context.applicationContext)
            val assetDir = File(context.filesDir, "xray_assets").apply {
                if (!exists()) mkdirs()
            }

            if (isInitialized.compareAndSet(false, true)) {
                // Copy routing rule dat files from assets to internal storage if needed
                copyAssetFile(context, "geoip.dat", File(assetDir, "geoip.dat"))
                copyAssetFile(context, "geosite.dat", File(assetDir, "geosite.dat"))
            }

            // Leave deviceId empty ("") so Xray-core's ensureBaseKey() automatically generates
            // a cryptographically secure 32-byte XUDP BaseKey without RawURLEncoding mismatches.
            Libv2ray.initCoreEnv(assetDir.absolutePath, "")
            VpnLogger.i("XrayCoreManager: Initialized Xray environment at ${assetDir.absolutePath}")
        } catch (e: Throwable) {
            VpnLogger.e("XrayCoreManager: Init error: ${e.message}", e)
        }
    }

    /**
     * Starts the native Xray-core processing loop with the provided JSON configuration and TUN file descriptor.
     */
    @Synchronized
    fun start(configJson: String, tunFd: Int): Boolean {
        if (isRunning) {
            VpnLogger.w("XrayCoreManager: Core is already running, skipping start")
            return true
        }

        try {
            VpnLogger.i("XrayCoreManager: Starting native Xray-core with TUN FD: $tunFd")
            val callbackHandler = object : CoreCallbackHandler {
                override fun startup(): Long {
                    VpnLogger.i("XrayCoreManager [Native Callback]: Core started successfully")
                    return 0L
                }

                override fun shutdown(): Long {
                    VpnLogger.i("XrayCoreManager [Native Callback]: Core shut down")
                    return 0L
                }

                override fun onEmitStatus(code: Long, message: String?): Long {
                    VpnLogger.d("XrayCoreManager [Status $code]: $message")
                    return 0L
                }
            }

            coreController = Libv2ray.newCoreController(callbackHandler)
            // The native core needs the owning UID when it opens sockets from the TUN.
            // Without this callback, Android can route the core's own connection back
            // into the VPN and the tunnel appears connected but carries no traffic.
            coreController?.registerProcessFinder(object : ProcessFinder {
                override fun findProcessByConnection(
                    network: String?,
                    source: String?,
                    sourcePort: Long,
                    destination: String?,
                    destinationPort: Long
                ): Long {
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || source.isNullOrBlank() || destination.isNullOrBlank()) {
                        return -1L
                    }

                    return try {
                        val protocol = if (network.equals("udp", ignoreCase = true)) {
                            OsConstants.IPPROTO_UDP
                        } else {
                            OsConstants.IPPROTO_TCP
                        }
                        val connectivity = appContext?.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                            ?: return -1L
                        connectivity.getConnectionOwnerUid(
                            protocol,
                            InetSocketAddress(source, sourcePort.toInt()),
                            InetSocketAddress(destination, destinationPort.toInt())
                        ).toLong()
                    } catch (e: Exception) {
                        VpnLogger.d("XrayCoreManager: process lookup unavailable: ${e.message}")
                        -1L
                    }
                }
            })
            coreController?.startLoop(configJson, tunFd)

            val started = coreController?.isRunning == true
            if (started) {
                VpnLogger.i("XrayCoreManager: Xray-core successfully running!")
                totalUploadBytes.set(0L)
                totalDownloadBytes.set(0L)
            } else {
                VpnLogger.e("XrayCoreManager: Core reported not running after startLoop")
            }
            return started
        } catch (e: Exception) {
            VpnLogger.e("XrayCoreManager: Failed to start Xray core: ${e.message}", e)
            stop()
            return false
        }
    }

    /**
     * Stops the native Xray-core and releases all native resources.
     */
    @Synchronized
    fun stop() {
        try {
            if (coreController?.isRunning == true) {
                VpnLogger.i("XrayCoreManager: Stopping Xray core...")
                coreController?.stopLoop()
            }
        } catch (e: Exception) {
            VpnLogger.e("XrayCoreManager: Error during stopLoop: ${e.message}", e)
        } finally {
            coreController = null
            VpnLogger.i("XrayCoreManager: Core stopped and cleaned up")
        }
    }

    /**
     * Queries real delta traffic counters from the native Xray stats manager since last query.
     * CoreController.queryAllOutboundTrafficStats() returns and resets counters.
     * Output format from core: "tag,direction,value;tag,direction,value;"
     * Returns Pair(uploadDelta, downloadDelta).
     */
    fun queryTrafficDelta(): Pair<Long, Long> {
        val controller = coreController ?: return Pair(0L, 0L)
        if (!controller.isRunning) return Pair(0L, 0L)

        var upDelta = 0L
        var downDelta = 0L

        try {
            val statsRaw = controller.queryAllOutboundTrafficStats()
            if (!statsRaw.isNullOrBlank()) {
                VpnLogger.d("XrayCoreManager: queryTrafficStats raw: '$statsRaw'")
                val parts = statsRaw.split(";")
                for (part in parts) {
                    val tokens = part.trim().split(",")
                    if (tokens.size >= 3) {
                        val direction = tokens[1].lowercase()
                        val bytes = tokens[2].toLongOrNull() ?: 0L
                        if (direction == "uplink") {
                            upDelta += bytes
                            totalUploadBytes.addAndGet(bytes)
                        } else if (direction == "downlink") {
                            downDelta += bytes
                            totalDownloadBytes.addAndGet(bytes)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            VpnLogger.d("XrayCoreManager: queryTrafficStats error: ${e.message}")
        }

        return Pair(upDelta, downDelta)
    }

    /**
     * Queries real cumulative traffic counters from the native Xray stats manager.
     * Returns Pair(totalUploadBytes, totalDownloadBytes).
     */
    fun queryTrafficStats(): Pair<Long, Long> {
        queryTrafficDelta()
        return Pair(totalUploadBytes.get(), totalDownloadBytes.get())
    }

    /**
     * Measures real outbound latency to a test URL via Xray-core.
     * Returns delay in milliseconds, or -1 if unreachable.
     */
    suspend fun measureDelay(
        configJson: String,
        testUrl: String = "https://www.google.com/generate_204"
    ): Long = withContext(Dispatchers.IO) {
        try {
            val controller = coreController
            val delay = if (controller?.isRunning == true) {
                controller.measureDelay(testUrl)
            } else {
                Libv2ray.measureOutboundDelay(configJson, testUrl)
            }
            if (delay in 1..20000) delay else -1L
        } catch (e: Exception) {
            VpnLogger.d("XrayCoreManager: measureDelay failed: ${e.message}")
            -1L
        }
    }

    private fun copyAssetFile(context: Context, assetName: String, outFile: File) {
        if (outFile.exists() && outFile.length() > 0) return
        try {
            context.assets.open(assetName).use { input ->
                FileOutputStream(outFile).use { output ->
                    input.copyTo(output)
                }
            }
            VpnLogger.d("Copied asset $assetName to ${outFile.absolutePath}")
        } catch (_: Exception) {
            // Asset may not exist in assets/ directly if bundled in AAR
        }
    }
}
