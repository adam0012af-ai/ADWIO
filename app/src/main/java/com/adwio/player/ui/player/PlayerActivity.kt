package com.adwio.player.ui.player

import android.app.PictureInPictureParams
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Rational
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import com.adwio.player.data.AppSettings
import com.adwio.player.data.PlaybackHistory
import com.adwio.player.data.RecentChannelsStore
import com.adwio.player.data.model.MediaType
import com.adwio.player.databinding.ActivityPlayerBinding
import com.adwio.player.ui.BaseFullscreenActivity

@UnstableApi
class PlayerActivity : BaseFullscreenActivity() {
    private lateinit var b: ActivityPlayerBinding
    private lateinit var settings: AppSettings
    private lateinit var history: PlaybackHistory
    private val handler = Handler(Looper.getMainLooper())
    private var mediaId = ""
    private var title = ""
    private var url = ""
    private var type = MediaType.MOVIE
    private var retryCount = 0
    private var inPip = false
    private var nextUrl = ""
    private var nextTitle = ""
    private var nextId = ""
    private lateinit var recentChannels: RecentChannelsStore

    private val progressSaver = object : Runnable {
        override fun run() {
            savePosition()
            handler.postDelayed(this, 10_000L)
        }
    }

    private val playerListener = object : Player.Listener {
        override fun onPlayerError(error: PlaybackException) {
            if (retryCount < 3) {
                retryCount++
                b.statusText.visibility = View.VISIBLE
                b.statusText.text = "Reconnecting… $retryCount/3"
                handler.postDelayed({ retryPlayback() }, 1200L * retryCount)
            } else {
                b.statusText.visibility = View.VISIBLE
                b.statusText.text = "Playback unavailable • press OK to retry"
                b.statusText.isFocusable = true
                b.statusText.requestFocus()
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_READY) {
                retryCount = 0
                b.statusText.visibility = View.GONE
            } else if (playbackState == Player.STATE_ENDED && type == MediaType.SERIES && settings.autoNextEpisode) {
                playNextEpisodeIfAvailable()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityPlayerBinding.inflate(layoutInflater)
        setContentView(b.root)

        settings = AppSettings(this)
        history = PlaybackHistory(this)
        recentChannels = RecentChannelsStore(this)
        mediaId = intent.getStringExtra("id").orEmpty()
        title = intent.getStringExtra("title").orEmpty()
        url = intent.getStringExtra("url").orEmpty()
        type = detectType(intent.getStringExtra("type"), mediaId)
        nextUrl = intent.getStringExtra("next_url").orEmpty()
        nextTitle = intent.getStringExtra("next_title").orEmpty()
        nextId = intent.getStringExtra("next_id").orEmpty()
        b.channelTitle.text = title
        b.liveZappingControls.visibility = if (type == MediaType.LIVE) View.VISIBLE else View.GONE
        b.previousChannelButton.setOnClickListener { switchLiveChannel(ChannelNavigator.previous()) }
        b.nextChannelButton.setOnClickListener { switchLiveChannel(ChannelNavigator.next()) }

        b.statusText.setOnClickListener {
            retryCount = 0
            retryPlayback()
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                savePosition()
                if (settings.backgroundPlayback && PlaybackEngine.player?.isPlaying == true) {
                    maybeEnterPip()
                    if (!inPip) finish()
                } else {
                    PlaybackEngine.stopAndRelease()
                    finish()
                }
            }
        })
    }

    override fun onStart() {
        super.onStart()
        if (url.isBlank()) return finish()
        attachAndPlay()
        handler.removeCallbacks(progressSaver)
        handler.postDelayed(progressSaver, 10_000L)
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (settings.pictureInPicture && PlaybackEngine.player?.isPlaying == true) maybeEnterPip()
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        inPip = isInPictureInPictureMode
        b.channelTitle.visibility = if (isInPictureInPictureMode) View.GONE else View.VISIBLE
        b.playerView.useController = !isInPictureInPictureMode
    }

    override fun onStop() {
        handler.removeCallbacks(progressSaver)
        savePosition()
        PlaybackEngine.player?.removeListener(playerListener)
        b.playerView.player = null
        if (!settings.backgroundPlayback && !inPip) PlaybackEngine.stopAndRelease()
        super.onStop()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        b.playerView.player = null
        super.onDestroy()
    }

    private fun attachAndPlay() {
        val saved = if (settings.rememberPosition && type != MediaType.LIVE && mediaId.isNotBlank()) {
            history.positionFor(mediaId, type)
        } else 0L

        if (settings.backgroundPlayback) {
            runCatching { startService(Intent(this, PlaybackService::class.java)) }
        }
        val player = PlaybackEngine.play(this, settings, url, saved)
        player.removeListener(playerListener)
        player.addListener(playerListener)
        b.playerView.player = player
        b.playerView.controllerShowTimeoutMs = settings.playerControlsTimeoutMs
        b.playerView.resizeMode = when (settings.aspectMode) {
            "fill" -> AspectRatioFrameLayout.RESIZE_MODE_FILL
            "zoom" -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
        }
    }

    private fun retryPlayback() {
        val player = PlaybackEngine.player ?: return attachAndPlay()
        b.statusText.visibility = View.VISIBLE
        b.statusText.text = "Reconnecting…"
        player.prepare()
        player.playWhenReady = true
    }

    private fun savePosition() {
        val player = PlaybackEngine.player ?: return
        if (mediaId.isBlank() || type == MediaType.LIVE) return
        history.save(mediaId, title, url, type, player.currentPosition, player.duration.coerceAtLeast(0L))
    }

    private fun maybeEnterPip() {
        if (!settings.pictureInPicture || Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        runCatching {
            val params = PictureInPictureParams.Builder()
                .setAspectRatio(Rational(16, 9))
                .build()
            inPip = enterPictureInPictureMode(params)
        }
    }

    private fun switchLiveChannel(item: com.adwio.player.data.model.MediaItemModel?) {
        if (type != MediaType.LIVE || item == null) return
        recentChannels.add(item)
        mediaId = "LIVE:${item.id}"
        title = item.name
        url = item.streamUrl
        b.channelTitle.text = title
        b.statusText.visibility = View.VISIBLE
        b.statusText.text = "Loading $title…"
        retryCount = 0
        PlaybackEngine.player?.let { player ->
            player.setMediaItem(androidx.media3.common.MediaItem.fromUri(url))
            player.prepare()
            player.playWhenReady = true
        } ?: attachAndPlay()
    }


    private fun playNextEpisodeIfAvailable() {
        if (nextUrl.isBlank()) return
        mediaId = nextId
        title = nextTitle.ifBlank { "Next episode" }
        url = nextUrl
        nextUrl = ""
        nextTitle = ""
        nextId = ""
        b.channelTitle.text = title
        b.statusText.visibility = View.VISIBLE
        b.statusText.text = "Loading next episode…"
        retryCount = 0
        val player = PlaybackEngine.player ?: return attachAndPlay()
        player.setMediaItem(androidx.media3.common.MediaItem.fromUri(url))
        player.prepare()
        player.playWhenReady = true
    }

    private fun detectType(explicit: String?, id: String): MediaType =
        runCatching { MediaType.valueOf(explicit.orEmpty()) }.getOrElse {
            when {
                id.startsWith("LIVE:", true) -> MediaType.LIVE
                id.startsWith("SERIES:", true) -> MediaType.SERIES
                else -> MediaType.MOVIE
            }
        }
}
