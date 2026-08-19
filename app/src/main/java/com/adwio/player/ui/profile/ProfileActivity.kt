package com.adwio.player.ui.profile

import android.content.Intent
import android.os.Bundle
import com.adwio.player.data.SessionStore
import com.adwio.player.databinding.ActivityProfileBinding
import com.adwio.player.ui.BaseFullscreenActivity
import com.adwio.player.ui.playlist.PlaylistActivity

class ProfileActivity : BaseFullscreenActivity() {
    private lateinit var b: ActivityProfileBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityProfileBinding.inflate(layoutInflater)
        setContentView(b.root)

        val store = SessionStore(this)
        val session = store.load()
        b.serverNameText.text = session?.server?.name ?: "ADWIO"
        b.usernameText.text = session?.username ?: "—"
        b.statusText.text = session?.status?.takeIf { it.isNotBlank() } ?: "Active"
        b.expiryText.text = session?.expiresAt?.takeIf { it.isNotBlank() } ?: "—"

        b.backButton.setOnClickListener { finish() }
        b.managePlaylistsButton.setOnClickListener {
            startActivity(Intent(this, PlaylistActivity::class.java))
        }
        b.logoutButton.setOnClickListener {
            store.clear()
            startActivity(Intent(this, PlaylistActivity::class.java).addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            ))
            finish()
        }
    }
}
