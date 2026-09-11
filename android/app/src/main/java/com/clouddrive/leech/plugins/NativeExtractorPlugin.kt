package com.clouddrive.leech.plugins

import com.clouddrive.leech.App
import com.clouddrive.leech.extractor.resolvers.UsersDriveResolver
import com.clouddrive.leech.extractor.resolvers.netflix.NetflixResolver
import com.clouddrive.leech.proxy.LocalMediaProxy
import com.getcapacitor.JSArray
import com.getcapacitor.JSObject
import com.getcapacitor.Plugin
import com.getcapacitor.PluginCall
import com.getcapacitor.PluginMethod
import com.getcapacitor.annotation.CapacitorPlugin
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject

@CapacitorPlugin(name = "NativeExtractor")
class NativeExtractorPlugin : Plugin() {

    private val scope = CoroutineScope(Dispatchers.Main)
    private val gson = Gson()

    private fun safeJsonArray(rawJson: String): JSArray {
        return try {
            JSArray(rawJson)
        } catch (e: Exception) {
            JSArray()
        }
    }

    private fun safeJsonObject(rawJson: String): JSObject {
        return try {
            JSObject.fromJSONObject(JSONObject(rawJson))
        } catch (e: Exception) {
            try {
                JSObject(rawJson)
            } catch (_: Exception) {
                JSObject()
            }
        }
    }

    @PluginMethod
    fun searchMovies(call: PluginCall) {
        val query = call.getString("query") ?: ""
        val page = call.getInt("page") ?: 1

        scope.launch {
            try {
                val response = App.instance.extractorManager.searchMovies(query, page)
                val ret = JSObject()
                if (response.success && response.data != null) {
                    val jsonArrStr = gson.toJson(response.data)
                    ret.put("success", true)
                    ret.put("results", safeJsonArray(jsonArrStr))
                } else {
                    ret.put("success", false)
                    ret.put("error", response.error ?: "Search failed")
                    ret.put("results", JSArray())
                }
                call.resolve(ret)
            } catch (e: Exception) {
                val ret = JSObject()
                ret.put("success", false)
                ret.put("error", e.message ?: "Search exception")
                ret.put("results", JSArray())
                call.resolve(ret)
            }
        }
    }

    @PluginMethod
    fun clearCache(call: PluginCall) {
        App.instance.extractorManager.clearCache()
        val ret = JSObject()
        ret.put("success", true)
        call.resolve(ret)
    }

    @PluginMethod
    fun getMovieDetails(call: PluginCall) {
        val url = call.getString("url") ?: ""
        if (url.isEmpty()) {
            call.reject("URL parameter is required")
            return
        }

        scope.launch {
            try {
                val response = App.instance.extractorManager.getMovieDetails(url)
                if (response.success && response.data != null) {
                    val jsonObjStr = gson.toJson(response.data)
                    val ret = JSObject()
                    ret.put("success", true)
                    ret.put("details", safeJsonObject(jsonObjStr))
                    call.resolve(ret)
                } else {
                    call.reject(response.error ?: "Failed to get movie details")
                }
            } catch (e: Exception) {
                call.reject(e.message ?: "Extraction error")
            }
        }
    }

    @PluginMethod
    fun resolveMovieStream(call: PluginCall) {
        val url = call.getString("url") ?: ""
        if (url.isEmpty()) {
            call.reject("URL parameter is required")
            return
        }

        scope.launch {
            try {
                // 1. Layered Scraper & Direct Link Extractor for UsersDrive (Phases 1-7, 12, 18, 20)
                if (UsersDriveResolver.canHandle(url)) {
                    val act = activity
                    if (act != null && !act.isFinishing && !act.isDestroyed) {
                        when (val res = UsersDriveResolver.resolve(act, url)) {
                            is UsersDriveResolver.ResolveResult.Success -> {
                                val s = res.stream
                                val proxyPort = com.clouddrive.leech.proxy.LocalMediaProxy.port
                                val proxyUrl = if (proxyPort > 0) {
                                    "http://127.0.0.1:$proxyPort/stream?url=" + java.net.URLEncoder.encode(s.directUrl, "UTF-8")
                                } else {
                                    s.directUrl
                                }
                                val ret = JSObject()
                                ret.put("success", true)
                                ret.put("streamUrl", s.directUrl)
                                ret.put("downloadUrl", s.directUrl)
                                ret.put("proxyUrl", proxyUrl)
                                ret.put("directUrl", s.directUrl)
                                ret.put("fileName", s.fileName)
                                ret.put("contentType", s.contentType)
                                ret.put("contentLength", s.contentLength)
                                ret.put("type", if (s.isZip) "download" else "video")
                                ret.put("isZip", s.isZip)
                                ret.put("title", s.fileName.ifEmpty { "UsersDrive Media" })
                                call.resolve(ret)
                                return@launch
                            }
                            is UsersDriveResolver.ResolveResult.Error -> {
                                call.reject(res.message, res.code)
                                return@launch
                            }
                        }
                    } else {
                        call.reject("Activity unavailable for resolution", "ACTIVITY_UNAVAILABLE")
                        return@launch
                    }
                } else if (UsersDriveResolver.isAlreadyDirectMediaUrl(url)) {
                    val validated = UsersDriveResolver.validateCandidateDirectUrl(url)
                    if (validated != null) {
                        val isZip = validated.contains(".zip", true) || validated.contains(".rar", true)
                        val fileName = android.net.Uri.parse(validated).lastPathSegment ?: "video.mp4"
                        val proxyPort = com.clouddrive.leech.proxy.LocalMediaProxy.port
                        val proxyUrl = if (proxyPort > 0) {
                            "http://127.0.0.1:$proxyPort/stream?url=" + java.net.URLEncoder.encode(validated, "UTF-8")
                        } else {
                            validated
                        }
                        val ret = JSObject()
                        ret.put("success", true)
                        ret.put("streamUrl", validated)
                        ret.put("downloadUrl", validated)
                        ret.put("proxyUrl", proxyUrl)
                        ret.put("directUrl", validated)
                        ret.put("fileName", fileName)
                        ret.put("type", if (isZip) "download" else "video")
                        ret.put("isZip", isZip)
                        ret.put("title", fileName)
                        call.resolve(ret)
                        return@launch
                    }
                }

                // 1b. Netflix & NetMirror Cloud Stream Resolver
                if (NetflixResolver.canHandle(url)) {
                    val act = activity
                    if (act != null && !act.isFinishing && !act.isDestroyed) {
                        val nRes = NetflixResolver.resolve(act, url)
                        if (nRes != null && nRes.success && nRes.streamUrl.isNotEmpty()) {
                            val ret = JSObject()
                            ret.put("success", true)
                            ret.put("streamUrl", nRes.streamUrl)
                            ret.put("downloadUrl", nRes.streamUrl)
                            ret.put("embedUrl", nRes.streamUrl)
                            ret.put("type", nRes.type)
                            ret.put("title", nRes.title)
                            ret.put("isHls", nRes.isHls)
                            call.resolve(ret)
                            return@launch
                        }
                    }
                }

                // 2. Standard ExtractorManager Pipeline
                val response = App.instance.extractorManager.resolveMovieStream(url)
                if (response.success && response.data != null) {
                    val jsonObjStr = gson.toJson(response.data)
                    val ret = safeJsonObject(jsonObjStr)
                    ret.put("success", true)
                    call.resolve(ret)
                } else {
                    call.reject(response.error ?: "Failed to resolve stream")
                }
            } catch (e: Exception) {
                call.reject(e.message ?: "Stream resolution error")
            }
        }
    }

    @PluginMethod
    fun resolveNetflixStream(call: PluginCall) {
        val url = call.getString("url") ?: ""
        if (url.isEmpty()) {
            call.reject("URL parameter is required")
            return
        }

        scope.launch {
            try {
                val act = activity
                if (act != null && !act.isFinishing && !act.isDestroyed) {
                    val nRes = NetflixResolver.resolve(act, url)
                    if (nRes != null && nRes.success) {
                        val ret = JSObject()
                        ret.put("success", true)
                        ret.put("streamUrl", nRes.streamUrl)
                        ret.put("downloadUrl", nRes.streamUrl)
                        ret.put("embedUrl", nRes.streamUrl)
                        ret.put("type", nRes.type)
                        ret.put("title", nRes.title)
                        ret.put("isHls", nRes.isHls)
                        call.resolve(ret)
                        return@launch
                    }
                }
                call.reject("Could not resolve Netflix stream")
            } catch (e: Exception) {
                call.reject(e.message ?: "Netflix resolution error")
            }
        }
    }

    @PluginMethod
    fun extractMedia(call: PluginCall) {
        val url = call.getString("url") ?: ""
        if (url.isEmpty()) {
            call.reject("URL parameter is required")
            return
        }

        scope.launch {
            try {
                val response = App.instance.extractorManager.extractMedia(url)
                if (response.success && response.data != null) {
                    val infoObjStr = gson.toJson(response.data)
                    val ret = JSObject()
                    ret.put("success", true)
                    val safeObj = safeJsonObject(infoObjStr)
                    if (safeObj.has("qualities") && !safeObj.has("formats")) {
                        safeObj.put("formats", safeObj.get("qualities"))
                    }
                    ret.put("info", safeObj)
                    ret.put("details", safeObj)
                    call.resolve(ret)
                } else {
                    call.reject(response.error ?: "Extraction failed")
                }
            } catch (e: Exception) {
                call.reject(e.message ?: "Media extraction error")
            }
        }
    }

    @PluginMethod
    fun searchAdult(call: PluginCall) {
        val query = call.getString("query") ?: "popular"
        val page = call.getInt("page") ?: 1
        val source = call.getString("source") ?: "all"

        scope.launch {
            try {
                val response = App.instance.extractorManager.searchAdult(query, page, source)
                val ret = JSObject()
                if (response.success && response.data != null) {
                    val jsonArrStr = gson.toJson(response.data)
                    ret.put("success", true)
                    ret.put("results", safeJsonArray(jsonArrStr))
                } else {
                    ret.put("success", false)
                    ret.put("results", JSArray())
                }
                call.resolve(ret)
            } catch (e: Exception) {
                val ret = JSObject()
                ret.put("success", false)
                ret.put("results", JSArray())
                call.resolve(ret)
            }
        }
    }

    @PluginMethod
    fun resolveAdultStream(call: PluginCall) {
        val url = call.getString("url") ?: ""
        if (url.isEmpty()) {
            call.reject("URL parameter is required")
            return
        }

        scope.launch {
            try {
                val response = App.instance.extractorManager.resolveAdultStream(url)
                if (response.success && response.data != null) {
                    val jsonObjStr = gson.toJson(response.data)
                    val jsonObj = safeJsonObject(jsonObjStr)
                    val ret = JSObject()
                    ret.put("success", true)
                    ret.put("resolved", jsonObj)
                    ret.put("stream", jsonObj)
                    ret.put("streamUrl", response.data.streamUrl)
                    ret.put("downloadUrl", response.data.downloadUrl)
                    ret.put("embedUrl", response.data.embedUrl)
                    ret.put("title", response.data.title)
                    ret.put("type", response.data.type)
                    if (jsonObj.has("qualities")) {
                        ret.put("qualities", jsonObj.get("qualities"))
                    }
                    if (jsonObj.has("headers")) {
                        ret.put("headers", jsonObj.get("headers"))
                    }
                    call.resolve(ret)
                } else {
                    call.reject(response.error ?: "Failed to resolve 18+ stream")
                }
            } catch (e: Exception) {
                call.reject(e.message ?: "18+ stream resolution error")
            }
        }
    }

    @PluginMethod
    fun getProxiedEmbedUrl(call: PluginCall) {
        val url = call.getString("url") ?: ""
        if (url.isEmpty()) {
            call.reject("URL parameter is required")
            return
        }
        val proxied = LocalMediaProxy.getProxiedEmbedUrl(url)
        val ret = JSObject()
        ret.put("success", true)
        ret.put("url", proxied)
        call.resolve(ret)
    }

    @PluginMethod
    fun probeMediaUrl(call: PluginCall) {
        val url = call.getString("url") ?: ""
        if (url.isEmpty()) {
            call.reject("URL parameter is required")
            return
        }
        scope.launch(Dispatchers.IO) {
            try {
                val safeUrl = NetflixResolver.sanitizeAndEncodeMediaUrl(url)
                val reqBuilder = okhttp3.Request.Builder()
                    .url(safeUrl)
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36")
                    .header("Referer", "https://cinejoy.to/")
                    .header("Range", "bytes=0-10")

                val client = okhttp3.OkHttpClient.Builder()
                    .dns(com.clouddrive.leech.vpn.net.UniversalAntiCensorDns.instance)
                    .connectTimeout(3, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(3, java.util.concurrent.TimeUnit.SECONDS)
                    .followRedirects(true)
                    .build()

                val response = client.newCall(reqBuilder.build()).execute()
                val code = response.code
                val isSuccess = response.isSuccessful || code == 206
                var isQuotaExceeded = false
                if (code == 403) {
                    val body = try { response.peekBody(1024).string() } catch (_: Exception) { "" }
                    if (body.contains("quota", ignoreCase = true) || body.contains("exceeded", ignoreCase = true)) {
                        isQuotaExceeded = true
                    }
                }
                response.close()

                val ret = JSObject()
                ret.put("status", code)
                ret.put("isAlive", isSuccess)
                ret.put("isQuotaExceeded", isQuotaExceeded)
                call.resolve(ret)
            } catch (e: Exception) {
                val ret = JSObject()
                ret.put("status", 0)
                ret.put("isAlive", false)
                ret.put("isQuotaExceeded", false)
                ret.put("error", e.message ?: "Probe error")
                call.resolve(ret)
            }
        }
    }
}

