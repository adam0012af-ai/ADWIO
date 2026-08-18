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

        runCatching {
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

            val rawId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
            val safeId = rawId?.takeIf { it.isNotBlank() } ?: "UNKNOWN"
            val shortId = safeId.takeLast(10).uppercase()
            b.deviceInfoText.text = "DEVICE ID\n$shortId\n\nMulti-server ready"

            b.addPlaylistButton.setOnClickListener {
                startActivity(Intent(this, LoginActivity::class.java))
            }
            b.addPlaylistButton.requestFocus()
        }.onFailure { error ->
            AlertDialog.Builder(this)
                .setTitle("ADWIO startup error")
                .setMessage(error.stackTraceToString().take(3500))
                .setCancelable(false)
                .setPositiveButton("Close") { _, _ -> finish() }
                .show()
        }
    }

    override fun onResume() {
        super.onResume()
        if (::adapter.isInitialized && ::store.isInitialized) refresh()
    }

    private fun refresh() {
        runCatching { adapter.submit(store.list()) }
    }
}
