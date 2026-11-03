package com.idbiintech.studio.attendance

import android.os.Build
import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.URLEncoder
import java.net.UnknownHostException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import javax.net.ssl.SSLException

/** Result of one API call. `data` is never null. */
class ApiResult(
    val ok: Boolean,
    val message: String,
    val data: JSONObject,
    val http: Int
) {
    /** Server says the token is no longer valid → show the activation screen. */
    val needsReactivation: Boolean get() = http == 401 && data.optBoolean("reactivate", false)
    val updateRequired: Boolean get() = http == 426 || data.optBoolean("update_required", false)
}

/** Small HTTPS client for mobile-app-api.php (form POST in, JSON out). */
class Api(private val store: Store) {

    /** Blocking call — always run through [Bg.run]. */
    fun call(action: String, params: Map<String, String> = emptyMap(), baseUrl: String? = null): ApiResult {
        val base = normaliseBaseUrl(baseUrl ?: store.serverUrl)
            ?: return ApiResult(false, "The Studio address is not valid.", JSONObject(), 0)
        val body = LinkedHashMap<String, String>()
        body["action"] = action
        body["device_id"] = store.deviceId
        body["app_version"] = BuildConfig.VERSION_NAME
        body["version_code"] = BuildConfig.VERSION_CODE.toString()
        body.putAll(params)
        val encoded = body.entries.joinToString("&") {
            URLEncoder.encode(it.key, "UTF-8") + "=" + URLEncoder.encode(it.value, "UTF-8")
        }

        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(base + BuildConfig.API_PATH).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 15_000
                readTimeout = 25_000
                doOutput = true
                useCaches = false
                instanceFollowRedirects = false
                setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "StudioAttendance/${BuildConfig.VERSION_NAME} (Android ${Build.VERSION.RELEASE})")
                val token = store.token
                if (!token.isNullOrEmpty()) {
                    setRequestProperty("Authorization", "Bearer $token")
                    setRequestProperty("X-Studio-Token", token)
                }
            }
            conn.outputStream.use { it.write(encoded.toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            if (code in 300..399) {
                return ApiResult(false, "The Studio address redirects to another page. Check the address (use https://).", JSONObject(), code)
            }
            val stream = if (code >= 400) conn.errorStream else conn.inputStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
            val json = try {
                JSONObject(text.trim().removePrefix("﻿"))
            } catch (e: Exception) {
                return ApiResult(
                    false,
                    if (code == 404) "Studio app service not found at this address. Check the Studio address."
                    else "Studio sent an unexpected reply (HTTP $code). Please try again later.",
                    JSONObject(), code
                )
            }
            ApiResult(
                json.optBoolean("status", false),
                json.optString("message", if (code >= 400) "Request failed (HTTP $code)." else ""),
                json.optJSONObject("data") ?: JSONObject(),
                code
            )
        } catch (e: UnknownHostException) {
            ApiResult(false, "Cannot reach Studio. Check your internet connection and the Studio address.", JSONObject(), 0)
        } catch (e: SocketTimeoutException) {
            ApiResult(false, "Studio is taking too long to answer. Please try again.", JSONObject(), 0)
        } catch (e: SSLException) {
            ApiResult(false, sslMessage(e), JSONObject(), 0)
        } catch (e: java.net.ConnectException) {
            ApiResult(false, "Studio refused the connection on https (port 443). Check the address, or Studio may not be set up for https.", JSONObject(), 0)
        } catch (e: IOException) {
            ApiResult(false, "Network error. Please check your internet connection.", JSONObject(), 0)
        } catch (e: Exception) {
            ApiResult(false, "Something went wrong: ${e.javaClass.simpleName}", JSONObject(), 0)
        } finally {
            conn?.disconnect()
        }
    }

    companion object {
        /** Turns Android's TLS error into a reason the employee / IT can act on. */
        fun sslMessage(e: Throwable): String {
            val all = StringBuilder()
            var c: Throwable? = e
            var depth = 0
            while (c != null && depth++ < 6) {
                all.append(c.javaClass.simpleName).append(": ").append(c.message ?: "").append(" | ")
                c = c.cause
            }
            val t = all.toString()
            val reason = when {
                t.contains("Trust anchor", true) || t.contains("CertPathValidator", true) ->
                    "Android does not trust Studio's security certificate. Usually the server is missing the intermediate certificate (incomplete chain) or uses a self-signed / company certificate. IT must install the full certificate chain on the Studio server."
                t.contains("Hostname", true) || t.contains("not verified", true) ->
                    "Studio's certificate does not match this address. Use the exact Studio domain name (not an IP address or a short name)."
                t.contains("expired", true) || t.contains("not yet valid", true) ->
                    "Studio's certificate is expired or not yet valid. Check the phone's date and time; if correct, IT must renew the certificate."
                t.contains("protocol", true) || t.contains("handshake", true) || t.contains("unrecognized_name", true) ->
                    "Studio's server and this phone could not agree on a secure connection (old TLS version or wrong https setup on the server)."
                else -> "Secure connection to Studio failed."
            }
            return "$reason\n\nTechnical detail: " + t.take(300).trimEnd(' ', '|')
        }

        /** "studio.example.com" → "https://studio.example.com/". Returns null if not usable. */
        fun normaliseBaseUrl(input: String): String? {
            var s = input.trim()
            if (s.isEmpty()) return null
            if (!s.startsWith("http://", true) && !s.startsWith("https://", true)) s = "https://$s"
            if (s.startsWith("http://", true) && !BuildConfig.DEBUG) s = "https://" + s.substring(7)
            if (!s.endsWith("/")) s += "/"
            return try {
                val u = URL(s)
                if (u.host.isNullOrBlank() || !u.host.contains('.') && u.host != "localhost") null else s
            } catch (e: Exception) {
                null
            }
        }
    }
}

/** Runs work off the main thread and delivers the result on the main thread. */
object Bg {
    private val pool: ExecutorService = Executors.newFixedThreadPool(3)
    private val main = Handler(Looper.getMainLooper())

    fun <T> run(work: () -> T, done: (T) -> Unit) {
        pool.execute {
            val result = work()
            main.post { done(result) }
        }
    }
}
