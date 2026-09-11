package com.clouddrive.leech.plugins

import android.content.Context
import android.os.Build
import android.provider.Settings
import com.getcapacitor.JSObject
import com.getcapacitor.Plugin
import com.getcapacitor.PluginCall
import com.getcapacitor.PluginMethod
import com.getcapacitor.annotation.CapacitorPlugin
import java.security.MessageDigest
import java.util.UUID

@CapacitorPlugin(name = "NativeLicense")
class NativeLicensePlugin : Plugin() {

    private val KEY_TOKEN = "cld_signed_license_token"
    private val KEY_INSTALL_ID = "cld_device_installation_id"

    private fun getPrefs(): android.content.SharedPreferences {
        return com.clouddrive.leech.security.SecureStorageManager.getEncryptedPrefs(context)
    }

    @PluginMethod
    fun getInstallationIdentity(call: PluginCall) {
        try {
            val prefs = getPrefs()
            var installId = prefs.getString(KEY_INSTALL_ID, null)

            if (installId.isNullOrEmpty()) {
                val androidId = try {
                    Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "UNKNOWN_ID"
                } catch (_: Exception) { "UNKNOWN_ID" }

                // Deterministic hardware signature combining immutable device parameters
                val seed = "CLD_HW:$androidId:${Build.BOARD}:${Build.HARDWARE}:${Build.BRAND}:${Build.MANUFACTURER}:${Build.MODEL}:${Build.FINGERPRINT}"
                val digest = MessageDigest.getInstance("SHA-256").digest(seed.toByteArray())
                val hex = digest.joinToString("") { "%02x".format(it) }
                installId = "CLD-DEV-${hex.substring(0, 16).uppercase()}"

                prefs.edit().putString(KEY_INSTALL_ID, installId).apply()
            }

            val ret = JSObject()
            ret.put("installationId", installId)
            ret.put("deviceModel", "${Build.MANUFACTURER} ${Build.MODEL}")
            ret.put("androidVersion", Build.VERSION.RELEASE)
            call.resolve(ret)
        } catch (e: Exception) {
            call.reject("Failed to obtain installation identity: ${e.message}")
        }
    }

    @PluginMethod
    fun getDeviceInfo(call: PluginCall) {
        try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            val ret = JSObject()
            ret.put("manufacturer", Build.MANUFACTURER)
            ret.put("model", Build.MODEL)
            ret.put("brand", Build.BRAND)
            ret.put("androidVersion", Build.VERSION.RELEASE)
            ret.put("sdkInt", Build.VERSION.SDK_INT)
            ret.put("appVersion", pInfo.versionName)
            call.resolve(ret)
        } catch (e: Exception) {
            call.reject("Failed to get device info: ${e.message}")
        }
    }

    @PluginMethod
    fun saveLicenseToken(call: PluginCall) {
        val token = call.getString("token") ?: ""
        if (token.isEmpty()) {
            call.reject("Token is required")
            return
        }
        val prefs = getPrefs()
        prefs.edit().putString(KEY_TOKEN, token).apply()

        val ret = JSObject()
        ret.put("success", true)
        call.resolve(ret)
    }

    @PluginMethod
    fun getLicenseToken(call: PluginCall) {
        val prefs = getPrefs()
        val token = prefs.getString(KEY_TOKEN, null)

        val ret = JSObject()
        ret.put("token", token)
        call.resolve(ret)
    }

    @PluginMethod
    fun clearLicenseToken(call: PluginCall) {
        val prefs = getPrefs()
        prefs.edit().remove(KEY_TOKEN).apply()

        val ret = JSObject()
        ret.put("success", true)
        call.resolve(ret)
    }
}
