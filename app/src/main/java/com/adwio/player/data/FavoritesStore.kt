package com.adwio.player.data

import android.content.Context

class FavoritesStore(context: Context) {
    private val prefs = context.getSharedPreferences("adwio_favorites", Context.MODE_PRIVATE)
    fun isFavorite(id: String): Boolean = prefs.getBoolean(id, false)
    fun toggle(id: String): Boolean {
        val next = !isFavorite(id)
        prefs.edit().putBoolean(id, next).apply()
        return next
    }
}
