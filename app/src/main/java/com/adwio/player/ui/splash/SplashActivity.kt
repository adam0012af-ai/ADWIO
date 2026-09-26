package com.adwio.player.ui.splash

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.lifecycleScope
import com.adwio.player.R
import com.adwio.player.data.AppSettings
import com.adwio.player.data.M3uWarmup
import com.adwio.player.data.RemoteContentClient
import com.adwio.player.data.SessionStore
import com.adwio.player.data.model.MediaType
import com.adwio.player.data.model.ServerHost
import com.adwio.player.data.model.Session
import com.adwio.player.ui.BaseFullscreenActivity
import com.adwio.player.ui.home.HomeActivity
import com.adwio.player.ui.library.LibraryActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SplashActivity : BaseFullscreenActivity() {
    companion object {
        private const val DEFAULT_PLAYLIST_URL =
            "https://iptv-org.github.io/iptv/index.m3u"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        runCatching {
            setContentView(R.layout.activity_splash)

            lifecycleScope.launch {
                delay(80)
                runCatching {
                    val settings = AppSettings(this@SplashActivity)
                    val locales =
                        if (settings.language == "system") {
                            LocaleListCompat.getEmptyLocaleList()
                        } else {
                            LocaleListCompat.forLanguageTags(settings.language)
                        }
                    AppCompatDelegate.setApplicationLocales(locales)

                    val sessionStore = SessionStore(this@SplashActivity)
                    val savedSession = sessionStore.load()

                    val bootstrap = runCatching {
                        withContext(Dispatchers.IO) {
                            RemoteContentClient().bootstrap()
                        }
                    }.getOrNull()

                    val playlistUrl = when {
                        bootstrap != null &&
                            bootstrap.configured &&
                            bootstrap.enabled &&
                            !bootstrap.authEnabled &&
                            bootstrap.playlistUrl.isNotBlank() -> bootstrap.playlistUrl

                        savedSession?.server?.id.equals("m3u", ignoreCase = true) &&
                            !savedSession?.server?.baseUrl.isNullOrBlank() ->
                            savedSession!!.server.baseUrl

                        else -> DEFAULT_PLAYLIST_URL
                    }

                    val session = Session(
                        username = "",
                        password = "",
                        server = ServerHost(
                            id = "m3u",
                            name = "ADWIO",
                            baseUrl = playlistUrl
                        ),
                        expiresAt = null,
                        status = "Active",
                        displayName = "ADWIO"
                    )

                    sessionStore.save(session)
                    M3uWarmup.start(this@SplashActivity, playlistUrl)

                    when (settings.startupScreen) {
                        "live" -> openLibrary(MediaType.LIVE)
                        "movies" -> openLibrary(MediaType.MOVIE)
                        "series" -> openLibrary(MediaType.SERIES)
                        else -> startActivity(
                            Intent(this@SplashActivity, HomeActivity::class.java)
                        )
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

        runCatching {
            val fallbackSession = Session(
                username = "",
                password = "",
                server = ServerHost(
                    id = "m3u",
                    name = "ADWIO",
                    baseUrl = DEFAULT_PLAYLIST_URL
                ),
                expiresAt = null,
                status = "Active",
                displayName = "ADWIO"
            )
            SessionStore(this).save(fallbackSession)
            M3uWarmup.start(this, DEFAULT_PLAYLIST_URL)
            startActivity(Intent(this, HomeActivity::class.java))
            finish()
        }.onFailure {
            finish()
        }
    }

    private fun openLibrary(type: MediaType) {
        startActivity(Intent(this, LibraryActivity::class.java).apply {
            putExtra(LibraryActivity.EXTRA_TYPE, type.name)
        })
    }
}
