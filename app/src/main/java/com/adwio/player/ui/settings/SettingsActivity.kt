package com.adwio.player.ui.settings

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.adwio.player.data.AppSettings
import com.adwio.player.data.PlaybackHistory
import com.adwio.player.data.SessionStore
import com.adwio.player.databinding.ActivitySettingsBinding
import com.adwio.player.ui.BaseFullscreenActivity
import com.adwio.player.ui.about.AboutActivity

class SettingsActivity : BaseFullscreenActivity() {
    private lateinit var b: ActivitySettingsBinding
    private lateinit var settings: AppSettings

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(b.root)
        settings = AppSettings(this)
        b.backButton.setOnClickListener { finish() }

        b.autoplaySwitch.isChecked = settings.autoplayLastChannel
        b.rememberSwitch.isChecked = settings.rememberPosition
        b.refreshSwitch.isChecked = settings.autoRefresh
        b.backgroundSwitch.isChecked = settings.backgroundPlayback
        b.pipSwitch.isChecked = settings.pictureInPicture
        b.autoNextSwitch.isChecked = settings.autoNextEpisode

        b.autoplaySwitch.setOnCheckedChangeListener { _, v -> settings.autoplayLastChannel = v }
        b.rememberSwitch.setOnCheckedChangeListener { _, v -> settings.rememberPosition = v }
        b.refreshSwitch.setOnCheckedChangeListener { _, v -> settings.autoRefresh = v }
        b.backgroundSwitch.setOnCheckedChangeListener { _, v -> settings.backgroundPlayback = v }
        b.pipSwitch.setOnCheckedChangeListener { _, v -> settings.pictureInPicture = v }
        b.autoNextSwitch.setOnCheckedChangeListener { _, v -> settings.autoNextEpisode = v }

        updateButtons()

        b.streamFormatButton.setOnClickListener {
            val labels = arrayOf("Auto", "HLS", "MPEG-TS")
            val keys = arrayOf("auto", "hls", "ts")
            AlertDialog.Builder(this).setTitle(com.adwio.player.R.string.stream_format).setSingleChoiceItems(labels, keys.indexOf(settings.streamFormat).coerceAtLeast(0)) { d, i ->
                settings.streamFormat = keys[i]; updateButtons(); d.dismiss()
            }.show()
        }
        b.decoderButton.setOnClickListener {
            val labels = arrayOf("Auto", "Hardware preferred", "Software fallback")
            val keys = arrayOf("auto", "hardware", "software")
            AlertDialog.Builder(this).setTitle(com.adwio.player.R.string.decoder).setSingleChoiceItems(labels, keys.indexOf(settings.decoderMode).coerceAtLeast(0)) { d, i ->
                settings.decoderMode = keys[i]; updateButtons(); d.dismiss()
            }.show()
        }

        b.bufferButton.setOnClickListener {
            val values = arrayOf("Small", "Normal", "Large")
            val keys = arrayOf("small", "normal", "large")
            val selected = keys.indexOf(settings.bufferMode).coerceAtLeast(1)
            AlertDialog.Builder(this)
                .setTitle(com.adwio.player.R.string.buffer_mode)
                .setSingleChoiceItems(values, selected) { dialog, which ->
                    settings.bufferMode = keys[which]
                    updateButtons()
                    dialog.dismiss()
                }
                .show()
        }

        b.aspectButton.setOnClickListener {
            val values = arrayOf("Fit", "Fill", "Zoom")
            val keys = arrayOf("fit", "fill", "zoom")
            val selected = keys.indexOf(settings.aspectMode).coerceAtLeast(0)
            AlertDialog.Builder(this)
                .setTitle(com.adwio.player.R.string.aspect_ratio)
                .setSingleChoiceItems(values, selected) { dialog, which ->
                    settings.aspectMode = keys[which]
                    updateButtons()
                    dialog.dismiss()
                }
                .show()
        }


        b.languageButton.setOnClickListener {
            val values = arrayOf(getString(com.adwio.player.R.string.language_system), "English", "العربية")
            val keys = arrayOf("system", "en", "ar")
            AlertDialog.Builder(this).setTitle(com.adwio.player.R.string.language).setItems(values) { _, which ->
                settings.language = keys[which]
                val locales = if (keys[which] == "system") LocaleListCompat.getEmptyLocaleList() else LocaleListCompat.forLanguageTags(keys[which])
                AppCompatDelegate.setApplicationLocales(locales)
                window.decorView.postDelayed({ recreate() }, 120L)
            }.show()
        }
        b.gridButton.setOnClickListener {
            val values = arrayOf("Auto", "6 posters", "8 posters")
            val keys = arrayOf("auto", "6", "8")
            AlertDialog.Builder(this).setTitle(com.adwio.player.R.string.grid_density).setItems(values) { _, which -> settings.gridDensity = keys[which]; updateButtons() }.show()
        }
        b.startupButton.setOnClickListener {
            val values = arrayOf("Home", "Live TV", "Movies", "Series")
            val keys = arrayOf("home", "live", "movies", "series")
            AlertDialog.Builder(this).setTitle(com.adwio.player.R.string.startup_screen).setItems(values) { _, which -> settings.startupScreen = keys[which]; updateButtons() }.show()
        }

        b.clearCacheButton.setOnClickListener {
            cacheDir.deleteRecursively()
            Toast.makeText(this, com.adwio.player.R.string.cache_cleared, Toast.LENGTH_SHORT).show()
        }

        b.clearHistoryButton.setOnClickListener {
            PlaybackHistory(this).clearCurrentPlaylist()
            Toast.makeText(this, com.adwio.player.R.string.history_cleared, Toast.LENGTH_SHORT).show()
        }

        b.resetSettingsButton.setOnClickListener {
            settings.reset()
            recreate()
        }

        b.logoutButton.setOnClickListener {
            SessionStore(this).clear()
            finishAffinity()
        }

        b.aboutButton.setOnClickListener {
            startActivity(Intent(this, AboutActivity::class.java))
        }
    }

    private fun updateButtons() {
        val buffer = when (settings.bufferMode) { "small" -> getString(com.adwio.player.R.string.buffer_small); "large" -> getString(com.adwio.player.R.string.buffer_large); else -> getString(com.adwio.player.R.string.buffer_normal) }
        val aspect = when (settings.aspectMode) { "fill" -> getString(com.adwio.player.R.string.aspect_fill); "zoom" -> getString(com.adwio.player.R.string.aspect_zoom); else -> getString(com.adwio.player.R.string.aspect_fit) }
        val language = when (settings.language) { "ar" -> "العربية"; "en" -> "English"; else -> getString(com.adwio.player.R.string.language_system) }
        val startup = when (settings.startupScreen) { "live" -> getString(com.adwio.player.R.string.live_title); "movies" -> getString(com.adwio.player.R.string.movies_title); "series" -> getString(com.adwio.player.R.string.series_title); else -> getString(com.adwio.player.R.string.home_title) }
        b.streamFormatButton.text = getString(com.adwio.player.R.string.stream_format_value, settings.streamFormat.uppercase())
        b.decoderButton.text = getString(com.adwio.player.R.string.decoder_value, settings.decoderMode.uppercase())
        b.bufferButton.text = getString(com.adwio.player.R.string.buffer_value, buffer)
        b.aspectButton.text = getString(com.adwio.player.R.string.aspect_value, aspect)
        b.languageButton.text = getString(com.adwio.player.R.string.language_value, language)
        b.gridButton.text = getString(com.adwio.player.R.string.grid_value, settings.gridDensity.uppercase())
        b.startupButton.text = getString(com.adwio.player.R.string.startup_value, startup)
    }
}
