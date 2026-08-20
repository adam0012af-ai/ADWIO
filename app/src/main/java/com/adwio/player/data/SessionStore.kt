package com.adwio.player.data

import android.content.Context
import com.adwio.player.data.model.ServerHost
import com.adwio.player.data.model.Session

class SessionStore(context: Context) {
    private val prefs = context.getSharedPreferences("adwio_session", Context.MODE_PRIVATE)
    private val m3uPrefs = context.getSharedPreferences("adwio_m3u", Context.MODE_PRIVATE)

    fun save(session: Session) {
        val editor = prefs.edit()
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

        /*
         * M3U profiles do not have username/password fields in the Session model.
         * Keep a dedicated active URL as a migration/fallback source so older
         * profiles can never become an "empty session" after an app update.
         */
        if (session.server.id == "m3u" && session.server.baseUrl.isNotBlank()) {
            m3uPrefs.edit()
                .putString("active_url", session.server.baseUrl.trim())
                .apply()
        }

        editor.apply()
    }

    fun load(): Session? {
        val serverId = prefs.getString("server_id", null)
            ?: if (!m3uPrefs.getString("active_url", null).isNullOrBlank()) "m3u" else null
            ?: return null

        val isM3u = serverId.equals("m3u", ignoreCase = true)

        val storedUrl = prefs.getString("server_url", null).orEmpty().trim()
        val fallbackM3uUrl = m3uPrefs.getString("active_url", null).orEmpty().trim()
        val url = if (storedUrl.isNotBlank()) storedUrl else if (isM3u) fallbackM3uUrl else ""

        if (url.isBlank()) return null

        /*
         * Do NOT require username/password for M3U.
         * Previous code returned null when either key was missing, which made
         * LibraryActivity immediately finish for migrated M3U sessions.
         */
        val username = prefs.getString("username", "").orEmpty()
        val password = prefs.getString("password", "").orEmpty()

        return Session(
            username = username,
            password = password,
            server = ServerHost(
                id = serverId,
                name = prefs.getString(
                    "server_name",
                    if (isM3u) "M3U" else "Server"
                ) ?: if (isM3u) "M3U" else "Server",
                baseUrl = url
            ),
            expiresAt = prefs.getString("expires", null),
            status = prefs.getString("status", null)
                ?: if (isM3u) "Active" else null,
            displayName = prefs.getString("display_name", "") ?: "",
            createdAt = prefs.getString("created_at", null),
            activeConnections = prefs.getString("active_cons", null),
            maxConnections = prefs.getString("max_cons", null)
        )
    }

    fun clear() {
        prefs.edit().clear().apply()
        // active_url intentionally follows the active session and must not
        // resurrect a session after explicit logout/clear.
        m3uPrefs.edit().remove("active_url").apply()
    }
}
