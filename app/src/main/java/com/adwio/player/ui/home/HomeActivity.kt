package com.adwio.player.ui.home

import android.content.Intent
import android.os.Bundle
import com.adwio.player.data.SessionStore
import com.adwio.player.data.model.MediaType
import com.adwio.player.databinding.ActivityHomeBinding
import com.adwio.player.ui.BaseFullscreenActivity
import com.adwio.player.ui.continuewatching.ContinueWatchingActivity
import com.adwio.player.ui.favorites.FavoritesActivity
import com.adwio.player.ui.library.LibraryActivity
import com.adwio.player.ui.library.GlobalSearchActivity
import com.adwio.player.ui.settings.SettingsActivity

class HomeActivity : BaseFullscreenActivity() {
    private lateinit var b: ActivityHomeBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(b.root)

        val session = SessionStore(this).load()
        b.profileText.text = session?.server?.name ?: "ADWIO Professional"

        b.liveCard.setOnClickListener { open(MediaType.LIVE) }
        b.moviesCard.setOnClickListener { open(MediaType.MOVIE) }
        b.seriesCard.setOnClickListener { open(MediaType.SERIES) }
        b.settingsButton.setOnClickListener { startActivity(Intent(this, SettingsActivity::class.java)) }
        b.searchButton.setOnClickListener { startActivity(Intent(this, GlobalSearchActivity::class.java)) }
        b.continueButton.setOnClickListener { startActivity(Intent(this, ContinueWatchingActivity::class.java)) }
        b.favoritesButton.setOnClickListener { startActivity(Intent(this, FavoritesActivity::class.java)) }
        b.liveCard.requestFocus()
    }

    private fun open(type: MediaType, search: Boolean = false, favorites: Boolean = false) {
        startActivity(Intent(this, LibraryActivity::class.java).apply {
            putExtra(LibraryActivity.EXTRA_TYPE, type.name)
            putExtra(LibraryActivity.EXTRA_SEARCH, search)
            putExtra(LibraryActivity.EXTRA_FAVORITES, favorites)
        })
    }
}
