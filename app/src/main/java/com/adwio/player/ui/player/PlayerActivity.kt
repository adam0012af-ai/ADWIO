package com.adwio.player.ui.player

import android.app.PictureInPictureParams
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Rational
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.widget.SeekBar
import androidx.appcompat.app.AlertDialog
import androidx.activity.OnBackPressedCallback
import androidx.core.view.isVisible
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.recyclerview.widget.LinearLayoutManager
import com.adwio.player.data.AppSettings
import com.adwio.player.data.FavoritesStore
import com.adwio.player.data.PlaybackHistory
import com.adwio.player.data.RecentChannelsStore
import com.adwio.player.data.model.CategoryModel
import com.adwio.player.data.model.MediaItemModel
import com.adwio.player.data.model.MediaType
import com.adwio.player.databinding.ActivityPlayerBinding
import com.adwio.player.ui.BaseFullscreenActivity
import com.adwio.player.ui.home.CategoryAdapter
import com.adwio.player.ui.home.MediaAdapter

@UnstableApi
class PlayerActivity : BaseFullscreenActivity() {
    private lateinit var b: ActivityPlayerBinding
    private lateinit var settings: AppSettings
    private lateinit var history: PlaybackHistory
    private lateinit var recentChannels: RecentChannelsStore
    private lateinit var favorites: FavoritesStore
    private lateinit var overlayCategoryAdapter: CategoryAdapter
    private lateinit var overlayChannelAdapter: MediaAdapter

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
    private var controlsVisible = true
    private var dragging = false
    private var resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT

    private val hideControls = Runnable { setControlsVisible(false) }

    private val progressUpdater = object : Runnable {
        override fun run() {
            updateProgress()
            handler.postDelayed(this, 500L)
        }
    }

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
                showStatus("Reconnecting… $retryCount/3")
                handler.postDelayed({ retryPlayback() }, 1200L * retryCount)
            } else {
                showStatus("Playback unavailable • OK")
                b.statusText.isFocusable = true
                b.statusText.requestFocus()
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_READY) {
                retryCount = 0
                b.statusText.visibility = View.GONE
                updateQuality()
            } else if (playbackState == Player.STATE_ENDED && type == MediaType.SERIES && settings.autoNextEpisode) {
                playNextEpisodeIfAvailable()
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (type == MediaType.LIVE && !isPlaying && PlaybackEngine.player?.playbackState == Player.STATE_READY) {
                handler.postDelayed({ PlaybackEngine.player?.play() }, 150L)
            }
            updatePlayPauseIcon()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityPlayerBinding.inflate(layoutInflater)
        setContentView(b.root)

        settings = AppSettings(this)
        history = PlaybackHistory(this)
        recentChannels = RecentChannelsStore(this)
        favorites = FavoritesStore(this)
        mediaId = intent.getStringExtra("id").orEmpty()
        title = intent.getStringExtra("title").orEmpty()
        url = intent.getStringExtra("url").orEmpty()
        type = detectType(intent.getStringExtra("type"), mediaId)
        nextUrl = intent.getStringExtra("next_url").orEmpty()
        nextTitle = intent.getStringExtra("next_title").orEmpty()
        nextId = intent.getStringExtra("next_id").orEmpty()

        setupUi()
        setupLiveOverlay()

        b.statusText.setOnClickListener {
            retryCount = 0
            retryPlayback()
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (b.liveBrowseOverlay.isVisible) {
                    hideLiveOverlay()
                    return
                }
                leavePlayer()
            }
        })
    }

    private fun setupUi() {
        b.playerView.useController = false
        b.playPauseButton.visibility = if (type == MediaType.LIVE) View.GONE else View.VISIBLE
        b.browseButton.visibility = if (type == MediaType.LIVE) View.VISIBLE else View.GONE
        b.progressBar.isEnabled = type != MediaType.LIVE

        resizeMode = if (type == MediaType.LIVE) {
            AspectRatioFrameLayout.RESIZE_MODE_ZOOM
        } else {
            when (settings.aspectMode) {
                "fill" -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                "zoom" -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
            }
        }
        b.playerView.resizeMode = resizeMode

        b.backControlButton.setOnClickListener { leavePlayer() }
        b.playPauseButton.setOnClickListener {
            val p = PlaybackEngine.player ?: return@setOnClickListener
            if (p.isPlaying) p.pause() else p.play()
            showControls()
        }
        b.qualityText.setOnClickListener { showVideoQualityMenu() }
        b.audioButton.setOnClickListener { showTrackMenu(C.TRACK_TYPE_AUDIO) }
        b.subtitleButton.setOnClickListener { showTrackMenu(C.TRACK_TYPE_TEXT) }
        b.zoomButton.setOnClickListener {
            resizeMode = when (resizeMode) {
                AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                AspectRatioFrameLayout.RESIZE_MODE_FIT -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                else -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            }
            b.playerView.resizeMode = resizeMode
            showControls()
        }
        b.browseButton.setOnClickListener { showLiveOverlay() }

        b.progressBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) = Unit
            override fun onStartTrackingTouch(seekBar: SeekBar?) { dragging = true }
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                dragging = false
                if (type == MediaType.LIVE) return
                val p = PlaybackEngine.player ?: return
                val duration = p.duration.takeIf { it > 0L } ?: return
                p.seekTo((duration * b.progressBar.progress) / 1000L)
                showControls()
            }
        })

        b.playerRoot.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_UP && !b.liveBrowseOverlay.isVisible) {
                showControls()
            }
            true
        }
        b.playerView.setOnClickListener { showControls() }
        showControls()
    }

    private fun setupLiveOverlay() {
        overlayCategoryAdapter = CategoryAdapter(::applyOverlayCategory)
        overlayChannelAdapter = MediaAdapter(favorites, ::selectOverlayChannel)
        b.overlayCategoryRecycler.layoutManager = LinearLayoutManager(this)
        b.overlayCategoryRecycler.adapter = overlayCategoryAdapter
        b.overlayChannelRecycler.layoutManager = LinearLayoutManager(this)
        b.overlayChannelRecycler.adapter = overlayChannelAdapter
    }

    override fun onStart() {
        super.onStart()
        if (url.isBlank()) return finish()
        attachAndPlay()
        handler.removeCallbacks(progressSaver)
        handler.postDelayed(progressSaver, 10_000L)
        handler.removeCallbacks(progressUpdater)
        handler.post(progressUpdater)
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (settings.pictureInPicture && PlaybackEngine.player?.isPlaying == true) maybeEnterPip()
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        inPip = isInPictureInPictureMode
        if (isInPictureInPictureMode) {
            b.playerControls.visibility = View.GONE
            b.liveBrowseOverlay.visibility = View.GONE
            b.statusText.visibility = View.GONE
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            if (type == MediaType.LIVE && event.keyCode in setOf(KeyEvent.KEYCODE_MEDIA_PAUSE, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)) {
                return true
            }
            when (event.keyCode) {
                KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_DPAD_UP -> {
                    if (type == MediaType.LIVE) {
                        showLiveOverlay()
                        return true
                    }
                }
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                    if (!b.liveBrowseOverlay.isVisible) {
                        showControls()
                    }
                }
                else -> if (!b.liveBrowseOverlay.isVisible) showControls()
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onStop() {
        handler.removeCallbacks(progressSaver)
        handler.removeCallbacks(progressUpdater)
        handler.removeCallbacks(hideControls)
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
        b.playerView.resizeMode = resizeMode
        if (type == MediaType.LIVE) player.playWhenReady = true
        updateQuality()
        updatePlayPauseIcon()
    }

    private fun updateProgress() {
        val p = PlaybackEngine.player ?: return
        if (type == MediaType.LIVE) {
            b.progressBar.progress = 1000
            return
        }
        if (!dragging) {
            val duration = p.duration.takeIf { it > 0L } ?: 0L
            b.progressBar.progress = if (duration > 0L) ((p.currentPosition * 1000L) / duration).toInt().coerceIn(0, 1000) else 0
        }
    }

    private fun updateQuality() {
        val f = PlaybackEngine.player?.videoFormat
        val label = when {
            f == null -> "—"
            f.height >= 2160 -> "2160p"
            f.height >= 1440 -> "1440p"
            f.height >= 1080 -> "1080p"
            f.height >= 720 -> "720p"
            f.height > 0 -> "${f.height}p"
            f.width > 0 -> "${f.width}w"
            else -> "—"
        }
        b.qualityText.text = label
    }

    private data class TrackChoice(val group: androidx.media3.common.Tracks.Group, val index: Int, val label: String)

    private fun collectTrackChoices(trackType: Int): List<TrackChoice> {
        val player = PlaybackEngine.player ?: return emptyList()
        val out = mutableListOf<TrackChoice>()
        player.currentTracks.groups.forEach { group ->
            if (group.type == trackType && group.isSupported) {
                for (i in 0 until group.length) {
                    if (!group.isTrackSupported(i)) continue
                    val f = group.getTrackFormat(i)
                    val label = when (trackType) {
                        C.TRACK_TYPE_VIDEO -> {
                            val quality = if (f.height > 0) "${f.height}p" else "Video"
                            val fps = if (f.frameRate > 0) " • ${f.frameRate.toInt()}fps" else ""
                            val bitrate = if (f.bitrate > 0) " • ${f.bitrate / 1000}kbps" else ""
                            quality + fps + bitrate
                        }
                        C.TRACK_TYPE_AUDIO -> listOfNotNull(f.label, f.language, if (f.channelCount > 0) "${f.channelCount}ch" else null).distinct().joinToString(" • ").ifBlank { "Audio ${out.size + 1}" }
                        else -> listOfNotNull(f.label, f.language).distinct().joinToString(" • ").ifBlank { "Subtitle ${out.size + 1}" }
                    }
                    out += TrackChoice(group, i, label)
                }
            }
        }
        return out.distinctBy { it.label }
    }

    private fun showTrackMenu(trackType: Int) {
        val player = PlaybackEngine.player ?: return
        val choices = collectTrackChoices(trackType)
        if (choices.isEmpty()) {
            showStatus(if (trackType == C.TRACK_TYPE_AUDIO) "No audio tracks" else "No subtitles")
            return
        }
        val isText = trackType == C.TRACK_TYPE_TEXT
        val labels = buildList {
            if (isText) add("Off")
            addAll(choices.map { it.label })
        }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(if (isText) "Subtitles" else "Audio")
            .setItems(labels) { _, which ->
                val builder = player.trackSelectionParameters.buildUpon().clearOverridesOfType(trackType)
                if (isText && which == 0) {
                    builder.setTrackTypeDisabled(trackType, true)
                } else {
                    builder.setTrackTypeDisabled(trackType, false)
                    val choice = choices[which - if (isText) 1 else 0]
                    builder.setOverrideForType(TrackSelectionOverride(choice.group.mediaTrackGroup, listOf(choice.index)))
                }
                player.trackSelectionParameters = builder.build()
                updateQuality(); showControls()
            }.show()
    }

    private fun showVideoQualityMenu() {
        val player = PlaybackEngine.player ?: return
        val choices = collectTrackChoices(C.TRACK_TYPE_VIDEO)
        if (choices.isEmpty()) { showStatus("Quality unavailable"); return }
        val labels = (listOf("Auto") + choices.map { it.label }).toTypedArray()
        AlertDialog.Builder(this).setTitle("Quality").setItems(labels) { _, which ->
            val builder = player.trackSelectionParameters.buildUpon().clearOverridesOfType(C.TRACK_TYPE_VIDEO)
            if (which > 0) {
                val choice = choices[which - 1]
                builder.setOverrideForType(TrackSelectionOverride(choice.group.mediaTrackGroup, listOf(choice.index)))
            }
            player.trackSelectionParameters = builder.build()
            updateQuality(); showControls()
        }.show()
    }

    private fun showLiveOverlay() {
        if (type != MediaType.LIVE) return
        val categories = LiveCatalog.categories()
        val channels = LiveCatalog.channelsFor()
        if (categories.isEmpty() || channels.isEmpty()) {
            showStatus("Channel list unavailable")
            return
        }
        setControlsVisible(false)
        b.liveBrowseOverlay.visibility = View.VISIBLE
        overlayCategoryAdapter.submit(categories)
        overlayChannelAdapter.submit(channels)
        b.overlayChannelHeader.text = "القنوات • ${channels.size}"
        b.overlayCategoryRecycler.requestFocus()
    }

    private fun hideLiveOverlay() {
        b.liveBrowseOverlay.visibility = View.GONE
        showControls()
    }

    private fun applyOverlayCategory(category: CategoryModel) {
        LiveCatalog.selectCategory(category.id)
        val channels = LiveCatalog.channelsFor(category.id)
        overlayChannelAdapter.submit(channels)
        b.overlayChannelHeader.text = "${category.name} • ${channels.size}"
        b.overlayChannelRecycler.requestFocus()
    }

    private fun selectOverlayChannel(item: MediaItemModel) {
        val visible = LiveCatalog.channelsFor()
        ChannelNavigator.setQueue(visible, item.id)
        switchLiveChannel(item)
        hideLiveOverlay()
    }

    private fun showControls() {
        if (inPip || b.liveBrowseOverlay.isVisible) return
        setControlsVisible(true)
        handler.removeCallbacks(hideControls)
        handler.postDelayed(hideControls, 3200L)
    }

    private fun setControlsVisible(visible: Boolean) {
        controlsVisible = visible
        b.playerControls.visibility = if (visible) View.VISIBLE else View.GONE
    }

    private fun updatePlayPauseIcon() {
        if (type == MediaType.LIVE) return
        val res = if (PlaybackEngine.player?.isPlaying == true) com.adwio.player.R.drawable.ic_pause else com.adwio.player.R.drawable.ic_play
        b.playPauseButton.setImageResource(res)
    }

    private fun retryPlayback() {
        val player = PlaybackEngine.player ?: return attachAndPlay()
        showStatus("Reconnecting…")
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
            val params = PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9)).build()
            inPip = enterPictureInPictureMode(params)
        }
    }

    private fun switchLiveChannel(item: MediaItemModel?) {
        if (type != MediaType.LIVE || item == null) return
        recentChannels.add(item)
        mediaId = "LIVE:${item.id}"
        title = item.name
        url = item.streamUrl
        showStatus("Loading…")
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
        showStatus("Loading…")
        retryCount = 0
        val player = PlaybackEngine.player ?: return attachAndPlay()
        player.setMediaItem(androidx.media3.common.MediaItem.fromUri(url))
        player.prepare()
        player.playWhenReady = true
    }

    private fun leavePlayer() {
        savePosition()
        if (settings.backgroundPlayback && PlaybackEngine.player?.isPlaying == true) {
            maybeEnterPip()
            if (!inPip) finish()
        } else {
            PlaybackEngine.stopAndRelease()
            finish()
        }
    }

    private fun showStatus(text: String) {
        b.statusText.text = text
        b.statusText.visibility = View.VISIBLE
        handler.postDelayed({ if (!b.statusText.hasFocus()) b.statusText.visibility = View.GONE }, 1800L)
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
