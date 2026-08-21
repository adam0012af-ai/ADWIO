package com.adwio.player.data

import android.content.Context
import com.adwio.player.data.model.MediaType

class AppResumeState(context: Context) {
    companion object {
        const val SCREEN_HOME = "HOME"
        const val SCREEN_LIBRARY = "LIBRARY"
        const val SCREEN_PLAYER = "PLAYER"

        const val MODE_NONE = "NONE"
        const val MODE_MINI = "MINI"
        const val MODE_FULLSCREEN = "FULLSCREEN"
    }

    data class State(
        val screen: String,
        val mediaType: MediaType,
        val categoryId: String,
        val scrollPosition: Int,
        val playbackMode: String,
        val playbackActive: Boolean,
        val mediaId: String,
        val title: String,
        val url: String
    )

    private val prefs = context.getSharedPreferences("adwio_resume_state", Context.MODE_PRIVATE)

    fun saveHome() {
        prefs.edit()
            .putString("screen", SCREEN_HOME)
            .putString("playback_mode", MODE_NONE)
            .putBoolean("playback_active", false)
            .apply()
    }

    fun saveLibrary(
        type: MediaType,
        categoryId: String,
        scrollPosition: Int,
        playbackActive: Boolean = false,
        playbackMode: String = if (playbackActive) MODE_MINI else MODE_NONE,
        mediaId: String = "",
        title: String = "",
        url: String = ""
    ) {
        prefs.edit()
            .putString("screen", SCREEN_LIBRARY)
            .putString("media_type", type.name)
            .putString("category_id", categoryId)
            .putInt("scroll_position", scrollPosition.coerceAtLeast(0))
            .putString("playback_mode", if (playbackActive) playbackMode else MODE_NONE)
            .putBoolean("playback_active", playbackActive)
            .putString("media_id", mediaId)
            .putString("title", title)
            .putString("url", url)
            .apply()
    }

    fun savePlayer(
        type: MediaType,
        mediaId: String,
        title: String,
        url: String
    ) {
        prefs.edit()
            .putString("screen", SCREEN_PLAYER)
            .putString("media_type", type.name)
            .putString("playback_mode", MODE_FULLSCREEN)
            .putBoolean("playback_active", true)
            .putString("media_id", mediaId)
            .putString("title", title)
            .putString("url", url)
            .apply()
    }

    fun clearPlaybackKeepingLibrary() {
        prefs.edit()
            .putString("screen", SCREEN_LIBRARY)
            .putString("playback_mode", MODE_NONE)
            .putBoolean("playback_active", false)
            .putString("media_id", "")
            .putString("title", "")
            .putString("url", "")
            .apply()
    }

    fun clearPlayback() {
        prefs.edit()
            .putString("playback_mode", MODE_NONE)
            .putBoolean("playback_active", false)
            .putString("media_id", "")
            .putString("title", "")
            .putString("url", "")
            .apply()
    }

    fun load(): State {
        val type = runCatching {
            MediaType.valueOf(prefs.getString("media_type", MediaType.LIVE.name).orEmpty())
        }.getOrDefault(MediaType.LIVE)

        return State(
            screen = prefs.getString("screen", SCREEN_HOME).orEmpty().ifBlank { SCREEN_HOME },
            mediaType = type,
            categoryId = prefs.getString("category_id", "").orEmpty(),
            scrollPosition = prefs.getInt("scroll_position", 0),
            playbackMode = prefs.getString("playback_mode", MODE_NONE).orEmpty(),
            playbackActive = prefs.getBoolean("playback_active", false),
            mediaId = prefs.getString("media_id", "").orEmpty(),
            title = prefs.getString("title", "").orEmpty(),
            url = prefs.getString("url", "").orEmpty()
        )
    }
}
