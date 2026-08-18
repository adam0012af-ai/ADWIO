package com.adwio.player.ui.home

import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.adwio.player.data.FavoritesStore
import com.adwio.player.data.SessionStore
import com.adwio.player.data.XtreamClient
import com.adwio.player.data.model.EpisodeModel
import com.adwio.player.data.model.MediaItemModel
import com.adwio.player.data.model.MediaType
import com.adwio.player.databinding.ActivityHomeBinding
import com.adwio.player.ui.player.PlayerActivity
import com.adwio.player.ui.playlist.PlaylistActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HomeActivity : AppCompatActivity() {
    private lateinit var b: ActivityHomeBinding
    private val api = XtreamClient()
    private lateinit var adapter: MediaAdapter
    private lateinit var store: SessionStore
    private lateinit var fav: FavoritesStore
    private var allCache: List<MediaItemModel> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(b.root)

        store = SessionStore(this)
        fav = FavoritesStore(this)
        adapter = MediaAdapter(fav) { openItem(it) }

        b.contentRecycler.layoutManager = LinearLayoutManager(this)
        b.contentRecycler.adapter = adapter

        b.menuLive.setOnClickListener { load(MediaType.LIVE) }
        b.menuMovies.setOnClickListener { load(MediaType.MOVIE) }
        b.menuSeries.setOnClickListener { load(MediaType.SERIES) }
        b.menuSearch.setOnClickListener { showSearch() }
        b.menuFavorites.setOnClickListener { showFavorites() }
        b.menuSettings.setOnClickListener { showSettings() }

        b.menuLive.requestFocus()
        load(MediaType.LIVE)
    }

    private fun load(type: MediaType) {
        val session = store.load() ?: return logout()
        b.titleText.text = when(type) {
            MediaType.LIVE -> "Live TV"
            MediaType.MOVIE -> "Movies"
            MediaType.SERIES -> "Series"
        }
        b.subtitleText.text = "Loading…"

        lifecycleScope.launch {
            val items = withContext(Dispatchers.IO) {
                when(type) {
                    MediaType.LIVE -> api.loadLive(session)
                    MediaType.MOVIE -> api.loadMovies(session)
                    MediaType.SERIES -> api.loadSeries(session)
                }
            }
            allCache = items
            adapter.submit(items)
            b.subtitleText.text = "${items.size} items"
        }
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
                b.titleText.text = "Search"
                val matches = if (q.isBlank()) allCache else allCache.filter {
                    it.name.contains(q, ignoreCase = true)
                }
                adapter.submit(matches)
                b.subtitleText.text = "${matches.size} results"
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showFavorites() {
        val list = allCache.filter { fav.isFavorite(it.id) }
        b.titleText.text = "Favorites"
        b.subtitleText.text = "${list.size} saved"
        adapter.submit(list)
    }

    private fun showSettings() {
        val session = store.load()
        AlertDialog.Builder(this)
            .setTitle("ADWIO Player")
            .setMessage(
                "Server: ${session?.server?.name ?: "-"}\n" +
                "Status: ${session?.status ?: "Active"}\n\n" +
                "The account server is selected automatically."
            )
            .setPositiveButton("OK", null)
            .setNegativeButton("Logout") { _, _ -> logout() }
            .show()
    }

    private fun openItem(item: MediaItemModel) {
        if (item.type == MediaType.SERIES) {
            openSeries(item)
            return
        }
        play(item.streamUrl, item.name)
    }

    private fun openSeries(series: MediaItemModel) {
        val session = store.load() ?: return logout()

        val loading = AlertDialog.Builder(this)
            .setTitle(series.name)
            .setMessage("Loading episodes…")
            .setCancelable(false)
            .create()

        loading.show()

        lifecycleScope.launch {
            val episodes = withContext(Dispatchers.IO) {
                api.loadSeriesEpisodes(session, series.id)
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

            showEpisodePicker(series.name, episodes)
        }
    }

    private fun showEpisodePicker(seriesName: String, episodes: List<EpisodeModel>) {
        val labels = episodes.map {
            "S${it.season.toString().padStart(2, '0')} • E${it.episodeNumber.toString().padStart(2, '0')}  ${it.title}"
        }
        val listAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, labels)

        AlertDialog.Builder(this)
            .setTitle(seriesName)
            .setAdapter(listAdapter) { dialog, which ->
                val ep = episodes[which]
                dialog.dismiss()
                play(ep.streamUrl, "$seriesName • S${ep.season}E${ep.episodeNumber}")
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun play(url: String, title: String) {
        if (url.isBlank()) return
        startActivity(Intent(this, PlayerActivity::class.java).apply {
            putExtra("url", url)
            putExtra("title", title)
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
