package com.adwio.player.ui.splash

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import com.adwio.player.R
import com.adwio.player.data.SessionStore
import com.adwio.player.ui.BaseFullscreenActivity
import com.adwio.player.ui.home.HomeActivity
import com.adwio.player.ui.playlist.PlaylistActivity
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SplashActivity : BaseFullscreenActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        runCatching {
            setContentView(R.layout.activity_splash)

            lifecycleScope.launch {
                delay(900)
                runCatching {
                    val next = if (SessionStore(this@SplashActivity).load() != null) {
                        HomeActivity::class.java
                    } else {
                        PlaylistActivity::class.java
                    }
                    startActivity(Intent(this@SplashActivity, next))
                    finish()
                }.onFailure { showStartupError(it) }
            }
        }.onFailure { showStartupError(it) }
    }

    private fun showStartupError(error: Throwable) {
        if (isFinishing) return
        AlertDialog.Builder(this)
            .setTitle("ADWIO startup error")
            .setMessage(error.stackTraceToString().take(3500))
            .setCancelable(false)
            .setPositiveButton("Close") { _, _ -> finish() }
            .show()
    }
}
