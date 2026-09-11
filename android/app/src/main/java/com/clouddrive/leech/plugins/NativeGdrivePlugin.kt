package com.clouddrive.leech.plugins

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.graphics.Color
import android.provider.OpenableColumns
import android.util.Log
import androidx.activity.result.ActivityResult
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import com.clouddrive.leech.proxy.LocalMediaProxy
import com.getcapacitor.JSArray
import com.getcapacitor.JSObject
import com.getcapacitor.Plugin
import com.getcapacitor.PluginCall
import com.getcapacitor.PluginMethod
import com.getcapacitor.annotation.ActivityCallback
import com.getcapacitor.annotation.CapacitorPlugin
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSink
import org.json.JSONObject
import com.clouddrive.leech.extractor.providers.movies.MovieStreamResolver
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

@CapacitorPlugin(name = "NativeGdrive")
class NativeGdrivePlugin : Plugin() {

    companion object {
        const val TAG = "NativeGdrivePlugin"
        const val PREFS_NAME = "cdl_gdrive_prefs"
        const val KEY_ACTIVE_EMAIL = "active_email"
        const val KEY_ACTIVE_NAME = "active_name"
        const val KEY_ACTIVE_QUOTA = "active_quota"
        const val KEY_CONNECTED = "is_connected"
        const val KEY_AUTH_TOKEN = "auth_token"
        const val KEY_REFRESH_TOKEN = "refresh_token"
        const val KEY_APP_FOLDER_ID = "app_folder_id"
        const val KEY_TOKEN_EXPIRES_AT = "token_expires_at"
        const val OAUTH_SCOPE = "oauth2:https://www.googleapis.com/auth/drive https://www.googleapis.com/auth/drive.file https://www.googleapis.com/auth/userinfo.profile https://www.googleapis.com/auth/userinfo.email"
        const val CLIENT_ID = "202264815644.apps.googleusercontent.com"
        @Volatile var isTransferCancelled = false

        val sharedClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .protocols(listOf(okhttp3.Protocol.HTTP_1_1))
                .dns(com.clouddrive.leech.vpn.net.UniversalAntiCensorDns.instance)
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .build()
        }

        fun parseGoogleErrorMessage(rawJson: String, defaultMsg: String): String {
            return try {
                if (rawJson.isEmpty()) return defaultMsg
                val json = JSONObject(rawJson)
                val errObj = json.optJSONObject("error") ?: return defaultMsg
                val msg = errObj.optString("message", "")
                val errorsArr = errObj.optJSONArray("errors")
                var isPermissionIssue = msg.contains("insufficient", ignoreCase = true) ||
                        msg.contains("permission", ignoreCase = true) ||
                        msg.contains("access", ignoreCase = true)

                if (!isPermissionIssue && errorsArr != null) {
                    for (i in 0 until errorsArr.length()) {
                        val e = errorsArr.optJSONObject(i)
                        val r = e?.optString("reason", "") ?: ""
                        if (r.contains("permission", ignoreCase = true) || r.contains("access", ignoreCase = true)) {
                            isPermissionIssue = true
                            break
                        }
                    }
                }

                if (msg.isNotEmpty()) {
                    if (isPermissionIssue) {
                        return "$msg (Please ensure you checked the Google Drive permissions box when signing in)"
                    }
                    return msg
                }
                defaultMsg
            } catch (_: Exception) {
                defaultMsg
            }
        }

        fun getValidToken(context: Context): String? {
            val prefs = com.clouddrive.leech.security.SecureStorageManager.getEncryptedPrefs(context)
            var token = prefs.getString(KEY_AUTH_TOKEN, null)
            val refreshToken = prefs.getString(KEY_REFRESH_TOKEN, null)
            val expiresAt = prefs.getLong(KEY_TOKEN_EXPIRES_AT, 0L)
            val isExpired = (expiresAt > 0L && System.currentTimeMillis() >= (expiresAt - 120_000L))

            if ((token.isNullOrEmpty() || isExpired) && !refreshToken.isNullOrEmpty()) {
                val newToken = refreshAccessToken(context, refreshToken)
                if (!newToken.isNullOrEmpty()) {
                    return newToken
                }
            }
            return token
        }

        fun refreshAccessToken(context: Context, refreshToken: String): String? {
            return try {
                val client = sharedClient
                // RFC 7636 PKCE Token Refresh - Zero Client Secret Architecture
                val formBody = FormBody.Builder()
                    .add("client_id", CLIENT_ID)
                    .add("grant_type", "refresh_token")
                    .add("refresh_token", refreshToken)
                    .build()

                val req = Request.Builder()
                    .url("https://oauth2.googleapis.com/token")
                    .post(formBody)
                    .build()

                val resp = client.newCall(req).execute()
                if (resp.isSuccessful) {
                    val json = JSONObject(resp.body?.string() ?: "{}")
                    val newAccToken = json.optString("access_token", "")
                    val expiresIn = json.optLong("expires_in", 3600L)
                    val expiresAt = System.currentTimeMillis() + (expiresIn * 1000L)
                    if (newAccToken.isNotEmpty()) {
                        com.clouddrive.leech.security.SecureStorageManager.getEncryptedPrefs(context)
                            .edit()
                            .putString(KEY_AUTH_TOKEN, newAccToken)
                            .putLong(KEY_TOKEN_EXPIRES_AT, expiresAt)
                            .apply()
                        return newAccToken
                    }
                }
                null
            } catch (e: Exception) {
                Log.w(TAG, "refreshAccessToken failed: ${e.message}")
                null
            }
        }

        fun ensureAppFolderId(context: Context, token: String?): String? {
            if (token.isNullOrEmpty()) return null
            val prefs = com.clouddrive.leech.security.SecureStorageManager.getEncryptedPrefs(context)
            var folderId = prefs.getString(KEY_APP_FOLDER_ID, null)

            // Validate cached folder if present
            if (!folderId.isNullOrEmpty()) {
                try {
                    val chkReq = Request.Builder()
                        .url("https://www.googleapis.com/drive/v3/files/$folderId?fields=id,trashed&supportsAllDrives=true")
                        .addHeader("Authorization", "Bearer $token")
                        .build()
                    val chkResp = sharedClient.newCall(chkReq).execute()
                    if (chkResp.isSuccessful) {
                        val chkJson = JSONObject(chkResp.body?.string() ?: "{}")
                        if (!chkJson.optBoolean("trashed", false)) {
                            return folderId
                        }
                    }
                    // Folder invalid or trashed - clear cached ID
                    prefs.edit().remove(KEY_APP_FOLDER_ID).apply()
                    folderId = null
                } catch (_: Exception) {}
            }

            return try {
                val client = sharedClient
                val q = URLEncoder.encode("name='Cloud Drive Leech' and mimeType='application/vnd.google-apps.folder' and trashed=false", "UTF-8")
                val findReq = Request.Builder()
                    .url("https://www.googleapis.com/drive/v3/files?q=$q&fields=files(id,name)&supportsAllDrives=true&includeItemsFromAllDrives=true")
                    .addHeader("Authorization", "Bearer $token")
                    .build()
                val fResp = client.newCall(findReq).execute()
                if (fResp.isSuccessful) {
                    val fJson = JSONObject(fResp.body?.string() ?: "{}")
                    val fArr = fJson.optJSONArray("files")
                    if (fArr != null && fArr.length() > 0) {
                        folderId = fArr.getJSONObject(0).optString("id")
                        prefs.edit().putString(KEY_APP_FOLDER_ID, folderId).apply()
                        return folderId
                    }
                }

                // Create folder if missing
                val folderPayload = JSONObject().apply {
                    put("name", "Cloud Drive Leech")
                    put("mimeType", "application/vnd.google-apps.folder")
                }
                val reqBody = folderPayload.toString().toRequestBody("application/json".toMediaTypeOrNull())
                val createReq = Request.Builder()
                    .url("https://www.googleapis.com/drive/v3/files?supportsAllDrives=true")
                    .addHeader("Authorization", "Bearer $token")
                    .post(reqBody)
                    .build()
                val cResp = client.newCall(createReq).execute()
                if (cResp.isSuccessful) {
                    val cJson = JSONObject(cResp.body?.string() ?: "{}")
                    folderId = cJson.optString("id")
                    if (!folderId.isNullOrEmpty()) {
                        prefs.edit().putString(KEY_APP_FOLDER_ID, folderId).apply()
                        return folderId
                    }
                }
                null
            } catch (e: Exception) {
                Log.w(TAG, "ensureAppFolderId error: ${e.message}")
                null
            }
        }

        fun performCloudTransfer(
            context: Context,
            sourceUrl: String,
            rawTitle: String,
            type: String = "movie",
            onStart: () -> Unit = {},
            onProgress: (Int, Long, Long) -> Unit = { _, _, _ -> },
            onSuccess: (String, String) -> Unit = { _, _ -> },
            onError: (String) -> Unit = {}
        ) {
            Thread {
                try {
                    var token = getValidToken(context)
                    if (token.isNullOrEmpty()) {
                        onError("Not authenticated with Google Drive")
                        return@Thread
                    }

                    var title = rawTitle.trim().ifEmpty { "Cloud_Media_${System.currentTimeMillis()}" }
                    if (!title.contains(".")) {
                        title += if (type == "zip") ".zip" else ".mp4"
                    }

                    val client = OkHttpClient.Builder()
                        .connectTimeout(30, TimeUnit.SECONDS)
                        .readTimeout(120, TimeUnit.SECONDS)
                        .followRedirects(true)
                        .followSslRedirects(true)
                        .build()

                    var finalUrl = sourceUrl.trim()

                    // 0. Cinejoy & Netflix Direct Stream Resolver
                    if (finalUrl.contains("cinejoy") || finalUrl.contains("shegu.st") || finalUrl.contains("netflix")) {
                        try {
                            val cjRes = kotlinx.coroutines.runBlocking {
                                com.clouddrive.leech.extractor.resolvers.netflix.NetflixResolver.resolve(context as? Activity ?: Activity(), finalUrl)
                            }
                            if (cjRes != null && cjRes.streamUrl.isNotEmpty() && !cjRes.streamUrl.contains("/watch/")) {
                                finalUrl = cjRes.streamUrl
                            }
                        } catch (_: Exception) {}
                    }

                    // 1. Resolve redirectors (/links/, dl.sub.lk, usersdrive, etc.) via MovieStreamResolver
                    if (finalUrl.contains("/links/") || finalUrl.contains("dl.sub.lk") || finalUrl.contains("cinesubz") ||
                        finalUrl.contains("zt-links") || finalUrl.contains("baiscope") || finalUrl.contains("usersdrive") || finalUrl.contains("userdrive")) {
                        try {
                            val resolved = MovieStreamResolver(client).resolve(finalUrl)
                            val resolvedUrl = resolved.downloadUrl.ifEmpty { resolved.streamUrl }
                            if (resolvedUrl.isNotEmpty() && !resolvedUrl.contains("/links/")) {
                                finalUrl = resolvedUrl
                            }
                            if (resolved.isZip || finalUrl.contains(".zip", true)) {
                                if (!title.endsWith(".zip", true)) {
                                    title = if (title.contains(".")) "${title.substringBeforeLast(".")}.zip" else "$title.zip"
                                }
                            }
                        } catch (_: Exception) {}
                    }

                    // 2. Format PixelDrain direct API endpoint
                    val pdMatch = Regex("""pixeldrain\.com\/(?:u|l|api\/file|e)\/([a-zA-Z0-9_-]+)""").find(finalUrl)
                    if (pdMatch != null) {
                        finalUrl = "https://pixeldrain.com/api/file/${pdMatch.groupValues[1]}"
                    }

                    // 3. Format Google Drive direct download endpoint
                    val gdMatch = Regex("""(?:drive\.google\.com|drive\.usercontent\.google\.com)\/(?:file\/d\/|open\?id=|uc\?id=|download\?id=)([a-zA-Z0-9_-]+)""").find(finalUrl)
                    if (gdMatch != null) {
                        finalUrl = "https://drive.usercontent.google.com/download?id=${gdMatch.groupValues[1]}&export=download&authuser=0"
                    }

                    finalUrl = com.clouddrive.leech.extractor.resolvers.netflix.NetflixResolver.sanitizeAndEncodeMediaUrl(finalUrl)

                    val sourceReqBuilder = Request.Builder()
                        .url(finalUrl)
                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36")

                    if (finalUrl.contains("pixeldrain")) {
                        sourceReqBuilder.header("Referer", "https://pixeldrain.com/")
                    } else if (finalUrl.contains("sinhalasub")) {
                        sourceReqBuilder.header("Referer", "https://sinhalasub.lk/")
                    } else if (finalUrl.contains("cineru")) {
                        sourceReqBuilder.header("Referer", "https://cineru.lk/")
                    } else if (finalUrl.contains("usersdrive") || finalUrl.contains("userdrive")) {
                        sourceReqBuilder.header("Referer", "https://usersdrive.com/")
                        sourceReqBuilder.header("Origin", "https://usersdrive.com")
                    } else if (finalUrl.contains("cinejoy") || finalUrl.contains("shegu.st") || finalUrl.contains("workers.dev") || finalUrl.contains("cloudflarestorage") || finalUrl.contains("4khdhub")) {
                        sourceReqBuilder.header("Referer", "https://cinejoy.to/")
                        sourceReqBuilder.header("Origin", "https://cinejoy.to")
                    }

                    val sourceResp = client.newCall(sourceReqBuilder.build()).execute()
                    if (!sourceResp.isSuccessful) {
                        onError("Failed to fetch source stream: HTTP ${sourceResp.code}")
                        return@Thread
                    }

                    val rawContentType = sourceResp.body?.contentType()?.toString() ?: ""
                    val contentLength = sourceResp.body?.contentLength() ?: -1L

                    // Guard against piping HTML error/captcha landing pages to Drive as video
                    if (rawContentType.contains("text/html", ignoreCase = true) || (contentLength in 1..80000 && type != "zip")) {
                        sourceResp.close()
                        onError("Source link returned an HTML webpage instead of a binary media stream. Please choose a direct mirror like PixelDrain or DLServer.")
                        return@Thread
                    }

                    val contentType = if (rawContentType.isNotEmpty() && !rawContentType.contains("text/html")) rawContentType else (if (type == "zip") "application/zip" else "video/mp4")
                    val sourceStream = sourceResp.body?.byteStream()
                    if (sourceStream == null) {
                        onError("Source response body stream is null")
                        return@Thread
                    }

                    isTransferCancelled = false
                    onStart()

                    var folderId = ensureAppFolderId(context, token)
                    val metaJson = JSONObject().apply {
                        put("name", title)
                        if (!folderId.isNullOrEmpty()) {
                            put("parents", org.json.JSONArray().put(folderId))
                        }
                    }

                    var initReq = Request.Builder()
                        .url("https://www.googleapis.com/upload/drive/v3/files?uploadType=resumable&supportsAllDrives=true")
                        .addHeader("Authorization", "Bearer $token")
                        .addHeader("X-Upload-Content-Type", contentType)
                        .apply {
                            if (contentLength > 0) addHeader("X-Upload-Content-Length", contentLength.toString())
                        }
                        .post(metaJson.toString().toRequestBody("application/json; charset=UTF-8".toMediaTypeOrNull()))
                        .build()

                    var initResp = sharedClient.newCall(initReq).execute()

                    // Auto-refresh token if 401
                    if (initResp.code == 401) {
                        val prefs = com.clouddrive.leech.security.SecureStorageManager.getEncryptedPrefs(context)
                        val refreshToken = prefs.getString(KEY_REFRESH_TOKEN, null)
                        if (!refreshToken.isNullOrEmpty()) {
                            val newToken = refreshAccessToken(context, refreshToken)
                            if (!newToken.isNullOrEmpty()) {
                                token = newToken
                                initReq = Request.Builder()
                                    .url("https://www.googleapis.com/upload/drive/v3/files?uploadType=resumable&supportsAllDrives=true")
                                    .addHeader("Authorization", "Bearer $token")
                                    .addHeader("X-Upload-Content-Type", contentType)
                                    .apply {
                                        if (contentLength > 0) addHeader("X-Upload-Content-Length", contentLength.toString())
                                    }
                                    .post(metaJson.toString().toRequestBody("application/json; charset=UTF-8".toMediaTypeOrNull()))
                                    .build()
                                initResp = sharedClient.newCall(initReq).execute()
                            }
                        }
                    }

                    // Retry without parent if 403 or 404 (due to stale/inaccessible parent folder)
                    if ((initResp.code == 403 || initResp.code == 404) && !folderId.isNullOrEmpty()) {
                        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().remove(KEY_APP_FOLDER_ID).apply()
                        folderId = null
                        val fallbackMeta = JSONObject().apply { put("name", title) }
                        initReq = Request.Builder()
                            .url("https://www.googleapis.com/upload/drive/v3/files?uploadType=resumable&supportsAllDrives=true")
                            .addHeader("Authorization", "Bearer $token")
                            .addHeader("X-Upload-Content-Type", contentType)
                            .apply {
                                if (contentLength > 0) addHeader("X-Upload-Content-Length", contentLength.toString())
                            }
                            .post(fallbackMeta.toString().toRequestBody("application/json; charset=UTF-8".toMediaTypeOrNull()))
                            .build()
                        initResp = sharedClient.newCall(initReq).execute()
                    }

                    val uploadUrl = initResp.header("Location")
                    if (uploadUrl.isNullOrEmpty()) {
                        val errBody = initResp.body?.string() ?: ""
                        onError(parseGoogleErrorMessage(errBody, "Failed to initiate Drive resumable transfer: HTTP ${initResp.code}"))
                        return@Thread
                    }

                    val pipeBody = object : RequestBody() {
                        override fun contentType() = contentType.toMediaTypeOrNull()
                        override fun contentLength() = contentLength
                        override fun writeTo(sink: BufferedSink) {
                            val buffer = ByteArray(64 * 1024)
                            var bytesRead: Int
                            var transferred = 0L
                            var lastEmit = 0L

                            sourceStream.use { input ->
                                while (input.read(buffer).also { bytesRead = it } != -1) {
                                    if (isTransferCancelled) {
                                        throw java.io.IOException("Transfer cancelled by user")
                                    }
                                    sink.write(buffer, 0, bytesRead)
                                    transferred += bytesRead
                                    val now = System.currentTimeMillis()
                                    if (now - lastEmit > 500) {
                                        lastEmit = now
                                        val pct = if (contentLength > 0) ((transferred.toDouble() / contentLength) * 100).toInt() else 0
                                        onProgress(pct, transferred, contentLength)
                                    }
                                }
                            }
                        }
                    }

                    val uploadReq = Request.Builder()
                        .url(uploadUrl)
                        .put(pipeBody)
                        .build()

                    val uploadResp = client.newCall(uploadReq).execute()
                    if (uploadResp.isSuccessful || uploadResp.code == 201) {
                        val respJson = JSONObject(uploadResp.body?.string() ?: "{}")
                        val fileId = respJson.optString("id")
                        val fileName = respJson.optString("name", title)
                        onSuccess(fileId, fileName)
                    } else {
                        val errBody = uploadResp.body?.string() ?: ""
                        onError(parseGoogleErrorMessage(errBody, "Drive upload failed with HTTP ${uploadResp.code}"))
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "performCloudTransfer error: ${e.message}", e)
                    onError("Cloud Transfer error: ${e.message}")
                }
            }.start()
        }
    }

    private val httpClient: OkHttpClient get() = sharedClient

    private val prefs: SharedPreferences by lazy {
        com.clouddrive.leech.security.SecureStorageManager.getEncryptedPrefs(context)
    }

    // ─── Token Management with Safe Auto-Refresh ───
    private fun getValidToken(): String? {
        return getValidToken(context)
    }

    private fun refreshAccessToken(refreshToken: String): String? {
        return refreshAccessToken(context, refreshToken)
    }

    // ─── 1. Account Profile & Connection State ───
    @PluginMethod
    fun getActiveAccount(call: PluginCall) {
        Thread {
            try {
                val isLoggedOut = prefs.getBoolean("user_logged_out", false)
                val isConnected = prefs.getBoolean(KEY_CONNECTED, false)
                val refreshToken = prefs.getString(KEY_REFRESH_TOKEN, null)

                if (isLoggedOut || !isConnected || refreshToken.isNullOrEmpty()) {
                    val lastAuthStatus = prefs.getString("last_oauth_status", null)
                    if (!lastAuthStatus.isNullOrEmpty()) {
                        prefs.edit().remove("last_oauth_status").apply()
                    }
                    val ret = JSObject()
                    ret.put("connected", false)
                    if (lastAuthStatus != null) {
                        ret.put("lastAuthStatus", lastAuthStatus)
                    }
                    call.resolve(ret)
                    return@Thread
                }

                var email = prefs.getString(KEY_ACTIVE_EMAIL, null)
                var token = getValidToken()
                var quotaUsage = prefs.getLong("quota_usage", 0L)
                var quotaLimit = prefs.getLong("quota_limit", 16106127360L)
                var photo = prefs.getString("active_photo", null)
                var displayName = prefs.getString(KEY_ACTIVE_NAME, null)
                val folderId = prefs.getString(KEY_APP_FOLDER_ID, null)

                if (!token.isNullOrEmpty()) {
                    try {
                        val aboutReq = Request.Builder()
                            .url("https://www.googleapis.com/drive/v3/about?fields=user,storageQuota")
                            .addHeader("Authorization", "Bearer $token")
                            .build()
                        val aboutResp = httpClient.newCall(aboutReq).execute()
                        if (aboutResp.isSuccessful) {
                            val aJson = JSONObject(aboutResp.body?.string() ?: "{}")
                            val u = aJson.optJSONObject("user")
                            if (u != null) {
                                displayName = u.optString("displayName", displayName ?: "")
                                email = u.optString("emailAddress", email ?: "")
                                photo = u.optString("photoLink", photo ?: "")
                            }
                            val s = aJson.optJSONObject("storageQuota")
                            if (s != null) {
                                quotaUsage = s.optLong("usage", quotaUsage)
                                quotaLimit = s.optLong("limit", quotaLimit)
                                prefs.edit()
                                    .putLong("quota_usage", quotaUsage)
                                    .putLong("quota_limit", quotaLimit)
                                    .putString(KEY_ACTIVE_NAME, displayName)
                                    .putString(KEY_ACTIVE_EMAIL, email)
                                    .putString("active_photo", photo)
                                    .apply()
                            }
                        }
                    } catch (_: Exception) {}
                }

                val lastAuthStatus = prefs.getString("last_oauth_status", null)
                if (!lastAuthStatus.isNullOrEmpty()) {
                    prefs.edit().remove("last_oauth_status").apply()
                }

                val ret = JSObject()
                ret.put("connected", true)
                ret.put("email", email ?: "Google Account")
                ret.put("displayName", displayName ?: (email ?: "Google Account"))
                ret.put("photo", photo)
                val validToken = getValidToken()
                val curRefreshToken = refreshToken ?: (prefs.getString(KEY_REFRESH_TOKEN, "") ?: "")
                ret.put("quotaUsage", quotaUsage)
                ret.put("quotaLimit", quotaLimit)
                ret.put("appFolderId", folderId)
                ret.put("driveScopeGranted", prefs.getBoolean("drive_scope_granted", true))
                ret.put("accessToken", validToken ?: "")
                ret.put("refreshToken", curRefreshToken)
                ret.put("clientId", CLIENT_ID)
                ret.put("clientSecret", "")
                if (lastAuthStatus != null) {
                    ret.put("lastAuthStatus", lastAuthStatus)
                }

                call.resolve(ret)
            } catch (e: Exception) {
                Log.e(TAG, "getActiveAccount error: ${e.message}", e)
                val ret = JSObject()
                ret.put("connected", false)
                ret.put("error", e.message)
                call.resolve(ret)
            }
        }.start()
    }

    // ─── 1.1 Dedicated Drive Credentials Exporter (For Remote Cloud Runner) ───
    @PluginMethod
    fun getDriveCredentials(call: PluginCall) {
        val validToken = getValidToken()
        val refreshToken = prefs.getString(KEY_REFRESH_TOKEN, "") ?: ""
        val folderId = prefs.getString(KEY_APP_FOLDER_ID, "") ?: ""
        val email = prefs.getString(KEY_ACTIVE_EMAIL, "") ?: ""
        val ret = JSObject().apply {
            put("connected", !validToken.isNullOrEmpty() || refreshToken.isNotEmpty())
            put("accessToken", validToken ?: "")
            put("refreshToken", refreshToken)
            put("clientId", CLIENT_ID)
            put("clientSecret", "")
            put("folderId", folderId)
            put("email", email)
        }
        call.resolve(ret)
    }

    // ─── 2. Google OAuth Launch (Supports 1-Click Linking & Account Switching) ───
    @PluginMethod
    fun launchGoogleOAuthBrowser(call: PluginCall) {
        try {
            val switchAccount = call.getBoolean("switchAccount", false) ?: false
            val promptParam = if (switchAccount) "select_account consent" else "select_account consent"

            val port = LocalMediaProxy.start()
            val redirectUri = "http://127.0.0.1:$port/oauth2callback"
            val scopes = listOf(
                "https://www.googleapis.com/auth/drive",
                "https://www.googleapis.com/auth/drive.file",
                "https://www.googleapis.com/auth/userinfo.profile",
                "https://www.googleapis.com/auth/userinfo.email"
            ).joinToString(" ")

            // RFC 7636 PKCE (Proof Key for Code Exchange - Zero Client Secret)
            val codeVerifier = com.clouddrive.leech.security.SecureStorageManager.generateCodeVerifier()
            val codeChallenge = com.clouddrive.leech.security.SecureStorageManager.generateCodeChallenge(codeVerifier)

            prefs.edit()
                .putString("pkce_code_verifier", codeVerifier)
                .putBoolean("user_logged_out", false)
                .putString("last_oauth_status", "pending")
                .apply()

            val authUrl = "https://accounts.google.com/o/oauth2/v2/auth?" +
                    "client_id=$CLIENT_ID&" +
                    "redirect_uri=" + URLEncoder.encode(redirectUri, "UTF-8") + "&" +
                    "response_type=code&" +
                    "scope=" + URLEncoder.encode(scopes, "UTF-8") + "&" +
                    "code_challenge=" + URLEncoder.encode(codeChallenge, "UTF-8") + "&" +
                    "code_challenge_method=S256&" +
                    "access_type=offline&" +
                    "include_granted_scopes=true&" +
                    "prompt=" + URLEncoder.encode(promptParam, "UTF-8")

            try {
                val displayMetrics = activity.resources.displayMetrics
                val initialHeight = (displayMetrics.heightPixels * 0.88).toInt()

                val colorSchemeParams = CustomTabColorSchemeParams.Builder()
                    .setToolbarColor(Color.parseColor("#0a0f1d"))
                    .setNavigationBarColor(Color.parseColor("#0a0f1d"))
                    .setSecondaryToolbarColor(Color.parseColor("#0a0f1d"))
                    .build()

                val customTabsIntent = CustomTabsIntent.Builder()
                    .setDefaultColorSchemeParams(colorSchemeParams)
                    .setInitialActivityHeightPx(initialHeight, CustomTabsIntent.ACTIVITY_HEIGHT_DEFAULT)
                    .setToolbarCornerRadiusDp(16)
                    .setShowTitle(true)
                    .setUrlBarHidingEnabled(true)
                    .build()
                customTabsIntent.launchUrl(activity, Uri.parse(authUrl))
            } catch (_: Exception) {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(authUrl)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                activity.startActivity(intent)
            }

            val ret = JSObject()
            ret.put("success", true)
            ret.put("authUrl", authUrl)
            call.resolve(ret)
        } catch (e: Exception) {
            Log.e(TAG, "launchGoogleOAuthBrowser error: ${e.message}", e)
            call.reject("Could not launch Google Sign-In browser: ${e.message}")
        }
    }

    // ─── 3. Disconnect / Unlink Account ───
    @PluginMethod
    fun disconnectAccount(call: PluginCall) {
        prefs.edit()
            .clear()
            .putBoolean("user_logged_out", true)
            .putBoolean(KEY_CONNECTED, false)
            .putString("last_oauth_status", "unlinked")
            .apply()

        val ret = JSObject()
        ret.put("success", true)
        ret.put("connected", false)
        call.resolve(ret)
    }

    @PluginMethod
    fun unlinkAccount(call: PluginCall) {
        disconnectAccount(call)
    }

    // ─── 4. Folder Discovery / Creation ("Cloud Drive Leech") ───
    private fun ensureAppFolderId(token: String?): String? {
        return Companion.ensureAppFolderId(context, token)
    }

    // ─── 5. Fetch Real Google Drive Data ───
    @PluginMethod
    fun fetchRealGoogleDriveData(call: PluginCall) {
        var token = getValidToken()
        val folderOnly = call.getBoolean("folderOnly", true) ?: true
        val searchQuery = call.getString("searchQuery")?.trim() ?: ""
        val category = call.getString("category")?.trim()?.lowercase() ?: "all"
        val pageToken = call.getString("pageToken")

        if (token.isNullOrEmpty()) {
            call.resolve(JSObject().put("success", false).put("error", "No auth token"))
            return
        }

        Thread {
            try {
                // 1. Storage Quota & Profile Info
                var aboutReq = Request.Builder()
                    .url("https://www.googleapis.com/drive/v3/about?fields=user,storageQuota")
                    .addHeader("Authorization", "Bearer $token")
                    .build()
                var aboutResp = httpClient.newCall(aboutReq).execute()

                if (aboutResp.code == 401) {
                    val refreshToken = prefs.getString(KEY_REFRESH_TOKEN, null)
                    if (!refreshToken.isNullOrEmpty()) {
                        val newToken = refreshAccessToken(refreshToken)
                        if (!newToken.isNullOrEmpty()) {
                            token = newToken
                            aboutReq = Request.Builder()
                                .url("https://www.googleapis.com/drive/v3/about?fields=user,storageQuota")
                                .addHeader("Authorization", "Bearer $token")
                                .build()
                            aboutResp = httpClient.newCall(aboutReq).execute()
                        }
                    }
                }

                var userObj: JSONObject? = null
                var storageObj: JSONObject? = null
                if (aboutResp.isSuccessful) {
                    val bodyStr = aboutResp.body?.string() ?: "{}"
                    val json = JSONObject(bodyStr)
                    userObj = json.optJSONObject("user")
                    storageObj = json.optJSONObject("storageQuota")
                }

                // 2. Discover / Create "Cloud Drive Leech" Folder
                val appFolderId = ensureAppFolderId(token)

                // 3. Build Query Filters
                val queryParts = mutableListOf("trashed=false")
                if (folderOnly && !appFolderId.isNullOrEmpty()) {
                    queryParts.add("'$appFolderId' in parents")
                } else {
                    queryParts.add("mimeType!='application/vnd.google-apps.folder'")
                }

                if (searchQuery.isNotEmpty()) {
                    val safeSearch = searchQuery.replace("'", "\\'")
                    queryParts.add("name contains '$safeSearch'")
                }

                when (category) {
                    "videos", "movies" -> queryParts.add("(mimeType contains 'video/' or name contains '.mp4' or name contains '.mkv' or name contains '.webm' or name contains '.avi' or name contains '.mov' or name contains '.flv' or name contains '.m4v')")
                    "images" -> queryParts.add("(mimeType contains 'image/' or name contains '.jpg' or name contains '.jpeg' or name contains '.png' or name contains '.webp' or name contains '.gif')")
                    "documents" -> queryParts.add("(mimeType contains 'text/' or mimeType contains 'pdf' or mimeType contains 'document' or mimeType contains 'presentation' or mimeType contains 'spreadsheet' or name contains '.pdf' or name contains '.txt' or name contains '.doc')")
                    "archives" -> queryParts.add("(name contains '.zip' or name contains '.rar' or name contains '.7z' or name contains '.tar' or name contains '.gz')")
                }

                val fullQuery = queryParts.joinToString(" and ")
                var filesUrl = "https://www.googleapis.com/drive/v3/files?pageSize=100&supportsAllDrives=true&includeItemsFromAllDrives=true&fields=nextPageToken,files(id,name,mimeType,size,createdTime,modifiedTime,thumbnailLink,webContentLink,webViewLink,iconLink)&q=" +
                        URLEncoder.encode(fullQuery, "UTF-8") +
                        "&orderBy=createdTime%20desc"

                if (!pageToken.isNullOrEmpty()) {
                    filesUrl += "&pageToken=" + URLEncoder.encode(pageToken, "UTF-8")
                }

                var filesReq = Request.Builder()
                    .url(filesUrl)
                    .addHeader("Authorization", "Bearer $token")
                    .build()

                var filesResp = httpClient.newCall(filesReq).execute()

                // If token expired, refresh and retry once
                if (filesResp.code == 401) {
                    val refreshToken = prefs.getString(KEY_REFRESH_TOKEN, null)
                    if (!refreshToken.isNullOrEmpty()) {
                        val newToken = refreshAccessToken(refreshToken)
                        if (!newToken.isNullOrEmpty()) {
                            token = newToken
                            filesReq = Request.Builder()
                                .url(filesUrl)
                                .addHeader("Authorization", "Bearer $token")
                                .build()
                            filesResp = httpClient.newCall(filesReq).execute()
                        }
                    }
                }

                val filesArray = JSArray()
                var nextPage: String? = null

                if (filesResp.isSuccessful) {
                    val bodyStr = filesResp.body?.string() ?: "{}"
                    val json = JSONObject(bodyStr)
                    nextPage = json.optString("nextPageToken", "")
                    val rawFiles = json.optJSONArray("files")
                    if (rawFiles != null) {
                        for (i in 0 until rawFiles.length()) {
                            val f = rawFiles.getJSONObject(i)
                            val fObj = JSObject()
                            val fName = f.optString("name")
                            val fMime = f.optString("mimeType")
                            fObj.put("id", f.optString("id"))
                            fObj.put("name", fName)
                            fObj.put("mimeType", fMime)
                            fObj.put("size", f.optLong("size", 0L))
                            fObj.put("createdTime", f.optString("createdTime"))
                            fObj.put("modifiedTime", f.optString("modifiedTime"))
                            fObj.put("thumbnailLink", f.optString("thumbnailLink"))
                            fObj.put("webContentLink", f.optString("webContentLink"))
                            fObj.put("webViewLink", f.optString("webViewLink"))
                            fObj.put("iconLink", f.optString("iconLink"))

                            // Category categorization helper
                            val cat = when {
                                fMime.contains("video/") || fName.matches(Regex(""".*\.(mp4|mkv|webm|avi|mov|flv)$""", RegexOption.IGNORE_CASE)) -> "movies"
                                fMime.contains("image/") || fName.matches(Regex(""".*\.(jpg|jpeg|png|webp|gif)$""", RegexOption.IGNORE_CASE)) -> "images"
                                fName.matches(Regex(""".*\.(zip|rar|7z|tar|gz)$""", RegexOption.IGNORE_CASE)) -> "archives"
                                fMime.contains("text/") || fMime.contains("pdf") || fMime.contains("document") -> "documents"
                                else -> "other"
                            }
                            fObj.put("category", cat)
                            filesArray.put(fObj)
                        }
                    }
                }

                val res = JSObject()
                res.put("success", true)
                res.put("files", filesArray)
                res.put("folderId", appFolderId)
                res.put("nextPageToken", nextPage)

                if (userObj != null) {
                    val u = JSObject()
                    u.put("displayName", userObj.optString("displayName"))
                    u.put("emailAddress", userObj.optString("emailAddress"))
                    u.put("photoLink", userObj.optString("photoLink"))
                    res.put("user", u)
                }

                if (storageObj != null) {
                    val s = JSObject()
                    s.put("usage", storageObj.optLong("usage", 0L))
                    s.put("limit", storageObj.optLong("limit", 0L))
                    res.put("storageQuota", s)
                }

                call.resolve(res)
            } catch (e: Exception) {
                Log.e(TAG, "fetchRealGoogleDriveData failed: ${e.message}", e)
                call.resolve(JSObject().put("success", false).put("error", e.message ?: "Network error"))
            }
        }.start()
    }

    // ─── 6. Real File Upload (Native File Picker & Resumable Streaming) ───
    @PluginMethod
    fun uploadFile(call: PluginCall) {
        val uriStr = call.getString("uri")
        if (uriStr.isNullOrEmpty()) {
            // Launch System File Picker
            val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                type = "*/*"
                addCategory(Intent.CATEGORY_OPENABLE)
            }
            startActivityForResult(call, intent, "fileUploadPickerResult")
            return
        }

        // Upload provided URI
        performUpload(call, Uri.parse(uriStr))
    }

    @ActivityCallback
    private fun fileUploadPickerResult(call: PluginCall, result: ActivityResult) {
        if (result.resultCode == Activity.RESULT_OK && result.data?.data != null) {
            val selectedUri = result.data!!.data!!
            performUpload(call, selectedUri)
        } else {
            val res = JSObject()
            res.put("success", false)
            res.put("cancelled", true)
            call.resolve(res)
        }
    }

    private fun performUpload(call: PluginCall, uri: Uri) {
        var token = getValidToken()
        if (token.isNullOrEmpty()) {
            call.reject("Not authenticated with Google Drive")
            return
        }

        Thread {
            var stagingFile: File? = null
            try {
                var fileName = "Upload_" + System.currentTimeMillis()
                var fileSize = -1L
                var mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream"

                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                        if (nameIndex != -1) cursor.getString(nameIndex)?.let { if (it.isNotEmpty()) fileName = it }
                        if (sizeIndex != -1) fileSize = cursor.getLong(sizeIndex)
                    }
                }

                if (mimeType == "application/octet-stream" && fileName.contains(".")) {
                    val ext = fileName.substringAfterLast(".", "").lowercase()
                    val inferredMime = android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
                    if (!inferredMime.isNullOrEmpty()) mimeType = inferredMime
                }

                // Stage file to app cache to ensure exact file size, seekable input, and zero stream failures
                val tempFile = File(context.cacheDir, "gdrive_upload_${System.currentTimeMillis()}.tmp")
                try {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        FileOutputStream(tempFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                    if (tempFile.exists() && tempFile.length() > 0) {
                        stagingFile = tempFile
                        fileSize = tempFile.length()
                    }
                } catch (stageEx: Exception) {
                    Log.w(TAG, "Staging fallback notice: ${stageEx.message}")
                }

                if (fileSize <= 0 && stagingFile == null) {
                    try {
                        context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { afd ->
                            val len = afd.length
                            if (len > 0) fileSize = len
                        }
                    } catch (_: Exception) {}
                }

                var folderId = ensureAppFolderId(token)

                // 1. Initiate Resumable Upload Session
                val metaJson = JSONObject().apply {
                    put("name", fileName)
                    if (!folderId.isNullOrEmpty()) {
                        put("parents", org.json.JSONArray().put(folderId))
                    }
                }

                var initReq = Request.Builder()
                    .url("https://www.googleapis.com/upload/drive/v3/files?uploadType=resumable&supportsAllDrives=true")
                    .addHeader("Authorization", "Bearer $token")
                    .addHeader("X-Upload-Content-Type", mimeType)
                    .apply {
                        if (fileSize > 0) addHeader("X-Upload-Content-Length", fileSize.toString())
                    }
                    .post(metaJson.toString().toRequestBody("application/json; charset=UTF-8".toMediaTypeOrNull()))
                    .build()

                var initResp = httpClient.newCall(initReq).execute()

                // If token expired, refresh and retry once
                if (initResp.code == 401) {
                    val refreshToken = prefs.getString(KEY_REFRESH_TOKEN, null)
                    if (!refreshToken.isNullOrEmpty()) {
                        val newToken = refreshAccessToken(refreshToken)
                        if (!newToken.isNullOrEmpty()) {
                            token = newToken
                            initReq = Request.Builder()
                                .url("https://www.googleapis.com/upload/drive/v3/files?uploadType=resumable&supportsAllDrives=true")
                                .addHeader("Authorization", "Bearer $token")
                                .addHeader("X-Upload-Content-Type", mimeType)
                                .apply {
                                    if (fileSize > 0) addHeader("X-Upload-Content-Length", fileSize.toString())
                                }
                                .post(metaJson.toString().toRequestBody("application/json; charset=UTF-8".toMediaTypeOrNull()))
                                .build()
                            initResp = httpClient.newCall(initReq).execute()
                        }
                    }
                }

                // If 403 or 404 with folder parent, clear cached folder ID and retry without parent
                if ((initResp.code == 403 || initResp.code == 404) && !folderId.isNullOrEmpty()) {
                    prefs.edit().remove(KEY_APP_FOLDER_ID).apply()
                    folderId = null
                    val fallbackMeta = JSONObject().apply { put("name", fileName) }
                    initReq = Request.Builder()
                        .url("https://www.googleapis.com/upload/drive/v3/files?uploadType=resumable&supportsAllDrives=true")
                        .addHeader("Authorization", "Bearer $token")
                        .addHeader("X-Upload-Content-Type", mimeType)
                        .apply {
                            if (fileSize > 0) addHeader("X-Upload-Content-Length", fileSize.toString())
                        }
                        .post(fallbackMeta.toString().toRequestBody("application/json; charset=UTF-8".toMediaTypeOrNull()))
                        .build()
                    initResp = httpClient.newCall(initReq).execute()
                }

                val uploadUrl = initResp.header("Location")
                if (uploadUrl.isNullOrEmpty()) {
                    val errBody = initResp.body?.string() ?: ""
                    call.reject(parseGoogleErrorMessage(errBody, "Failed to initiate Google Drive upload session: HTTP ${initResp.code}"))
                    return@Thread
                }

                // 2. Stream Data to Google Drive Resumable Endpoint
                val progressBody = object : RequestBody() {
                    override fun contentType() = mimeType.toMediaTypeOrNull()
                    override fun contentLength() = fileSize
                    override fun writeTo(sink: BufferedSink) {
                        val buffer = ByteArray(64 * 1024)
                        var bytesRead: Int
                        var uploaded = 0L
                        var lastEmitTime = 0L

                        val stream = if (stagingFile != null && stagingFile!!.exists()) {
                            java.io.FileInputStream(stagingFile)
                        } else {
                            context.contentResolver.openInputStream(uri)
                        }

                        if (stream == null) {
                            throw java.io.IOException("Cannot open input stream for upload")
                        }

                        stream.use { s ->
                            while (s.read(buffer).also { bytesRead = it } != -1) {
                                sink.write(buffer, 0, bytesRead)
                                uploaded += bytesRead
                                val now = System.currentTimeMillis()
                                if (now - lastEmitTime > 400 && fileSize > 0) {
                                    lastEmitTime = now
                                    val pct = ((uploaded.toDouble() / fileSize) * 100).toInt()
                                    val ev = JSObject().apply {
                                        put("progress", pct)
                                        put("transferred", uploaded)
                                        put("total", fileSize)
                                        put("filename", fileName)
                                    }
                                    notifyListeners("driveUploadProgress", ev)
                                }
                            }
                        }
                    }
                }

                val uploadReq = Request.Builder()
                    .url(uploadUrl)
                    .apply {
                        if (fileSize > 0) {
                            addHeader("Content-Range", "bytes 0-${fileSize - 1}/$fileSize")
                            addHeader("Content-Length", fileSize.toString())
                        }
                    }
                    .put(progressBody)
                    .build()

                val uploadResp = httpClient.newCall(uploadReq).execute()
                if (uploadResp.isSuccessful || uploadResp.code == 201) {
                    val respJson = JSONObject(uploadResp.body?.string() ?: "{}")
                    val ret = JSObject().apply {
                        put("success", true)
                        put("id", respJson.optString("id"))
                        put("name", respJson.optString("name", fileName))
                        put("mimeType", respJson.optString("mimeType", mimeType))
                        put("size", respJson.optLong("size", fileSize))
                    }
                    call.resolve(ret)
                } else {
                    val errBody = uploadResp.body?.string() ?: ""
                    call.reject(parseGoogleErrorMessage(errBody, "Google Drive upload failed: HTTP ${uploadResp.code}"))
                }
            } catch (e: Exception) {
                Log.e(TAG, "performUpload error: ${e.message}", e)
                call.reject("Upload error: ${e.message}")
            } finally {
                stagingFile?.delete()
            }
        }.start()
    }

    // ─── 7. Real Native Cloud Transfer (Piping Remote Media Directly into Drive) ───
    @PluginMethod
    fun startCloudTransferNative(call: PluginCall) {
        val sourceUrl = call.getString("url")?.trim() ?: ""
        val title = call.getString("title")?.trim() ?: "Cloud_Media_${System.currentTimeMillis()}"
        val type = call.getString("type") ?: "movie"

        if (sourceUrl.isEmpty()) {
            call.reject("Source media URL is required")
            return
        }

        if (sourceUrl.startsWith("magnet:", ignoreCase = true)) {
            call.reject("Torrent/Magnet transfers directly into Google Drive require the Remote Cloud Runner (Google Colab). Direct media HTTP/HTTPS links stream directly.")
            return
        }

        performCloudTransfer(
            context,
            sourceUrl,
            title,
            type,
            onProgress = { pct, transferred, total ->
                val ev = JSObject().apply {
                    put("progress", pct)
                    put("transferred", transferred)
                    put("total", total)
                    put("filename", title)
                }
                notifyListeners("cloudTransferProgress", ev)
            },
            onSuccess = { fileId, fileName ->
                val ret = JSObject().apply {
                    put("success", true)
                    put("id", fileId)
                    put("name", fileName)
                    put("webViewLink", "https://drive.google.com/file/d/$fileId/view?usp=sharing")
                }
                call.resolve(ret)
            },
            onError = { msg ->
                call.reject(msg)
            }
        )
    }

    @PluginMethod
    fun cancelCloudTransferNative(call: PluginCall) {
        isTransferCancelled = true
        val ret = JSObject().apply { put("success", true) }
        call.resolve(ret)
    }

    // ─── 8. Real Rename File ───
    @PluginMethod
    fun renameFile(call: PluginCall) {
        val fileId = call.getString("fileId")
        val newName = call.getString("newName")?.trim()

        if (fileId.isNullOrEmpty() || newName.isNullOrEmpty()) {
            call.reject("fileId and newName are required")
            return
        }

        var token = getValidToken()
        if (token.isNullOrEmpty()) {
            call.reject("Not authenticated with Google Drive")
            return
        }

        Thread {
            try {
                val patchJson = JSONObject().apply {
                    put("name", newName)
                }
                val reqBody = patchJson.toString().toRequestBody("application/json".toMediaTypeOrNull())
                var req = Request.Builder()
                    .url("https://www.googleapis.com/drive/v3/files/$fileId?supportsAllDrives=true")
                    .addHeader("Authorization", "Bearer $token")
                    .patch(reqBody)
                    .build()

                var resp = httpClient.newCall(req).execute()

                if (resp.code == 401) {
                    val refreshToken = prefs.getString(KEY_REFRESH_TOKEN, null)
                    if (!refreshToken.isNullOrEmpty()) {
                        val newToken = refreshAccessToken(refreshToken)
                        if (!newToken.isNullOrEmpty()) {
                            token = newToken
                            req = Request.Builder()
                                .url("https://www.googleapis.com/drive/v3/files/$fileId?supportsAllDrives=true")
                                .addHeader("Authorization", "Bearer $token")
                                .patch(reqBody)
                                .build()
                            resp = httpClient.newCall(req).execute()
                        }
                    }
                }

                if (resp.isSuccessful) {
                    val ret = JSObject().apply {
                        put("success", true)
                        put("name", newName)
                        put("fileId", fileId)
                    }
                    call.resolve(ret)
                } else {
                    val errBody = resp.body?.string() ?: ""
                    call.reject(parseGoogleErrorMessage(errBody, "Failed to rename file: HTTP ${resp.code}"))
                }
            } catch (e: Exception) {
                call.reject("Rename error: ${e.message}")
            }
        }.start()
    }

    // ─── 9. Real Delete File (With Automatic Resilient Trash Fallback) ───
    @PluginMethod
    fun deleteFile(call: PluginCall) {
        val fileId = call.getString("fileId")
        if (fileId.isNullOrEmpty()) {
            call.reject("fileId is required")
            return
        }

        var token = getValidToken()
        if (token.isNullOrEmpty()) {
            call.reject("Not authenticated with Google Drive")
            return
        }

        Thread {
            try {
                var req = Request.Builder()
                    .url("https://www.googleapis.com/drive/v3/files/$fileId?supportsAllDrives=true")
                    .addHeader("Authorization", "Bearer $token")
                    .delete()
                    .build()
                var resp = httpClient.newCall(req).execute()

                if (resp.code == 401) {
                    val refreshToken = prefs.getString(KEY_REFRESH_TOKEN, null)
                    if (!refreshToken.isNullOrEmpty()) {
                        val newToken = refreshAccessToken(refreshToken)
                        if (!newToken.isNullOrEmpty()) {
                            token = newToken
                            req = Request.Builder()
                                .url("https://www.googleapis.com/drive/v3/files/$fileId?supportsAllDrives=true")
                                .addHeader("Authorization", "Bearer $token")
                                .delete()
                                .build()
                            resp = httpClient.newCall(req).execute()
                        }
                    }
                }

                // If permanent DELETE succeeded:
                if (resp.isSuccessful || resp.code == 204) {
                    val ret = JSObject().put("success", true).put("fileId", fileId).put("trashed", false)
                    call.resolve(ret)
                    return@Thread
                }

                // Fallback: If permanent DELETE was blocked (HTTP 403 / 404 / 400), move to Trash (PATCH trashed=true)
                // In Google Drive, moving to Trash does not require permanent delete privileges!
                val trashReqBody = JSONObject().put("trashed", true).toString()
                    .toRequestBody("application/json".toMediaTypeOrNull())
                val trashReq = Request.Builder()
                    .url("https://www.googleapis.com/drive/v3/files/$fileId?supportsAllDrives=true")
                    .addHeader("Authorization", "Bearer $token")
                    .patch(trashReqBody)
                    .build()
                val trashResp = httpClient.newCall(trashReq).execute()

                if (trashResp.isSuccessful || trashResp.code == 200 || trashResp.code == 204) {
                    val ret = JSObject().put("success", true).put("fileId", fileId).put("trashed", true)
                    call.resolve(ret)
                    return@Thread
                }

                // If both permanent delete and trash failed, parse exact Google error message
                val errBody = trashResp.body?.string() ?: (resp.body?.string() ?: "")
                call.reject(parseGoogleErrorMessage(errBody, "Google Drive deletion error: HTTP ${trashResp.code}"))
            } catch (e: Exception) {
                call.reject("Delete error: ${e.message}")
            }
        }.start()
    }

    // ─── 10. Real Safe Share Link (No Silent Public Exposure) ───
    @PluginMethod
    fun makeFilePublicAndGetShareLink(call: PluginCall) {
        val fileId = call.getString("fileId")
        val makePublic = call.getBoolean("makePublic", false) ?: false

        if (fileId.isNullOrEmpty()) {
            call.reject("fileId is required")
            return
        }

        var token = getValidToken()

        Thread {
            try {
                // If user explicitly confirmed making file public:
                if (makePublic && !token.isNullOrEmpty()) {
                    val permJson = JSONObject().apply {
                        put("role", "reader")
                        put("type", "anyone")
                    }
                    val reqBody = permJson.toString().toRequestBody("application/json".toMediaTypeOrNull())
                    var permReq = Request.Builder()
                        .url("https://www.googleapis.com/drive/v3/files/$fileId/permissions?supportsAllDrives=true")
                        .addHeader("Authorization", "Bearer $token")
                        .post(reqBody)
                        .build()
                    var permResp = httpClient.newCall(permReq).execute()
                    if (permResp.code == 401) {
                        val refreshToken = prefs.getString(KEY_REFRESH_TOKEN, null)
                        if (!refreshToken.isNullOrEmpty()) {
                            val newToken = refreshAccessToken(refreshToken)
                            if (!newToken.isNullOrEmpty()) {
                                token = newToken
                                permReq = Request.Builder()
                                    .url("https://www.googleapis.com/drive/v3/files/$fileId/permissions?supportsAllDrives=true")
                                    .addHeader("Authorization", "Bearer $token")
                                    .post(reqBody)
                                    .build()
                                httpClient.newCall(permReq).execute()
                            }
                        }
                    }
                }

                var shareUrl = "https://drive.google.com/file/d/$fileId/view?usp=sharing"
                if (!token.isNullOrEmpty()) {
                    val getReq = Request.Builder()
                        .url("https://www.googleapis.com/drive/v3/files/$fileId?fields=webViewLink,webContentLink&supportsAllDrives=true")
                        .addHeader("Authorization", "Bearer $token")
                        .build()
                    val getResp = httpClient.newCall(getReq).execute()
                    if (getResp.isSuccessful) {
                        val gJson = JSONObject(getResp.body?.string() ?: "{}")
                        val wvl = gJson.optString("webViewLink", "")
                        if (wvl.isNotEmpty()) shareUrl = wvl
                    }
                }

                val ret = JSObject().apply {
                    put("success", true)
                    put("shareLink", shareUrl)
                    put("isPublic", makePublic)
                }
                call.resolve(ret)
            } catch (e: Exception) {
                val fallback = "https://drive.google.com/file/d/$fileId/view?usp=sharing"
                call.resolve(JSObject().put("success", true).put("shareLink", fallback).put("isPublic", false))
            }
        }.start()
    }

    // ─── 11. Real File Details / Metadata ───
    @PluginMethod
    fun getFileMetadata(call: PluginCall) {
        val fileId = call.getString("fileId")
        if (fileId.isNullOrEmpty()) {
            call.reject("fileId is required")
            return
        }

        var token = getValidToken()
        if (token.isNullOrEmpty()) {
            call.reject("Not authenticated with Google Drive")
            return
        }

        Thread {
            try {
                var req = Request.Builder()
                    .url("https://www.googleapis.com/drive/v3/files/$fileId?fields=id,name,mimeType,size,createdTime,modifiedTime,thumbnailLink,webContentLink,webViewLink,shared,permissions&supportsAllDrives=true")
                    .addHeader("Authorization", "Bearer $token")
                    .build()

                var resp = httpClient.newCall(req).execute()
                if (resp.code == 401) {
                    val refreshToken = prefs.getString(KEY_REFRESH_TOKEN, null)
                    if (!refreshToken.isNullOrEmpty()) {
                        val newToken = refreshAccessToken(refreshToken)
                        if (!newToken.isNullOrEmpty()) {
                            token = newToken
                            req = Request.Builder()
                                .url("https://www.googleapis.com/drive/v3/files/$fileId?fields=id,name,mimeType,size,createdTime,modifiedTime,thumbnailLink,webContentLink,webViewLink,shared,permissions&supportsAllDrives=true")
                                .addHeader("Authorization", "Bearer $token")
                                .build()
                            resp = httpClient.newCall(req).execute()
                        }
                    }
                }

                if (resp.isSuccessful) {
                    val json = JSONObject(resp.body?.string() ?: "{}")
                    val ret = JSObject().apply {
                        put("success", true)
                        put("id", json.optString("id"))
                        put("name", json.optString("name"))
                        put("mimeType", json.optString("mimeType"))
                        put("size", json.optLong("size", 0L))
                        put("createdTime", json.optString("createdTime"))
                        put("modifiedTime", json.optString("modifiedTime"))
                        put("thumbnailLink", json.optString("thumbnailLink"))
                        put("webViewLink", json.optString("webViewLink"))
                        put("webContentLink", json.optString("webContentLink"))
                        put("shared", json.optBoolean("shared", false))
                    }
                    call.resolve(ret)
                } else {
                    val errBody = resp.body?.string() ?: ""
                    call.reject(parseGoogleErrorMessage(errBody, "Failed to retrieve file metadata: HTTP ${resp.code}"))
                }
            } catch (e: Exception) {
                call.reject("Metadata error: ${e.message}")
            }
        }.start()
    }

    // ─── 12. Real Download to Device Storage ───
    @PluginMethod
    fun downloadFile(call: PluginCall) {
        val fileId = call.getString("fileId")
        var fileName = call.getString("fileName") ?: "downloaded_file"

        if (fileId.isNullOrEmpty()) {
            call.reject("fileId is required")
            return
        }

        var token = getValidToken()
        if (token.isNullOrEmpty()) {
            call.reject("Not authenticated with Google Drive")
            return
        }

        Thread {
            try {
                val downloadUrl = "https://www.googleapis.com/drive/v3/files/$fileId?alt=media&supportsAllDrives=true"
                var req = Request.Builder()
                    .url(downloadUrl)
                    .addHeader("Authorization", "Bearer $token")
                    .build()

                var resp = httpClient.newCall(req).execute()
                if (resp.code == 401) {
                    val refreshToken = prefs.getString(KEY_REFRESH_TOKEN, null)
                    if (!refreshToken.isNullOrEmpty()) {
                        val newToken = refreshAccessToken(refreshToken)
                        if (!newToken.isNullOrEmpty()) {
                            token = newToken
                            req = Request.Builder()
                                .url(downloadUrl)
                                .addHeader("Authorization", "Bearer $token")
                                .build()
                            resp = httpClient.newCall(req).execute()
                        }
                    }
                }

                if (!resp.isSuccessful) {
                    val errBody = resp.body?.string() ?: ""
                    call.reject(parseGoogleErrorMessage(errBody, "Drive download error: HTTP ${resp.code}"))
                    return@Thread
                }

                val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                if (!downloadDir.exists()) downloadDir.mkdirs()

                val targetFile = File(downloadDir, fileName)
                resp.body?.byteStream()?.use { input ->
                    FileOutputStream(targetFile).use { output ->
                        input.copyTo(output)
                    }
                }

                val ret = JSObject().apply {
                    put("success", true)
                    put("filePath", targetFile.absolutePath)
                    put("fileName", fileName)
                }
                call.resolve(ret)
            } catch (e: Exception) {
                Log.e(TAG, "downloadFile error: ${e.message}", e)
                call.reject("Download error: ${e.message}")
            }
        }.start()
    }

    // ─── 13. Real Create Folder ───
    @PluginMethod
    fun createFolder(call: PluginCall) {
        val folderName = (call.getString("name") ?: call.getString("folderName"))?.trim() ?: "New Folder"
        val parentId = call.getString("parentId") ?: ensureAppFolderId(getValidToken())

        var token = getValidToken()
        if (token.isNullOrEmpty()) {
            call.reject("Not authenticated with Google Drive")
            return
        }

        Thread {
            try {
                val folderPayload = JSONObject().apply {
                    put("name", folderName)
                    put("mimeType", "application/vnd.google-apps.folder")
                    if (!parentId.isNullOrEmpty()) {
                        put("parents", org.json.JSONArray().put(parentId))
                    }
                }
                val reqBody = folderPayload.toString().toRequestBody("application/json".toMediaTypeOrNull())
                var createReq = Request.Builder()
                    .url("https://www.googleapis.com/drive/v3/files?supportsAllDrives=true")
                    .addHeader("Authorization", "Bearer $token")
                    .post(reqBody)
                    .build()

                var cResp = httpClient.newCall(createReq).execute()
                if (cResp.code == 401) {
                    val refreshToken = prefs.getString(KEY_REFRESH_TOKEN, null)
                    if (!refreshToken.isNullOrEmpty()) {
                        val newToken = refreshAccessToken(refreshToken)
                        if (!newToken.isNullOrEmpty()) {
                            token = newToken
                            createReq = Request.Builder()
                                .url("https://www.googleapis.com/drive/v3/files?supportsAllDrives=true")
                                .addHeader("Authorization", "Bearer $token")
                                .post(reqBody)
                                .build()
                            cResp = httpClient.newCall(createReq).execute()
                        }
                    }
                }

                if (cResp.isSuccessful) {
                    val cJson = JSONObject(cResp.body?.string() ?: "{}")
                    val ret = JSObject().apply {
                        put("success", true)
                        put("id", cJson.optString("id"))
                        put("name", cJson.optString("name", folderName))
                    }
                    call.resolve(ret)
                } else {
                    val errBody = cResp.body?.string() ?: ""
                    call.reject(parseGoogleErrorMessage(errBody, "Failed to create folder: HTTP ${cResp.code}"))
                }
            } catch (e: Exception) {
                call.reject("Create folder error: ${e.message}")
            }
        }.start()
    }
}
