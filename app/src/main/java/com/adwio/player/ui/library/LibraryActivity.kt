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
import com.adwio.player.ui.details.MovieDetailsActivity
import com.adwio.player.ui.details.SeriesDetailsActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LibraryActivity : BaseFullscreenActivity() {
    companion object {
        const val EXTRA_TYPE = "type"
        const val EXTRA_SEARCH = "search"
        const val EXTRA_FAVORITES = "favorites"
    }
    private lateinit var b: ActivityLibraryBinding
    private val api = XtreamClient()
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
    private val type: MediaType by lazy { runCatching { MediaType.valueOf(intent.getStringExtra(EXTRA_TYPE) ?: "LIVE") }.getOrDefault(MediaType.LIVE) }

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
        b.searchButton.setOnClickListener { showSearch() }
        b.favoritesButton.setOnClickListener { showFavorites() }
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
            val spans = when (AppSettings(this).gridDensity) { "6" -> 6; "8" -> 8; else -> if (widthDp >= 1000f) 8 else 6 }
            b.contentRecycler.layoutManager = GridLayoutManager(this, spans)
            b.contentRecycler.adapter = posterAdapter
        }
    }

    private fun load() {
        val session = store.load() ?: return finish()
        b.titleText.text = when(type) { MediaType.LIVE -> "LIVE TV"; MediaType.MOVIE -> "MOVIES"; MediaType.SERIES -> "SERIES" }
        b.subtitleText.text = getString(com.adwio.player.R.string.loading_content)
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { runCatching {
                val cats = api.loadCategories(session, type)
                val items = when(type) { MediaType.LIVE -> api.loadLive(session); MediaType.MOVIE -> api.loadMovies(session); MediaType.SERIES -> api.loadSeries(session) }
                cats to items
            }}
            result.onSuccess { (cats, items) ->
                allItems = items
                categoryAdapter.submit(cats)
                submit(items)
                b.subtitleText.text = "${items.size} items"
                if (intent.getBooleanExtra(EXTRA_FAVORITES, false)) showFavorites()
                else if (intent.getBooleanExtra(EXTRA_SEARCH, false)) showSearch()
            }.onFailure { b.subtitleText.text = getString(com.adwio.player.R.string.load_failed) }
        }
    }

    private fun submit(items: List<MediaItemModel>) {
        if (type == MediaType.LIVE) liveAdapter.submit(items) else posterAdapter.submit(items)
    }

    private fun applyCategory(c: CategoryModel) {
        selectedCategory = c.id
        val list = if (c.id.isBlank()) allItems else allItems.filter { it.categoryId == c.id }
        submit(list)
        b.subtitleText.text = "${c.name} • ${list.size}"
    }

    private fun showSearch() {
        val input = EditText(this).apply { hint = getString(com.adwio.player.R.string.search_hint); setSingleLine() }
        AlertDialog.Builder(this).setTitle(com.adwio.player.R.string.search).setView(input)
            .setPositiveButton(com.adwio.player.R.string.search) { _, _ ->
                val q = input.text.toString().trim()
                val base = if (selectedCategory.isBlank()) allItems else allItems.filter { it.categoryId == selectedCategory }
                val list = if (q.isBlank()) base else base.filter { it.name.contains(q, true) }
                submit(list); b.subtitleText.text = "${list.size} results"
            }.setNegativeButton(android.R.string.cancel, null).show()
    }

    private fun showFavorites() {
        val list = allItems.filter { favorites.isFavorite(it.id, it.type) }
        submit(list)
        b.subtitleText.text = "${list.size} favorites"
    }

    private fun showRecentChannels() {
        if (type != MediaType.LIVE) return
        val recentIds = recentChannels.list().map { it.id }.toSet()
        val byId = allItems.associateBy { it.id }
        val list = recentChannels.list().mapNotNull { recent -> byId[recent.id] ?: recent }
        submit(list)
        b.subtitleText.text = "${list.size} recent channels"
    }

    private fun previewLive(item: MediaItemModel) {
        if (type != MediaType.LIVE) return
        previewJob?.cancel()
        b.previewChannelName.text = item.name
        b.previewNow.text = getString(com.adwio.player.R.string.epg_loading)
        b.previewNext.text = ""
        previewJob = lifecycleScope.launch {
            delay(650)
            val session = store.load() ?: return@launch
            val epg = withContext(Dispatchers.IO) { runCatching { api.loadShortEpg(session, item.id, 2) }.getOrDefault(emptyList()) }
            if (epg.isEmpty()) {
                b.previewNow.text = getString(com.adwio.player.R.string.epg_unavailable)
                b.previewNext.text = ""
            } else {
                b.previewNow.text = "Now • ${epg[0].title}"
                b.previewNext.text = epg.getOrNull(1)?.let { "Next • ${it.title}" }.orEmpty()
            }
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

    override fun onStop() {
        previewJob?.cancel()
        previewPlayer?.release()
        previewPlayer = null
        b.previewPlayer.player = null
        super.onStop()
    }

    private fun openItem(item: MediaItemModel) {
        when (item.type) {
            MediaType.SERIES -> startActivity(Intent(this, SeriesDetailsActivity::class.java).apply {
                putExtra(SeriesDetailsActivity.EXTRA_ID, item.id); putExtra(SeriesDetailsActivity.EXTRA_TITLE, item.name); putExtra(SeriesDetailsActivity.EXTRA_IMAGE, item.logoUrl); putExtra(SeriesDetailsActivity.EXTRA_META, item.meta)
            })
            MediaType.MOVIE -> startActivity(Intent(this, MovieDetailsActivity::class.java).apply {
                putExtra(MovieDetailsActivity.EXTRA_ID, item.id); putExtra(MovieDetailsActivity.EXTRA_TITLE, item.name); putExtra(MovieDetailsActivity.EXTRA_URL, item.streamUrl); putExtra(MovieDetailsActivity.EXTRA_IMAGE, item.logoUrl); putExtra(MovieDetailsActivity.EXTRA_META, item.meta)
            })
            MediaType.LIVE -> {
                recentChannels.add(item)
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
