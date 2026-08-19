package com.adwio.player.data

import android.content.Context
import android.os.Build
import com.adwio.player.BuildConfig
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.net.URI
import java.util.UUID
import java.util.concurrent.TimeUnit

/** Privacy-minimized app telemetry. Never sends playlist credentials or full playlist URLs. */
class TelemetryClient(private val context: Context) {
    private val client = OkHttpClient.Builder().callTimeout(6, TimeUnit.SECONDS).build()
    private val prefs = context.getSharedPreferences("adwio_install", Context.MODE_PRIVATE)

    fun heartbeat(session: com.adwio.player.data.model.Session?) {
        val endpoint = BuildConfig.CONTROL_API_URL.trim().trimEnd('/')
        if (endpoint.isBlank()) return
        val installationId = prefs.getString("installation_id", null) ?: UUID.randomUUID().toString().also {
            prefs.edit().putString("installation_id", it).apply()
        }
        val host = session?.server?.baseUrl?.let(::safeHost).orEmpty()
        val type = if (session?.server?.id == "m3u") "M3U" else "XTREAM"
        val json = JSONObject()
            .put("installationId", installationId)
            .put("host", host)
            .put("playlistType", type)
            .put("appVersion", BuildConfig.VERSION_NAME)
            .put("androidVersion", Build.VERSION.RELEASE ?: "")
            .put("device", Build.MODEL ?: "")
        val req = Request.Builder().url("$endpoint/v1/heartbeat")
            .post(json.toString().toRequestBody("application/json".toMediaType())).build()
        runCatching { client.newCall(req).execute().close() }
    }

    private fun safeHost(raw: String): String = runCatching {
        val value = if (raw.startsWith("http://") || raw.startsWith("https://")) raw else "http://$raw"
        val uri = URI(value)
        buildString { append(uri.host.orEmpty()); if (uri.port > 0) append(":${uri.port}") }
    }.getOrDefault("")
}
