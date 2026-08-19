package com.adwio.player.ui.playlist

import android.content.Intent
import android.os.Bundle
import com.adwio.player.databinding.ActivityPlaylistBinding
import com.adwio.player.ui.BaseFullscreenActivity
import com.adwio.player.ui.login.LoginActivity

class PlaylistActivity : BaseFullscreenActivity() {
    private lateinit var b: ActivityPlaylistBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityPlaylistBinding.inflate(layoutInflater)
        setContentView(b.root)

        b.addPlaylistButton.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
        }
        b.addPlaylistButton.requestFocus()
    }
}
