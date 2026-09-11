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

@CapacitorPlugin(name = "NativeLicense")
class NativeLicensePlugin : Plugin() {

    companion object {
        private const val KEY_TOKEN = "cld_signed_license_token"
        private const val KEY_INSTALL_ID = "cld_device_installation_id"
        private const val KEY_VERIFIED_PLAN = "cld_verified_plan"
        private const val KEY_VERIFIED_STATUS = "cld_verified_status"
        private const val KEY_VERIFIED_EXPIRY = "cld_verified_expiry"
        private const val KEY_VERIFIED_PAYLOAD = "cld_verified_license_payload"
        private const val KEY_INTEGRITY_SIG = "cld_license_integrity_sig"
        private const val SECRET_SALT = "CLD_SECURE_SALT_2026_LEEECH_V1"
    }

    private fun getPrefs(): android.content.SharedPreferences {
        return com.clouddrive.leech.security.SecureStorageManager.getEncryptedPrefs(context)
    }

    private fun computeSignature(installId: String, token: String, plan: String, status: String): String {
        val raw = "$installId:$token:$plan:$status:$SECRET_SALT"
        val digest = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun getOrCreateInstallId(): String {
        val prefs = getPrefs()
        var installId = prefs.getString(KEY_INSTALL_ID, null)
        if (installId.isNullOrEmpty()) {
            val androidId = try {
                Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "UNKNOWN_ID"
            } catch (_: Exception) { "UNKNOWN_ID" }

            val seed = "CLD_HW:$androidId:${Build.BOARD}:${Build.HARDWARE}:${Build.BRAND}:${Build.MANUFACTURER}:${Build.MODEL}:${Build.FINGERPRINT}"
            val digest = MessageDigest.getInstance("SHA-256").digest(seed.toByteArray(Charsets.UTF_8))
            val hex = digest.joinToString("") { "%02x".format(it) }
            installId = "CLD-DEV-${hex.substring(0, 16).uppercase()}"

            prefs.edit().putString(KEY_INSTALL_ID, installId).apply()
        }
        return installId
    }

    @PluginMethod
    fun getInstallationIdentity(call: PluginCall) {
        try {
            val installId = getOrCreateInstallId()
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
        prefs.edit()
            .remove(KEY_TOKEN)
            .remove(KEY_VERIFIED_PLAN)
            .remove(KEY_VERIFIED_STATUS)
            .remove(KEY_VERIFIED_EXPIRY)
            .remove(KEY_VERIFIED_PAYLOAD)
            .remove(KEY_INTEGRITY_SIG)
            .apply()

        val ret = JSObject()
        ret.put("success", true)
        call.resolve(ret)
    }

    /**
     * Authoritative License Storage: Saves verified license payload with hardware-bound HMAC-SHA256 signature
     * into EncryptedSharedPreferences (AES-256-GCM).
     */
    @PluginMethod
    fun saveVerifiedLicense(call: PluginCall) {
        try {
            val token = call.getString("token") ?: ""
            val plan = call.getString("plan") ?: "FREE"
            val status = call.getString("status") ?: "ACTIVE"
            val expiresAt = call.getString("expiresAt") ?: ""
            val payload = call.getString("payload") ?: ""

            val prefs = getPrefs()
            val installId = getOrCreateInstallId()

            val isPro = plan.equals("PRO", ignoreCase = true) || plan.equals("ENTERPRISE", ignoreCase = true) || plan.equals("PREMIUM", ignoreCase = true)
            if (isPro) {
                if (token.isEmpty()) {
                    call.reject("Token is required for PRO plan")
                    return
                }
                val sig = computeSignature(installId, token, plan, status)
                prefs.edit()
                    .putString(KEY_TOKEN, token)
                    .putString(KEY_VERIFIED_PLAN, plan)
                    .putString(KEY_VERIFIED_STATUS, status)
                    .putString(KEY_VERIFIED_EXPIRY, expiresAt)
                    .putString(KEY_VERIFIED_PAYLOAD, payload)
                    .putString(KEY_INTEGRITY_SIG, sig)
                    .apply()
            } else {
                // Free tier reset
                prefs.edit()
                    .remove(KEY_TOKEN)
                    .putString(KEY_VERIFIED_PLAN, "FREE")
                    .putString(KEY_VERIFIED_STATUS, "ACTIVE")
                    .remove(KEY_VERIFIED_EXPIRY)
                    .remove(KEY_VERIFIED_PAYLOAD)
                    .remove(KEY_INTEGRITY_SIG)
                    .apply()
            }

            val ret = JSObject()
            ret.put("success", true)
            call.resolve(ret)
        } catch (e: Exception) {
            call.reject("Failed to save verified license: ${e.message}")
        }
    }

    /**
     * Authoritative License Verification: Checks hardware binding, token, and cryptographic signature
     * directly inside Kotlin EncryptedSharedPreferences.
     */
    @PluginMethod
    fun getVerifiedLicense(call: PluginCall) {
        try {
            val prefs = getPrefs()
            val installId = getOrCreateInstallId()
            val token = prefs.getString(KEY_TOKEN, null)
            val plan = prefs.getString(KEY_VERIFIED_PLAN, "FREE") ?: "FREE"
            val status = prefs.getString(KEY_VERIFIED_STATUS, "ACTIVE") ?: "ACTIVE"
            val expiresAt = prefs.getString(KEY_VERIFIED_EXPIRY, null)
            val payload = prefs.getString(KEY_VERIFIED_PAYLOAD, null)
            val storedSig = prefs.getString(KEY_INTEGRITY_SIG, null)

            val ret = JSObject()
            ret.put("installationId", installId)

            val isPro = plan.equals("PRO", ignoreCase = true) || plan.equals("ENTERPRISE", ignoreCase = true) || plan.equals("PREMIUM", ignoreCase = true)
            if (!isPro || token.isNullOrEmpty() || storedSig.isNullOrEmpty()) {
                ret.put("valid", false)
                ret.put("plan", "FREE")
                ret.put("status", "ACTIVE")
                ret.put("token", null)
                ret.put("payload", null)
                call.resolve(ret)
                return
            }

            val expectedSig = computeSignature(installId, token, plan, status)
            if (storedSig != expectedSig) {
                // Cryptographic integrity failure: Wipe tampered state
                prefs.edit()
                    .remove(KEY_TOKEN)
                    .remove(KEY_INTEGRITY_SIG)
                    .putString(KEY_VERIFIED_PLAN, "FREE")
                    .apply()

                ret.put("valid", false)
                ret.put("plan", "FREE")
                ret.put("status", "REVOKED")
                ret.put("tampered", true)
                call.resolve(ret)
                return
            }

            ret.put("valid", true)
            ret.put("plan", plan)
            ret.put("status", status)
            ret.put("token", token)
            ret.put("expiresAt", expiresAt)
            ret.put("payload", payload)
            call.resolve(ret)
        } catch (e: Exception) {
            call.reject("Failed to get verified license: ${e.message}")
        }
    }
}
