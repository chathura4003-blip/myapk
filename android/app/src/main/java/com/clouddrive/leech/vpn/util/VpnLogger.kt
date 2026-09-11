package com.clouddrive.leech.vpn.util

import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedDeque

data class VpnLogEntry(
    val timestamp: Long,
    val timeFormatted: String,
    val level: String,
    val message: String
)

object VpnLogger {
    private const val TAG = "CloudDriveVpn"
    private const val MAX_LOGS = 500
    private val logBuffer = ConcurrentLinkedDeque<VpnLogEntry>()
    private val dateFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
    private var currentSessionId: String = "0000"

    fun newSession(): String {
        currentSessionId = UUID.randomUUID().toString().take(4).uppercase()
        i("NEW SESSION INITIALIZED [$currentSessionId]")
        return currentSessionId
    }

    fun d(msg: String) {
        val sanitized = sanitize(msg)
        addEntry("DEBUG", sanitized)
        try {
            Log.d(TAG, "VPN[$currentSessionId] $sanitized")
        } catch (_: Throwable) {
            println("DEBUG: VPN[$currentSessionId] $sanitized")
        }
    }

    fun i(msg: String) {
        val sanitized = sanitize(msg)
        addEntry("INFO", sanitized)
        try {
            Log.i(TAG, "VPN[$currentSessionId] $sanitized")
        } catch (_: Throwable) {
            println("INFO: VPN[$currentSessionId] $sanitized")
        }
    }

    fun w(msg: String) {
        val sanitized = sanitize(msg)
        addEntry("WARN", sanitized)
        try {
            Log.w(TAG, "VPN[$currentSessionId] $sanitized")
        } catch (_: Throwable) {
            println("WARN: VPN[$currentSessionId] $sanitized")
        }
    }

    fun e(msg: String, throwable: Throwable? = null) {
        val sanitized = sanitize(msg)
        val fullMsg = if (throwable != null) "$sanitized (${throwable.message})" else sanitized
        addEntry("ERROR", fullMsg)
        try {
            if (throwable != null) {
                Log.e(TAG, "VPN[$currentSessionId] $sanitized", throwable)
            } else {
                Log.e(TAG, "VPN[$currentSessionId] $sanitized")
            }
        } catch (_: Throwable) {
            System.err.println("ERROR: VPN[$currentSessionId] $fullMsg")
        }
    }

    private fun addEntry(level: String, message: String) {
        val now = System.currentTimeMillis()
        val timeStr = synchronized(dateFormat) { dateFormat.format(Date(now)) }
        logBuffer.addLast(VpnLogEntry(now, timeStr, level, message))
        while (logBuffer.size > MAX_LOGS) {
            logBuffer.pollFirst()
        }
    }

    fun getLogs(limit: Int = 200, filterLevel: String? = null): List<VpnLogEntry> {
        val list = logBuffer.toList()
        val filtered = if (!filterLevel.isNullOrEmpty() && filterLevel != "ALL") {
            list.filter { it.level.equals(filterLevel, ignoreCase = true) }
        } else {
            list
        }
        return filtered.takeLast(limit)
    }

    fun clearLogs() {
        logBuffer.clear()
        i("Log buffer cleared by user")
    }

    fun exportSafeLogs(): String {
        val sb = StringBuilder()
        sb.append("=== CLOUDDRIVE LEECH VPN SAFE AUDIT LOGS ===\n")
        sb.append("Exported: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())}\n\n")
        for (entry in logBuffer) {
            sb.append("[${entry.timeFormatted}] [${entry.level}] ${entry.message}\n")
        }
        return sb.toString()
    }

    fun sanitize(raw: String): String {
        var clean = raw.replace(Regex("""[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"""), "UUID-REDACTED")
        clean = clean.replace(Regex("""pbk=[^&\s]+"""), "pbk=REDACTED")
        clean = clean.replace(Regex("""sid=[^&\s]+"""), "sid=REDACTED")
        clean = clean.replace(Regex("""password=[^&\s]+"""), "password=REDACTED")
        clean = clean.replace(Regex("""trojan://[^@\s]+@"""), "trojan://REDACTED@")
        clean = clean.replace(Regex("""ss://[^@\s]+@"""), "ss://REDACTED@")
        return clean
    }
}
