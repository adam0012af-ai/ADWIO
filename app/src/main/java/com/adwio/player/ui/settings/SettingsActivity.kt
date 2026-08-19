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

        b.autoplaySwitch.setOnCheckedChangeListener { _, value ->
            settings.autoplayLastChannel = value
        }

        b.rememberSwitch.setOnCheckedChangeListener { _, value ->
            settings.rememberPosition = value
        }

        b.refreshSwitch.setOnCheckedChangeListener { _, value ->
            settings.autoRefresh = value
        }

        b.backgroundSwitch.setOnCheckedChangeListener { _, value ->
            settings.backgroundPlayback = value
        }

        b.pipSwitch.setOnCheckedChangeListener { _, value ->
            settings.pictureInPicture = value
        }

        b.autoNextSwitch.setOnCheckedChangeListener { _, value ->
            settings.autoNextEpisode = value
        }

        updateButtons()

        b.bufferButton.setOnClickListener {
            val labels = arrayOf(
                "Small",
                "Normal",
                "Large"
            )

            val values = arrayOf(
                "small",
                "normal",
                "large"
            )

            val selected =
                values.indexOf(settings.bufferMode)
                    .coerceAtLeast(1)

            AlertDialog.Builder(this)
                .setTitle("Buffer mode")
                .setSingleChoiceItems(
                    labels,
                    selected
                ) { dialog, which ->

                    settings.bufferMode =
                        values[which]

                    updateButtons()

                    dialog.dismiss()
                }
                .show()
        }

        b.aspectButton.setOnClickListener {
            val labels = arrayOf(
                "Fit",
                "Fill",
                "Zoom"
            )

            val values = arrayOf(
                "fit",
                "fill",
                "zoom"
            )

            val selected =
                values.indexOf(settings.aspectMode)
                    .coerceAtLeast(0)

            AlertDialog.Builder(this)
                .setTitle("Aspect ratio")
                .setSingleChoiceItems(
                    labels,
                    selected
                ) { dialog, which ->

                    settings.aspectMode =
                        values[which]

                    updateButtons()

                    dialog.dismiss()
                }
                .show()
        }

        b.languageButton.setOnClickListener {

            val labels = arrayOf(
                "System",
                "English",
                "العربية"
            )

            val values = arrayOf(
                "system",
                "en",
                "ar"
            )

            val selected =
                values.indexOf(settings.language)
                    .coerceAtLeast(0)

            AlertDialog.Builder(this)
                .setTitle("Language")
                .setSingleChoiceItems(
                    labels,
                    selected
                ) { dialog, which ->

                    val selectedLanguage =
                        values[which]

                    settings.language =
                        selectedLanguage

                    val locales =
                        if (selectedLanguage == "system") {

                            LocaleListCompat
                                .getEmptyLocaleList()

                        } else {

                            LocaleListCompat
                                .forLanguageTags(
                                    selectedLanguage
                                )
                        }

                    AppCompatDelegate
                        .setApplicationLocales(
                            locales
                        )

                    updateButtons()

                    dialog.dismiss()
                }
                .show()
        }

        b.gridButton.setOnClickListener {

            val labels = arrayOf(
                "Auto",
                "6 posters",
                "8 posters"
            )

            val values = arrayOf(
                "auto",
                "6",
                "8"
            )

            val selected =
                values.indexOf(settings.gridDensity)
                    .coerceAtLeast(0)

            AlertDialog.Builder(this)
                .setTitle("Grid density")
                .setSingleChoiceItems(
                    labels,
                    selected
                ) { dialog, which ->

                    settings.gridDensity =
                        values[which]

                    updateButtons()

                    dialog.dismiss()
                }
                .show()
        }

        b.startupButton.setOnClickListener {

            val labels = arrayOf(
                "Home",
                "Live TV",
                "Movies",
                "Series"
            )

            val values = arrayOf(
                "home",
                "live",
                "movies",
                "series"
            )

            val selected =
                values.indexOf(settings.startupScreen)
                    .coerceAtLeast(0)

            AlertDialog.Builder(this)
                .setTitle("Startup screen")
                .setSingleChoiceItems(
                    labels,
                    selected
                ) { dialog, which ->

                    settings.startupScreen =
                        values[which]

                    updateButtons()

                    dialog.dismiss()
                }
                .show()
        }

        b.clearCacheButton.setOnClickListener {

            runCatching {
                cacheDir.deleteRecursively()
            }

            Toast.makeText(
                this,
                "Cache cleared",
                Toast.LENGTH_SHORT
            ).show()
        }

        b.clearHistoryButton.setOnClickListener {

            PlaybackHistory(this)
                .clearCurrentPlaylist()

            Toast.makeText(
                this,
                "Watch history cleared",
                Toast.LENGTH_SHORT
            ).show()
        }

        b.resetSettingsButton.setOnClickListener {

            settings.reset()

            recreate()
        }

        b.logoutButton.setOnClickListener {

            SessionStore(this)
                .clear()

            finishAffinity()
        }
    }

    private fun updateButtons() {

        b.bufferButton.text =
            "BUFFER: ${settings.bufferMode.uppercase()}"

        b.aspectButton.text =
            "ASPECT: ${settings.aspectMode.uppercase()}"

        b.languageButton.text =
            "LANGUAGE: ${settings.language.uppercase()}"

        b.gridButton.text =
            "GRID: ${settings.gridDensity.uppercase()}"

        b.startupButton.text =
            "STARTUP: ${settings.startupScreen.uppercase()}"
    }
}
