package com.adwio.player.ui.home

import android.content.Intent
import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import com.adwio.player.data.*
import com.adwio.player.data.model.MediaType
import com.adwio.player.databinding.ActivityHomeBinding
import com.adwio.player.ui.BaseFullscreenActivity
import com.adwio.player.ui.library.GlobalSearchActivity
import com.adwio.player.ui.library.LibraryActivity
import com.adwio.player.ui.profile.ProfileActivity
import com.adwio.player.ui.playlist.PlaylistActivity
import com.adwio.player.ui.settings.SettingsActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HomeActivity : BaseFullscreenActivity() {
    private lateinit var b: ActivityHomeBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(b.root)
        val session = SessionStore(this).load()
        b.profileText.text = session?.server?.name ?: "ADWIO Player"
        RemoteConfigClient(this).check()
        b.liveCard.setOnClickListener { open(MediaType.LIVE) }
        b.moviesCard.setOnClickListener { open(MediaType.MOVIE) }
        b.seriesCard.setOnClickListener { open(MediaType.SERIES) }
        b.settingsButton.setOnClickListener { startActivity(Intent(this, SettingsActivity::class.java)) }
        b.searchButton.setOnClickListener { startActivity(Intent(this, GlobalSearchActivity::class.java)) }
        b.userInfoButton.setOnClickListener { startActivity(Intent(this, ProfileActivity::class.java)) }
        b.switchAccountButton.setOnClickListener { startActivity(Intent(this, PlaylistActivity::class.java)) }
        b.liveCard.requestFocus()
        loadCounts()
    }
    private fun open(type: MediaType) = startActivity(Intent(this, LibraryActivity::class.java).putExtra(LibraryActivity.EXTRA_TYPE, type.name))
    private fun loadCounts() {
        val session = SessionStore(this).load() ?: return
        lifecycleScope.launch {
            val counts = withContext(Dispatchers.IO) {
                runCatching {
                    if (session.server.id == "m3u") {
                        val all = M3uCache(this@HomeActivity).load(session.server.baseUrl)
                        Triple(all.count { it.type == MediaType.LIVE }, all.count { it.type == MediaType.MOVIE }, all.count { it.type == MediaType.SERIES })
                    } else {
                        val api = XtreamClient()
                        Triple(api.loadLive(session).size, api.loadMovies(session).size, api.loadSeries(session).size)
                    }
                }.getOrDefault(Triple(0,0,0))
            }
            b.liveCountText.text = "${counts.first} قناة"
            b.moviesCountText.text = "${counts.second} فيلم"
            b.seriesCountText.text = "${counts.third} مسلسل"
        }
    }
}
