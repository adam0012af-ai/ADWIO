package com.adwio.player.ui.library

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.adwio.player.data.FavoritesStore
import com.adwio.player.data.SessionStore
import com.adwio.player.data.XtreamClient
import com.adwio.player.data.model.MediaItemModel
import com.adwio.player.data.model.MediaType
import com.adwio.player.databinding.ActivityGlobalSearchBinding
import com.adwio.player.ui.BaseFullscreenActivity
import com.adwio.player.ui.details.MovieDetailsActivity
import com.adwio.player.ui.details.SeriesDetailsActivity
import com.adwio.player.ui.home.MediaAdapter
import com.adwio.player.ui.player.PlayerActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class GlobalSearchActivity : BaseFullscreenActivity() {
    private lateinit var b: ActivityGlobalSearchBinding
    private val api = XtreamClient()
    private lateinit var adapter: MediaAdapter
    private var all = emptyList<MediaItemModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityGlobalSearchBinding.inflate(layoutInflater)
        setContentView(b.root)
        adapter = MediaAdapter(FavoritesStore(this), ::open)
        b.resultsRecycler.layoutManager = LinearLayoutManager(this)
        b.resultsRecycler.adapter = adapter
        b.backButton.setOnClickListener { finish() }
        b.searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = filter(s?.toString().orEmpty())
            override fun afterTextChanged(s: Editable?) = Unit
        })
        load()
    }

    private fun load() {
        val session = SessionStore(this).load() ?: return finish()
        b.statusText.text = getString(com.adwio.player.R.string.loading_content)
        lifecycleScope.launch {
            val loaded = withContext(Dispatchers.IO) {
                runCatching { api.loadLive(session) + api.loadMovies(session) + api.loadSeries(session) }.getOrDefault(emptyList())
            }
            all = loaded
            filter(b.searchInput.text?.toString().orEmpty())
            b.searchInput.requestFocus()
        }
    }

    private fun filter(query: String) {
        val q = query.trim()
        val result = if (q.isBlank()) emptyList() else all.filter { it.name.contains(q, ignoreCase = true) }.take(250)
        adapter.submit(result)
        b.statusText.text = if (q.isBlank()) getString(com.adwio.player.R.string.search_all_hint) else getString(com.adwio.player.R.string.results_count, result.size)
    }

    private fun open(item: MediaItemModel) {
        when (item.type) {
            MediaType.LIVE -> startActivity(Intent(this, PlayerActivity::class.java).apply { putExtra("url", item.streamUrl); putExtra("title", item.name); putExtra("id", "LIVE:${item.id}") })
            MediaType.MOVIE -> startActivity(Intent(this, MovieDetailsActivity::class.java).apply { putExtra(MovieDetailsActivity.EXTRA_ID,item.id); putExtra(MovieDetailsActivity.EXTRA_TITLE,item.name); putExtra(MovieDetailsActivity.EXTRA_URL,item.streamUrl); putExtra(MovieDetailsActivity.EXTRA_IMAGE,item.logoUrl); putExtra(MovieDetailsActivity.EXTRA_META,item.meta) })
            MediaType.SERIES -> startActivity(Intent(this, SeriesDetailsActivity::class.java).apply { putExtra(SeriesDetailsActivity.EXTRA_ID,item.id); putExtra(SeriesDetailsActivity.EXTRA_TITLE,item.name); putExtra(SeriesDetailsActivity.EXTRA_IMAGE,item.logoUrl); putExtra(SeriesDetailsActivity.EXTRA_META,item.meta) })
        }
    }
}
