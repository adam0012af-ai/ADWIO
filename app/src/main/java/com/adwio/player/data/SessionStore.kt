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
            .putString("display_name", session.displayName)
            .putString("created_at", session.createdAt)
            .putString("active_cons", session.activeConnections)
            .putString("max_cons", session.maxConnections)
            .apply()
    }

    fun load(): Session? {
        val username = prefs.getString("username", null) ?: return null
        val password = prefs.getString("password", null) ?: return null
        val url = prefs.getString("server_url", null) ?: return null

        return Session(
            username = username,
            password = password,
            server = ServerHost(
                prefs.getString("server_id", "cached") ?: "cached",
                prefs.getString("server_name", "Server") ?: "Server",
                url
            ),
            expiresAt = prefs.getString("expires", null),
            status = prefs.getString("status", null),
            displayName = prefs.getString("display_name", "") ?: "",
            createdAt = prefs.getString("created_at", null),
            activeConnections = prefs.getString("active_cons", null),
            maxConnections = prefs.getString("max_cons", null)
        )
    }

    fun clear() = prefs.edit().clear().apply()
}
