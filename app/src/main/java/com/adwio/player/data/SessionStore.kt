package com.adwio.player.data

import android.content.Context
import com.adwio.player.data.model.ServerHost
import com.adwio.player.data.model.Session

class SessionStore(context: Context) {
    private val prefs = context.getSharedPreferences("adwio_session", Context.MODE_PRIVATE)
    fun save(session: Session) {
        prefs.edit()
            .putString("username", session.username)
            .putString("password", session.password)
            .putString("server_id", session.server.id)
            .putString("server_name", session.server.name)
            .putString("server_url", session.server.baseUrl)
            .putString("expires", session.expiresAt)
            .putString("status", session.status)
            .apply()
    }
    fun load(): Session? {
        val u = prefs.getString("username", null) ?: return null
        val p = prefs.getString("password", null) ?: return null
        val url = prefs.getString("server_url", null) ?: return null
        return Session(
            u, p,
            ServerHost(
                prefs.getString("server_id", "cached") ?: "cached",
                prefs.getString("server_name", "Server") ?: "Server",
                url
            ),
            prefs.getString("expires", null),
            prefs.getString("status", null)
        )
    }
    fun clear() = prefs.edit().clear().apply()
}
