package com.adwio.player.ui.settings

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

class SettingsActivity : BaseFullscreenActivity() {
    private lateinit var b: ActivitySettingsBinding
    private lateinit var settings: AppSettings

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(b.root)
        settings = AppSettings(this)

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

        b.bufferButton.setOnClickListener {
            val values = arrayOf("Small", "Normal", "Large")
            val keys = arrayOf("small", "normal", "large")
            val selected = keys.indexOf(settings.bufferMode).coerceAtLeast(1)
            AlertDialog.Builder(this)
                .setTitle("Buffer mode")
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
                .setTitle("Aspect ratio")
                .setSingleChoiceItems(values, selected) { dialog, which ->
                    settings.aspectMode = keys[which]
                    updateButtons()
                    dialog.dismiss()
                }
                .show()
        }


        b.languageButton.setOnClickListener {
            val values = arrayOf("System", "English", "العربية")
            val keys = arrayOf("system", "en", "ar")
            AlertDialog.Builder(this).setTitle("Language").setItems(values) { _, which ->
                settings.language = keys[which]
                val locales = if (keys[which] == "system") LocaleListCompat.getEmptyLocaleList() else LocaleListCompat.forLanguageTags(keys[which])
                AppCompatDelegate.setApplicationLocales(locales)
                updateButtons()
            }.show()
        }
        b.gridButton.setOnClickListener {
            val values = arrayOf("Auto", "6 posters", "8 posters")
            val keys = arrayOf("auto", "6", "8")
            AlertDialog.Builder(this).setTitle("Grid density").setItems(values) { _, which -> settings.gridDensity = keys[which]; updateButtons() }.show()
        }
        b.startupButton.setOnClickListener {
            val values = arrayOf("Home", "Live TV", "Movies", "Series")
            val keys = arrayOf("home", "live", "movies", "series")
            AlertDialog.Builder(this).setTitle("Startup screen").setItems(values) { _, which -> settings.startupScreen = keys[which]; updateButtons() }.show()
        }

        b.clearCacheButton.setOnClickListener {
            cacheDir.deleteRecursively()
            Toast.makeText(this, "Cache cleared", Toast.LENGTH_SHORT).show()
        }

        b.clearHistoryButton.setOnClickListener {
            PlaybackHistory(this).clearCurrentPlaylist()
            Toast.makeText(this, "Watch history cleared", Toast.LENGTH_SHORT).show()
        }

        b.resetSettingsButton.setOnClickListener {
            settings.reset()
            recreate()
        }

        b.logoutButton.setOnClickListener {
            SessionStore(this).clear()
            finishAffinity()
        }
    }

    private fun updateButtons() {
        b.bufferButton.text = "BUFFER: ${settings.bufferMode.uppercase()}"
        b.aspectButton.text = "ASPECT: ${settings.aspectMode.uppercase()}"
        b.languageButton.text = "LANGUAGE: ${settings.language.uppercase()}"
        b.gridButton.text = "GRID: ${settings.gridDensity.uppercase()}"
        b.startupButton.text = "STARTUP: ${settings.startupScreen.uppercase()}"
    }
}
