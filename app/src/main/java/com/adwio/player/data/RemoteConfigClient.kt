package com.adwio.player.data

import android.app.Activity
import androidx.appcompat.app.AlertDialog
import com.adwio.player.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** Optional remote operational configuration. Does nothing when no control URL is configured. */
class RemoteConfigClient(private val activity: Activity) {
    private val client = OkHttpClient.Builder().callTimeout(5, TimeUnit.SECONDS).build()

    fun check() {
        val base = BuildConfig.CONTROL_API_URL.trim().trimEnd('/')
        if (base.isBlank()) return
        Thread {
            val json = runCatching {
                client.newCall(Request.Builder().url("$base/v1/config").get().build()).execute().use { response ->
                    if (!response.isSuccessful) return@use null
                    response.body?.string()?.let(::JSONObject)
                }
            }.getOrNull() ?: return@Thread

            val maintenance = json.optBoolean("maintenance", false)
            val minVersion = json.optInt("minVersionCode", 0)
            val message = json.optString("message", "").trim()
            activity.runOnUiThread {
                if (activity.isFinishing) return@runOnUiThread
                when {
                    maintenance -> AlertDialog.Builder(activity)
                        .setTitle("ADWIO")
                        .setMessage(if (message.isBlank()) "The service is temporarily under maintenance." else message)
                        .setCancelable(false)
                        .setPositiveButton(android.R.string.ok, null)
                        .show()
                    minVersion > BuildConfig.VERSION_CODE -> AlertDialog.Builder(activity)
                        .setTitle("Update available")
                        .setMessage(if (message.isBlank()) "A newer ADWIO version is required." else message)
                        .setPositiveButton(android.R.string.ok, null)
                        .show()
                    message.isNotBlank() -> {
                        val prefs = activity.getSharedPreferences("adwio_remote", Activity.MODE_PRIVATE)
                        val key = message.hashCode().toString()
                        if (prefs.getString("last_message", "") != key) {
                            prefs.edit().putString("last_message", key).apply()
                            AlertDialog.Builder(activity).setTitle("ADWIO").setMessage(message).setPositiveButton(android.R.string.ok, null).show()
                        }
                    }
                }
            }
        }.start()
    }
}
