package com.adwio.player.data

import android.content.Context
import com.adwio.player.data.model.MediaType
import java.security.MessageDigest

/** Favorites are isolated per active playlist and media type. */
class FavoritesStore(private val context: Context) {
    private val prefs = context.getSharedPreferences("adwio_favorites_v2", Context.MODE_PRIVATE)

    fun isFavorite(id: String, type: MediaType? = null): Boolean =
        prefs.getBoolean(key(id, type), false)

    fun toggle(id: String, type: MediaType? = null): Boolean {
        val k = key(id, type)
        val next = !prefs.getBoolean(k, false)
        prefs.edit().putBoolean(k, next).apply()
        return next
    }

    fun clearCurrentPlaylist() {
        val prefix = "fav:${playlistScope()}:"
        val editor = prefs.edit()
        prefs.all.keys.filter { it.startsWith(prefix) }.forEach(editor::remove)
        editor.apply()
    }

    private fun key(id: String, type: MediaType?): String =
        "fav:${playlistScope()}:${type?.name ?: "ANY"}:$id"

    private fun playlistScope(): String {
        val session = SessionStore(context).load() ?: return "guest"
        val raw = "${session.server.id}|${session.server.baseUrl}|${session.username}"
        val bytes = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray())
        return bytes.take(8).joinToString("") { "%02x".format(it) }
    }
}
