package com.adwio.player.data

import android.content.Context
import com.adwio.player.data.model.MediaType
import java.security.MessageDigest

data class WatchEntry(
    val id: String,
    val title: String,
    val url: String,
    val type: MediaType,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAt: Long
) {
    val progress: Float
        get() = if (durationMs > 0L) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
}

/** Multi-item history, isolated per playlist. Live channels are kept out of Continue Watching. */
class PlaybackHistory(private val context: Context) {
    private val prefs = context.getSharedPreferences("adwio_history_v2", Context.MODE_PRIVATE)

    fun save(
        id: String,
        title: String,
        url: String,
        type: MediaType,
        positionMs: Long,
        durationMs: Long
    ) {
        if (id.isBlank() || url.isBlank()) return
        val scoped = scopedId(id, type)
        val prefix = "watch:$scoped:"
        val editor = prefs.edit()
            .putString(prefix + "id", id)
            .putString(prefix + "title", title)
            .putString(prefix + "url", url)
            .putString(prefix + "type", type.name)
            .putLong(prefix + "position", positionMs.coerceAtLeast(0L))
            .putLong(prefix + "duration", durationMs.coerceAtLeast(0L))
            .putLong(prefix + "time", System.currentTimeMillis())

        val indexKey = indexKey()
        val index = prefs.getStringSet(indexKey, emptySet()).orEmpty().toMutableSet()
        index += scoped
        editor.putStringSet(indexKey, index)

        // If the item is effectively finished, keep resume at zero and remove from Continue Watching.
        if (durationMs > 60_000L && positionMs >= durationMs * 0.93) {
            index -= scoped
            editor.putStringSet(indexKey, index)
            listOf("id", "title", "url", "type", "position", "duration", "time").forEach {
                editor.remove(prefix + it)
            }
        }
        editor.apply()
    }

    fun positionFor(id: String, type: MediaType): Long =
        prefs.getLong("watch:${scopedId(id, type)}:position", 0L)

    fun continueWatching(limit: Int = 40): List<WatchEntry> =
        prefs.getStringSet(indexKey(), emptySet()).orEmpty()
            .mapNotNull(::read)
            .filter { it.type != MediaType.LIVE && it.positionMs > 10_000L }
            .sortedByDescending { it.updatedAt }
            .take(limit)

    fun remove(id: String, type: MediaType) {
        val scoped = scopedId(id, type)
        val prefix = "watch:$scoped:"
        val index = prefs.getStringSet(indexKey(), emptySet()).orEmpty().toMutableSet().apply { remove(scoped) }
        val editor = prefs.edit().putStringSet(indexKey(), index)
        listOf("id", "title", "url", "type", "position", "duration", "time").forEach {
            editor.remove(prefix + it)
        }
        editor.apply()
    }

    fun clearCurrentPlaylist() {
        val scope = playlistScope()
        val editor = prefs.edit()
        prefs.all.keys.filter { it.contains(":$scope:") || it == "watch_index:$scope" }.forEach(editor::remove)
        editor.apply()
    }

    private fun read(scoped: String): WatchEntry? {
        val prefix = "watch:$scoped:"
        val id = prefs.getString(prefix + "id", null) ?: return null
        val url = prefs.getString(prefix + "url", null) ?: return null
        val type = runCatching { MediaType.valueOf(prefs.getString(prefix + "type", "MOVIE")!!) }.getOrDefault(MediaType.MOVIE)
        return WatchEntry(
            id = id,
            title = prefs.getString(prefix + "title", "") ?: "",
            url = url,
            type = type,
            positionMs = prefs.getLong(prefix + "position", 0L),
            durationMs = prefs.getLong(prefix + "duration", 0L),
            updatedAt = prefs.getLong(prefix + "time", 0L)
        )
    }

    private fun indexKey() = "watch_index:${playlistScope()}"
    private fun scopedId(id: String, type: MediaType) = "${playlistScope()}:${type.name}:$id"

    private fun playlistScope(): String {
        val session = SessionStore(context).load() ?: return "guest"
        val raw = "${session.server.id}|${session.server.baseUrl}|${session.username}"
        val bytes = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray())
        return bytes.take(8).joinToString("") { "%02x".format(it) }
    }
}
