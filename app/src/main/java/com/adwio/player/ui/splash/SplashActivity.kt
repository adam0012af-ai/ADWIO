package com.adwio.player.ui.splash

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.animation.AccelerateDecelerateInterpolator
import com.adwio.player.data.SessionStore
import com.adwio.player.databinding.ActivitySplashBinding
import com.adwio.player.ui.BaseFullscreenActivity
import com.adwio.player.ui.home.HomeActivity
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

    // True cold start always begins at Home.
    val target = if (session == null) {
        Intent(this, LoginActivity::class.java)
    } else {
        Intent(this, HomeActivity::class.java)
    }

    startActivity(target)
    finish()
}

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}
