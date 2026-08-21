package com.adwio.player.ui.library

import android.app.PictureInPictureParams
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.media3.common.C
import androidx.media3.common.TrackSelectionOverride
import android.util.Rational
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.EditText
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.adwio.player.R
import com.adwio.player.data.AppResumeState
import com.adwio.player.data.AppSettings
import com.adwio.player.data.FavoritesStore
import com.adwio.player.data.LibrarySnapshotCache
import com.adwio.player.data.M3uCache
import com.adwio.player.data.M3uClient
import com.adwio.player.data.RecentChannelsStore
import com.adwio.player.data.SessionStore
import com.adwio.player.data.XtreamClient
import com.adwio.player.data.model.CategoryModel
import com.adwio.player.data.model.MediaItemModel
import com.adwio.player.data.model.MediaType
import com.adwio.player.databinding.ActivityLibraryBinding
import com.adwio.player.ui.BaseFullscreenActivity
import com.adwio.player.ui.details.MovieDetailsActivity
import com.adwio.player.ui.details.SeriesDetailsActivity
import com.adwio.player.ui.home.CategoryAdapter
import com.adwio.player.ui.home.MediaAdapter
import com.adwio.player.ui.home.PosterAdapter
import com.adwio.player.ui.player.ChannelNavigator
import com.adwio.player.ui.player.LiveCatalog
import com.adwio.player.ui.player.PlaybackEngine
import com.adwio.player.ui.player.PlayerActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

class LibraryActivity : BaseFullscreenActivity() {
    companion object {
        private const val RECENTLY_ADDED_ID = "__recent_30__"
        private const val RECENT_LIVE_ID = "__recent_live__"
        private const val RECENT_WATCHED_ID = "__recent_watched__"
        private const val SEARCH_ID = "__search__"
        private const val FAVORITES_ID = "__favorites__"
        const val EXTRA_TYPE = "type"
        const val EXTRA_SEARCH = "search"
        const val EXTRA_FAVORITES = "favorites"
    }

    private lateinit var b: ActivityLibraryBinding
    private val api = XtreamClient()
    private val m3u = M3uClient()
    private val m3uCache by lazy { M3uCache(this) }
    private val snapshotCache by lazy { LibrarySnapshotCache(this) }
    private lateinit var store: SessionStore
    private lateinit var favorites: FavoritesStore
    private lateinit var recentChannels: RecentChannelsStore
    private lateinit var categoryAdapter: CategoryAdapter
    private lateinit var liveAdapter: MediaAdapter
    private lateinit var posterAdapter: PosterAdapter
    private var allItems: List<MediaItemModel> = emptyList()
    private var selectedCategory = ""
    private var previewJob: Job? = null
    private var activePreviewLiveId: String? = null
    private var hasVisibleContent = false
    private val liveUiState by lazy { getSharedPreferences("adwio_live_ui_state", MODE_PRIVATE) }
    private var restoreLiveOnResume = false
    private var enteringMiniPip = false
    private var inMiniPip = false
    private var wasMiniPip = false
    private var previewOriginalParams: LinearLayout.LayoutParams? = null
    private var liveFullscreenMode = false
    private lateinit var fullscreenCategoryAdapter: CategoryAdapter
    private lateinit var fullscreenChannelAdapter: MediaAdapter
    private val liveControlsHandler = Handler(Looper.getMainLooper())
    private var liveStartedAt = 0L
    private var liveControlsVisible = true
    private val liveClockRunnable = object : Runnable {
        override fun run() {
  if (type == MediaType.LIVE && liveStartedAt > 0L) {
      val elapsed = ((System.currentTimeMillis() - liveStartedAt) / 1000L).coerceAtLeast(0L)
      val h = elapsed / 3600L
      val m = (elapsed % 3600L) / 60L
      val s = elapsed % 60L
      b.previewWatchTime.text = if (h > 0) "%02d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
  }
  liveControlsHandler.postDelayed(this, 1000L)
        }
    }
    private val hideLiveControlsRunnable = Runnable { setLiveControlsVisible(false) }

    private val type: MediaType by lazy {
        runCatching { MediaType.valueOf(intent.getStringExtra(EXTRA_TYPE) ?: "LIVE") }
            .getOrDefault(MediaType.LIVE)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityLibraryBinding.inflate(layoutInflater)
        setContentView(b.root)

        store = SessionStore(this)
        favorites = FavoritesStore(this)
        recentChannels = RecentChannelsStore(this)
        liveAdapter = MediaAdapter(favorites, ::openItem, ::previewLive)
        posterAdapter = PosterAdapter(favorites, ::openItem)
        categoryAdapter = CategoryAdapter(::applyCategory)

        b.categoryRecycler.layoutManager = LinearLayoutManager(this)
        b.categoryRecycler.adapter = categoryAdapter
        b.backButton.setOnClickListener { finish() }
        b.searchButton.visibility = View.GONE
        b.favoritesButton.visibility = View.GONE
        b.recentButton.visibility = if (type == MediaType.LIVE) View.VISIBLE else View.GONE
        b.recentButton.setOnClickListener { showRecentChannels() }
        b.livePreviewPanel.visibility = if (type == MediaType.LIVE) View.VISIBLE else View.GONE
        b.previewFullscreenButton.visibility = if (type == MediaType.LIVE) View.VISIBLE else View.GONE

        if (type == MediaType.LIVE) {
            // Reference architecture: the same live PlayerView/ExoPlayer stays alive
            // for preview, fullscreen and PiP. Fullscreen is a UI mode, not a new Activity.
            b.previewPlayer.setKeepContentOnPlayerReset(true)
            b.previewPlayer.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
            b.previewPlayer.onFullscreenRequested = {
                if (liveFullscreenMode) toggleLiveControls() else enterLiveFullscreenInPlace()
            }
            b.previewFullscreenButton.setOnClickListener {
                if (liveFullscreenMode) exitLiveFullscreenInPlace() else enterLiveFullscreenInPlace()
            }
            b.previewBackButton.setOnClickListener { exitLiveFullscreenInPlace() }
            b.previewAspectButton.setOnClickListener { cycleLiveAspectRatio() }
            b.previewChannelsButton.setOnClickListener { toggleFullscreenChannels() }
            b.previewAudioButton.setOnClickListener { showLiveTrackPicker(C.TRACK_TYPE_AUDIO, "Audio") }
            b.previewSubtitleButton.setOnClickListener { showLiveTrackPicker(C.TRACK_TYPE_TEXT, "Subtitles") }
            fullscreenCategoryAdapter = CategoryAdapter(::applyFullscreenCategory)
            fullscreenChannelAdapter = MediaAdapter(favorites, ::openFullscreenChannel, ::previewLive)
            b.fullscreenCategoryRecycler.layoutManager = LinearLayoutManager(this)
            b.fullscreenCategoryRecycler.adapter = fullscreenCategoryAdapter
            b.fullscreenChannelRecycler.layoutManager = LinearLayoutManager(this)
            b.fullscreenChannelRecycler.adapter = fullscreenChannelAdapter
            liveControlsHandler.post(liveClockRunnable)

            onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (liveFullscreenMode) {
                        exitLiveFullscreenInPlace()
                    } else {
                        PlaybackEngine.stopAndRelease()
                        AppResumeState(this@LibraryActivity).clearPlaybackKeepingLibrary()
                        finish()
                    }
                }
            })
        } else {
            b.previewFullscreenButton.setOnClickListener { openCurrentLiveFullscreen() }
        }

        configureGrid()
        load()
    }

    private fun configureGrid() {
        if (type == MediaType.LIVE) {
            b.contentRecycler.layoutManager = LinearLayoutManager(this)
            b.contentRecycler.adapter = liveAdapter
        } else {
            val widthDp = resources.displayMetrics.widthPixels / resources.displayMetrics.density
            val spans = when (AppSettings(this).gridDensity) {
                "6" -> 6
                "8" -> 8
                else -> when {
                    widthDp >= 1200f -> 10
                    widthDp >= 900f -> 8
                    widthDp >= 650f -> 7
                    else -> 5
                }
            }
            b.contentRecycler.layoutManager = GridLayoutManager(this, spans)
            b.contentRecycler.adapter = posterAdapter
        }
    }

    private fun load() {
        val session = store.load() ?: return finish()
        val sourceKey = "${session.server.id}|${session.server.baseUrl}"

        b.titleText.text = when (type) {
            MediaType.LIVE -> getString(R.string.live_title)
            MediaType.MOVIE -> getString(R.string.movies_title)
            MediaType.SERIES -> getString(R.string.series_title)
        }
        b.subtitleText.text = getString(R.string.loading_content)

        lifecycleScope.launch {
            val cached = withContext(Dispatchers.IO) { snapshotCache.load(sourceKey, type) }
            if (cached != null && cached.items.isNotEmpty()) {
                renderLibrary(cached.categories, cached.items)
                hasVisibleContent = true
                if (snapshotCache.isFresh(cached)) {
                    applyLaunchIntent()
                    return@launch
                }
                b.subtitleText.text = getString(R.string.updating_content)
            } else {
                b.contentRecycler.alpha = 0.45f
                b.categoryRecycler.alpha = 0.45f
            }

            val remote = withContext(Dispatchers.IO) {
                withTimeoutOrNull(45_000L) { loadRemote(session) }
            }

            b.contentRecycler.alpha = 1f
            b.categoryRecycler.alpha = 1f

            if (remote != null && remote.second.isNotEmpty()) {
                val (cats, items) = remote
                snapshotCache.save(sourceKey, type, cats, items)
                renderLibrary(cats, items)
                hasVisibleContent = true
                b.subtitleText.text = if (type == MediaType.LIVE) {
                    getString(R.string.recently_added)
                } else {
                    getString(R.string.recently_added)
                }
                applyLaunchIntent()
            } else if (hasVisibleContent) {
                b.subtitleText.text = getString(R.string.showing_saved_content)
                applyLaunchIntent()
            } else {
                allItems = emptyList()
                submit(emptyList())
                b.subtitleText.text = getString(R.string.load_failed_keep_cache)
            }
        }
    }

    private suspend fun loadRemote(session: com.adwio.player.data.model.Session): Pair<List<CategoryModel>, List<MediaItemModel>>? {
        return if (session.server.id == "m3u") {
            val fast = m3uCache.loadFast(session.server.baseUrl, 1800).filter { it.type == type }
            if (fast.isNotEmpty()) {
                val cats = m3u.categories(fast, type)
                // Full refresh is still requested once, but only after we have usable local/partial content.
                val full = m3uCache.loadForType(session.server.baseUrl, type)
                val finalItems = if (full.isNotEmpty()) full else fast
                m3u.categories(finalItems, type) to finalItems
            } else {
                val full = m3uCache.loadForType(session.server.baseUrl, type)
                if (full.isEmpty()) null else m3u.categories(full, type) to full
            }
        } else {
            coroutineScope {
                val catsDeferred = async { api.loadCategories(session, type) }
                val itemsDeferred = async {
                    when (type) {
                        MediaType.LIVE -> api.loadLive(session)
                        MediaType.MOVIE -> api.loadMovies(session)
                        MediaType.SERIES -> api.loadSeries(session)
                    }
                }
                val cats = catsDeferred.await()
                val items = itemsDeferred.await()
                if (items.isEmpty()) null else cats to items
            }
        }
    }

    private fun renderLibrary(cats: List<CategoryModel>, items: List<MediaItemModel>) {
        if (items.isEmpty()) return
        allItems = items

        val totalLabel = java.text.NumberFormat.getIntegerInstance().format(items.size)
        val allSectionLabel = when (type) {
            MediaType.LIVE -> getString(R.string.all_channels)
            MediaType.MOVIE -> getString(R.string.all_movies)
            MediaType.SERIES -> getString(R.string.all_series)
        }

        val named = (if (cats.isEmpty()) listOf(CategoryModel("", allSectionLabel)) else cats).map {
            if (it.id.isBlank()) it.copy(name = "$allSectionLabel ($totalLabel)") else it
        }

        val allCategory = named.firstOrNull { it.id.isBlank() }
            ?: CategoryModel("", "$allSectionLabel ($totalLabel)")
        val providerCategories = named.filterNot { it.id.isBlank() }

        val tools = mutableListOf(
            CategoryModel(SEARCH_ID, getString(R.string.search)),
            allCategory
        )

        if (type == MediaType.LIVE) {
            tools += CategoryModel(RECENT_WATCHED_ID, getString(R.string.recent_watched_channels))
            tools += CategoryModel(RECENT_LIVE_ID, getString(R.string.recently_added))
        } else {
            tools += CategoryModel(RECENTLY_ADDED_ID, getString(R.string.recently_added_30))
        }

        tools += CategoryModel(FAVORITES_ID, getString(R.string.favorites))
        categoryAdapter.submit(tools + providerCategories)

        if (type == MediaType.LIVE) {
            LiveCatalog.setData(named, items, "")
            restoreLiveStateOrDefault()
        } else {
            selectedCategory = RECENTLY_ADDED_ID
            submit(recentlyAdded(items))
        }
    }

    private fun restoreLiveStateOrDefault() {
        if (type != MediaType.LIVE) return

        val resume = AppResumeState(this).load()
        val savedCategory = liveUiState.getString("category", null)
            ?: resume.categoryId.takeIf { resume.screen == AppResumeState.SCREEN_LIBRARY && resume.mediaType == MediaType.LIVE }
        val recent = recentWatchedItems()

        selectedCategory = when {
            !savedCategory.isNullOrBlank() &&
                (savedCategory == RECENT_WATCHED_ID ||
                 savedCategory == RECENT_LIVE_ID ||
                 savedCategory == FAVORITES_ID ||
                 allItems.any { it.categoryId == savedCategory }) -> savedCategory

            recent.isNotEmpty() -> RECENT_WATCHED_ID
            else -> ""
        }

        submitListForSelectedCategory()

        val position = liveUiState.getInt("scroll_position", 0).coerceAtLeast(0)
        b.contentRecycler.post {
            (b.contentRecycler.layoutManager as? LinearLayoutManager)
                ?.scrollToPositionWithOffset(position, 0)
        }
    }

    private fun recentWatchedItems(): List<MediaItemModel> {
        val byId = allItems.associateBy { it.id }
        return recentChannels.list()
            .mapNotNull { recent -> byId[recent.id] ?: recent }
            .distinctBy { it.id }
            .take(30)
    }

    private fun submitListForSelectedCategory() {
        val list = when {
            selectedCategory == RECENT_WATCHED_ID -> recentWatchedItems()
            selectedCategory == RECENT_LIVE_ID -> recentlyAdded(allItems).take(40)
            selectedCategory == FAVORITES_ID -> allItems.filter { favorites.isFavorite(it.id, it.type) }
            selectedCategory.isBlank() -> allItems
            selectedCategory.startsWith("__") -> allItems
            else -> allItems.filter { it.categoryId == selectedCategory }
        }
        submit(list)
    }

    private fun applyLaunchIntent() {
        if (intent.getBooleanExtra(EXTRA_FAVORITES, false)) showFavorites()
        else if (intent.getBooleanExtra(EXTRA_SEARCH, false)) showSearch()
    }

    private fun submit(items: List<MediaItemModel>) {
        if (type == MediaType.LIVE) liveAdapter.submit(items) else posterAdapter.submit(items)
    }

    private fun applyCategory(c: CategoryModel) {
        selectedCategory = c.id
        if (type == MediaType.LIVE) LiveCatalog.selectCategory(c.id)
        if (c.id == SEARCH_ID) return showSearch()
        if (c.id == FAVORITES_ID) return showFavorites()
        if (c.id == RECENT_WATCHED_ID) {
            val list = recentWatchedItems()
            submit(list)
            b.subtitleText.text = getString(R.string.recent_watched_channels)
            persistLiveUiState()
            return
        }

        val list = when {
            c.id == RECENTLY_ADDED_ID -> recentlyAdded(allItems)
            c.id == RECENT_LIVE_ID -> recentlyAdded(allItems).take(40)
            c.id.isBlank() -> allItems
            else -> allItems.filter { it.categoryId == c.id }
        }
        submit(list)
        b.subtitleText.text = c.name
        if (type == MediaType.LIVE) persistLiveUiState()
    }

    private fun recentlyAdded(items: List<MediaItemModel>): List<MediaItemModel> {
        val dated = items.filter { it.addedAt > 0L }
        return if (dated.isNotEmpty()) dated.sortedByDescending { it.addedAt }.take(30)
        else items.takeLast(30).asReversed()
    }

    private fun showSearch() {
        val input = EditText(this).apply {
            hint = getString(R.string.search_hint)
            setSingleLine()
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.search)
            .setView(input)
            .setPositiveButton(R.string.search) { _, _ ->
                val q = input.text.toString().trim()
                val base = when {
                    selectedCategory.isBlank() || selectedCategory.startsWith("__") -> allItems
                    else -> allItems.filter { it.categoryId == selectedCategory }
                }
                val list = if (q.isBlank()) base else base.filter { it.name.contains(q, true) }
                submit(list)
                b.subtitleText.text = getString(R.string.results_count, list.size)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showFavorites() {
        val list = allItems.filter { favorites.isFavorite(it.id, it.type) }
        submit(list)
        b.subtitleText.text = getString(R.string.favorites_count, list.size)
    }

    private fun showRecentChannels() {
        if (type != MediaType.LIVE) return
        selectedCategory = RECENT_WATCHED_ID
        val list = recentWatchedItems()
        submit(list)
        b.subtitleText.text = getString(R.string.recent_channels_count, list.size)
        persistLiveUiState()
    }

    private fun previewLive(item: MediaItemModel) {
        if (type == MediaType.LIVE) b.previewChannelName.text = item.name
    }

    private fun openFullscreenChannel(item: MediaItemModel) {
        val changed = PlaybackEngine.currentId.removePrefix("LIVE:") != item.id
        openItem(item)
        if (changed) liveStartedAt = System.currentTimeMillis()
        b.fullscreenChannelsOverlay.visibility = View.GONE
        scheduleLiveControlsHide()
    }

    private fun applyFullscreenCategory(category: CategoryModel) {
        selectedCategory = category.id
        LiveCatalog.selectCategory(category.id)
        val list = when {
  category.id == RECENT_WATCHED_ID -> recentWatchedItems()
  category.id == RECENT_LIVE_ID -> recentlyAdded(allItems).take(40)
  category.id == FAVORITES_ID -> allItems.filter { favorites.isFavorite(it.id, it.type) }
  category.id.isBlank() -> allItems
  category.id.startsWith("__") -> allItems
  else -> allItems.filter { it.categoryId == category.id }
        }
        fullscreenChannelAdapter.submit(list)
    }

    private fun refreshFullscreenCatalog() {
        if (type != MediaType.LIVE || !::fullscreenCategoryAdapter.isInitialized) return
        val total = java.text.NumberFormat.getIntegerInstance().format(allItems.size)
        val categories = mutableListOf<CategoryModel>()
        categories += CategoryModel("", "${getString(R.string.all_channels)} ($total)")
        categories += CategoryModel(RECENT_WATCHED_ID, getString(R.string.recent_watched_channels))
        categories += CategoryModel(RECENT_LIVE_ID, getString(R.string.recently_added))
        categories += CategoryModel(FAVORITES_ID, getString(R.string.favorites))
        categories += LiveCatalog.categories().filter { it.id.isNotBlank() && it.name.isNotBlank() }.distinctBy { it.id }
        fullscreenCategoryAdapter.submit(categories)
        val list = if (selectedCategory.isBlank() || selectedCategory.startsWith("__")) allItems else allItems.filter { it.categoryId == selectedCategory }
        fullscreenChannelAdapter.submit(list)
    }

    private fun toggleFullscreenChannels() {
        if (!liveFullscreenMode) return
        refreshFullscreenCatalog()
        b.fullscreenChannelsOverlay.visibility = if (b.fullscreenChannelsOverlay.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        setLiveControlsVisible(true)
        scheduleLiveControlsHide()
    }

    private fun toggleLiveControls() {
        if (!liveFullscreenMode) return
        setLiveControlsVisible(!liveControlsVisible)
        if (liveControlsVisible) scheduleLiveControlsHide()
    }

    private fun setLiveControlsVisible(visible: Boolean) {
        liveControlsVisible = visible
        b.previewFooter.visibility = if (visible && !inMiniPip) View.VISIBLE else View.GONE
        if (!visible) b.fullscreenChannelsOverlay.visibility = View.GONE
    }

    private fun scheduleLiveControlsHide() {
        liveControlsHandler.removeCallbacks(hideLiveControlsRunnable)
        liveControlsHandler.postDelayed(hideLiveControlsRunnable, 3000L)
    }

    private fun showLiveTrackPicker(trackType: Int, title: String) {
        val player = PlaybackEngine.player ?: return
        val choices = mutableListOf<Pair<String, TrackSelectionOverride>>()
        player.currentTracks.groups.filter { it.type == trackType }.forEach { group ->
  for (i in 0 until group.length) {
      if (!group.isTrackSupported(i)) continue
      val format = group.getTrackFormat(i)
      val label = format.label?.takeIf { it.isNotBlank() } ?: format.language?.uppercase() ?: if (trackType == C.TRACK_TYPE_AUDIO) "Audio ${choices.size + 1}" else "Subtitle ${choices.size + 1}"
      choices += label to TrackSelectionOverride(group.mediaTrackGroup, listOf(i))
  }
        }
        if (choices.isEmpty()) {
  android.widget.Toast.makeText(this, "$title unavailable", android.widget.Toast.LENGTH_SHORT).show()
  return
        }
        AlertDialog.Builder(this).setTitle(title).setItems(choices.map { it.first }.toTypedArray()) { _, which ->
  player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().setOverrideForType(choices[which].second).build()
  scheduleLiveControlsHide()
        }.show()
    }


    private fun activateLivePreview(item: MediaItemModel) {
        previewJob?.cancel()
        activePreviewLiveId = item.id
        b.previewChannelName.text = item.name
        val player = PlaybackEngine.play(
            context = this,
            settings = AppSettings(this),
            url = item.streamUrl,
            title = item.name,
            id = "LIVE:${item.id}",
            type = MediaType.LIVE
        )
        b.previewPlayer.player = player
        player.volume = 1f
        configureLiveAutoPip()
        b.previewPlayer.requestFocus()
    }

    override fun onStart() {
        super.onStart()
        if (type == MediaType.LIVE && PlaybackEngine.player != null &&
            PlaybackEngine.currentType == MediaType.LIVE && PlaybackEngine.currentUrl.isNotBlank()
        ) {
            b.previewPlayer.player = PlaybackEngine.player
            b.previewChannelName.text = PlaybackEngine.currentTitle
            activePreviewLiveId = PlaybackEngine.currentId.removePrefix("LIVE:").ifBlank { activePreviewLiveId }
            configureLiveAutoPip()
        }
    }

    override fun onResume() {
        super.onResume()
        if (type == MediaType.LIVE && !inMiniPip &&
            PlaybackEngine.player != null && PlaybackEngine.currentType == MediaType.LIVE
        ) {
            b.previewPlayer.player = PlaybackEngine.player
            PlaybackEngine.player?.let { player ->
                player.playWhenReady = true
                player.play()
            }
        }
        if (type == MediaType.LIVE && restoreLiveOnResume && allItems.isNotEmpty()) {
            restoreLiveOnResume = false
            restoreLiveStateOrDefault()
        }
    }

    override fun onPause() {
        if (type == MediaType.LIVE) {
            persistLiveUiState()
        } else {
            AppResumeState(this).saveLibrary(
                type = type,
                categoryId = selectedCategory,
                scrollPosition = (b.contentRecycler.layoutManager as? LinearLayoutManager)
                    ?.findFirstVisibleItemPosition()?.coerceAtLeast(0) ?: 0
            )
        }
        super.onPause()
    }

    override fun onUserLeaveHint() {
        val hasActiveLive =
            type == MediaType.LIVE &&
            PlaybackEngine.player != null &&
            PlaybackEngine.currentType == MediaType.LIVE &&
            PlaybackEngine.currentUrl.isNotBlank()

        if (hasActiveLive &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            Build.VERSION.SDK_INT < Build.VERSION_CODES.S
        ) {
            enterMiniPictureInPicture()
        }
        super.onUserLeaveHint()
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        inMiniPip = isInPictureInPictureMode
        enteringMiniPip = false

        if (isInPictureInPictureMode) {
            wasMiniPip = true
            PlaybackEngine.player?.let { player ->
                player.playWhenReady = true
                player.play()
                b.previewPlayer.player = player
            }
            prepareMiniPipUi()
        } else {
            PlaybackEngine.player?.let { player ->
                b.previewPlayer.player = player
                player.playWhenReady = true
                player.play()
            }
            restoreMiniUi()
        }
    }

    private fun configureLiveAutoPip() {
        if (type != MediaType.LIVE || Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        if (PlaybackEngine.currentUrl.isBlank()) return

        runCatching {
            val builder = PictureInPictureParams.Builder()
                .setAspectRatio(Rational(16, 9))

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                builder.setAutoEnterEnabled(true)
                builder.setSeamlessResizeEnabled(true)
            }

            setPictureInPictureParams(builder.build())
        }
    }

    private fun enterMiniPictureInPicture() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        enteringMiniPip = true
        prepareMiniPipUi()
        val entered = runCatching {
            enterPictureInPictureMode(
                PictureInPictureParams.Builder()
                    .setAspectRatio(Rational(16, 9))
                    .build()
            )
        }.getOrDefault(false)
        inMiniPip = entered
        if (!entered) {
            enteringMiniPip = false
            restoreMiniUi()
        }
    }

    private fun prepareMiniPipUi() {
        if (type != MediaType.LIVE) return
        applyExpandedLiveUi()
    }

    private fun restoreMiniUi() {
        if (type != MediaType.LIVE) return
        if (liveFullscreenMode) {
            applyExpandedLiveUi()
        } else {
            restoreNormalLiveUi()
        }
    }

    private fun enterLiveFullscreenInPlace() {
        if (type != MediaType.LIVE ||
            PlaybackEngine.currentType != MediaType.LIVE ||
            PlaybackEngine.currentUrl.isBlank()
        ) return

        persistLiveUiState()
        liveFullscreenMode = true
        b.previewPlayer.player = PlaybackEngine.player

        // Fullscreen means the player occupies the complete device area.
        // Keep the video aspect ratio by default; do not crop/over-zoom.
        b.previewPlayer.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT

        b.previewBackButton.visibility = View.VISIBLE
        b.previewAspectButton.visibility = View.VISIBLE
        b.previewChannelsButton.visibility = View.VISIBLE
        b.previewAudioButton.visibility = View.VISIBLE
        b.previewSubtitleButton.visibility = View.VISIBLE
        b.previewQualityText.visibility = View.VISIBLE
        b.previewWatchTime.visibility = View.VISIBLE
        if (liveStartedAt == 0L) liveStartedAt = System.currentTimeMillis()
        val height = PlaybackEngine.player?.videoFormat?.height ?: 0
        b.previewQualityText.text = if (height > 0) "${height}p" else "AUTO"
        configureLiveAutoPip()
        applyExpandedLiveUi()
        setLiveControlsVisible(true)
        scheduleLiveControlsHide()
    }

    private fun exitLiveFullscreenInPlace() {
        if (type != MediaType.LIVE) return
        liveFullscreenMode = false
        liveControlsHandler.removeCallbacks(hideLiveControlsRunnable)
        b.fullscreenChannelsOverlay.visibility = View.GONE
        b.previewBackButton.visibility = View.GONE
        b.previewAspectButton.visibility = View.GONE
        b.previewChannelsButton.visibility = View.GONE
        b.previewAudioButton.visibility = View.GONE
        b.previewSubtitleButton.visibility = View.GONE
        b.previewQualityText.visibility = View.GONE
        b.previewWatchTime.visibility = View.GONE
        restoreNormalLiveUi()
        b.previewPlayer.player = PlaybackEngine.player
        PlaybackEngine.player?.play()
        b.previewPlayer.requestFocus()
    }

    private fun cycleLiveAspectRatio() {
        if (type != MediaType.LIVE) return

        b.previewPlayer.resizeMode = when (b.previewPlayer.resizeMode) {
            AspectRatioFrameLayout.RESIZE_MODE_FIT ->
                AspectRatioFrameLayout.RESIZE_MODE_FILL
            AspectRatioFrameLayout.RESIZE_MODE_FILL ->
                AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            else ->
                AspectRatioFrameLayout.RESIZE_MODE_FIT
        }
    }

    private fun applyExpandedLiveUi() {
        if (type != MediaType.LIVE) return

        if (previewOriginalParams == null) {
            previewOriginalParams = LinearLayout.LayoutParams(
                b.livePreviewPanel.layoutParams as LinearLayout.LayoutParams
            )
        }

        b.topBar.visibility = View.GONE
        b.categoryPanel.visibility = View.GONE
        b.contentRecycler.visibility = View.GONE

        // Keep the live control strip visible in fullscreen.
        b.previewFooter.visibility = if (inMiniPip) View.GONE else View.VISIBLE
        b.previewBackButton.visibility = if (inMiniPip) View.GONE else View.VISIBLE
        b.previewAspectButton.visibility = if (inMiniPip) View.GONE else View.VISIBLE

        b.libraryRoot.setPadding(0, 0, 0, 0)
        (b.libraryContentRow.layoutParams as? LinearLayout.LayoutParams)?.let { row ->
            row.topMargin = 0
            b.libraryContentRow.layoutParams = row
        }

        b.livePreviewPanel.layoutParams = LinearLayout.LayoutParams(
            0,
            ViewGroup.LayoutParams.MATCH_PARENT,
            1f
        ).apply { marginStart = 0 }

        b.livePreviewPanel.setPadding(0, 0, 0, 0)
        b.livePreviewPanel.setBackgroundColor(Color.BLACK)

        // FIT is the default fullscreen presentation. The control button lets
        // the user choose Fill/Zoom manually without forcing a cropped image.
        if (!inMiniPip) {
            b.previewPlayer.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
        }
        b.previewPlayer.player = PlaybackEngine.player
        PlaybackEngine.player?.let { player ->
            player.playWhenReady = true
            player.play()
        }
    }

    private fun restoreNormalLiveUi() {
        if (type != MediaType.LIVE) return

        b.topBar.visibility = View.VISIBLE
        b.categoryPanel.visibility = View.VISIBLE
        b.contentRecycler.visibility = View.VISIBLE
        b.previewFooter.visibility = View.VISIBLE
        b.previewBackButton.visibility = View.GONE
        b.previewAspectButton.visibility = View.GONE

        val density = resources.displayMetrics.density
        val rootPad = (7 * density).toInt()
        val previewPad = (5 * density).toInt()
        val rowTop = (5 * density).toInt()

        b.libraryRoot.setPadding(rootPad, rootPad, rootPad, rootPad)
        (b.libraryContentRow.layoutParams as? LinearLayout.LayoutParams)?.let { row ->
            row.topMargin = rowTop
            b.libraryContentRow.layoutParams = row
        }

        previewOriginalParams?.let {
            b.livePreviewPanel.layoutParams = LinearLayout.LayoutParams(it)
        }

        b.livePreviewPanel.setPadding(previewPad, previewPad, previewPad, previewPad)
        b.livePreviewPanel.setBackgroundResource(R.drawable.bg_live_preview_premium)
        b.previewPlayer.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
    }

    private fun persistLiveUiState() {
        if (type != MediaType.LIVE) return
        val position = (b.contentRecycler.layoutManager as? LinearLayoutManager)
            ?.findFirstVisibleItemPosition()
            ?.coerceAtLeast(0) ?: 0

        liveUiState.edit()
            .putString("category", selectedCategory)
            .putInt("scroll_position", position)
            .putString("channel_id", activePreviewLiveId)
            .apply()

        val active = PlaybackEngine.currentType == MediaType.LIVE &&
            PlaybackEngine.currentUrl.isNotBlank()
        AppResumeState(this).saveLibrary(
            type = MediaType.LIVE,
            categoryId = selectedCategory,
            scrollPosition = position,
            playbackActive = active,
            mediaId = if (active) PlaybackEngine.currentId else "",
            title = if (active) PlaybackEngine.currentTitle else "",
            url = if (active) PlaybackEngine.currentUrl else ""
        )
    }

    private fun openCurrentLiveFullscreen() {
        enterLiveFullscreenInPlace()
    }

    override fun onStop() {
        previewJob?.cancel()

        val playbackActive =
            PlaybackEngine.player != null &&
            PlaybackEngine.currentType == MediaType.LIVE &&
            PlaybackEngine.currentUrl.isNotBlank() &&
            PlaybackEngine.player?.playWhenReady == true

        val keepForPip = inMiniPip || enteringMiniPip ||
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && isInPictureInPictureMode)

        val keepForBackground = !isFinishing && playbackActive
        val keepPlayerAttached = keepForPip || keepForBackground

        if (!keepPlayerAttached) {
            b.previewPlayer.player = null
        }
        super.onStop()
    }

    override fun onDestroy() {
        if (wasMiniPip && isFinishing) {
            PlaybackEngine.stopAndRelease()
            AppResumeState(this).clearPlaybackKeepingLibrary()
        }
        super.onDestroy()
    }

    private fun openItem(item: MediaItemModel) {
        when (item.type) {
            MediaType.SERIES -> {
                val session = store.load()
                if (session?.server?.id == "m3u" && item.streamUrl.isNotBlank()) {
                    startActivity(Intent(this, PlayerActivity::class.java).apply {
                        putExtra("url", item.streamUrl)
                        putExtra("title", item.name)
                        putExtra("id", "SERIES:${item.id}")
                        putExtra("type", MediaType.SERIES.name)
                    })
                } else {
                    startActivity(Intent(this, SeriesDetailsActivity::class.java).apply {
                        putExtra(SeriesDetailsActivity.EXTRA_ID, item.id)
                        putExtra(SeriesDetailsActivity.EXTRA_TITLE, item.name)
                        putExtra(SeriesDetailsActivity.EXTRA_IMAGE, item.logoUrl)
                        putExtra(SeriesDetailsActivity.EXTRA_META, item.meta)
                    })
                }
            }
            MediaType.MOVIE -> startActivity(Intent(this, MovieDetailsActivity::class.java).apply {
                putExtra(MovieDetailsActivity.EXTRA_ID, item.id)
                putExtra(MovieDetailsActivity.EXTRA_TITLE, item.name)
                putExtra(MovieDetailsActivity.EXTRA_URL, item.streamUrl)
                putExtra(MovieDetailsActivity.EXTRA_IMAGE, item.logoUrl)
                putExtra(MovieDetailsActivity.EXTRA_META, item.meta)
            })
            MediaType.LIVE -> {
                recentChannels.add(item)
                if (activePreviewLiveId != item.id) {
                    activateLivePreview(item)
                    b.subtitleText.text = item.name
                    return
                }
                LiveCatalog.setData(
                    LiveCatalog.categories().ifEmpty { listOf(CategoryModel("", getString(R.string.all))) },
                    allItems,
                    selectedCategory
                )
                val visible = if (selectedCategory.isBlank() || selectedCategory.startsWith("__")) {
                    allItems
                } else allItems.filter { it.categoryId == selectedCategory }
                ChannelNavigator.setQueue(visible, item.id)
                persistLiveUiState()

                // Second tap enlarges the SAME live preview/player in-place.
                enterLiveFullscreenInPlace()
            }
        }
    }
}
