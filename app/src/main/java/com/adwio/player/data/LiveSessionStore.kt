package com.adwio.player.data

import android.content.Context
import androidx.media3.ui.AspectRatioFrameLayout

class LiveSessionStore(context: Context) {
    companion object {
        const val MODE_MINI = "MINI"
        const val MODE_FULLSCREEN = "FULLSCREEN"
    }

    data class State(
        val active: Boolean,
        val mediaId: String,
        val title: String,
        val url: String,
        val categoryId: String,
        val scrollPosition: Int,
        val mode: String,
        val resizeMode: Int,
        val updatedAt: Long
    )

    private val prefs =
        context.applicationContext.getSharedPreferences("adwio_live_session", Context.MODE_PRIVATE)

    fun load(): State = State(
        active = prefs.getBoolean("active", false),
        mediaId = prefs.getString("media_id", "").orEmpty(),
        title = prefs.getString("title", "").orEmpty(),
        url = prefs.getString("url", "").orEmpty(),
        categoryId = prefs.getString("category_id", "").orEmpty(),
        scrollPosition = prefs.getInt("scroll_position", 0),
        mode = prefs.getString("mode", MODE_MINI).orEmpty().ifBlank { MODE_MINI },
        resizeMode = prefs.getInt("resize_mode", AspectRatioFrameLayout.RESIZE_MODE_FILL),
        updatedAt = prefs.getLong("updated_at", 0L)
    )

    fun save(
        active: Boolean,
        mediaId: String,
        title: String,
        url: String,
        categoryId: String,
        scrollPosition: Int,
        mode: String,
        resizeMode: Int
    ) {
        prefs.edit()
            .putBoolean("active", active)
            .putString("media_id", mediaId)
            .putString("title", title)
            .putString("url", url)
            .putString("category_id", categoryId)
            .putInt("scroll_position", scrollPosition.coerceAtLeast(0))
            .putString("mode", mode)
            .putInt("resize_mode", resizeMode)
            .putLong("updated_at", System.currentTimeMillis())
            .commit()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }
}
