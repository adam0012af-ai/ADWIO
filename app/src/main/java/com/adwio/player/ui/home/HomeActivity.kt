package com.adwio.player.ui.home

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Toast
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HomeActivity : BaseFullscreenActivity() {
    private lateinit var b: ActivityHomeBinding
    private val handler = Handler(Looper.getMainLooper())

    private val clockTick = object : Runnable {
        override fun run() {
            val now = Date()
            b.clockText.text = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(now)
            b.dateText.text = SimpleDateFormat("EEE, dd MMM yyyy", Locale.getDefault()).format(now)
            handler.postDelayed(this, 30_000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(b.root)

        val session = SessionStore(this).load()
        showExpiry(session?.expiresAt)

        RemoteConfigClient(this).check()
        b.liveCard.setOnClickListener { open(MediaType.LIVE) }
        b.moviesCard.setOnClickListener { open(MediaType.MOVIE) }
        b.seriesCard.setOnClickListener { open(MediaType.SERIES) }
        b.settingsButton.setOnClickListener { startActivity(Intent(this, SettingsActivity::class.java)) }
        b.searchButton.setOnClickListener { startActivity(Intent(this, GlobalSearchActivity::class.java)) }
        b.userInfoButton.setOnClickListener { startActivity(Intent(this, ProfileActivity::class.java)) }
        b.switchAccountButton.setOnClickListener { startActivity(Intent(this, PlaylistActivity::class.java)) }
        b.refreshButton.setOnClickListener { refreshContent() }

        b.liveCard.requestFocus()
        loadCounts(forceRefresh = false)
    }

    override fun onStart() {
        super.onStart()
        handler.removeCallbacks(clockTick)
        handler.post(clockTick)
    }

    override fun onStop() {
        handler.removeCallbacks(clockTick)
        super.onStop()
    }

    private fun showExpiry(raw: String?) {
        val epoch = raw?.trim()?.toLongOrNull()
        if (epoch == null || epoch <= 0L) {
            b.expiryText.visibility = View.GONE
            return
        }
        val millis = if (epoch < 10_000_000_000L) epoch * 1000L else epoch
        val formatted = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(millis))
        b.expiryText.text = "Expires: $formatted"
        b.expiryText.visibility = View.VISIBLE
    }

    private fun open(type: MediaType) =
        startActivity(Intent(this, LibraryActivity::class.java).putExtra(LibraryActivity.EXTRA_TYPE, type.name))

    private fun refreshContent() {
        b.refreshButton.isEnabled = false
        Toast.makeText(this, "Refreshing content…", Toast.LENGTH_SHORT).show()
        val session = SessionStore(this).load() ?: run {
            b.refreshButton.isEnabled = true
            return
        }

        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                if (session.server.id == "m3u") {
                    M3uCache(this@HomeActivity).clear()
                    runCatching { M3uCache(this@HomeActivity).load(session.server.baseUrl, forceRefresh = true) }
                }
            }
            loadCounts(forceRefresh = true)
            b.refreshButton.isEnabled = true
            Toast.makeText(this@HomeActivity, "Content updated", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadCounts(forceRefresh: Boolean) {
        val session = SessionStore(this).load() ?: return
        lifecycleScope.launch {
            val counts = withContext(Dispatchers.IO) {
                runCatching {
                    if (session.server.id == "m3u") {
                        val all = M3uCache(this@HomeActivity).load(session.server.baseUrl, forceRefresh)
                        Triple(
                            all.count { it.type == MediaType.LIVE },
                            all.count { it.type == MediaType.MOVIE },
                            all.count { it.type == MediaType.SERIES }
                        )
                    } else {
                        val api = XtreamClient()
                        Triple(api.loadLive(session).size, api.loadMovies(session).size, api.loadSeries(session).size)
                    }
                }.getOrDefault(Triple(0, 0, 0))
            }
            b.liveCountText.text = "${counts.first} قناة"
            b.moviesCountText.text = "${counts.second} فيلم"
            b.seriesCountText.text = "${counts.third} مسلسل"
        }
    }
}
