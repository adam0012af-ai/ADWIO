package com.adwio.player.ui.sports

import android.content.Context

class SportsFavorites(context: Context) {
    private val prefs = context.getSharedPreferences("adwio_sports_favorites", Context.MODE_PRIVATE)

    fun isFavorite(matchId: Long): Boolean = prefs.getBoolean(matchId.toString(), false)

    fun toggle(matchId: Long): Boolean {
        val now = !isFavorite(matchId)
        prefs.edit().putBoolean(matchId.toString(), now).apply()
        return now
    }
}
