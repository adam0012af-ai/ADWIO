package com.adwio.player.ui.details

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.lifecycle.lifecycleScope
import com.adwio.player.R
import com.adwio.player.data.FavoritesStore
import com.adwio.player.data.SessionStore
import com.adwio.player.data.XtreamClient
import com.adwio.player.data.model.MediaType
import com.adwio.player.databinding.ActivityMovieDetailsBinding
import com.adwio.player.ui.BaseFullscreenActivity
import com.adwio.player.ui.player.PlayerActivity
import com.squareup.picasso.Picasso
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MovieDetailsActivity : BaseFullscreenActivity() {
    companion object {
        const val EXTRA_ID = "id"
        const val EXTRA_TITLE = "title"
        const val EXTRA_URL = "url"
        const val EXTRA_IMAGE = "image"
        const val EXTRA_META = "meta"
    }

    private lateinit var b: ActivityMovieDetailsBinding
    private lateinit var fav: FavoritesStore
    private val api = XtreamClient()
    private var id = ""
    private var trailerUrl: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityMovieDetailsBinding.inflate(layoutInflater)
        setContentView(b.root)
        fav = FavoritesStore(this)

        id = intent.getStringExtra(EXTRA_ID).orEmpty()
        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        val url = intent.getStringExtra(EXTRA_URL).orEmpty()
        val image = intent.getStringExtra(EXTRA_IMAGE).orEmpty()

        b.titleText.text = title
        b.metaText.text = intent.getStringExtra(EXTRA_META).orEmpty()
        b.infoText.text = getString(R.string.loading_info)
        b.trailerButton.visibility = View.GONE

        if (image.isNotBlank()) {
            Picasso.get().load(image)
                .placeholder(R.drawable.ic_adwio)
                .error(R.drawable.ic_adwio)
                .fit().centerCrop().into(b.posterImage)
        }

        b.playButton.setOnClickListener {
            startActivity(Intent(this, PlayerActivity::class.java).apply {
                putExtra("url", url)
                putExtra("title", title)
                putExtra("id", id)
                putExtra("type", MediaType.MOVIE.name)
            })
        }

        b.trailerButton.setOnClickListener {
            trailerUrl?.let(::openTrailer)
        }

        fun refreshFav() {
            b.favoriteButton.text =
                if (fav.isFavorite(id, MediaType.MOVIE)) getString(R.string.remove_favorite)
                else getString(R.string.add_favorite)
        }

        refreshFav()
        b.favoriteButton.setOnClickListener {
            fav.toggle(id, MediaType.MOVIE)
            refreshFav()
        }
        b.backButton.setOnClickListener { finish() }

        val session = SessionStore(this).load()
        if (session != null) lifecycleScope.launch {
            val info = withContext(Dispatchers.IO) { api.loadMovieInfo(session, id) }
            trailerUrl = info.trailerUrl
            b.trailerButton.visibility = if (trailerUrl.isNullOrBlank()) View.GONE else View.VISIBLE

            b.metaText.text = listOf(
                info.rating.takeIf { it.isNotBlank() }?.let { "★ $it" },
                info.genre.takeIf { it.isNotBlank() },
                info.releaseDate.takeIf { it.isNotBlank() },
                info.duration.takeIf { it.isNotBlank() }
            ).filterNotNull().joinToString("  •  ")

            b.infoText.text = buildString {
                if (info.plot.isNotBlank()) append(info.plot)
                if (info.director.isNotBlank()) append("\n\n${getString(R.string.director)}: ${info.director}")
                if (info.cast.isNotBlank()) append("\n${getString(R.string.cast)}: ${info.cast}")
            }.ifBlank { getString(R.string.no_extra_info) }
        }

        b.playButton.requestFocus()
    }

    private fun openTrailer(url: String) {
        val isYouTube = url.contains("youtube.com", true) || url.contains("youtu.be", true)
        if (isYouTube) {
            runCatching {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            }
        } else {
            startActivity(Intent(this, PlayerActivity::class.java).apply {
                putExtra("url", url)
                putExtra("title", getString(R.string.trailer))
                putExtra("id", "TRAILER:$id")
                putExtra("type", MediaType.MOVIE.name)
            })
        }
    }
}
