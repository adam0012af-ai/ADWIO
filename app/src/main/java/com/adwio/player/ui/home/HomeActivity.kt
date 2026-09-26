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
import com.adwio.player.ui.settings.SettingsActivity
import com.adwio.player.ui.sports.SportsActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HomeActivity : BaseFullscreenActivity() {
    private lateinit var b: ActivityHomeBinding
    private val handler = Handler(Looper.getMainLooper())
    private var refreshing = false

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

        b.userInfoButton.visibility = View.GONE
        b.userInfoButton.isFocusable = false
        b.userInfoButton.isClickable = false
        b.switchAccountButton.visibility = View.GONE
        b.switchAccountButton.isFocusable = false
        b.switchAccountButton.isClickable = false

        b.liveCard.setOnClickListener { open(MediaType.LIVE) }
        b.moviesCard.setOnClickListener { open(MediaType.MOVIE) }
        b.seriesCard.setOnClickListener { open(MediaType.SERIES) }
        b.matchesCard.setOnClickListener {
            startActivity(Intent(this, SportsActivity::class.java))
        }
        b.settingsButton.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        b.searchButton.setOnClickListener {
            startActivity(Intent(this, GlobalSearchActivity::class.java))
        }
        b.refreshButton.setOnClickListener { refreshContent() }
        b.liveCard.requestFocus()
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
        b.expiryText.text =
            "Expires: " + SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(millis))
        b.expiryText.visibility = View.VISIBLE
    }

    private fun open(type: MediaType) =
        startActivity(
            Intent(this, LibraryActivity::class.java)
                .putExtra(LibraryActivity.EXTRA_TYPE, type.name)
        )

    private fun refreshContent() {
        if (refreshing) return
        refreshing = true
        b.refreshButton.isEnabled = false
        Toast.makeText(this, "جاري تحديث المحتوى…", Toast.LENGTH_SHORT).show()

        lifecycleScope.launch {
            val success = withContext(Dispatchers.IO) {
                runCatching {
                    val bootstrap = RemoteContentClient().bootstrap()
                    check(bootstrap.configured && bootstrap.enabled)
                    check(bootstrap.playlistUrl.isNotBlank())
                    check(!bootstrap.authEnabled)

                    val session = SessionStore(this@HomeActivity).load()
                        ?: throw IllegalStateException("Missing managed session")

                    val updated = session.copy(
                        server = session.server.copy(baseUrl = bootstrap.playlistUrl)
                    )
                    SessionStore(this@HomeActivity).save(updated)

                    val cache = M3uCache(this@HomeActivity)
                    cache.clear()
                    LibrarySnapshotCache(this@HomeActivity).clear()
                    M3uWarmup.start(this@HomeActivity, bootstrap.playlistUrl)
                    true
                }.getOrDefault(false)
            }

            refreshing = false
            b.refreshButton.isEnabled = true
            getSharedPreferences("adwio_refresh_state", MODE_PRIVATE)
                .edit()
                .putLong("last_refresh", System.currentTimeMillis())
                .apply()

            Toast.makeText(
                this@HomeActivity,
                if (success) "تم تحديث المحتوى بنجاح"
                else "تعذر تحديث المحتوى — حاول مرة أخرى",
                Toast.LENGTH_SHORT
            ).show()
        }
    }
}
