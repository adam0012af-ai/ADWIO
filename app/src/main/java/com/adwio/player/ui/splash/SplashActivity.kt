package com.adwio.player.ui.splash

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.animation.AccelerateDecelerateInterpolator
import com.adwio.player.data.AppResumeState
import com.adwio.player.data.LiveSessionStore
import com.adwio.player.data.SessionStore
import com.adwio.player.data.model.MediaType
import com.adwio.player.databinding.ActivitySplashBinding
import com.adwio.player.ui.BaseFullscreenActivity
import com.adwio.player.ui.home.HomeActivity
import com.adwio.player.ui.library.LibraryActivity
import com.adwio.player.ui.login.LoginActivity

class SplashActivity : BaseFullscreenActivity() {
    private lateinit var b: ActivitySplashBinding
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    // Existing task: reveal the current screen immediately.
    if (!isTaskRoot) {
        finish()
        overridePendingTransition(0, 0)
        return
    }

    val live = LiveSessionStore(this).load()
    val session = SessionStore(this).load()

    if (session != null && live.active && live.url.isNotBlank()) {
        startActivity(Intent(this, LibraryActivity::class.java).apply {
            putExtra(LibraryActivity.EXTRA_TYPE, MediaType.LIVE.name)
            putExtra(
                LibraryActivity.EXTRA_RESTORE_FULLSCREEN,
                live.mode == LiveSessionStore.MODE_FULLSCREEN
            )
            addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        })
        finish()
        overridePendingTransition(0, 0)
        return
    }

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

    handler.postDelayed({ routeColdStart() }, 1050L)
}

private fun routeColdStart() {
    val session = SessionStore(this).load()
    val live = LiveSessionStore(this).load()

    val target = when {
        session == null -> Intent(this, LoginActivity::class.java)
        live.active && live.url.isNotBlank() ->
            Intent(this, LibraryActivity::class.java).apply {
                putExtra(LibraryActivity.EXTRA_TYPE, MediaType.LIVE.name)
                putExtra(
                    LibraryActivity.EXTRA_RESTORE_FULLSCREEN,
                    live.mode == LiveSessionStore.MODE_FULLSCREEN
                )
                addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
        else -> Intent(this, HomeActivity::class.java)
    }

    startActivity(target)
    finish()
}

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}
