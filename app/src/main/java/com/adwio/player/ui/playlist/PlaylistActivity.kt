package com.adwio.player.ui.playlist

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.appcompat.app.AppCompatActivity
import com.adwio.player.databinding.ActivityPlaylistBinding
import com.adwio.player.ui.login.LoginActivity

class PlaylistActivity : AppCompatActivity() {
    private lateinit var b: ActivityPlaylistBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityPlaylistBinding.inflate(layoutInflater)
        setContentView(b.root)
        val deviceId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
        b.deviceInfoText.text = "DEVICE ID\n${deviceId.takeLast(10).uppercase()}\n\nMulti-server ready"
        b.addPlaylistButton.setOnClickListener { startActivity(Intent(this, LoginActivity::class.java)) }
        b.addPlaylistButton.requestFocus()
    }
}
