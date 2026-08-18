package com.adwio.player.ui.playlist

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.LinearLayoutManager
import com.adwio.player.data.PlaylistStore
import com.adwio.player.data.SessionStore
import com.adwio.player.databinding.ActivityPlaylistBinding
import com.adwio.player.ui.BaseFullscreenActivity
import com.adwio.player.ui.home.HomeActivity
import com.adwio.player.ui.login.LoginActivity

class PlaylistActivity : BaseFullscreenActivity() {
    private lateinit var b: ActivityPlaylistBinding
    private lateinit var store: PlaylistStore
    private lateinit var adapter: PlaylistAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityPlaylistBinding.inflate(layoutInflater)
        setContentView(b.root)

        store = PlaylistStore(this)
        adapter = PlaylistAdapter(
            onOpen = { profile ->
                SessionStore(this).save(store.toSession(profile))
                store.put(profile)
                startActivity(Intent(this, HomeActivity::class.java))
            },
            onDelete = { profile ->
                AlertDialog.Builder(this)
                    .setTitle("Delete playlist?")
                    .setMessage(profile.name)
                    .setPositiveButton("Delete") { _, _ ->
                        store.remove(profile.id)
                        refresh()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        )

        b.playlistRecycler.layoutManager = LinearLayoutManager(this)
        b.playlistRecycler.adapter = adapter

        val deviceId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
        b.deviceInfoText.text = "DEVICE ID\n${deviceId.takeLast(10).uppercase()}\n\nMulti-server ready"

        b.addPlaylistButton.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
        }
        b.addPlaylistButton.requestFocus()
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        adapter.submit(store.list())
    }
}
