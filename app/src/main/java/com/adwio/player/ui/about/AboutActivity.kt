package com.adwio.player.ui.about

import android.os.Bundle
import com.adwio.player.BuildConfig
import com.adwio.player.databinding.ActivityAboutBinding
import com.adwio.player.ui.BaseFullscreenActivity

class AboutActivity : BaseFullscreenActivity() {
    private lateinit var b: ActivityAboutBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityAboutBinding.inflate(layoutInflater)
        setContentView(b.root)

        b.versionText.text = "Version ${BuildConfig.VERSION_NAME}"
        b.backButton.setOnClickListener { finish() }
    }
}
