package com.adwio.player.ui.player

import android.os.Bundle
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import com.adwio.player.data.AppSettings
import com.adwio.player.data.PlaybackHistory
import com.adwio.player.databinding.ActivityPlayerBinding
import com.adwio.player.ui.BaseFullscreenActivity

@UnstableApi
class PlayerActivity : BaseFullscreenActivity() {
    private lateinit var b: ActivityPlayerBinding
    private var player: ExoPlayer? = null
    private lateinit var settings: AppSettings
    private lateinit var history: PlaybackHistory
    private var mediaId: String = ""
    private var title: String = ""
    private var url: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityPlayerBinding.inflate(layoutInflater)
        setContentView(b.root)
        settings = AppSettings(this)
        history = PlaybackHistory(this)

        mediaId = intent.getStringExtra("id").orEmpty()
        title = intent.getStringExtra("title").orEmpty()
        url = intent.getStringExtra("url").orEmpty()
        b.channelTitle.text = title
    }

    override fun onStart() {
        super.onStart()
        if (url.isBlank()) return

        val (minBuffer, maxBuffer, startBuffer, rebuffer) = when (settings.bufferMode) {
            "small" -> intArrayOf(5000, 15000, 1500, 2500)
            "large" -> intArrayOf(25000, 70000, 3500, 6000)
            else -> intArrayOf(15000, 35000, 2500, 4000)
        }

        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(minBuffer, maxBuffer, startBuffer, rebuffer)
            .build()

        player = ExoPlayer.Builder(this)
            .setLoadControl(loadControl)
            .build()
            .also {
                b.playerView.player = it
                b.playerView.controllerShowTimeoutMs = settings.playerControlsTimeoutMs
                b.playerView.resizeMode = when (settings.aspectMode) {
                    "fill" -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                    "zoom" -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                }

                it.setMediaItem(MediaItem.fromUri(url))
                it.prepare()

                if (settings.rememberPosition && mediaId.isNotBlank()) {
                    val pos = history.positionFor(mediaId)
                    if (pos > 10_000L) it.seekTo(pos)
                }

                it.playWhenReady = true
            }
    }

    override fun onStop() {
        player?.let {
            if (mediaId.isNotBlank()) history.saveLast(mediaId, title, url, it.currentPosition)
        }
        b.playerView.player = null
        player?.release()
        player = null
        super.onStop()
    }
}
