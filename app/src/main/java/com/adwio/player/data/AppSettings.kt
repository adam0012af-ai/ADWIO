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
    var backgroundPlayback: Boolean
        get() = false
        set(_) = prefs.edit().putBoolean("background_playback", false).apply()
    var pictureInPicture: Boolean
        get() = prefs.getBoolean("pip", true)
        set(v) = prefs.edit().putBoolean("pip", v).apply()
    var autoNextEpisode: Boolean
        get() = prefs.getBoolean("auto_next_episode", true)
        set(v) = prefs.edit().putBoolean("auto_next_episode", v).apply()
    var language: String
        get() = prefs.getString("language", "system") ?: "system"
        set(v) = prefs.edit().putString("language", v).apply()
    var startupScreen: String
        get() = prefs.getString("startup_screen", "home") ?: "home"
        set(v) = prefs.edit().putString("startup_screen", v).apply()
    var gridDensity: String
        get() = prefs.getString("grid_density", "auto") ?: "auto"
        set(v) = prefs.edit().putString("grid_density", v).apply()
    var bufferMode: String
        get() = prefs.getString("buffer_mode", "normal") ?: "normal"
        set(v) = prefs.edit().putString("buffer_mode", v).apply()
    var aspectMode: String
        get() = prefs.getString("aspect_mode", "fit") ?: "fit"
        set(v) = prefs.edit().putString("aspect_mode", v).apply()
    var streamFormat: String
        get() = prefs.getString("stream_format", "auto") ?: "auto"
        set(v) = prefs.edit().putString("stream_format", v).apply()
    var decoderMode: String
        get() = prefs.getString("decoder_mode", "auto") ?: "auto"
        set(v) = prefs.edit().putString("decoder_mode", v).apply()
    var playerControlsTimeoutMs: Int
        get() = prefs.getInt("controls_timeout", 3200)
        set(v) = prefs.edit().putInt("controls_timeout", v).apply()
    var parentalPin: String?
        get() = prefs.getString("parental_pin", null)
        set(v) = prefs.edit().putString("parental_pin", v).apply()
    var timeFormat: String
        get() = prefs.getString("time_format", "system") ?: "system"
        set(v) = prefs.edit().putString("time_format", v).apply()
    var playerEngine: String
        get() = prefs.getString("player_engine", "internal") ?: "internal"
        set(v) = prefs.edit().putString("player_engine", v).apply()
    var externalPlayerEnabled: Boolean
        get() = prefs.getBoolean("external_player", false)
        set(v) = prefs.edit().putBoolean("external_player", v).apply()
    var multiScreenEnabled: Boolean
        get() = prefs.getBoolean("multi_screen", false)
        set(v) = prefs.edit().putBoolean("multi_screen", v).apply()

    fun reset() = prefs.edit().clear().apply()
}
