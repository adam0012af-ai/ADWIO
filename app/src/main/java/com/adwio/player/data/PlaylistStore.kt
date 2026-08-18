package com.adwio.player.data

import android.content.Context
import com.adwio.player.data.model.PlaylistProfile
import com.adwio.player.data.model.ServerHost
import com.adwio.player.data.model.Session
import java.util.UUID

class PlaylistStore(context: Context) {
    private val prefs = context.getSharedPreferences("adwio_playlists", Context.MODE_PRIVATE)

    fun list(): List<PlaylistProfile> {
        val raw = prefs.getStringSet("profiles", emptySet()).orEmpty()
        return raw.mapNotNull(::decode).sortedByDescending { it.lastUsedAt }
    }

    fun add(name: String, session: Session): PlaylistProfile {
        val profile = PlaylistProfile(
            id = UUID.randomUUID().toString(),
            name = name.ifBlank { "Playlist ${list().size + 1}" },
            username = session.username,
            password = session.password,
            serverId = session.server.id,
            serverName = session.server.name,
            serverUrl = session.server.baseUrl
        )
        put(profile)
        return profile
    }

    fun put(profile: PlaylistProfile) {
        val current = list().filterNot { it.id == profile.id }.toMutableList()
        current += profile.copy(lastUsedAt = System.currentTimeMillis())
        prefs.edit().putStringSet("profiles", current.map(::encode).toSet()).apply()
    }

    fun remove(id: String) {
        prefs.edit().putStringSet("profiles", list().filterNot { it.id == id }.map(::encode).toSet()).apply()
    }

    fun clear() = prefs.edit().clear().apply()

    fun toSession(profile: PlaylistProfile) = Session(
        username = profile.username,
        password = profile.password,
        server = ServerHost(profile.serverId, profile.serverName, profile.serverUrl)
    )

    private fun encode(p: PlaylistProfile): String = listOf(
        p.id, p.name, p.username, p.password, p.serverId, p.serverName, p.serverUrl, p.lastUsedAt.toString()
    ).joinToString("\u001F") { it.replace("\u001F", "") }

    private fun decode(raw: String): PlaylistProfile? {
        val x = raw.split("\u001F")
        if (x.size < 8) return null
        return PlaylistProfile(x[0], x[1], x[2], x[3], x[4], x[5], x[6], x[7].toLongOrNull() ?: 0L)
    }
}
