package com.adwio.player.ui.home

import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.adwio.player.data.FavoritesStore
import com.adwio.player.data.SessionStore
import com.adwio.player.data.XtreamClient
import com.adwio.player.data.model.CategoryModel
import com.adwio.player.data.model.EpisodeModel
import com.adwio.player.data.model.MediaItemModel
import com.adwio.player.data.model.MediaType
import com.adwio.player.databinding.ActivityHomeBinding
import com.adwio.player.ui.BaseFullscreenActivity
import com.adwio.player.ui.player.PlayerActivity
import com.adwio.player.ui.playlist.PlaylistActivity
import com.adwio.player.ui.settings.SettingsActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HomeActivity : BaseFullscreenActivity() {
    private lateinit var b: ActivityHomeBinding
    private val api = XtreamClient()
    private lateinit var liveAdapter: MediaAdapter
    private lateinit var posterAdapter: PosterAdapter
    private lateinit var categoryAdapter: CategoryAdapter
    private lateinit var store: SessionStore
    private lateinit var fav: FavoritesStore

    private var allCache: List<MediaItemModel> = emptyList()
    private var currentType: MediaType = MediaType.LIVE
    private var selectedCategoryId: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(b.root)

        store = SessionStore(this)
        fav = FavoritesStore(this)

        liveAdapter = MediaAdapter(fav) { openItem(it) }
        posterAdapter = PosterAdapter(fav) { openItem(it) }
        categoryAdapter = CategoryAdapter { category ->
            selectedCategoryId = category.id
            applyCategory(category)
        }

        b.categoryRecycler.layoutManager = LinearLayoutManager(this)
        b.categoryRecycler.adapter = categoryAdapter

        b.menuLive.setOnClickListener { load(MediaType.LIVE) }
        b.menuMovies.setOnClickListener { load(MediaType.MOVIE) }
        b.menuSeries.setOnClickListener { load(MediaType.SERIES) }
        b.menuSearch.setOnClickListener { showSearch() }
        b.menuFavorites.setOnClickListener { showFavorites() }
        b.menuSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        b.menuLive.requestFocus()
        load(MediaType.LIVE)
    }

    private fun load(type: MediaType) {
        val session = store.load() ?: return logout()
        currentType = type
        selectedCategoryId = ""

        b.titleText.text = when (type) {
            MediaType.LIVE -> "Live TV"
            MediaType.MOVIE -> "Movies"
            MediaType.SERIES -> "Series"
        }
        b.subtitleText.text = "Loading categories and content…"

        configureContentLayout(type)

        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val categories = api.loadCategories(session, type)
                    val items = when (type) {
                        MediaType.LIVE -> api.loadLive(session)
                        MediaType.MOVIE -> api.loadMovies(session)
                        MediaType.SERIES -> api.loadSeries(session)
                    }
                    categories to items
                }
            }

            result.onSuccess { (categories, items) ->
                allCache = items
                categoryAdapter.submit(categories)
                submitContent(items)
                b.subtitleText.text = "${items.size} items • ${categories.size - 1} categories"
            }.onFailure { error ->
                allCache = emptyList()
                categoryAdapter.submit(listOf(CategoryModel("", "All")))
                submitContent(emptyList())
                b.subtitleText.text = "Unable to load content • ${error.message ?: "network error"}"
            }
        }
    }

    private fun configureContentLayout(type: MediaType) {
        if (type == MediaType.LIVE) {
            b.contentRecycler.layoutManager = LinearLayoutManager(this)
            b.contentRecycler.adapter = liveAdapter
        } else {
            val widthDp = resources.displayMetrics.widthPixels / resources.displayMetrics.density
            val spanCount = if (widthDp >= 1000f) 8 else 6
            b.contentRecycler.layoutManager = GridLayoutManager(this, spanCount)
            b.contentRecycler.adapter = posterAdapter
        }
    }

    private fun submitContent(items: List<MediaItemModel>) {
        if (currentType == MediaType.LIVE) {
            liveAdapter.submit(items)
        } else {
            posterAdapter.submit(items)
        }
    }

    private fun applyCategory(category: CategoryModel) {
        val filtered = if (category.id.isBlank()) {
            allCache
        } else {
            allCache.filter { it.categoryId == category.id }
        }
        submitContent(filtered)
        b.subtitleText.text = "${category.name} • ${filtered.size} items"
        b.contentRecycler.scrollToPosition(0)
    }

    private fun showSearch() {
        val input = EditText(this).apply {
            hint = "Search current section"
            setSingleLine()
        }
        AlertDialog.Builder(this)
            .setTitle("Search")
            .setView(input)
            .setPositiveButton("Search") { _, _ ->
                val q = input.text.toString().trim()
                val base = if (selectedCategoryId.isBlank()) {
                    allCache
                } else {
                    allCache.filter { it.categoryId == selectedCategoryId }
                }
                val matches = if (q.isBlank()) base else base.filter {
                    it.name.contains(q, ignoreCase = true)
                }
                submitContent(matches)
                b.titleText.text = "Search"
                b.subtitleText.text = "${matches.size} results"
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showFavorites() {
        val list = allCache.filter { fav.isFavorite(it.id) }
        submitContent(list)
        b.titleText.text = "Favorites"
        b.subtitleText.text = "${list.size} saved"
    }

    private fun openItem(item: MediaItemModel) {
        if (item.type == MediaType.SERIES) {
            openSeries(item)
            return
        }
        play(item.streamUrl, item.name, "${item.type}:${item.id}")
    }

    private fun openSeries(series: MediaItemModel) {
        val session = store.load() ?: return logout()
        val loading = AlertDialog.Builder(this)
            .setTitle(series.name)
            .setMessage("Loading seasons…")
            .setCancelable(false)
            .create()
        loading.show()

        lifecycleScope.launch {
            val episodes = withContext(Dispatchers.IO) {
                runCatching { api.loadSeriesEpisodes(session, series.id) }.getOrDefault(emptyList())
            }
            loading.dismiss()

            if (episodes.isEmpty()) {
                AlertDialog.Builder(this@HomeActivity)
                    .setTitle(series.name)
                    .setMessage("No episodes were returned by this server.")
                    .setPositiveButton("OK", null)
                    .show()
                return@launch
            }
            showSeasonPicker(series, episodes)
        }
    }

    private fun showSeasonPicker(series: MediaItemModel, episodes: List<EpisodeModel>) {
        val seasons = episodes.map { it.season }.distinct().sorted()
        val labels = seasons.map { "Season $it" }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("${series.name} • Select season")
            .setItems(labels) { _, which ->
                val season = seasons[which]
                showEpisodePicker(series, season, episodes.filter { it.season == season })
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun showEpisodePicker(
        series: MediaItemModel,
        season: Int,
        episodes: List<EpisodeModel>
    ) {
        val labels = episodes.map {
            "E${it.episodeNumber.toString().padStart(2, '0')}  ${it.title}"
        }
        val listAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, labels)

        AlertDialog.Builder(this)
            .setTitle("${series.name} • Season $season")
            .setAdapter(listAdapter) { dialog, which ->
                val ep = episodes[which]
                dialog.dismiss()
                play(
                    ep.streamUrl,
                    "${series.name} • S${season}E${ep.episodeNumber}",
                    "SERIES:${series.id}:${ep.id}"
                )
            }
            .setNegativeButton("Seasons") { _, _ ->
                openSeries(series)
            }
            .show()
    }

    private fun play(url: String, title: String, id: String) {
        if (url.isBlank()) return
        startActivity(Intent(this, PlayerActivity::class.java).apply {
            putExtra("url", url)
            putExtra("title", title)
            putExtra("id", id)
        })
    }

    private fun logout() {
        store.clear()
        startActivity(
            Intent(this, PlaylistActivity::class.java).addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            )
        )
        finish()
    }
}
