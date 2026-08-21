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
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.recyclerview.widget.LinearLayoutManager
import org.json.JSONArray
import com.adwio.player.R
import com.adwio.player.data.AppResumeState
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
import kotlin.math.ceil

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
    private var enteringPip = false
    private var seekRepeatDirection = 0
    private var returningToLivePreview = false
    private var nextUrl = ""
    private var nextTitle = ""
    private var nextId = ""
    private var dragging = false
    private var resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
    private var wasInPip = false
    private var nextPromptCancelled = false
    private var episodeQueue = mutableListOf<QueuedEpisode>()

    private data class QueuedEpisode(
        val id: String,
        val title: String,
        val url: String
    )

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
                showStatus("Playback unavailable")
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_READY) {
                retryCount = 0
                b.statusText.visibility = View.GONE
                updateQuality()
            } else if (playbackState == Player.STATE_ENDED && type == MediaType.SERIES && settings.autoNextEpisode && !nextPromptCancelled) {
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
        episodeQueue = parseEpisodeQueue(intent.getStringExtra("episode_queue"))
        if (episodeQueue.isNotEmpty()) {
            nextUrl = ""
            nextTitle = ""
            nextId = ""
        }

        setupUi()
        setupLiveOverlay()

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
        b.playerView.setKeepContentOnPlayerReset(true)
        b.pipPlayerView.useController = false
        b.pipPlayerView.setKeepContentOnPlayerReset(true)
        b.pipPlayerView.visibility = View.GONE
        b.playPauseButton.visibility = if (type == MediaType.LIVE) View.INVISIBLE else View.VISIBLE
        b.browseButton.visibility = if (type == MediaType.LIVE) View.VISIBLE else View.INVISIBLE
        b.progressBar.visibility = if (type == MediaType.LIVE) View.INVISIBLE else View.VISIBLE
        b.progressBar.isEnabled = type != MediaType.LIVE

        resizeMode = if (type == MediaType.LIVE) {
            // Live fullscreen always fills the physical display.
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

        b.rewind15Button.visibility = if (type == MediaType.LIVE) View.INVISIBLE else View.VISIBLE
        b.forward15Button.visibility = if (type == MediaType.LIVE) View.INVISIBLE else View.VISIBLE

        b.rewind15Button.setOnClickListener { seekBy(-15_000L) }
        b.forward15Button.setOnClickListener { seekBy(15_000L) }

        b.rewind15Button.setOnLongClickListener {
            startRepeatedSeek(-1)
            true
        }
        b.forward15Button.setOnLongClickListener {
            startRepeatedSeek(1)
            true
        }

        val stopRepeat = View.OnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_UP || event.action == MotionEvent.ACTION_CANCEL) {
                stopRepeatedSeek()
            }
            false
        }
        b.rewind15Button.setOnTouchListener(stopRepeat)
        b.forward15Button.setOnTouchListener(stopRepeat)

        b.qualityText.setOnClickListener { showVideoQualityMenu() }
        b.audioButton.setOnClickListener { showTrackMenu(C.TRACK_TYPE_AUDIO) }
        b.subtitleButton.setOnClickListener { showTrackMenu(C.TRACK_TYPE_TEXT) }

        b.zoomButton.setOnClickListener {
            resizeMode = when (resizeMode) {
                AspectRatioFrameLayout.RESIZE_MODE_FIT -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                AspectRatioFrameLayout.RESIZE_MODE_FILL -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
            }
            settings.aspectMode = when (resizeMode) {
                AspectRatioFrameLayout.RESIZE_MODE_FILL -> "fill"
                AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> "zoom"
                else -> "fit"
            }
            b.playerView.resizeMode = resizeMode
            showStatus(settings.aspectMode.uppercase())
            showControls()
        }

        b.browseButton.setOnClickListener { showLiveOverlay() }

        b.playNextNowButton.setOnClickListener {
            nextPromptCancelled = false
            playNextEpisodeIfAvailable()
        }
        b.cancelNextEpisodeButton.setOnClickListener {
            nextPromptCancelled = true
            b.nextEpisodePanel.visibility = View.GONE
        }

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
        AppResumeState(this).savePlayer(type, mediaId, title, url)
        attachAndPlay()
        handler.post(progressUpdater)
        handler.postDelayed(progressSaver, 10_000L)
    }

    override fun onUserLeaveHint() {
        val hasActivePlayback =
            PlaybackEngine.player != null &&
            PlaybackEngine.currentUrl.isNotBlank()

        if (hasActivePlayback && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            enteringPip = true
            preparePipSurface()
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                maybeEnterPip()
            }
        }
        super.onUserLeaveHint()
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        inPip = isInPictureInPictureMode
        enteringPip = false
        if (inPip) {
            wasInPip = true
            preparePipSurface()
            b.playerControls.visibility = View.GONE
            b.backControlButton.visibility = View.GONE
            b.liveBrowseOverlay.visibility = View.GONE
            b.nextEpisodePanel.visibility = View.GONE
            b.statusText.visibility = View.GONE
        } else {
            restoreMainPlayerSurface()
            showControls()
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
                KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_DPAD_CENTER -> {
                    if (!b.liveBrowseOverlay.isVisible) {
                        showControls()
                        return true
                    }
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onStop() {
        handler.removeCallbacksAndMessages(null)
        savePosition()

        val playbackActive =
            PlaybackEngine.player != null &&
            PlaybackEngine.currentUrl.isNotBlank() &&
            PlaybackEngine.player?.playWhenReady == true

        val keepForPip = inPip || enteringPip ||
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && isInPictureInPictureMode)

        val keepForBackground = !isFinishing && playbackActive
        val keepPlayer = keepForPip || keepForBackground

        if (!keepPlayer) {
            PlaybackEngine.player?.removeListener(playerListener)
            b.playerView.player = null
            b.pipPlayerView.player = null
        }

        if (!returningToLivePreview && !keepPlayer && !wasInPip) {
            PlaybackEngine.stopAndRelease()
            stopService(Intent(this, PlaybackService::class.java))
        }
        super.onStop()
    }

    override fun onDestroy() {
        if (wasInPip && isFinishing && !returningToLivePreview) {
            savePosition()
            PlaybackEngine.stopAndRelease()
            stopService(Intent(this, PlaybackService::class.java))
            AppResumeState(this).clearPlayback()
        }
        super.onDestroy()
    }

    private fun attachAndPlay() {
        val existing = PlaybackEngine.player
        val canReuse = existing != null &&
            PlaybackEngine.currentUrl == url &&
            PlaybackEngine.currentId == mediaId &&
            PlaybackEngine.currentType == type

        val p = if (canReuse) {
            existing!!
        } else {
            val saved = if (settings.rememberPosition && type != MediaType.LIVE && mediaId.isNotBlank()) {
                history.positionFor(mediaId, type)
            } else 0L
            PlaybackEngine.play(this, settings, url, title, mediaId, type, saved)
        }

        p.removeListener(playerListener)
        p.addListener(playerListener)
        b.playerView.player = p
        b.playerView.resizeMode = resizeMode
        p.playWhenReady = true
        configureAutoPip()
        updateQuality()
        updatePlayPauseIcon()
    }

    private fun showControls() {
        if (inPip || b.liveBrowseOverlay.isVisible) return
        setControlsVisible(true)
        handler.removeCallbacks(hideControls)
        handler.postDelayed(hideControls, settings.playerControlsTimeoutMs.toLong().coerceAtLeast(1800L))
    }

    private fun setControlsVisible(visible: Boolean) {
        b.playerControls.visibility = if (visible) View.VISIBLE else View.GONE
        b.backControlButton.visibility = if (visible) View.VISIBLE else View.GONE
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
        b.backControlButton.visibility = View.VISIBLE
        overlayCategoryAdapter.submit(categories)
        overlayChannelAdapter.submit(channels)
        b.overlayChannelHeader.text = "القنوات • ${channels.size}"
        b.overlayCategoryRecycler.requestFocus()
    }

    private fun hideLiveOverlay() {
        b.liveBrowseOverlay.visibility = View.GONE
        b.backControlButton.visibility = View.GONE
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
        ChannelNavigator.setQueue(LiveCatalog.channelsFor(), item.id)
        switchLiveChannel(item)
        b.liveBrowseOverlay.visibility = View.VISIBLE
        b.backControlButton.visibility = View.VISIBLE
        b.overlayChannelRecycler.requestFocus()
    }

    private fun switchLiveChannel(item: MediaItemModel) {
        recentChannels.add(item)
        mediaId = "LIVE:${item.id}"
        title = item.name
        url = item.streamUrl
        retryCount = 0
        showStatus("Loading…")

        val p = PlaybackEngine.play(
            context = this,
            settings = settings,
            url = url,
            title = title,
            id = mediaId,
            type = MediaType.LIVE
        )
        b.playerView.player = p
        b.playerView.resizeMode = resizeMode
    }

    private fun updateProgress() {
        val p = PlaybackEngine.player ?: return
        if (type == MediaType.LIVE) return
        updateNextEpisodePrompt(p)
        if (!dragging) {
            val duration = p.duration.takeIf { it > 0L } ?: 0L
            b.progressBar.progress = if (duration > 0L) ((p.currentPosition * 1000L) / duration).toInt().coerceIn(0, 1000) else 0
        }
    }

    private fun updateQuality() {
        val f = PlaybackEngine.player?.videoFormat
        b.qualityText.text = when {
            f == null -> "—"
            f.height >= 2160 -> "2160p"
            f.height >= 1440 -> "1440p"
            f.height >= 1080 -> "1080p"
            f.height >= 720 -> "720p"
            f.height > 0 -> "${f.height}p"
            else -> "—"
        }
    }

    private data class TrackChoice(
        val group: androidx.media3.common.Tracks.Group,
        val index: Int,
        val label: String
    )

    private fun collectTrackChoices(trackType: Int): List<TrackChoice> {
        val p = PlaybackEngine.player ?: return emptyList()
        val out = mutableListOf<TrackChoice>()
        p.currentTracks.groups.forEach { group ->
            if (group.type == trackType && group.isSupported) {
                for (i in 0 until group.length) {
                    if (!group.isTrackSupported(i)) continue
                    val f = group.getTrackFormat(i)
                    val label = when (trackType) {
                        C.TRACK_TYPE_VIDEO -> if (f.height > 0) "${f.height}p" else "Video"
                        C.TRACK_TYPE_AUDIO -> listOfNotNull(f.label, f.language).distinct().joinToString(" • ").ifBlank { "Audio ${out.size + 1}" }
                        else -> listOfNotNull(f.label, f.language).distinct().joinToString(" • ").ifBlank { "Subtitle ${out.size + 1}" }
                    }
                    out += TrackChoice(group, i, label)
                }
            }
        }
        return out.distinctBy { it.label }
    }

    private fun showTrackMenu(trackType: Int) {
        val p = PlaybackEngine.player ?: return
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
                val builder = p.trackSelectionParameters.buildUpon().clearOverridesOfType(trackType)
                if (isText && which == 0) {
                    builder.setTrackTypeDisabled(trackType, true)
                } else {
                    builder.setTrackTypeDisabled(trackType, false)
                    val choice = choices[which - if (isText) 1 else 0]
                    builder.setOverrideForType(TrackSelectionOverride(choice.group.mediaTrackGroup, listOf(choice.index)))
                }
                p.trackSelectionParameters = builder.build()
                showControls()
            }.show()
    }

    private fun showVideoQualityMenu() {
        val p = PlaybackEngine.player ?: return
        val choices = collectTrackChoices(C.TRACK_TYPE_VIDEO)
        if (choices.isEmpty()) {
            showStatus("Quality unavailable")
            return
        }

        val labels = (listOf("Auto") + choices.map { it.label }).toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Quality")
            .setItems(labels) { _, which ->
                val builder = p.trackSelectionParameters.buildUpon().clearOverridesOfType(C.TRACK_TYPE_VIDEO)
                if (which > 0) {
                    val choice = choices[which - 1]
                    builder.setOverrideForType(TrackSelectionOverride(choice.group.mediaTrackGroup, listOf(choice.index)))
                }
                p.trackSelectionParameters = builder.build()
                showControls()
            }.show()
    }

    private fun updatePlayPauseIcon() {
        if (type == MediaType.LIVE) return
        b.playPauseButton.setImageResource(
            if (PlaybackEngine.player?.isPlaying == true) R.drawable.ic_pause else R.drawable.ic_play
        )
    }

    private fun retryPlayback() {
        val p = PlaybackEngine.player ?: return attachAndPlay()
        p.prepare()
        p.playWhenReady = true
    }

    private fun savePosition() {
        val p = PlaybackEngine.player ?: return
        if (mediaId.isBlank() || type == MediaType.LIVE) return
        history.save(mediaId, title, url, type, p.currentPosition, p.duration.coerceAtLeast(0L))
    }

    private fun preparePipSurface() {
        val active = PlaybackEngine.player ?: return
        b.playerView.player = null
        b.pipPlayerView.visibility = View.VISIBLE
        b.pipPlayerView.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
        b.pipPlayerView.player = active
        active.playWhenReady = true
        active.play()
    }

    private fun restoreMainPlayerSurface() {
        val active = PlaybackEngine.player
        b.pipPlayerView.player = null
        b.pipPlayerView.visibility = View.GONE
        b.playerView.visibility = View.VISIBLE
        b.playerView.player = active
        b.playerView.resizeMode = resizeMode
        active?.let {
            it.playWhenReady = true
            it.play()
        }
    }

    private fun configureAutoPip() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        if (PlaybackEngine.currentUrl.isBlank()) return

        runCatching {
            val format = PlaybackEngine.player?.videoFormat
            val width = format?.width?.takeIf { it > 0 } ?: 16
            val height = format?.height?.takeIf { it > 0 } ?: 9
            val ratio = runCatching { Rational(width, height) }
                .getOrDefault(Rational(16, 9))

            val builder = PictureInPictureParams.Builder()
                .setAspectRatio(ratio)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                builder.setAutoEnterEnabled(true)
                builder.setSeamlessResizeEnabled(true)
            }

            setPictureInPictureParams(builder.build())
        }
    }

    private fun maybeEnterPip() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            enteringPip = false
            return
        }

        val format = PlaybackEngine.player?.videoFormat
        val width = format?.width?.takeIf { it > 0 } ?: 16
        val height = format?.height?.takeIf { it > 0 } ?: 9
        val ratio = runCatching { Rational(width, height) }.getOrDefault(Rational(16, 9))

        runCatching {
            val entered = enterPictureInPictureMode(
                PictureInPictureParams.Builder()
                    .setAspectRatio(ratio)
                    .build()
            )
            inPip = entered
            if (!entered) {
                enteringPip = false
                restoreMainPlayerSurface()
            }
        }.onFailure {
            enteringPip = false
            restoreMainPlayerSurface()
        }
    }

    private fun seekBy(deltaMs: Long) {
        if (type == MediaType.LIVE) return
        val p = PlaybackEngine.player ?: return
        val duration = p.duration.takeIf { it > 0L } ?: Long.MAX_VALUE
        val target = (p.currentPosition + deltaMs).coerceIn(0L, duration)
        p.seekTo(target)
        showStatus(formatPosition(target))
        showControls()
    }

    private fun startRepeatedSeek(direction: Int) {
        if (type == MediaType.LIVE) return
        seekRepeatDirection = direction
        handler.removeCallbacks(repeatedSeekRunnable)
        handler.post(repeatedSeekRunnable)
    }

    private val repeatedSeekRunnable = object : Runnable {
        override fun run() {
            if (seekRepeatDirection == 0) return
            seekBy(15_000L * seekRepeatDirection)
            handler.postDelayed(this, 260L)
        }
    }

    private fun stopRepeatedSeek() {
        seekRepeatDirection = 0
        handler.removeCallbacks(repeatedSeekRunnable)
    }

    private fun formatPosition(ms: Long): String {
        val total = (ms / 1000L).coerceAtLeast(0L)
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
    }

    private fun parseEpisodeQueue(raw: String?): MutableList<QueuedEpisode> {
        if (raw.isNullOrBlank()) return mutableListOf()
        return runCatching {
            val arr = JSONArray(raw)
            MutableList(arr.length()) { index ->
                val o = arr.getJSONObject(index)
                QueuedEpisode(
                    id = o.optString("id"),
                    title = o.optString("title"),
                    url = o.optString("url")
                )
            }.filter { it.url.isNotBlank() }.toMutableList()
        }.getOrDefault(mutableListOf())
    }

    private fun hasNextEpisode(): Boolean =
        episodeQueue.isNotEmpty() || nextUrl.isNotBlank()

    private fun peekNextEpisode(): QueuedEpisode? =
        episodeQueue.firstOrNull() ?: nextUrl.takeIf { it.isNotBlank() }?.let {
            QueuedEpisode(nextId, nextTitle.ifBlank { getString(R.string.next_episode) }, it)
        }

    private fun updateNextEpisodePrompt(p: Player) {
        if (type != MediaType.SERIES || !settings.autoNextEpisode || nextPromptCancelled || !hasNextEpisode()) {
            b.nextEpisodePanel.visibility = View.GONE
            return
        }

        val duration = p.duration.takeIf { it > 0L } ?: return
        val remaining = (duration - p.currentPosition).coerceAtLeast(0L)

        if (remaining <= 15_000L && remaining > 0L) {
            val next = peekNextEpisode() ?: return
            val seconds = ceil(remaining / 1000.0).toInt().coerceAtLeast(1)
            b.nextEpisodeTitle.text = next.title
            b.nextEpisodeCountdown.text = getString(R.string.next_episode_starts, seconds)
            b.nextEpisodePanel.visibility = View.VISIBLE
        } else if (remaining > 15_000L) {
            b.nextEpisodePanel.visibility = View.GONE
        }
    }

    private fun playNextEpisodeIfAvailable() {
        if (nextPromptCancelled || !settings.autoNextEpisode) return

        val next = if (episodeQueue.isNotEmpty()) {
            episodeQueue.removeAt(0)
        } else {
            if (nextUrl.isBlank()) return
            QueuedEpisode(nextId, nextTitle.ifBlank { getString(R.string.next_episode) }, nextUrl).also {
                nextUrl = ""
                nextTitle = ""
                nextId = ""
            }
        }

        mediaId = next.id
        title = next.title
        url = next.url
        nextPromptCancelled = false
        b.nextEpisodePanel.visibility = View.GONE

        AppResumeState(this).savePlayer(MediaType.SERIES, mediaId, title, url)

        val p = PlaybackEngine.play(
            context = this,
            settings = settings,
            url = url,
            title = title,
            id = mediaId,
            type = MediaType.SERIES
        )
        p.removeListener(playerListener)
        p.addListener(playerListener)
        b.playerView.player = p
        b.playerView.resizeMode = resizeMode
        p.playWhenReady = true
    }

    private fun leavePlayer() {
        savePosition()

        if (type == MediaType.LIVE) {
            returningToLivePreview = true

            // Release only PlayerActivity's video surface, not the ExoPlayer.
            // LibraryActivity will attach this exact live session again.
            b.playerView.player = null

            AppResumeState(this).clearPlaybackKeepingLibrary()
            finish()
            return
        }

        AppResumeState(this).clearPlayback()
        PlaybackEngine.stopAndRelease()
        stopService(Intent(this, PlaybackService::class.java))
        finish()
    }

    private fun showStatus(text: String) {
        b.statusText.text = text
        b.statusText.visibility = View.VISIBLE
        handler.postDelayed({ b.statusText.visibility = View.GONE }, 1600L)
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
