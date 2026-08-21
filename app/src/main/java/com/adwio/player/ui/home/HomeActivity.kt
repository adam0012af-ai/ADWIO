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
import com.adwio.player.ui.player.PlaybackEngine
import com.adwio.player.ui.profile.ProfileActivity
import com.adwio.player.ui.playlist.UsersActivity
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

        b.liveCard.setOnClickListener { open(MediaType.LIVE) }
        b.moviesCard.setOnClickListener { open(MediaType.MOVIE) }
        b.seriesCard.setOnClickListener { open(MediaType.SERIES) }
        b.matchesCard.setOnClickListener { startActivity(Intent(this, SportsActivity::class.java)) }

        b.settingsButton.setOnClickListener { startActivity(Intent(this, SettingsActivity::class.java)) }
        b.searchButton.setOnClickListener { startActivity(Intent(this, GlobalSearchActivity::class.java)) }
        b.userInfoButton.setOnClickListener { startActivity(Intent(this, ProfileActivity::class.java)) }
        b.switchAccountButton.setOnClickListener { startActivity(Intent(this, UsersActivity::class.java)) }
        b.refreshButton.setOnClickListener { refreshContent() }









        installPremiumFocus(b.liveCard, b.moviesCard, b.seriesCard, b.matchesCard)

        b.liveCard.requestFocus()
    }

    override fun onStart() {
        super.onStart()

        // Never let the Home activity overwrite an active Live/PiP resume state.
        val activeLive =
            PlaybackEngine.player != null &&
            PlaybackEngine.currentType == MediaType.LIVE &&
            PlaybackEngine.currentUrl.isNotBlank()

        if (!activeLive) {
            AppResumeState(this).saveHome()
        }

        handler.removeCallbacks(clockTick)
        handler.post(clockTick)
    }

    override fun onStop() {
        handler.removeCallbacks(clockTick)
        super.onStop()
    }

    private fun installPremiumFocus(vararg views: View) {
        views.forEach { view ->
            view.setOnFocusChangeListener { v, focused ->
                v.animate()
                    .scaleX(if (focused) 1.025f else 1f)
                    .scaleY(if (focused) 1.025f else 1f)
                    .translationZ(if (focused) 14f else 0f)
                    .setDuration(145L)
                    .start()
            }
        }
    }

    private fun showExpiry(raw: String?) {
        val epoch = raw?.trim()?.toLongOrNull()
        if (epoch == null || epoch <= 0L) {
            b.expiryText.visibility = View.GONE
            return
        }
        val millis = if (epoch < 10_000_000_000L) epoch * 1000L else epoch
        b.expiryText.text = "Expires: " +
            SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(millis))
        b.expiryText.visibility = View.VISIBLE
    }

    private fun open(type: MediaType) {
        startActivity(
            Intent(this, LibraryActivity::class.java)
                .putExtra(LibraryActivity.EXTRA_TYPE, type.name)
        )
    }

    private fun refreshContent() {
        if (refreshing) return
        refreshing = true
        b.refreshButton.isEnabled = false
        Toast.makeText(this, "جاري تحديث المحتوى…", Toast.LENGTH_SHORT).show()

        val session = SessionStore(this).load() ?: run {
            refreshing = false
            b.refreshButton.isEnabled = true
            Toast.makeText(this, "تعذر تحديث المحتوى", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            val success = withContext(Dispatchers.IO) {
                runCatching {
                    if (session.server.id == "m3u") {
                        val cache = M3uCache(this@HomeActivity)
                        cache.clear()
                        cache.warm(session.server.baseUrl)
                    } else {
                        val api = XtreamClient()
                        api.loadCategories(session, MediaType.LIVE)
                        api.loadCategories(session, MediaType.MOVIE)
                        api.loadCategories(session, MediaType.SERIES)
                    }
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
