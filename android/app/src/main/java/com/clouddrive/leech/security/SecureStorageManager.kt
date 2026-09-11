package com.clouddrive.leech.security

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.MessageDigest
import java.security.SecureRandom

object SecureStorageManager {
    private const val TAG = "SecureStorageManager"
    private const val LEGACY_PREFS_NAME = "cdl_gdrive_prefs"
    private const val ENCRYPTED_PREFS_NAME = "cdl_gdrive_secure_prefs"

    @Volatile
    private var encryptedPrefsInstance: SharedPreferences? = null

    fun getEncryptedPrefs(context: Context): SharedPreferences {
        return encryptedPrefsInstance ?: synchronized(this) {
            encryptedPrefsInstance ?: try {
                val masterKey = MasterKey.Builder(context.applicationContext)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build()

                val encPrefs = EncryptedSharedPreferences.create(
                    context.applicationContext,
                    ENCRYPTED_PREFS_NAME,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                )

                // Seamless migration: If legacy plaintext tokens exist, securely migrate and wipe plaintext file
                try {
                    val legacyPrefs = context.applicationContext.getSharedPreferences(LEGACY_PREFS_NAME, Context.MODE_PRIVATE)
                    val allLegacy = legacyPrefs.all
                    if (allLegacy.isNotEmpty() && !encPrefs.contains("auth_token")) {
                        val editor = encPrefs.edit()
                        for ((k, v) in allLegacy) {
                            when (v) {
                                is String -> editor.putString(k, v)
                                is Boolean -> editor.putBoolean(k, v)
                                is Long -> editor.putLong(k, v)
                                is Int -> editor.putInt(k, v)
                                is Float -> editor.putFloat(k, v)
                            }
                        }
                        editor.apply()
                        legacyPrefs.edit().clear().apply()
                        Log.i(TAG, "Successfully migrated legacy Google Drive credentials to EncryptedSharedPreferences")
                    }
                } catch (migErr: Exception) {
                    Log.w(TAG, "Legacy preference migration notice: ${migErr.message}")
                }

                encPrefs.also { encryptedPrefsInstance = it }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize EncryptedSharedPreferences, falling back to private prefs: ${e.message}")
                context.applicationContext.getSharedPreferences(LEGACY_PREFS_NAME, Context.MODE_PRIVATE)
            }
        }
    }

    // ─── PKCE (Proof Key for Code Exchange - RFC 7636) ───
    fun generateCodeVerifier(): String {
        val secureRandom = SecureRandom()
        val bytes = ByteArray(32)
        secureRandom.nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    fun generateCodeChallenge(codeVerifier: String): String {
        val bytes = codeVerifier.toByteArray(Charsets.US_ASCII)
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(bytes)
        return Base64.encodeToString(digest, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }
}
