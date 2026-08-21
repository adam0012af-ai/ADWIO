package com.adwio.player.ui.splash

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.lifecycleScope
import com.adwio.player.R
import com.adwio.player.data.SessionStore
import com.adwio.player.data.AppSettings
import com.adwio.player.data.model.MediaType
import com.adwio.player.ui.BaseFullscreenActivity
import com.adwio.player.ui.home.HomeActivity
import com.adwio.player.ui.library.LibraryActivity
import com.adwio.player.ui.playlist.PlaylistActivity
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SplashActivity : BaseFullscreenActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        runCatching {
            setContentView(R.layout.activity_splash)

            lifecycleScope.launch {
                delay(80)
                runCatching {
                    val settings = AppSettings(this@SplashActivity)
                    val locales = if (settings.language == "system") LocaleListCompat.getEmptyLocaleList() else LocaleListCompat.forLanguageTags(settings.language)
                    AppCompatDelegate.setApplicationLocales(locales)
                    val hasSession = SessionStore(this@SplashActivity).load() != null
                    if (!hasSession) {
                        startActivity(Intent(this@SplashActivity, PlaylistActivity::class.java))
                    } else {
                        when (settings.startupScreen) {
                            "live" -> openLibrary(MediaType.LIVE)
                            "movies" -> openLibrary(MediaType.MOVIE)
                            "series" -> openLibrary(MediaType.SERIES)
                            else -> startActivity(Intent(this@SplashActivity, HomeActivity::class.java))
                        }
                    }
                    finish()
                }.onFailure { showStartupError(it) }
            }
        }.onFailure { showStartupError(it) }
    }

    private fun showStartupError(error: Throwable) {
        if (isFinishing) return
        getSharedPreferences("adwio_crash", MODE_PRIVATE).edit()
            .putString("last_crash", error.stackTraceToString().take(9000))
            .apply()
        AlertDialog.Builder(this)
            .setTitle("ADWIO")
            .setMessage("Unable to start the app. You can retry or clear the saved session.")
            .setCancelable(false)
            .setPositiveButton("Retry") { _, _ -> recreate() }
            .setNegativeButton("Reset session") { _, _ ->
                SessionStore(this).clear()
                startActivity(Intent(this, PlaylistActivity::class.java))
                finish()
            }
            .setNeutralButton("Close") { _, _ -> finish() }
            .show()
    }

    private fun openLibrary(type: MediaType) {
        startActivity(Intent(this, LibraryActivity::class.java).apply {
            putExtra(LibraryActivity.EXTRA_TYPE, type.name)
        })
    }
}
