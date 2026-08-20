package com.adwio.player.ui.player

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import com.adwio.player.data.AppSettings
import com.adwio.player.data.model.MediaType

@UnstableApi
object PlaybackEngine {
    var player: ExoPlayer? = null
        private set

    var currentUrl: String = ""
        private set
    var currentTitle: String = ""
        private set
    var currentId: String = ""
        private set
    var currentType: MediaType? = null
        private set

    fun obtain(context: Context, settings: AppSettings): ExoPlayer {
        player?.let { return it }

        val (minBuffer, maxBuffer, startBuffer, rebuffer) = when (settings.bufferMode) {
            "small" -> intArrayOf(3500, 12000, 1000, 1800)
            "large" -> intArrayOf(18000, 55000, 2500, 4500)
            else -> intArrayOf(8000, 28000, 1500, 2800)
        }

        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(minBuffer, maxBuffer, startBuffer, rebuffer)
            .build()

        return ExoPlayer.Builder(context.applicationContext)
            .setLoadControl(loadControl)
            .build()
            .also { player = it }
    }

    fun play(
        context: Context,
        settings: AppSettings,
        url: String,
        title: String = "",
        id: String = "",
        type: MediaType? = null,
        startPosition: Long = 0L
    ): ExoPlayer {
        val p = obtain(context, settings)

        currentTitle = title
        currentId = id
        currentType = type

        if (currentUrl != url) {
            currentUrl = url
            p.setMediaItem(MediaItem.fromUri(url))
            p.prepare()
            if (startPosition > 10_000L) p.seekTo(startPosition)
        }
        p.playWhenReady = true
        p.volume = 1f

        return p
    }

    fun updateMetadata(title: String, id: String, type: MediaType?) {
        currentTitle = title
        currentId = id
        currentType = type
    }

    fun stopAndRelease() {
        player?.stop()
        player?.clearMediaItems()
        player?.release()
        player = null
        currentUrl = ""
        currentTitle = ""
        currentId = ""
        currentType = null
    }
}
