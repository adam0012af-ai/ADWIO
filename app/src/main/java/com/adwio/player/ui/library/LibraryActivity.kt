package com.adwio.player.ui.library

import android.content.Intent
import android.os.Bundle
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.common.MediaItem
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.adwio.player.data.FavoritesStore
import com.adwio.player.data.AppSettings
import com.adwio.player.data.SessionStore
import com.adwio.player.data.RecentChannelsStore
import com.adwio.player.data.XtreamClient
import com.adwio.player.data.M3uClient
import com.adwio.player.data.M3uCache
import com.adwio.player.data.model.CategoryModel
import com.adwio.player.data.model.MediaItemModel
import com.adwio.player.data.model.MediaType
import com.adwio.player.databinding.ActivityLibraryBinding
import com.adwio.player.ui.BaseFullscreenActivity
import com.adwio.player.ui.home.CategoryAdapter
import com.adwio.player.ui.home.MediaAdapter
import com.adwio.player.ui.home.PosterAdapter
import com.adwio.player.ui.player.PlayerActivity
import com.adwio.player.ui.player.ChannelNavigator
import com.adwio.player.ui.player.LiveCatalog
import com.adwio.player.ui.details.MovieDetailsActivity
import com.adwio.player.ui.details.SeriesDetailsActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LibraryActivity : BaseFullscreenActivity() {
    companion object {
        private const val RECENTLY_ADDED_ID = "__recent_30__"
        private const val RECENT_LIVE_ID = "__recent_live__"
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
    private lateinit var store: SessionStore
    private lateinit var favorites: FavoritesStore
    private lateinit var recentChannels: RecentChannelsStore
    private lateinit var categoryAdapter: CategoryAdapter
    private lateinit var liveAdapter: MediaAdapter
    private lateinit var posterAdapter: PosterAdapter
    private var allItems: List<MediaItemModel> = emptyList()
    private var selectedCategory = ""
    private var previewPlayer: ExoPlayer? = null
    private var previewJob: Job? = null
    private var activePreviewLiveId: String? = null
    private val type: MediaType by lazy { runCatching { MediaType.valueOf(intent.getStringExtra(EXTRA_TYPE) ?: "LIVE") }.getOrDefault(MediaType.LIVE) }
    private val categoryPrefs by lazy { getSharedPreferences("adwio_library_state", MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityLibraryBinding.inflate(layoutInflater)
        setContentView(b.root)
        store = SessionStore(this)
        favorites = FavoritesStore(this)
        recentChannels = RecentChannelsStore(this)
        liveAdapter = MediaAdapter(favorites, ::openItem, ::previewLive)
        posterAdapter = PosterAdapter(favorites, ::openItem)
        categoryAdapter = CategoryAdapter { applyCategory(it) }
        b.categoryRecycler.layoutManager = LinearLayoutManager(this)
        b.categoryRecycler.adapter = categoryAdapter
        b.backButton.setOnClickListener { finish() }
        b.searchButton.visibility = android.view.View.GONE
        b.favoritesButton.visibility = android.view.View.GONE
        b.recentButton.visibility = if (type == MediaType.LIVE) android.view.View.VISIBLE else android.view.View.GONE
        b.recentButton.setOnClickListener { showRecentChannels() }
        b.livePreviewPanel.visibility = if (type == MediaType.LIVE) android.view.View.VISIBLE else android.view.View.GONE
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
        b.titleText.text = when(type) { MediaType.LIVE -> getString(com.adwio.player.R.string.live_title); MediaType.MOVIE -> getString(com.adwio.player.R.string.movies_title); MediaType.SERIES -> getString(com.adwio.player.R.string.series_title) }
        b.subtitleText.text = "جاري تحميل المحتوى…"
        b.contentRecycler.alpha = 0.35f
        b.categoryRecycler.alpha = 0.35f
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { runCatching {
                if (session.server.id == "m3u") {
                    val all = m3uCache.loadFast(session.server.baseUrl, 700)
                    val items = all.filter { it.type == type }
                    m3u.categories(items, type) to items
                } else {
                    var cats = api.loadCategories(session, type)
                    var items = when(type) {
                        MediaType.LIVE -> api.loadLive(session)
                        MediaType.MOVIE -> api.loadMovies(session)
                        MediaType.SERIES -> api.loadSeries(session)
                    }

                    if (items.isEmpty()) {
                        cats = api.loadCategories(session, type)
                        items = when(type) {
                            MediaType.LIVE -> api.loadLive(session)
                            MediaType.MOVIE -> api.loadMovies(session)
                            MediaType.SERIES -> api.loadSeries(session)
                        }
                    }
                    cats to items
                }
            }}
            result.onSuccess { (cats, items) ->
                b.contentRecycler.alpha = 1f
                b.categoryRecycler.alpha = 1f

                if (items.isEmpty()) {
                    allItems = emptyList()
                    categoryAdapter.submit(cats)
                    submit(emptyList())
                    b.subtitleText.text = "لم يتم تحميل المحتوى • استخدم تحديث المحتوى ثم حاول مرة أخرى"
                    return@onSuccess
                }

                allItems = items
                val totalLabel = java.text.NumberFormat.getIntegerInstance().format(items.size)
                val allNamed = cats.map {
                    if (it.id.isBlank()) it.copy(name = "ALL ($totalLabel)") else it
                }
                val tools = mutableListOf(CategoryModel(SEARCH_ID, getString(com.adwio.player.R.string.search)))
                if (type == MediaType.LIVE) {
                    tools += CategoryModel(RECENT_LIVE_ID, "أضيف حديثًا")
                } else {
                    tools += CategoryModel(RECENTLY_ADDED_ID, getString(com.adwio.player.R.string.recently_added_30))
                }
                tools += CategoryModel(FAVORITES_ID, getString(com.adwio.player.R.string.favorites))
                val displayCategories = tools + allNamed

                if (type == MediaType.LIVE) {
                    selectedCategory = RECENT_LIVE_ID
                    LiveCatalog.setData(allNamed, items, "")
                    categoryAdapter.submit(displayCategories)
                    val latest = recentlyAdded(items).take(40)
                    val initial = if (latest.isNotEmpty()) latest else {
                        val firstReal = allNamed.firstOrNull { it.id.isNotBlank() }
                        if (firstReal == null) items.take(40) else items.filter { it.categoryId == firstReal.id }.take(40)
                    }
                    submit(initial)
                    b.subtitleText.text = "أضيف حديثًا"
                } else {
                    categoryAdapter.submit(displayCategories)
                    selectedCategory = RECENTLY_ADDED_ID
                    val recent = recentlyAdded(items)
                    submit(recent)
                    b.subtitleText.text = getString(com.adwio.player.R.string.recently_added)
                }
                if (session.server.id == "m3u" && !m3uCache.hasCache(session.server.baseUrl)) {
                    lifecycleScope.launch(Dispatchers.IO) {
                        runCatching { m3uCache.warm(session.server.baseUrl) }
                    }
                }

                if (intent.getBooleanExtra(EXTRA_FAVORITES, false)) showFavorites()
                else if (intent.getBooleanExtra(EXTRA_SEARCH, false)) showSearch()
            }.onFailure {
                b.contentRecycler.alpha = 1f
                b.categoryRecycler.alpha = 1f
                b.subtitleText.text = "تعذر تحميل المحتوى • حاول مرة أخرى"
            }
        }
    }

    private fun submit(items: List<MediaItemModel>) {
        if (type == MediaType.LIVE) liveAdapter.submit(items) else posterAdapter.submit(items)
    }

    private fun applyCategory(c: CategoryModel) {
        selectedCategory = c.id
        if (type == MediaType.LIVE) {
            LiveCatalog.selectCategory(c.id)
            if (c.id.isNotBlank()) categoryPrefs.edit().putString("last_category_${type.name}", c.id).apply()
        }
        if (c.id == SEARCH_ID) { showSearch(); return }
        if (c.id == FAVORITES_ID) { showFavorites(); return }
        val list = when {
            c.id == RECENTLY_ADDED_ID -> recentlyAdded(allItems)
            c.id == RECENT_LIVE_ID -> recentlyAdded(allItems).take(40)
            c.id.isBlank() -> allItems
            else -> allItems.filter { it.categoryId == c.id }
        }
        submit(list)
        b.subtitleText.text = c.name
    }

    private fun recentlyAdded(items: List<MediaItemModel>): List<MediaItemModel> {
        val withDates = items.filter { it.addedAt > 0L }
        return if (withDates.isNotEmpty()) {
            withDates.sortedByDescending { it.addedAt }.take(30)
        } else {
            items.takeLast(30).asReversed()
        }
    }

    private fun showSearch() {
        val input = EditText(this).apply { hint = getString(com.adwio.player.R.string.search_hint); setSingleLine() }
        AlertDialog.Builder(this).setTitle(com.adwio.player.R.string.search).setView(input)
            .setPositiveButton(com.adwio.player.R.string.search) { _, _ ->
                val q = input.text.toString().trim()
                val base = if (selectedCategory.isBlank()) allItems else allItems.filter { it.categoryId == selectedCategory }
                val list = if (q.isBlank()) base else base.filter { it.name.contains(q, true) }
                submit(list); b.subtitleText.text = getString(com.adwio.player.R.string.results_count, list.size)
            }.setNegativeButton(android.R.string.cancel, null).show()
    }

    private fun showFavorites() {
        val list = allItems.filter { favorites.isFavorite(it.id, it.type) }
        submit(list)
        b.subtitleText.text = getString(com.adwio.player.R.string.favorites_count, list.size)
    }

    private fun showRecentChannels() {
        if (type != MediaType.LIVE) return
        val recentIds = recentChannels.list().map { it.id }.toSet()
        val byId = allItems.associateBy { it.id }
        val list = recentChannels.list().mapNotNull { recent -> byId[recent.id] ?: recent }
        submit(list)
        b.subtitleText.text = getString(com.adwio.player.R.string.recent_channels_count, list.size)
    }

    private fun previewLive(item: MediaItemModel) {
        if (type != MediaType.LIVE || activePreviewLiveId == item.id) return
        previewJob?.cancel()
        b.previewChannelName.text = item.name
        previewJob = lifecycleScope.launch {
            delay(500)
            val player = previewPlayer ?: ExoPlayer.Builder(this@LibraryActivity).build().also {
                previewPlayer = it
                b.previewPlayer.player = it
            }
            player.setMediaItem(MediaItem.fromUri(item.streamUrl))
            player.prepare()
            player.playWhenReady = true
            player.volume = 0f
        }
    }

    private fun activateLivePreview(item: MediaItemModel) {
        previewJob?.cancel()
        activePreviewLiveId = item.id
        b.previewChannelName.text = item.name
        val player = previewPlayer ?: ExoPlayer.Builder(this).build().also {
            previewPlayer = it
            b.previewPlayer.player = it
        }
        player.setMediaItem(MediaItem.fromUri(item.streamUrl))
        player.prepare()
        player.playWhenReady = true
        player.volume = 1f
        b.previewPlayer.requestFocus()
    }

    override fun onStop() {
        previewJob?.cancel()
        previewPlayer?.release()
        previewPlayer = null
        b.previewPlayer.player = null
        super.onStop()
    }

    private fun openItem(item: MediaItemModel) {
        when (item.type) {
            MediaType.SERIES -> {
                val session = store.load()
                if (session?.server?.id == "m3u" && item.streamUrl.isNotBlank()) {
                    startActivity(Intent(this, PlayerActivity::class.java).apply {
                        putExtra("url", item.streamUrl); putExtra("title", item.name); putExtra("id", "SERIES:${item.id}"); putExtra("type", MediaType.SERIES.name)
                    })
                } else startActivity(Intent(this, SeriesDetailsActivity::class.java).apply {
                    putExtra(SeriesDetailsActivity.EXTRA_ID, item.id); putExtra(SeriesDetailsActivity.EXTRA_TITLE, item.name); putExtra(SeriesDetailsActivity.EXTRA_IMAGE, item.logoUrl); putExtra(SeriesDetailsActivity.EXTRA_META, item.meta)
                })
            }
            MediaType.MOVIE -> startActivity(Intent(this, MovieDetailsActivity::class.java).apply {
                putExtra(MovieDetailsActivity.EXTRA_ID, item.id); putExtra(MovieDetailsActivity.EXTRA_TITLE, item.name); putExtra(MovieDetailsActivity.EXTRA_URL, item.streamUrl); putExtra(MovieDetailsActivity.EXTRA_IMAGE, item.logoUrl); putExtra(MovieDetailsActivity.EXTRA_META, item.meta)
            })
            MediaType.LIVE -> {
                recentChannels.add(item)
                if (activePreviewLiveId != item.id) {
                    activateLivePreview(item)
                    b.subtitleText.text = item.name
                    return
                }
                LiveCatalog.setData(LiveCatalog.categories().ifEmpty { listOf(CategoryModel("", "All")) }, allItems, selectedCategory)
                val visible = if (selectedCategory.isBlank()) allItems else allItems.filter { it.categoryId == selectedCategory }
                ChannelNavigator.setQueue(visible, item.id)
                startActivity(Intent(this, PlayerActivity::class.java).apply {
                    putExtra("url", item.streamUrl)
                    putExtra("title", item.name)
                    putExtra("id", "LIVE:${item.id}")
                    putExtra("type", MediaType.LIVE.name)
                })
            }
        }
    }

    private fun showSeriesEpisodes(series: MediaItemModel) {
        val session = store.load() ?: return
        b.subtitleText.text = "Loading ${series.name}…"
        lifecycleScope.launch {
            val eps = withContext(Dispatchers.IO) { runCatching { api.loadSeriesEpisodes(session, series.id) }.getOrDefault(emptyList()) }
            if (eps.isEmpty()) { b.subtitleText.text = getString(com.adwio.player.R.string.no_episodes); return@launch }
            val seasons = eps.map { it.season }.distinct().sorted()
            AlertDialog.Builder(this@LibraryActivity).setTitle(series.name)
                .setItems(seasons.map { "Season $it" }.toTypedArray()) { _, which ->
                    val season = seasons[which]
                    val seasonEps = eps.filter { it.season == season }
                    AlertDialog.Builder(this@LibraryActivity).setTitle("${series.name} • Season $season")
                        .setItems(seasonEps.map { "E${it.episodeNumber}  ${it.title}" }.toTypedArray()) { _, i ->
                            val ep = seasonEps[i]
                            startActivity(Intent(this@LibraryActivity, PlayerActivity::class.java).apply {
                                putExtra("url", ep.streamUrl); putExtra("title", "${series.name} • S${season}E${ep.episodeNumber}"); putExtra("id", "SERIES:${series.id}:${ep.id}")
                            })
                        }.show()
                }.show()
        }
    }
}
