package com.adwio.player.ui.library

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.adwio.player.R
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
        val named = (if (cats.isEmpty()) listOf(CategoryModel("", getString(R.string.all))) else cats).map {
            if (it.id.isBlank()) it.copy(name = "${getString(R.string.all)} ($totalLabel)") else it
        }

        val tools = mutableListOf(CategoryModel(SEARCH_ID, getString(R.string.search)))
        if (type == MediaType.LIVE) {
            tools += CategoryModel(RECENT_LIVE_ID, getString(R.string.recently_added))
        } else {
            tools += CategoryModel(RECENTLY_ADDED_ID, getString(R.string.recently_added_30))
        }
        tools += CategoryModel(FAVORITES_ID, getString(R.string.favorites))
        categoryAdapter.submit(tools + named)

        if (type == MediaType.LIVE) {
            selectedCategory = RECENT_LIVE_ID
            LiveCatalog.setData(named, items, "")
            val latest = recentlyAdded(items).take(40)
            val first = if (latest.isNotEmpty()) latest else items.take(40)
            submit(first)
        } else {
            selectedCategory = RECENTLY_ADDED_ID
            submit(recentlyAdded(items))
        }
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
        val byId = allItems.associateBy { it.id }
        val list = recentChannels.list().mapNotNull { recent -> byId[recent.id] ?: recent }
        submit(list)
        b.subtitleText.text = getString(R.string.recent_channels_count, list.size)
    }

    private fun previewLive(item: MediaItemModel) {
        if (type == MediaType.LIVE) b.previewChannelName.text = item.name
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
        }
    }

    override fun onStop() {
        previewJob?.cancel()
        b.previewPlayer.player = null
        super.onStop()
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
                startActivity(Intent(this, PlayerActivity::class.java).apply {
                    putExtra("url", item.streamUrl)
                    putExtra("title", item.name)
                    putExtra("id", "LIVE:${item.id}")
                    putExtra("type", MediaType.LIVE.name)
                })
            }
        }
    }
}
