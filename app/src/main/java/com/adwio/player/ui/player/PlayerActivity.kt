package com.adwio.player.ui.player

import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import com.adwio.player.data.AppSettings
import com.adwio.player.data.PlaybackHistory
import com.adwio.player.databinding.ActivityPlayerBinding
import com.adwio.player.ui.BaseFullscreenActivity

@UnstableApi
class PlayerActivity : BaseFullscreenActivity() {
    private lateinit var b: ActivityPlayerBinding
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

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                savePosition()
                b.playerView.player = null
                PlaybackEngine.stopAndRelease()
                finish()
            }
        })
    }

    override fun onStart() {
        super.onStart()
        if (url.isBlank()) {
            finish()
            return
        }

        val savedPosition =
            if (settings.rememberPosition && mediaId.isNotBlank()) {
                history.positionFor(mediaId)
            } else 0L

        val p = PlaybackEngine.play(this, settings, url, savedPosition)
        b.playerView.player = p
        b.playerView.controllerShowTimeoutMs = settings.playerControlsTimeoutMs
        b.playerView.resizeMode = when (settings.aspectMode) {
            "fill" -> AspectRatioFrameLayout.RESIZE_MODE_FILL
            "zoom" -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
        }
    }

    override fun onStop() {
        savePosition()
        b.playerView.player = null
        // Do not release here: playback continues when the app is minimized/backgrounded.
        super.onStop()
    }

    override fun onDestroy() {
        b.playerView.player = null
        super.onDestroy()
    }

    private fun savePosition() {
        PlaybackEngine.player?.let {
            if (mediaId.isNotBlank()) {
                history.saveLast(mediaId, title, url, it.currentPosition)
            }
        }
    }
}
