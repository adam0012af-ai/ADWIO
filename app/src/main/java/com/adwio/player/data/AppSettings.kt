package com.adwio.player.data

import android.content.Context

class AppSettings(context: Context) {
    private val prefs = context.getSharedPreferences("adwio_settings", Context.MODE_PRIVATE)

    var autoplayLastChannel: Boolean
        get() = prefs.getBoolean("autoplay_last", false)
        set(v) = prefs.edit().putBoolean("autoplay_last", v).apply()

    var rememberPosition: Boolean
        get() = prefs.getBoolean("remember_position", true)
        set(v) = prefs.edit().putBoolean("remember_position", v).apply()

    var autoRefresh: Boolean
        get() = prefs.getBoolean("auto_refresh", true)
        set(v) = prefs.edit().putBoolean("auto_refresh", v).apply()

    var bufferMode: String
        get() = prefs.getString("buffer_mode", "normal") ?: "normal"
        set(v) = prefs.edit().putString("buffer_mode", v).apply()

    var aspectMode: String
        get() = prefs.getString("aspect_mode", "fit") ?: "fit"
        set(v) = prefs.edit().putString("aspect_mode", v).apply()

    var playerControlsTimeoutMs: Int
        get() = prefs.getInt("controls_timeout", 4000)
        set(v) = prefs.edit().putInt("controls_timeout", v).apply()

    var parentalPin: String?
        get() = prefs.getString("parental_pin", null)
        set(v) = prefs.edit().putString("parental_pin", v).apply()

    fun reset() = prefs.edit().clear().apply()
}
