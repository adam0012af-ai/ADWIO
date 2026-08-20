package com.adwio.player.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.adwio.player.BuildConfig
import com.adwio.player.R
import com.adwio.player.data.AppSettings
import com.adwio.player.data.LibrarySnapshotCache
import com.adwio.player.data.M3uCache
import com.adwio.player.data.PlaybackHistory
import com.adwio.player.databinding.ActivitySettingsBinding
import com.adwio.player.ui.BaseFullscreenActivity
import com.adwio.player.ui.about.AboutActivity
import java.security.MessageDigest
import java.util.Locale

class SettingsActivity : BaseFullscreenActivity() {
    private lateinit var b: ActivitySettingsBinding
    private lateinit var settings: AppSettings

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(b.root)
        settings = AppSettings(this)

        b.backButton.setOnClickListener { finish() }
        b.generalCard.setOnClickListener { generalMenu() }
        b.streamFormatCard.setOnClickListener {
            choose(
                getString(R.string.stream_format),
                arrayOf(getString(R.string.auto), "HLS", "MPEG-TS"),
                arrayOf("auto", "hls", "ts"),
                settings.streamFormat
            ) { settings.streamFormat = it }
        }
        b.timeFormatCard.setOnClickListener {
            choose(
                getString(R.string.time_format),
                arrayOf(getString(R.string.system_default), getString(R.string.hour_12), getString(R.string.hour_24)),
                arrayOf("system", "12", "24"),
                settings.timeFormat
            ) { settings.timeFormat = it }
        }
        b.parentalCard.setOnClickListener { parental() }
        b.playerSettingsCard.setOnClickListener { playerSettings() }
        b.speedTestCard.setOnClickListener { speedTest() }
        b.storageCard.setOnClickListener { storage() }
        b.supportCard.setOnClickListener { sendSupportEmail() }
        b.aboutCard.setOnClickListener { startActivity(Intent(this, AboutActivity::class.java)) }
        b.resetCard.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle(R.string.reset_settings)
                .setMessage(R.string.reset_settings_message)
                .setPositiveButton(R.string.reset) { _, _ ->
                    settings.reset()
                    AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList())
                    toast(getString(R.string.settings_reset_done))
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }
        b.generalCard.requestFocus()
    }

    private fun generalMenu() {
        val languageLabel = when (settings.language) {
            "ar" -> "العربية"
            "en" -> "English"
            else -> getString(R.string.language_system)
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.general_settings)
            .setItems(arrayOf(
                "${getString(R.string.language)}  •  $languageLabel",
                getString(R.string.playback_general_options)
            )) { _, which ->
                if (which == 0) chooseLanguage() else generalOptions()
            }
            .show()
    }

    private fun chooseLanguage() {
        val labels = arrayOf(getString(R.string.language_system), "العربية", "English")
        val keys = arrayOf("system", "ar", "en")
        val idx = keys.indexOf(settings.language).coerceAtLeast(0)
        AlertDialog.Builder(this)
            .setTitle(R.string.language)
            .setSingleChoiceItems(labels, idx) { d, which ->
                val key = keys[which]
                settings.language = key
                d.dismiss()
                val locales = when (key) {
                    "ar" -> LocaleListCompat.forLanguageTags("ar")
                    "en" -> LocaleListCompat.forLanguageTags("en")
                    else -> LocaleListCompat.getEmptyLocaleList()
                }
                AppCompatDelegate.setApplicationLocales(locales)
            }
            .show()
    }

    private fun generalOptions() {
        val labels = arrayOf(
            getString(R.string.setting_autoplay_last),
            getString(R.string.setting_remember_position),
            getString(R.string.setting_auto_refresh),
            getString(R.string.setting_pip),
            getString(R.string.setting_auto_next)
        )
        val checked = booleanArrayOf(
            settings.autoplayLastChannel,
            settings.rememberPosition,
            settings.autoRefresh,
            settings.pictureInPicture,
            settings.autoNextEpisode
        )
        AlertDialog.Builder(this)
            .setTitle(R.string.playback_general_options)
            .setMultiChoiceItems(labels, checked) { _, i, v -> checked[i] = v }
            .setPositiveButton(R.string.save) { _, _ ->
                settings.autoplayLastChannel = checked[0]
                settings.rememberPosition = checked[1]
                settings.autoRefresh = checked[2]
                settings.pictureInPicture = checked[3]
                settings.autoNextEpisode = checked[4]
                settings.backgroundPlayback = false
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun playerSettings() {
        val items = arrayOf(
            getString(R.string.decoder_value, settings.decoderMode),
            getString(R.string.buffer_value, settings.bufferMode),
            getString(R.string.aspect_value, settings.aspectMode),
            getString(R.string.controls_timeout_value, settings.playerControlsTimeoutMs / 1000)
        )
        AlertDialog.Builder(this).setTitle(R.string.player_settings).setItems(items) { _, i ->
            when (i) {
                0 -> choose(
                    getString(R.string.decoder),
                    arrayOf(getString(R.string.auto), getString(R.string.hardware_preferred), getString(R.string.software_fallback)),
                    arrayOf("auto", "hardware", "software"),
                    settings.decoderMode
                ) { settings.decoderMode = it }
                1 -> choose(
                    getString(R.string.buffer_mode),
                    arrayOf(getString(R.string.buffer_small), getString(R.string.buffer_normal), getString(R.string.buffer_large)),
                    arrayOf("small", "normal", "large"),
                    settings.bufferMode
                ) { settings.bufferMode = it }
                2 -> choose(
                    getString(R.string.aspect_ratio),
                    arrayOf(getString(R.string.aspect_fit), getString(R.string.aspect_fill), getString(R.string.aspect_zoom)),
                    arrayOf("fit", "fill", "zoom"),
                    settings.aspectMode
                ) { settings.aspectMode = it }
                3 -> choose(
                    getString(R.string.controls_timeout),
                    arrayOf("3s", "4s", "6s", "8s"),
                    arrayOf("3000", "4000", "6000", "8000"),
                    settings.playerControlsTimeoutMs.toString()
                ) { settings.playerControlsTimeoutMs = it.toInt() }
            }
        }.show()
    }

    private fun parental() {
        val input = EditText(this).apply {
            hint = getString(R.string.pin_hint)
            inputType = 2
        }
        AlertDialog.Builder(this)
            .setTitle(if (settings.parentalPin.isNullOrBlank()) R.string.set_parental_pin else R.string.change_parental_pin)
            .setView(input)
            .setPositiveButton(R.string.save) { _, _ ->
                val x = input.text.toString().trim()
                if (x.length == 4) {
                    settings.parentalPin = x
                    toast(getString(R.string.parental_pin_saved))
                } else toast(getString(R.string.pin_four_digits))
            }
            .setNeutralButton(R.string.disable) { _, _ ->
                settings.parentalPin = null
                toast(getString(R.string.parental_disabled))
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun storage() {
        AlertDialog.Builder(this)
            .setTitle(R.string.storage_cache)
            .setItems(arrayOf(getString(R.string.clear_cache), getString(R.string.clear_history))) { _, i ->
                if (i == 0) {
                    M3uCache(this).clear()
                    LibrarySnapshotCache(this).clear()
                    toast(getString(R.string.cache_cleared))
                } else {
                    PlaybackHistory(this).clearCurrentPlaylist()
                    toast(getString(R.string.history_cleared))
                }
            }.show()
    }

    private fun speedTest() {
        toast(getString(R.string.checking_connection))
        Thread {
            val start = System.currentTimeMillis()
            val ok = runCatching {
                java.net.URL("https://www.google.com/generate_204").openConnection().apply {
                    connectTimeout = 4000
                    readTimeout = 4000
                }.getInputStream().close()
                true
            }.getOrDefault(false)
            val ms = System.currentTimeMillis() - start
            runOnUiThread {
                toast(if (ok) getString(R.string.connection_response, ms) else getString(R.string.connection_test_failed))
            }
        }.start()
    }

    private fun choose(title: String, labels: Array<String>, keys: Array<String>, current: String, onPick: (String) -> Unit) {
        val idx = keys.indexOf(current).coerceAtLeast(0)
        AlertDialog.Builder(this)
            .setTitle(title)
            .setSingleChoiceItems(labels, idx) { d, i ->
                onPick(keys[i])
                d.dismiss()
            }.show()
    }

    private fun sendSupportEmail() {
        val id = diagnosticId()
        val subject = "ADWIO Support • $id"
        val body = getString(
            R.string.support_email_body,
            id,
            BuildConfig.VERSION_NAME,
            BuildConfig.VERSION_CODE,
            Build.VERSION.RELEASE,
            Build.VERSION.SDK_INT,
            Build.MANUFACTURER,
            Build.MODEL,
            Locale.getDefault().toString()
        )
        val uri = Uri.parse("mailto:adwio.support@gmail.com?subject=${Uri.encode(subject)}&body=${Uri.encode(body)}")
        runCatching { startActivity(Intent(Intent.ACTION_SENDTO, uri)) }
            .onFailure { toast(getString(R.string.no_email_app)) }
    }

    private fun diagnosticId(): String {
        val androidId = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID).orEmpty()
        val raw = "${BuildConfig.APPLICATION_ID}|${BuildConfig.VERSION_CODE}|$androidId"
        val digest = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray())
        return "ADWIO-" + digest.take(4).joinToString("") { "%02X".format(it) }
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()
}
