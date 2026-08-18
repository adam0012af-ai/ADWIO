package com.adwio.player.ui.splash

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.adwio.player.R
import com.adwio.player.data.SessionStore
import com.adwio.player.ui.home.HomeActivity
import com.adwio.player.ui.playlist.PlaylistActivity
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SplashActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)
        lifecycleScope.launch {
            delay(1200)
            val next = if (SessionStore(this@SplashActivity).load() != null)
                HomeActivity::class.java else PlaylistActivity::class.java
            startActivity(Intent(this@SplashActivity, next))
            finish()
        }
    }
}
