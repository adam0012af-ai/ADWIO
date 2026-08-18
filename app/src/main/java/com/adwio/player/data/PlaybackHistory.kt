package com.adwio.player.data

import android.content.Context

class PlaybackHistory(context: Context) {
    private val prefs = context.getSharedPreferences("adwio_history", Context.MODE_PRIVATE)

    fun saveLast(id: String, title: String, url: String, positionMs: Long) {
        prefs.edit()
            .putString("last_id", id)
            .putString("last_title", title)
            .putString("last_url", url)
            .putLong("last_position", positionMs)
            .putLong("last_time", System.currentTimeMillis())
            .apply()
    }

    fun positionFor(id: String): Long =
        if (prefs.getString("last_id", null) == id) prefs.getLong("last_position", 0L) else 0L

    fun clear() = prefs.edit().clear().apply()
}
