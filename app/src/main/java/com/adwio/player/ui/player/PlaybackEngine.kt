package com.adwio.player.ui.player

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import com.adwio.player.data.AppSettings

@UnstableApi
object PlaybackEngine {
    var player: ExoPlayer? = null
        private set

    var currentUrl: String = ""
        private set

    fun obtain(context: Context, settings: AppSettings): ExoPlayer {
        player?.let { return it }

        val (minBuffer, maxBuffer, startBuffer, rebuffer) = when (settings.bufferMode) {
            "small" -> intArrayOf(5000, 15000, 1500, 2500)
            "large" -> intArrayOf(25000, 70000, 3500, 6000)
            else -> intArrayOf(15000, 35000, 2500, 4000)
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
        startPosition: Long = 0L
    ): ExoPlayer {
        val p = obtain(context, settings)
        if (currentUrl != url) {
            currentUrl = url
            p.setMediaItem(MediaItem.fromUri(url))
            p.prepare()
            if (startPosition > 10_000L) p.seekTo(startPosition)
        }
        p.playWhenReady = true
        return p
    }

    fun stopAndRelease() {
        player?.release()
        player = null
        currentUrl = ""
    }
}
