package com.adwio.player.ui.favorites

import android.content.Intent
import android.os.Bundle
import com.adwio.player.data.model.MediaType
import com.adwio.player.databinding.ActivityFavoritesBinding
import com.adwio.player.ui.BaseFullscreenActivity
import com.adwio.player.ui.library.LibraryActivity

class FavoritesActivity : BaseFullscreenActivity() {
    private lateinit var b: ActivityFavoritesBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityFavoritesBinding.inflate(layoutInflater)
        setContentView(b.root)
        b.backButton.setOnClickListener { finish() }
        b.liveFavorites.setOnClickListener { open(MediaType.LIVE) }
        b.movieFavorites.setOnClickListener { open(MediaType.MOVIE) }
        b.seriesFavorites.setOnClickListener { open(MediaType.SERIES) }
        b.liveFavorites.requestFocus()
    }
    private fun open(type: MediaType) {
        startActivity(Intent(this, LibraryActivity::class.java).apply {
            putExtra(LibraryActivity.EXTRA_TYPE, type.name)
            putExtra(LibraryActivity.EXTRA_FAVORITES, true)
        })
    }
}
