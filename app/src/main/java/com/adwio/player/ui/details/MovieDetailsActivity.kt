package com.adwio.player.ui.details

import android.content.Intent
import android.os.Bundle
import com.adwio.player.R
import com.adwio.player.data.FavoritesStore
import com.adwio.player.data.model.MediaType
import com.adwio.player.databinding.ActivityMovieDetailsBinding
import com.adwio.player.ui.BaseFullscreenActivity
import com.adwio.player.ui.player.PlayerActivity
import com.squareup.picasso.Picasso

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
    private var id = ""

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
        if (image.isNotBlank()) Picasso.get().load(image).placeholder(R.drawable.ic_adwio).error(R.drawable.ic_adwio).fit().centerInside().into(b.posterImage)
        b.playButton.setOnClickListener {
            startActivity(Intent(this, PlayerActivity::class.java).apply {
                putExtra("url", url); putExtra("title", title); putExtra("id", id); putExtra("type", MediaType.MOVIE.name)
            })
        }
        fun refreshFav() { b.favoriteButton.text = if (fav.isFavorite(id, MediaType.MOVIE)) getString(R.string.remove_favorite) else getString(R.string.add_favorite) }
        refreshFav()
        b.favoriteButton.setOnClickListener { fav.toggle(id, MediaType.MOVIE); refreshFav() }
        b.backButton.setOnClickListener { finish() }
        b.playButton.requestFocus()
    }
}
