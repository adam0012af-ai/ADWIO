package com.adwio.player.ui.splash

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.animation.AccelerateDecelerateInterpolator
import com.adwio.player.data.AppResumeState
import com.adwio.player.data.SessionStore
import com.adwio.player.databinding.ActivitySplashBinding
import com.adwio.player.ui.BaseFullscreenActivity
import com.adwio.player.ui.home.HomeActivity
import com.adwio.player.ui.library.LibraryActivity
import com.adwio.player.ui.login.LoginActivity
import com.adwio.player.ui.player.PlayerActivity

class SplashActivity : BaseFullscreenActivity() {
    private lateinit var b: ActivitySplashBinding
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(b.root)

        b.splashContent.scaleX = 0.94f
        b.splashContent.scaleY = 0.94f
        b.splashContent.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(520L)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .start()

        b.loaderBar.translationX = -60f
        b.loaderBar.animate()
            .translationX(82f)
            .setDuration(900L)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .start()

        handler.postDelayed({ route() }, 1050L)
    }

    private fun route() {
        val session = SessionStore(this).load()
        if (session == null) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        val state = AppResumeState(this).load()
        val target = when {
            state.screen == AppResumeState.SCREEN_PLAYER &&
                state.playbackActive &&
                state.url.isNotBlank() -> {
                Intent(this, PlayerActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    putExtra("url", state.url)
                    putExtra("title", state.title)
                    putExtra("id", state.mediaId)
                    putExtra("type", state.mediaType.name)
                }
            }

            state.screen == AppResumeState.SCREEN_LIBRARY -> {
                Intent(this, LibraryActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    putExtra(LibraryActivity.EXTRA_TYPE, state.mediaType.name)
                }
            }

            else -> Intent(this, HomeActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
        }

        startActivity(target)
        finish()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}
