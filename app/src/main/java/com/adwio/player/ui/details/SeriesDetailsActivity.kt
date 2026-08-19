package com.adwio.player.ui.details

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import androidx.lifecycle.lifecycleScope
import com.adwio.player.R
import com.adwio.player.data.SessionStore
import com.adwio.player.data.XtreamClient
import com.adwio.player.data.model.EpisodeModel
import com.adwio.player.data.model.MediaType
import com.adwio.player.databinding.ActivitySeriesDetailsBinding
import com.adwio.player.ui.BaseFullscreenActivity
import com.adwio.player.ui.player.PlayerActivity
import com.squareup.picasso.Picasso
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SeriesDetailsActivity : BaseFullscreenActivity() {
    companion object { const val EXTRA_ID="id"; const val EXTRA_TITLE="title"; const val EXTRA_IMAGE="image"; const val EXTRA_META="meta" }
    private lateinit var b: ActivitySeriesDetailsBinding
    private val api = XtreamClient()
    private var episodes: List<EpisodeModel> = emptyList()
    private var seriesId = ""
    private var title = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivitySeriesDetailsBinding.inflate(layoutInflater)
        setContentView(b.root)
        seriesId = intent.getStringExtra(EXTRA_ID).orEmpty(); title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        b.titleText.text = title; b.metaText.text = intent.getStringExtra(EXTRA_META).orEmpty(); b.backButton.setOnClickListener { finish() }
        intent.getStringExtra(EXTRA_IMAGE)?.takeIf { it.isNotBlank() }?.let { Picasso.get().load(it).placeholder(R.drawable.ic_adwio).error(R.drawable.ic_adwio).fit().centerInside().into(b.posterImage) }
        loadEpisodes()
    }

    private fun loadEpisodes() {
        val session = SessionStore(this).load() ?: return finish()
        b.loading.visibility = View.VISIBLE
        lifecycleScope.launch {
            episodes = withContext(Dispatchers.IO) { runCatching { api.loadSeriesEpisodes(session, seriesId) }.getOrDefault(emptyList()) }
            b.loading.visibility = View.GONE
            if (episodes.isEmpty()) { b.statusText.text = getString(R.string.no_episodes); return@launch }
            val seasons = episodes.map { it.season }.distinct().sorted()
            b.seasonSpinner.adapter = ArrayAdapter(this@SeriesDetailsActivity, android.R.layout.simple_spinner_dropdown_item, seasons.map { getString(R.string.season_number, it) })
            b.seasonSpinner.setSelection(0)
            b.seasonSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
                override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) { renderSeason(seasons[position]) }
            }
        }
    }

    private fun renderSeason(season: Int) {
        b.episodesContainer.removeAllViews()
        val list = episodes.filter { it.season == season }
        list.forEach { ep ->
            val btn = Button(this).apply {
                text = "E${ep.episodeNumber.toString().padStart(2,'0')}  ${ep.title}"
                isAllCaps = false
                setTextColor(getColor(R.color.adwio_text))
                setBackgroundResource(R.drawable.bg_focus)
                setPadding(18,0,18,0)
                setOnClickListener {
                    val currentIndex = episodes.indexOfFirst { it.id == ep.id }
                    val next = episodes.getOrNull(currentIndex + 1)
                    startActivity(Intent(this@SeriesDetailsActivity, PlayerActivity::class.java).apply {
                        putExtra("url", ep.streamUrl)
                        putExtra("title", "$title • S${ep.season}E${ep.episodeNumber}")
                        putExtra("id", "$seriesId:${ep.id}")
                        putExtra("type", MediaType.SERIES.name)
                        next?.let { n ->
                            putExtra("next_url", n.streamUrl)
                            putExtra("next_title", "$title • S${n.season}E${n.episodeNumber}")
                            putExtra("next_id", "$seriesId:${n.id}")
                        }
                    })
                }
            }
            b.episodesContainer.addView(btn, android.widget.LinearLayout.LayoutParams(android.widget.LinearLayout.LayoutParams.MATCH_PARENT, 58.dp()).apply { bottomMargin = 8.dp() })
        }
        list.firstOrNull()?.let { if (b.episodesContainer.childCount > 0) b.episodesContainer.getChildAt(0).requestFocus() }
    }

    private fun Int.dp(): Int = (this * resources.displayMetrics.density).toInt()
}
