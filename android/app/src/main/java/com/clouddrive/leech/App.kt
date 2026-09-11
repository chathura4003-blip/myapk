package com.clouddrive.leech

import android.app.Application
import android.util.Log
import com.clouddrive.leech.database.AppDatabase
import com.clouddrive.leech.downloader.NewPipeDownloader
import com.clouddrive.leech.extractor.ExtractorManager
import com.clouddrive.leech.extractor.providers.movies.BaseMovieScraper
import com.clouddrive.leech.extractor.providers.movies.CarrierBypassDns
import com.clouddrive.leech.proxy.LocalMediaProxy
import com.clouddrive.leech.vpn.net.UniversalAntiCensorDns
import kotlinx.coroutines.launch
import org.schabi.newpipe.extractor.NewPipe

/**
 * Main Application class for Cloud Drive Leech Standalone Android Edition.
 * Initializes NewPipe Extractor and Room Database on startup.
 */
class App : Application() {

    lateinit var database: AppDatabase
        private set

    lateinit var extractorManager: ExtractorManager
        private set

    val downloadScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO)

    companion object {
        private const val TAG = "CloudDriveLeechApp"

        lateinit var instance: App
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this

        // Enforce fast IPv4 routing across Android carrier networks where IPv6 is unroutable
        try {
            System.setProperty("java.net.preferIPv4Stack", "true")
            System.setProperty("java.net.preferIPv6Addresses", "false")
        } catch (_: Exception) {}

        // 1. Initialize Local Room Database
        database = AppDatabase.getInstance(this)

        // 2. Initialize NewPipe Extractor with high-speed OkHttp Downloader
        try {
            val downloader = NewPipeDownloader.init()
            NewPipe.init(downloader)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize NewPipe extractor: ${e.message}", e)
        }

        // 3. Initialize Extractor Manager
        extractorManager = ExtractorManager.getInstance()

        // 4. Pre-initialize Xray-Core environment and assets asynchronously off main thread
        downloadScope.launch {
            try {
                com.clouddrive.leech.vpn.core.XrayCoreManager.init(this@App)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to pre-initialize Xray core: ${e.message}", e)
            }
        }

        // 5. Auto-resume interrupted background downloads asynchronously
        downloadScope.launch {
            try {
                com.clouddrive.leech.plugins.NativeDownloadPlugin.resumeInterruptedDownloads()
            } catch (e: Exception) {
                Log.w(TAG, "Failed to auto-resume background downloads: ${e.message}")
            }
        }
    }

    private fun clearMemoryCaches() {
        try {
            extractorManager.clearCache()
            UniversalAntiCensorDns.instance.clearCache()
            LocalMediaProxy.clearCache()
            CarrierBypassDns.clearDnsCache()
            BaseMovieScraper.defaultClient.connectionPool.evictAll()
        } catch (e: Exception) {
            Log.w(TAG, "Error clearing memory caches: ${e.message}")
        }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= TRIM_MEMORY_BACKGROUND || level >= TRIM_MEMORY_MODERATE) {
            clearMemoryCaches()
        }
        if (level >= TRIM_MEMORY_RUNNING_CRITICAL || level >= TRIM_MEMORY_COMPLETE) {
            System.gc()
        }
    }

    override fun onLowMemory() {
        super.onLowMemory()
        clearMemoryCaches()
        System.gc()
    }
}
