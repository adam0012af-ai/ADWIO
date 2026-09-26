package com.adwio.player.data

import com.adwio.player.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class ManagedContentBootstrap(
    val configured: Boolean,
    val enabled: Boolean,
    val playlistUrl: String,
    val authEnabled: Boolean,
    val adsEnabled: Boolean
)

class RemoteContentClient {
    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    fun bootstrap(): ManagedContentBootstrap {
        val base = BuildConfig.CONTROL_API_URL.trim().trimEnd('/')
        require(base.isNotBlank()) { "Content service is not configured" }

        val request = Request.Builder()
            .url("$base/api/v1/content/bootstrap")
            .header("User-Agent", "ADWIO-Player/${BuildConfig.VERSION_NAME}")
            .header("Accept", "application/json")
            .get()
            .build()

        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("Content service returned HTTP ${response.code}")
            }

            val json = JSONObject(response.body?.string().orEmpty())
            ManagedContentBootstrap(
                configured = json.optBoolean("configured", false),
                enabled = json.optBoolean("enabled", false),
                playlistUrl = json.optString("playlist_url", "").trim(),
                authEnabled = json.optBoolean("auth_enabled", false),
                adsEnabled = json.optBoolean("ads_enabled", false)
            )
        }
    }
}
